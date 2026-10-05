package com.turbo.gamebooster.overlay

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.turbo.gamebooster.R
import com.turbo.gamebooster.core.Prefs
import com.turbo.gamebooster.core.SystemInfo
import com.turbo.gamebooster.core.Tweaks
import com.turbo.gamebooster.shell.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Mostra HUD (temperatura, RAM, bateria, hora) e mira por cima do jogo. */
class OverlayService : Service() {

    companion object {
        private const val CH = "turbo_overlay"
        private const val ACTION_STOP = "com.turbo.gamebooster.STOP"
        private const val ACTION_RESTORE = "com.turbo.gamebooster.RESTORE"

        @Volatile
        var running = false
            private set

        fun start(ctx: Context, hud: Boolean, crosshair: Boolean, watchPkg: String? = null) {
            val i = Intent(ctx, OverlayService::class.java)
                .putExtra("hud", hud).putExtra("cross", crosshair).putExtra("watch", watchPkg)
            ContextCompat.startForegroundService(ctx, i)
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, OverlayService::class.java))
        }
    }

    private lateinit var wm: WindowManager
    private val views = mutableListOf<View>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var hudView: TextView? = null
    private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var watchJob: Job? = null

    private var hudJob: Job? = null
    private var cpuTemp: Float? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        when (intent?.action) {
            ACTION_STOP -> {
                if (watchJob != null) {
                    watchJob?.cancel()
                    scope.launch { Tweaks.resetGlobalResolution(applicationContext); stopSelf() }
                } else stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTORE -> {
                scope.launch {
                    Tweaks.restoreAll(applicationContext)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
        }
        removeViews()
        if (!Settings.canDrawOverlays(this) && intent?.getStringExtra("watch") == null) {
            stopSelf(); return START_NOT_STICKY
        }
        if (intent?.getBooleanExtra("hud", false) == true) addHud()
        if (intent?.getBooleanExtra("cross", false) == true) addCrosshair()
        intent?.getStringExtra("watch")?.let { startWatch(it) }
        if (views.isEmpty() && watchJob == null) stopSelf()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        removeViews()
        scope.cancel()
        super.onDestroy()
    }

    /** Quando o jogo sai da tela, volta a resolução nativa sozinho. */
    private fun startWatch(pkg: String) {
        watchJob?.cancel()
        watchJob = scope.launch {
            delay(6000) // tempo do jogo abrir
            var misses = 0
            while (isActive) {
                val top = Tweaks.topPackage()
                misses = if (top != null && top != pkg) misses + 1 else 0
                if (misses >= 2) {
                    Tweaks.resetGlobalResolution(applicationContext)
                    watchJob = null
                    if (views.isEmpty()) stopSelf()
                    break
                }
                delay(3000)
            }
        }
    }

    private fun startForegroundCompat() {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CH, "Sobreposição de jogo", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n = NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle("Sv Booster ativo")
            .setContentText("HUD/mira na tela • toque em Restaurar ao terminar de jogar")
            .setOngoing(true)
            .addAction(0, "Parar", pending(ACTION_STOP, 1))
            .addAction(0, "Restaurar tudo", pending(ACTION_RESTORE, 2))
            .build()
        ServiceCompat.startForeground(
            this, 7, n,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
    }

    private fun pending(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this, code, Intent(this, OverlayService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun baseParams(w: Int, h: Int, gravity: Int) = WindowManager.LayoutParams(
        w, h,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        this.gravity = gravity
        // Android 12+: sobreposições com opacidade > 0.8 bloqueiam o toque no jogo.
        alpha = 0.8f
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    @SuppressLint("ClickableViewAccessibility")
    private fun addHud() {
        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(0xAA000000.toInt())
            }
        }
        // O HUD pode ser arrastado; o resto da tela continua recebendo os toques normalmente.
        val p = baseParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        ).apply {
            flags = flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            x = Prefs.hudX(this@OverlayService, dp(12)); y = Prefs.hudY(this@OverlayService, dp(8))
        }
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        tv.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY; startX = p.x; startY = p.y
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = startX + (e.rawX - downX).toInt()
                    p.y = startY + (e.rawY - downY).toInt()
                    try { wm.updateViewLayout(v, p) } catch (_: Exception) {}
                }
                MotionEvent.ACTION_UP -> Prefs.setHudPos(this, p.x, p.y)
            }
            true
        }
        wm.addView(tv, p)
        views += tv
        hudView = tv
        hudJob = scope.launch {
            var n = 0
            while (isActive) {
                if (n % 2 == 0 && Shell.hasShell()) cpuTemp = Tweaks.cpuTemp()
                updateHud()
                n++
                delay(2000)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateHud() {
        val tv = hudView ?: return
        val s = SystemInfo.snapshot(this)
        val hot = maxOf(s.batteryTempC, (cpuTemp ?: 0f) - 20f)
        val tempColor = when {
            hot < 38f -> "🟢"
            hot < 43f -> "🟡"
            else -> "🔴"
        }
        val cpu = cpuTemp?.let { "CPU ${"%.0f".format(it)}°C  " } ?: ""
        tv.text = "$tempColor ${cpu}BAT ${"%.1f".format(s.batteryTempC)}°C  RAM ${"%.1f".format(s.ramUsedMb / 1024f)}/" +
            "${"%.1f".format(s.ramTotalMb / 1024f)}G  🔋${s.batteryPct}%${if (s.charging) "⚡" else ""}  ${clock.format(Date())}"
    }

    private fun addCrosshair() {
        val size = dp(Prefs.crossSize(this) + 8)
        val v = CrosshairView(this, Prefs.crossColor(this), Prefs.crossStyle(this))
        wm.addView(v, baseParams(size, size, Gravity.CENTER))
        views += v
    }

    private fun removeViews() {
        hudJob?.cancel()
        hudJob = null
        views.forEach { try { wm.removeView(it) } catch (_: Exception) {} }
        views.clear()
        hudView = null
    }
}

/** Estilos: 0 = cruz, 1 = ponto, 2 = círculo com ponto. */
class CrosshairView(ctx: Context, private val color: Int, private val style: Int) : View(ctx) {
    private val d = ctx.resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = this@CrosshairView.color
        strokeWidth = 2.2f * d
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99000000.toInt()
        strokeWidth = 4f * d
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@CrosshairView.color }

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = (minOf(width, height) / 2f) - 4 * d
        val gap = 4 * d
        when (style) {
            1 -> {
                c.drawCircle(cx, cy, 4f * d, outline.apply { style = Paint.Style.FILL })
                c.drawCircle(cx, cy, 3f * d, fill)
            }
            2 -> {
                c.drawCircle(cx, cy, r, outline)
                c.drawCircle(cx, cy, r, paint)
                c.drawCircle(cx, cy, 2.2f * d, fill)
            }
            else -> for (p in listOf(outline, paint)) {
                c.drawLine(cx - r, cy, cx - gap, cy, p)
                c.drawLine(cx + gap, cy, cx + r, cy, p)
                c.drawLine(cx, cy - r, cx, cy - gap, p)
                c.drawLine(cx, cy + gap, cx, cy + r, p)
            }
        }
    }
}
