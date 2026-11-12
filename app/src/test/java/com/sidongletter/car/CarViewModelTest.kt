package com.sidongletter.car

import com.sidongletter.car.data.LetterRepository
import com.sidongletter.car.data.MemoryStore
import com.sidongletter.car.data.PairingStore
import com.sidongletter.letter.Letter
import com.sidongletter.letter.Reply
import com.sidongletter.screen.CarScreenState
import com.sidongletter.screen.DriveState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarViewModelTest {
    private class FakeRepo(private val base: List<Letter>) : LetterRepository {
        val opened = mutableMapOf<String, Long>()
        val replies = mutableListOf<Reply>()
        override fun letters() = base.map { l -> opened[l.id]?.let { if (l.openedAt == null) l.copy(openedAt = it) else l } ?: l }
        override fun markOpened(id: String, at: Long) { opened.putIfAbsent(id, at) }
        override fun sendReply(reply: Reply) { replies += reply }
        override fun resetOpened() = opened.clear()
    }

    private fun letter(id: String, created: Long, opened: Long? = null) =
        Letter(id, "K7Q2MX", "민준", created, 16f / 9, emptyList(), null, opened)

    private val old = letter("old", 100, opened = 150)
    private val a = letter("a", 200)
    private val b = letter("b", 300)
    private val late = letter("late", 900) // 시동 뒤에 온 편지

    private var clock = 1_000L
    private fun vm(repo: FakeRepo, store: MemoryStore = MemoryStore()) =
        CarViewModel(repo, PairingStore(store), now = { clock }, elapsedSinceBoot = { 500 }) // 시동 = 500

    @Test fun startup_reveals_letters_sent_before_ignition_oldest_first() {
        val v = vm(FakeRepo(listOf(old, b, a, late)))
        assertEquals(CarScreenState.Revealing(a, index = 1, total = 2), v.model.value.screen)
        assertEquals(listOf(old), v.model.value.archive)
    }

    @Test fun back_marks_opened_and_ends_in_archive() {
        val repo = FakeRepo(listOf(old, a, b))
        val v = vm(repo)
        v.back()
        assertEquals(setOf("a"), repo.opened.keys)
        v.back()
        assertEquals(CarScreenState.Archive, v.model.value.screen)
        assertEquals(listOf(b, a, old), v.model.value.archive)
        assertEquals(setOf("a", "b"), repo.opened.keys)
    }

    @Test fun reply_is_recorded_and_letter_marked_opened() {
        val repo = FakeRepo(listOf(a))
        val v = vm(repo)
        v.requestReply()
        v.sendReply("고마워", preset = true)
        assertEquals(listOf(Reply("a", "고마워", null)), repo.replies)
        assertEquals(CarScreenState.Replied(a, "고마워"), v.model.value.screen)
        assertTrue("a" in repo.opened)
    }

    @Test fun voice_reply_goes_to_text() {
        val repo = FakeRepo(listOf(a))
        val v = vm(repo)
        v.requestReply()
        v.sendReply("조금 늦어", preset = false)
        assertEquals(Reply("a", null, "조금 늦어"), repo.replies.single())
    }

    @Test fun next_ignition_does_not_show_read_letters_again_but_shows_late_one() {
        val repo = FakeRepo(listOf(a, late))
        vm(repo).back()
        clock = 2_000
        val v = vm(repo).apply { restartIgnition() }
        assertEquals(CarScreenState.Revealing(late, index = 1, total = 1), v.model.value.screen)
    }

    @Test fun reset_read_shows_new_letters_again() {
        val repo = FakeRepo(listOf(old, a))
        val v = vm(repo)
        v.back()
        v.resetRead()
        assertEquals(CarScreenState.Revealing(a, index = 1, total = 1), v.model.value.screen)
    }

    @Test fun drive_override_beats_system_signal_until_cleared() {
        val v = vm(FakeRepo(listOf(a)))
        v.overrideDrive(DriveState.DRIVING)
        assertEquals(CarScreenState.Locked(1), v.model.value.screen)
        v.onSystemDrive(DriveState.PARKED)
        assertEquals(CarScreenState.Locked(1), v.model.value.screen)
        v.overrideDrive(null)
        assertEquals(CarScreenState.Revealing(a, index = 1, total = 1), v.model.value.screen)
    }

    @Test fun locked_does_not_mark_opened() {
        val repo = FakeRepo(listOf(a))
        val v = vm(repo)
        v.onSystemDrive(DriveState.DRIVING)
        v.back()
        assertTrue(repo.opened.isEmpty())
    }

    @Test fun pairing_shown_until_confirmed() {
        val store = MemoryStore()
        val v = vm(FakeRepo(emptyList()), store)
        assertTrue(v.showPairing.value)
        v.confirmPairing()
        assertFalse(v.showPairing.value)
        assertFalse(vm(FakeRepo(emptyList()), store).showPairing.value)
    }
}
