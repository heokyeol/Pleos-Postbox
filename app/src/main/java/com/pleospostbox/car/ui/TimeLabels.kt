package com.pleospostbox.car.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** 편지 시각을 "오늘 아침 8:12"처럼 풀어 쓴다. */
object TimeLabels {
    fun partOfDay(hour: Int): String = when (hour) {
        in 5..10 -> "아침"
        in 11..16 -> "낮"
        in 17..20 -> "저녁"
        else -> "밤"
    }

    /** "아침 8:12" */
    fun clock(ms: Long, zone: ZoneId): String {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        val h12 = (t.hour % 12).let { if (it == 0) 12 else it }
        return "${partOfDay(t.hour)} $h12:%02d".format(t.minute)
    }

    /** 편지 화면 위쪽: "오늘 아침 8:12", "어제 저녁 6:05", "11월 10일 낮 2:30" */
    fun meta(ms: Long, nowMs: Long, zone: ZoneId): String {
        val day = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val prefix = when (day) {
            today -> "오늘"
            today.minusDays(1) -> "어제"
            else -> "${day.monthValue}월 ${day.dayOfMonth}일"
        }
        return "$prefix ${clock(ms, zone)}"
    }

    /** 받은 편지함 날짜 묶음 제목: (굵게, 흐리게). ("오늘", "11월 13일 금요일"), ("11월 10일", "화요일") */
    fun dayHeader(day: LocalDate, today: LocalDate): Pair<String, String> {
        val weekday = day.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.KOREAN)
        val date = "${day.monthValue}월 ${day.dayOfMonth}일"
        return when (day) {
            today -> "오늘" to "$date $weekday"
            today.minusDays(1) -> "어제" to "$date $weekday"
            else -> date to weekday
        }
    }

    fun localDate(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** 1~10은 "한·두·세…", 그 위는 숫자. "편지 두 통" */
    fun countWord(n: Int): String =
        listOf("한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열").getOrNull(n - 1) ?: n.toString()
}
