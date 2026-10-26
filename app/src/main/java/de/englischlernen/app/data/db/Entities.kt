package de.englischlernen.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "lesson_progress")
data class LessonProgressEntity(
    @PrimaryKey val lessonId: String,
    val levelId: String,
    val bestScore: Int,
    val lastScore: Int,
    val attempts: Int,
    val passed: Boolean,
    val updatedAt: Long,
)

/** Karteikarte fuer die verteilte Wiederholung (Leitner-System). */
@Serializable
@Entity(tableName = "vocab_cards")
data class VocabCardEntity(
    @PrimaryKey val key: String,
    val levelId: String,
    val lessonId: String,
    val en: String,
    val de: String,
    val box: Int,
    val dueAt: Long,
    val lapses: Int,
    val reviews: Int,
)

/** Statistik je Uebungselement – Grundlage fuer das Schwaechen-Training. */
@Serializable
@Entity(tableName = "item_stats")
data class ItemStatEntity(
    @PrimaryKey val key: String,
    val correct: Int,
    val wrong: Int,
    val lastSeen: Long,
)

@Serializable
@Entity(tableName = "exam_results")
data class ExamResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val levelId: String,
    val score: Int,
    val passed: Boolean,
    val takenAt: Long,
)

/** Tagesaktivitaet: Basis fuer Serie (Streak), Tagesziel und Statistik. */
@Serializable
@Entity(tableName = "daily_activity")
data class DailyActivityEntity(
    @PrimaryKey val epochDay: Long,
    val xp: Int = 0,
    val correct: Int = 0,
    val total: Int = 0,
    val lessons: Int = 0,
    val reviews: Int = 0,
    val spoken: Int = 0,
)
