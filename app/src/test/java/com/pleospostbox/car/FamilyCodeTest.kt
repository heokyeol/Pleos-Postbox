package com.pleospostbox.car

import com.pleospostbox.car.data.FamilyCode
import com.pleospostbox.car.data.MemoryStore
import com.pleospostbox.car.data.PairingStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FamilyCodeTest {
    @Test fun generated_codes_are_valid_and_avoid_confusing_chars() {
        val r = Random(1)
        repeat(500) {
            val c = FamilyCode.generate(r)
            assertTrue(c, FamilyCode.isValid(c))
            assertTrue(c.none { it in "0O1IL" })
        }
    }

    @Test fun validation() {
        assertTrue(FamilyCode.isValid("K7Q2MX"))
        assertFalse(FamilyCode.isValid("K7Q2M"))
        assertFalse(FamilyCode.isValid("K7Q2M0"))
        assertFalse(FamilyCode.isValid("k7q2mx"))
    }

    @Test fun pairing_store_keeps_same_code() {
        val store = MemoryStore()
        val code = PairingStore(store).familyCode
        assertEquals(code, PairingStore(store).familyCode)
        assertFalse(PairingStore(store).paired)
        PairingStore(store).paired = true
        assertTrue(PairingStore(store).paired)
    }
}
