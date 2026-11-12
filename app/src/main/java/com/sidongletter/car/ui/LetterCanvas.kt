package com.sidongletter.car.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as StrokeStyle
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import com.sidongletter.letter.Letter
import com.sidongletter.letter.Stroke
import com.sidongletter.letter.TextBox
import com.sidongletter.replay.visibleStrokes
import com.sidongletter.replay.visibleTexts

private const val ERASE = "#00000000"

/**
 * 편지 한 장을 그린다. elapsedMs가 null이면 다 그린 모습, 아니면 그 시점까지 다시 그린 모습.
 * 종이는 영역을 가득 채우고, 그림은 편지 비율(aspect)대로 가운데 맞춘다.
 * 지우개 획은 그 아래 획만 지운다(레이어 안에서 Clear, 종이는 레이어 바깥).
 */
@Composable
fun LetterCanvas(letter: Letter, elapsedMs: Long?, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val strokes = if (elapsedMs == null) letter.strokes else visibleStrokes(letter.strokes, elapsedMs, texts = letter.texts)
    val texts = if (elapsedMs == null) letter.texts else visibleTexts(letter.texts, letter.strokes, elapsedMs)
    Canvas(
        modifier
            .background(Ink.Paper)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    ) {
        val aspect = if (letter.aspect > 0f) letter.aspect else 16f / 9f
        val w = minOf(size.width, size.height * aspect)
        val h = w / aspect
        val origin = Offset((size.width - w) / 2, (size.height - h) / 2)
        strokes.forEach { drawLetterStroke(it, origin, w, h) }
        texts.forEach { drawLetterText(measurer, it, origin, w, h) }
    }
}

private fun DrawScope.drawLetterStroke(s: Stroke, o: Offset, w: Float, h: Float) {
    val erase = s.color == ERASE
    val color = if (erase) Color.Black else parseInk(s.color)
    val blend = if (erase) BlendMode.Clear else BlendMode.SrcOver
    val width = s.width * w
    val p = s.points
    if (p.isEmpty()) return
    fun at(i: Int) = Offset(o.x + p[i].x * w, o.y + p[i].y * h)
    if (p.size == 1) {
        drawCircle(color, radius = width / 2, center = at(0), blendMode = blend)
        return
    }
    // 웹과 같게: 점 사이를 중점 곡선으로 잇는다
    val path = Path().apply {
        moveTo(at(0).x, at(0).y)
        for (i in 1 until p.size - 1) {
            val a = at(i)
            val b = at(i + 1)
            quadraticBezierTo(a.x, a.y, (a.x + b.x) / 2, (a.y + b.y) / 2)
        }
        val last = at(p.size - 1)
        lineTo(last.x, last.y)
    }
    drawPath(path, color, style = StrokeStyle(width, cap = StrokeCap.Round, join = StrokeJoin.Round), blendMode = blend)
}

private fun DrawScope.drawLetterText(m: TextMeasurer, t: TextBox, o: Offset, w: Float, h: Float) {
    if (t.text.isBlank()) return
    val layout = m.measure(
        t.text,
        TextStyle(color = parseInk(t.color), fontSize = (t.size * w).toSp(), fontWeight = FontWeight.SemiBold, fontFamily = AppFont),
        softWrap = false,
        maxLines = 1,
    )
    drawText(layout, topLeft = Offset(o.x + t.x * w, o.y + t.y * h))
}

/** "#RRGGBB" 또는 "#RRGGBBAA" */
fun parseInk(hex: String): Color {
    val v = hex.removePrefix("#")
    return when (v.length) {
        6 -> Color(0xFF000000 or v.toLong(16))
        8 -> Color(((v.substring(6).toLong(16) shl 24) or v.substring(0, 6).toLong(16)).toInt())
        else -> Color.Black
    }
}
