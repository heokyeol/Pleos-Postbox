package com.pleospostbox.delivery

import com.pleospostbox.letter.Letter
import org.junit.Assert.assertEquals
import org.junit.Test

class DeliveryTest {
    private fun letter(id: String, createdAt: Long, openedAt: Long? = null) =
        Letter(id, "K", "s", createdAt, 0.75f, emptyList(), null, openedAt)

    @Test fun ignition_is_now_minus_uptime() {
        assertEquals(9_000L, ignitionTime(nowMs = 10_000L, elapsedSinceBootMs = 1_000L))
    }

    @Test fun letters_before_ignition_revealed_oldest_first() {
        val shown = lettersToReveal(listOf(letter("b", 200), letter("a", 100)), ignitionAt = 500)
        assertEquals(listOf("a", "b"), shown.map { it.id })
    }

    @Test fun letter_sent_after_ignition_waits_for_next() {
        val letters = listOf(letter("before", 100), letter("after", 600))
        assertEquals(listOf("before"), lettersToReveal(letters, ignitionAt = 500).map { it.id })
        // 다음 시동(1000)에는 보인다
        assertEquals(listOf("before", "after"), lettersToReveal(letters, ignitionAt = 1_000).map { it.id })
    }

    @Test fun opened_letters_are_not_revealed_again() {
        val shown = lettersToReveal(listOf(letter("a", 100, openedAt = 300), letter("b", 200)), ignitionAt = 500)
        assertEquals(listOf("b"), shown.map { it.id })
    }
}
