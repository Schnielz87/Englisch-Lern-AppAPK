package de.englischlernen.app.domain

import de.englischlernen.app.data.db.DailyActivityEntity

/** Punkte ("Fundpunkte") fuer Lernaktivitaeten. */
object Xp {
    const val CORRECT = 10
    const val REVIEW_CORRECT = 5
    const val LESSON_BONUS = 20
    const val PERFECT_BONUS = 15
    const val EXAM_PASSED = 50
}

/** Expeditionsrang abhaengig von gesammelten Punkten. */
data class Rank(val title: String, val minXp: Int, val nextXp: Int?)

object Ranks {
    private val table = listOf(
        "Schatzsucher-Lehrling" to 0,
        "Spurenleser" to 250,
        "Fundstück-Jäger" to 750,
        "Expeditionsleiter" to 2000,
        "Schatzmeister" to 5000,
        "Legende der Fundbrüder" to 10000,
    )

    fun of(xp: Int): Rank {
        val idx = table.indexOfLast { xp >= it.second }.coerceAtLeast(0)
        return Rank(table[idx].first, table[idx].second, table.getOrNull(idx + 1)?.second)
    }
}

object Streak {
    /** Aufeinanderfolgende Lerntage, die heute oder gestern enden. */
    fun current(days: List<DailyActivityEntity>, today: Long): Int {
        val active = days.filter { it.xp > 0 }.map { it.epochDay }.toSet()
        var day = if (today in active) today else today - 1
        var count = 0
        while (day in active) { count++; day-- }
        return count
    }

    fun longest(days: List<DailyActivityEntity>): Int {
        val sorted = days.filter { it.xp > 0 }.map { it.epochDay }.sorted()
        var best = 0; var run = 0; var prev = Long.MIN_VALUE
        for (d in sorted) {
            run = if (d == prev + 1) run + 1 else 1
            best = maxOf(best, run); prev = d
        }
        return best
    }
}

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val unlocked: Boolean,
    val progress: Float,
)

data class AchievementInput(
    val lessonsPassed: Int,
    val perfectLessons: Int,
    val longestStreak: Int,
    val vocabCount: Int,
    val masteredVocab: Int,
    val examsPassed: Set<String>,
    val spokenTotal: Int,
    val totalXp: Int,
)

/** "Schatzkammer": Erfolge, die aus dem gespeicherten Fortschritt berechnet werden. */
object Achievements {
    fun compute(i: AchievementInput): List<Achievement> {
        fun a(id: String, t: String, d: String, cur: Int, goal: Int) =
            Achievement(id, t, d, cur >= goal, (cur.toFloat() / goal).coerceIn(0f, 1f))
        val levels = listOf("a1", "a2", "b1", "b2", "c1", "c2")
        return listOf(
            a("first_lesson", "Erster Fund", "Schließe deine erste Lektion ab.", i.lessonsPassed, 1),
            a("lessons_10", "Fleißiger Sucher", "Schließe 10 Lektionen ab.", i.lessonsPassed, 10),
            a("lessons_30", "Unermüdlicher Entdecker", "Schließe 30 Lektionen ab.", i.lessonsPassed, 30),
            a("perfect", "Goldnugget", "Beende eine Lektion fehlerfrei.", i.perfectLessons, 1),
            a("perfect_10", "Goldader", "10 fehlerfreie Lektionen.", i.perfectLessons, 10),
            a("streak_3", "Auf der Spur", "3 Tage in Folge gelernt.", i.longestStreak, 3),
            a("streak_7", "Wochen-Expedition", "7 Tage in Folge gelernt.", i.longestStreak, 7),
            a("streak_30", "Monats-Expedition", "30 Tage in Folge gelernt.", i.longestStreak, 30),
            a("vocab_50", "Fundbuch gefüllt", "50 Vokabeln im Fundbuch.", i.vocabCount, 50),
            a("vocab_mastered_100", "Wortschatz-Hüter", "100 Vokabeln sicher beherrscht.", i.masteredVocab, 100),
            a("speak_20", "Mutige Stimme", "20 Sätze laut gesprochen.", i.spokenTotal, 20),
            a("xp_1000", "Schatztruhe", "1.000 Fundpunkte gesammelt.", i.totalXp, 1000),
        ) + levels.map { id ->
            val code = id.uppercase()
            a("exam_$id", "Zertifikat $code", "Bestehe die Abschlussprüfung $code.", if (id in i.examsPassed) 1 else 0, 1)
        }
    }
}
