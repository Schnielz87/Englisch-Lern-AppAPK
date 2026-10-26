package de.englischlernen.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import de.englischlernen.app.ai.AiClient
import de.englischlernen.app.ai.ChatGptAuth
import de.englischlernen.app.ai.SecureStore
import de.englischlernen.app.data.BackupManager
import de.englischlernen.app.data.ProgressRepository
import de.englischlernen.app.data.content.ContentRepository
import de.englischlernen.app.data.db.AppDatabase
import de.englischlernen.app.data.prefs.SettingsRepository
import de.englischlernen.app.speech.SpeechInput
import de.englischlernen.app.speech.TtsManager
import de.englischlernen.app.update.UpdateChecker

/** Einfache manuelle Dependency Injection – haelt alle app-weiten Abhaengigkeiten. */
class AppContainer(context: Context, scope: CoroutineScope) {
    val database: AppDatabase = AppDatabase.create(context)
    val content = ContentRepository(context)
    val settings = SettingsRepository(context)
    val progress = ProgressRepository(database)
    val tts = TtsManager(context)
    val speech = SpeechInput(context)
    val updates = UpdateChecker()
    val backup = BackupManager(context, database, settings)

    /** HTTP-Client fuer KI-Anfragen (laengere Lesezeit wegen Streaming). */
    private val aiHttp = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()
    val secureStore = SecureStore(context)
    val chatGptAuth = ChatGptAuth(context, secureStore, aiHttp, scope)
    val ai = AiClient(secureStore, aiHttp, chatGptAuth)

    init {
        scope.launch { chatGptAuth.state.collect { ai.refresh() } }
    }
}
