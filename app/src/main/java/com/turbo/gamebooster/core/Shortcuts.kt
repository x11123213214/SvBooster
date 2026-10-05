package com.turbo.gamebooster.core

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import com.turbo.gamebooster.MainActivity

/** Atalho na tela inicial que dá Boost e abre o jogo com 1 toque. */
object Shortcuts {
    const val EXTRA_BOOST = "boost_pkg"

    fun supported(ctx: Context) = ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)

    fun pin(ctx: Context, pkg: String, label: String): Boolean {
        if (!supported(ctx)) return false
        val icon = try {
            IconCompat.createWithBitmap(ctx.packageManager.getApplicationIcon(pkg).toBitmap(192, 192))
        } catch (e: Exception) {
            IconCompat.createWithResource(ctx, com.turbo.gamebooster.R.drawable.ic_bolt)
        }
        val intent = Intent(ctx, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .putExtra(EXTRA_BOOST, pkg)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val info = ShortcutInfoCompat.Builder(ctx, "boost_$pkg")
            .setShortLabel("⚡ $label".take(25))
            .setLongLabel("Boost & jogar $label")
            .setIcon(icon)
            .setIntent(intent)
            .build()
        return ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
    }
}
