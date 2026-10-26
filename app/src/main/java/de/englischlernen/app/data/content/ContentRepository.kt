package de.englischlernen.app.data.content

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Laedt die Lerninhalte offline aus den App-Assets. Neue Inhalte werden einfach als
 * JSON-Datei ergaenzt (siehe README, Abschnitt "Inhalte erweitern").
 */
class ContentRepository(private val context: Context) {

    private val mutex = Mutex()
    private var cache: List<Level>? = null

    suspend fun levels(): List<Level> = mutex.withLock {
        cache ?: withContext(Dispatchers.IO) {
            LEVEL_IDS.map { id ->
                context.assets.open("content/$id.json").bufferedReader().use { parse(it.readText()) }
            }
        }.also { cache = it }
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
    }
}
