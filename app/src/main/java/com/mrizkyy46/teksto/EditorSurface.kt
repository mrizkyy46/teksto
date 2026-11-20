package com.mrizkyy46.teksto

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

internal class EditorJump(val offset: Int)

@Composable
internal fun EditorSurface(
    value: TextFieldValue,
    settings: EditorSettings,
    starts: List<Int>,
    transformation: VisualTransformation,
    jump: EditorJump?,
    onJumpHandled: (EditorJump) -> Unit,
    onValueChange: (TextFieldValue) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val vertical = rememberScrollState()
    val horizontal = rememberScrollState()
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewportWidth by remember { mutableIntStateOf(0) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val margin = with(density) { 8.dp.toPx() }
    val textSizePx = with(density) { settings.fontSize.sp.toPx() }
    val style = remember(settings.fontSize) {
        TextStyle(
            color = EditorInk,
            fontFamily = FontFamily.Monospace,
            fontSize = settings.fontSize.sp,
            lineHeight = (settings.fontSize * 1.55f).sp,
        )
    }
    val digits = starts.size.toString().length
    val gutterWidth = with(density) { (digits * textSizePx * 0.65f + 22.dp.toPx()).toDp() }
    val paint = remember(textSizePx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = EditorMuted.toArgb()
            typeface = Typeface.MONOSPACE
            textSize = textSizePx
            textAlign = Paint.Align.RIGHT
        }
    }

    LaunchedEffect(jump, textLayout, viewportWidth, viewportHeight, settings.wordWrap) {
        val request = jump ?: return@LaunchedEffect
        val layout = textLayout ?: return@LaunchedEffect
        if (viewportHeight == 0 || viewportWidth == 0 || layout.layoutInput.text.text != value.text) {
            return@LaunchedEffect
        }
        val rect = layout.getCursorRect(request.offset.coerceIn(0, value.text.length))
        val top = rect.top + margin
        val bottom = rect.bottom + margin
        val y = when {
            top < vertical.value -> top - margin
            bottom > vertical.value + viewportHeight -> bottom - viewportHeight + margin
            else -> vertical.value.toFloat()
        }
        vertical.scrollTo(y.roundToInt().coerceIn(0, vertical.maxValue))
        if (!settings.wordWrap) {
            val x = when {
                rect.left < horizontal.value -> rect.left - margin
                rect.right > horizontal.value + viewportWidth -> rect.right - viewportWidth + margin
                else -> horizontal.value.toFloat()
            }
            horizontal.scrollTo(x.roundToInt().coerceIn(0, horizontal.maxValue))
        }
        onJumpHandled(request)
    }

    Box(
        modifier.fillMaxWidth()
            .onSizeChanged { viewportHeight = it.height }
            .verticalScroll(vertical)
            .padding(vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(gutterWidth))
            BoxWithConstraints(
                Modifier.weight(1f).padding(end = 12.dp).onSizeChanged { viewportWidth = it.width },
            ) {
                val editorWidth = maxWidth
                Box(if (settings.wordWrap) Modifier else Modifier.horizontalScroll(horizontal)) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier.widthIn(min = editorWidth)
                            .focusRequester(focusRequester)
                            .semantics { contentDescription = "Document editor" }
                            .onPreviewKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) {
                                    return@onPreviewKeyEvent false
                                }
                                when {
                                    event.key == Key.Y || (event.key == Key.Z && event.isShiftPressed) -> {
                                        onRedo()
                                        true
                                    }
                                    event.key == Key.Z -> {
                                        onUndo()
                                        true
                                    }
                                    else -> false
                                }
                            },
                        textStyle = style,
                        cursorBrush = SolidColor(EditorAccent),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                        visualTransformation = transformation,
                        onTextLayout = { textLayout = it },
                        decorationBox = { inner ->
                            Box {
                                if (value.text.isEmpty()) {
                                    Text("Start typing or open a file…", style = style, color = EditorMuted)
                                }
                                inner()
                            }
                        },
                    )
                }
            }
        }
        Canvas(Modifier.matchParentSize()) {
            val layout = textLayout ?: return@Canvas
            if (layout.layoutInput.text.text != value.text) return@Canvas
            val visibleStart = layout.getLineForVerticalPosition(vertical.value - margin)
            val visibleEnd = layout.getLineForVerticalPosition(vertical.value + viewportHeight.toFloat())
            val first = lineIndex(starts, layout.getLineStart(visibleStart))
            val last = lineIndex(starts, layout.getLineStart(visibleEnd))
            val right = gutterWidth.toPx() - 10.dp.toPx()
            drawIntoCanvas { canvas ->
                for (index in first..last) {
                    val line = layout.getLineForOffset(starts[index])
                    canvas.nativeCanvas.drawText((index + 1).toString(), right, layout.getLineBaseline(line), paint)
                }
            }
        }
    }
}
