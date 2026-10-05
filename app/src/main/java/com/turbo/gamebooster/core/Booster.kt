package com.turbo.gamebooster.core

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.os.Build
import android.provider.Settings
import android.view.Display
import com.turbo.gamebooster.overlay.OverlayService
import com.turbo.gamebooster.shell.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

    /** Aplica o perfil do jogo e abre o jogo. */
    suspend fun boostAndLaunch(ctx: Context, pkg: String, p: GameProfile, log: (String) -> Unit) {
        val shell = Shell.hasShell()
        Prefs.setLastGame(ctx, pkg)

        if (p.killBackground) {
            val freed = Cleaner.clean(ctx, exclude = pkg)
            log("Apps em segundo plano fechados (+$freed MB livres)")
        }

        if (shell) {
            Shell.run("am force-stop $pkg") // o jogo precisa reiniciar para pegar a nova resolução
            val sdk = Build.VERSION.SDK_INT
            when {
                sdk >= 33 -> {
                    Shell.run("cmd game reset $pkg")
                    val fpsArg = if (p.fps > 0) " --fps ${p.fps}" else ""
                    val r = Shell.run("cmd game set --mode ${p.mode} --downscale ${f2(p.downscale)}$fpsArg $pkg")
                    Shell.run("cmd game mode ${modeName(p.mode)} $pkg")
                    if (r.ok && r.err.isBlank()) {
                        log("Resolução ${(p.downscale * 100).roundToInt()}%" +
                            (if (p.fps > 0) ", FPS travado em ${p.fps}" else "") +
                            ", modo ${modeName(p.mode)}")
                    } else {
                        log("O Android recusou a mudança por app: ${(r.err + r.out).trim().take(160)}")
                        log("Dica: use 'Resolução global' na aba Ferramentas — funciona em qualquer app.")
                    }
                }
                sdk >= 31 -> {
                    val r = Shell.run(
                        "device_config put game_overlay $pkg mode=${p.mode},downscaleFactor=${f2(p.downscale)}" +
                            (if (p.fps > 0) ",fps=${p.fps}" else ""),
                        "cmd game mode ${modeName(p.mode)} $pkg"
                    )
                    log(if (r.ok) "Resolução ${(p.downscale * 100).roundToInt()}% aplicada (Android 12)"
                    else "Falha: ${r.err.take(160)}")
                }
                else -> log("Resolução por app exige Android 12+. Use 'Resolução global' em Ferramentas.")
            }
            if (p.noAnimations && Tweaks.disableAnimations(ctx)) log("Animações do sistema desligadas")
            if (p.maxRefresh && Tweaks.setMaxRefresh(ctx, true)) log("Tela travada em ${Tweaks.maxRefreshRate(ctx).roundToInt()} Hz")
        } else if (p.downscale < 1f || p.fps > 0 || p.noAnimations || p.maxRefresh) {
            log("Shizuku/root não conectado — resolução, FPS, animações e Hz foram pulados (veja aba Ajustes)")
        }

        if (p.dnd) log(if (Tweaks.setDnd(ctx, true)) "Não perturbe ligado" else "Sem acesso ao Não perturbe (aba Ajustes)")

        var watch: String? = null
        if (p.forceGlobal && shell && p.downscale < 1f) {
            log(Tweaks.applyGlobalResolution(ctx, p.downscale) + " (volta ao normal quando você sair do jogo)")
            watch = pkg
        }

        val canOverlay = Settings.canDrawOverlays(ctx)
        if ((p.hud || p.crosshair) && !canOverlay) log("Sem permissão de sobreposição — HUD/mira não exibidos")
        if ((p.hud || p.crosshair) && canOverlay || watch != null) {
            OverlayService.start(ctx, p.hud && canOverlay, p.crosshair && canOverlay, watch)
            if (canOverlay && (p.hud || p.crosshair)) {
                log("Sobreposição ativa: " + listOfNotNull(if (p.hud) "HUD" else null, if (p.crosshair) "mira" else null).joinToString(" + "))
            }
        }

        if (launch(ctx, pkg)) log("Abrindo o jogo… bom jogo!") else log("Não consegui abrir o app")
    }

    fun launch(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return true
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
    /** Fecha processos em segundo plano. Retorna MB de RAM liberados (aprox.). */
    suspend fun clean(ctx: Context, exclude: String? = null): Long = withContext(Dispatchers.IO) {
        val before = SystemInfo.availRamMb(ctx)
        if (Shell.hasShell()) Shell.run("am kill-all")
        val am = ctx.getSystemService(ActivityManager::class.java)
        for (app in AppRepo.launchableApps(ctx)) {
            if (app.pkg != exclude) try {
                am.killBackgroundProcesses(app.pkg)
            } catch (_: Exception) {
            }
        }
        delay(900)
        (SystemInfo.availRamMb(ctx) - before).coerceAtLeast(0)
    }
}

object Tweaks {
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
        return Shell.run(*ANIM_KEYS.map { "settings put global $it 0" }.toTypedArray()).ok
    }

    suspend fun restoreAnimations(ctx: Context): Boolean {
        if (!Shell.hasShell()) return false
        val vals = Prefs.savedAnim(ctx) ?: listOf("1.0", "1.0", "1.0")
        val r = Shell.run(*ANIM_KEYS.mapIndexed { i, k -> "settings put global $k ${vals[i]}" }.toTypedArray())
        Prefs.clearAnim(ctx)
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
            Shell.run("settings put system peak_refresh_rate $hz.0", "settings put system min_refresh_rate $hz.0")
        } else {
            Shell.run("settings delete system min_refresh_rate")
        }
        if (r.ok) Prefs.setMaxHz(ctx, on)
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
        if (!Shell.hasShell()) return "Precisa do Shizuku ou root (aba Ajustes)."
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
