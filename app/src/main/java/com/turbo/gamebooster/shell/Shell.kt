package com.turbo.gamebooster.shell

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.TimeUnit

/**
 * Executa comandos com privilégio de "shell" (ADB) via Shizuku, ou root via su.
 * Sem um dos dois, o Android não deixa um app mexer na resolução/FPS de outro app.
 */
object Shell {

    data class Result(val code: Int, val out: String, val err: String) {
        val ok get() = code == 0
    }

    enum class Mode { SHIZUKU, ROOT, NONE }

    @Volatile
    var rootAvailable = false

    fun shizukuRunning(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun shizukuGranted(): Boolean = try {
        shizukuRunning() && !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun requestShizuku() {
        try {
            if (shizukuRunning() && !shizukuGranted()) Shizuku.requestPermission(42)
        } catch (_: Throwable) {
        }
    }

    fun mode(): Mode = when {
        shizukuGranted() -> Mode.SHIZUKU
        rootAvailable -> Mode.ROOT
        else -> Mode.NONE
    }

    fun hasShell() = mode() != Mode.NONE

    suspend fun checkRoot(): Boolean = withContext(Dispatchers.IO) {
        rootAvailable = try {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val finished = p.waitFor(8, TimeUnit.SECONDS)
            finished && p.exitValue() == 0 &&
                p.inputStream.bufferedReader().readText().contains("uid=0")
        } catch (t: Throwable) {
            false
        }
        rootAvailable
    }

    /** Roda um ou mais comandos (em sequência, no mesmo shell). */
    suspend fun run(vararg cmds: String): Result = withContext(Dispatchers.IO) {
        val script = cmds.joinToString("\n")
        try {
            val p: Process = when (mode()) {
                Mode.SHIZUKU -> newShizukuProcess(arrayOf("sh", "-c", script))
                Mode.ROOT -> Runtime.getRuntime().exec(arrayOf("su", "-c", script))
                Mode.NONE -> return@withContext Result(-1, "", "Shizuku/root não conectado")
            }
            val err = StringBuilder()
            val t = Thread { err.append(p.errorStream.bufferedReader().readText()) }
            t.start()
            val out = p.inputStream.bufferedReader().readText()
            val code = p.waitFor()
            t.join(2000)
            Result(code, out, err.toString())
        } catch (t: Throwable) {
            Result(-1, "", t.message ?: t.javaClass.simpleName)
        }
    }

    // Shizuku 13 deixou newProcess privado; o acesso por reflexão é o caminho usado pela comunidade.
    private fun newShizukuProcess(cmd: Array<String>): Process {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java
        )
        m.isAccessible = true
        return m.invoke(null, cmd, null, null) as Process
    }
}
