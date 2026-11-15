package com.pleospostbox.replay

import com.pleospostbox.letter.Stroke
import com.pleospostbox.letter.TextBox

/** 재생할 때 이보다 긴 공백(펜을 뗀 채 머문 시간)은 이만큼으로 줄인다. */
const val MAX_GAP_MS = 1_000L

/**
 * 획과 획(또는 글상자) 사이 공백이 maxGapMs보다 길면 maxGapMs로 줄인 시간 축.
 * 그리다 오래 멈춘 편지도 재생할 때는 다음 획이 곧바로 이어진다.
 */
fun compactGaps(
    strokes: List<Stroke>,
    texts: List<TextBox>,
    maxGapMs: Long = MAX_GAP_MS,
): Pair<List<Stroke>, List<TextBox>> {
    // 펜이 종이에 닿아 있던 구간(획 하나, 글상자는 한 순간). 획 안의 시간은 건드리지 않는다.
    val spans = (strokes.filter { it.points.isNotEmpty() }.map { st -> st.points.minOf { it.t } to st.points.maxOf { it.t } } +
        texts.map { it.t to it.t }).sortedBy { it.first }
    // 공백이 끝나는 시각과, 그 시각부터 당겨야 할 누적 시간
    val ends = ArrayList<Long>()
    val shifts = ArrayList<Long>()
    var shift = 0L
    var reach = spans.firstOrNull()?.second ?: 0L
    for ((start, end) in spans.drop(1)) {
        val gap = start - reach
        if (gap > maxGapMs) {
            shift += gap - maxGapMs
            ends += start
            shifts += shift
        }
        reach = maxOf(reach, end)
    }
    if (ends.isEmpty()) return strokes to texts
    fun map(t: Long): Long {
        val i = ends.binarySearch(t).let { if (it >= 0) it else -it - 2 }
        return if (i < 0) t else t - shifts[i]
    }
    return strokes.map { st -> st.copy(points = st.points.map { it.copy(t = map(it.t)) }) } to
        texts.map { it.copy(t = map(it.t)) }
}

/** 원래 그린 시간. 마지막 점이나 마지막 글상자 중 늦은 쪽. */
private fun originalDurationMs(strokes: List<Stroke>, texts: List<TextBox>): Long = maxOf(
    strokes.maxOfOrNull { s -> s.points.maxOfOrNull { it.t } ?: 0L } ?: 0L,
    texts.maxOfOrNull { it.t } ?: 0L,
)

/** 원래 그린 시간 그대로 재생하되, maxMs보다 길면 maxMs로 압축한다. */
fun replayDurationMs(strokes: List<Stroke>, texts: List<TextBox> = emptyList(), maxMs: Long = 8_000): Long {
    val (s, t) = compactGaps(strokes, texts)
    return minOf(originalDurationMs(s, t), maxMs)
}

/** 재생 시작 후 elapsedMs 시점이 (공백을 줄인) 그림에서 몇 ms에 해당하는지. 압축했으면 그만큼 늘려 계산한다. */
private fun replayCutoffMs(strokes: List<Stroke>, texts: List<TextBox>, elapsedMs: Long, maxMs: Long = 8_000): Long {
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
    val (cs, ct) = compactGaps(strokes, texts)
    val cutoff = replayCutoffMs(cs, ct, elapsedMs, maxMs)
    return cs.mapNotNull { s ->
        val pts = s.points.filter { it.t <= cutoff }
        if (pts.isEmpty()) null else s.copy(points = pts)
    }
}

/** elapsedMs 시점에 이미 놓인 글상자. 획과 같은 시간 축을 쓴다. */
fun visibleTexts(texts: List<TextBox>, strokes: List<Stroke>, elapsedMs: Long, maxMs: Long = 8_000): List<TextBox> {
    val (cs, ct) = compactGaps(strokes, texts)
    val cutoff = replayCutoffMs(cs, ct, elapsedMs, maxMs)
    return texts.filterIndexed { i, _ -> ct[i].t <= cutoff }
}
