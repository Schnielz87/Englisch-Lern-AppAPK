package de.englischlernen.app.ui.help

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.englischlernen.app.BuildConfig
import de.englischlernen.app.ui.components.LocalAppContainer
import de.englischlernen.app.ui.components.VSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Ein Kapitel der Hilfe (assets/content/help.json – dieselbe Quelle wie die Word-Anleitung). */
@Serializable
data class HelpSection(val id: String, val title: String, val icon: String, val blocks: List<JsonObject>) {
    /** Gesamter Text fuer die Suche. */
    val searchText: String by lazy { (listOf(title) + blocks.flatMap { b -> b.values.flatMap { flatten(it) } }).joinToString(" ").lowercase() }

    private fun flatten(e: kotlinx.serialization.json.JsonElement): List<String> = when (e) {
        is JsonArray -> e.flatMap { flatten(it) }
        is JsonObject -> e.values.flatMap { flatten(it) }
        else -> listOf(e.jsonPrimitive.content)
    }
}

object HelpContent {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(text: String): List<HelpSection> = json.decodeFromString(ListSerializer(HelpSection.serializer()), text)
}

private fun iconFor(name: String): ImageVector = when (name) {
    "rocket" -> Icons.Filled.RocketLaunch
    "map" -> Icons.Filled.Explore
    "book" -> Icons.AutoMirrored.Filled.MenuBook
    "edit" -> Icons.Filled.Edit
    "medal" -> Icons.Filled.WorkspacePremium
    "fitness" -> Icons.Filled.FitnessCenter
    "ai" -> Icons.Filled.AutoAwesome
    "trophy" -> Icons.Filled.EmojiEvents
    "settings" -> Icons.Filled.Settings
    "update" -> Icons.Filled.SystemUpdate
    "lock" -> Icons.Filled.Lock
    else -> Icons.AutoMirrored.Filled.HelpOutline
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val c = LocalAppContainer.current
    var sections by remember { mutableStateOf<List<HelpSection>>(emptyList()) }
    var query by rememberSaveable { mutableStateOf("") }
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    LaunchedEffect(Unit) {
        sections = withContext(Dispatchers.IO) {
            context.assets.open("content/help.json").bufferedReader().use { HelpContent.parse(it.readText()) }
        }
    }
    val q = query.trim().lowercase()
    val visible = if (q.isEmpty()) sections else sections.filter { it.searchText.contains(q) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Hilfe & Anleitung") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
        )
    }) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text("Hier erfährst du, was du wie machen kannst. Tippe auf ein Kapitel, um es aufzuklappen.", style = MaterialTheme.typography.bodyMedium)
                VSpace(8.dp)
                OutlinedTextField(
                    query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Suchen, z. B. „Mikrofon“") },
                )
            }
            items(visible, key = { it.id }) { s ->
                val open = expanded[s.id] ?: (q.isNotEmpty())
                Card(
                    onClick = { expanded[s.id] = !open },
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(iconFor(s.icon), null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.secondary)
                            Text("  ${s.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
                        }
                        AnimatedVisibility(open) {
                            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                s.blocks.forEach { HelpBlock(it) }
                            }
                        }
                    }
                }
            }
            if (visible.isEmpty() && sections.isNotEmpty()) item { Text("Nichts gefunden. Versuche ein anderes Wort – oder frag Finn im Tab „Finn“.") }
            item {
                VSpace(8.dp)
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}/releases/latest/download/Anleitung_Fundbrueder_English.docx")))
                }, modifier = Modifier.fillMaxWidth()) { Text("Ausführliche Anleitung (Word) öffnen") }
                if (c.ai.config.value.ready) Text(
                    "Noch Fragen? Finn beantwortet sie dir im Tab „Finn“ → „Frag Finn“.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp),
                )
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun HelpBlock(b: JsonObject) {
    fun list(key: String) = b[key]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
    when {
        "p" in b -> Text(b["p"]!!.jsonPrimitive.content, style = MaterialTheme.typography.bodyLarge)
        "steps" in b -> list("steps").forEachIndexed { i, t -> Row { Text("${i + 1}.", fontWeight = FontWeight.Bold, modifier = Modifier.width(26.dp)); Text(t, style = MaterialTheme.typography.bodyLarge) } }
        "bullets" in b -> list("bullets").forEach { t -> Row { Text("•", modifier = Modifier.width(18.dp)); Text(t, style = MaterialTheme.typography.bodyLarge) } }
        "table" in b -> b["table"]!!.jsonArray.drop(1).forEach { row ->
            val cells = row.jsonArray.map { it.jsonPrimitive.content }
            Column {
                Text(cells[0], style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Text(cells.getOrElse(1) { "" }, style = MaterialTheme.typography.bodyMedium)
            }
        }
        "qa" in b -> b["qa"]!!.jsonArray.forEach { pair ->
            val (qq, a) = pair.jsonArray.map { it.jsonPrimitive.content }
            Column {
                Text("❓ $qq", style = MaterialTheme.typography.titleSmall)
                Text(a, style = MaterialTheme.typography.bodyMedium)
            }
        }
        else -> {} // Bilder gibt es nur in der Word-Anleitung
    }
}
