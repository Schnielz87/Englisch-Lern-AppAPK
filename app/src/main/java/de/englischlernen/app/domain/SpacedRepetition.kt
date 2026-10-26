package de.englischlernen.app.domain

import de.englischlernen.app.data.db.VocabCardEntity
import java.util.concurrent.TimeUnit

/**
 * Leitner-System mit wachsenden Abstaenden. Richtige Antwort -> naechstes Fach,
 * falsche Antwort -> zurueck in Fach 1 und sofort wieder faellig.
 */
object SpacedRepetition {
    /** Wiederholungsabstand in Tagen je Fach (Index = Fach). */
    val intervalsDays = intArrayOf(0, 1, 3, 7, 14, 30, 60, 120)
    const val MAX_BOX = 7

    fun schedule(card: VocabCardEntity, correct: Boolean, now: Long): VocabCardEntity =
        if (correct) {
            val box = (card.box + 1).coerceAtMost(MAX_BOX)
            card.copy(box = box, dueAt = now + days(intervalsDays[box]), reviews = card.reviews + 1)
        } else {
            card.copy(box = 1, dueAt = now + TimeUnit.MINUTES.toMillis(10), lapses = card.lapses + 1, reviews = card.reviews + 1)
        }

    /** Neue Karte nach Abschluss einer Lektion: morgen erstmals faellig. */
    fun newCard(key: String, levelId: String, lessonId: String, en: String, de: String, now: Long) =
        VocabCardEntity(key, levelId, lessonId, en, de, box = 1, dueAt = now + days(1), lapses = 0, reviews = 0)

    private fun days(d: Int) = TimeUnit.DAYS.toMillis(d.toLong())
}
