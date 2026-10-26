package de.englischlernen.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

enum class AiProvider(val label: String, val description: String, val keyHint: String?) {
    NONE("Keine KI", "Die App funktioniert vollständig ohne KI.", null),
    CHATGPT("Mit ChatGPT anmelden", "Nutzt dein ChatGPT-Abo (Plus oder Pro). Kein API-Schlüssel nötig.", null),
    GEMINI("Google Gemini", "API-Schlüssel von aistudio.google.com – mit kostenlosem Kontingent.", "Gemini-API-Schlüssel"),
    OPENAI("OpenAI API", "API-Schlüssel von platform.openai.com (nutzungsbasierte Kosten).", "OpenAI-API-Schlüssel (sk-…)"),
    ANTHROPIC("Anthropic Claude", "API-Schlüssel von console.anthropic.com (nutzungsbasierte Kosten).", "Claude-API-Schlüssel (sk-ant-…)"),
}

data class AiMessage(val fromUser: Boolean, val text: String)
data class AiModel(val id: String, val name: String)
data class AiConfig(val provider: AiProvider, val model: String?, val ready: Boolean)

/**
 * Einheitlicher Zugang zu verschiedenen KI-Anbietern. Alle Verbindungen laufen ueber HTTPS
 * direkt vom Geraet zum Anbieter – ohne eigenen Server dazwischen.
 */
class AiClient(
    private val store: SecureStore,
    private val http: OkHttpClient,
    val chatGpt: ChatGptAuth,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val jsonType = "application/json".toMediaType()

    private val _config = MutableStateFlow(load())
    val config: StateFlow<AiConfig> = _config

    private fun load(): AiConfig {
        val p = store.getPlain("provider")?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() } ?: AiProvider.NONE
        val ready = when (p) {
            AiProvider.NONE -> false
            AiProvider.CHATGPT -> chatGpt.state.value is ChatGptAuth.State.SignedIn
            else -> !store.get("key_${p.name}").isNullOrBlank()
        }
        return AiConfig(p, store.getPlain("model_${p.name}"), ready && store.getPlain("model_${p.name}") != null)
    }

    fun refresh() { _config.value = load() }

    fun setProvider(p: AiProvider) { store.putPlain("provider", p.name); refresh() }
    fun setKey(p: AiProvider, key: String?) { store.put("key_${p.name}", key?.trim()); refresh() }
    fun hasKey(p: AiProvider) = !store.get("key_${p.name}").isNullOrBlank()
    fun setModel(p: AiProvider, model: String) { store.putPlain("model_${p.name}", model); refresh() }

    private fun key(p: AiProvider) = store.get("key_${p.name}") ?: throw AiException("Kein API-Schlüssel hinterlegt.")

    // ---------------- Modelle ----------------

    suspend fun listModels(p: AiProvider): List<AiModel> = withContext(Dispatchers.IO) {
        when (p) {
            AiProvider.CHATGPT -> get("https://api.openai.com/v1/models", "Authorization" to "Bearer ${chatGpt.accessToken()}") { o ->
                o["models"]!!.jsonArray.map { it.jsonObject }
                    .filter { it.s("visibility") == null || it.s("visibility") == "list" }
                    .map { AiModel(it.s("slug")!!, it.s("display_name") ?: it.s("slug")!!) }
            }
            AiProvider.OPENAI -> get("https://api.openai.com/v1/models", "Authorization" to "Bearer ${key(p)}") { o ->
                val skip = Regex("audio|realtime|tts|transcribe|image|search|embedding|moderation|instruct|codex|dall|whisper|davinci|babbage")
                o["data"]!!.jsonArray.map { it.jsonObject.s("id")!! }
                    .filter { it.startsWith("gpt-") && !skip.containsMatchIn(it) }
                    .sortedDescending().map { AiModel(it, it) }
            }
            AiProvider.ANTHROPIC -> get(
                "https://api.anthropic.com/v1/models?limit=50", "x-api-key" to key(p), "anthropic-version" to "2023-06-01",
            ) { o -> o["data"]!!.jsonArray.map { it.jsonObject }.map { AiModel(it.s("id")!!, it.s("display_name") ?: it.s("id")!!) } }
            AiProvider.GEMINI -> get("https://generativelanguage.googleapis.com/v1beta/models?pageSize=100", "x-goog-api-key" to key(p)) { o ->
                o["models"]!!.jsonArray.map { it.jsonObject }
                    .filter { m -> m["supportedGenerationMethods"]?.jsonArray?.any { it.jsonPrimitive.content == "generateContent" } == true }
                    .map { AiModel(it.s("name")!!.removePrefix("models/"), it.s("displayName") ?: it.s("name")!!) }
                    .filter { it.id.startsWith("gemini") && !it.id.contains("embedding") && !it.id.contains("image") && !it.id.contains("tts") }
                    .sortedWith(compareByDescending<AiModel> { "flash" in it.id }.thenByDescending { it.id })
            }
            AiProvider.NONE -> emptyList()
        }
    }

    // ---------------- Antworten ----------------

    /**
     * Sendet [history] mit Systemanweisung [instructions]. [onDelta] erhaelt bei Streaming-Anbietern
     * Teilstuecke, damit die Antwort live erscheint. Gibt den vollstaendigen Text zurueck.
     */
    suspend fun reply(instructions: String, history: List<AiMessage>, onDelta: (String) -> Unit = {}): String = withContext(Dispatchers.IO) {
        val cfg = _config.value
        val model = cfg.model ?: throw AiException("Bitte zuerst ein KI-Modell auswählen.")
        when (cfg.provider) {
            AiProvider.CHATGPT -> responsesStream(chatGpt.accessToken(), model, instructions, history, onDelta)
            AiProvider.OPENAI -> responsesStream(key(AiProvider.OPENAI), model, instructions, history, onDelta)
            AiProvider.ANTHROPIC -> anthropic(model, instructions, history)
            AiProvider.GEMINI -> gemini(model, instructions, history)
            AiProvider.NONE -> throw AiException("Keine KI eingerichtet.")
        }.also { if (cfg.provider == AiProvider.ANTHROPIC || cfg.provider == AiProvider.GEMINI) onDelta(it) }
    }

    /** OpenAI Responses API mit Streaming (Pflicht fuer die ChatGPT-Abo-Nutzung: store=false, stream=true). */
    private fun responsesStream(token: String, model: String, instructions: String, history: List<AiMessage>, onDelta: (String) -> Unit): String {
        val body = buildJsonObject {
            put("model", model)
            put("instructions", instructions)
            put("input", buildJsonArray {
                history.forEach { m ->
                    add(buildJsonObject { put("role", if (m.fromUser) "user" else "assistant"); put("content", m.text) })
                }
            })
            put("store", false)
            put("stream", true)
        }
        val req = Request.Builder().url("https://api.openai.com/v1/responses")
            .header("Authorization", "Bearer $token").header("Accept", "text/event-stream")
            .post(body.toString().toRequestBody(jsonType)).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw AiException(errorMessage(r))
            val out = StringBuilder()
            var completed = false
            val source = r.body.source()
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                val ev = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                when (ev.s("type")) {
                    "response.output_text.delta" -> ev.s("delta")?.let { out.append(it); onDelta(out.toString()) }
                    "response.completed" -> completed = true
                    "response.failed", "error" -> {
                        val code = ev["response"]?.jsonObject?.get("error")?.jsonObject?.s("code") ?: ev.s("code") ?: "unbekannt"
                        throw AiException(
                            when (code) {
                                "subscription_sharing_usage_limit_exceeded" -> "Dein ChatGPT-Nutzungslimit ist erreicht. Versuche es später erneut."
                                "subscription_sharing_usage_unavailable" -> "Die Abo-Nutzung ist für dein Konto gerade nicht verfügbar."
                                else -> "Die KI konnte nicht antworten ($code)."
                            }
                        )
                    }
                    "response.incomplete" -> completed = true
                }
            }
            if (!completed && out.isEmpty()) throw AiException("Die Verbindung wurde unterbrochen.")
            return out.toString()
        }
    }

    private fun anthropic(model: String, instructions: String, history: List<AiMessage>): String {
        val body = buildJsonObject {
            put("model", model)
            put("max_tokens", 1500)
            put("system", instructions)
            put("messages", buildJsonArray {
                normalized(history).forEach { m ->
                    add(buildJsonObject { put("role", if (m.fromUser) "user" else "assistant"); put("content", m.text) })
                }
            })
        }
        val req = Request.Builder().url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", key(AiProvider.ANTHROPIC)).header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody(jsonType)).build()
        return http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw AiException(errorMessage(r))
            val o = json.parseToJsonElement(r.body.string()).jsonObject
            o["content"]!!.jsonArray.mapNotNull { it.jsonObject.s("text") }.joinToString("")
        }
    }

    private fun gemini(model: String, instructions: String, history: List<AiMessage>): String {
        val body = buildJsonObject {
            put("systemInstruction", buildJsonObject { put("parts", buildJsonArray { add(buildJsonObject { put("text", instructions) }) }) })
            put("contents", buildJsonArray {
                normalized(history).forEach { m ->
                    add(buildJsonObject {
                        put("role", if (m.fromUser) "user" else "model")
                        put("parts", buildJsonArray { add(buildJsonObject { put("text", m.text) }) })
                    })
                }
            })
        }
        val req = Request.Builder().url("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .header("x-goog-api-key", key(AiProvider.GEMINI))
            .post(body.toString().toRequestBody(jsonType)).build()
        return http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw AiException(errorMessage(r))
            val o = json.parseToJsonElement(r.body.string()).jsonObject
            val parts = o["candidates"]?.jsonArray?.firstOrNull()?.jsonObject?.get("content")?.jsonObject?.get("parts") as? JsonArray
            parts?.mapNotNull { it.jsonObject.s("text") }?.joinToString("")?.takeIf { it.isNotBlank() }
                ?: throw AiException("Die KI hat keine Antwort geliefert.")
        }
    }

    /** Manche Anbieter verlangen: erste Nachricht vom Nutzer, Rollen abwechselnd. */
    private fun normalized(history: List<AiMessage>): List<AiMessage> {
        val out = mutableListOf<AiMessage>()
        history.forEach { m ->
            if (out.isEmpty() && !m.fromUser) out += AiMessage(true, "(Start)")
            if (out.isNotEmpty() && out.last().fromUser == m.fromUser) out[out.lastIndex] = AiMessage(m.fromUser, out.last().text + "\n\n" + m.text)
            else out += m
        }
        return out
    }

    private fun <T> get(url: String, vararg headers: Pair<String, String>, parse: (JsonObject) -> T): T {
        val req = Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.build()
        return http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw AiException(errorMessage(r))
            parse(json.parseToJsonElement(r.body.string()).jsonObject)
        }
    }

    private fun errorMessage(r: Response): String = when (r.code) {
        401, 403 -> "Zugang abgelehnt – bitte Schlüssel bzw. Anmeldung prüfen."
        404 -> "Modell nicht gefunden – bitte ein anderes Modell wählen."
        429 -> "Zu viele Anfragen oder Kontingent erschöpft. Bitte später erneut versuchen."
        in 500..599 -> "Der KI-Dienst ist gerade nicht erreichbar."
        else -> "KI-Anfrage fehlgeschlagen (Fehler ${r.code})."
    }

    private fun JsonObject.s(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
}
