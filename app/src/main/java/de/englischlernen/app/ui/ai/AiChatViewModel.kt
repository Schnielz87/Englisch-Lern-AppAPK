package de.englischlernen.app.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.englischlernen.app.AppContainer
import de.englischlernen.app.ai.AiMessage
import de.englischlernen.app.ai.Prompts
import de.englischlernen.app.data.content.ContentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
enum class AiMode(val title: String) { TUTOR("Frag Finn"), ROLEPLAY("Gesprächspartner"), WRITING("Schreibtrainer"), COACH("Lernberater") }

data class ChatItem(val fromUser: Boolean, val text: String, val feedback: String? = null, val hidden: Boolean = false)

data class AiChatState(
    val title: String = "",
    val items: List<ChatItem> = emptyList(),
    val streaming: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val autoSpeak: Boolean = true,
)

class AiChatViewModel(private val c: AppContainer, val mode: AiMode, private val scenarioIndex: Int) : ViewModel() {
    private val _state = MutableStateFlow(AiChatState(title = mode.title))
    val state: StateFlow<AiChatState> = _state
    private var instructions = ""
    private var level = "A1"

    init {
        viewModelScope.launch {
            val s = c.settings.current()
            level = ContentRepository.LEVEL_IDS.getOrElse(s.unlockedLevel) { "a1" }.uppercase()
            instructions = when (mode) {
                AiMode.TUTOR -> Prompts.tutor(level, s.userName)
                AiMode.WRITING -> Prompts.writingCoach(level, s.userName)
                AiMode.COACH -> Prompts.coach(level, s.userName)
                AiMode.ROLEPLAY -> Prompts.rolePlay(level, s.userName, Prompts.scenarios.getOrElse(scenarioIndex) { Prompts.scenarios.last() }.description)
            }
            when (mode) {
                AiMode.TUTOR -> _state.update {
                    it.copy(items = listOf(ChatItem(false, "Hallo${if (s.userName.isNotBlank()) " ${s.userName}" else ""}! Ich bin Finn, dein Fundbruder für Englisch. 🧭\nFrag mich alles rund um Grammatik, Wörter, Aussprache oder wie du am besten lernst – oder lass einen Satz von mir prüfen."))) }
                AiMode.WRITING -> _state.update {
                    it.copy(items = listOf(ChatItem(false, "Schreib oder füge einen englischen Text ein – zum Beispiel eine E-Mail, eine Nachricht oder einen kurzen Aufsatz. Ich korrigiere ihn und erkläre dir die Fehler."))) }
                AiMode.ROLEPLAY -> {
                    val sc = Prompts.scenarios.getOrElse(scenarioIndex) { Prompts.scenarios.last() }
                    _state.update { it.copy(title = "${sc.emoji} ${sc.title}") }
                    send("Please start the role-play now.", hidden = true)
                }
                AiMode.COACH -> send(statsSummary(), hidden = true)
            }
        }
    }

    fun toggleAutoSpeak() = _state.update { it.copy(autoSpeak = !it.autoSpeak) }

    fun send(text: String, hidden: Boolean = false) {
        if (text.isBlank() || _state.value.busy) return
        _state.update { it.copy(items = it.items + ChatItem(true, text.trim(), hidden = hidden), busy = true, error = null, streaming = "") }
        viewModelScope.launch {
            try {
                val history = _state.value.items.filterIndexed { i, item -> !(i == 0 && !item.fromUser && mode != AiMode.ROLEPLAY) }
                    .map { AiMessage(it.fromUser, it.text + (it.feedback?.let { f -> "\n${Prompts.SEPARATOR}\n$f" } ?: "")) }
                val reply = c.ai.reply(instructions, history) { partial ->
                    _state.update { it.copy(streaming = partial.substringBefore(Prompts.SEPARATOR)) }
                }
                val (main, feedback) = if (mode == AiMode.ROLEPLAY) {
                    reply.substringBefore(Prompts.SEPARATOR).trim() to reply.substringAfter(Prompts.SEPARATOR, "").trim().ifBlank { null }
                } else reply.trim() to null
                _state.update { it.copy(items = it.items + ChatItem(false, main, feedback), busy = false, streaming = null) }
                if (mode == AiMode.ROLEPLAY && _state.value.autoSpeak) c.tts.speak(main)
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, streaming = null, error = e.message ?: "Unbekannter Fehler") }
            }
        }
    }

    fun retry() {
        val last = _state.value.items.lastOrNull { it.fromUser } ?: return
        _state.update { it.copy(items = it.items.dropLast(1), error = null) }
        send(last.text, last.hidden)
    }

    /** Lerndaten fuer den Lernberater zusammenfassen (keine persoenlichen Daten ausser Lernstand). */
    private suspend fun statsSummary(): String {
        val levels = c.content.levels()
        val progress = c.progress.lessonProgress.first().associateBy { it.lessonId }
        val exams = c.progress.examResults.first()
        val activity = c.progress.activity.first()
        val weak = c.progress.weakestKeys(8).mapNotNull { key ->
            val id = key.split(':').getOrNull(1)
            levels.flatMap { it.lessons }.firstOrNull { it.id == id }?.title
        }.distinct()
        val sb = StringBuilder("Meine Lerndaten:\n")
        sb.append("Aktuelles Niveau: $level\n")
        levels.forEach { lv ->
            val done = lv.lessons.filter { progress[it.id]?.passed == true }
            if (done.isNotEmpty()) sb.append("${lv.code}: ${done.size}/${lv.lessons.size} Lektionen, Ø ${done.map { progress[it.id]!!.bestScore }.average().toInt()} %\n")
        }
        exams.groupBy { it.levelId }.forEach { (id, list) -> sb.append("Prüfung ${id.uppercase()}: bestes Ergebnis ${list.maxOf { it.score }} %\n") }
        val total = activity.sumOf { it.total }
        if (total > 0) sb.append("Trefferquote gesamt: ${activity.sumOf { it.correct } * 100 / total} %\n")
        sb.append("Aktive Lerntage: ${activity.count { it.xp > 0 }}, gesprochene Sätze: ${activity.sumOf { it.spoken }}\n")
        if (weak.isNotEmpty()) sb.append("Häufige Fehler in den Lektionen: ${weak.joinToString()}\n")
        return sb.toString()
    }
}
