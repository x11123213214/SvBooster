package com.turbo.gamebooster.shell

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.turbo.gamebooster.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Pareamento com a Depuração por Wi-Fi sem sair das Configurações:
 * o app acha a porta sozinho e você digita o código direto na notificação.
 */
class PairingService : Service() {

    companion object {
        private const val CH = "sv_pair"
        private const val NID = 9
        const val ACTION_CODE = "com.sv.booster.PAIR_CODE"
        const val KEY = "pair_code"

        @Volatile
        var status: String = ""
            private set

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, PairingService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var nsd: NsdManager? = null
    private var listener: NsdManager.DiscoveryListener? = null
    private var timeout: Job? = null

    @Volatile
    private var port = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CODE) {
            val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY)?.toString()?.trim()
            if (!code.isNullOrEmpty()) doPair(code) else show("Digite o código de 6 dígitos", ask = true)
            return START_NOT_STICKY
        }
        show("Procurando o pareamento…", "Em Depuração por Wi-Fi, toque em \"Parear dispositivo com código de pareamento\"", ask = true)
        startDiscovery()
        timeout?.cancel()
        timeout = scope.launch { delay(5 * 60_000L); stopSelf() }
        return START_NOT_STICKY
    }

    private fun show(title: String, text: String? = null, ask: Boolean = false, alert: Boolean = false) {
        status = title
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Pareamento do modo turbo", NotificationManager.IMPORTANCE_HIGH))
        val b = NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_bolt)
            .setContentTitle("Sv Booster: $title")
            .setContentText(text ?: if (port > 0) "Pareamento encontrado. Toque em \"Digitar código\"." else null)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text ?: ""))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(!alert)
            .setOngoing(ask)
        if (ask) {
            val ri = RemoteInput.Builder(KEY).setLabel("Código de pareamento").build()
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getService(
                this, 3, Intent(this, PairingService::class.java).setAction(ACTION_CODE), flags
            )
            b.addAction(NotificationCompat.Action.Builder(0, "Digitar código", pi).addRemoteInput(ri).build())
        }
        ServiceCompat.startForeground(
            this, NID, b.build(),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
    }

    private fun startDiscovery() {
        if (listener != null) return
        val nm = getSystemService(NsdManager::class.java) ?: return
        nsd = nm
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                nm.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(si: NsdServiceInfo) {
                        port = si.port
                        scope.launch { show("Pareamento encontrado!", "Toque em \"Digitar código\" e escreva o código que aparece na tela.", ask = true, alert = true) }
                    }
                })
            }
        }
        listener = l
        try {
            nm.discoverServices("_adb-tls-pairing._tcp", NsdManager.PROTOCOL_DNS_SD, l)
        } catch (_: Exception) {
        }
    }

    private fun doPair(code: String) {
        scope.launch {
            show("Pareando…")
            var waited = 0
            while (port < 0 && waited < 30) {
                delay(500); waited++
            }
            if (port < 0) {
                show("Não achei o pareamento", "Deixe aberta a janela \"Parear dispositivo com código\" e tente de novo.", ask = true)
                return@launch
            }
            if (AdbShell.pair(port, code)) {
                show("Pareado! Conectando…")
                val ok = AdbShell.connect()
                show(if (ok) "Modo turbo ativado ✓" else "Pareado ✓ — abra o Sv Booster", if (ok) "Já pode voltar ao app." else AdbShell.lastError, alert = true)
                delay(4000)
                ServiceCompat.stopForeground(this@PairingService, ServiceCompat.STOP_FOREGROUND_DETACH)
                stopSelf()
            } else {
                show("Código errado", "${AdbShell.lastError ?: ""}\nConfira o código e tente de novo.", ask = true, alert = true)
            }
        }
    }

    override fun onDestroy() {
        try {
            listener?.let { nsd?.stopServiceDiscovery(it) }
        } catch (_: Exception) {
        }
        scope.cancel()
        super.onDestroy()
    }
}
