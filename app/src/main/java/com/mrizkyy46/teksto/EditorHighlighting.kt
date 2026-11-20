package com.mrizkyy46.teksto

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

private val Keyword = Color(0xFFBCABFC)
private val Literal = Color(0xFFA6D8B2)
private val Number = Color(0xFFFFC694)
private val Comment = Color(0xFF93A0B5)
private val Heading = Color(0xFF9DB7FF)

private val jsonTokens = Regex(
    """"(?:[^"\\\x00-\x1F]++|\\.)*+"|(?<![\w.])-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?|\b(?:true|false|null)\b""",
)
private val markdownTokens = Regex(
    """^[ \t]{0,3}(?:\x60{3,}|~{3,})[^\n]*\n[\s\S]*?(?:^[ \t]{0,3}(?:\x60{3,}|~{3,})[^\n]*|$(?![\s\S]))|^[ \t]{0,3}#{1,6}[ \t].*|(\x60+)[^\n]+?\1|!?\[[^\]\n]+]\([^\n)]+\)|\*\*[^*\n]+\*\*|__[^_\n]+__""",
    RegexOption.MULTILINE,
)
private val xmlTokens = Regex(
    """<!--[\s\S]*?(?:-->|\z)|<!\[CDATA\[[\s\S]*?(?:]]>|\z)|<[/!?]?[A-Za-z_:](?:"[^"]*"|'[^']*'|[^'">]++)*+>|&(?:#x[\da-fA-F]+|#\d+|[\w:.-]+);""",
)
private val xmlAttributes = Regex("""([\w:.-]+)\s*=\s*("[^"]*"|'[^']*')""")

internal class EditorHighlighting(
    private val syntax: Syntax,
    private val matches: List<TextMatch>,
    private val activeMatch: Int,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = AnnotatedString.Builder(text)
        when (syntax) {
            Syntax.Plain -> Unit
            Syntax.Json -> for (token in jsonTokens.findAll(text.text)) {
                val color = when (token.value.first()) {
                    '"' -> {
                        val following = text.text.indexOfFirstFrom(token.range.last + 1) { !it.isWhitespace() }
                        if (following >= 0 && text[following] == ':') Keyword else Literal
                    }
                    '-', in '0'..'9' -> Number
                    else -> Heading
                }
                highlighted.addStyle(SpanStyle(color = color), token.range.first, token.range.last + 1)
            }
            Syntax.Markdown -> for (token in markdownTokens.findAll(text.text)) {
                val color = when (token.value.trimStart().first()) {
                    '#' -> Heading
                    '[', '!' -> Keyword
                    else -> Literal
                }
                highlighted.addStyle(SpanStyle(color = color), token.range.first, token.range.last + 1)
            }
            Syntax.Html, Syntax.Xml -> for (token in xmlTokens.findAll(text.text)) {
                val content = token.value
                val color = when {
                    content.startsWith("<!--") -> Comment
                    content.startsWith("<![CDATA[") || content.startsWith('&') -> Literal
                    else -> Heading
                }
                highlighted.addStyle(SpanStyle(color = color), token.range.first, token.range.last + 1)
                if (color == Heading) {
                    for (attribute in xmlAttributes.findAll(content)) {
                        val name = checkNotNull(attribute.groups[1]).range
                        val value = checkNotNull(attribute.groups[2]).range
                        val start = token.range.first
                        highlighted.addStyle(SpanStyle(color = Keyword), start + name.first, start + name.last + 1)
                        highlighted.addStyle(SpanStyle(color = Literal), start + value.first, start + value.last + 1)
                    }
                }
            }
        }
        for ((index, match) in matches.withIndex()) {
            if (match.end > text.length) continue
            val style = if (index == activeMatch) {
                SpanStyle(color = Color(0xFF161210), background = Color(0xFFF8CB79))
            } else {
                SpanStyle(background = Color(0xFF584627))
            }
            highlighted.addStyle(style, match.start, match.end)
        }
        return TransformedText(highlighted.toAnnotatedString(), OffsetMapping.Identity)
    }
}

private inline fun String.indexOfFirstFrom(start: Int, predicate: (Char) -> Boolean): Int {
    for (index in start until length) {
        if (predicate(this[index])) return index
    }
    return -1
}
