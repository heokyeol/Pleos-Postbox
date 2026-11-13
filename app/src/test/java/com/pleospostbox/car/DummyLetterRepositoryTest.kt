package com.pleospostbox.car

import com.pleospostbox.car.data.DummyLetterRepository
import com.pleospostbox.car.data.MemoryStore
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class DummyLetterRepositoryTest {
    private val json = File("src/main/assets/letters.json").readText()

    @Test fun bundled_dummy_letters_parse_with_two_new_ones() {
        val repo = DummyLetterRepository({ json }, MemoryStore())
        val letters = repo.letters()
        assertEquals(6, letters.size)
        assertEquals(2, letters.count { it.openedAt == null })
    }

    @Test fun opened_is_stored_locally_and_can_be_reset() {
        val repo = DummyLetterRepository({ json }, MemoryStore())
        val id = repo.letters().first { it.openedAt == null }.id
        repo.markOpened(id, 42)
        assertEquals(42L, repo.letters().first { it.id == id }.openedAt)
        repo.resetOpened()
        assertEquals(null, repo.letters().first { it.id == id }.openedAt)
    }
}
