package de.englischlernen.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LessonProgressDao {
    @Query("SELECT * FROM lesson_progress")
    fun observeAll(): Flow<List<LessonProgressEntity>>

    @Query("SELECT * FROM lesson_progress")
    suspend fun all(): List<LessonProgressEntity>

    @Query("SELECT * FROM lesson_progress WHERE lessonId = :id")
    suspend fun get(id: String): LessonProgressEntity?

    @Upsert
    suspend fun upsert(entity: LessonProgressEntity)

    @Upsert
    suspend fun upsertAll(list: List<LessonProgressEntity>)

    @Query("SELECT AVG(lastScore) FROM (SELECT lastScore FROM lesson_progress WHERE levelId = :levelId ORDER BY updatedAt DESC LIMIT 5)")
    suspend fun recentAverage(levelId: String): Double?
}

@Dao
interface VocabCardDao {
    @Query("SELECT COUNT(*) FROM vocab_cards WHERE dueAt <= :now")
    fun observeDueCount(now: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM vocab_cards")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM vocab_cards WHERE box >= 4")
    fun observeMasteredCount(): Flow<Int>

    @Query("SELECT * FROM vocab_cards WHERE dueAt <= :now ORDER BY box ASC, dueAt ASC LIMIT :limit")
    suspend fun due(now: Long, limit: Int): List<VocabCardEntity>

    @Query("SELECT * FROM vocab_cards WHERE `key` IN (:keys)")
    suspend fun byKeys(keys: List<String>): List<VocabCardEntity>

    @Query("SELECT * FROM vocab_cards")
    suspend fun all(): List<VocabCardEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(cards: List<VocabCardEntity>)

    @Upsert
    suspend fun upsertAll(cards: List<VocabCardEntity>)
}

@Dao
interface ItemStatDao {
    @Query("SELECT * FROM item_stats WHERE `key` IN (:keys)")
    suspend fun byKeys(keys: List<String>): List<ItemStatEntity>

    @Query("SELECT * FROM item_stats WHERE wrong > correct OR wrong >= 2 ORDER BY (wrong - correct) DESC, lastSeen DESC LIMIT :limit")
    suspend fun weakest(limit: Int): List<ItemStatEntity>

    @Query("SELECT COUNT(*) FROM item_stats WHERE wrong > correct OR wrong >= 2")
    fun observeWeakCount(): Flow<Int>

    @Query("SELECT * FROM item_stats")
    suspend fun all(): List<ItemStatEntity>

    @Upsert
    suspend fun upsertAll(list: List<ItemStatEntity>)
}

@Dao
interface ExamResultDao {
    @Query("SELECT * FROM exam_results ORDER BY takenAt DESC")
    fun observeAll(): Flow<List<ExamResultEntity>>

    @Query("SELECT * FROM exam_results")
    suspend fun all(): List<ExamResultEntity>

    @Insert
    suspend fun insert(entity: ExamResultEntity)

    @Insert
    suspend fun insertAll(list: List<ExamResultEntity>)
}

@Dao
interface DailyActivityDao {
    @Query("SELECT * FROM daily_activity ORDER BY epochDay DESC")
    fun observeAll(): Flow<List<DailyActivityEntity>>

    @Query("SELECT * FROM daily_activity")
    suspend fun all(): List<DailyActivityEntity>

    @Query("SELECT * FROM daily_activity WHERE epochDay = :day")
    suspend fun get(day: Long): DailyActivityEntity?

    @Upsert
    suspend fun upsert(entity: DailyActivityEntity)

    @Upsert
    suspend fun upsertAll(list: List<DailyActivityEntity>)
}
