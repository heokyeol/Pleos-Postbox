package com.pleospostbox.car

import com.pleospostbox.car.data.LetterRepository
import com.pleospostbox.car.data.MemoryStore
import com.pleospostbox.car.data.PairingStore
import com.pleospostbox.letter.Letter
import com.pleospostbox.letter.Reply
import com.pleospostbox.screen.CarScreenState
import com.pleospostbox.screen.CarScreenState.Reading
import com.pleospostbox.screen.DriveState
import com.pleospostbox.screen.LetterSource.NEW
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

    @Test fun startup_shows_letters_sent_before_ignition_oldest_first() {
        val v = vm(FakeRepo(listOf(old, b, a, late)))
        assertEquals(CarScreenState.Arrived(2), v.model.value.screen)
        v.readNew()
        assertEquals(Reading(a, NEW, index = 0, total = 2), v.model.value.screen)
        assertEquals(listOf(b, a, old), v.model.value.archive)
    }

    @Test fun shown_letters_are_marked_opened() {
        val repo = FakeRepo(listOf(old, a, b))
        val v = vm(repo)
        assertTrue(repo.opened.isEmpty()) // 도착 화면에서는 아직 안 읽었다
        v.readNew()
        assertEquals(setOf("a"), repo.opened.keys)
        v.next()
        assertEquals(setOf("a", "b"), repo.opened.keys)
    }

    @Test fun to_archive_leaves_unseen_letters_new_for_next_ignition() {
        val repo = FakeRepo(listOf(a, b))
        vm(repo).apply { readNew() }.toArchive()
        assertEquals(setOf("a"), repo.opened.keys)
        clock = 2_000
        val v = vm(repo)
        assertEquals(CarScreenState.Arrived(1), v.model.value.screen)
        v.readNew()
        assertEquals(Reading(b, NEW, index = 0, total = 1), v.model.value.screen)
    }

    @Test fun reply_is_recorded_and_shown_on_same_letter() {
        val repo = FakeRepo(listOf(a, b))
        val v = vm(repo)
        v.readNew()
        v.requestReply()
        v.sendReply("고마워", preset = true)
        assertEquals(listOf(Reply("a", "고마워", null)), repo.replies)
        assertEquals(Reading(a, NEW, index = 0, total = 2, reply = "고마워"), v.model.value.screen)
    }

    @Test fun voice_reply_goes_to_text() {
        val repo = FakeRepo(listOf(a))
        val v = vm(repo)
        v.readNew()
        v.requestReply()
        v.sendReply("조금 늦어", preset = false)
        assertEquals(Reply("a", null, "조금 늦어"), repo.replies.single())
    }

    @Test fun next_ignition_does_not_show_read_letters_again_but_shows_late_one() {
        val repo = FakeRepo(listOf(a, late))
        vm(repo).readNew()
        clock = 2_000
        val v = vm(repo)
        v.readNew()
        assertEquals(Reading(late, NEW, index = 0, total = 1), v.model.value.screen)
    }

    @Test fun reset_read_shows_new_letters_again() {
        val repo = FakeRepo(listOf(old, a))
        val v = vm(repo)
        v.readNew()
        v.toArchive()
        v.resetRead()
        assertEquals(CarScreenState.Arrived(1), v.model.value.screen)
    }

    @Test fun drive_override_beats_system_signal_until_cleared() {
        val v = vm(FakeRepo(listOf(a, b)))
        v.readNew()
        v.overrideDrive(DriveState.DRIVING)
        assertEquals(CarScreenState.Locked(1), v.model.value.screen)
        v.onSystemDrive(DriveState.PARKED)
        assertEquals(CarScreenState.Locked(1), v.model.value.screen)
        v.overrideDrive(null)
        assertEquals(CarScreenState.Arrived(1), v.model.value.screen)
        v.readNew()
        assertEquals(Reading(b, NEW, index = 1, total = 2), v.model.value.screen)
    }

    @Test fun nothing_is_marked_while_driving() {
        val repo = FakeRepo(listOf(a))
        val v = CarViewModel(repo, PairingStore(MemoryStore()), { clock }, { 500 }).apply { overrideDrive(DriveState.DRIVING) }
        repo.opened.clear()
        v.restartIgnition()
        v.readNew()
        v.next()
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
