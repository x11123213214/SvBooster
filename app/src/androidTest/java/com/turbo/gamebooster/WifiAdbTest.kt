package com.turbo.gamebooster

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import androidx.core.app.RemoteInput
import com.turbo.gamebooster.shell.PairingService
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.turbo.gamebooster.core.Tweaks
import com.turbo.gamebooster.shell.AdbShell
import com.turbo.gamebooster.shell.Shell
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Teste de ponta a ponta do Modo Turbo como no celular de verdade:
 * liga a Depuração por Wi-Fi, abre "Parear com código", lê o código da tela,
 * pareia pelo Sv Booster, conecta e roda comandos pela conexão própria do app.
 */
@RunWith(AndroidJUnit4::class)
class WifiAdbTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = inst.targetContext
    private val device = UiDevice.getInstance(inst)

    private fun sh(script: String): String {
        val f = File(ctx.getExternalFilesDir(null), "w${System.nanoTime()}.sh")
        f.writeText("exec 2>&1\n$script\n")
        val pfd = inst.uiAutomation.executeShellCommand("sh ${f.absolutePath}")
        val text = ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().readText()
        f.delete()
        return text
    }

    private fun shot(name: String) {
        Thread.sleep(600)
        val bmp: Bitmap = inst.uiAutomation.takeScreenshot() ?: return
        val f = File(ctx.getExternalFilesDir(null), name)
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        sh("mkdir -p /data/local/tmp/svshots && cp ${f.absolutePath} /data/local/tmp/svshots/$name")
    }

    private fun screenTexts(): List<String> =
        device.findObjects(By.textContains("")).mapNotNull { it.text }

    /** Responde "Permitir depuração por Wi-Fi nesta rede?" (sempre permitir). */
    private fun allowNetworkIfAsked() {
        if (device.wait(Until.hasObject(By.textContains("Allow wireless debugging")), 2500) == true) {
            shot("w0_permitir_rede.png")
            device.findObject(By.textContains("Always allow"))?.click()
            Thread.sleep(400)
            device.findObject(By.text("Allow"))?.click() ?: device.findObject(By.text("ALLOW"))?.click()
            Thread.sleep(2500)
        }
    }

    @Test
    fun w1_pareiaConectaERodaComandos() {
        Shell.testExecutor = null
        sh("settings put global development_settings_enabled 1; settings put global adb_wifi_enabled 1")
        Thread.sleep(3000)
        Log.i("SvTest", "wifi: " + sh("dumpsys wifi | grep -m1 'mWifiInfo'").take(200))

        // Como no uso real: abre o Sv Booster e toca em "Começar pareamento" (inicia o serviço da notificação).
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        Thread.sleep(1500)
        PairingService.start(ctx)
        Thread.sleep(1000)

        // A pergunta "permitir nesta rede?" aparece ao ligar a depuração: responde primeiro.
        allowNetworkIfAsked()
        Log.i("SvTest", "adb_wifi_enabled=" + sh("settings get global adb_wifi_enabled").trim())
        Log.i("SvTest", "dev-activities: " + sh("cmd package query-activities --brief -a android.settings.APPLICATION_DEVELOPMENT_SETTINGS").replace("\n", " | "))
        Log.i("SvTest", "wireless-activities: " + sh("dumpsys package com.android.settings | grep -iE 'Wireless|AdbWireless|DEVELOPMENT' | head -20").replace("\n", " | "))

        // Vai até Opções do desenvolvedor → Depuração por Wi-Fi e liga a chave (como uma pessoa faria)
        for (attempt in 1..6) {
            when {
                device.hasObject(By.textContains("Pair device with pairing code")) -> break
                device.hasObject(By.textContains("IP address")) -> {
                    // Já está ligada: rola até "Pair device with pairing code"
                    var n = 0
                    while (!device.hasObject(By.textContains("Pair device with pairing code")) && n < 10) {
                        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 3, 20)
                        Thread.sleep(300); n++
                    }
                }
                device.hasObject(By.text("Use wireless debugging")) -> {
                    device.findObject(By.text("Use wireless debugging"))?.click()
                    allowNetworkIfAsked()
                    device.wait(Until.hasObject(By.textContains("Pair device with pairing code")), 8000)
                }
                device.hasObject(By.text("Wireless debugging")) -> {
                    device.findObject(By.text("Wireless debugging"))?.click()
                    device.wait(Until.hasObject(By.text("Use wireless debugging")), 6000)
                }
                device.hasObject(By.text("Use developer options")) -> {
                    // Volta ao topo e desce devagar até achar "Wireless debugging"
                    try { UiScrollable(UiSelector().scrollable(true)).flingToBeginning(10) } catch (_: Exception) {}
                    var n = 0
                    while (!device.hasObject(By.text("Wireless debugging")) && n < 25) {
                        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4, device.displayWidth / 2, device.displayHeight / 2, 20)
                        Thread.sleep(300); n++
                    }
                }
                else -> {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    device.wait(Until.hasObject(By.text("Use developer options")), 6000)
                    allowNetworkIfAsked()
                    if (!device.hasObject(By.text("Use developer options"))) {
                        try { UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView("System") } catch (_: Exception) {}
                        device.findObject(By.text("System"))?.click()
                        device.wait(Until.hasObject(By.textContains("Developer options")), 6000)
                        try { UiScrollable(UiSelector().scrollable(true)).scrollTextIntoView("Developer options") } catch (_: Exception) {}
                        device.findObject(By.text("Developer options"))?.click()
                        device.wait(Until.hasObject(By.text("Use developer options")), 6000)
                    }
                }
            }
            Log.i("SvTest", "passo $attempt: " + screenTexts().take(10))
        }
        shot("w1_depuracao_wifi.png")
        device.findObject(By.textContains("Pair device with pairing code"))?.click()
        allowNetworkIfAsked()
        device.wait(Until.hasObject(By.textContains("pairing code")), 8000)
        Thread.sleep(1500)
        shot("w2_codigo_pareamento.png")

        val texts = screenTexts()
        Log.i("SvTest", "tela: $texts")
        val code = texts.firstNotNullOfOrNull { Regex("^\\d{6}$").find(it.trim())?.value }
        val port = texts.firstNotNullOfOrNull { Regex(":(\\d{4,5})$").find(it.trim())?.groupValues?.get(1)?.toInt() }
        assertTrue("não achei código/porta na tela: $texts", code != null && port != null)

        // O serviço tem que achar a porta sozinho (mDNS)…
        var w = 0
        while (!PairingService.status.contains("encontrado") && w < 20) { Thread.sleep(500); w++ }
        Log.i("SvTest", "serviço: ${PairingService.status}")
        shot("w3_notificacao_encontrou.png")
        assertTrue("o app não achou o pareamento sozinho: ${PairingService.status}", PairingService.status.contains("encontrado"))

        // …e o código é "digitado" na notificação (mesma Intent que a resposta da notificação envia).
        val reply = Intent(ctx, PairingService::class.java).setAction(PairingService.ACTION_CODE)
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(PairingService.KEY).build()), reply,
            Bundle().apply { putCharSequence(PairingService.KEY, code) }
        )
        ctx.startService(reply)
        w = 0
        while (!(PairingService.status.contains("✓") || PairingService.status.contains("errado") || PairingService.status.contains("Não achei")) && w < 80) {
            Thread.sleep(500); w++
        }
        Log.i("SvTest", "serviço depois do código: ${PairingService.status} erro=${AdbShell.lastError}")
        shot("w4_notificacao_resultado.png")
        assertTrue("pareamento pela notificação falhou: ${PairingService.status} / ${AdbShell.lastError}", AdbShell.paired())
        assertTrue("não conectou depois de parear: ${PairingService.status} / ${AdbShell.lastError}", AdbShell.connected || runBlocking { AdbShell.connect() })
        assertTrue("modo deveria ser ADB: ${Shell.mode()}", Shell.mode() == Shell.Mode.ADB)

        val r = runBlocking { withTimeout(20_000) { Shell.run("id -un; wm size") } }
        Log.i("SvTest", "comando via Wi-Fi: $r")
        assertTrue("comando via Wi-Fi falhou: $r", r.ok && r.out.contains("shell"))

        val msg = runBlocking { withTimeout(20_000) { Tweaks.applyGlobalResolution(ctx, 0.5f) } }
        val size = sh("wm size")
        Log.i("SvTest", "resolução via Wi-Fi: $msg / $size")
        assertTrue("resolução via Wi-Fi não mudou: $msg / $size", size.contains("Override size"))
        runBlocking { Tweaks.resetGlobalResolution(ctx) }

        val ok = runBlocking { withTimeout(20_000) { Tweaks.disableAnimations(ctx) } }
        assertTrue("animações via Wi-Fi: ${Tweaks.lastError}", ok && Tweaks.animationsOff(ctx))
        runBlocking { Tweaks.restoreAnimations(ctx) }

        // Reconexão depois de perder a conexão (como quando o app é reaberto)
        val again = runBlocking { withTimeout(20_000) { AdbShell.selfTest() } }
        Log.i("SvTest", "autoteste: $again")
        assertTrue("autoteste: $again", again.startsWith("OK"))
        sh("wm size reset; wm density reset")
    }
}
