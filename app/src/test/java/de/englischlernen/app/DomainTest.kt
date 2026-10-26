package de.englischlernen.app

import de.englischlernen.app.data.db.DailyActivityEntity
import de.englischlernen.app.data.db.VocabCardEntity
import de.englischlernen.app.domain.AnswerChecker
import de.englischlernen.app.domain.AnswerChecker.Verdict
import de.englischlernen.app.domain.Ranks
import de.englischlernen.app.domain.SpacedRepetition
import de.englischlernen.app.domain.Streak
import de.englischlernen.app.update.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {

    @Test
    fun answerCheckerIsTolerant() {
        assertEquals(Verdict.CORRECT, AnswerChecker.check("i'm fine, thanks", listOf("I am fine, thanks.")))
        assertEquals(Verdict.CORRECT, AnswerChecker.check("  Hello!! ", listOf("hello")))
        assertEquals(Verdict.CORRECT, AnswerChecker.check("He doesn’t like coffee", listOf("He does not like coffee.")))
        assertEquals(Verdict.TYPO, AnswerChecker.check("umbrela", listOf("umbrella")))
        assertEquals(Verdict.WRONG, AnswerChecker.check("cat", listOf("dog")))
        assertEquals(Verdict.WRONG, AnswerChecker.check("", listOf("dog")))
        // kurze Woerter: kein Tippfehler-Bonus
        assertEquals(Verdict.WRONG, AnswerChecker.check("in", listOf("on")))
    }

    @Test
    fun speechSimilarity() {
        assertEquals(1.0, AnswerChecker.speechSimilarity("thank you very much", "Thank you very much."), 0.001)
        assertTrue(AnswerChecker.speechSimilarity("thank you much", "Thank you very much.") in 0.5..0.9)
        assertTrue(AnswerChecker.speechSimilarity("banana", "Thank you very much.") < 0.2)
    }

    @Test
    fun spacedRepetitionMovesBoxes() {
        val now = 1_000_000L
        val card = VocabCardEntity("v:x:0", "a1", "x", "hello", "hallo", box = 1, dueAt = now, lapses = 0, reviews = 0)
        val up = SpacedRepetition.schedule(card, correct = true, now = now)
        assertEquals(2, up.box)
        assertTrue(up.dueAt > now)
        val down = SpacedRepetition.schedule(up.copy(box = 5), correct = false, now = now)
        assertEquals(1, down.box)
        assertEquals(1, down.lapses)
        val max = SpacedRepetition.schedule(card.copy(box = SpacedRepetition.MAX_BOX), true, now)
        assertEquals(SpacedRepetition.MAX_BOX, max.box)
    }

    @Test
    fun streakCounting() {
        val days = listOf(10L, 9L, 8L, 5L).map { DailyActivityEntity(it, xp = 10) }
        assertEquals(3, Streak.current(days, today = 10))
        assertEquals(3, Streak.current(days, today = 11)) // gestern zaehlt noch
        assertEquals(0, Streak.current(days, today = 13))
        assertEquals(3, Streak.longest(days))
    }

    @Test
    fun ranks() {
        assertEquals(0, Ranks.of(0).minXp)
        assertEquals(250, Ranks.of(300).minXp)
        assertEquals(null, Ranks.of(50_000).nextXp)
    }

    @Test
    fun versionComparison() {
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0.0"))
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.9"))
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("0.9", "1.0.0"))
    }
}
