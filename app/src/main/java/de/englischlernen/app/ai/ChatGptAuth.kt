package de.englischlernen.app.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.math.BigInteger
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URLDecoder
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.RSAPublicKeySpec
import java.util.UUID

/**
 * "Mit ChatGPT anmelden" nach OpenAIs Ablauf fuer Open-Source-/lokal laufende Apps
 * (developers.openai.com/siwc/token-sharing-open-source): OAuth 2.0 Authorization Code + PKCE,
 * dynamische Client-Registrierung, Rueckruf ueber 127.0.0.1 (Loopback) im Geraet.
 * Die Nutzung laeuft ueber das ChatGPT-Abo des Nutzers (Plus/Pro).
 */
class ChatGptAuth(
    private val context: Context,
    private val store: SecureStore,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
) {
    sealed interface State {
        data object SignedOut : State
        data object InProgress : State
        data class SignedIn(val email: String) : State
        data class Error(val message: String) : State
    }

    @Serializable
    data class Credentials(
        val email: String,
        val subject: String,
        val clientId: String,
        val idToken: String,
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long,
        val scopes: List<String>,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val refreshMutex = Mutex()
    private val _state = MutableStateFlow<State>(State.SignedOut)
    val state: StateFlow<State> = _state
    private var pending: Job? = null

    init {
        credentials()?.let { _state.value = State.SignedIn(it.email) }
    }

    private fun credentials(): Credentials? =
        store.get(KEY_CREDS)?.let { runCatching { json.decodeFromString(Credentials.serializer(), it) }.getOrNull() }

    private fun save(c: Credentials) = store.put(KEY_CREDS, json.encodeToString(Credentials.serializer(), c))

    /** Stabile, nicht personenbezogene Kennung dieses Geraets (ext_agent_host_id). */
    private fun hostId(): String = store.getPlain(KEY_HOST) ?: "urn:uuid:${UUID.randomUUID()}".also { store.putPlain(KEY_HOST, it) }

    fun signIn() {
        pending?.cancel()
        _state.value = State.InProgress
        pending = scope.launch(Dispatchers.IO) {
            try {
                _state.value = State.SignedIn(runFlow().email)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: java.io.IOException) {
                _state.value = State.Error("Keine Verbindung zu OpenAI. Bitte Internet prüfen und erneut versuchen.")
            } catch (e: Exception) {
                _state.value = State.Error(e.message ?: "Anmeldung fehlgeschlagen")
            }
        }
    }

    fun cancel() {
        pending?.cancel()
        _state.value = credentials()?.let { State.SignedIn(it.email) } ?: State.SignedOut
    }

    private suspend fun runFlow(): Credentials {
        val savedClient = store.getPlain(KEY_CLIENT)
        val previous = credentials()
        val state = random(24)
        val nonce = random(24)
        val verifier = random(48)
        val challenge = b64url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))

        val server = openServer()
        server.use { srv ->
            val redirect = "http://127.0.0.1:${srv.localPort}/auth/callback"
            val params = linkedMapOf(
                "client_id" to (savedClient ?: "dynamic_agent_client"),
                "ext_agent_host_id" to hostId(),
                "response_type" to "code",
                "redirect_uri" to redirect,
                "scope" to SCOPES,
                "resource" to RESOURCE,
                "state" to state,
                "nonce" to nonce,
                "code_challenge_method" to "S256",
                "code_challenge" to challenge,
            )
            if (savedClient == null) params["agent_name_hint"] = APP_NAME
            previous?.let { params["id_token_hint"] = it.idToken; params["login_hint"] = it.email }
            val url = Uri.parse(AUTHORIZE).buildUpon().apply { params.forEach { (k, v) -> appendQueryParameter(k, v) } }.build()
            withContext(Dispatchers.Main) {
                context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }

            val query = awaitCallback(srv)
            if (query["state"] != state) error("Sicherheitsprüfung fehlgeschlagen (state).")
            query["error"]?.let {
                error(if (it == "access_denied") "Anmeldung abgelehnt oder ChatGPT-Abo-Nutzung nicht erlaubt." else "OpenAI meldet: $it")
            }
            val code = query["code"] ?: error("Kein Autorisierungscode erhalten.")
            val issued = query["client_id"]
            val clientId = when {
                savedClient == null -> issued ?: error("Registrierung unvollständig (keine client_id).")
                issued != null && issued != savedClient -> error("Unerwartete client_id – Anmeldung verworfen.")
                else -> savedClient
            }
            val tokens = withNetworkRetry {
                tokenRequest(
                    FormBody.Builder()
                        .add("grant_type", "authorization_code").add("client_id", clientId).add("code", code)
                        .add("code_verifier", verifier).add("redirect_uri", redirect).add("resource", RESOURCE).build()
                )
            }
            val idToken = tokens.str("id_token") ?: error("Kein ID-Token erhalten.")
            val jwks = withNetworkRetry {
                http.newCall(Request.Builder().url(JWKS).build()).execute().use { json.parseToJsonElement(it.body.string()).jsonObject }
            }
            val claims = verifyIdToken(idToken, clientId, nonce, jwks)
            val scopes = tokens.str("scope")?.split(' ')?.filter { it.isNotBlank() } ?: emptyList()
            if ("chatgpt.tokens.use.direct" !in scopes) error("Die Nutzung deines ChatGPT-Abos wurde nicht freigegeben.")
            val creds = Credentials(
                email = claims.str("email") ?: "ChatGPT-Konto",
                subject = claims.str("sub") ?: error("ID-Token ohne sub"),
                clientId = clientId,
                idToken = idToken,
                accessToken = tokens.str("access_token") ?: error("Kein Access-Token"),
                refreshToken = tokens.str("refresh_token") ?: error("Kein Refresh-Token"),
                expiresAt = System.currentTimeMillis() + (tokens["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600) * 1000,
                scopes = scopes,
            )
            if (previous != null && previous.subject != creds.subject && savedClient != null) error("Anderes Konto als erwartet.")
            store.putPlain(KEY_CLIENT, clientId)
            save(creds)
            return creds
        }
    }

    private fun openServer(): ServerSocket {
        val lo = InetAddress.getByName("127.0.0.1")
        return runCatching { ServerSocket(1455, 4, lo) }.getOrElse { ServerSocket(0, 4, lo) }.apply { soTimeout = 5 * 60 * 1000 }
    }

    /** Wartet auf den Browser-Rueckruf, antwortet mit Weiterleitung zurueck in die App. */
    private fun awaitCallback(server: ServerSocket): Map<String, String> {
        while (true) {
            val socket = try { server.accept() } catch (e: java.net.SocketTimeoutException) { error("Zeitüberschreitung bei der Anmeldung.") }
            socket.use { s ->
                val reader = s.getInputStream().bufferedReader()
                val requestLine = reader.readLine() ?: return@use
                val path = requestLine.split(' ').getOrNull(1) ?: return@use
                val out = s.getOutputStream()
                if (!path.startsWith("/auth/callback")) {
                    out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    return@use
                }
                val html = "<html><head><meta name=viewport content='width=device-width'></head><body style='font-family:sans-serif;text-align:center;padding:40px'>" +
                    "<h2>Fertig!</h2><p>Du kannst jetzt zu <b>Fundbrüder English</b> zurückkehren.</p>" +
                    "<p><a href='$APP_RETURN'>Zurück zur App</a></p></body></html>"
                val body = html.toByteArray()
                out.write(("HTTP/1.1 302 Found\r\nLocation: $APP_RETURN\r\nContent-Type: text/html; charset=utf-8\r\n" +
                    "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray())
                out.write(body)
                out.flush()
                val q = path.substringAfter('?', "")
                return q.split('&').filter { '=' in it }.associate {
                    URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
                }
            }
        }
    }

    /** Liefert ein gueltiges Access-Token und erneuert es bei Bedarf. */
    suspend fun accessToken(): String = refreshMutex.withLock {
        val c = credentials() ?: throw AiException("Bitte zuerst mit ChatGPT anmelden.")
        if (c.expiresAt - 60_000 > System.currentTimeMillis()) return c.accessToken
        val tokens = withContext(Dispatchers.IO) {
            runCatching {
                tokenRequest(
                    FormBody.Builder().add("grant_type", "refresh_token").add("client_id", c.clientId)
                        .add("refresh_token", c.refreshToken).add("resource", RESOURCE).build()
                )
            }.getOrElse {
                _state.value = State.Error("Sitzung abgelaufen – bitte erneut anmelden.")
                throw AiException("Sitzung abgelaufen – bitte erneut mit ChatGPT anmelden.")
            }
        }
        val updated = c.copy(
            accessToken = tokens.str("access_token") ?: c.accessToken,
            refreshToken = tokens.str("refresh_token") ?: c.refreshToken,
            expiresAt = System.currentTimeMillis() + (tokens["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600) * 1000,
            scopes = tokens.str("scope")?.split(' ') ?: c.scopes,
        )
        save(updated)
        updated.accessToken
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        credentials()?.let { c ->
            runCatching {
                http.newCall(
                    Request.Builder().url(REVOKE).post(
                        FormBody.Builder().add("token", c.refreshToken).add("token_type_hint", "refresh_token").add("client_id", c.clientId).build()
                    ).build()
                ).execute().close()
            }
        }
        // Client-Registrierung und Host-ID bleiben fuer eine spaetere Anmeldung erhalten.
        store.put(KEY_CREDS, null)
        _state.value = State.SignedOut
    }

    /**
     * Waehrend der Browser im Vordergrund ist, sperren viele Geraete (Energiesparen, Datensparmodus)
     * das Internet fuer Apps im Hintergrund – das zeigt sich als "Unable to resolve host".
     * Deshalb Netzwerkfehler bis zu 2 Minuten lang wiederholen: Sobald man zur App zurueckkehrt, klappt es.
     */
    private suspend fun <T> withNetworkRetry(block: () -> T): T {
        val deadline = System.currentTimeMillis() + 120_000
        while (true) {
            try {
                return block()
            } catch (e: java.io.IOException) {
                if (System.currentTimeMillis() > deadline) {
                    throw AiException("Keine Verbindung zu OpenAI. Bitte Internet prüfen und erneut anmelden.")
                }
                kotlinx.coroutines.delay(2_000)
            }
        }
    }

    private fun tokenRequest(body: FormBody): JsonObject =
        http.newCall(Request.Builder().url(TOKEN).post(body).build()).execute().use { r ->
            val text = r.body.string()
            if (!r.isSuccessful) throw AiException("Token-Anfrage fehlgeschlagen (${r.code}).")
            json.parseToJsonElement(text).jsonObject
        }

    /** Prueft Signatur (RS256 gegen OpenAIs JWKS), Aussteller, Empfaenger, Ablauf und nonce. */
    private fun verifyIdToken(token: String, clientId: String, nonce: String, jwks: JsonObject): JsonObject {
        val parts = token.split('.')
        require(parts.size == 3) { "Ungültiges ID-Token" }
        val header = json.parseToJsonElement(String(b64decode(parts[0]))).jsonObject
        val claims = json.parseToJsonElement(String(b64decode(parts[1]))).jsonObject
        if (header.str("alg") != "RS256") error("Nicht unterstützter Token-Algorithmus")
        val kid = header.str("kid")
        val jwk = jwks["keys"]!!.jsonArray.map { it.jsonObject }.firstOrNull { it.str("kid") == kid } ?: error("Signaturschlüssel nicht gefunden")
        val pub = KeyFactory.getInstance("RSA").generatePublic(
            RSAPublicKeySpec(BigInteger(1, b64decode(jwk.str("n")!!)), BigInteger(1, b64decode(jwk.str("e")!!)))
        )
        val ok = Signature.getInstance("SHA256withRSA").run {
            initVerify(pub); update("${parts[0]}.${parts[1]}".toByteArray()); verify(b64decode(parts[2]))
        }
        if (!ok) error("ID-Token-Signatur ungültig")
        if (claims.str("iss") != ISSUER) error("Falscher Aussteller")
        val aud = claims["aud"]
        val audOk = aud?.let { a -> runCatching { a.jsonArray.any { it.jsonPrimitive.content == clientId } }.getOrElse { a.jsonPrimitive.content == clientId } } ?: false
        if (!audOk) error("ID-Token für falschen Empfänger")
        val exp = claims["exp"]?.jsonPrimitive?.longOrNull ?: 0
        if (exp * 1000 < System.currentTimeMillis() - 60_000) error("ID-Token abgelaufen")
        if (claims.str("nonce") != nonce) error("Sicherheitsprüfung fehlgeschlagen (nonce).")
        return claims
    }

    private fun JsonObject.str(k: String) = this[k]?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
    private fun random(bytes: Int) = b64url(ByteArray(bytes).also { SecureRandom().nextBytes(it) })
    private fun b64url(b: ByteArray) = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    private fun b64decode(s: String) = Base64.decode(s, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    companion object {
        const val APP_NAME = "Fundbrüder English"
        const val APP_RETURN = "fundbrueder://auth-done"
        const val ISSUER = "https://auth.openai.com"
        const val AUTHORIZE = "https://auth.openai.com/api/accounts/authorize"
        const val TOKEN = "https://auth.openai.com/api/accounts/oauth/token"
        const val REVOKE = "https://auth.openai.com/api/accounts/oauth/revoke"
        const val JWKS = "https://auth.openai.com/.well-known/jwks.json"
        const val RESOURCE = "https://api.openai.com/v1"
        const val SCOPES = "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct"
        private const val KEY_CREDS = "chatgpt_credentials"
        private const val KEY_CLIENT = "chatgpt_client_id"
        private const val KEY_HOST = "chatgpt_host_id"
    }
}

class AiException(message: String) : Exception(message)
