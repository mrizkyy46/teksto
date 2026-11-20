package com.mrizkyy46.teksto

import android.net.Uri
import android.util.AtomicFile
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.io.File
import java.io.FileNotFoundException
import org.json.JSONArray
import org.json.JSONObject

internal data class SessionDocument(
    val id: Int,
    val name: String,
    val uri: Uri?,
    val text: String,
    val savedText: String,
    val selection: TextRange,
    val syntax: Syntax?,
) {
    fun toDocument(): EditorDocument {
        val validSelection = TextRange(
            selection.start.coerceIn(0, text.length),
            selection.end.coerceIn(0, text.length),
        )
        return EditorDocument(id, name, uri, TextFieldValue(text, validSelection), savedText, syntax)
    }
}

internal data class SessionSnapshot(
    val documents: List<SessionDocument>,
    val activeId: Int,
    val nextId: Int,
    val settings: EditorSettings,
    val recentFiles: List<RecentFile>,
)

internal class SessionStore(directory: File) {
    private val file = AtomicFile(File(directory, "editor-session.json"))

    fun read(): SessionSnapshot? {
        val content = try {
            file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: FileNotFoundException) {
            return null
        }
        val root = JSONObject(content)
        val tabs = root.getJSONArray("documents")
        val documents = List(tabs.length()) { index ->
            val tab = tabs.getJSONObject(index)
            val text = tab.getString("text")
            SessionDocument(
                tab.getInt("id"), tab.getString("name"),
                tab.optString("uri").takeIf { it.isNotEmpty() }?.let(Uri::parse),
                text, tab.optString("savedText", text),
                TextRange(tab.optInt("selectionStart"), tab.optInt("selectionEnd")),
                Syntax.entries.firstOrNull { it.name == tab.optString("syntax") },
            )
        }
        val recents = root.getJSONArray("recent")
        return SessionSnapshot(
            documents,
            root.getInt("activeId"),
            root.getInt("nextId"),
            EditorSettings(root.optInt("fontSize", 14).coerceIn(11, 22), root.optBoolean("wordWrap")),
            List(recents.length()) { index ->
                val entry = recents.getJSONObject(index)
                RecentFile(Uri.parse(entry.getString("uri")), entry.getString("name"))
            },
        )
    }

    fun write(session: SessionSnapshot) {
        val tabs = JSONArray()
        for (tab in session.documents) {
            val item = JSONObject()
                .put("id", tab.id)
                .put("name", tab.name)
                .put("uri", tab.uri?.toString())
                .put("text", tab.text)
                .put("selectionStart", tab.selection.start)
                .put("selectionEnd", tab.selection.end)
                .put("syntax", tab.syntax?.name)
            if (tab.text != tab.savedText) item.put("savedText", tab.savedText)
            tabs.put(item)
        }
        val recents = JSONArray()
        for (recent in session.recentFiles) {
            recents.put(JSONObject().put("uri", recent.uri.toString()).put("name", recent.name))
        }
        val root = JSONObject()
            .put("documents", tabs)
            .put("activeId", session.activeId)
            .put("nextId", session.nextId)
            .put("fontSize", session.settings.fontSize)
            .put("wordWrap", session.settings.wordWrap)
            .put("recent", recents)
        val stream = file.startWrite()
        try {
            val writer = stream.bufferedWriter(Charsets.UTF_8)
            writer.write(root.toString())
            writer.flush()
            file.finishWrite(stream)
        } catch (failure: Exception) {
            file.failWrite(stream)
            throw failure
        }
    }
}
