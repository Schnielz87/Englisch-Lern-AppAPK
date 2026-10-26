package de.englischlernen.app.data

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import de.englischlernen.app.data.db.AppDatabase
import de.englischlernen.app.data.db.DailyActivityEntity
import de.englischlernen.app.data.db.ExamResultEntity
import de.englischlernen.app.data.db.ItemStatEntity
import de.englischlernen.app.data.db.LessonProgressEntity
import de.englischlernen.app.data.db.VocabCardEntity
import de.englischlernen.app.data.prefs.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Sicherung des Lernfortschritts als JSON-Datei (z. B. vor einer Neuinstallation). */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
) {
    @Serializable
    data class Backup(
        val version: Int = 1,
        val userName: String,
        val unlockedLevel: Int,
        val dailyGoalXp: Int,
        val lessons: List<LessonProgressEntity>,
        val cards: List<VocabCardEntity>,
        val stats: List<ItemStatEntity>,
        val exams: List<ExamResultEntity>,
        val activity: List<DailyActivityEntity>,
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) {
        val s = settings.current()
        val backup = Backup(
            userName = s.userName, unlockedLevel = s.unlockedLevel, dailyGoalXp = s.dailyGoalXp,
            lessons = db.lessonProgress().all(), cards = db.vocabCards().all(), stats = db.itemStats().all(),
            exams = db.examResults().all(), activity = db.dailyActivity().all(),
        )
        context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
            it.write(json.encodeToString(Backup.serializer(), backup))
        } ?: error("Datei konnte nicht geschrieben werden")
    }

    suspend fun import(uri: Uri) = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("Datei konnte nicht gelesen werden")
        val b = json.decodeFromString(Backup.serializer(), text)
        db.clearAllTables()
        db.withTransaction {
            db.lessonProgress().upsertAll(b.lessons)
            db.vocabCards().upsertAll(b.cards)
            db.itemStats().upsertAll(b.stats)
            db.examResults().insertAll(b.exams.map { it.copy(id = 0) })
            db.dailyActivity().upsertAll(b.activity)
        }
        settings.completeOnboarding(b.userName, b.unlockedLevel)
        settings.setDailyGoal(b.dailyGoalXp)
    }
}
