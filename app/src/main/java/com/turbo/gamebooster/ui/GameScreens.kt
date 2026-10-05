@file:OptIn(ExperimentalLayoutApi::class)

package com.turbo.gamebooster.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turbo.gamebooster.core.AppEntry
import com.turbo.gamebooster.core.AppRepo
import com.turbo.gamebooster.core.Booster
import com.turbo.gamebooster.core.GameProfile
import com.turbo.gamebooster.core.Prefs
import com.turbo.gamebooster.core.Shortcuts
import com.turbo.gamebooster.shell.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ---------------- Lista de jogos ----------------

@Composable
fun GamesScreen(tick: Int, onOpen: (String) -> Unit, onAdd: () -> Unit) {
    val ctx = LocalContext.current
    val games = remember(tick) {
        Prefs.games(ctx).mapNotNull { pkg -> AppRepo.label(ctx, pkg)?.let { pkg to it } }
            .sortedBy { it.second.lowercase() }
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { MonitorCard() }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "MEUS JOGOS", color = Muted, fontWeight = FontWeight.Black, fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        items(games, key = { it.first }) { (pkg, label) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onOpen(pkg) }
                    .padding(12.dp)
            ) {
                AppIcon(pkg, 56.dp)
                Spacer(Modifier.height(8.dp))
                Text(
                    label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .aspectRatio(0.82f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.5.dp, Neon.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .clickable { onAdd() }
                    .padding(12.dp)
            ) {
                Icon(Icons.Filled.Add, null, tint = Neon, modifier = Modifier.size(32.dp))
                Text("Adicionar", fontSize = 12.sp, color = Neon)
            }
        }
        if (games.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Hint("Adicione seus jogos para criar um perfil de desempenho para cada um.")
            }
        }
    }
}

// ---------------- Seletor de apps ----------------

@Composable
fun PickerScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) { AppRepo.launchableApps(ctx) }
    }
    var sel by remember { mutableStateOf(Prefs.games(ctx)) }
    var q by remember { mutableStateOf("") }
    val save = { Prefs.setGames(ctx, sel); onDone() }
    BackHandler { save() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = save) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            Text("Escolher jogos", fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = save) { Text("Salvar (${sel.size})") }
        }
        OutlinedTextField(
            value = q, onValueChange = { q = it }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Buscar app") },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val filtered = list.filter { q.isBlank() || it.label.contains(q, ignoreCase = true) }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filtered, key = { it.pkg }) { a ->
                    val checked = a.pkg in sel
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { sel = if (checked) sel - a.pkg else sel + a.pkg }
                            .padding(8.dp)
                    ) {
                        AppIcon(a.pkg, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (a.isGame) Tag("JOGO", Neon)
                        }
                        Checkbox(checked = checked, onCheckedChange = { sel = if (checked) sel - a.pkg else sel + a.pkg })
                    }
                }
            }
        }
    }
}

// ---------------- Perfil do jogo ----------------

private val DOWNSCALE = listOf(1.0f, 0.9f, 0.8f, 0.75f, 0.7f, 0.6f, 0.5f, 0.4f, 0.3f)
private val FPS = listOf(0, 30, 40, 45, 60, 90, 120)
private val PRESETS = listOf(
    "Equilibrado" to GameProfile(downscale = 0.8f, fps = 60, mode = 2),
    "Máx. FPS" to GameProfile(downscale = 0.6f, fps = 0, mode = 2, noAnimations = true, maxRefresh = true),
    "Celular fraco" to GameProfile(downscale = 0.5f, fps = 30, mode = 2, noAnimations = true),
    "Economia" to GameProfile(downscale = 0.75f, fps = 30, mode = 3),
)

@Composable
fun ProfileScreen(pkg: String, mode: Shell.Mode, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val label = remember(pkg) { AppRepo.label(ctx, pkg) ?: pkg }
    var p by remember(pkg) { mutableStateOf(Prefs.profile(ctx, pkg)) }
    LaunchedEffect(p) { Prefs.saveProfile(ctx, pkg, p) }
    var log by remember { mutableStateOf(listOf<String>()) }
    var running by remember { mutableStateOf(false) }
    val hasShell = mode != Shell.Mode.NONE
    val needShell = if (hasShell) null else "SHIZUKU"
    BackHandler { onBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            AppIcon(pkg, 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                label, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard("Predefinições", Icons.Filled.AutoAwesome) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PRESETS.forEach { (name, preset) ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                p = preset.copy(
                                    killBackground = p.killBackground, dnd = p.dnd,
                                    hud = p.hud, crosshair = p.crosshair, forceGlobal = p.forceGlobal
                                )
                            },
                            label = { Text(name) }
                        )
                    }
                }
            }

            SectionCard("Resolução do jogo", Icons.Filled.AspectRatio, needShell) {
                val idx = DOWNSCALE.indexOf(p.downscale).let { if (it < 0) 0 else it }
                Text(
                    if (p.downscale >= 1f) "Nativa (100%)"
                    else "${(p.downscale * 100).roundToInt()}% — mais liso, imagem mais serrilhada",
                    color = if (p.downscale >= 1f) Muted else Neon, fontWeight = FontWeight.Bold
                )
                Slider(
                    value = idx.toFloat(),
                    onValueChange = { p = p.copy(downscale = DOWNSCALE[it.roundToInt()]) },
                    valueRange = 0f..(DOWNSCALE.size - 1).toFloat(),
                    steps = DOWNSCALE.size - 2,
                    enabled = hasShell
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Hint("Qualidade"); Hint("Desempenho")
                }
                Spacer(Modifier.height(4.dp))
                Hint("O jogo é desenhado com menos pixels e esticado na tela: a GPU trabalha menos, o FPS sobe e o celular esquenta menos.")
                Spacer(Modifier.height(6.dp))
                ToggleRow(
                    "Forçar (funciona em qualquer jogo)",
                    "Se o jogo ignorar a redução, baixa a resolução da tela inteira enquanto ele estiver aberto e volta sozinho quando você sair",
                    p.forceGlobal, hasShell, needShell
                ) { p = p.copy(forceGlobal = it) }
            }

            SectionCard("Limite de FPS", Icons.Filled.Speed, needShell) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FPS.forEach { f ->
                        FilterChip(
                            selected = p.fps == f,
                            onClick = { p = p.copy(fps = f) },
                            enabled = hasShell,
                            label = { Text(if (f == 0) "Sem limite" else "$f") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Neon, selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
                Hint("Travar em 30/40/60 evita quedas bruscas (stutter) e economiza bateria.")
            }

            SectionCard("Modo do sistema", Icons.Filled.Tune, needShell) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2 to "Desempenho", 1 to "Padrão", 3 to "Bateria").forEach { (m, n) ->
                        FilterChip(
                            selected = p.mode == m, onClick = { p = p.copy(mode = m) },
                            enabled = hasShell, label = { Text(n) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Neon, selectedLabelColor = Color.Black
                            )
                        )
                    }
                }
                Hint("Usa o Game Mode do Android: o fabricante pode priorizar CPU/GPU para o jogo.")
            }

            SectionCard("Extras", Icons.Filled.SportsEsports) {
                ToggleRow("Limpar RAM antes", "Fecha apps em segundo plano", p.killBackground) {
                    p = p.copy(killBackground = it)
                }
                ToggleRow("Não perturbe", "Sem notificações durante a partida", p.dnd) { p = p.copy(dnd = it) }
                ToggleRow("Desligar animações", "Sistema mais rápido e responsivo", p.noAnimations, hasShell, needShell) {
                    p = p.copy(noAnimations = it)
                }
                ToggleRow("Forçar Hz máximo", "Tela sempre em 90/120 Hz se suportado", p.maxRefresh, hasShell, needShell) {
                    p = p.copy(maxRefresh = it)
                }
                ToggleRow("HUD na tela", "Temperatura, RAM, bateria e hora", p.hud) { p = p.copy(hud = it) }
                ToggleRow("Mira na tela", "Configure estilo e cor em Ferramentas", p.crosshair) { p = p.copy(crosshair = it) }
            }

            if (log.isNotEmpty()) {
                SectionCard("Relatório", Icons.Filled.Terminal) {
                    log.forEach {
                        Text("› $it", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFB8C4D0))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasShell) TextButton(onClick = {
                    scope.launch {
                        log = listOf(if (Booster.resetGame(pkg)) "Configurações do jogo zeradas" else "Falha ao zerar")
                    }
                }) { Text("Zerar jogo") }
                TextButton(onClick = {
                    log = listOf(
                        if (Shortcuts.pin(ctx, pkg, label)) "Atalho enviado para a tela inicial: 1 toque = Boost & jogar"
                        else "Seu launcher não aceita atalhos fixos"
                    )
                }) { Text("Atalho") }
                TextButton(onClick = {
                    Prefs.setGames(ctx, Prefs.games(ctx) - pkg); onBack()
                }) { Text("Remover da lista", color = Danger) }
            }
        }

        Button(
            onClick = {
                if (!running) {
                    running = true
                    scope.launch {
                        log = emptyList()
                        Booster.boostAndLaunch(ctx, pkg, p) { msg -> log = log + msg }
                        running = false
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Neon, contentColor = Color.Black),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(60.dp)
        ) {
            if (running) {
                CircularProgressIndicator(color = Color.Black, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
            } else {
                Icon(Icons.Filled.Bolt, null)
                Spacer(Modifier.width(8.dp))
                Text("BOOST & JOGAR", fontWeight = FontWeight.Black, fontSize = 18.sp)
            }
        }
    }
}
