package com.turbo.gamebooster.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.turbo.gamebooster.shell.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class FEntry(val name: String, val path: String, val isDir: Boolean, val size: Long)

/**
 * Acesso a arquivos. Armazenamento comum: java.io.File (precisa de "Acesso a todos os arquivos").
 * Android/data e Android/obb (bloqueados no Android 11+): via Shizuku/root.
 */
object FileOps {
    val ROOT: String = Environment.getExternalStorageDirectory().absolutePath
    const val MAX_EDIT = 1_048_576L // 1 MB

    private val TEXT_EXT = setOf(
        "txt", "json", "xml", "ini", "cfg", "conf", "properties", "log", "csv", "lua", "yaml", "yml", "md", "prop"
    )

    fun isText(name: String) = name.substringAfterLast('.', "").lowercase() in TEXT_EXT

    fun hasAllFilesAccess(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED

    fun restricted(path: String) = Build.VERSION.SDK_INT >= 30 &&
        (path.startsWith("$ROOT/Android/data") || path.startsWith("$ROOT/Android/obb"))

    private fun q(p: String) = "'" + p.replace("'", "'\\''") + "'"

    private fun needShell(path: String): String? =
        if (restricted(path) && !Shell.hasShell()) "Android/data e Android/obb precisam do Shizuku ou root (aba Ajustes)" else null

    suspend fun list(path: String): Result<List<FEntry>> = withContext(Dispatchers.IO) {
        needShell(path)?.let { return@withContext Result.failure(Exception(it)) }
        val entries = if (restricted(path)) {
            val r = Shell.run("ls -1pA ${q(path)}")
            if (!r.ok) return@withContext Result.failure(Exception(r.err.ifBlank { "Sem acesso" }))
            r.out.lines().filter { it.isNotBlank() }.map { n ->
                val name = n.trimEnd('/')
                FEntry(name, "$path/$name", n.endsWith("/"), -1)
            }
        } else {
            val arr = File(path).listFiles()
                ?: return@withContext Result.failure(Exception("Sem acesso a esta pasta"))
            arr.map { FEntry(it.name, it.absolutePath, it.isDirectory, if (it.isFile) it.length() else -1) }
        }
        Result.success(entries.sortedWith(compareByDescending<FEntry> { it.isDir }.thenBy { it.name.lowercase() }))
    }

    suspend fun readText(path: String): Result<String> = withContext(Dispatchers.IO) {
        needShell(path)?.let { return@withContext Result.failure(Exception(it)) }
        try {
            if (restricted(path)) {
                val r = Shell.run("cat ${q(path)}")
                if (!r.ok) Result.failure(Exception(r.err))
                else if (r.out.length > MAX_EDIT) Result.failure(Exception("Arquivo grande demais para editar"))
                else Result.success(r.out)
            } else {
                val f = File(path)
                if (f.length() > MAX_EDIT) Result.failure(Exception("Arquivo grande demais para editar"))
                else Result.success(f.readText())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Salva o texto. Antes de sobrescrever pela 1ª vez, cria uma cópia "<arquivo>.bak". */
    suspend fun writeText(ctx: Context, path: String, text: String): Result<String> = withContext(Dispatchers.IO) {
        needShell(path)?.let { return@withContext Result.failure(Exception(it)) }
        try {
            if (restricted(path)) {
                val tmp = File(ctx.externalCacheDir, "edit.tmp")
                tmp.writeText(text)
                val r = Shell.run(
                    "[ -e ${q("$path.bak")} ] || cp ${q(path)} ${q("$path.bak")}",
                    "cp ${q(tmp.absolutePath)} ${q(path)}"
                )
                tmp.delete()
                if (r.ok) Result.success("Salvo (backup em .bak)") else Result.failure(Exception(r.err))
            } else {
                val f = File(path)
                val bak = File("$path.bak")
                if (f.exists() && !bak.exists()) f.copyTo(bak)
                f.writeText(text)
                Result.success("Salvo (backup em .bak)")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun delete(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        needShell(path)?.let { return@withContext Result.failure(Exception(it)) }
        if (restricted(path)) {
            val r = Shell.run("rm -rf ${q(path)}")
            if (r.ok) Result.success(Unit) else Result.failure(Exception(r.err))
        } else {
            if (File(path).deleteRecursively()) Result.success(Unit) else Result.failure(Exception("Não consegui apagar"))
        }
    }

    suspend fun rename(path: String, newName: String): Result<Unit> = withContext(Dispatchers.IO) {
        needShell(path)?.let { return@withContext Result.failure(Exception(it)) }
        val target = path.substringBeforeLast('/') + "/" + newName
        if (restricted(path)) {
            val r = Shell.run("mv ${q(path)} ${q(target)}")
            if (r.ok) Result.success(Unit) else Result.failure(Exception(r.err))
        } else {
            if (File(path).renameTo(File(target))) Result.success(Unit) else Result.failure(Exception("Não consegui renomear"))
        }
    }

    suspend fun mkdir(parent: String, name: String): Result<Unit> = withContext(Dispatchers.IO) {
        needShell(parent)?.let { return@withContext Result.failure(Exception(it)) }
        val target = "$parent/$name"
        if (restricted(parent)) {
            val r = Shell.run("mkdir -p ${q(target)}")
            if (r.ok) Result.success(Unit) else Result.failure(Exception(r.err))
        } else {
            if (File(target).mkdirs()) Result.success(Unit) else Result.failure(Exception("Não consegui criar"))
        }
    }

    fun fmtSize(b: Long): String = when {
        b < 0 -> ""
        b < 1024 -> "$b B"
        b < 1_048_576 -> "${b / 1024} KB"
        b < 1_073_741_824 -> "%.1f MB".format(b / 1_048_576f)
        else -> "%.2f GB".format(b / 1_073_741_824f)
    }
}
