package de.englischlernen.app.domain

import de.englischlernen.app.data.content.Gap
import de.englischlernen.app.data.content.Lesson
import de.englischlernen.app.data.content.Level
import de.englischlernen.app.data.content.Quiz
import de.englischlernen.app.data.content.Sentence
import de.englischlernen.app.data.content.Vocab
import kotlin.random.Random

/** Schluessel fuer Lernelemente. */
object Keys {
    fun vocab(lessonId: String, i: Int) = "v:$lessonId:$i"
    fun quiz(lessonId: String, i: Int) = "q:$lessonId:$i"
    fun gap(lessonId: String, i: Int) = "g:$lessonId:$i"
    fun sentence(lessonId: String, i: Int) = "s:$lessonId:$i"
    fun dialogue(lessonId: String) = "d:$lessonId"
}

/**
 * Erzeugt abwechslungsreiche Uebungsfolgen aus den Inhalten.
 * Die Mischung passt sich dem Koennen ([skill] 0..1) an: Wer sicher ist, bekommt mehr
 * freie Eingaben (aktive Sprachproduktion), wer Schwierigkeiten hat, mehr Wiedererkennung.
 */
class ExerciseGenerator(private val random: Random = Random.Default) {

    // ---------- Lektion ----------

    fun lesson(
        level: Level,
        lesson: Lesson,
        skill: Double,
        reviewItems: List<Pair<String, Vocab>> = emptyList(),
    ): List<Exercise> {
        val pool = level.lessons.flatMap { it.vocab }
        val intro = mutableListOf<Exercise>()
        lesson.theory.forEach { t -> intro += Exercise.Info(t.heading, t.text, t.examples.map { it.en to it.de }) }
        lesson.vocab.forEach { intro += Exercise.Flashcard(it) }

        val practice = mutableListOf<Exercise>()
        val strong = skill >= 0.8
        val weak = skill < 0.6

        lesson.vocab.forEachIndexed { i, v ->
            val key = Keys.vocab(lesson.id, i)
            practice += when {
                i % 3 == 2 -> vocabListening(key, v, pool)
                strong && i % 2 == 1 -> vocabType(key, v)
                i % 2 == 1 -> vocabChoice(key, v, pool, toEnglish = false)
                else -> vocabChoice(key, v, pool, toEnglish = true)
            }
        }
        // Schwache Lerner: Vokabeln zusaetzlich nochmal schriftlich abfragen.
        if (!weak) lesson.vocab.indices.shuffled(random).take(2).forEach { i ->
            practice += vocabType(Keys.vocab(lesson.id, i), lesson.vocab[i])
        }
        lesson.quiz.forEachIndexed { i, q -> practice += quizChoice(Keys.quiz(lesson.id, i), q) }
        lesson.gaps.forEachIndexed { i, g -> practice += gapType(Keys.gap(lesson.id, i), g) }

        lesson.sentences.forEachIndexed { i, s ->
            val key = Keys.sentence(lesson.id, i)
            practice += when (i % 3) {
                0 -> arrangeOrChoice(key, s, lesson.sentences)
                1 -> if (weak) arrangeOrChoice(key, s, lesson.sentences) else translateOrArrange(key, s, lesson.sentences)
                else -> dictationOrChoice(key, s, lesson.sentences)
            }
        }
        // Wiederholung frueherer Schwachstellen in die Lektion einstreuen.
        reviewItems.take(3).forEach { (key, v) -> practice += vocabChoice(key, v, pool, toEnglish = random.nextBoolean()) }
        practice.shuffle(random)

        val speaking = lesson.sentences.indices.shuffled(random).take(if (strong) 3 else 2).map { i ->
            Exercise.Speak(Keys.sentence(lesson.id, i), lesson.sentences[i].en, lesson.sentences[i].de)
        }
        val dialogue = lesson.dialogue?.let {
            listOf(Exercise.DialoguePractice(Keys.dialogue(lesson.id), it, roleFor(it)))
        } ?: emptyList()

        return intro + practice + speaking + dialogue
    }

    // ---------- Pruefung ----------

    /** Abschlusspruefung eines Niveaus mit ca. [size] Aufgaben aus allen Lektionen. */
    fun exam(level: Level, size: Int = 20): List<Exercise> {
        val pool = level.lessons.flatMap { it.vocab }
        val vocab = level.lessons.flatMap { l -> l.vocab.mapIndexed { i, v -> Keys.vocab(l.id, i) to v } }.shuffled(random)
        val quiz = level.lessons.flatMap { l -> l.quiz.mapIndexed { i, q -> Keys.quiz(l.id, i) to q } }.shuffled(random)
        val gaps = level.lessons.flatMap { l -> l.gaps.mapIndexed { i, g -> Keys.gap(l.id, i) to g } }.shuffled(random)
        val sentences = level.lessons.flatMap { l -> l.sentences.mapIndexed { i, s -> Triple(Keys.sentence(l.id, i), s, l.sentences) } }.shuffled(random)

        val out = mutableListOf<Exercise>()
        vocab.take(5).forEachIndexed { i, (k, v) -> out += vocabChoice(k, v, pool, toEnglish = i % 2 == 0) }
        vocab.drop(5).take(1).forEach { (k, v) -> out += vocabListening(k, v, pool) }
        quiz.take(6).forEach { (k, q) -> out += quizChoice(k, q) }
        gaps.take(4).forEach { (k, g) -> out += gapType(k, g) }
        sentences.take(2).forEach { (k, s, all) -> out += arrangeOrChoice(k, s, all) }
        sentences.drop(2).take(1).forEach { (k, s, all) -> out += dictationOrChoice(k, s, all) }
        sentences.drop(3).take(1).forEach { (k, s, all) -> out += translateOrArrange(k, s, all) }
        return out.shuffled(random).take(size)
    }

    // ---------- Einstufungstest ----------

    /** Fuenf Auswahlaufgaben pro Niveau fuer den adaptiven Einstufungstest. */
    fun placementBlock(level: Level): List<Exercise> {
        val pool = level.lessons.flatMap { it.vocab }
        val quiz = level.lessons.flatMap { l -> l.quiz.mapIndexed { i, q -> Keys.quiz(l.id, i) to q } }.shuffled(random)
        val vocab = level.lessons.flatMap { l -> l.vocab.mapIndexed { i, v -> Keys.vocab(l.id, i) to v } }.shuffled(random)
        val out = quiz.take(3).map { (k, q) -> quizChoice(k, q) } +
            vocab.take(2).map { (k, v) -> vocabChoice(k, v, pool, toEnglish = true) }
        return out.map { it.copy(instruction = "${level.code}: ${it.instruction}") }
    }

    // ---------- Training ----------

    /** Vokabel-Wiederholung: Abfrageart steigt mit dem Leitner-Fach. */
    fun review(cards: List<Triple<String, Vocab, Int>>, pool: List<Vocab>): List<Exercise> =
        cards.map { (key, v, box) ->
            when {
                box <= 1 -> vocabChoice(key, v, pool, toEnglish = random.nextBoolean())
                box == 2 -> vocabListening(key, v, pool)
                else -> vocabType(key, v)
            }
        }.shuffled(random)

    fun listening(items: List<Pair<String, Sentence>>, pool: List<Sentence>): List<Exercise> =
        items.mapIndexed { i, (k, s) ->
            if (i % 2 == 0) dictationOrChoice(k, s, pool) else listeningChoice(k, s, pool)
        }

    fun speaking(items: List<Pair<String, Sentence>>): List<Exercise> =
        items.map { (k, s) -> Exercise.Speak(k, s.en, s.de) }

    /** Erzeugt fuer einen gespeicherten Schluessel wieder eine passende Uebung. */
    fun forKey(key: String, levels: List<Level>): Exercise? {
        val parts = key.split(':')
        if (parts.size < 2) return null
        val lesson = levels.flatMap { it.lessons }.firstOrNull { it.id == parts[1] } ?: return null
        val level = levels.first { lv -> lv.lessons.any { it.id == lesson.id } }
        val idx = parts.getOrNull(2)?.toIntOrNull()
        return when (parts[0]) {
            "v" -> lesson.vocab.getOrNull(idx ?: -1)?.let {
                if (random.nextBoolean()) vocabType(key, it) else vocabChoice(key, it, level.lessons.flatMap { l -> l.vocab }, true)
            }
            "q" -> lesson.quiz.getOrNull(idx ?: -1)?.let { quizChoice(key, it) }
            "g" -> lesson.gaps.getOrNull(idx ?: -1)?.let { gapType(key, it) }
            "s" -> lesson.sentences.getOrNull(idx ?: -1)?.let { translateOrArrange(key, it, lesson.sentences) }
            "d" -> lesson.dialogue?.let { Exercise.DialoguePractice(key, it, roleFor(it)) }
            else -> null
        }
    }

    fun dialogue(lessonId: String, lesson: Lesson): Exercise? =
        lesson.dialogue?.let { Exercise.DialoguePractice(Keys.dialogue(lessonId), it, roleFor(it)) }

    // ---------- Bausteine ----------

    private fun roleFor(d: de.englischlernen.app.data.content.Dialogue): String {
        val speakers = d.lines.map { it.speaker }.distinct()
        // Nutzer spielt "You", sonst die zweite Rolle (antwortet meist).
        return speakers.firstOrNull { it.equals("You", ignoreCase = true) } ?: speakers.getOrElse(1) { speakers.first() }
    }

    /** Ablenker mit eindeutig unterschiedlicher Anzeige (keine doppelten Antwortoptionen). */
    private fun <T> distractors(pool: List<T>, correct: T, shown: (T) -> String, other: (T) -> String): List<T> =
        pool.asSequence()
            .filter { shown(it).lowercase() != shown(correct).lowercase() && other(it).lowercase() != other(correct).lowercase() }
            .distinctBy { shown(it).lowercase() }
            .toList().shuffled(random).take(3)

    fun vocabChoice(key: String, v: Vocab, pool: List<Vocab>, toEnglish: Boolean): Exercise.Choice {
        val distractors = if (toEnglish) distractors(pool, v, { it.en }, { it.de }) else distractors(pool, v, { it.de }, { it.en })
        val opts = (distractors + v).shuffled(random)
        return if (toEnglish) Exercise.Choice(
            key, "Was heißt das auf Englisch?", v.de,
            opts.map { it.en }, opts.indexOf(v), explanation = v.example?.let { "Beispiel: $it" } ?: "",
        ) else Exercise.Choice(
            key, "Was bedeutet das?", v.en,
            opts.map { it.de }, opts.indexOf(v), speak = v.en,
            explanation = v.example?.let { "Beispiel: $it" } ?: "",
        )
    }

    fun vocabListening(key: String, v: Vocab, pool: List<Vocab>): Exercise.Choice {
        val distractors = distractors(pool, v, { it.de }, { it.en })
        val opts = (distractors + v).shuffled(random)
        return Exercise.Choice(
            key, "Hör zu: Was hast du gehört?", v.en, opts.map { it.de }, opts.indexOf(v),
            speak = v.en, listening = true,
        )
    }

    fun vocabType(key: String, v: Vocab) = Exercise.TypeIn(
        key, "Schreibe auf Englisch:", v.de, listOf(v.en) + v.alt,
        hint = "Beginnt mit „${v.en.take(1)}“ (${v.en.length} Zeichen)",
        explanation = v.example?.let { "Beispiel: $it" } ?: "",
    )

    fun quizChoice(key: String, q: Quiz): Exercise.Choice {
        val order = q.options.indices.shuffled(random)
        return Exercise.Choice(
            key, "Wähle die richtige Antwort:", q.q,
            order.map { q.options[it] }, order.indexOf(q.answer), explanation = q.explain,
        )
    }

    fun gapType(key: String, g: Gap) = Exercise.TypeIn(
        key, "Ergänze die Lücke:", g.text, listOf(g.answer) + g.alt,
        hint = g.hint, explanation = g.de,
    )

    private fun arrangeOrChoice(key: String, s: Sentence, pool: List<Sentence>): Exercise {
        val tokens = AnswerChecker.tokens(s.en)
        if (tokens.size !in 3..14) return translationChoice(key, s, pool)
        var shuffled = tokens.shuffled(random)
        var guard = 0
        while (shuffled == tokens && guard++ < 5) shuffled = tokens.shuffled(random)
        return Exercise.Arrange(key, s.de, s.en, shuffled)
    }

    private fun translateOrArrange(key: String, s: Sentence, pool: List<Sentence>): Exercise =
        if (AnswerChecker.tokens(s.en).size <= 10) Exercise.TypeIn(
            key, "Übersetze ins Englische:", s.de, listOf(s.en) + s.alt,
            hint = "Erstes Wort: „${AnswerChecker.tokens(s.en).first()}“",
        ) else arrangeOrChoice(key, s, pool)

    private fun dictationOrChoice(key: String, s: Sentence, pool: List<Sentence>): Exercise =
        if (AnswerChecker.tokens(s.en).size <= 9) Exercise.TypeIn(
            key, "Hör zu und schreibe mit:", s.en, listOf(s.en) + s.alt,
            speak = s.en, listening = true, explanation = s.de,
        ) else listeningChoice(key, s, pool)

    private fun listeningChoice(key: String, s: Sentence, pool: List<Sentence>): Exercise.Choice {
        val opts = (distractors(pool, s, { it.de }, { it.en }) + s).shuffled(random)
        return Exercise.Choice(
            key, "Hör zu: Was bedeutet der Satz?", s.en, opts.map { it.de }, opts.indexOf(s),
            speak = s.en, listening = true,
        )
    }

    private fun translationChoice(key: String, s: Sentence, pool: List<Sentence>): Exercise.Choice {
        val opts = (distractors(pool, s, { it.en }, { it.de }) + s).shuffled(random)
        return Exercise.Choice(
            key, "Welche Übersetzung passt?", s.de, opts.map { it.en }, opts.indexOf(s),
        )
    }
}
