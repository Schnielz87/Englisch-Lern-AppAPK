package de.englischlernen.app.data.content

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Laedt die Lerninhalte offline aus den App-Assets. Neue Inhalte werden einfach als
 * JSON-Datei ergaenzt (siehe README, Abschnitt "Inhalte erweitern").
 */
class ContentRepository(private val context: Context) {

    private val mutex = Mutex()
    private var cache: List<Level>? = null
    private var speakingCache: Map<String, List<SpeakingPrompt>>? = null

    suspend fun levels(): List<Level> = mutex.withLock {
        cache ?: withContext(Dispatchers.IO) {
            LEVEL_IDS.map { id ->
                context.assets.open("content/$id.json").bufferedReader().use { parse(it.readText()) }
            }
        }.also { cache = it }
    }

    /** Freie Sprechaufgaben je Niveau (assets/content/speaking.json). */
    suspend fun speaking(): Map<String, List<SpeakingPrompt>> = mutex.withLock {
        speakingCache ?: withContext(Dispatchers.IO) {
            context.assets.open("content/speaking.json").bufferedReader().use { parseSpeaking(it.readText()) }
        }.also { speakingCache = it }
    }

    suspend fun level(id: String): Level? = levels().firstOrNull { it.id == id }

    suspend fun lesson(lessonId: String): Pair<Level, Lesson>? {
        for (level in levels()) {
            level.lessons.firstOrNull { it.id == lessonId }?.let { return level to it }
        }
        return null
    }

    companion object {
        val LEVEL_IDS = listOf("a1", "a2", "b1", "b2", "c1", "c2")
        val json = Json { ignoreUnknownKeys = true }
        fun parse(text: String): Level = json.decodeFromString(Level.serializer(), text)
        fun parseSpeaking(text: String): Map<String, List<SpeakingPrompt>> =
            json.decodeFromString(kotlinx.serialization.builtins.MapSerializer(
                String.serializer(),
                kotlinx.serialization.builtins.ListSerializer(SpeakingPrompt.serializer()),
            ), text)
    }
}
