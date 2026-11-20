package com.mrizkyy46.teksto

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.ShortText
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FormatPaint
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
internal fun EditorToolbar(
    document: EditorDocument,
    searchOpen: Boolean,
    previewOpen: Boolean,
    fileActionsEnabled: Boolean,
    onMenu: () -> Unit,
    onSave: () -> Unit,
    onSearch: () -> Unit,
    onPreview: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) { Icon(Icons.Outlined.Menu, "Open sidebar") }
        Column(Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
            Text("Teksto", style = MaterialTheme.typography.titleMedium)
            Text(
                document.name,
                style = MaterialTheme.typography.bodySmall,
                color = EditorMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onSave, enabled = fileActionsEnabled) { Icon(Icons.Outlined.Save, "Save file") }
        val searchActive = searchOpen && !previewOpen
        IconToggleButton(checked = searchActive, onCheckedChange = { onSearch() }) {
            Icon(Icons.Outlined.Search, "Find", tint = if (searchActive) EditorAccent else EditorInk)
        }
        if (document.syntax.supportsPreview) {
            IconToggleButton(checked = previewOpen, onCheckedChange = { onPreview() }) {
                Icon(
                    if (previewOpen) Icons.Outlined.Edit else Icons.Outlined.Visibility,
                    if (previewOpen) "Edit source" else "Show preview",
                    tint = if (previewOpen) EditorAccent else EditorInk,
                )
            }
        }
    }
}

@Composable
internal fun EditorTabs(
    documents: List<EditorDocument>,
    activeId: Int,
    onSelect: (Int) -> Unit,
    onClose: (EditorDocument) -> Unit,
) {
    val state = rememberLazyListState()
    LaunchedEffect(activeId) {
        val index = documents.indexOfFirst { it.id == activeId }
        if (index >= 0 && state.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            state.scrollToItem(index)
        }
    }
    LazyRow(state = state, modifier = Modifier.fillMaxWidth().background(EditorPanel)) {
        items(documents, key = { it.id }) { document ->
            val selected = document.id == activeId
            Row(
                Modifier.background(if (selected) EditorBackground else EditorPanel).drawBehind {
                    if (selected) {
                        val thickness = 1.dp.toPx()
                        drawRect(EditorAccent, Offset(0f, size.height - thickness), Size(size.width, thickness))
                    }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    document.name + if (document.modified) " •" else "",
                    modifier = Modifier.widthIn(max = 176.dp).clickable { onSelect(document.id) }
                        .padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
                    color = if (selected) EditorAccent else EditorMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                )
                IconButton(onClick = { onClose(document) }, modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Outlined.Close, "Close tab ${document.name}", Modifier.size(16.dp), tint = EditorMuted)
                }
            }
        }
    }
}

@Composable
internal fun EditingControls(
    document: EditorDocument,
    wordWrap: Boolean,
    formattingOpen: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onWrap: () -> Unit,
    onFormatting: () -> Unit,
    onSyntax: (Syntax?) -> Unit,
) {
    var syntaxMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(enabled = document.canUndo, onClick = onUndo) {
            Icon(Icons.AutoMirrored.Outlined.Undo, "Undo")
        }
        IconButton(enabled = document.canRedo, onClick = onRedo) {
            Icon(Icons.AutoMirrored.Outlined.Redo, "Redo")
        }
        IconToggleButton(checked = wordWrap, onCheckedChange = { onWrap() }) {
            Icon(Icons.AutoMirrored.Outlined.WrapText, "Word wrap", tint = if (wordWrap) EditorAccent else EditorMuted)
        }
        IconToggleButton(checked = formattingOpen, onCheckedChange = { onFormatting() }) {
            Icon(Icons.Outlined.FormatPaint, "Formatting toolbar", tint = if (formattingOpen) EditorAccent else EditorMuted)
        }
        Spacer(Modifier.weight(1f))
        Box {
            TextButton(onClick = { syntaxMenu = true }) {
                Text(document.syntax.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.KeyboardArrowDown, "Syntax mode", Modifier.size(20.dp))
            }
            DropdownMenu(expanded = syntaxMenu, onDismissRequest = { syntaxMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Detect from file name") },
                    onClick = {
                        onSyntax(null)
                        syntaxMenu = false
                    },
                    trailingIcon = if (document.syntaxOverride == null) {
                        { Icon(Icons.Outlined.Check, "Selected") }
                    } else null,
                )
                for (syntax in Syntax.entries) {
                    DropdownMenuItem(
                        text = { Text(syntax.label) },
                        onClick = {
                            onSyntax(syntax)
                            syntaxMenu = false
                        },
                        trailingIcon = if (document.syntaxOverride == syntax) {
                            { Icon(Icons.Outlined.Check, "Selected") }
                        } else null,
                    )
                }
            }
        }
    }
    HorizontalDivider(color = EditorPanel)
}

@Composable
internal fun SearchPanel(
    query: String,
    replacement: String,
    matchIndex: Int,
    matchCount: Int,
    onQuery: (String) -> Unit,
    onReplacement: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onReplaceOne: () -> Unit,
    onReplaceAll: () -> Unit,
) {
    var showReplace by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val hasMatches = matchCount > 0
    Column(Modifier.fillMaxWidth().background(EditorPanel).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                label = { Text("Find") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focus.clearFocus()
                    onNext()
                }),
            )
            IconButton(onClick = { focus.clearFocus(); onPrevious() }, enabled = hasMatches) {
                Icon(Icons.Outlined.ArrowUpward, "Previous match")
            }
            IconButton(onClick = { focus.clearFocus(); onNext() }, enabled = hasMatches) {
                Icon(Icons.Outlined.ArrowDownward, "Next match")
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    query.isEmpty() -> ""
                    !hasMatches -> "No matches"
                    else -> "${matchIndex + 1} of $matchCount"
                },
                color = EditorMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showReplace = !showReplace }) {
                Text(if (showReplace) "Hide replace" else "Replace")
            }
        }
        if (showReplace) {
            OutlinedTextField(
                value = replacement,
                onValueChange = onReplacement,
                label = { Text("Replace with") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(enabled = hasMatches, onClick = { focus.clearFocus(); onReplaceOne() }) {
                    Text("Replace one")
                }
                TextButton(enabled = hasMatches, onClick = { focus.clearFocus(); onReplaceAll() }) {
                    Text("Replace all")
                }
            }
        }
    }
}

@Composable
internal fun EditorStatus(document: EditorDocument, starts: List<Int>, onGoToLine: () -> Unit) {
    val cursor = document.value.selection.end
    val line = lineIndex(starts, cursor)
    Row(
        Modifier.fillMaxWidth().background(EditorPanel).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onGoToLine, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(
                "Ln ${line + 1}, Col ${cursor - starts[line] + 1}",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (starts.size == 1) "1 line" else "${starts.size} lines",
            color = EditorMuted,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Text(
            if (document.modified) "Unsaved" else "UTF-8",
            color = if (document.modified) EditorAccent else EditorMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
internal fun EditorDrawer(
    documents: List<EditorDocument>,
    activeId: Int,
    recent: List<RecentFile>,
    fileActionsEnabled: Boolean,
    onClose: () -> Unit,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSaveAs: () -> Unit,
    onSelectTab: (Int) -> Unit,
    onRecent: (RecentFile) -> Unit,
    onGoToLine: () -> Unit,
    onValidateJson: () -> Unit,
    onFormatJson: () -> Unit,
    onSettings: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.widthIn(max = 320.dp),
        drawerContainerColor = EditorPanel,
    ) {
        LazyColumn(Modifier.fillMaxHeight(), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Teksto", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close sidebar") }
                }
            }
            item { DrawerAction("New file", Icons.Outlined.Add, onNew) }
            item { DrawerAction("Open file", Icons.Outlined.FolderOpen, onOpen, fileActionsEnabled) }
            item { DrawerAction("Save as", Icons.Outlined.Save, onSaveAs, fileActionsEnabled) }

            item { DrawerHeading("Open tabs") }
            items(documents, key = { "tab-${it.id}" }) { document ->
                DrawerAction(
                    document.name + if (document.modified) " •" else "",
                    if (document.id == activeId) Icons.Outlined.Check else Icons.Outlined.Description,
                    { onSelectTab(document.id) },
                    selected = document.id == activeId,
                )
            }

            if (recent.isNotEmpty()) {
                item { DrawerHeading("Recent files") }
                items(recent, key = { it.uri.toString() }) { file ->
                    DrawerAction(file.name, Icons.Outlined.History, { onRecent(file) }, fileActionsEnabled)
                }
            }

            item { DrawerHeading("Tools") }
            item { DrawerAction("Go to line", Icons.AutoMirrored.Outlined.ShortText, onGoToLine) }
            item { DrawerAction("Validate JSON", Icons.Outlined.Check, onValidateJson) }
            item { DrawerAction("Format JSON", Icons.Outlined.Code, onFormatJson) }
            item { DrawerAction("Editor settings", Icons.Outlined.Settings, onSettings) }
        }
    }
}

@Composable
private fun DrawerHeading(text: String) {
    HorizontalDivider(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = EditorMuted.copy(alpha = 0.25f))
    Text(
        text, Modifier.padding(start = 20.dp, top = 4.dp, bottom = 4.dp), color = EditorMuted,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun DrawerAction(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val color = when {
        !enabled -> EditorMuted
        selected -> EditorAccent
        else -> EditorInk
    }
    Row(
        Modifier.fillMaxWidth().height(44.dp)
            .then(if (selected) Modifier.background(EditorAccent.copy(alpha = 0.10f)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, contentDescription = null, Modifier.size(20.dp), tint = color)
        Text(text, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun SettingsDialog(settings: EditorSettings, onChange: (EditorSettings) -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Editor settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Font size: ${settings.fontSize} sp")
                Slider(
                    value = settings.fontSize.toFloat(),
                    onValueChange = { onChange(settings.copy(fontSize = it.roundToInt())) },
                    valueRange = 11f..22f,
                    steps = 10,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Word wrap", Modifier.weight(1f))
                    Switch(checked = settings.wordWrap, onCheckedChange = { onChange(settings.copy(wordWrap = it)) })
                }
                Spacer(Modifier.height(8.dp))
                Text("Formatting toolbar position")
                Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (position in FormattingToolbarPosition.entries) {
                        FilterChip(
                            selected = settings.formattingToolbarPosition == position,
                            onClick = { onChange(settings.copy(formattingToolbarPosition = position)) },
                            label = { Text(position.name) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}

@Composable
internal fun SaveNameDialog(initialName: String, onClose: () -> Unit, onChooseLocation: (String) -> Unit) {
    var input by rememberSaveable(initialName) { mutableStateOf(initialName) }
    val name = input.trim()
    val valid = name.isNotEmpty() && name != "." && name != ".." && '/' !in name
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Save file") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("File name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { if (valid) onChooseLocation(name) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onChooseLocation(name) }, enabled = valid) { Text("Choose location") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}

@Composable
internal fun GoToLineDialog(lineCount: Int, onClose: () -> Unit, onGo: (Int) -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    val line = input.toIntOrNull()?.takeIf { it in 1..lineCount }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Go to line") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it.filter { character -> character in '0'..'9' } },
                label = { Text("Line number") },
                supportingText = { Text(if (lineCount == 1) "1 line" else "Lines 1–$lineCount") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { line?.let(onGo) }),
            )
        },
        confirmButton = { TextButton(onClick = { line?.let(onGo) }, enabled = line != null) { Text("Go") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
}
