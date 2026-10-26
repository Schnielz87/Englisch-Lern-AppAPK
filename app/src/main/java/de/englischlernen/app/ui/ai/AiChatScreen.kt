package de.englischlernen.app.ui.ai

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.englischlernen.app.speech.SpeechInput
import de.englischlernen.app.ui.components.LocalAppContainer
import de.englischlernen.app.ui.components.VSpace
import de.englischlernen.app.ui.session.RichText
import de.englischlernen.app.ui.theme.Brand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(mode: AiMode, scenario: Int, onBack: () -> Unit) {
    val c = LocalAppContainer.current
    val context = LocalContext.current
    val vm: AiChatViewModel = viewModel(key = "ai:$mode:$scenario") { AiChatViewModel(c, mode, scenario) }
    val state by vm.state.collectAsStateWithLifecycle()
    val speech by c.speech.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val visible = state.items.filter { !it.hidden }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) c.speech.start() }

    LaunchedEffect(visible.size, state.streaming) { if (visible.isNotEmpty()) list.animateScrollToItem(list.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1) }
    LaunchedEffect(speech) {
        when (val s = speech) {
            is SpeechInput.State.Partial -> input = s.text
            is SpeechInput.State.Result -> {
                val text = s.alternatives.first()
                c.speech.reset()
                if (mode == AiMode.ROLEPLAY) { vm.send(text); input = "" } else input = text
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = { IconButton(onClick = { c.tts.stop(); c.speech.reset(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = {
                    if (mode == AiMode.ROLEPLAY) IconButton(onClick = vm::toggleAutoSpeak) {
                        Icon(if (state.autoSpeak) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff, "Vorlesen an/aus")
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding().padding(12.dp)) {
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        input, { input = it }, modifier = Modifier.weight(1f),
                        placeholder = { Text(if (mode == AiMode.ROLEPLAY) "Antworte auf Englisch …" else "Deine Nachricht …") },
                        minLines = if (mode == AiMode.WRITING) 3 else 1, maxLines = if (mode == AiMode.WRITING) 10 else 4,
                    )
                    val listening = speech is SpeechInput.State.Listening || speech is SpeechInput.State.Partial
                    IconButton(onClick = {
                        if (listening) c.speech.reset()
                        else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            c.tts.stop(); c.speech.start(if (mode == AiMode.ROLEPLAY || mode == AiMode.WRITING) "en-US" else "de-DE")
                        } else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Icon(if (listening) Icons.Filled.Stop else Icons.Filled.Mic, "Sprechen", tint = if (listening) Brand.Error else MaterialTheme.colorScheme.onSurfaceVariant) }
                    FilledIconButton(
                        onClick = { vm.send(input); input = "" }, enabled = input.isNotBlank() && !state.busy,
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                    ) { Icon(Icons.AutoMirrored.Filled.Send, "Senden") }
                }
                (speech as? SpeechInput.State.Error)?.let { Text(it.message, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error) }
            }
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible) { item -> ChatBubble(item, speakable = mode == AiMode.ROLEPLAY) }
            state.streaming?.let { s -> item { ChatBubble(ChatItem(false, s.ifBlank { "…" }), speakable = false) } }
            state.error?.let { e ->
                item {
                    Column {
                        Text(e, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = vm::retry) { Text("Erneut versuchen") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(item: ChatItem, speakable: Boolean) {
    val c = LocalAppContainer.current
    Box(Modifier.fillMaxWidth(), contentAlignment = if (item.fromUser) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(Modifier.widthIn(max = 340.dp)) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = if (item.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Column(Modifier.padding(12.dp)) {
                    if (item.fromUser) Text(item.text, style = MaterialTheme.typography.bodyLarge) else RichText(item.text)
                    if (speakable && !item.fromUser) TextButton(onClick = { c.tts.speak(item.text) }) { Text("🔊 Anhören") }
                }
            }
            item.feedback?.let { f ->
                VSpace(4.dp)
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.padding(10.dp)) {
                        Text("💡 Finns Feedback", style = MaterialTheme.typography.labelMedium)
                        RichText(f)
                    }
                }
            }
        }
    }
}
