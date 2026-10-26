package de.englischlernen.app.ui.session

import de.englischlernen.app.AppContainer
import de.englischlernen.app.data.content.Level
import de.englischlernen.app.data.content.Vocab
import de.englischlernen.app.domain.Exercise
import de.englischlernen.app.domain.ExerciseGenerator
import de.englischlernen.app.domain.ExerciseResult
import de.englischlernen.app.domain.Keys
import de.englischlernen.app.domain.Xp
import kotlinx.serialization.Serializable

/** Alle Arten von Lerneinheiten, die der Sitzungs-Bildschirm abspielen kann. */
@Serializable
enum class SessionMode { LESSON, EXAM, PLACEMENT, REVIEW, WEAK, LISTENING, SPEAKING, DIALOGUE }

/** Zusammenfassung nach Abschluss einer Sitzung. */
data class SessionSummary(
    val title: String,
    val scorePercent: Int,
    val correct: Int,
    val total: Int,
    val xp: Int,
    val headline: String,
    val message: String,
    val passed: Boolean?,
    val mistakes: List<ExerciseResult>,
    val nextLessonId: String? = null,
)

/** Strategie je Modus: erzeugt Aufgaben und verarbeitet das Ergebnis. */
interface SessionPlan {
    val title: String
    /** In Pruefungen wird erst am Ende ausgewertet. */
    val showFeedback: Boolean get() = true
    val isReview: Boolean get() = false
    val xpPerCorrect: Int get() = Xp.CORRECT

    suspend fun initial(): List<Exercise>

    /** Wird aufgerufen, wenn alle bisherigen Aufgaben erledigt sind; liefert ggf. weitere. */
    suspend fun more(results: List<ExerciseResult>): List<Exercise>? = null

    suspend fun finish(results: List<ExerciseResult>, baseXp: Int): SessionSummary
}

fun percent(results: List<ExerciseResult>): Int =
    if (results.isEmpty()) 100 else results.count { it.correct } * 100 / results.size

object SessionPlans {

    suspend fun create(mode: SessionMode, arg: String, c: AppContainer): SessionPlan {
        val levels = c.content.levels()
        val gen = ExerciseGenerator()
        val settings = c.settings.current()
        val unlocked = if (settings.freeMode) levels else levels.take(settings.unlockedLevel + 1)
        return when (mode) {
            SessionMode.LESSON -> LessonPlan(c, gen, arg)
            SessionMode.EXAM -> ExamPlan(c, gen, levels.first { it.id == arg }, levels)
            SessionMode.PLACEMENT -> PlacementPlan(c, gen, levels)
            SessionMode.REVIEW -> ReviewPlan(c, gen, levels)
            SessionMode.WEAK -> SimplePlan("Schwachstellen-Training") {
                c.progress.weakestKeys(15).mapNotNull { gen.forKey(it, levels) }
                    .filter { it !is Exercise.DialoguePractice }
            }
            SessionMode.LISTENING -> SimplePlan("Hörtraining") {
                val pool = unlocked.flatMap { lv -> lv.lessons.flatMap { l -> l.sentences.mapIndexed { i, s -> Keys.sentence(l.id, i) to s } } }
                gen.listening(pool.shuffled().take(10), pool.map { it.second })
            }
            SessionMode.SPEAKING -> SimplePlan("Aussprache-Training") {
                val pool = unlocked.flatMap { lv -> lv.lessons.flatMap { l -> l.sentences.mapIndexed { i, s -> Keys.sentence(l.id, i) to s } } }
                gen.speaking(pool.shuffled().take(8))
            }
            SessionMode.DIALOGUE -> {
                val (_, lesson) = c.content.lesson(arg) ?: error("Lektion $arg fehlt")
                SimplePlan("Gespräch: ${lesson.dialogue?.title ?: lesson.title}") { listOfNotNull(gen.dialogue(lesson.id, lesson)) }
            }
        }
    }
}

private class SimplePlan(override val title: String, private val build: suspend () -> List<Exercise>) : SessionPlan {
    override suspend fun initial() = build()
    override suspend fun finish(results: List<ExerciseResult>, baseXp: Int): SessionSummary {
        val p = percent(results)
        return SessionSummary(
            title, p, results.count { it.correct }, results.size, baseXp,
            headline = if (p >= 80) "Stark gemacht!" else "Training abgeschlossen",
            message = if (p >= 80) "Deine Funde sitzen. Weiter so!" else "Übung macht den Meister – die Fehler kommen bald wieder dran.",
            passed = null, mistakes = results.filter { !it.correct },
        )
    }
}

private class LessonPlan(val c: AppContainer, val gen: ExerciseGenerator, val lessonId: String) : SessionPlan {
    override var title: String = "Lektion"
    private lateinit var level: Level

    override suspend fun initial(): List<Exercise> {
        val (lv, lesson) = c.content.lesson(lessonId) ?: error("Lektion $lessonId fehlt")
        level = lv
        title = lesson.title
        val skill = c.progress.skill(lv.id)
        // Faellige bzw. schwache Vokabeln aus frueheren Lektionen einstreuen (adaptiv).
        val levels = c.content.levels()
        val review = c.progress.dueCards(3).mapNotNull { card ->
            vocabFor(card.key, levels)?.let { card.key to it }
        }
        return gen.lesson(lv, lesson, skill, review)
    }

    override suspend fun finish(results: List<ExerciseResult>, baseXp: Int): SessionSummary {
        val lesson = level.lessons.first { it.id == lessonId }
        val p = percent(results)
        val passed = p >= PASS
        c.progress.completeLesson(level, lesson, p, passed)
        var xp = baseXp
        if (passed) xp += Xp.LESSON_BONUS
        if (p == 100) xp += Xp.PERFECT_BONUS
        val idx = level.lessons.indexOf(lesson)
        val next = level.lessons.getOrNull(idx + 1)?.id
        return SessionSummary(
            lesson.title, p, results.count { it.correct }, results.size, xp,
            headline = when {
                p == 100 -> "Goldnugget! Fehlerfrei!"
                passed -> "Lektion geschafft!"
                else -> "Fast geschafft"
            },
            message = when {
                passed && next == null -> "Alle Lektionen von ${level.code} erledigt? Dann wartet die Abschlussprüfung."
                passed -> "Die Vokabeln liegen jetzt in deinem Fundbuch und kommen zur Wiederholung."
                else -> "Für den Abschluss brauchst du $PASS %. Wiederhole die Lektion – du kennst jetzt schon mehr!"
            },
            passed = passed, mistakes = results.filter { !it.correct }, nextLessonId = if (passed) next else null,
        )
    }

    companion object { const val PASS = 60 }
}

private class ExamPlan(val c: AppContainer, val gen: ExerciseGenerator, val level: Level, val levels: List<Level>) : SessionPlan {
    override val title = "Abschlussprüfung ${level.code}"
    override val showFeedback = false
    override suspend fun initial() = gen.exam(level)

    override suspend fun finish(results: List<ExerciseResult>, baseXp: Int): SessionSummary {
        val p = percent(results)
        val passed = p >= PASS
        c.progress.saveExam(level.id, p, passed)
        val idx = levels.indexOf(level)
        if (passed) c.settings.unlockLevel(idx + 1)
        val nextCode = levels.getOrNull(idx + 1)?.code
        return SessionSummary(
            title, p, results.count { it.correct }, results.size, baseXp + if (passed) Xp.EXAM_PASSED else 0,
            headline = if (passed) "Zertifikat ${level.code} erhalten!" else "Noch nicht bestanden",
            message = if (passed) {
                if (nextCode != null) "Herzlichen Glückwunsch! Die Expedition $nextCode ist jetzt freigeschaltet." else "Du hast die höchste Stufe gemeistert. Respekt!"
            } else "Zum Bestehen brauchst du $PASS %. Trainiere deine Schwachstellen und versuche es erneut.",
            passed = passed, mistakes = results.filter { !it.correct },
        )
    }

    companion object { const val PASS = 70 }
}

/** Adaptiver Einstufungstest: Steigt Niveau fuer Niveau auf, solange >= 4 von 5 richtig sind. */
private class PlacementPlan(val c: AppContainer, val gen: ExerciseGenerator, val levels: List<Level>) : SessionPlan {
    override val title = "Einstufungstest"
    override val showFeedback = false
    private var stage = 0
    private var placed = 0

    override suspend fun initial() = gen.placementBlock(levels[0])

    override suspend fun more(results: List<ExerciseResult>): List<Exercise>? {
        val block = results.takeLast(BLOCK)
        if (block.count { it.correct } >= 4 && stage < levels.lastIndex) {
            stage++
            placed = stage
            return gen.placementBlock(levels[stage])
        }
        if (block.count { it.correct } >= 4) placed = stage
        return null
    }

    override suspend fun finish(results: List<ExerciseResult>, baseXp: Int): SessionSummary {
        c.settings.unlockLevel(placed)
        val code = levels[placed].code
        return SessionSummary(
            title, percent(results), results.count { it.correct }, results.size, baseXp,
            headline = "Dein Startniveau: $code",
            message = "${levels[placed].title}. Alle Stufen bis $code sind freigeschaltet. Du kannst jederzeit auch leichtere Lektionen wiederholen.",
            passed = null, mistakes = emptyList(),
        )
    }

    companion object { const val BLOCK = 5 }
}

private class ReviewPlan(val c: AppContainer, val gen: ExerciseGenerator, val levels: List<Level>) : SessionPlan {
    override val title = "Fundbuch-Wiederholung"
    override val isReview = true
    override val xpPerCorrect = Xp.REVIEW_CORRECT

    override suspend fun initial(): List<Exercise> {
        val cards = c.progress.dueCards(20)
        val pool = levels.flatMap { lv -> lv.lessons.flatMap { it.vocab } }
        return gen.review(cards.mapNotNull { card -> vocabFor(card.key, levels)?.let { Triple(card.key, it, card.box) } }, pool)
    }

    override suspend fun finish(results: List<ExerciseResult>, baseXp: Int): SessionSummary {
        val p = percent(results)
        return SessionSummary(
            title, p, results.count { it.correct }, results.size, baseXp,
            headline = "Wiederholung erledigt",
            message = "Richtige Vokabeln wandern ins nächste Fach und kommen später wieder. Falsche üben wir bald erneut.",
            passed = null, mistakes = results.filter { !it.correct },
        )
    }
}

private fun vocabFor(key: String, levels: List<Level>): Vocab? {
    val parts = key.split(':')
    if (parts.size != 3 || parts[0] != "v") return null
    val lesson = levels.flatMap { it.lessons }.firstOrNull { it.id == parts[1] } ?: return null
    return lesson.vocab.getOrNull(parts[2].toIntOrNull() ?: return null)
}

