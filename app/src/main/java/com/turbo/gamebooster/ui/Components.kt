package com.turbo.gamebooster.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turbo.gamebooster.core.AppRepo
import com.turbo.gamebooster.core.SystemInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

val Neon = Color(0xFF39FF88)
val Cyan = Color(0xFF00D1FF)
val Amber = Color(0xFFFFB020)
val Danger = Color(0xFFFF4D5E)
val Muted = Color(0xFF8A96A3)
val Pink = Color(0xFFFF4FD8)

val NeonBrush = Brush.horizontalGradient(listOf(Color(0xFF39FF88), Color(0xFF00D1FF)))
val HeroBrush = Brush.linearGradient(listOf(Color(0xFF12301F), Color(0xFF0E1C2A), Color(0xFF131A22)))

/** Botão principal com degradê neon. */
@Composable
fun BoostButton(running: Boolean, text: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(NeonBrush)
            .clickable(enabled = !running) { onClick() }
    ) {
        if (running) {
            CircularProgressIndicator(color = Color.Black, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text(text, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 17.sp, letterSpacing = 1.sp)
            }
        }
    }
}

/** Logo "SV" do app. */
@Composable
fun SvLogo(size: Dp = 36.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3.2f))
            .background(NeonBrush)
    ) {
        Text("SV", color = Color.Black, fontWeight = FontWeight.Black, fontSize = (size.value * 0.42f).sp)
    }
}

@Composable
fun TurboTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Neon,
            onPrimary = Color(0xFF00210F),
            secondary = Cyan,
            background = Color(0xFF0B0F14),
            onBackground = Color(0xFFE8EEF4),
            surface = Color(0xFF131A22),
            onSurface = Color(0xFFE8EEF4),
            surfaceVariant = Color(0xFF1C2530),
            onSurfaceVariant = Muted,
            surfaceContainerHighest = Color(0xFF1C2530),
            error = Danger,
        ),
    ) {
        // Superfície base: garante texto claro em todas as telas.
        Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            content()
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector? = null,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = Neon, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                if (badge != null) Tag(badge, Amber)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
fun Tag(text: String, color: Color = Cyan) {
    Text(
        text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier
            .border(1.dp, color, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun Hint(text: String) {
    Text(text, color = Muted, style = MaterialTheme.typography.bodySmall)
}

@Composable
fun ToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    enabled: Boolean = true,
    tag: String? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(vertical = 6.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, color = if (enabled) Color.Unspecified else Muted)
                if (tag != null) {
                    Spacer(Modifier.width(6.dp)); Tag(tag, Amber)
                }
            }
            if (subtitle != null) Hint(subtitle)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun AppIcon(pkg: String, size: Dp = 48.dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, pkg) {
        value = withContext(Dispatchers.IO) { AppRepo.icon(ctx, pkg) }
    }
    val b = bmp
    if (b != null) {
        Image(b, null, Modifier.size(size).clip(RoundedCornerShape(size / 4)))
    } else {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size / 4))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    }
}

fun tempColor(t: Float) = when {
    t < 38f -> Neon
    t < 43f -> Amber
    else -> Danger
}

@Composable
fun MonitorCard() {
    val ctx = LocalContext.current
    var snap by remember { mutableStateOf(SystemInfo.snapshot(ctx)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000); snap = SystemInfo.snapshot(ctx)
        }
    }
    val s = snap
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Stat(
                "RAM", "${"%.1f".format(s.ramUsedMb / 1024f)}/${"%.0f".format(s.ramTotalMb / 1024f)} GB",
                s.ramFraction, if (s.ramFraction > 0.85f) Danger else Cyan, Modifier.weight(1f)
            )
            Stat(
                "TEMP", "${"%.1f".format(s.batteryTempC)}°C",
                ((s.batteryTempC - 25f) / 25f).coerceIn(0f, 1f), tempColor(s.batteryTempC), Modifier.weight(1f)
            )
            Stat(
                "BATERIA", "${s.batteryPct}%${if (s.charging) " ⚡" else ""}",
                s.batteryPct / 100f, if (s.batteryPct < 20) Danger else Neon, Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String, frac: Float, color: Color, modifier: Modifier) {
    Column(modifier) {
        Text(label, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = 16.sp, fontWeight = FontWeight.Black, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { frac },
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
        )
    }
}

@Composable
fun CrosshairPreview(color: Color, sizeDp: Int, style: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.size((sizeDp + 8).dp)) {
        val cx = size.width / 2
        val cy = size.height / 2
        val r = size.minDimension / 2 - 4.dp.toPx()
        val gap = 4.dp.toPx()
        val sw = 2.2.dp.toPx()
        when (style) {
            1 -> drawCircle(color, 3.dp.toPx(), Offset(cx, cy))
            2 -> {
                drawCircle(color, r, Offset(cx, cy), style = Stroke(sw))
                drawCircle(color, 2.2.dp.toPx(), Offset(cx, cy))
            }
            else -> {
                drawLine(color, Offset(cx - r, cy), Offset(cx - gap, cy), sw, StrokeCap.Round)
                drawLine(color, Offset(cx + gap, cy), Offset(cx + r, cy), sw, StrokeCap.Round)
                drawLine(color, Offset(cx, cy - r), Offset(cx, cy - gap), sw, StrokeCap.Round)
                drawLine(color, Offset(cx, cy + gap), Offset(cx, cy + r), sw, StrokeCap.Round)
            }
        }
    }
}


/** Se a mensagem pede para mexer nas Opções do desenvolvedor, mostra um botão que abre a tela. */
@Composable
fun DevOptionsHelp(msg: String?) {
    if (msg == null || !msg.contains("Opções do desenvolvedor")) return
    val ctx = LocalContext.current
    androidx.compose.material3.OutlinedButton(
        onClick = {
            try {
                ctx.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
        },
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) { Text("Abrir Opções do desenvolvedor") }
}
