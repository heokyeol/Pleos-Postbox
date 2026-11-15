package com.pleospostbox.replay

import com.pleospostbox.letter.Point
import com.pleospostbox.letter.Stroke
import com.pleospostbox.letter.TextBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StrokeReplayTest {
    private fun stroke(vararg ts: Long) = Stroke("#000000", 0.01f, ts.map { Point(0f, 0f, it) })

    private val twoStrokes = listOf(stroke(0, 500, 1_000), stroke(2_000, 3_000))

    @Test fun only_first_points_visible_at_zero() {
        assertEquals(listOf(stroke(0)), visibleStrokes(twoStrokes, elapsedMs = 0))
    }

    @Test fun partial_progress_cuts_inside_strokes() {
        assertEquals(listOf(stroke(0, 500, 1_000), stroke(2_000)), visibleStrokes(twoStrokes, elapsedMs = 2_500))
    }

    @Test fun everything_visible_after_duration() {
        assertEquals(3_000L, replayDurationMs(twoStrokes))
        assertEquals(twoStrokes, visibleStrokes(twoStrokes, replayDurationMs(twoStrokes)))
    }

    @Test fun long_drawing_is_compressed_to_8s() {
        val long = listOf(stroke(0, 10_000, 20_000))
        assertEquals(8_000L, replayDurationMs(long))
        assertEquals(listOf(stroke(0, 10_000)), visibleStrokes(long, elapsedMs = 4_000))
        assertEquals(long, visibleStrokes(long, elapsedMs = 8_000))
    }

    @Test fun text_only_letter_has_no_strokes() {
        assertEquals(0L, replayDurationMs(emptyList()))
        assertTrue(visibleStrokes(emptyList(), 1_000).isEmpty())
    }

    @Test fun text_placed_after_last_stroke_extends_replay() {
        val texts = listOf(TextBox(0f, 0f, "안녕", "#000000", 0.05f, t = 4_000))
        assertEquals(4_000L, replayDurationMs(twoStrokes, texts))
        assertTrue(visibleTexts(texts, twoStrokes, elapsedMs = 3_500).isEmpty())
        assertEquals(texts, visibleTexts(texts, twoStrokes, elapsedMs = 4_000))
    }

    @Test fun texts_are_compressed_with_strokes() {
        val long = listOf(stroke(0, 16_000))
        val texts = listOf(TextBox(0f, 0f, "a", "#000000", 0.05f, t = 8_000))
        assertTrue(visibleTexts(texts, long, elapsedMs = 3_000).isEmpty())
        assertEquals(texts, visibleTexts(texts, long, elapsedMs = 4_000))
    }

    @Test fun long_pause_between_strokes_shrinks_to_1s() {
        val paused = listOf(stroke(0, 500), stroke(4_500, 5_000)) // 4초 멈춤
        assertEquals(2_000L, replayDurationMs(paused))
        assertEquals(listOf(stroke(0, 500), stroke(1_500)), visibleStrokes(paused, elapsedMs = 1_500))
        assertEquals(5_000L, replayDurationMs(listOf(stroke(0, 5_000)))) // 획 안의 시간은 그대로
    }

    @Test fun texts_follow_shrunk_pause() {
        val texts = listOf(TextBox(0f, 0f, "a", "#000000", 0.05f, t = 10_000))
        val strokes = listOf(stroke(0, 500))
        assertEquals(1_500L, replayDurationMs(strokes, texts))
        assertEquals(texts, visibleTexts(texts, strokes, elapsedMs = 1_500))
    }
}
