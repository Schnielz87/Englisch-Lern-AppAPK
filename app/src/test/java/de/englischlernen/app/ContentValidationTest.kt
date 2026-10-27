package de.englischlernen.app

import de.englischlernen.app.data.content.ContentRepository
import de.englischlernen.app.data.content.Level
import de.englischlernen.app.domain.AnswerChecker
import de.englischlernen.app.domain.Exercise
import de.englischlernen.app.domain.ExerciseGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Prueft alle Lerninhalte – so fallen Tippfehler in den JSON-Dateien sofort im Build auf. */
class ContentValidationTest {

    private val levels: List<Level> = ContentRepository.LEVEL_IDS.map { id ->
        ContentRepository.parse(File("src/main/assets/content/$id.json").readText())
    }

    @Test
    fun allSixLevelsPresent() {
        assertEquals(listOf("A1", "A2", "B1", "B2", "C1", "C2"), levels.map { it.code })
        levels.forEach { assertTrue("${it.code} braucht Lektionen", it.lessons.size >= 5) }
    }

    @Test
    fun speakingPromptsForEveryLevel() {
        val sp = ContentRepository.parseSpeaking(File("src/main/assets/content/speaking.json").readText())
        ContentRepository.LEVEL_IDS.forEach { id ->
            val list = sp[id].orEmpty()
            assertTrue("$id: zu wenige Sprechaufgaben", list.size >= 4)
            list.forEach { assertTrue("${it.id}: unvollstaendig", it.task.isNotBlank() && it.question.isNotBlank() && it.sample.isNotBlank()) }
        }
        val ids = sp.values.flatten().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun helpContentIsValid() {
        val help = de.englischlernen.app.ui.help.HelpContent.parse(File("src/main/assets/content/help.json").readText())
        assertTrue("zu wenige Hilfe-Kapitel", help.size >= 8)
        help.forEach { assertTrue("${it.id}: leer", it.blocks.isNotEmpty()) }
        assertEquals(help.size, help.map { it.id }.toSet().size)
    }

    @Test
    fun lessonIdsUnique() {
        val ids = levels.flatMap { l -> l.lessons.map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun lessonsAreComplete() {
        levels.flatMap { it.lessons }.forEach { l ->
            assertTrue("${l.id}: Theorie fehlt", l.theory.isNotEmpty())
            assertTrue("${l.id}: zu wenige Vokabeln", l.vocab.size >= 6)
            assertTrue("${l.id}: zu wenige Saetze", l.sentences.size >= 4)
            assertTrue("${l.id}: Quiz fehlt", l.quiz.isNotEmpty())
            assertTrue("${l.id}: Luecken fehlen", l.gaps.isNotEmpty())
            assertTrue("${l.id}: Dialog fehlt", l.dialogue != null && l.dialogue.lines.size >= 4)
        }
    }

    @Test
    fun quizzesAreValid() {
        levels.flatMap { it.lessons }.forEach { l ->
            l.quiz.forEachIndexed { i, q ->
                assertTrue("${l.id} Quiz $i: Antwortindex", q.answer in q.options.indices)
                assertEquals("${l.id} Quiz $i: doppelte Optionen", q.options.size, q.options.toSet().size)
                assertTrue("${l.id} Quiz $i: mind. 3 Optionen", q.options.size >= 3)
            }
        }
    }

    @Test
    fun gapsHaveExactlyOnePlaceholder() {
        levels.flatMap { it.lessons }.forEach { l ->
            l.gaps.forEachIndexed { i, g ->
                assertEquals("${l.id} Luecke $i: genau ein ___", 1, Regex("___").findAll(g.text).count())
                assertTrue("${l.id} Luecke $i: Antwort leer", g.answer.isNotBlank())
            }
        }
    }

    @Test
    fun vocabUniquePerLesson() {
        levels.flatMap { it.lessons }.forEach { l ->
            val en = l.vocab.map { it.en.lowercase() }
            assertEquals("${l.id}: doppelte Vokabeln", en.size, en.toSet().size)
        }
    }

    @Test
    fun acceptedAnswersMatchThemselves() {
        levels.flatMap { it.lessons }.forEach { l ->
            l.sentences.forEach { s ->
                (listOf(s.en) + s.alt).forEach {
                    assertEquals("${l.id}: $it", AnswerChecker.Verdict.CORRECT, AnswerChecker.check(it, listOf(s.en) + s.alt))
                }
            }
        }
    }

    @Test
    fun generatedExercisesAreConsistent() {
        val gen = ExerciseGenerator(Random(42))
        levels.forEach { level ->
            level.lessons.forEach { lesson ->
                listOf(0.4, 0.7, 0.9).forEach { skill ->
                    val sp = ContentRepository.parseSpeaking(File("src/main/assets/content/speaking.json").readText())[level.id]!!.first()
                    val list = gen.lesson(level, lesson, skill, freeSpeak = sp)
                    assertTrue("${lesson.id}: freie Sprechaufgabe fehlt", list.any { it is Exercise.FreeSpeak })
                    assertTrue("${lesson.id}: keine Uebungen", list.count { it.scorable } >= 10)
                    list.forEach { ex -> checkExercise(lesson.id, ex) }
                }
            }
            val exam = gen.exam(level)
            assertEquals("${level.code}: Pruefungsgroesse", 20, exam.size)
            exam.forEach { checkExercise(level.id, it) }
            gen.placementBlock(level).forEach { checkExercise(level.id, it) }
        }
    }

    private fun checkExercise(where: String, ex: Exercise) {
        when (ex) {
            is Exercise.Choice -> {
                assertTrue("$where: Auswahl-Index", ex.correctIndex in ex.options.indices)
                assertEquals("$where: doppelte Optionen in ${ex.prompt}", ex.options.size, ex.options.toSet().size)
                assertTrue("$where: zu wenige Optionen", ex.options.size >= 2)
            }
            is Exercise.Arrange -> assertEquals(
                AnswerChecker.Verdict.CORRECT,
                AnswerChecker.check(ex.shuffled.sorted().joinToString(" "), listOf(ex.answer.split(" ").sorted().joinToString(" "))),
            )
            is Exercise.TypeIn -> assertTrue("$where: keine Loesung", ex.accepted.isNotEmpty())
            else -> {}
        }
    }
}
