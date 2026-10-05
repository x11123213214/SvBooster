@file:OptIn(ExperimentalLayoutApi::class)

package com.turbo.gamebooster.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turbo.gamebooster.core.Cleaner
import com.turbo.gamebooster.core.Ping
import com.turbo.gamebooster.core.Prefs
import com.turbo.gamebooster.core.Tweaks
import com.turbo.gamebooster.overlay.OverlayService
import com.turbo.gamebooster.shell.Shell
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class Server(val name: String, val host: String, val port: Int)

private val SERVERS = listOf(
    Server("Google", "8.8.8.8", 53),
    Server("Cloudflare", "1.1.1.1", 53),
    Server("AWS São Paulo", "dynamodb.sa-east-1.amazonaws.com", 443),
    Server("AWS Virgínia (EUA)", "dynamodb.us-east-1.amazonaws.com", 443),
)

private val CROSS_COLORS = listOf(Neon, Cyan, Color(0xFFFF3B3B), Color(0xFFFFE600), Color.White, Color(0xFFFF4FD8))

fun openOverlaySettings(ctx: Context) {
    ctx.startActivity(
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Composable
fun ToolsScreen(mode: Shell.Mode) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasShell = mode != Shell.Mode.NONE
    val needShell = if (hasShell) null else "TURBO"

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // ---- Limpeza de RAM
        var cleaning by remember { mutableStateOf(false) }
        var cleanMsg by remember { mutableStateOf<String?>(null) }
        SectionCard("Limpar RAM", Icons.Filled.CleaningServices) {
            Hint("Fecha processos em segundo plano para liberar memória antes de jogar.")
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    cleaning = true
                    scope.launch {
                        val mb = Cleaner.clean(ctx)
                        cleanMsg = if (mb > 0) "$mb MB liberados" else "A memória já estava otimizada"
                        cleaning = false
                    }
                },
                enabled = !cleaning,
                colors = ButtonDefaults.buttonColors(containerColor = Neon, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (cleaning) CircularProgressIndicator(Modifier.size(18.dp), color = Color.Black, strokeWidth = 2.dp)
                else Text("Limpar agora", fontWeight = FontWeight.Bold)
            }
            cleanMsg?.let { Spacer(Modifier.height(6.dp)); Text(it, color = Neon) }
        }

        // ---- Ping
        val results = remember { mutableStateMapOf<String, Ping.Res?>() }
        var pinging by remember { mutableStateOf(false) }
        var custom by remember { mutableStateOf("") }
        SectionCard("Teste de ping", Icons.Filled.NetworkCheck) {
            Hint("Mede a latência até servidores comuns. Muitos jogos no Brasil usam AWS São Paulo.")
            Spacer(Modifier.height(8.dp))
            val all = SERVERS + (if (custom.isNotBlank()) listOf(Server(custom.trim(), custom.trim(), 443)) else emptyList())
            all.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(s.name, modifier = Modifier.weight(1f))
                    val r = results[s.host]
                    when {
                        !results.containsKey(s.host) -> Text("—", color = Muted)
                        r == null || r.avg < 0 -> Text("sem resposta", color = Danger)
                        else -> {
                            val c = when {
                                r.avg < 60 -> Neon; r.avg < 120 -> Amber; else -> Danger
                            }
                            Text("${r.avg} ms", color = c, fontWeight = FontWeight.Bold)
                            Text("  ±${r.jitter}${if (r.lossPct > 0) " · ${r.lossPct}% perda" else ""}", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
            OutlinedTextField(
                value = custom, onValueChange = { custom = it }, singleLine = true,
                placeholder = { Text("Outro servidor (ex: meuservidor.com)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    pinging = true
                    results.clear()
                    scope.launch {
                        all.forEach { s -> results[s.host] = Ping.test(s.host, s.port) }
                        pinging = false
                    }
                },
                enabled = !pinging, modifier = Modifier.fillMaxWidth()
            ) { Text(if (pinging) "Testando…" else "Testar conexão") }
        }

        // ---- Resolução global
        var scale by remember { mutableFloatStateOf(Prefs.globalScale(ctx)) }
        var resMsg by remember { mutableStateOf<String?>(null) }
        SectionCard("Resolução global", Icons.Filled.AspectRatio, needShell) {
            Hint("Abaixa a resolução da tela inteira. Use se o jogo não aceitar a redução por app. Lembre de restaurar depois!")
            Spacer(Modifier.height(6.dp))
            Text(
                if (scale >= 0.99f) "Nativa (100%)" else "${(scale * 100).roundToInt()}%",
                color = if (scale >= 0.99f) Muted else Neon, fontWeight = FontWeight.Bold
            )
            Slider(
                value = scale, onValueChange = { scale = (it * 20).roundToInt() / 20f },
                valueRange = 0.5f..1f, steps = 9, enabled = hasShell
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { scope.launch { resMsg = Tweaks.applyGlobalResolution(ctx, scale) } },
                    enabled = hasShell, modifier = Modifier.weight(1f)
                ) { Text("Aplicar") }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            resMsg = if (Tweaks.resetGlobalResolution(ctx)) "Resolução nativa restaurada" else "Falha ao restaurar"
                            scale = 1f
                        }
                    },
                    enabled = hasShell, modifier = Modifier.weight(1f)
                ) { Text("Restaurar") }
            }
            resMsg?.let { Text(it, color = Cyan, fontSize = 13.sp) }
        }

        // ---- Tela e sistema
        var animOff by remember { mutableStateOf(Tweaks.animationsOff(ctx)) }
        var maxHz by remember { mutableStateOf(Prefs.maxHzOn(ctx)) }
        SectionCard("Tela e sistema", Icons.Filled.ScreenRotation, needShell) {
            ToggleRow("Desligar animações", "Menus e transições instantâneos", animOff, hasShell) { on ->
                scope.launch {
                    val ok = if (on) Tweaks.disableAnimations(ctx) else Tweaks.restoreAnimations(ctx)
                    if (ok) animOff = on
                }
            }
            ToggleRow(
                "Forçar Hz máximo",
                "Trava a tela em ${Tweaks.maxRefreshRate(ctx).roundToInt()} Hz (gasta mais bateria)",
                maxHz, hasShell
            ) { on ->
                scope.launch { if (Tweaks.setMaxRefresh(ctx, on)) maxHz = on }
            }
        }

        // ---- Mira
        var color by remember { mutableIntStateOf(Prefs.crossColor(ctx)) }
        var size by remember { mutableIntStateOf(Prefs.crossSize(ctx)) }
        var style by remember { mutableIntStateOf(Prefs.crossStyle(ctx)) }
        val saveCross = { Prefs.setCross(ctx, color, size, style) }
        SectionCard("Mira personalizada", Icons.Filled.CenterFocusStrong) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF2A3540)),
                    contentAlignment = Alignment.Center
                ) { CrosshairPreview(Color(color), size, style) }
                Spacer(Modifier.width(14.dp))
                Column {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Cruz", "Ponto", "Círculo").forEachIndexed { i, n ->
                            FilterChip(selected = style == i, onClick = { style = i; saveCross() }, label = { Text(n) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CROSS_COLORS.forEach { c ->
                            Box(
                                Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(2.dp, if (c.toArgb() == color) Color.White else Color.Transparent, CircleShape)
                                    .clickable { color = c.toArgb(); saveCross() }
                            )
                        }
                    }
                }
            }
            Text("Tamanho: $size dp", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            Slider(
                value = size.toFloat(), onValueChange = { size = it.roundToInt() },
                onValueChangeFinished = { saveCross() }, valueRange = 12f..64f
            )
            Hint("Ative a mira no perfil de cada jogo ou mostre agora pelo botão abaixo.")
        }

        // ---- Sobreposição rápida
        SectionCard("Sobreposição agora", Icons.Filled.Layers) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (Settings.canDrawOverlays(ctx)) OverlayService.start(ctx, hud = true, crosshair = true)
                    else openOverlaySettings(ctx)
                }, modifier = Modifier.weight(1f)) { Text("HUD + mira") }
                Button(onClick = {
                    if (Settings.canDrawOverlays(ctx)) OverlayService.start(ctx, hud = true, crosshair = false)
                    else openOverlaySettings(ctx)
                }, modifier = Modifier.weight(1f)) { Text("Só HUD") }
            }
            OutlinedButton(onClick = { OverlayService.stop(ctx) }, modifier = Modifier.fillMaxWidth()) {
                Text("Esconder")
            }
        }

        // ---- Restaurar
        var restoreMsg by remember { mutableStateOf<String?>(null) }
        SectionCard("Terminei de jogar", Icons.Filled.RestartAlt) {
            Hint("Desfaz tudo: resolução, animações, Hz, não perturbe e sobreposições.")
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        OverlayService.stop(ctx)
                        restoreMsg = Tweaks.restoreAll(ctx).joinToString(" · ")
                        animOff = Tweaks.animationsOff(ctx)
                        maxHz = Prefs.maxHzOn(ctx)
                        scale = Prefs.globalScale(ctx)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = Neon),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Restaurar tudo", fontWeight = FontWeight.Bold) }
            restoreMsg?.let { Text(it, color = Cyan, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}
