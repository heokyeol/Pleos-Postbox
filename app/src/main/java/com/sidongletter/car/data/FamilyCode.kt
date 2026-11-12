package com.sidongletter.car.data

import kotlin.random.Random

/** 차와 가족을 잇는 6자 코드. 헷갈리는 0/O/1/I/L은 쓰지 않는다(웹 CODE_RE와 같다). */
object FamilyCode {
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    const val LENGTH = 6

    fun generate(random: Random = Random.Default): String =
        String(CharArray(LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }
}
