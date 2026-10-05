@file:OptIn(ExperimentalLayoutApi::class)

package com.turbo.gamebooster.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.AddToHomeScreen
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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

// ---------------- Níveis de turbo (o jeito fácil) ----------------

data class TurboLevel(val name: String, val desc: String, val icon: ImageVector, val color: Color, val p: GameProfile)

val LEVELS = listOf(
    TurboLevel("Leve", "90% · 60 FPS", Icons.Filled.Eco, Cyan, GameProfile(downscale = 0.9f, fps = 60, mode = 2)),
    TurboLevel("Equilibrado", "75% · 60 FPS", Icons.Filled.Balance, Neon, GameProfile(downscale = 0.75f, fps = 60, mode = 2)),
    TurboLevel(
        "Máximo", "60% · FPS livre", Icons.Filled.LocalFireDepartment, Amber,
        GameProfile(downscale = 0.6f, fps = 0, mode = 2, noAnimations = true, maxRefresh = true)
    ),
    TurboLevel(
        "Celular fraco", "50% · 30 FPS", Icons.Filled.PhoneAndroid, Pink,
        GameProfile(downscale = 0.5f, fps = 30, mode = 2, noAnimations = true)
    ),
)

fun levelOf(p: GameProfile) = LEVELS.firstOrNull {
    it.p.downscale == p.downscale && it.p.fps == p.fps && it.p.mode == p.mode
}

fun summary(p: GameProfile): String {
    val res = if (p.downscale >= 1f) "Resolução nativa" else "${(p.downscale * 100).roundToInt()}%"
    val fps = if (p.fps == 0) "FPS livre" else "${p.fps} FPS"
    return "$res · $fps"
}

// ---------------- Início ----------------

@Composable
fun GamesScreen(
    tick: Int,
    mode: Shell.Mode,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onSetup: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var local by remember { mutableIntStateOf(0) }

    // Primeira abertura: já adiciona os jogos que o Android reconhece.
    LaunchedEffect(Unit) {
        if (Prefs.firstRun(ctx)) {
            val found = withContext(Dispatchers.IO) {
                AppRepo.launchableApps(ctx).filter { it.isGame }.map { it.pkg }.toSet()
            }
            if (found.isNotEmpty()) Prefs.setGames(ctx, Prefs.games(ctx) + found)
            Prefs.setFirstRunDone(ctx)
            local++
        }
    }

    val games = remember(tick, local) {
        Prefs.games(ctx).mapNotNull { pkg -> AppRepo.label(ctx, pkg)?.let { pkg to it } }
            .sortedBy { it.second.lowercase() }
    }
    val heroPkg = Prefs.lastGame(ctx)?.takeIf { last -> games.any { it.first == last } } ?: games.firstOrNull()?.first
    var boosting by remember { mutableStateOf(false) }
    var heroLog by remember { mutableStateOf(listOf<String>()) }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (mode == Shell.Mode.NONE) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Amber.copy(alpha = 0.12f))
                        .border(1.dp, Amber.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .clickable { onSetup() }
                        .padding(14.dp)
                ) {
                    Icon(Icons.Filled.Warning, null, tint = Amber)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Libere o modo turbo", fontWeight = FontWeight.Bold)
                        Hint("Resolução e FPS precisam do Modo Turbo. Sem Shizuku, sem root: toque para ativar (2 min).")
                    }
                }
            }
        }

        if (heroPkg != null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                val label = games.firstOrNull { it.first == heroPkg }?.second ?: ""
                val prof = remember(heroPkg, tick) { Prefs.profile(ctx, heroPkg) }
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(HeroBrush)
                        .border(1.dp, Neon.copy(alpha = 0.35f), RoundedCornerShape(24.dp))
                        .padding(18.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.clickable { onOpen(heroPkg) }) { AppIcon(heroPkg, 64.dp) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("PRONTO PARA JOGAR", color = Neon, fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            Text(label, fontSize = 20.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                (levelOf(prof)?.name?.let { "Turbo $it · " } ?: "") + summary(prof),
                                color = Muted, fontSize = 13.sp
                            )
                        }
                        IconButton(onClick = { onOpen(heroPkg) }) { Icon(Icons.Filled.Tune, "Ajustar", tint = Muted) }
                    }
                    Spacer(Modifier.height(14.dp))
                    BoostButton(running = boosting, text = "JOGAR COM BOOST") {
                        boosting = true
                        scope.launch {
                            heroLog = emptyList()
                            Booster.boostAndLaunch(ctx, heroPkg, prof) { heroLog = heroLog + it }
                            boosting = false
                        }
                    }
                    if (heroLog.isNotEmpty()) Column(Modifier.padding(top = 10.dp)) {
                        heroLog.forEach { Text("› $it", color = if (it.startsWith("⚠")) Amber else Color(0xFFB8C4D0), fontSize = 12.sp) }
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) { MonitorCard() }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text("MEUS JOGOS", color = Muted, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
                Spacer(Modifier.width(8.dp))
                Tag("${games.size}", Muted)
            }
        }
        items(games, key = { it.first }) { (pkg, label) ->
            val prof = remember(pkg, tick) { Prefs.profile(ctx, pkg) }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .clickable { onOpen(pkg) }
                    .padding(vertical = 12.dp, horizontal = 8.dp)
            ) {
                AppIcon(pkg, 56.dp)
                Spacer(Modifier.height(8.dp))
                Text(label, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                val lv = levelOf(prof)
                Tag(lv?.name ?: if (prof.downscale < 1f) "${(prof.downscale * 100).roundToInt()}%" else "Nativo", lv?.color ?: Muted)
            }
        }
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .aspectRatio(0.78f)
                    .clip(RoundedCornerShape(18.dp))
                    .border(1.5.dp, Neon.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
                    .clickable { onAdd() }
                    .padding(12.dp)
            ) {
                Icon(Icons.Filled.Add, null, tint = Neon, modifier = Modifier.size(32.dp))
                Text("Adicionar", fontSize = 12.sp, color = Neon)
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
    var onlyGames by remember { mutableStateOf(false) }
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
            TextButton(onClick = save) { Text("Salvar (${sel.size})", color = Neon, fontWeight = FontWeight.Bold) }
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
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !onlyGames, onClick = { onlyGames = false }, label = { Text("Todos os apps") })
            FilterChip(selected = onlyGames, onClick = { onlyGames = true }, label = { Text("Só jogos") })
        }
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            val filtered = list.filter {
                (q.isBlank() || it.label.contains(q, ignoreCase = true)) && (!onlyGames || it.isGame)
            }
            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filtered, key = { it.pkg }) { a ->
                    val checked = a.pkg in sel
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (checked) Neon.copy(alpha = 0.08f) else Color.Transparent)
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

@Composable
private fun LevelCard(level: TurboLevel, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val s by animateFloatAsState(if (selected) 1f else 0.97f, label = "lvl")
    Column(
        modifier
            .scale(s)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) level.color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) level.color else MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(18.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(14.dp)
    ) {
        Icon(level.icon, null, tint = if (enabled) level.color else Muted, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(8.dp))
        Text(level.name, fontWeight = FontWeight.Black, color = if (enabled) Color.Unspecified else Muted)
        Text(level.desc, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun QuickToggle(icon: ImageVector, label: String, on: Boolean, enabled: Boolean = true, modifier: Modifier, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (on) Neon.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (on) Neon else MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        Icon(icon, null, tint = if (!enabled) Muted.copy(alpha = 0.5f) else if (on) Neon else Muted)
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 1, color = if (enabled) Color.Unspecified else Muted)
    }
}

@Composable
fun ProfileScreen(pkg: String, mode: Shell.Mode, onSetup: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val label = remember(pkg) { AppRepo.label(ctx, pkg) ?: pkg }
    var p by remember(pkg) { mutableStateOf(Prefs.profile(ctx, pkg)) }
    LaunchedEffect(p) { Prefs.saveProfile(ctx, pkg, p) }
    var log by remember { mutableStateOf(listOf<String>()) }
    var running by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    val hasShell = mode != Shell.Mode.NONE
    val needShell = if (hasShell) null else "TURBO"
    val current = levelOf(p)
    BackHandler { onBack() }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
            AppIcon(pkg, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, fontWeight = FontWeight.Black, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(summary(p), color = Neon, fontSize = 12.sp)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!hasShell) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Amber.copy(alpha = 0.12f))
                        .clickable { onSetup() }
                        .padding(12.dp)
                ) {
                    Icon(Icons.Filled.Warning, null, tint = Amber)
                    Spacer(Modifier.width(10.dp))
                    Text("Ative o Modo Turbo para liberar resolução e FPS →", fontSize = 13.sp, modifier = Modifier.weight(1f))
                }
            }

            // ---- Níveis
            Text("NÍVEL DE TURBO", color = Muted, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
            LEVELS.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { lv ->
                        LevelCard(lv, current == lv, hasShell, Modifier.weight(1f)) {
                            p = lv.p.copy(
                                killBackground = p.killBackground, dnd = p.dnd, hud = p.hud,
                                crosshair = p.crosshair, forceGlobal = p.forceGlobal
                            )
                        }
                    }
                }
            }
            if (current == null && hasShell) Hint("Personalizado: ${summary(p)} (veja em Avançado)")

            // ---- Atalhos rápidos
            Text("NA PARTIDA", color = Muted, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickToggle(Icons.Filled.Visibility, "HUD", p.hud, modifier = Modifier.weight(1f)) { p = p.copy(hud = !p.hud) }
                QuickToggle(Icons.Filled.CenterFocusStrong, "Mira", p.crosshair, modifier = Modifier.weight(1f)) { p = p.copy(crosshair = !p.crosshair) }
                QuickToggle(Icons.Filled.DoNotDisturbOn, "Silêncio", p.dnd, modifier = Modifier.weight(1f)) { p = p.copy(dnd = !p.dnd) }
                QuickToggle(Icons.Filled.CleaningServices, "Limpar", p.killBackground, modifier = Modifier.weight(1f)) { p = p.copy(killBackground = !p.killBackground) }
            }
            Hint("A resolução baixa enquanto o jogo estiver aberto e volta ao normal sozinha quando você sair dele.")

            // ---- Avançado
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { advanced = !advanced }
                    .padding(vertical = 8.dp)
            ) {
                Icon(Icons.Filled.Tune, null, tint = Muted)
                Spacer(Modifier.width(8.dp))
                Text("Avançado", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(if (advanced) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = Muted)
            }
            AnimatedVisibility(advanced) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionCard("Resolução", Icons.Filled.Fullscreen, needShell) {
                        val idx = DOWNSCALE.indexOf(p.downscale).let { if (it < 0) 0 else it }
                        Text(
                            if (p.downscale >= 1f) "Nativa (100%)" else "${(p.downscale * 100).roundToInt()}% — mais liso",
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
                    }
                    SectionCard("Limite de FPS", Icons.Filled.Speed, needShell) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FPS.forEach { f ->
                                FilterChip(
                                    selected = p.fps == f, onClick = { p = p.copy(fps = f) }, enabled = hasShell,
                                    label = { Text(if (f == 0) "Livre" else "$f") },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Neon, selectedLabelColor = Color.Black)
                                )
                            }
                        }
                    }
                    SectionCard("Sistema", Icons.Filled.Bolt) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(2 to "Desempenho", 1 to "Padrão", 3 to "Bateria").forEach { (m, n) ->
                                FilterChip(
                                    selected = p.mode == m, onClick = { p = p.copy(mode = m) }, enabled = hasShell,
                                    label = { Text(n) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Neon, selectedLabelColor = Color.Black)
                                )
                            }
                        }
                        ToggleRow("Limpar RAM antes", "Fecha apps em segundo plano", p.killBackground) { p = p.copy(killBackground = it) }
                        ToggleRow("Desligar animações", "Menus instantâneos", p.noAnimations, hasShell, needShell) { p = p.copy(noAnimations = it) }
                        ToggleRow("Forçar Hz máximo", "Tela sempre em 90/120 Hz", p.maxRefresh, hasShell, needShell) { p = p.copy(maxRefresh = it) }
                    }
                }
            }

            if (log.isNotEmpty()) {
                SectionCard("Relatório", Icons.Filled.Terminal) {
                    log.forEach { Text("› $it", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFB8C4D0)) }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = {
                    log = listOf(
                        if (Shortcuts.pin(ctx, pkg, label)) "Atalho criado na tela inicial: 1 toque = Boost & jogar"
                        else "Seu launcher não aceita atalhos fixos"
                    )
                }) { Icon(Icons.Filled.AddToHomeScreen, null); Spacer(Modifier.width(4.dp)); Text("Atalho") }
                if (hasShell) TextButton(onClick = {
                    scope.launch { log = listOf(if (Booster.resetGame(pkg)) "Configurações do jogo zeradas" else "Falha ao zerar") }
                }) { Icon(Icons.Filled.RestartAlt, null); Spacer(Modifier.width(4.dp)); Text("Zerar") }
                TextButton(onClick = { Prefs.setGames(ctx, Prefs.games(ctx) - pkg); onBack() }) {
                    Text("Remover", color = Danger)
                }
            }
        }

        Box(Modifier.padding(16.dp)) {
            BoostButton(running = running, text = "BOOST & JOGAR") {
                running = true
                scope.launch {
                    log = emptyList()
                    Booster.boostAndLaunch(ctx, pkg, p) { msg -> log = log + msg }
                    running = false
                }
            }
        }
    }
}
