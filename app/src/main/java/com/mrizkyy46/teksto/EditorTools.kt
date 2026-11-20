package com.mrizkyy46.teksto

internal data class TextMatch(val start: Int, val end: Int)

internal fun findMatches(text: String, query: String): List<TextMatch> {
    if (query.isEmpty()) return emptyList()
    return buildList {
        var start = text.indexOf(query)
        while (start >= 0) {
            val end = start + query.length
            add(TextMatch(start, end))
            start = text.indexOf(query, end)
        }
    }
}

internal fun lineStarts(text: String): List<Int> = buildList {
    add(0)
    for (index in text.indices) {
        if (text[index] == '\n') add(index + 1)
    }
}

internal fun lineIndex(starts: List<Int>, offset: Int): Int {
    val index = starts.binarySearch(offset)
    return if (index >= 0) index else -index - 2
}

internal enum class Syntax(val label: String) {
    Plain("Plain text"), Json("JSON"), Markdown("Markdown"), Html("HTML"), Xml("XML");

    val supportsFormatting get() = this == Markdown || this == Html
    val supportsPreview get() = supportsFormatting || this == Json

    fun suggestedFileName(name: String): String {
        if (!supportsFormatting || fromName(name) == this) return name
        val base = name.substringBeforeLast('.').ifEmpty { name }
        val extension = if (this == Markdown) "md" else "html"
        return "$base.$extension"
    }

    companion object {
        fun fromName(name: String): Syntax = when (name.substringAfterLast('.').lowercase()) {
            "json" -> Json
            "md", "markdown" -> Markdown
            "html", "htm" -> Html
            "xml" -> Xml
            else -> Plain
        }
    }
}

internal class JsonSyntaxError(val offset: Int, message: String) : IllegalArgumentException(message)

private val jsonString = Regex(""""(?:[^"\\\x00-\x1F]++|\\(?:["\\/bfnrt]|u[0-9a-fA-F]{4}))*+"""")
private val jsonNumber = Regex("""-?(?:0|[1-9][0-9]*)(?:\.[0-9]+)?(?:[Ee][+-]?[0-9]+)?""")

internal fun validateJson(source: String) {
    JsonReader(source, null).read()
}

internal fun formatJson(source: String): String =
    buildString { JsonReader(source, this).read() }

private class JsonReader(private val source: String, private val output: StringBuilder?) {
    private var offset = 0

    fun read() {
        value(0)
        skipWhitespace()
        if (offset < source.length) fail("Unexpected text after the JSON value.")
    }

    private fun value(depth: Int) {
        if (depth > 128) fail("JSON is nested more than 128 levels.")
        skipWhitespace()
        when (source.getOrNull(offset)) {
            '{' -> container('{', '}', depth, objectEntry = true)
            '[' -> container('[', ']', depth, objectEntry = false)
            '"' -> token(jsonString, "Invalid JSON string.")
            't' -> literal("true")
            'f' -> literal("false")
            'n' -> literal("null")
            '-', in '0'..'9' -> token(jsonNumber, "Invalid JSON number.")
            else -> fail("Expected a JSON value.")
        }
    }

    private fun container(open: Char, close: Char, depth: Int, objectEntry: Boolean) {
        consume(open)
        if (consume(close)) return
        do {
            indent(depth + 1)
            if (objectEntry) {
                skipWhitespace()
                token(jsonString, "Expected a quoted property name.")
                if (!consume(':')) fail("Expected ':' after the property name.")
                output?.append(' ')
            }
            value(depth + 1)
        } while (consume(','))
        indent(depth)
        if (!consume(close)) fail("Expected ',' or '$close'.")
    }

    private fun token(pattern: Regex, message: String) {
        val match = pattern.matchAt(source, offset) ?: fail(message)
        output?.append(match.value)
        offset = match.range.last + 1
    }

    private fun literal(value: String) {
        if (!source.startsWith(value, offset)) fail("Expected '$value'.")
        output?.append(value)
        offset += value.length
    }

    private fun consume(character: Char): Boolean {
        skipWhitespace()
        if (source.getOrNull(offset) != character) return false
        output?.append(character)
        offset++
        return true
    }

    private fun skipWhitespace() {
        while (offset < source.length && source[offset] in " \t\r\n") offset++
    }

    private fun indent(depth: Int) {
        output?.run {
            append('\n')
            repeat(depth * 2) { append(' ') }
        }
    }

    private fun fail(message: String): Nothing = throw JsonSyntaxError(offset, message)
}
