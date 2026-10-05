package com.turbo.gamebooster.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.turbo.gamebooster.shell.AdbShell
import com.turbo.gamebooster.shell.PairingService
import com.turbo.gamebooster.shell.Shell
import kotlinx.coroutines.launch

@Composable
private fun Step(n: Int, done: Boolean, title: String, content: @Composable () -> Unit = {}) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(if (done) Neon else Color(0xFF26313D))
        ) {
            if (done) Icon(Icons.Filled.CheckCircle, null, tint = Color.Black, modifier = Modifier.size(18.dp))
            else Text("$n", fontWeight = FontWeight.Black, fontSize = 13.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = if (done) Muted else Color.Unspecified)
            content()
        }
    }
}

fun wirelessDebugIntent(): Intent {
    val key = "toggle_adb_wireless"
    return Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        .putExtra(":settings:fragment_args_key", key)
        .putExtra(":settings:show_fragment_args", Bundle().apply { putString(":settings:fragment_args_key", key) })
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** Modo Turbo: o próprio app se conecta à Depuração por Wi-Fi. Não precisa de Shizuku, root ou PC. */
@Composable
fun TurboModeCard(tick: Int, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val active = remember(tick) { Shell.mode() != Shell.Mode.NONE }
    val paired = remember(tick) { AdbShell.paired() }
    val devOn = remember(tick) {
        Settings.Global.getInt(ctx.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var manual by remember { mutableStateOf(false) }
    var port by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    val startPairing = {
        PairingService.start(ctx)
        try { ctx.startActivity(wirelessDebugIntent()) } catch (_: Exception) {}
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startPairing()
        else {
            // Sem notificação: usa a digitação aqui mesmo (tela dividida)
            manual = true
            msg = "Sem permissão de notificação: digite porta e código aqui embaixo (use a tela dividida)."
        }
    }

    SectionCard("Modo Turbo", Icons.Filled.Bolt, if (active) null else "DESATIVADO") {
        if (active) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = Neon)
                Spacer(Modifier.width(8.dp))
                Text(
                    when (Shell.mode()) {
                        Shell.Mode.ADB -> if (AdbShell.connected) "Conectado pela Depuração por Wi-Fi" else "Pareado (conecta quando precisar)"
                        Shell.Mode.SHIZUKU -> "Ativado pelo Shizuku"
                        else -> "Ativado pelo root"
                    },
                    color = Neon, fontWeight = FontWeight.Bold
                )
            }
            Hint("Resolução, FPS, animações e Hz estão liberados.")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        busy = true
                        scope.launch { msg = AdbShell.selfTest(); busy = false; onChanged() }
                    },
                    enabled = !busy, modifier = Modifier.weight(1f)
                ) { Text(if (busy) "Testando…" else "Testar") }
                if (Shell.mode() == Shell.Mode.ADB) OutlinedButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            msg = if (AdbShell.connect()) "Conectado ✓" else "Não conectou: ${AdbShell.lastError ?: ""}"
                            busy = false; onChanged()
                        }
                    },
                    enabled = !busy, modifier = Modifier.weight(1f)
                ) { Text("Reconectar") }
            }
            msg?.let { Text(it, color = Cyan, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            if (Shell.mode() == Shell.Mode.ADB) Hint("Se o teste falhar: ligue a Depuração por Wi-Fi (Opções do desenvolvedor), fique no Wi-Fi e toque em Reconectar.")
            return@SectionCard
        }
        if (!AdbShell.supported()) {
            Hint("O modo turbo sem PC precisa do Android 11 ou mais novo. No seu Android, use root ou o Shizuku pelo PC.")
            return@SectionCard
        }

        Hint("Libera resolução e FPS sem Shizuku e sem root. É feito uma vez só, pelo próprio celular, em uns 2 minutos.")
        Spacer(Modifier.height(8.dp))

        Step(1, devOn, "Ative as Opções do desenvolvedor") {
            Hint("Configurações → Sobre o telefone → toque 7 vezes em \"Número da versão\".")
            if (!devOn) TextButton(onClick = {
                try { ctx.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
            }) { Text("Abrir Sobre o telefone") }
        }
        Step(2, paired, "Pareie com o Sv Booster") {
            Hint(
                "Toque no botão abaixo. Nas Configurações: ligue \"Depuração por Wi-Fi\" (se perguntar sobre a rede, marque " +
                    "\"Sempre permitir\" e toque em Permitir) → toque no nome \"Depuração por Wi-Fi\" → " +
                    "\"Parear dispositivo com código de pareamento\". Depois digite o código na notificação do Sv Booster."
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                            ctx, Manifest.permission.POST_NOTIFICATIONS
                        ) != PackageManager.PERMISSION_GRANTED
                    ) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else startPairing()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Neon, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (paired) "Parear de novo" else "Começar pareamento", fontWeight = FontWeight.Bold) }
            TextButton(onClick = { manual = !manual }) { Text(if (manual) "Esconder" else "Digitar código aqui (tela dividida)") }
            if (manual) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = port, onValueChange = { port = it.filter(Char::isDigit) }, singleLine = true,
                        label = { Text("Porta") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = code, onValueChange = { code = it.filter(Char::isDigit) }, singleLine = true,
                        label = { Text("Código") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Hint("A porta é o número depois dos \":\" na janela de pareamento (ex: 192.168.0.5:37123 → 37123).")
                OutlinedButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            msg = if (AdbShell.pair(port.toIntOrNull() ?: 0, code)) {
                                if (AdbShell.connect()) "Modo turbo ativado ✓" else "Pareado ✓ — " + (AdbShell.lastError ?: "")
                            } else "Falhou: ${AdbShell.lastError ?: "confira porta e código"}"
                            busy = false
                            onChanged()
                        }
                    },
                    enabled = !busy && port.isNotBlank() && code.length >= 6,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Pareando…" else "Parear") }
            }
        }
        Step(3, false, "Conectar") {
            Hint("Depois de pareado, o app conecta sozinho. Se reiniciar o celular, é só tocar aqui.")
            OutlinedButton(
                onClick = {
                    busy = true
                    scope.launch {
                        msg = if (AdbShell.connect()) "Modo turbo ativado ✓" else (AdbShell.lastError ?: "Não conectou")
                        busy = false
                        onChanged()
                    }
                },
                enabled = paired && !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "Conectando…" else "Conectar agora") }
        }
        msg?.let { Text(it, color = Cyan, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        Hint("Precisa estar no Wi-Fi. Seus dados não saem do celular: a conexão é do aparelho com ele mesmo.")
    }
}
