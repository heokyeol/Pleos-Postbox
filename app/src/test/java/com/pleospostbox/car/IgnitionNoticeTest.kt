package com.pleospostbox.car

import com.pleospostbox.car.data.DummyLetterRepository
import com.pleospostbox.car.data.MemoryStore
import com.pleospostbox.car.notify.IgnitionNotice
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class IgnitionNoticeTest {
    private val json = File("src/main/assets/letters.json").readText()

    @Test fun counts_unread_letters_that_arrived_before_ignition() {
        val repo = DummyLetterRepository({ json }, MemoryStore())
        val unread = repo.letters().count { it.openedAt == null }
        assertEquals(unread, IgnitionNotice.arrivals(repo, Long.MAX_VALUE).size)
    }

    @Test fun letters_after_ignition_and_read_letters_are_not_counted() {
        val repo = DummyLetterRepository({ json }, MemoryStore())
        val unread = repo.letters().filter { it.openedAt == null }.sortedBy { it.createdAt }
        // 두 번째 새 편지가 오기 직전에 시동을 켰다면 한 통
        assertEquals(1, IgnitionNotice.arrivals(repo, unread[1].createdAt).size)
        repo.markOpened(unread[0].id, 1)
        assertEquals(unread.size - 1, IgnitionNotice.arrivals(repo, Long.MAX_VALUE).size)
    }
}
