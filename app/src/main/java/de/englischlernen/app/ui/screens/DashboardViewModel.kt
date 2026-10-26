package de.englischlernen.app.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.englischlernen.app.AppContainer
import de.englischlernen.app.data.content.Level
import de.englischlernen.app.data.db.DailyActivityEntity
import de.englischlernen.app.data.db.ExamResultEntity
import de.englischlernen.app.data.db.LessonProgressEntity
import de.englischlernen.app.data.prefs.Settings
import de.englischlernen.app.domain.Achievement
import de.englischlernen.app.domain.AchievementInput
import de.englischlernen.app.domain.Achievements
import de.englischlernen.app.domain.Rank
import de.englischlernen.app.domain.Ranks
import de.englischlernen.app.domain.Streak
import de.englischlernen.app.ui.session.SessionMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** Empfehlung "Was soll ich als Naechstes tun?" */
data class Recommendation(val title: String, val subtitle: String, val mode: SessionMode, val arg: String)

data class Dashboard(
    val settings: Settings,
    val levels: List<Level>,
    val progress: Map<String, LessonProgressEntity>,
    val exams: List<ExamResultEntity>,
    val activity: List<DailyActivityEntity>,
    val today: Long,
    val vocabCount: Int,
    val mastered: Int,
    val due: Int,
    val weak: Int,
) {
    val todayXp: Int = activity.firstOrNull { it.epochDay == today }?.xp ?: 0
    val totalXp: Int = activity.sumOf { it.xp }
    val streak: Int = Streak.current(activity, today)
    val longestStreak: Int = Streak.longest(activity)
    val rank: Rank = Ranks.of(totalXp)
    val passedExams: Set<String> = exams.filter { it.passed }.map { it.levelId }.toSet()
    val accuracy: Int = activity.sumOf { it.total }.let { t -> if (t == 0) 0 else activity.sumOf { it.correct } * 100 / t }

    fun levelUnlocked(index: Int) = settings.freeMode || index <= settings.unlockedLevel

    fun lessonUnlocked(levelIndex: Int, lessonIndex: Int): Boolean {
        if (!levelUnlocked(levelIndex)) return false
        if (settings.freeMode || levelIndex < settings.unlockedLevel || lessonIndex == 0) return true
        val prev = levels[levelIndex].lessons[lessonIndex - 1]
        return progress[prev.id]?.passed == true
    }

    fun passedCount(level: Level) = level.lessons.count { progress[it.id]?.passed == true }

    fun examAvailable(levelIndex: Int): Boolean {
        if (!levelUnlocked(levelIndex)) return false
        val lv = levels[levelIndex]
        return settings.freeMode || levelIndex < settings.unlockedLevel || passedCount(lv) == lv.lessons.size
    }

    /** Aktuelles Arbeitsniveau: hoechstes freigeschaltetes. */
    val currentLevelIndex: Int = settings.unlockedLevel.coerceIn(0, (levels.size - 1).coerceAtLeast(0))

    val recommendation: Recommendation? = run {
        if (levels.isEmpty()) return@run null
        if (due >= 10) return@run Recommendation("Fundbuch wiederholen", "$due Vokabeln sind fällig – jetzt festigen", SessionMode.REVIEW, "")
        val li = currentLevelIndex
        val lv = levels[li]
        val next = lv.lessons.withIndex().firstOrNull { (i, l) -> progress[l.id]?.passed != true && lessonUnlocked(li, i) }
        when {
            next != null -> Recommendation(next.value.title, "${lv.code} · Lektion ${next.index + 1} von ${lv.lessons.size}", SessionMode.LESSON, next.value.id)
            lv.id !in passedExams -> Recommendation("Abschlussprüfung ${lv.code}", "Alle Lektionen geschafft – hol dir das Zertifikat", SessionMode.EXAM, lv.id)
            due > 0 -> Recommendation("Fundbuch wiederholen", "$due Vokabeln sind fällig", SessionMode.REVIEW, "")
            else -> Recommendation("Hörtraining", "Trainiere dein Ohr mit Sätzen aus deinen Lektionen", SessionMode.LISTENING, "")
        }
    }

    val achievements: List<Achievement> = Achievements.compute(
        AchievementInput(
            lessonsPassed = progress.values.count { it.passed },
            perfectLessons = progress.values.count { it.bestScore == 100 },
            longestStreak = longestStreak,
            vocabCount = vocabCount,
            masteredVocab = mastered,
            examsPassed = passedExams,
            spokenTotal = activity.sumOf { it.spoken },
            totalXp = totalXp,
        )
    )
}

class DashboardViewModel(c: AppContainer) : ViewModel() {
    private val levels = flow { emit(c.content.levels()) }
    private val counts = combine(c.progress.vocabCount, c.progress.masteredCount, c.progress.dueCount(), c.progress.weakCount) { a, b, d, w ->
        intArrayOf(a, b, d, w)
    }

    val state: StateFlow<Dashboard?> = combine(
        c.settings.settings, levels, c.progress.lessonProgress, c.progress.examResults,
        combine(c.progress.activity, counts) { a, n -> a to n },
    ) { s, lv, p, e, (act, n) ->
        Dashboard(s, lv, p.associateBy { it.lessonId }, e, act, c.progress.today(), n[0], n[1], n[2], n[3])
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
