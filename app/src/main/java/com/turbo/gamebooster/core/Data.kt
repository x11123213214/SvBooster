package com.turbo.gamebooster.core

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.BatteryManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONObject

/** Configuração salva por jogo. */
data class GameProfile(
    val downscale: Float = 1.0f,   // 1.0 = resolução nativa
    val fps: Int = 0,              // 0 = sem limite
    val mode: Int = 2,             // 1 padrão, 2 desempenho, 3 bateria
    val killBackground: Boolean = true,
    val dnd: Boolean = false,
    val noAnimations: Boolean = false,
    val maxRefresh: Boolean = false,
    val hud: Boolean = false,
    val crosshair: Boolean = false,
    val forceGlobal: Boolean = false, // resolução da tela inteira enquanto o jogo está aberto
) {
    fun toJson(): String = JSONObject()
        .put("downscale", downscale.toDouble())
        .put("fps", fps)
        .put("mode", mode)
        .put("kill", killBackground)
        .put("dnd", dnd)
        .put("anim", noAnimations)
        .put("hz", maxRefresh)
        .put("hud", hud)
        .put("cross", crosshair)
        .put("force", forceGlobal)
        .toString()

    companion object {
        fun fromJson(s: String?): GameProfile {
            if (s == null) return GameProfile()
            return try {
                val o = JSONObject(s)
                GameProfile(
                    downscale = o.optDouble("downscale", 1.0).toFloat(),
                    fps = o.optInt("fps", 0),
                    mode = o.optInt("mode", 2),
                    killBackground = o.optBoolean("kill", true),
                    dnd = o.optBoolean("dnd", false),
                    noAnimations = o.optBoolean("anim", false),
                    maxRefresh = o.optBoolean("hz", false),
                    hud = o.optBoolean("hud", false),
                    crosshair = o.optBoolean("cross", false),
                    forceGlobal = o.optBoolean("force", false),
                )
            } catch (e: Exception) {
                GameProfile()
            }
        }
    }
}

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("turbo", Context.MODE_PRIVATE)

    fun games(ctx: Context): Set<String> = sp(ctx).getStringSet("games", emptySet())!!.toSet()
    fun setGames(ctx: Context, s: Set<String>) = sp(ctx).edit().putStringSet("games", s).apply()

    fun profile(ctx: Context, pkg: String) = GameProfile.fromJson(sp(ctx).getString("p_$pkg", null))
    fun saveProfile(ctx: Context, pkg: String, p: GameProfile) =
        sp(ctx).edit().putString("p_$pkg", p.toJson()).apply()

    // Mira
    fun crossColor(ctx: Context) = sp(ctx).getInt("cross_color", 0xFF39FF88.toInt())
    fun crossSize(ctx: Context) = sp(ctx).getInt("cross_size", 28)
    fun crossStyle(ctx: Context) = sp(ctx).getInt("cross_style", 0)
    fun setCross(ctx: Context, color: Int, size: Int, style: Int) = sp(ctx).edit()
        .putInt("cross_color", color).putInt("cross_size", size).putInt("cross_style", style).apply()

    // Estado salvo para restaurar depois
    fun savedAnim(ctx: Context): List<String>? =
        sp(ctx).getString("anim_saved", null)?.split(";")?.takeIf { it.size == 3 }
    fun saveAnim(ctx: Context, v: List<String>) = sp(ctx).edit().putString("anim_saved", v.joinToString(";")).apply()
    fun clearAnim(ctx: Context) = sp(ctx).edit().remove("anim_saved").apply()

    fun maxHzOn(ctx: Context) = sp(ctx).getBoolean("max_hz", false)
    fun setMaxHz(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("max_hz", on).apply()

    fun dndByUs(ctx: Context) = sp(ctx).getBoolean("dnd_by_us", false)
    fun setDndByUs(ctx: Context, on: Boolean) = sp(ctx).edit().putBoolean("dnd_by_us", on).apply()

    fun hudX(ctx: Context, def: Int) = sp(ctx).getInt("hud_x", def)
    fun hudY(ctx: Context, def: Int) = sp(ctx).getInt("hud_y", def)
    fun setHudPos(ctx: Context, x: Int, y: Int) = sp(ctx).edit().putInt("hud_x", x).putInt("hud_y", y).apply()

    fun firstRun(ctx: Context) = sp(ctx).getBoolean("first_run", true)
    fun setFirstRunDone(ctx: Context) = sp(ctx).edit().putBoolean("first_run", false).apply()
    fun lastGame(ctx: Context): String? = sp(ctx).getString("last_game", null)
    fun setLastGame(ctx: Context, pkg: String) = sp(ctx).edit().putString("last_game", pkg).apply()

    fun globalScale(ctx: Context) = sp(ctx).getFloat("global_scale", 1f)
    fun setGlobalScale(ctx: Context, s: Float) = sp(ctx).edit().putFloat("global_scale", s).apply()
}

data class AppEntry(val pkg: String, val label: String, val isGame: Boolean)

object AppRepo {
    @Suppress("DEPRECATION")
    fun launchableApps(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .map { ri ->
                val ai = ri.activityInfo.applicationInfo
                val game = ai.category == ApplicationInfo.CATEGORY_GAME ||
                    (ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                AppEntry(ai.packageName, ri.loadLabel(pm).toString(), game)
            }
            .distinctBy { it.pkg }
            .filter { it.pkg != ctx.packageName }
            .sortedWith(compareByDescending<AppEntry> { it.isGame }.thenBy { it.label.lowercase() })
    }

    fun label(ctx: Context, pkg: String): String? = try {
        val pm = ctx.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        null
    }

    fun icon(ctx: Context, pkg: String): ImageBitmap? = try {
        ctx.packageManager.getApplicationIcon(pkg).toBitmap(144, 144).asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

object SystemInfo {
    data class Snapshot(
        val ramUsedMb: Long,
        val ramTotalMb: Long,
        val batteryPct: Int,
        val batteryTempC: Float,
        val charging: Boolean,
    ) {
        val ramFraction get() = if (ramTotalMb == 0L) 0f else ramUsedMb.toFloat() / ramTotalMb
    }

    fun availRamMb(ctx: Context): Long {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi.availMem / 1_048_576
    }

    fun snapshot(ctx: Context): Snapshot {
        val am = ctx.getSystemService(ActivityManager::class.java)
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val bi = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val status = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val total = mi.totalMem / 1_048_576
        return Snapshot(
            ramUsedMb = total - mi.availMem / 1_048_576,
            ramTotalMb = total,
            batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else -1,
            batteryTempC = temp,
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL,
        )
    }
}
