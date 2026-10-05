package com.turbo.gamebooster

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.turbo.gamebooster.core.Booster
import com.turbo.gamebooster.core.Prefs
import com.turbo.gamebooster.core.Shortcuts
import com.turbo.gamebooster.shell.AdbShell
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turbo.gamebooster.shell.Shell
import com.turbo.gamebooster.ui.Amber
import com.turbo.gamebooster.ui.FilesScreen
import com.turbo.gamebooster.ui.GamesScreen
import com.turbo.gamebooster.ui.Muted
import com.turbo.gamebooster.ui.Neon
import com.turbo.gamebooster.ui.SvLogo
import com.turbo.gamebooster.ui.PickerScreen
import com.turbo.gamebooster.ui.ProfileScreen
import com.turbo.gamebooster.ui.SetupScreen
import com.turbo.gamebooster.ui.Tag
import com.turbo.gamebooster.ui.ToolsScreen
import com.turbo.gamebooster.ui.TurboTheme
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val tick = mutableIntStateOf(0)
    private val permListener = Shizuku.OnRequestPermissionResultListener { _, _ -> tick.intValue++ }
    private val binderListener = Shizuku.OnBinderReceivedListener { tick.intValue++ }
    private val deadListener = Shizuku.OnBinderDeadListener { tick.intValue++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Shizuku.addRequestPermissionResultListener(permListener)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
        setContent { TurboTheme { App(tick.intValue) { tick.intValue++ } } }
        handleShortcut(intent)
        // Já pareado antes? Reconecta o modo turbo sozinho.
        lifecycleScope.launch {
            if (AdbShell.paired() && !Shell.shizukuGranted()) {
                AdbShell.connect(); tick.intValue++
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcut(intent)
    }

    /** Veio de um atalho da tela inicial: dá Boost no jogo e abre direto. */
    private fun handleShortcut(i: Intent?) {
        val pkg = i?.getStringExtra(Shortcuts.EXTRA_BOOST) ?: return
        i.removeExtra(Shortcuts.EXTRA_BOOST)
        lifecycleScope.launch {
            // Na abertura a frio a conexão leva alguns instantes.
            if (AdbShell.paired()) AdbShell.connect()
            var waited = 0
            while (Shell.mode() == Shell.Mode.NONE && waited < 30) { delay(100); waited++ }
            Toast.makeText(this@MainActivity, "⚡ Sv Booster: preparando…", Toast.LENGTH_SHORT).show()
            Booster.boostAndLaunch(this@MainActivity, pkg, Prefs.profile(this@MainActivity, pkg)) {}
            moveTaskToBack(true)
        }
    }

    override fun onResume() {
        super.onResume()
        tick.intValue++
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permListener)
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(deadListener)
        super.onDestroy()
    }
}

private sealed interface Screen {
    data object Tabs : Screen
    data object Picker : Screen
    data class Profile(val pkg: String) : Screen
}

@Composable
private fun App(tick: Int, refresh: () -> Unit) {
    var screen by remember { mutableStateOf<Screen>(Screen.Tabs) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val mode = remember(tick) { Shell.mode() }

    when (val s = screen) {
        is Screen.Profile -> ProfileScreen(s.pkg, mode, onSetup = { screen = Screen.Tabs; tab = 2 }) { screen = Screen.Tabs; refresh() }
        Screen.Picker -> PickerScreen { screen = Screen.Tabs; refresh() }
        Screen.Tabs -> Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = { Header(mode) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf(
                        Triple("Início", Icons.Filled.SportsEsports, 0),
                        Triple("Extras", Icons.Filled.Build, 1),
                        Triple("Arquivos", Icons.Filled.Folder, 3),
                        Triple("Ajustes", Icons.Filled.Settings, 2),
                    ).forEach { (label, icon, i) ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Icon(icon, null) }, label = { Text(label, maxLines = 1, fontSize = 12.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Color.Black, indicatorColor = Neon
                            )
                        )
                    }
                }
            }
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> GamesScreen(tick, mode, onOpen = { screen = Screen.Profile(it) }, onAdd = { screen = Screen.Picker }, onSetup = { tab = 2 })
                    1 -> ToolsScreen(mode)
                    3 -> FilesScreen(tick, refresh)
                    else -> SetupScreen(tick, refresh)
                }
            }
        }
    }
}

@Composable
private fun Header(mode: Shell.Mode) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        SvLogo(36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("SV BOOSTER", fontWeight = FontWeight.Black, fontSize = 20.sp, letterSpacing = 1.sp)
            Text("Mais FPS, menos travadas", color = Muted, fontSize = 11.sp)
        }
        when (mode) {
            Shell.Mode.NONE -> Tag("MODO BÁSICO", Amber)
            else -> Tag("TURBO ✓", Neon)
        }
    }
}
