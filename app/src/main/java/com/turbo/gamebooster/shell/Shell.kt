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

    enum class Mode { SHIZUKU, ROOT, ADB, NONE }

    @Volatile
    var rootAvailable = false

    /** Só para os testes automáticos: executor de comandos injetado. */
    @Volatile
    var testExecutor: ((String) -> Result)? = null

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
        testExecutor != null -> Mode.ROOT
        shizukuGranted() -> Mode.SHIZUKU
        rootAvailable -> Mode.ROOT
        AdbShell.connected || AdbShell.paired() -> Mode.ADB
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

    /**
     * Roda um ou mais comandos (em sequência, no mesmo shell).
     * Nunca trava: se passar do tempo limite, desiste e devolve erro.
     */
    suspend fun run(vararg cmds: String, timeoutMs: Long = 12_000): Result = withContext(Dispatchers.IO) {
        val script = cmds.joinToString("\n")
        testExecutor?.let { return@withContext it(script) }
        when (mode()) {
            Mode.ADB -> AdbShell.exec(script, timeoutMs)
            Mode.NONE -> Result(-1, "", "Modo Turbo desativado (aba Ajustes)")
            Mode.SHIZUKU, Mode.ROOT -> runProcess(script, timeoutMs)
        }
    }

    private fun runProcess(script: String, timeoutMs: Long): Result {
        val p: Process = try {
            if (mode() == Mode.SHIZUKU) newShizukuProcess(arrayOf("sh", "-c", script))
            else Runtime.getRuntime().exec(arrayOf("su", "-c", script))
        } catch (t: Throwable) {
            return Result(-1, "", t.message ?: t.javaClass.simpleName)
        }
        val out = StringBuffer()
        val err = StringBuffer()
        val tOut = Thread { try { out.append(p.inputStream.bufferedReader().readText()) } catch (_: Throwable) {} }
        val tErr = Thread { try { err.append(p.errorStream.bufferedReader().readText()) } catch (_: Throwable) {} }
        tOut.start(); tErr.start()
        val finished = try { p.waitFor(timeoutMs, TimeUnit.MILLISECONDS) } catch (_: Throwable) { false }
        if (!finished) {
            try { p.destroy() } catch (_: Throwable) {}
            return Result(-2, out.toString(), "Tempo esgotado")
        }
        tOut.join(1500); tErr.join(1500)
        return Result(p.exitValue(), out.toString(), err.toString())
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
