package de.englischlernen.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        LessonProgressEntity::class,
        VocabCardEntity::class,
        ItemStatEntity::class,
        ExamResultEntity::class,
        DailyActivityEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lessonProgress(): LessonProgressDao
    abstract fun vocabCards(): VocabCardDao
    abstract fun itemStats(): ItemStatDao
    abstract fun examResults(): ExamResultDao
    abstract fun dailyActivity(): DailyActivityDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "englisch_lernen.db")
                // Kuenftige Schema-Aenderungen bitte mit Migrationen versehen,
                // damit Lernfortschritt bei Updates erhalten bleibt.
                .build()
    }
}
