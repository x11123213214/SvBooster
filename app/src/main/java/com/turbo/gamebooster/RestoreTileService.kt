package com.turbo.gamebooster

import android.service.quicksettings.TileService
import android.widget.Toast
import com.turbo.gamebooster.core.Tweaks
import com.turbo.gamebooster.overlay.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Bloco das Configurações Rápidas: "Restaurar tudo" com 1 toque, de dentro do jogo. */
class RestoreTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onClick() {
        super.onClick()
        scope.launch {
            OverlayService.stop(applicationContext)
            val r = Tweaks.restoreAll(applicationContext)
            Toast.makeText(applicationContext, r.joinToString(" · "), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
