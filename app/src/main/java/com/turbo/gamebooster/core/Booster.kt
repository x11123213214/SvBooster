package com.turbo.gamebooster.core

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.view.Display
import com.turbo.gamebooster.overlay.OverlayService
import com.turbo.gamebooster.shell.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object Booster {

    private fun modeName(m: Int) = when (m) {
        1 -> "standard"; 3 -> "battery"; else -> "performance"
    }

    private fun f2(v: Float) = String.format(Locale.US, "%.2f", v)

    /**
     * Aplica o perfil e abre o jogo. O jogo SEMPRE abre no final, mesmo que algum ajuste falhe.
     */
    suspend fun boostAndLaunch(ctx: Context, pkg: String, p: GameProfile, log: (String) -> Unit) {
        Prefs.setLastGame(ctx, pkg)
        try {
            val done = withTimeoutOrNull(40_000) { applyProfile(ctx, pkg, p, log) }
            if (done == null) log("Alguns ajustes demoraram demais e foram pulados")
        } catch (e: Throwable) {
            log("Erro nos ajustes: ${e.message ?: e.javaClass.simpleName}")
        }
        log(if (launch(ctx, pkg)) "Abrindo o jogo… bom jogo! 🎮" else "Não consegui abrir o jogo (ele ainda está instalado?)")
    }

    private suspend fun applyProfile(ctx: Context, pkg: String, p: GameProfile, log: (String) -> Unit) {
        if (p.killBackground) {
            val r = Cleaner.clean(ctx, exclude = pkg)
            log("Limpeza: +${r.ramMb} MB de RAM" + (if (r.cacheMb > 0) ", ${r.cacheMb} MB de cache" else ""))
        }

        // Testa o Modo Turbo antes de tudo, para não esperar à toa.
        var shell = Shell.hasShell()
        if (shell) {
            val t = Shell.run("echo ok", timeoutMs = 10_000)
            if (!t.ok) {
                shell = false
                log("⚠ Modo Turbo não respondeu: ${t.err.ifBlank { t.out }.take(120)}")
                log("Abra Ajustes → Modo Turbo → Conectar agora")
            }
        } else if (p.downscale < 1f || p.fps > 0 || p.noAnimations || p.maxRefresh) {
            log("⚠ Modo Turbo desativado: resolução, FPS, animações e Hz foram pulados (ative em Ajustes)")
        }

        var watch: String? = null
        if (shell) {
            Shell.run("am force-stop $pkg") // reinicia o jogo para ele pegar a nova configuração
            val sdk = Build.VERSION.SDK_INT

            // FPS e modo do jogo (Game Mode do Android) — alguns jogos ignoram, então é um bônus.
            if (sdk >= 33) {
                Shell.run("cmd game reset $pkg")
                val fpsArg = if (p.fps > 0) " --fps ${p.fps}" else ""
                val r = Shell.run("cmd game set --mode ${p.mode} --downscale ${f2(p.downscale)}$fpsArg $pkg", "cmd game mode ${modeName(p.mode)} $pkg")
                if (p.fps > 0) log(if (r.ok) "FPS limitado em ${p.fps}" else "Este jogo não aceita limite de FPS pelo sistema")
            } else if (sdk >= 31) {
                Shell.run(
                    "device_config put game_overlay $pkg mode=${p.mode},downscaleFactor=${f2(p.downscale)}" +
                        (if (p.fps > 0) ",fps=${p.fps}" else ""),
                    "cmd game mode ${modeName(p.mode)} $pkg"
                )
            }

            // Resolução: abaixa a tela inteira enquanto o jogo estiver aberto (funciona em qualquer jogo).
            if (p.downscale < 1f) {
                log(Tweaks.applyGlobalResolution(ctx, p.downscale))
                watch = pkg
            } else if (Prefs.globalScale(ctx) < 0.99f) {
                Tweaks.resetGlobalResolution(ctx)
            }

            if (p.noAnimations) log(if (Tweaks.disableAnimations(ctx)) "Animações desligadas" else "Animações: ${Tweaks.lastError}")
            if (p.maxRefresh) log(if (Tweaks.setMaxRefresh(ctx, true)) "Tela em ${Tweaks.maxRefreshRate(ctx).roundToInt()} Hz" else "Hz: ${Tweaks.lastError}")
        }

        if (p.dnd) log(if (Tweaks.setDnd(ctx, true)) "Não perturbe ligado" else "Sem acesso ao Não perturbe (Ajustes → Permissões)")

        val canOverlay = Settings.canDrawOverlays(ctx)
        if ((p.hud || p.crosshair) && !canOverlay) log("Sem permissão de sobreposição: HUD/mira não exibidos")
        if ((p.hud || p.crosshair) && canOverlay || watch != null) {
            try {
                OverlayService.start(ctx, p.hud && canOverlay, p.crosshair && canOverlay, watch)
            } catch (e: Throwable) {
                log("Sobreposição: ${e.message}")
            }
        }
    }

    fun launch(ctx: Context, pkg: String): Boolean {
        val pm = ctx.packageManager
        val i = pm.getLaunchIntentForPackage(pkg) ?: pm.getLeanbackLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            ctx.startActivity(i); true
        } catch (e: Throwable) {
            false
        }
    }

    suspend fun resetGame(pkg: String): Boolean {
        if (!Shell.hasShell()) return false
        return Shell.run(
            "cmd game reset $pkg 2>/dev/null",
            "device_config delete game_overlay $pkg 2>/dev/null",
            "am force-stop $pkg"
        ).ok
    }
}

object Cleaner {
    data class Res(val ramMb: Long, val cacheMb: Long)

    private fun freeStorageMb() = try {
        StatFs(Environment.getDataDirectory().path).availableBytes / 1_048_576
    } catch (e: Exception) {
        0L
    }

    /** Fecha apps em segundo plano e (com Modo Turbo) limpa o cache de todos os apps. */
    suspend fun clean(ctx: Context, exclude: String? = null): Res = withContext(Dispatchers.IO) {
        val ramBefore = SystemInfo.availRamMb(ctx)
        val diskBefore = freeStorageMb()
        if (Shell.hasShell()) {
            Shell.run("pm trim-caches 1000G", timeoutMs = 20_000)
            Shell.run("am kill-all", timeoutMs = 8_000)
        }
        val am = ctx.getSystemService(ActivityManager::class.java)
        for (app in AppRepo.launchableApps(ctx)) {
            if (app.pkg != exclude) try {
                am.killBackgroundProcesses(app.pkg)
            } catch (_: Exception) {
            }
        }
        delay(900)
        Res(
            (SystemInfo.availRamMb(ctx) - ramBefore).coerceAtLeast(0),
            (freeStorageMb() - diskBefore).coerceAtLeast(0)
        )
    }
}

object Tweaks {
    @Volatile
    var lastError: String = ""

    private fun fail(r: Shell.Result): Boolean {
        lastError = r.err.ifBlank { r.out }.ifBlank { "erro ${r.code}" }.take(150)
        return false
    }
    private val ANIM_KEYS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")

    fun animationsOff(ctx: Context): Boolean =
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    suspend fun disableAnimations(ctx: Context): Boolean {
        if (!Shell.hasShell()) return false
        if (Prefs.savedAnim(ctx) == null) {
            val vals = ANIM_KEYS.map { k ->
                Shell.run("settings get global $k").out.trim().let { v -> if (v.isEmpty() || v == "null") "1.0" else v }
            }
            Prefs.saveAnim(ctx, vals)
        }
        val r = Shell.run(*ANIM_KEYS.map { "settings put global $it 0" }.toTypedArray())
        return if (r.ok) true else fail(r)
    }

    suspend fun restoreAnimations(ctx: Context): Boolean {
        if (!Shell.hasShell()) return false
        val vals = Prefs.savedAnim(ctx) ?: listOf("1.0", "1.0", "1.0")
        val r = Shell.run(*ANIM_KEYS.mapIndexed { i, k -> "settings put global $k ${vals[i]}" }.toTypedArray())
        if (r.ok) Prefs.clearAnim(ctx) else fail(r)
        return r.ok
    }

    fun maxRefreshRate(ctx: Context): Float {
        val d = ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        return d?.supportedModes?.maxOfOrNull { it.refreshRate } ?: 60f
    }

    suspend fun setMaxRefresh(ctx: Context, on: Boolean): Boolean {
        if (!Shell.hasShell()) return false
        val r = if (on) {
            val hz = maxRefreshRate(ctx).roundToInt()
            // Padrão do Android + chaves usadas por Xiaomi/Poco e Samsung (as que não existem são ignoradas).
            Shell.run(
                "settings put system peak_refresh_rate $hz.0",
                "settings put system min_refresh_rate $hz.0",
                "settings put secure user_refresh_rate $hz 2>/dev/null; true",
                "settings put system user_refresh_rate $hz 2>/dev/null; true"
            )
        } else {
            Shell.run("settings delete system min_refresh_rate", "settings delete secure user_refresh_rate 2>/dev/null; true")
        }
        if (r.ok) Prefs.setMaxHz(ctx, on) else fail(r)
        return r.ok
    }

    fun dndAllowed(ctx: Context) =
        ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

    fun setDnd(ctx: Context, on: Boolean): Boolean {
        if (!dndAllowed(ctx)) return false
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL
        )
        Prefs.setDndByUs(ctx, on)
        return true
    }

    /** Muda a resolução da tela inteira (todos os apps). Funciona mesmo quando o modo por app falha. */
    suspend fun applyGlobalResolution(ctx: Context, scale: Float): String {
        if (!Shell.hasShell()) return "Ative o Modo Turbo (aba Ajustes)."
        if (scale >= 0.99f) {
            resetGlobalResolution(ctx); return "Resolução nativa restaurada."
        }
        val sizeOut = Shell.run("wm size").out
        val densOut = Shell.run("wm density").out
        val m = Regex("Physical size: (\\d+)x(\\d+)").find(sizeOut) ?: return "Não consegui ler a resolução."
        val d = Regex("Physical density: (\\d+)").find(densOut)?.groupValues?.get(1)?.toInt()
            ?: return "Não consegui ler a densidade."
        val w = m.groupValues[1].toInt()
        val h = m.groupValues[2].toInt()
        val nw = ((w * scale).roundToInt() / 2) * 2
        val nh = ((h * scale).roundToInt() / 2) * 2
        val nd = (d * scale).roundToInt()
        val r = Shell.run("wm size ${nw}x$nh", "wm density $nd")
        return if (r.ok) {
            Prefs.setGlobalScale(ctx, scale)
            "Tela agora em ${nw}x$nh (nativa ${w}x$h)"
        } else "Erro: ${r.err.take(160)}"
    }

    suspend fun resetGlobalResolution(ctx: Context): Boolean {
        val r = Shell.run("wm size reset", "wm density reset")
        if (r.ok) Prefs.setGlobalScale(ctx, 1f)
        return r.ok
    }

    /** Temperatura da CPU lida dos sensores térmicos (precisa de Shizuku/root). */
    suspend fun cpuTemp(): Float? {
        val out = Shell.run(
            "for z in /sys/class/thermal/thermal_zone*; do echo \"$(cat \$z/type 2>/dev/null) $(cat \$z/temp 2>/dev/null)\"; done"
        ).out
        val wanted = Regex("cpu|soc|tsens|apc|big|little|cluster", RegexOption.IGNORE_CASE)
        return out.lines().mapNotNull { line ->
            val parts = line.trim().split(" ")
            if (parts.size < 2 || !wanted.containsMatchIn(parts[0])) return@mapNotNull null
            val raw = parts.last().toFloatOrNull() ?: return@mapNotNull null
            val c = if (raw > 1000) raw / 1000f else raw
            c.takeIf { it in 15f..120f }
        }.maxOrNull()
    }

    /** Pacote do app que está na tela agora (via shell). */
    suspend fun topPackage(): String? {
        val out = Shell.run(
            "dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity' | head -n 1"
        ).out
        return Regex("u\\d+ ([\\w.]+)/").find(out)?.groupValues?.get(1)
    }

    /** Desfaz tudo que o app mudou no sistema. */
    suspend fun restoreAll(ctx: Context): List<String> {
        val out = mutableListOf<String>()
        if (Prefs.dndByUs(ctx) && setDnd(ctx, false)) out += "Não perturbe desligado"
        if (Shell.hasShell()) {
            if (Prefs.savedAnim(ctx) != null && restoreAnimations(ctx)) out += "Animações restauradas"
            if (Prefs.maxHzOn(ctx) && setMaxRefresh(ctx, false)) out += "Taxa de tela automática"
            if (Prefs.globalScale(ctx) < 0.99f && resetGlobalResolution(ctx)) out += "Resolução da tela restaurada"
        }
        if (out.isEmpty()) out += "Nada para restaurar"
        return out
    }
}

object Ping {
    data class Res(val avg: Int, val jitter: Int, val lossPct: Int)

    suspend fun test(host: String, port: Int, tries: Int = 6): Res? = withContext(Dispatchers.IO) {
        val addr = try {
            InetAddress.getByName(host)
        } catch (e: Exception) {
            return@withContext null
        }
        val times = mutableListOf<Long>()
        var fail = 0
        repeat(tries) {
            try {
                Socket().use { s ->
                    val t0 = System.nanoTime()
                    s.connect(InetSocketAddress(addr, port), 2000)
                    times += (System.nanoTime() - t0) / 1_000_000
                }
            } catch (e: Exception) {
                fail++
            }
            delay(150)
        }
        if (times.isEmpty()) return@withContext Res(-1, 0, 100)
        val jitter = if (times.size < 2) 0 else
            times.zipWithNext { a, b -> abs(a - b) }.average().roundToInt()
        Res(times.average().roundToInt(), jitter, fail * 100 / tries)
    }
}
