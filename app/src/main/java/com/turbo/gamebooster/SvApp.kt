package com.turbo.gamebooster

import android.app.Application
import com.turbo.gamebooster.shell.AdbShell
import org.conscrypt.Conscrypt
import java.security.Security

class SvApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Conscrypt é necessário para o pareamento da Depuração por Wi-Fi (TLS 1.3).
        try {
            Security.insertProviderAt(Conscrypt.newProvider(), 1)
        } catch (_: Throwable) {
        }
        AdbShell.init(this)
    }
}
