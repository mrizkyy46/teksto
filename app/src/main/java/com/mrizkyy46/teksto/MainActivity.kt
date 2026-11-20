package com.mrizkyy46.teksto

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalContext

private val Bg = Color(0xFF101319)
private val Surface = Color(0xFF191E27)
private val Accent = Color(0xFF9DB7FF)
private val Muted = Color(0xFF8F9AAC)
private data class Doc(val id: Int, val name: String, val uri: Uri?, val text: String, val savedText: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { EditorApp() } }
}

@Composable
private fun EditorApp() {
    val context = LocalContext.current
    val docs = remember { mutableStateListOf(Doc(0, "untitled.txt", null, "", "")) }
    var active by remember { mutableIntStateOf(0) }
    var nextId by remember { mutableIntStateOf(1) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var replace by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var fontSize by remember { mutableFloatStateOf(14f) }
    var wordWrap by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val current = docs.firstOrNull { it.id == active } ?: docs.first()
    fun update(newDoc: Doc) { val index = docs.indexOfFirst { it.id == newDoc.id }; if (index >= 0) docs[index] = newDoc }
    fun saveTo(uri: Uri) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(current.text.toByteArray(Charsets.UTF_8)) }
                ?: error("Unable to open output stream")
            update(current.copy(uri = uri, savedText = current.text))
        } catch (e: Exception) { error = "Save failed: ${e.message}" }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file.txt"
            val id = nextId++; docs.add(Doc(id, name, uri, content, content)); active = id
        } catch (e: Exception) { error = "Open failed: ${e.message}" }
    }
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> if (uri != null) saveTo(uri) }
    val colors = darkColorScheme(primary = Accent, background = Bg, surface = Surface, onBackground = Color(0xFFE7EBF3), onSurface = Color(0xFFE7EBF3))
    MaterialTheme(colorScheme = colors) {
        Column(Modifier.fillMaxSize().background(Bg)) {
            Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Code, null, tint = Accent)
                Spacer(Modifier.width(10.dp))
                Text("kode", style = MaterialTheme.typography.titleLarge, color = Color.White)
                Text(".", style = MaterialTheme.typography.titleLarge, color = Accent)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { val id = nextId++; docs.add(Doc(id, "untitled$id.txt", null, "", "")); active = id }) { Icon(Icons.Outlined.Add, "New") }
                IconButton(onClick = { openFile.launch(arrayOf("*/*")) }) { Icon(Icons.Outlined.FolderOpen, "Open") }
                IconButton(onClick = { if (current.uri != null) saveTo(current.uri) else createFile.launch(current.name) }) { Icon(Icons.Outlined.Save, "Save") }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Outlined.Settings, "Settings") }
            }
            LazyRow(Modifier.fillMaxWidth().background(Surface)) {
                items(docs, key = { it.id }) { doc ->
                    val selected = doc.id == active
                    Row(Modifier.clickable { active = doc.id }.background(if (selected) Bg else Surface).padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(doc.name + if (doc.text != doc.savedText) " •" else "", color = if (selected) Accent else Muted, maxLines = 1, fontSize = 12.sp)
                        IconButton(onClick = { if (docs.size > 1) { docs.remove(doc); if (active == doc.id) active = docs.first().id } }, modifier = Modifier.size(28.dp)) { Icon(Icons.Outlined.Close, "Close tab", modifier = Modifier.size(15.dp), tint = Muted) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("EDITOR", fontSize = 10.sp, color = Muted, letterSpacing = 2.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { searchOpen = !searchOpen }, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Search, "Find", tint = Muted) }
                Text("UTF-8", fontSize = 11.sp, color = Muted)
            }
            if (searchOpen) {
                OutlinedTextField(query, { query = it }, label = { Text("Find") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                OutlinedTextField(replace, { replace = it }, label = { Text("Replace with") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp))
                TextButton(onClick = { if (query.isNotEmpty()) update(current.copy(text = current.text.replace(query, replace))) }) { Text("Replace all") }
            }
            val scroll = rememberScrollState()
            val lineCount = current.text.count { it == '\n' } + 1
            Row(Modifier.weight(1f).fillMaxWidth().horizontalScroll(rememberScrollState()).verticalScroll(scroll).padding(12.dp)) {
                Column(Modifier.width(38.dp), horizontalAlignment = Alignment.End) {
                    for (line in 1..lineCount) Text("$line", fontSize = fontSize.sp, fontFamily = FontFamily.Monospace, lineHeight = (fontSize * 1.55f).sp, color = Muted)
                }
                Spacer(Modifier.width(12.dp))
                BasicTextField(value = current.text, onValueChange = { update(current.copy(text = it)) }, modifier = Modifier.widthIn(min = 300.dp).fillMaxWidth(), textStyle = TextStyle(color = Color(0xFFE7EBF3), fontFamily = FontFamily.Monospace, fontSize = fontSize.sp, lineHeight = (fontSize * 1.55f).sp), cursorBrush = androidx.compose.ui.graphics.SolidColor(Accent), keyboardOptions = KeyboardOptions(autoCorrectEnabled = false), decorationBox = { inner -> Box { if (current.text.isEmpty()) Text("Start typing or open a file…", color = Muted, fontFamily = FontFamily.Monospace, fontSize = fontSize.sp); inner() } })
            }
            Row(Modifier.fillMaxWidth().background(Surface).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${current.name.substringAfterLast('.', "TXT").uppercase()}  •  $lineCount lines", fontSize = 11.sp, color = Accent)
                Spacer(Modifier.weight(1f))
                Text(if (current.text != current.savedText) "UNSAVED" else "SAVED", fontSize = 10.sp, color = Muted)
            }
        }
        if (showSettings) AlertDialog(onDismissRequest = { showSettings = false }, title = { Text("Editor settings") }, text = { Column { Text("Font size: ${fontSize.toInt()} sp"); Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 11f..22f); Text("UTF-8 • LF (default)", color = Muted) } }, confirmButton = { TextButton(onClick = { showSettings = false }) { Text("Done") } })
        if (error != null) AlertDialog(onDismissRequest = { error = null }, title = { Text("File error") }, text = { Text(error ?: "") }, confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } })
    }
}
