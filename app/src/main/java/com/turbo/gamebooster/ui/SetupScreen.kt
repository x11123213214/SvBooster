package com.turbo.gamebooster.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.turbo.gamebooster.core.Tweaks
import com.turbo.gamebooster.shell.Shell
import kotlinx.coroutines.launch

@Composable
private fun StatusLine(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
            tint = if (ok) Neon else Muted, modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 14.sp)
    }
}

@Composable
fun SetupScreen(tick: Int, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val running = remember(tick) { Shell.shizukuRunning() }
    val granted = remember(tick) { Shell.shizukuGranted() }
    val overlay = remember(tick) { Settings.canDrawOverlays(ctx) }
    val dnd = remember(tick) { Tweaks.dndAllowed(ctx) }
    val notif = remember(tick) {
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
    var rootMsg by remember { mutableStateOf<String?>(null) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChanged() }

    fun open(intent: Intent) = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TurboModeCard(tick, onChanged)

        SectionCard("Shizuku (opcional)", Icons.Filled.AdminPanelSettings) {
            StatusLine(running, if (running) "Shizuku rodando" else "Shizuku não está rodando")
            StatusLine(granted, if (granted) "Permissão concedida — recursos avançados liberados" else "Permissão pendente")
            Spacer(Modifier.height(6.dp))
            Hint(
                "Não precisa: o Modo Turbo acima já faz tudo. Use o Shizuku só se você já tiver ele instalado."
            )
            Spacer(Modifier.height(10.dp))
            when {
                granted -> {}
                running -> Button(onClick = { Shell.requestShizuku() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Conceder permissão")
                }
                else -> {
                    Button(onClick = {
                        open(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")))
                    }, modifier = Modifier.fillMaxWidth()) { Text("Baixar Shizuku") }

                }
            }
        }

        SectionCard("Root (alternativa)", Icons.Filled.Security) {
            StatusLine(Shell.rootAvailable, if (Shell.rootAvailable) "Root disponível" else "Root não verificado")
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    rootMsg = if (Shell.checkRoot()) "Root OK!" else "Root não encontrado"
                    onChanged()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Verificar root") }
            rootMsg?.let { Text(it, color = Cyan, fontSize = 13.sp) }
        }

        SectionCard("Permissões", Icons.Filled.Info) {
            StatusLine(overlay, "Sobreposição (HUD e mira)")
            if (!overlay) OutlinedButton(onClick = { openOverlaySettings(ctx) }, modifier = Modifier.fillMaxWidth()) {
                Text("Permitir sobreposição")
            }
            StatusLine(dnd, "Acesso ao Não perturbe")
            if (!dnd) OutlinedButton(onClick = {
                open(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }, modifier = Modifier.fillMaxWidth()) { Text("Permitir Não perturbe") }
            StatusLine(notif, "Notificações (botão Restaurar)")
            if (!notif && Build.VERSION.SDK_INT >= 33) OutlinedButton(onClick = {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }, modifier = Modifier.fillMaxWidth()) { Text("Permitir notificações") }
        }

        SectionCard("Como funciona", Icons.Filled.Info) {
            Hint(
                "• Resolução e FPS por jogo usam o Game Mode do Android (12+). Alguns jogos/fabricantes ignoram — " +
                    "nesse caso use a Resolução global.\n" +
                    "• O jogo é reiniciado ao dar Boost para aplicar a nova resolução.\n" +
                    "• A temperatura mostrada é a da bateria (o Android não libera a da CPU para apps).\n" +
                    "• Ao terminar, use 'Restaurar tudo' em Ferramentas ou na notificação."
            )
        }
    }
}
