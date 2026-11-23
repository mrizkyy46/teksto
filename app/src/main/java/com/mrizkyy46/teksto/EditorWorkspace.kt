package com.mrizkyy46.teksto

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class EditorDocument(
    val id: Int,
    name: String,
    uri: Uri?,
    value: TextFieldValue = TextFieldValue(),
    savedText: String = value.text,
    syntax: Syntax? = null,
) {
    var name by mutableStateOf(name)
        private set
    var uri by mutableStateOf(uri)
        private set
    var value by mutableStateOf(value)
        private set
    var savedText by mutableStateOf(savedText)
        private set
    val modified get() = value.text != savedText
    var syntaxOverride by mutableStateOf(syntax)
        private set
    val syntax get() = syntaxOverride ?: Syntax.fromName(name)

    private val undo = ArrayDeque<TextFieldValue>()
    private val redo = ArrayDeque<TextFieldValue>()
    private var lastEditAt: Long? = null
    private var lastEditWasDeletion = false
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    fun update(next: TextFieldValue, atomic: Boolean = false) {
        if (value.text != next.text) {
            val now = SystemClock.uptimeMillis()
            val deletion = next.text.length < value.text.length
            val previousEditAt = lastEditAt
            val simpleEdit = !atomic && value.selection.collapsed && next.selection.collapsed &&
                kotlin.math.abs(next.text.length - value.text.length) == 1 &&
                next.selection.start == value.selection.start + if (deletion) -1 else 1
            val continuesEdit = simpleEdit && previousEditAt != null &&
                now - previousEditAt < 750 && deletion == lastEditWasDeletion

            if (!continuesEdit) {
                undo.addLast(value.copy(composition = null))
                if (undo.size > 50) undo.removeFirst()
            }
            redo.clear()
            lastEditAt = if (simpleEdit) now else null
            lastEditWasDeletion = deletion
        } else if (next.selection != value.selection) {
            lastEditAt = null
        }
        value = next
        updateHistoryAvailability()
    }

    fun undo() {
        val previous = undo.removeLastOrNull() ?: return
        redo.addLast(value.copy(composition = null))
        value = previous
        lastEditAt = null
        updateHistoryAvailability()
    }

    fun redo() {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(value.copy(composition = null))
        value = next
        lastEditAt = null
        updateHistoryAvailability()
    }

    fun markSaved(file: RecentFile, text: String) {
        name = file.name
        uri = file.uri
        savedText = text
    }

    fun setSyntax(syntax: Syntax?) {
        syntaxOverride = syntax
    }

    private fun updateHistoryAvailability() {
        canUndo = undo.isNotEmpty()
        canRedo = redo.isNotEmpty()
    }
}

internal data class RecentFile(val uri: Uri, val name: String)

internal enum class FormattingToolbarPosition { Top, Bottom }

internal data class EditorSettings(
    val fontSize: Int = 14,
    val wordWrap: Boolean = false,
    val formattingToolbarPosition: FormattingToolbarPosition = FormattingToolbarPosition.Bottom,
)

internal class EditorWorkspace(application: Application) : AndroidViewModel(application) {
    private val resolver = application.contentResolver
    private val store = SessionStore(application.noBackupFilesDir)
    private val snapshots = Channel<SessionSnapshot>(Channel.CONFLATED)
    private val ready = CompletableDeferred<Unit>()
    private val fileOperations = Mutex()
    private val openDocuments = mutableStateListOf<EditorDocument>()
    val documents: List<EditorDocument> get() = openDocuments

    var activeId by mutableIntStateOf(0)
        private set
    val active get() = openDocuments.first { it.id == activeId }
    var settings by mutableStateOf(EditorSettings())
        private set
    var recentFiles by mutableStateOf(emptyList<RecentFile>())
        private set
    var loading by mutableStateOf(true)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    private var nextId = 1

    init {
        viewModelScope.launch {
            try {
                val restored = withContext(Dispatchers.IO) { store.read() }
                if (restored != null) {
                    openDocuments.addAll(restored.documents.map { it.toDocument() })
                    activeId = restored.activeId
                    nextId = restored.nextId
                    settings = restored.settings
                    recentFiles = restored.recentFiles
                }
            } catch (failure: Exception) {
                error = "Couldn’t restore the previous session. ${failure.message.orEmpty()}"
            }
            if (openDocuments.isEmpty()) createDocument()
            if (openDocuments.none { it.id == activeId }) activeId = openDocuments.first().id
            loading = false
            ready.complete(Unit)
        }
        viewModelScope.launch(Dispatchers.IO) {
            for (pending in snapshots) {
                delay(150)
                val latest = snapshots.tryReceive().getOrNull() ?: pending
                try {
                    store.write(latest)
                } catch (failure: Exception) {
                    withContext(Dispatchers.Main) {
                        if (error == null) {
                            error = "Couldn’t save a recovery copy. Save your files to keep your edits."
                        }
                    }
                }
            }
        }
    }

    fun newDocument() {
        createDocument()
        persist()
    }

    private fun createDocument() {
        val id = nextId++
        var suffix = 1
        var name = "untitled.txt"
        while (openDocuments.any { it.name == name }) {
            suffix++
            name = "untitled$suffix.txt"
        }
        openDocuments.add(EditorDocument(id, name, null))
        activeId = id
    }

    fun select(id: Int) {
        activeId = id
        persist()
    }

    fun close(id: Int) {
        val index = openDocuments.indexOfFirst { it.id == id }
        if (index == -1) return
        openDocuments.removeAt(index)
        if (openDocuments.isEmpty()) {
            createDocument()
        } else if (activeId == id) {
            activeId = openDocuments[index.coerceAtMost(openDocuments.lastIndex)].id
        }
        persist()
    }

    fun edit(document: EditorDocument, value: TextFieldValue, atomic: Boolean = false) {
        val previous = document.value
        document.update(value, atomic)
        if (value.text != previous.text || value.selection != previous.selection) persist()
    }

    fun undo(document: EditorDocument) {
        document.undo()
        persist()
    }

    fun redo(document: EditorDocument) {
        document.redo()
        persist()
    }

    fun updateSettings(value: EditorSettings) {
        settings = value
        persist()
    }

    fun setSyntax(document: EditorDocument, syntax: Syntax?) {
        document.setSyntax(syntax)
        persist()
    }

    fun dismissError() {
        error = null
    }

    fun open(uri: Uri, nameHint: String? = null) {
        runFileOperation {
            val existing = openDocuments.firstOrNull { it.uri == uri }
            if (existing != null) {
                addRecent(RecentFile(uri, existing.name))
                select(existing.id)
                return@runFileOperation
            }
            try {
                val (file, text) = withContext(Dispatchers.IO) {
                    val text = resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: error("The file could not be read.")
                    keepAccess(uri)
                    RecentFile(uri, displayName(uri, nameHint ?: "file.txt")) to text
                }
                val document = EditorDocument(nextId++, file.name, uri, TextFieldValue(text))
                openDocuments.add(document)
                addRecent(file)
                select(document.id)
            } catch (failure: Exception) {
                error = "Couldn’t open the file. ${failure.message.orEmpty()}"
            }
        }
    }

    fun save(id: Int, uri: Uri, closeAfter: Boolean, nameHint: String? = null) {
        runFileOperation {
            val document = openDocuments.firstOrNull { it.id == id } ?: return@runFileOperation
            val text = document.value.text
            val sameFile = uri == document.uri
            val name = document.name
            try {
                val file = withContext(Dispatchers.IO) {
                    resolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) }
                        ?: error("The file could not be written.")
                    if (!sameFile) keepAccess(uri)
                    RecentFile(uri, if (sameFile) name else displayName(uri, nameHint ?: name))
                }
                document.markSaved(file, text)
                addRecent(file)
                if (closeAfter && !document.modified) close(id) else persist()
            } catch (failure: Exception) {
                error = "Couldn’t save the file. ${failure.message.orEmpty()}"
            }
        }
    }

    private fun runFileOperation(operation: suspend () -> Unit) {
        viewModelScope.launch {
            ready.await()
            fileOperations.withLock {
                busy = true
                try {
                    operation()
                } finally {
                    busy = false
                }
            }
        }
    }

    fun persist() {
        if (loading) return
        snapshots.trySend(
            SessionSnapshot(
                openDocuments.map { document ->
                    SessionDocument(
                        document.id, document.name, document.uri, document.value.text,
                        document.savedText, document.value.selection, document.syntaxOverride,
                    )
                },
                activeId, nextId, settings, recentFiles,
            ),
        )
    }

    private fun addRecent(file: RecentFile) {
        recentFiles = (listOf(file) + recentFiles.filterNot { it.uri == file.uri }).take(10)
    }

    private fun displayName(uri: Uri, fallback: String): String {
        return runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun keepAccess(uri: Uri) {
        for (flag in listOf(Intent.FLAG_GRANT_READ_URI_PERMISSION, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)) {
            try {
                resolver.takePersistableUriPermission(uri, flag)
            } catch (_: SecurityException) {
                // A provider can grant temporary or read-only access.
            }
        }
    }
}

internal fun TextFieldValue.withCursor(offset: Int): TextFieldValue =
    copy(selection = TextRange(offset.coerceIn(0, text.length)), composition = null)
