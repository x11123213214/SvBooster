package com.turbo.gamebooster

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.turbo.gamebooster.core.Booster
import com.turbo.gamebooster.core.Cleaner
import com.turbo.gamebooster.core.GameProfile
import com.turbo.gamebooster.core.Prefs
import com.turbo.gamebooster.core.Tweaks
import com.turbo.gamebooster.shell.Shell
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

/**
 * Testa as funções de verdade num Android: cada teste confere no sistema se a mudança aconteceu.
 * Os comandos rodam com o mesmo privilégio do Modo Turbo (usuário "shell").
 */
@RunWith(AndroidJUnit4::class)
class BoostTest {
    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context = inst.targetContext
    private val device = UiDevice.getInstance(inst)
    private val target = "com.android.settings" // faz o papel do "jogo"

    private fun sh(script: String): Shell.Result {
        val dir = ctx.getExternalFilesDir(null)!!
        val f = File(dir, "t${System.nanoTime()}.sh")
        f.writeText("exec 2>&1\n$script\necho \"__RC=\$?\"\n")
        val pfd = inst.uiAutomation.executeShellCommand("sh ${f.absolutePath}")
        val text = ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().readText()
        f.delete()
        val m = Regex("__RC=(\\d+)\\s*$").find(text)
        val code = m?.groupValues?.get(1)?.toIntOrNull() ?: -1
        val out = if (m != null) text.substring(0, m.range.first) else text
        return Shell.Result(code, out, if (code == 0) "" else out)
    }

    private fun top(): String =
        sh("dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity' | head -n 1").out

    private fun shot(name: String) {
        Thread.sleep(800)
        val bmp: Bitmap = inst.uiAutomation.takeScreenshot() ?: return
        val f = File(ctx.getExternalFilesDir(null), name)
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        sh("mkdir -p /data/local/tmp/svshots && cp ${f.absolutePath} /data/local/tmp/svshots/$name")
    }

    private fun restoreSystem() {
        sh(
            "wm size reset; wm density reset; " +
                "settings put global window_animation_scale 1; settings put global transition_animation_scale 1; " +
                "settings put global animator_duration_scale 1; settings delete system min_refresh_rate"
        )
    }

    @Before
    fun setUp() {
        Shell.testExecutor = { sh(it) }
        restoreSystem()
        Prefs.clearAnim(ctx)
    }

    @After
    fun tearDown() {
        restoreSystem()
        Shell.testExecutor = null
    }

    @Test
    fun t1_modoTurboExecutaComandos() {
        val r = runBlocking { withTimeout(15_000) { Shell.run("echo ok") } }
        assertTrue("comando falhou: $r", r.ok && r.out.contains("ok"))
    }

    @Test
    fun t2_resolucaoBaixaEVolta() {
        val msg = runBlocking { withTimeout(20_000) { Tweaks.applyGlobalResolution(ctx, 0.5f) } }
        val size = sh("wm size").out
        Log.i("SvTest", "resolução: $msg / $size")
        assertTrue("resolução não mudou: $msg / $size", size.contains("Override size"))
        runBlocking { Tweaks.resetGlobalResolution(ctx) }
        assertFalse("resolução não voltou", sh("wm size").out.contains("Override size"))
    }

    @Test
    fun t3_animacoesDesligamEVoltam() {
        val ok = runBlocking { withTimeout(20_000) { Tweaks.disableAnimations(ctx) } }
        assertTrue("disableAnimations falhou: ${Tweaks.lastError}", ok)
        assertEquals("0", sh("settings get global animator_duration_scale").out.trim().removeSuffix(".0"))
        assertTrue(Tweaks.animationsOff(ctx))
        runBlocking { Tweaks.restoreAnimations(ctx) }
        assertFalse(Tweaks.animationsOff(ctx))
    }

    @Test
    fun t4_hzMaximo() {
        val ok = runBlocking { withTimeout(20_000) { Tweaks.setMaxRefresh(ctx, true) } }
        assertTrue("setMaxRefresh falhou: ${Tweaks.lastError}", ok)
        val hz = Tweaks.maxRefreshRate(ctx).toInt()
        val v = sh("settings get system min_refresh_rate").out.trim()
        assertTrue("min_refresh_rate=$v (esperado $hz)", v.startsWith("$hz"))
    }

    @Test
    fun t5_limpezaNaoTrava() {
        val r = runBlocking { withTimeout(45_000) { Cleaner.clean(ctx) } }
        Log.i("SvTest", "limpeza: $r")
    }

    @Test
    fun t6_boostAbreOJogoEAplicaTudo() {
        ActivityScenario.launch(MainActivity::class.java).use {
            Thread.sleep(2000)
            val logs = mutableListOf<String>()
            val p = GameProfile(downscale = 0.5f, fps = 30, killBackground = true, noAnimations = true, maxRefresh = true)
            runBlocking { withTimeout(70_000) { Booster.boostAndLaunch(ctx, target, p) { logs += it } } }
            Log.i("SvTest", "relatório: $logs")
            device.wait(Until.hasObject(By.pkg(target).depth(0)), 8000)
            Thread.sleep(1500)
            shot("4_jogo_aberto_com_boost.png")
            assertTrue("o jogo não abriu. topo=${top()} relatório=$logs", top().contains(target))
            assertTrue("resolução não baixou. relatório=$logs", sh("wm size").out.contains("Override size"))
            assertTrue("animações não desligaram. relatório=$logs", Tweaks.animationsOff(ctx))

            // Sai do "jogo": a resolução tem que voltar sozinha.
            device.pressHome()
            var back = false
            repeat(30) {
                if (!back) {
                    Thread.sleep(1000)
                    back = !sh("wm size").out.contains("Override size")
                }
            }
            assertTrue("a resolução não voltou ao sair do jogo", back)
        }
    }

    @Test
    fun t7_boostSemModoTurboTambemAbreOJogo() {
        Shell.testExecutor = null
        ActivityScenario.launch(MainActivity::class.java).use {
            Thread.sleep(1500)
            val logs = mutableListOf<String>()
            runBlocking { withTimeout(30_000) { Booster.boostAndLaunch(ctx, target, GameProfile(downscale = 0.5f)) { logs += it } } }
            device.wait(Until.hasObject(By.pkg(target).depth(0)), 8000)
            Shell.testExecutor = { sh(it) }
            assertTrue("o jogo não abriu sem Modo Turbo. relatório=$logs", top().contains(target))
        }
    }

    @Test
    fun t8_prints() {
        Prefs.setGames(ctx, setOf(target, "com.android.chrome"))
        Prefs.setFirstRunDone(ctx)
        Prefs.setLastGame(ctx, target)
        ActivityScenario.launch(MainActivity::class.java).use {
            Thread.sleep(2500)
            shot("1_inicio.png")
            device.findObject(By.text("Ferramentas"))?.click()
            shot("2_ferramentas.png")
            device.findObject(By.text("Ajustes"))?.click()
            shot("3_ajustes.png")
            device.findObject(By.text("Início"))?.click()
            Thread.sleep(800)
            device.findObject(By.desc("Ajustar"))?.click()
            shot("5_perfil_do_jogo.png")
        }
    }
}
