package de.englischlernen.app.data.content

import kotlinx.serialization.Serializable

/** Ein GER-Niveau (A1 ... C2) mit seinen Lektionen. Wird aus assets/content/<id>.json geladen. */
@Serializable
data class Level(
    val id: String,
    val code: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val lessons: List<Lesson>,
)

@Serializable
data class Lesson(
    val id: String,
    val title: String,
    val goal: String,
    val theory: List<TheoryBlock> = emptyList(),
    val vocab: List<Vocab> = emptyList(),
    val sentences: List<Sentence> = emptyList(),
    val quiz: List<Quiz> = emptyList(),
    val gaps: List<Gap> = emptyList(),
    val dialogue: Dialogue? = null,
)

@Serializable
data class TheoryBlock(
    val heading: String,
    val text: String,
    val examples: List<Sentence> = emptyList(),
)

@Serializable
data class Vocab(
    val en: String,
    val de: String,
    /** Weitere akzeptierte englische Antworten. */
    val alt: List<String> = emptyList(),
    val example: String? = null,
    val exampleDe: String? = null,
)

@Serializable
data class Sentence(
    val en: String,
    val de: String,
    /** Weitere akzeptierte englische Uebersetzungen. */
    val alt: List<String> = emptyList(),
)

@Serializable
data class Quiz(
    val q: String,
    val options: List<String>,
    val answer: Int,
    val explain: String = "",
)

/** Lueckentext: [text] enthaelt genau einmal "___". */
@Serializable
data class Gap(
    val text: String,
    val answer: String,
    val alt: List<String> = emptyList(),
    val hint: String = "",
    val de: String = "",
)

/** Freie Sprechaufgabe: Nutzer antwortet in eigenen Worten, KI (oder App) bewertet. */
@Serializable
data class SpeakingPrompt(
    val id: String,
    val task: String,
    val question: String,
    val sample: String,
)

@Serializable
data class Dialogue(
    val title: String,
    val setting: String,
    val lines: List<Line>,
)

@Serializable
data class Line(
    val speaker: String,
    val en: String,
    val de: String,
)
