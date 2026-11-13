package com.pleospostbox.delivery

import com.pleospostbox.letter.Letter

/** 차량 전원이 켜지면 시스템이 부팅되므로, 부팅 시각을 시동 시각으로 본다. */
fun ignitionTime(nowMs: Long, elapsedSinceBootMs: Long): Long = nowMs - elapsedSinceBootMs

/** 이번 시동 전에 도착했고 아직 안 연 편지를, 오래된 순으로. 시동 뒤 온 편지는 다음 시동까지 기다린다. */
fun lettersToReveal(letters: List<Letter>, ignitionAt: Long): List<Letter> =
    letters.filter { it.openedAt == null && it.createdAt < ignitionAt }.sortedBy { it.createdAt }
