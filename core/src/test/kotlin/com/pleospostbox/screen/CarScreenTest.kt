package com.pleospostbox.screen

import com.pleospostbox.letter.Letter
import com.pleospostbox.screen.CarScreenState.Reading
import com.pleospostbox.screen.LetterSource.ARCHIVE
import com.pleospostbox.screen.LetterSource.NEW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarScreenTest {
    private fun letter(id: String, at: Long) = Letter(id, "K", "s", at, 16f / 9, emptyList(), null, null)
    private val a = letter("a", 10)
    private val b = letter("b", 20)
    private val old1 = letter("old1", 1)
    private val old2 = letter("old2", 2)

    private fun start(drive: DriveState, letters: List<Letter>, archived: List<Letter> = emptyList()) =
        reduce(CarModel.initial(drive), CarEvent.LettersReady(letters, archived))

    /** 도착 화면에서 '읽기'까지 누른 상태 */
    private fun reading(letters: List<Letter>, archived: List<Letter> = emptyList()) =
        reduce(start(DriveState.PARKED, letters, archived), CarEvent.ReadNew)

    @Test fun nothing_at_all_is_empty_screen() {
        assertEquals(CarScreenState.Empty, start(DriveState.PARKED, emptyList()).screen)
        assertEquals(CarScreenState.Empty, start(DriveState.DRIVING, emptyList()).screen)
    }

    @Test fun parked_with_new_letters_shows_arrival_with_count() {
        val m = start(DriveState.PARKED, listOf(a, b))
        assertEquals(CarScreenState.Arrived(2), m.screen)
        assertEquals(emptySet<String>(), m.seen)
        assertTrue(m.isUnread("a") && m.isUnread("b"))
    }

    @Test fun read_on_arrival_opens_first_and_marks_it_seen() {
        val m = reading(listOf(a, b))
        assertEquals(Reading(a, NEW, index = 0, total = 2), m.screen)
        assertEquals(setOf("a"), m.seen)
        assertFalse(m.isUnread("a"))
        assertTrue(m.isUnread("b"))
    }

    @Test fun later_on_arrival_goes_to_archive_and_stays_there_after_driving() {
        var m = reduce(start(DriveState.PARKED, listOf(a, b), listOf(old1)), CarEvent.Later)
        assertEquals(CarScreenState.Archive, m.screen)
        assertEquals(emptySet<String>(), m.seen)
        assertFalse(m.isUnread("old1"))
        m = reduce(m, CarEvent.DriveChanged(DriveState.DRIVING))
        m = reduce(m, CarEvent.DriveChanged(DriveState.PARKED))
        assertEquals(CarScreenState.Archive, m.screen)
    }

    @Test fun archive_holds_new_and_old_letters_newest_first() {
        val m = start(DriveState.PARKED, listOf(a, b), listOf(old1, old2))
        assertEquals(listOf(b, a, old2, old1), m.archive)
    }

    @Test fun parked_with_only_archive_shows_archive() {
        assertEquals(CarScreenState.Archive, start(DriveState.PARKED, emptyList(), listOf(old1)).screen)
    }

    @Test fun next_is_newer_and_prev_is_older_inside_new_letters() {
        var m = reading(listOf(a, b))
        m = reduce(m, CarEvent.Next)
        assertEquals(Reading(b, NEW, index = 1, total = 2), m.screen)
        assertEquals(setOf("a", "b"), m.seen)
        assertEquals(m, reduce(m, CarEvent.Next)) // 마지막에서는 그대로
        m = reduce(m, CarEvent.Prev)
        assertEquals(Reading(a, NEW, index = 0, total = 2), m.screen)
        assertEquals(m, reduce(m, CarEvent.Prev)) // 처음에서는 그대로
    }

    @Test fun to_archive_goes_straight_to_list() {
        var m = reading(listOf(a, b), listOf(old1))
        m = reduce(m, CarEvent.ToArchive)
        assertEquals(CarScreenState.Archive, m.screen)
        assertEquals(setOf("a"), m.seen) // b는 보지 않았다
    }

    @Test fun archive_prev_is_older_next_is_newer() {
        var m = start(DriveState.PARKED, emptyList(), listOf(old1, old2))
        m = reduce(m, CarEvent.Open(old2))
        assertEquals(Reading(old2, ARCHIVE, index = 0, total = 2), m.screen)
        assertEquals(m, reduce(m, CarEvent.Next)) // 가장 최근 편지라 더 최근은 없다
        m = reduce(m, CarEvent.Prev) // 더 이전 편지
        assertEquals(Reading(old1, ARCHIVE, index = 1, total = 2), m.screen)
        m = reduce(m, CarEvent.Next)
        assertEquals(Reading(old2, ARCHIVE, index = 0, total = 2), m.screen)
        m = reduce(m, CarEvent.ToArchive)
        assertEquals(CarScreenState.Archive, m.screen)
    }

    @Test fun opening_unseen_new_letter_from_archive_marks_it_seen() {
        var m = reduce(reading(listOf(a, b)), CarEvent.ToArchive)
        m = reduce(m, CarEvent.Open(b))
        assertTrue("b" in m.seen)
    }

    @Test fun reply_flow_returns_to_same_letter_with_reply() {
        var m = reading(listOf(a, b))
        m = reduce(m, CarEvent.ReplyRequested)
        val reading = Reading(a, NEW, index = 0, total = 2)
        assertEquals(CarScreenState.Replying(reading), m.screen)
        assertEquals(reading, reduce(m, CarEvent.ReplyCancelled).screen)
        m = reduce(m, CarEvent.ReplySent("고마워"))
        assertEquals(reading.copy(reply = "고마워"), m.screen)
        m = reduce(m, CarEvent.Next)
        assertEquals(Reading(b, NEW, index = 1, total = 2), m.screen) // 다음 편지에는 답장 표시가 없다
    }

    @Test fun not_parked_locks_with_unseen_count() {
        assertEquals(CarScreenState.Locked(2), start(DriveState.DRIVING, listOf(a, b)).screen)
        assertEquals(CarScreenState.Locked(2), start(DriveState.IDLING, listOf(a, b)).screen)
        assertEquals(CarScreenState.Locked(0), start(DriveState.DRIVING, emptyList(), listOf(old1)).screen)
        assertEquals(emptySet<String>(), start(DriveState.DRIVING, listOf(a)).seen)
    }

    @Test fun driving_mid_letters_locks_with_remaining_then_resumes_first_unseen() {
        var m = reading(listOf(a, b))
        m = reduce(m, CarEvent.DriveChanged(DriveState.DRIVING))
        assertEquals(CarScreenState.Locked(1), m.screen)
        m = reduce(m, CarEvent.DriveChanged(DriveState.PARKED))
        assertEquals(CarScreenState.Arrived(1), m.screen)
        assertEquals(Reading(b, NEW, index = 1, total = 2), reduce(m, CarEvent.ReadNew).screen)
    }

    @Test fun parking_with_everything_seen_shows_archive() {
        var m = reduce(reading(listOf(a)), CarEvent.DriveChanged(DriveState.DRIVING))
        m = reduce(m, CarEvent.DriveChanged(DriveState.PARKED))
        assertEquals(CarScreenState.Archive, m.screen)
    }

    @Test fun parked_again_keeps_current_screen() {
        val m = reduce(reading(listOf(a)), CarEvent.ReplyRequested)
        assertEquals(m, reduce(m, CarEvent.DriveChanged(DriveState.PARKED)))
    }

    @Test fun nothing_works_while_driving() {
        val m = start(DriveState.DRIVING, listOf(a), listOf(old1))
        for (e in listOf(CarEvent.ReplyRequested, CarEvent.Open(old1), CarEvent.Next, CarEvent.ToArchive, CarEvent.ReadNew, CarEvent.Later)) {
            assertEquals(m, reduce(m, e))
        }
    }
}
