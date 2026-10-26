package de.englischlernen.app.domain

import java.text.Normalizer

/** Bewertet freie Texteingaben tolerant (Gross/Klein, Satzzeichen, Kurzformen, kleine Tippfehler). */
object AnswerChecker {

    enum class Verdict { CORRECT, TYPO, WRONG }

    private val contractions = listOf(
        "won't" to "will not", "can't" to "cannot", "shan't" to "shall not",
        "n't" to " not", "i'm" to "i am", "'re" to " are", "'ve" to " have",
        "'ll" to " will", "'d" to " would", "it's" to "it is", "he's" to "he is",
        "she's" to "she is", "that's" to "that is", "what's" to "what is",
        "there's" to "there is", "let's" to "let us", "where's" to "where is",
        "who's" to "who is", "how's" to "how is",
    )

    fun normalize(input: String): String {
        var s = Normalizer.normalize(input, Normalizer.Form.NFC)
            .lowercase()
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('`', '\'')
        for ((short, long) in contractions) s = s.replace(short, long)
        s = s.replace("can not", "cannot")
        s = s.replace(Regex("[^\\p{L}\\p{N}' ]"), " ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    fun check(input: String, accepted: List<String>): Verdict {
        val given = normalize(input)
        if (given.isEmpty()) return Verdict.WRONG
        val targets = accepted.map(::normalize).filter { it.isNotEmpty() }
        if (targets.any { it == given }) return Verdict.CORRECT
        val best = targets.minOfOrNull { levenshtein(it, given) } ?: return Verdict.WRONG
        val len = targets.minOf { it.length }
        val tolerance = when {
            len <= 4 -> 0
            len <= 12 -> 1
            else -> 2
        }
        return if (best <= tolerance) Verdict.TYPO else Verdict.WRONG
    }

    /** Anteil der Zielwoerter (0..1), die in der erkannten Sprache vorkommen – fuer Ausspracheuebungen. */
    fun speechSimilarity(recognized: String, target: String): Double {
        val t = normalize(target).split(' ').filter { it.isNotEmpty() }
        if (t.isEmpty()) return 0.0
        val r = normalize(recognized).split(' ').filter { it.isNotEmpty() }.toMutableList()
        var hits = 0
        for (w in t) {
            val idx = r.indexOfFirst { it == w || (w.length > 4 && levenshtein(it, w) <= 1) }
            if (idx >= 0) {
                hits++
                r.removeAt(idx)
            }
        }
        val extraPenalty = (r.size.toDouble() / t.size).coerceAtMost(0.3)
        return (hits.toDouble() / t.size - extraPenalty * 0.5).coerceIn(0.0, 1.0)
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length]
    }

    /** Zerlegt einen Satz in Bausteine fuer die Satzbau-Uebung. */
    fun tokens(sentence: String): List<String> =
        sentence.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
}
