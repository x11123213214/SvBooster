package com.turbo.gamebooster.core

import android.content.Context
import android.os.Environment
import com.turbo.gamebooster.shell.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Limpeza completa: só apaga o que é descartável (cache, miniaturas, instaladores .apk, logs e temporários).
 * Fotos, vídeos, músicas, documentos e dados de jogos/apps nunca são tocados.
 */
object DeepClean {

    data class Category(
        val id: String,
        val name: String,
        val desc: String,
        val sizeBytes: Long,   // -1 = tamanho só é medido depois de limpar
        val files: List<String> = emptyList(),
    )

    private val ROOT: String = Environment.getExternalStorageDirectory().absolutePath
    private val THUMB_DIRS get() = listOf("$ROOT/DCIM/.thumbnails", "$ROOT/Pictures/.thumbnails", "$ROOT/Movies/.thumbnails", "$ROOT/Music/.thumbnails")
    private val TEMP_EXT = setOf("log", "tmp", "temp", "dmp", "bak~")

    private fun q(p: String) = "'" + p.replace("'", "'\\''") + "'"

    private fun freeBytes() = try {
        android.os.StatFs(Environment.getDataDirectory().path).availableBytes
    } catch (e: Exception) {
        0L
    }

    /** Lista "tamanho|caminho" de arquivos via shell (o Modo Turbo enxerga a memória toda). */
    private suspend fun shFiles(findArgs: String): List<Pair<Long, String>> {
        val r = Shell.run("find $findArgs -exec stat -c '%s|%n' {} + 2>/dev/null; true", timeoutMs = 30_000)
        return r.out.lines().mapNotNull { l ->
            val i = l.indexOf('|')
            if (i <= 0) null else l.substring(0, i).toLongOrNull()?.let { it to l.substring(i + 1) }
        }
    }

    private fun walk(dir: File, maxDepth: Int, filter: (File) -> Boolean): List<File> {
        val out = mutableListOf<File>()
        fun rec(d: File, depth: Int) {
            val list = d.listFiles() ?: return
            for (f in list) {
                if (f.isDirectory) {
                    if (depth < maxDepth && f.name != "Android") rec(f, depth + 1)
                } else if (filter(f)) out += f
            }
        }
        rec(dir, 0)
        return out
    }

    suspend fun scan(ctx: Context): List<Category> = withContext(Dispatchers.IO) {
        val shell = Shell.hasShell()
        val cats = mutableListOf<Category>()

        // 1) Cache interno de todos os apps (o Android apaga com segurança; tamanho medido depois)
        if (shell) cats += Category("cache_int", "Cache dos apps", "Arquivos temporários que os apps recriam sozinhos", -1)

        // 2) Cache externo (Android/data/*/cache)
        if (shell) {
            val r = Shell.run("du -sk $ROOT/Android/data/*/cache 2>/dev/null; true", timeoutMs = 30_000)
            val kb = r.out.lines().sumOf { it.trim().split(Regex("\\s+")).firstOrNull()?.toLongOrNull() ?: 0L }
            cats += Category("cache_ext", "Cache extra dos apps", "Pastas de cache de cada app na memória interna", kb * 1024)
        }

        // 3) Miniaturas de galeria (são recriadas quando precisar)
        val thumbs: List<Pair<Long, String>> = if (shell) {
            shFiles(THUMB_DIRS.joinToString(" ") { q(it) } + " -type f")
        } else THUMB_DIRS.flatMap { d -> walk(File(d), 3) { true }.map { it.length() to it.absolutePath } }
        cats += Category("thumbs", "Miniaturas", "Prévias de fotos e vídeos (a galeria recria)", thumbs.sumOf { it.first }, thumbs.map { it.second })

        // 4) Instaladores .apk baixados
        val apks: List<Pair<Long, String>> = if (shell) {
            shFiles("${q("$ROOT/Download")} ${q(ROOT)} -maxdepth 2 -type f -iname '*.apk'")
        } else listOf(File("$ROOT/Download"), File(ROOT)).flatMap { d ->
            walk(d, 1) { it.name.endsWith(".apk", true) }.map { it.length() to it.absolutePath }
        }.distinctBy { it.second }
        cats += Category("apks", "Instaladores (.apk)", "Arquivos de instalação já usados", apks.sumOf { it.first }, apks.map { it.second })

        // 5) Logs e temporários espalhados (fora de Android/)
        val temps: List<Pair<Long, String>> = if (shell) {
            shFiles("${q(ROOT)} -maxdepth 4 -path ${q("$ROOT/Android")} -prune -o -type f \\( -iname '*.log' -o -iname '*.tmp' -o -iname '*.temp' -o -iname '*.dmp' \\)")
        } else walk(File(ROOT), 4) { it.extension.lowercase() in TEMP_EXT }.map { it.length() to it.absolutePath }
        cats += Category("temps", "Logs e temporários", "Arquivos .log, .tmp e de erro", temps.sumOf { it.first }, temps.map { it.second })

        cats
    }

    /** Apaga as categorias escolhidas. Retorna quantos bytes foram liberados. */
    suspend fun clean(ctx: Context, cats: List<Category>): Long = withContext(Dispatchers.IO) {
        val before = freeBytes()
        var counted = 0L
        for (c in cats) {
            when (c.id) {
                "cache_int" -> Shell.run("pm trim-caches 1000G", timeoutMs = 40_000)
                "cache_ext" -> {
                    Shell.run("rm -rf $ROOT/Android/data/*/cache/* 2>/dev/null; true", timeoutMs = 60_000)
                    counted += c.sizeBytes
                }
                else -> {
                    if (Shell.hasShell()) {
                        c.files.chunked(40).forEach { chunk ->
                            Shell.run("rm -f " + chunk.joinToString(" ") { q(it) } + "; true", timeoutMs = 20_000)
                        }
                    } else c.files.forEach { File(it).delete() }
                    counted += c.sizeBytes.coerceAtLeast(0)
                }
            }
        }
        val measured = freeBytes() - before
        maxOf(measured, counted)
    }

    fun fmt(bytes: Long): String = when {
        bytes < 0 -> "?"
        bytes < 1024L * 1024 -> "${bytes / 1024} KB"
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / 1048576f)
        else -> "%.2f GB".format(bytes / 1073741824f)
    }
}
