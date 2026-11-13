package com.pleospostbox.replay

import com.pleospostbox.letter.Stroke
import com.pleospostbox.letter.TextBox

/** 원래 그린 시간. 마지막 점이나 마지막 글상자 중 늦은 쪽. */
private fun originalDurationMs(strokes: List<Stroke>, texts: List<TextBox>): Long = maxOf(
    strokes.maxOfOrNull { s -> s.points.maxOfOrNull { it.t } ?: 0L } ?: 0L,
    texts.maxOfOrNull { it.t } ?: 0L,
)

/** 원래 그린 시간 그대로 재생하되, maxMs보다 길면 maxMs로 압축한다. */
fun replayDurationMs(strokes: List<Stroke>, texts: List<TextBox> = emptyList(), maxMs: Long = 8_000): Long =
    minOf(originalDurationMs(strokes, texts), maxMs)

/** 재생 시작 후 elapsedMs 시점이 원래 그림에서 몇 ms에 해당하는지. 압축했으면 그만큼 늘려 계산한다. */
fun replayCutoffMs(strokes: List<Stroke>, texts: List<TextBox>, elapsedMs: Long, maxMs: Long = 8_000): Long {
    val original = originalDurationMs(strokes, texts)
    return if (original > maxMs) elapsedMs * original / maxMs else elapsedMs
}

/** 재생 시작 후 elapsedMs 시점에 보이는 점만 담은 획 목록. 점이 없는 획은 뺀다. */
fun visibleStrokes(
    strokes: List<Stroke>,
    elapsedMs: Long,
    maxMs: Long = 8_000,
    texts: List<TextBox> = emptyList(),
): List<Stroke> {
    val cutoff = replayCutoffMs(strokes, texts, elapsedMs, maxMs)
    return strokes.mapNotNull { s ->
        val pts = s.points.filter { it.t <= cutoff }
        if (pts.isEmpty()) null else s.copy(points = pts)
    }
}

/** elapsedMs 시점에 이미 놓인 글상자. 획과 같은 시간 축을 쓴다. */
fun visibleTexts(texts: List<TextBox>, strokes: List<Stroke>, elapsedMs: Long, maxMs: Long = 8_000): List<TextBox> {
    val cutoff = replayCutoffMs(strokes, texts, elapsedMs, maxMs)
    return texts.filter { it.t <= cutoff }
}
