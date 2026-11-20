package com.mrizkyy46.teksto

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.launch

internal val EditorBackground = Color(0xFF101319)
internal val EditorPanel = Color(0xFF191E27)
internal val EditorAccent = Color(0xFF9DB7FF)
internal val EditorMuted = Color(0xFF8F9AAC)
internal val EditorInk = Color(0xFFE7EBF3)

class MainActivity : ComponentActivity() {
    private val workspace by lazy { ViewModelProvider(this)[EditorWorkspace::class.java] }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent { EditorApp(workspace) }
    }
}

@Composable
private fun EditorApp(workspace: EditorWorkspace) {
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showLineDialog by rememberSaveable { mutableStateOf(false) }
    var closingTab by rememberSaveable { mutableIntStateOf(-1) }
    var namingTab by rememberSaveable { mutableIntStateOf(-1) }
    var pendingSaveTab by rememberSaveable { mutableIntStateOf(-1) }
    var pendingSaveName by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCloseAfterSave by rememberSaveable { mutableStateOf(false) }
    var jsonAction by remember { mutableStateOf<Boolean?>(null) }
    val focus = LocalFocusManager.current
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) workspace.open(uri)
    }
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val id = pendingSaveTab
        val name = pendingSaveName
        val closeAfter = pendingCloseAfterSave
        pendingSaveTab = -1
        pendingSaveName = null
        pendingCloseAfterSave = false
        if (result.resultCode == Activity.RESULT_OK && id != -1) {
            result.data?.data?.let { workspace.save(id, it, closeAfter, name) }
        }
    }
    val fileActionsEnabled = !workspace.busy && pendingSaveTab == -1

    fun save(document: EditorDocument, copy: Boolean = false, closeAfter: Boolean = false) {
        if (!fileActionsEnabled) return
        val uri = document.uri
        if (uri != null && !copy) {
            workspace.save(document.id, uri, closeAfter)
        } else {
            namingTab = document.id
            pendingCloseAfterSave = closeAfter
        }
    }

    fun showMessage(message: String) {
        scope.launch {
            snackbars.currentSnackbarData?.dismiss()
            snackbars.showSnackbar(message)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = EditorAccent,
            background = EditorBackground,
            surface = EditorPanel,
            onBackground = EditorInk,
            onSurface = EditorInk,
        ),
    ) {
        if (workspace.loading) {
            Box(
                Modifier.fillMaxSize().background(EditorBackground),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            return@MaterialTheme
        }

        val current = workspace.active
        var previewRequested by rememberSaveable(current.id) { mutableStateOf(false) }
        val previewOpen = previewRequested && current.syntax.supportsPreview
        BackHandler(previewOpen) { previewRequested = false }
        BackHandler(drawer.isOpen) { scope.launch { drawer.close() } }
        ModalNavigationDrawer(
            drawerState = drawer,
            gesturesEnabled = drawer.isOpen,
            drawerContent = {
                EditorDrawer(
                    documents = workspace.documents,
                    activeId = current.id,
                    recent = workspace.recentFiles,
                    fileActionsEnabled = fileActionsEnabled,
                    onClose = { scope.launch { drawer.close() } },
                    onNew = {
                        workspace.newDocument()
                        scope.launch { drawer.close() }
                    },
                    onOpen = {
                        scope.launch { drawer.close() }
                        openFile.launch(arrayOf("*/*"))
                    },
                    onSaveAs = {
                        scope.launch { drawer.close() }
                        save(current, copy = true)
                    },
                    onSelectTab = {
                        workspace.select(it)
                        scope.launch { drawer.close() }
                    },
                    onRecent = {
                        workspace.open(it.uri, it.name)
                        scope.launch { drawer.close() }
                    },
                    onGoToLine = {
                        previewRequested = false
                        showLineDialog = true
                        scope.launch { drawer.close() }
                    },
                    onValidateJson = {
                        jsonAction = false
                        scope.launch { drawer.close() }
                    },
                    onFormatJson = {
                        jsonAction = true
                        scope.launch { drawer.close() }
                    },
                    onSettings = {
                        showSettings = true
                        scope.launch { drawer.close() }
                    },
                )
            },
        ) {
            CompositionLocalProvider(LocalContentColor provides EditorInk) {
                Box(Modifier.fillMaxSize().background(EditorBackground).safeDrawingPadding()) {
                    Column(Modifier.fillMaxSize()) {
                        EditorToolbar(
                            document = current,
                            searchOpen = showSearch,
                            previewOpen = previewOpen,
                            fileActionsEnabled = fileActionsEnabled,
                            onMenu = {
                                focus.clearFocus()
                                scope.launch { drawer.open() }
                            },
                            onSave = { save(current) },
                            onSearch = {
                                showSearch = previewOpen || !showSearch
                                previewRequested = false
                                if (!showSearch) focus.clearFocus()
                            },
                            onPreview = {
                                focus.clearFocus()
                                previewRequested = !previewOpen
                            },
                        )
                        EditorTabs(
                            documents = workspace.documents,
                            activeId = current.id,
                            onSelect = {
                                focus.clearFocus()
                                workspace.select(it)
                            },
                            onClose = { document ->
                                if (document.modified) closingTab = document.id else workspace.close(document.id)
                            },
                        )
                        key(current.id) {
                            DocumentEditor(
                                document = current,
                                workspace = workspace,
                                previewOpen = previewOpen,
                                showSearch = showSearch,
                                showLineDialog = showLineDialog,
                                jsonAction = jsonAction,
                                onEditSource = { previewRequested = false },
                                onShowLineDialog = { showLineDialog = true },
                                onDismissLineDialog = { showLineDialog = false },
                                onJsonHandled = { jsonAction = null },
                                onMessage = ::showMessage,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter))
                }
            }
        }

        val toClose = workspace.documents.firstOrNull { it.id == closingTab }
        if (toClose != null) {
            AlertDialog(
                onDismissRequest = { closingTab = -1 },
                title = { Text("Save changes?") },
                text = { Text("Save your changes to ${toClose.name} before closing this tab.") },
                confirmButton = {
                    TextButton(
                        enabled = fileActionsEnabled,
                        onClick = {
                            closingTab = -1
                            save(toClose, closeAfter = true)
                        },
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { closingTab = -1 }) { Text("Cancel") }
                    TextButton(onClick = {
                        closingTab = -1
                        workspace.close(toClose.id)
                    }) { Text("Discard") }
                },
            )
        }
        workspace.documents.firstOrNull { it.id == namingTab }?.let { document ->
            SaveNameDialog(
                document.syntax.suggestedFileName(document.name),
                onClose = {
                    namingTab = -1
                    pendingCloseAfterSave = false
                },
                onChooseLocation = { name ->
                    namingTab = -1
                    pendingSaveTab = document.id
                    pendingSaveName = name
                    val extension = name.substringAfterLast('.', "").lowercase()
                    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                    createFile.launch(
                        Intent(Intent.ACTION_CREATE_DOCUMENT)
                            .addCategory(Intent.CATEGORY_OPENABLE)
                            .setType(mime ?: "application/octet-stream")
                            .putExtra(Intent.EXTRA_TITLE, name),
                    )
                },
            )
        }
        if (showSettings) {
            SettingsDialog(workspace.settings, workspace::updateSettings) { showSettings = false }
        }
        workspace.error?.let { message ->
            AlertDialog(
                onDismissRequest = workspace::dismissError,
                title = { Text("Editor error") },
                text = { Text(message) },
                confirmButton = { TextButton(onClick = workspace::dismissError) { Text("OK") } },
            )
        }
    }
}

@Composable
private fun DocumentEditor(
    document: EditorDocument,
    workspace: EditorWorkspace,
    previewOpen: Boolean,
    showSearch: Boolean,
    showLineDialog: Boolean,
    jsonAction: Boolean?,
    onEditSource: () -> Unit,
    onShowLineDialog: () -> Unit,
    onDismissLineDialog: () -> Unit,
    onJsonHandled: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val value = document.value
    val starts = remember(value.text) { lineStarts(value.text) }
    val editorFocus = remember { FocusRequester() }
    val editorState = rememberSaveableStateHolder()
    var formattingVisibility by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val formattingOpen = formattingVisibility ?: document.syntax.supportsFormatting
    var query by rememberSaveable { mutableStateOf("") }
    var replacement by rememberSaveable { mutableStateOf("") }
    var matchAnchor by rememberSaveable { mutableIntStateOf(-1) }
    var jump by remember { mutableStateOf<EditorJump?>(EditorJump(value.selection.end)) }
    var jsonError by remember { mutableStateOf<JsonSyntaxError?>(null) }
    val matches = remember(showSearch, value.text, query) {
        if (showSearch) findMatches(value.text, query) else emptyList()
    }
    val matchIndex = remember(matches, matchAnchor) {
        matches.indexOfFirst { it.start >= matchAnchor }.coerceAtLeast(0)
    }
    val transformation = remember(document.syntax, matches, matchIndex) {
        if (document.syntax == Syntax.Plain && matches.isEmpty()) {
            VisualTransformation.None
        } else {
            EditorHighlighting(document.syntax, matches, matchIndex)
        }
    }

    fun moveCursor(offset: Int) {
        workspace.edit(document, document.value.withCursor(offset))
        jump = EditorJump(offset)
    }

    fun navigateMatch(step: Int) {
        if (matches.isEmpty()) return
        val index = (matchIndex + step + matches.size) % matches.size
        matchAnchor = matches[index].start
        jump = EditorJump(matchAnchor)
    }

    fun undo() {
        workspace.undo(document)
        jump = EditorJump(document.value.selection.end)
    }

    fun redo() {
        workspace.redo(document)
        jump = EditorJump(document.value.selection.end)
    }

    fun applyFormatting(transform: (TextFieldValue) -> TextFieldValue) {
        val formatted = transform(document.value)
        workspace.edit(document, formatted, atomic = true)
        jump = EditorJump(formatted.selection.end)
        editorFocus.requestFocus()
    }

    LaunchedEffect(jsonAction) {
        if (jsonAction != null) {
            try {
                if (jsonAction) {
                    val formatted = formatJson(value.text)
                    val caret = value.selection.end.coerceAtMost(formatted.length)
                    workspace.edit(document, TextFieldValue(formatted, TextRange(caret)), atomic = true)
                    jump = EditorJump(caret)
                    onMessage("JSON formatted.")
                } else {
                    validateJson(value.text)
                    onMessage("JSON is valid.")
                }
            } catch (invalid: JsonSyntaxError) {
                jsonError = invalid
            } finally {
                onJsonHandled()
            }
        }
    }

    val formattingToolbar: @Composable () -> Unit = {
        FormattingToolbar(
            syntax = document.syntax,
            onUseMarkdown = { workspace.setSyntax(document, Syntax.Markdown) },
            onInlineFormat = { format -> applyFormatting { it.toggleInlineFormat(document.syntax, format) } },
            onFontSize = { size -> applyFormatting { it.setFontSize(document.syntax, size) } },
            onFontColor = { color -> applyFormatting { it.setFontColor(document.syntax, color) } },
            onIndent = { indent ->
                val width = if (document.syntax == Syntax.Markdown) 2 else 4
                applyFormatting { it.changeIndent(indent, width) }
            },
        )
    }
    val toolbarPosition = workspace.settings.formattingToolbarPosition
    if (previewOpen) {
        DocumentPreview(document.syntax, value.text, workspace.settings.fontSize, modifier)
    } else Column(modifier) {
        EditingControls(
            document = document,
            wordWrap = workspace.settings.wordWrap,
            formattingOpen = formattingOpen,
            onUndo = ::undo,
            onRedo = ::redo,
            onWrap = {
                workspace.updateSettings(workspace.settings.copy(wordWrap = !workspace.settings.wordWrap))
                jump = EditorJump(document.value.selection.end)
            },
            onFormatting = { formattingVisibility = !formattingOpen },
            onSyntax = { workspace.setSyntax(document, it) },
        )
        if (formattingOpen && toolbarPosition == FormattingToolbarPosition.Top) formattingToolbar()
        if (showSearch) {
            SearchPanel(
                query = query,
                replacement = replacement,
                matchIndex = matchIndex,
                matchCount = matches.size,
                onQuery = {
                    query = it
                    matchAnchor = value.selection.end
                    val next = findMatches(value.text, it)
                    val match = next.firstOrNull { candidate -> candidate.start >= matchAnchor } ?: next.firstOrNull()
                    if (match != null) jump = EditorJump(match.start)
                },
                onReplacement = { replacement = it },
                onPrevious = { navigateMatch(-1) },
                onNext = { navigateMatch(1) },
                onReplaceOne = {
                    val match = matches.getOrNull(matchIndex)
                    if (match != null) {
                        val text = value.text.replaceRange(match.start, match.end, replacement)
                        matchAnchor = match.start + replacement.length
                        val next = findMatches(text, query)
                        val nextMatch = next.firstOrNull { it.start >= matchAnchor } ?: next.firstOrNull()
                        workspace.edit(document, TextFieldValue(text, TextRange(matchAnchor)), atomic = true)
                        jump = EditorJump(nextMatch?.start ?: matchAnchor)
                    }
                },
                onReplaceAll = {
                    if (matches.isNotEmpty()) {
                        val text = value.text.replace(query, replacement)
                        val caret = value.selection.end.coerceAtMost(text.length)
                        workspace.edit(document, TextFieldValue(text, TextRange(caret)), atomic = true)
                        jump = EditorJump(caret)
                        onMessage(if (matches.size == 1) "Replaced 1 match." else "Replaced ${matches.size} matches.")
                    }
                },
            )
        }
        editorState.SaveableStateProvider("source") {
            EditorSurface(
                value = value,
                settings = workspace.settings,
                starts = starts,
                transformation = transformation,
                jump = jump,
                onJumpHandled = { if (jump === it) jump = null },
                onValueChange = { workspace.edit(document, it) },
                onUndo = ::undo,
                onRedo = ::redo,
                focusRequester = editorFocus,
                modifier = Modifier.weight(1f),
            )
        }
        if (formattingOpen && toolbarPosition == FormattingToolbarPosition.Bottom) formattingToolbar()
        EditorStatus(document, starts, onShowLineDialog)
    }

    if (showLineDialog) {
        GoToLineDialog(starts.size, onDismissLineDialog) { line ->
            moveCursor(starts[line - 1])
            onDismissLineDialog()
        }
    }
    jsonError?.let { invalid ->
        val offset = invalid.offset.coerceAtMost(value.text.length)
        val line = lineIndex(starts, offset)
        AlertDialog(
            onDismissRequest = { jsonError = null },
            title = { Text("Invalid JSON") },
            text = { Text("Line ${line + 1}, column ${offset - starts[line] + 1}. ${invalid.message}") },
            confirmButton = {
                TextButton(onClick = {
                    jsonError = null
                    onEditSource()
                    moveCursor(offset)
                }) { Text("Go to error") }
            },
            dismissButton = { TextButton(onClick = { jsonError = null }) { Text("Close") } },
        )
    }
}
