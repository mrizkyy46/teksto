package com.mrizkyy46.teksto

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

internal enum class InlineFormat(val label: String, val tag: String, val markdown: String? = null) {
    Bold("Bold", "strong", "**"),
    Italic("Italic", "em", "*"),
    Underline("Underline", "u"),
    Strike("Strikethrough", "del", "~~"),
}

internal enum class FormatColor(val label: String, val hex: String) {
    Slate("Slate", "#64748B"),
    Blue("Blue", "#3B82F6"),
    Lavender("Lavender", "#A78BFA"),
    Coral("Coral", "#F87171"),
    Amber("Amber", "#D98711"),
    Mint("Mint", "#21A67A"),
}

private val paragraphBreak = Regex("""\r?\n(?:[ \t]*\r?\n)+""")
private val sizeSpan = Regex("""<span style="font-size: \d+px">""")
private val colorSpan = Regex("""<span style="color: #[0-9a-fA-F]{6}">""")

internal fun TextFieldValue.toggleInlineFormat(syntax: Syntax, format: InlineFormat): TextFieldValue {
    if (!syntax.supportsFormatting) return this
    val marker = format.markdown?.takeIf { syntax == Syntax.Markdown }
    val opening = marker ?: "<${format.tag}>"
    val closing = marker ?: "</${format.tag}>"
    return styleSource(syntax, opening, closing, trimEdges = marker != null)
}

internal fun TextFieldValue.setFontSize(syntax: Syntax, pixels: Int): TextFieldValue =
    styleSource(
        syntax,
        "<span style=\"font-size: ${pixels}px\">",
        "</span>",
        existingOpening = sizeSpan,
    )

internal fun TextFieldValue.setFontColor(syntax: Syntax, color: FormatColor): TextFieldValue =
    styleSource(
        syntax,
        "<span style=\"color: ${color.hex}\">",
        "</span>",
        existingOpening = colorSpan,
    )

private fun TextFieldValue.styleSource(
    syntax: Syntax,
    opening: String,
    closing: String,
    trimEdges: Boolean = false,
    existingOpening: Regex? = null,
): TextFieldValue {
    val start = selection.min
    val end = selection.max
    val selected = text.substring(start, end)
    val breaks = if (syntax == Syntax.Markdown) paragraphBreak.findAll(selected).toList() else emptyList()
    if (breaks.isEmpty()) return styleSelection(opening, closing, trimEdges, existingOpening)

    fun styledParagraph(paragraph: String): String {
        if (paragraph.isBlank()) return paragraph
        return TextFieldValue(paragraph, TextRange(0, paragraph.length))
            .styleSelection(opening, closing, trimEdges, existingOpening).text
    }
    val replacement = buildString {
        var offset = 0
        for (separator in breaks) {
            append(styledParagraph(selected.substring(offset, separator.range.first)))
            append(separator.value)
            offset = separator.range.last + 1
        }
        append(styledParagraph(selected.substring(offset)))
    }
    val newEnd = start + replacement.length
    val range = if (selection.reversed) TextRange(newEnd, start) else TextRange(start, newEnd)
    return TextFieldValue(text.replaceRange(start, end, replacement), range)
}

private fun TextFieldValue.styleSelection(
    opening: String,
    closing: String,
    trimEdges: Boolean = false,
    existingOpening: Regex? = null,
): TextFieldValue {
    var start = selection.min
    var end = selection.max
    if (trimEdges && !selection.collapsed) {
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        if (start == end) return this
    }

    val prefix = existingOpening ?: Regex(Regex.escape(opening))
    val beforeStart = (start - 48).coerceAtLeast(0)
    val precedingText = text.substring(beforeStart, start)
    val precedingPrefixStart = if (existingOpening == null) {
        (start - opening.length).takeIf { text.startsWith(opening, it) }
    } else {
        prefix.findAll(precedingText).lastOrNull { it.range.last == precedingText.lastIndex }
            ?.let { beforeStart + it.range.first }
    }
    val selectedPrefix = prefix.matchAt(text, start)
    val isMarkdownItalic = opening == "*"
    val surrounded = precedingPrefixStart != null && text.startsWith(closing, end) &&
        (!isMarkdownItalic || (text.hasOddStars(start - 1, -1) && text.hasOddStars(end, 1)))
    val includesMarkup = selectedPrefix != null &&
        selectedPrefix.range.last + 1 <= end - closing.length &&
        text.startsWith(closing, end - closing.length) &&
        (!isMarkdownItalic || (text.hasOddStars(start, 1) && text.hasOddStars(end - 1, -1)))

    val contentStart: Int
    val contentEnd: Int
    val replaceStart: Int
    val replaceEnd: Int
    when {
        surrounded -> {
            contentStart = start
            contentEnd = end
            replaceStart = checkNotNull(precedingPrefixStart)
            replaceEnd = end + closing.length
        }
        includesMarkup -> {
            contentStart = checkNotNull(selectedPrefix).range.last + 1
            contentEnd = end - closing.length
            replaceStart = start
            replaceEnd = end
        }
        else -> {
            contentStart = start
            contentEnd = end
            replaceStart = start
            replaceEnd = end
        }
    }
    val remove = existingOpening == null && (surrounded || includesMarkup)
    val prefixText = if (remove) "" else opening
    val suffixText = if (remove) "" else closing
    val content = text.substring(contentStart, contentEnd)
    val result = text.replaceRange(replaceStart, replaceEnd, prefixText + content + suffixText)
    val selectedStart = replaceStart + prefixText.length
    val selectedEnd = selectedStart + content.length
    val range = if (selection.reversed) TextRange(selectedEnd, selectedStart) else TextRange(selectedStart, selectedEnd)
    return TextFieldValue(result, range)
}

private fun String.hasOddStars(start: Int, step: Int): Boolean {
    var index = start
    while (getOrNull(index) == '*') index += step
    return (index - start) % 2 != 0
}

private data class IndentChange(val offset: Int, val removed: Int, val inserted: String)

internal fun TextFieldValue.changeIndent(increase: Boolean, width: Int): TextFieldValue {
    val starts = lineStarts(text)
    val firstLine = lineIndex(starts, selection.min)
    val lastOffset = if (selection.collapsed) selection.max else selection.max - 1
    val lastLine = lineIndex(starts, lastOffset)
    val indent = " ".repeat(width)
    val changes = (firstLine..lastLine).mapNotNull { line ->
        val start = starts[line]
        if (increase) {
            IndentChange(start, 0, indent)
        } else {
            var removed = 0
            while (removed < width && text.getOrNull(start + removed) == ' ') removed++
            if (removed < width && text.getOrNull(start + removed) == '\t') removed++
            if (removed == 0) null else IndentChange(start, removed, "")
        }
    }
    if (changes.isEmpty()) return this

    val adjustedText = buildString {
        var cursor = 0
        for (change in changes) {
            append(text, cursor, change.offset)
            append(change.inserted)
            cursor = change.offset + change.removed
        }
        append(text, cursor, text.length)
    }

    fun adjustedOffset(offset: Int): Int {
        var shift = 0
        for (change in changes) {
            if (change.offset > offset) break
            val removedBeforeOffset = (offset - change.offset).coerceAtMost(change.removed)
            shift += change.inserted.length - removedBeforeOffset
        }
        return offset + shift
    }

    return TextFieldValue(adjustedText, TextRange(adjustedOffset(selection.start), adjustedOffset(selection.end)))
}
