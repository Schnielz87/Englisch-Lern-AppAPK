package de.englischlernen.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.englischlernen.app.AppContainer
import de.englischlernen.app.ui.theme.Brand

val LocalAppContainer = compositionLocalOf<AppContainer> { error("AppContainer fehlt") }

/** Das Fundbrueder-Logo im GTA-Stil (identisch mit dem App-Icon). */
@Composable
fun FundbruederLogo(size: Dp = 96.dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(de.englischlernen.app.R.drawable.logo_full),
        contentDescription = "Fundbrüder English",
        modifier = modifier.size(size),
    )
}

/** Persoenliche Widmung unter dem Logo. */
@Composable
fun DedicationNote(modifier: Modifier = Modifier) {
    Text(
        "Timo for you kleiner SchwaLu",
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
        color = MaterialTheme.colorScheme.secondary,
    )
}

@Composable
fun SpeakerButtons(text: String, modifier: Modifier = Modifier, showSlow: Boolean = true) {
    val c = LocalAppContainer.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalIconButton(onClick = { c.tts.speak(text) }) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Vorlesen")
        }
        if (showSlow) FilledTonalIconButton(onClick = { c.tts.speak(text, slow = true) }) {
            Icon(Icons.Filled.SlowMotionVideo, contentDescription = "Langsam vorlesen")
        }
    }
}

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = container),
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

@Composable
fun StatTile(icon: ImageVector, value: String, label: String, tint: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = tint)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LabeledProgress(label: String, value: Float, trailing: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { value.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/** Kennzeichnung fuer Funktionen, die Internet benoetigen. */
@Composable
fun OnlineBadge(text: String = "benötigt Internet") {
    Text(
        "🌐 $text",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.tertiary,
    )
}

@Composable
fun HSpace(w: Dp) = Spacer(Modifier.width(w))

@Composable
fun VSpace(h: Dp) = Spacer(Modifier.height(h))
