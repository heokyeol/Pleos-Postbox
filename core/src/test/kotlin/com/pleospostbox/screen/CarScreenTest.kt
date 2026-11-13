package com.pleospostbox.screen

import com.pleospostbox.letter.Letter
import org.junit.Assert.assertEquals
import org.junit.Test

class CarScreenTest {
    private fun letter(id: String, at: Long) = Letter(id, "K", "s", at, 16f / 9, emptyList(), null, null)
    private val a = letter("a", 10)
    private val b = letter("b", 20)
    private val old1 = letter("old1", 1)
    private val old2 = letter("old2", 2)

    private fun start(drive: DriveState, letters: List<Letter>, archived: List<Letter> = emptyList()) =
        reduce(CarModel.initial(drive), CarEvent.LettersReady(letters, archived))

    @Test fun nothing_at_all_is_empty_screen() {
        assertEquals(CarScreenState.Empty, start(DriveState.PARKED, emptyList()).screen)
        assertEquals(CarScreenState.Empty, start(DriveState.DRIVING, emptyList()).screen)
    }

    @Test fun parked_with_new_letters_reveals_first_with_position() {
        assertEquals(CarScreenState.Revealing(a, index = 1, total = 2), start(DriveState.PARKED, listOf(a, b)).screen)
    }

    @Test fun parked_with_only_archive_shows_archive_newest_first() {
        val m = start(DriveState.PARKED, emptyList(), listOf(old1, old2))
        assertEquals(CarScreenState.Archive, m.screen)
        assertEquals(listOf(old2, old1), m.archive)
    }

    @Test fun not_parked_locks_with_new_count() {
        assertEquals(CarScreenState.Locked(2), start(DriveState.DRIVING, listOf(a, b)).screen)
        assertEquals(CarScreenState.Locked(2), start(DriveState.IDLING, listOf(a, b)).screen)
        assertEquals(CarScreenState.Locked(0), start(DriveState.DRIVING, emptyList(), listOf(old1)).screen)
    }

    @Test fun driving_during_reveal_locks_and_restarts_later() {
        var m = start(DriveState.PARKED, listOf(a, b))
        m = reduce(m, CarEvent.DriveChanged(DriveState.DRIVING))
        assertEquals(CarScreenState.Locked(2), m.screen)
        m = reduce(m, CarEvent.DriveChanged(DriveState.PARKED))
        assertEquals(CarScreenState.Revealing(a, index = 1, total = 2), m.screen)
    }

    @Test fun parked_again_keeps_current_screen() {
        var m = start(DriveState.PARKED, listOf(a))
        m = reduce(m, CarEvent.ReplyRequested)
        assertEquals(m, reduce(m, CarEvent.DriveChanged(DriveState.PARKED)))
    }

    @Test fun back_moves_to_next_new_letter_then_archive() {
        var m = start(DriveState.PARKED, listOf(a, b), listOf(old1))
        m = reduce(m, CarEvent.Back)
        assertEquals(CarScreenState.Revealing(b, index = 2, total = 2), m.screen)
        m = reduce(m, CarEvent.Back)
        assertEquals(CarScreenState.Archive, m.screen)
        assertEquals(listOf(b, a, old1), m.archive)
        assertEquals(emptyList<Letter>(), m.queue)
    }

    @Test fun reply_to_new_letter_opens_next_or_shows_replied_on_last() {
        var m = start(DriveState.PARKED, listOf(a, b))
        m = reduce(m, CarEvent.ReplyRequested)
        assertEquals(CarScreenState.Replying(a, fromArchive = false), m.screen)
        m = reduce(m, CarEvent.ReplySent("고마워"))
        assertEquals(CarScreenState.Revealing(b, index = 2, total = 2), m.screen)
        m = reduce(m, CarEvent.ReplyRequested)
        m = reduce(m, CarEvent.ReplySent("사랑해"))
        assertEquals(CarScreenState.Replied(b, "사랑해"), m.screen)
        m = reduce(m, CarEvent.Back)
        assertEquals(CarScreenState.Archive, m.screen)
    }

    @Test fun reply_cancel_returns_to_where_it_came_from() {
        var m = reduce(start(DriveState.PARKED, listOf(a)), CarEvent.ReplyRequested)
        m = reduce(m, CarEvent.ReplyCancelled)
        assertEquals(CarScreenState.Revealing(a, index = 1, total = 1), m.screen)

        m = start(DriveState.PARKED, emptyList(), listOf(old1))
        m = reduce(reduce(m, CarEvent.Open(old1)), CarEvent.ReplyRequested)
        assertEquals(CarScreenState.Replying(old1, fromArchive = true), m.screen)
        m = reduce(m, CarEvent.ReplyCancelled)
        assertEquals(CarScreenState.Viewing(old1), m.screen)
    }

    @Test fun open_from_archive_and_back() {
        var m = start(DriveState.PARKED, emptyList(), listOf(old1, old2))
        m = reduce(m, CarEvent.Open(old1))
        assertEquals(CarScreenState.Viewing(old1), m.screen)
        m = reduce(m, CarEvent.Back)
        assertEquals(CarScreenState.Archive, m.screen)
    }

    @Test fun reply_to_archived_letter_shows_replied() {
        var m = start(DriveState.PARKED, emptyList(), listOf(old1))
        m = reduce(reduce(m, CarEvent.Open(old1)), CarEvent.ReplyRequested)
        m = reduce(m, CarEvent.ReplySent("♥"))
        assertEquals(CarScreenState.Replied(old1, "♥"), m.screen)
        assertEquals(listOf(old1), m.archive)
    }

    @Test fun nothing_opens_or_replies_while_driving() {
        val m = start(DriveState.DRIVING, listOf(a), listOf(old1))
        assertEquals(m, reduce(m, CarEvent.ReplyRequested))
        assertEquals(m, reduce(m, CarEvent.Open(old1)))
        assertEquals(m, reduce(m, CarEvent.Back))
    }

    @Test fun driving_while_viewing_archive_locks_then_returns_to_archive() {
        var m = start(DriveState.PARKED, emptyList(), listOf(old1))
        m = reduce(m, CarEvent.Open(old1))
        m = reduce(m, CarEvent.DriveChanged(DriveState.DRIVING))
        assertEquals(CarScreenState.Locked(0), m.screen)
        m = reduce(m, CarEvent.DriveChanged(DriveState.PARKED))
        assertEquals(CarScreenState.Archive, m.screen)
    }
}
