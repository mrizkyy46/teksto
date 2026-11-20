package com.mrizkyy46.teksto

import android.graphics.Color
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

private val markdownExtensions = listOf(StrikethroughExtension.create(), TablesExtension.create())
private val markdownParser = Parser.builder().extensions(markdownExtensions).build()
private val markdownRenderer = HtmlRenderer.builder().extensions(markdownExtensions).sanitizeUrls(true).build()

@Composable
internal fun DocumentPreview(
    syntax: Syntax,
    source: String,
    fontSize: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(EditorBackground), contentAlignment = Alignment.Center) {
        when {
            source.isBlank() -> Text("Add some text to preview it.", color = EditorMuted)
            syntax == Syntax.Json -> JsonPreview(source, fontSize)
            syntax.supportsFormatting -> WebPreview(syntax, source)
        }
    }
}

@Composable
private fun WebPreview(syntax: Syntax, source: String) {
    var result by remember(syntax, source) { mutableStateOf<Result<String>?>(null) }
    LaunchedEffect(syntax, source) {
        result = withContext(Dispatchers.Default) { runCatching { buildPreviewHtml(syntax, source) } }
    }
    var rendererStopped by remember(syntax, source) { mutableStateOf(false) }
    val page = result?.getOrNull()
    when {
        rendererStopped || result?.isFailure == true -> {
            Text("This document couldn’t be previewed.", color = EditorMuted, modifier = Modifier.padding(16.dp))
        }
        page == null -> CircularProgressIndicator()
        else -> AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    setBackgroundColor(if (syntax == Syntax.Markdown) EditorBackground.toArgb() else Color.WHITE)
                    settings.apply {
                        javaScriptEnabled = false
                        allowFileAccess = false
                        allowContentAccess = false
                        blockNetworkLoads = true
                        domStorageEnabled = false
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            !request.url.toString().startsWith("about:blank#")

                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            rendererStopped = true
                            return true
                        }
                    }
                }
            },
            update = { webView ->
                if (webView.tag != page) {
                    webView.tag = page
                    webView.loadDataWithBaseURL(null, page, "text/html", "UTF-8", null)
                }
            },
            onRelease = { it.destroy() },
        )
    }
}

internal fun buildPreviewHtml(syntax: Syntax, source: String): String = buildString {
    append(
        """
        <!doctype html>
        <meta charset="utf-8">
        <meta http-equiv="Content-Security-Policy"
          content="default-src 'none'; img-src data:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        """.trimIndent(),
    )
    append('\n')
    if (syntax == Syntax.Markdown) {
        append(
            """
            <style>
              :root { color-scheme: dark; background: #101319; color: #E7EBF3; }
              body { max-width: 68ch; margin: auto; padding: 24px 20px 40px;
                     font: 16px/1.65 system-ui, sans-serif; overflow-wrap: anywhere; }
              h1, h2, h3, h4 { line-height: 1.25; margin: 1.25em 0 0.5em; }
              body > :first-child { margin-top: 0; }
              a { color: #9DB7FF; }
              img { max-width: 100%; height: auto; }
              code, pre { background: #191E27; border-radius: 5px; }
              code { padding: 0.16em 0.3em; }
              pre { padding: 12px; overflow: auto; }
              pre code { padding: 0; }
              blockquote { margin-inline: 0; padding-inline-start: 14px; border-inline-start: 3px solid #596C96; }
              hr, th, td { border: 1px solid #394557; }
              hr { border-width: 1px 0 0; }
              table { display: block; overflow: auto; border-collapse: collapse; }
              th, td { padding: 6px 12px; }
            </style>
            """.trimIndent(),
        )
        append(markdownRenderer.render(markdownParser.parse(source)))
    } else {
        append(source)
    }
}

private data class JsonPreviewContent(val text: AnnotatedString, val valid: Boolean)

@Composable
private fun JsonPreview(source: String, fontSize: Int) {
    var preview by remember(source) { mutableStateOf<JsonPreviewContent?>(null) }
    LaunchedEffect(source) {
        preview = withContext(Dispatchers.Default) {
            val formatted = try {
                formatJson(source)
            } catch (_: JsonSyntaxError) {
                null
            }
            val highlighting = EditorHighlighting(Syntax.Json, emptyList(), 0)
            JsonPreviewContent(highlighting.filter(AnnotatedString(formatted ?: source)).text, formatted != null)
        }
    }
    val content = preview
    if (content == null) {
        CircularProgressIndicator()
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!content.valid) Text("Invalid JSON. Showing source.", color = EditorMuted)
        SelectionContainer {
            Text(
                content.text,
                color = EditorInk,
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.55f).sp,
            )
        }
    }
}
