package de.englischlernen.app.data

import androidx.room.withTransaction
import de.englischlernen.app.data.content.Lesson
import de.englischlernen.app.data.content.Level
import de.englischlernen.app.data.db.AppDatabase
import de.englischlernen.app.data.db.DailyActivityEntity
import de.englischlernen.app.data.db.ExamResultEntity
import de.englischlernen.app.data.db.ItemStatEntity
import de.englischlernen.app.data.db.LessonProgressEntity
import de.englischlernen.app.domain.ExerciseResult
import de.englischlernen.app.domain.Keys
import de.englischlernen.app.domain.SpacedRepetition
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Zentrale Stelle zum Speichern von Lernfortschritt. */
class ProgressRepository(private val db: AppDatabase) {

    val lessonProgress: Flow<List<LessonProgressEntity>> = db.lessonProgress().observeAll()
    val examResults: Flow<List<ExamResultEntity>> = db.examResults().observeAll()
    val activity: Flow<List<DailyActivityEntity>> = db.dailyActivity().observeAll()
    val vocabCount: Flow<Int> = db.vocabCards().observeCount()
    val masteredCount: Flow<Int> = db.vocabCards().observeMasteredCount()
    val weakCount: Flow<Int> = db.itemStats().observeWeakCount()
    fun dueCount(now: Long = System.currentTimeMillis()): Flow<Int> = db.vocabCards().observeDueCount(now)

    fun today(): Long = LocalDate.now().toEpochDay()

    /** Durchschnitt der letzten Ergebnisse (0..1) – steuert die Schwierigkeit. */
    suspend fun skill(levelId: String): Double = (db.lessonProgress().recentAverage(levelId) ?: 70.0) / 100.0

    suspend fun lessonProgress(id: String) = db.lessonProgress().get(id)

    suspend fun dueCards(limit: Int = 20) = db.vocabCards().due(System.currentTimeMillis(), limit)

    suspend fun weakestKeys(limit: Int = 15) = db.itemStats().weakest(limit).map { it.key }

    /** Speichert Einzelergebnisse: Statistik, Wiederholungskarten und Tagesaktivitaet. */
    suspend fun recordResults(results: List<ExerciseResult>, xp: Int, spoken: Int, isReview: Boolean) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val keys = results.mapNotNull { it.exercise.key }.distinct()
            if (keys.isNotEmpty()) {
                val stats = db.itemStats().byKeys(keys).associateBy { it.key }.toMutableMap()
                results.forEach { r ->
                    val k = r.exercise.key ?: return@forEach
                    val s = stats[k] ?: ItemStatEntity(k, 0, 0, now)
                    stats[k] = if (r.correct) s.copy(correct = s.correct + 1, lastSeen = now)
                    else s.copy(wrong = s.wrong + 1, lastSeen = now)
                }
                db.itemStats().upsertAll(stats.values.toList())

                // Vorhandene Vokabelkarten nach Ergebnis neu einplanen.
                val cards = db.vocabCards().byKeys(keys.filter { it.startsWith("v:") }).associateBy { it.key }
                val updated = results.mapNotNull { r ->
                    cards[r.exercise.key]?.let { SpacedRepetition.schedule(it, r.correct, now) }
                }.associateBy { it.key }.values.toList()
                if (updated.isNotEmpty()) db.vocabCards().upsertAll(updated)
            }
            val day = today()
            val a = db.dailyActivity().get(day) ?: DailyActivityEntity(day)
            db.dailyActivity().upsert(
                a.copy(
                    xp = a.xp + xp,
                    correct = a.correct + results.count { it.correct },
                    total = a.total + results.size,
                    reviews = a.reviews + if (isReview) results.size else 0,
                    spoken = a.spoken + spoken,
                )
            )
        }
    }

    suspend fun completeLesson(level: Level, lesson: Lesson, score: Int, passed: Boolean) {
        val now = System.currentTimeMillis()
        db.withTransaction {
            val old = db.lessonProgress().get(lesson.id)
            db.lessonProgress().upsert(
                LessonProgressEntity(
                    lessonId = lesson.id, levelId = level.id,
                    bestScore = maxOf(score, old?.bestScore ?: 0), lastScore = score,
                    attempts = (old?.attempts ?: 0) + 1, passed = passed || (old?.passed ?: false),
                    updatedAt = now,
                )
            )
            if (passed) {
                db.vocabCards().insertIfAbsent(lesson.vocab.mapIndexed { i, v ->
                    SpacedRepetition.newCard(Keys.vocab(lesson.id, i), level.id, lesson.id, v.en, v.de, now)
                })
                val day = today()
                val a = db.dailyActivity().get(day) ?: DailyActivityEntity(day)
                db.dailyActivity().upsert(a.copy(lessons = a.lessons + 1))
            }
        }
    }

    suspend fun saveExam(levelId: String, score: Int, passed: Boolean) {
        db.examResults().insert(ExamResultEntity(levelId = levelId, score = score, passed = passed, takenAt = System.currentTimeMillis()))
    }

    suspend fun clearAll() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() }
}
