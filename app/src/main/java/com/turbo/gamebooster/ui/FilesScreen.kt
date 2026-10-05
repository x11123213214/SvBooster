package com.turbo.gamebooster.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.turbo.gamebooster.core.FEntry
import com.turbo.gamebooster.core.FileOps
import kotlinx.coroutines.launch

private sealed interface FileDialog {
    data class Rename(val e: FEntry) : FileDialog
    data class Delete(val e: FEntry) : FileDialog
    data object NewFolder : FileDialog
}

@Composable
fun FilesScreen(tick: Int, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val access = remember(tick) { FileOps.hasAllFilesAccess(ctx) }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChanged() }

    var path by rememberSaveable { mutableStateOf(FileOps.ROOT) }
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var entries by remember { mutableStateOf<List<FEntry>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var msg by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<FileDialog?>(null) }

    LaunchedEffect(path, reload, access, tick) {
        entries = null; error = null
        FileOps.list(path).onSuccess { entries = it }.onFailure { error = it.message }
    }

    val ed = editing
    if (ed != null) {
        TextEditor(ed) { editing = null }
        return
    }

    BackHandler(enabled = path != FileOps.ROOT) { path = path.substringBeforeLast('/') }

    if (!access) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard("Acesso a todos os arquivos", Icons.Filled.FolderOpen) {
                Hint("Para ver e editar os arquivos do celular, permita o acesso total ao armazenamento.")
                Spacer(Modifier.size(10.dp))
                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= 30) {
                        try {
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (e: Exception) {
                            ctx.startActivity(
                                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    } else legacyLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }, modifier = Modifier.fillMaxWidth()) { Text("Permitir acesso") }
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        // Atalhos
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                "Início" to FileOps.ROOT,
                "Download" to "${FileOps.ROOT}/Download",
                "Android/data" to "${FileOps.ROOT}/Android/data",
                "Android/obb" to "${FileOps.ROOT}/Android/obb",
            ).forEach { (n, p) -> FilterChip(selected = path == p, onClick = { path = p }, label = { Text(n) }) }
        }
        // Caminho atual
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            IconButton(onClick = { path = path.substringBeforeLast('/') }, enabled = path != FileOps.ROOT) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Subir")
            }
            Text(
                path.removePrefix(FileOps.ROOT).ifEmpty { "/" }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Muted, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { reload++ }) { Icon(Icons.Filled.Refresh, "Atualizar") }
            IconButton(onClick = { dialog = FileDialog.NewFolder }) { Icon(Icons.Filled.CreateNewFolder, "Nova pasta") }
        }
        msg?.let { Text(it, color = Cyan, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp)) }

        val list = entries
        when {
            error != null -> Text(error ?: "", color = Danger, modifier = Modifier.padding(16.dp))
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            list.isEmpty() -> Box(Modifier.padding(16.dp)) { Hint("Pasta vazia") }
            else -> LazyColumn(contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                items(list, key = { it.path }) { e ->
                    var menu by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                when {
                                    e.isDir -> path = e.path
                                    FileOps.isText(e.name) && e.size <= FileOps.MAX_EDIT -> editing = e.path
                                    else -> msg = "Só dá para editar arquivos de texto (.json, .xml, .ini, .cfg, .txt…)"
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp)
                    ) {
                        Icon(
                            when {
                                e.isDir -> Icons.Filled.Folder
                                FileOps.isText(e.name) -> Icons.Filled.Description
                                else -> Icons.Filled.InsertDriveFile
                            },
                            null, tint = if (e.isDir) Amber else Muted
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!e.isDir && e.size >= 0) Text(FileOps.fmtSize(e.size), color = Muted, fontSize = 11.sp)
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Opções") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Renomear") }, onClick = { menu = false; dialog = FileDialog.Rename(e) })
                                DropdownMenuItem(text = { Text("Apagar", color = Danger) }, onClick = { menu = false; dialog = FileDialog.Delete(e) })
                            }
                        }
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> {}
        is FileDialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Apagar \"${d.e.name}\"?") },
            text = { Text(if (d.e.isDir) "A pasta e tudo dentro dela serão apagados. Não dá para desfazer." else "Não dá para desfazer.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    scope.launch {
                        msg = FileOps.delete(d.e.path).fold({ "Apagado" }, { "Erro: ${it.message}" }); reload++
                    }
                }) { Text("Apagar", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancelar") } }
        )
        is FileDialog.Rename -> NameDialog("Renomear", d.e.name, { dialog = null }) { name ->
            scope.launch { msg = FileOps.rename(d.e.path, name).fold({ "Renomeado" }, { "Erro: ${it.message}" }); reload++ }
        }
        FileDialog.NewFolder -> NameDialog("Nova pasta", "", { dialog = null }) { name ->
            scope.launch { msg = FileOps.mkdir(path, name).fold({ "Pasta criada" }, { "Erro: ${it.message}" }); reload++ }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
        confirmButton = {
            TextButton(
                onClick = { onDismiss(); onOk(name.trim()) },
                enabled = name.isNotBlank() && !name.contains('/')
            ) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun TextEditor(path: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf<String?>(null) }
    var original by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    BackHandler { onClose() }

    LaunchedEffect(path) {
        FileOps.readText(path).onSuccess { text = it; original = it }.onFailure { status = "Erro: ${it.message}" }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Fechar") }
            Text(
                path.substringAfterLast('/'), fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val t = text ?: return@IconButton
                    scope.launch {
                        status = FileOps.writeText(ctx, path, t).fold({ original = t; it }, { "Erro: ${it.message}" })
                    }
                },
                enabled = text != null && text != original
            ) { Icon(Icons.Filled.Save, "Salvar", tint = if (text != original) Neon else Muted) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
            Icon(Icons.Filled.Warning, null, tint = Amber, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Hint("Mudar arquivos de jogos online pode corromper o jogo ou dar ban. Um backup .bak é criado ao salvar.")
        }
        status?.let { Text(it, color = Cyan, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        val t = text
        if (t == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { if (status == null) CircularProgressIndicator() }
        } else {
            OutlinedTextField(
                value = t, onValueChange = { text = it },
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            )
        }
    }
}
