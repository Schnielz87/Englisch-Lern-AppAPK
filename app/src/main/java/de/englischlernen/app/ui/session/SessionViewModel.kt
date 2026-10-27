package de.englischlernen.app.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.englischlernen.app.AppContainer
import de.englischlernen.app.domain.AnswerChecker
import de.englischlernen.app.domain.Exercise
import de.englischlernen.app.domain.ExerciseResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Feedback(
    val correct: Boolean,
    val title: String,
    val expected: String?,
    val explanation: String,
    val prompt: String = "",
    val given: String = "",
    val aiExplanation: String? = null,
    val aiLoading: Boolean = false,
)

data class SessionUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val title: String = "",
    val exercises: List<Exercise> = emptyList(),
    val index: Int = 0,
    val feedback: Feedback? = null,
    val xp: Int = 0,
    val showFeedback: Boolean = true,
    val summary: SessionSummary? = null,
    val empty: Boolean = false,
    val evaluating: Boolean = false,
) {
    val current: Exercise? get() = exercises.getOrNull(index)
    val progress: Float get() = if (exercises.isEmpty()) 0f else index.toFloat() / exercises.size
}

class SessionViewModel(
    private val c: AppContainer,
    private val mode: SessionMode,
    private val arg: String,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionUiState())
    val state: StateFlow<SessionUiState> = _state
    private lateinit var plan: SessionPlan
    private val results = mutableListOf<ExerciseResult>()
    private var spoken = 0
    private var finishing = false

    init { load() }

    private fun load() = viewModelScope.launch {
        try {
            plan = SessionPlans.create(mode, arg, c)
            val list = plan.initial()
            _state.value = SessionUiState(
                loading = false, title = plan.title, exercises = list,
                showFeedback = plan.showFeedback, empty = list.isEmpty(),
            )
        } catch (e: Exception) {
            _state.value = SessionUiState(loading = false, error = e.message ?: "Fehler beim Laden")
        }
    }

    fun restart() {
        results.clear(); spoken = 0; finishing = false
        _state.value = SessionUiState()
        load()
    }

    // ---------- Antworten ----------

    fun answerChoice(index: Int) {
        val ex = _state.value.current as? Exercise.Choice ?: return
        val ok = index == ex.correctIndex
        record(ex, ok, ex.options.getOrElse(index) { "" }, ex.options[ex.correctIndex], ex.explanation, typo = false)
    }

    fun answerText(text: String) {
        val ex = _state.value.current as? Exercise.TypeIn ?: return
        val v = AnswerChecker.check(text, ex.accepted)
        val explanation = listOf(ex.explanation, if (ex.listening) "Gehört: ${ex.prompt}" else "")
            .filter { it.isNotBlank() }.distinct().joinToString("\n")
        record(ex, v != AnswerChecker.Verdict.WRONG, text, ex.accepted.first(), explanation, typo = v == AnswerChecker.Verdict.TYPO)
    }

    fun answerArrange(words: List<String>) {
        val ex = _state.value.current as? Exercise.Arrange ?: return
        val given = words.joinToString(" ")
        val ok = AnswerChecker.check(given, listOf(ex.answer)) == AnswerChecker.Verdict.CORRECT
        record(ex, ok, given, ex.answer, "", typo = false)
    }

    /** Aussprache: [similarity] 0..1 aus der Spracherkennung. */
    fun answerSpeech(ex: Exercise, similarity: Double, recognized: String, expected: String) {
        spoken++
        val ok = similarity >= SPEECH_THRESHOLD
        val note = "Erkannt: „$recognized“ (${(similarity * 100).toInt()} % Übereinstimmung)"
        record(ex, ok, recognized, expected, note, typo = false)
    }

    /**
     * Freies Sprechen bewerten: mit KI (falls eingerichtet) inkl. Korrekturvorschlaegen,
     * sonst einfache Pruefung in der App (Laenge, Englisch) plus Musterantwort.
     */
    fun evaluateFreeSpeech(ex: Exercise.FreeSpeak, transcript: String) {
        if (_state.value.evaluating || transcript.isBlank()) return
        spoken++
        _state.update { it.copy(evaluating = true) }
        viewModelScope.launch {
            val aiReady = c.ai.config.value.ready
            var title: String? = null
            val (ok, text) = if (aiReady) {
                runCatching {
                    val reply = c.ai.reply(
                        de.englischlernen.app.ai.Prompts.speakingFeedback(ex.level),
                        listOf(de.englischlernen.app.ai.AiMessage(true, de.englischlernen.app.ai.Prompts.speakingRequest(ex.prompt.question, ex.prompt.task, transcript))),
                    )
                    val score = Regex("PUNKTE:\\s*(\\d{1,3})").find(reply)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(0, 100)
                    val body = reply.lines().filterNot { it.trim().startsWith(de.englischlernen.app.ai.Prompts.SCORE_PREFIX) }.joinToString("\n").trim()
                    title = score?.let { "Finn gibt dir $it von 100 Punkten" }
                    ((score ?: 0) >= 60) to body
                }.getOrElse { offlineSpeechCheck(ex, transcript).let { (o, t) -> o to "Finn ist gerade nicht erreichbar (${it.message}).\n$t" } }
            } else offlineSpeechCheck(ex, transcript)
            _state.update { it.copy(evaluating = false) }
            record(ex, ok, transcript, ex.prompt.sample, text, typo = false, titleOverride = title)
        }
    }

    private fun offlineSpeechCheck(ex: Exercise.FreeSpeak, transcript: String): Pair<Boolean, String> {
        val words = AnswerChecker.normalize(transcript).split(' ').filter { it.isNotBlank() }
        val minWords = mapOf("A1" to 4, "A2" to 7, "B1" to 10, "B2" to 14, "C1" to 18, "C2" to 20)[ex.level] ?: 6
        val english = setOf("i", "you", "he", "she", "it", "we", "they", "the", "a", "an", "is", "am", "are", "was", "were", "and", "to", "of",
            "in", "my", "have", "has", "do", "like", "because", "but", "with", "for", "on", "at", "that", "this", "would", "will", "not", "very")
        val englishShare = if (words.isEmpty()) 0.0 else words.count { it in english }.toDouble() / words.size
        val ok = words.size >= minWords && englishShare >= 0.12
        val msg = buildString {
            append("Du hast ${words.size} Wörter gesprochen")
            append(if (ok) " – gut gemacht!" else if (words.size < minWords) " – versuche, ausführlicher zu antworten (mind. $minWords Wörter)." else " – antworte bitte auf Englisch.")
            append("\n**Musterantwort:** ${ex.prompt.sample}")
            append("\n💡 Mit Finn (KI) bekommst du eine genaue Bewertung und Korrekturen. Einrichten im Tab „Finn“.")
        }
        return ok to msg
    }

    /** Selbsteinschaetzung, wenn keine Spracherkennung verfuegbar ist. */
    fun answerSelf(ex: Exercise, ok: Boolean, expected: String) {
        spoken++
        record(ex, ok, if (ok) "selbst bewertet: richtig" else "selbst bewertet: nicht richtig", expected, "", typo = false)
    }

    /** Ueberspringen ohne Wertung (z. B. Erklaerkarten, oder wenn gerade nicht gesprochen werden kann). */
    fun continueUnscored() = advance()

    private fun record(ex: Exercise, ok: Boolean, given: String, expected: String, explanation: String, typo: Boolean, titleOverride: String? = null) {
        if (_state.value.feedback != null) return
        results += ExerciseResult(ex, ok, given, expected)
        val gained = if (ok) plan.xpPerCorrect else 0
        if (!plan.showFeedback) {
            _state.update { it.copy(xp = it.xp + gained) }
            advance()
            return
        }
        _state.update {
            it.copy(
                xp = it.xp + gained,
                feedback = Feedback(
                    correct = ok,
                    title = when {
                        titleOverride != null -> titleOverride
                        ok && typo -> "Richtig – kleiner Tippfehler"
                        ok -> listOf("Richtig!", "Super!", "Treffer!", "Gut gefunden!").random()
                        else -> "Nicht ganz"
                    },
                    expected = if (ex is Exercise.FreeSpeak) null else if (!ok || typo) expected else null,
                    explanation = explanation,
                    prompt = describe(ex),
                    given = given,
                ),
            )
        }
    }

    fun next() = advance()

    /** Laesst die KI (falls eingerichtet) erklaeren, warum die Antwort falsch war. */
    fun explainWithAi() {
        val f = _state.value.feedback ?: return
        if (f.aiLoading) return
        _state.update { it.copy(feedback = f.copy(aiLoading = true, aiExplanation = null)) }
        viewModelScope.launch {
            val level = de.englischlernen.app.data.content.ContentRepository.LEVEL_IDS
                .getOrElse(c.settings.current().unlockedLevel) { "a1" }.uppercase()
            val text = runCatching {
                c.ai.reply(
                    de.englischlernen.app.ai.Prompts.explainMistake(level),
                    listOf(de.englischlernen.app.ai.AiMessage(true, de.englischlernen.app.ai.Prompts.explainRequest(f.prompt, f.given, f.expected ?: ""))),
                )
            }.getOrElse { "Finn ist gerade nicht erreichbar: ${it.message}" }
            _state.update { s -> s.feedback?.let { s.copy(feedback = it.copy(aiLoading = false, aiExplanation = text)) } ?: s }
        }
    }

    private fun describe(ex: Exercise): String = when (ex) {
        is Exercise.Choice -> "${ex.instruction} ${ex.prompt} (Optionen: ${ex.options.joinToString(" | ")})"
        is Exercise.TypeIn -> if (ex.listening) "Diktat: Schreibe den gehörten englischen Satz." else "${ex.instruction} ${ex.prompt}"
        is Exercise.Arrange -> "Bilde den englischen Satz zu: ${ex.prompt}"
        is Exercise.Speak -> "Sprich nach: ${ex.text}"
        is Exercise.DialoguePractice -> "Rollenspiel ${ex.dialogue.title}"
        is Exercise.FreeSpeak -> "Frei sprechen: ${ex.prompt.question}"
        else -> ""
    }

    private fun advance() {
        val s = _state.value
        if (s.index + 1 < s.exercises.size) {
            _state.update { it.copy(index = it.index + 1, feedback = null) }
            return
        }
        if (finishing) return
        finishing = true
        viewModelScope.launch {
            val more = plan.more(results.toList())
            if (!more.isNullOrEmpty()) {
                finishing = false
                _state.update { it.copy(exercises = it.exercises + more, index = it.index + 1, feedback = null) }
                return@launch
            }
            val summary = plan.finish(results.toList(), _state.value.xp)
            c.progress.recordResults(results.toList(), summary.xp, spoken, plan.isReview)
            _state.update { it.copy(feedback = null, summary = summary, index = it.exercises.size) }
        }
    }

    override fun onCleared() {
        c.speech.reset()
        c.tts.stop()
    }

    companion object { const val SPEECH_THRESHOLD = 0.7 }
}
