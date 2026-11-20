package com.mrizkyy46.teksto

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.outlined.FormatIndentIncrease
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatColorText
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.FormatStrikethrough
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun FormattingToolbar(
    syntax: Syntax,
    onUseMarkdown: () -> Unit,
    onInlineFormat: (InlineFormat) -> Unit,
    onFontSize: (Int) -> Unit,
    onFontColor: (FormatColor) -> Unit,
    onIndent: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(EditorPanel)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (syntax.supportsFormatting) {
            for (format in InlineFormat.entries) {
                val icon = when (format) {
                    InlineFormat.Bold -> Icons.Outlined.FormatBold
                    InlineFormat.Italic -> Icons.Outlined.FormatItalic
                    InlineFormat.Underline -> Icons.Outlined.FormatUnderlined
                    InlineFormat.Strike -> Icons.Outlined.FormatStrikethrough
                }
                IconButton(onClick = { onInlineFormat(format) }) { Icon(icon, format.label) }
            }
            FontSizeMenu(onFontSize)
            FontColorMenu(onFontColor)
        } else if (syntax == Syntax.Plain) {
            TextButton(onClick = onUseMarkdown) { Text("Use Markdown") }
        }

        IconButton(onClick = { onIndent(true) }) {
            Icon(Icons.AutoMirrored.Outlined.FormatIndentIncrease, "Increase indent")
        }
        IconButton(onClick = { onIndent(false) }) {
            Icon(Icons.AutoMirrored.Outlined.FormatIndentDecrease, "Decrease indent")
        }
    }
}

@Composable
private fun FontSizeMenu(onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Outlined.FormatSize, "Font size") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (size in listOf(12, 14, 16, 18, 24, 32, 40)) {
                DropdownMenuItem(
                    text = { Text("$size px") },
                    onClick = {
                        expanded = false
                        onSelect(size)
                    },
                )
            }
        }
    }
}

@Composable
private fun FontColorMenu(onSelect: (FormatColor) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) { Icon(Icons.Outlined.FormatColorText, "Font color") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (color in FormatColor.entries) {
                DropdownMenuItem(
                    text = { Text(color.label) },
                    leadingIcon = {
                        Box(
                            Modifier.size(14.dp).background(
                                color = Color(android.graphics.Color.parseColor(color.hex)),
                                shape = CircleShape,
                            ),
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelect(color)
                    },
                )
            }
        }
    }
}
