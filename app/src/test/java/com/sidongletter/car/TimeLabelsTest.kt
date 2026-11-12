package com.sidongletter.car

import com.sidongletter.car.ui.TimeLabels
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeLabelsTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun at(d: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 11, d, h, m, 0, 0, seoul).toInstant().toEpochMilli()
    private val now = at(13, 9, 0)

    @Test fun meta_uses_today_yesterday_or_date() {
        assertEquals("오늘 아침 8:12", TimeLabels.meta(at(13, 8, 12), now, seoul))
        assertEquals("어제 저녁 6:05", TimeLabels.meta(at(12, 18, 5), now, seoul))
        assertEquals("11월 10일 낮 2:30", TimeLabels.meta(at(10, 14, 30), now, seoul))
        assertEquals("11월 10일 밤 12:00", TimeLabels.meta(at(10, 0, 0), now, seoul))
        assertEquals("11월 10일 낮 12:05", TimeLabels.meta(at(10, 12, 5), now, seoul))
    }

    @Test fun day_header() {
        val today = LocalDate.of(2026, 11, 13)
        assertEquals("오늘" to "11월 13일 금요일", TimeLabels.dayHeader(today, today))
        assertEquals("어제" to "11월 12일 목요일", TimeLabels.dayHeader(today.minusDays(1), today))
        assertEquals("11월 10일" to "화요일", TimeLabels.dayHeader(today.minusDays(3), today))
    }

    @Test fun count_words() {
        assertEquals("한", TimeLabels.countWord(1))
        assertEquals("두", TimeLabels.countWord(2))
        assertEquals("12", TimeLabels.countWord(12))
    }
}
