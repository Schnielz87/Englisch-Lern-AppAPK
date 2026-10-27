package de.englischlernen.app.domain

import de.englischlernen.app.data.content.Dialogue
import de.englischlernen.app.data.content.Vocab

/**
 * Alle Uebungstypen. [key] identifiziert das Lernelement fuer Statistik und Wiederholung
 * (z. B. "v:a1_01:3" = Vokabel 3 aus Lektion a1_01). Nicht bewertete Karten haben key = null.
 */
sealed interface Exercise {
    val key: String?
    val scorable: Boolean get() = key != null

    /** Erklaerkarte mit Grammatik/Theorie. */
    data class Info(val title: String, val body: String, val examples: List<Pair<String, String>>) : Exercise {
        override val key: String? = null
    }

    /** Lernkarte zum Einpraegen einer Vokabel. */
    data class Flashcard(val vocab: Vocab) : Exercise {
        override val key: String? = null
    }

    /** Auswahlaufgabe. [speak] wird vorgelesen; bei [listening] wird der Prompt erst nach der Antwort gezeigt. */
    data class Choice(
        override val key: String,
        val instruction: String,
        val prompt: String,
        val options: List<String>,
        val correctIndex: Int,
        val explanation: String = "",
        val speak: String? = null,
        val listening: Boolean = false,
    ) : Exercise

    /** Freie Eingabe (Lueckentext, Uebersetzung, Diktat). */
    data class TypeIn(
        override val key: String,
        val instruction: String,
        val prompt: String,
        val accepted: List<String>,
        val hint: String = "",
        val speak: String? = null,
        val listening: Boolean = false,
        val explanation: String = "",
    ) : Exercise

    /** Satzbau: Woerter in die richtige Reihenfolge bringen. */
    data class Arrange(
        override val key: String,
        val prompt: String,
        val answer: String,
        val shuffled: List<String>,
    ) : Exercise

    /** Aussprache: Satz nachsprechen, Spracherkennung vergleicht. */
    data class Speak(
        override val key: String,
        val text: String,
        val translation: String,
    ) : Exercise

    /** Freies Sprechen: eigene Antwort auf eine Frage, Bewertung durch KI bzw. App. */
    data class FreeSpeak(
        override val key: String,
        val prompt: de.englischlernen.app.data.content.SpeakingPrompt,
        val level: String,
    ) : Exercise

    /** Rollenspiel-Dialog: Nutzer spricht die Zeilen von [role]. */
    data class DialoguePractice(
        override val key: String,
        val dialogue: Dialogue,
        val role: String,
    ) : Exercise
}

/** Ergebnis einer bewerteten Uebung. */
data class ExerciseResult(
    val exercise: Exercise,
    val correct: Boolean,
    val given: String,
    val expected: String,
)
