package com.pleospostbox.car.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** docs/design/screens.html, car-v2.html 의 색. */
object Ink {
    val Black = Color(0xFF0C0B0A)      // 따뜻한 검정
    val Surface = Color(0xFF1D1B19)    // 어두운 바탕 위 버튼·패널
    val Line = Color(0xFF36322E)
    val White = Color(0xFFF6F1E9)      // 글자·주 버튼
    val Paper = Color(0xFFFFFFFF)      // 종이만 순백
    val Dim = Color(0xFFA9A198)        // 보조 글자
    val Meta = Color(0xFFD9D2C8)       // 반투명 칩 위 보조 글자
    val Off = Color(0xFF6C665F)        // 꺼진 버튼 글자
    val Glass = Color(0x850C0B0A)      // 종이 위 반투명 버튼 (검정 52%)
    val GlassStrong = Color(0xE00C0B0A) // 그림 위에서도 읽혀야 하는 문장 띠 (검정 88%)
    val GlassHi = Color(0xE6F6F1E9)    // 종이 위 반투명 주 버튼 (흰색 90%)
}

/**
 * 디자인은 1920×1080 기준 숫자로 쓴다. 실제 화면에 맞춰 이 단위를 늘리거나 줄인다.
 * (Pleos Connect v2.1.0 에뮬레이터는 2560×1440 mdpi라 1단위 ≈ 1.33dp)
 */
val LocalUnit = staticCompositionLocalOf { 1f }

val Int.u: Dp
    @Composable @ReadOnlyComposable get() = (this * LocalUnit.current).dp

val Int.su: TextUnit
    @Composable @ReadOnlyComposable get() = (this * LocalUnit.current / LocalDensity.current.fontScale).sp

/**
 * 앱 전체 글꼴. 현대차 기본 글꼴(Hyundai Sans) 파일을 받으면 res/font에 넣고 여기만 바꾼다.
 * 예) FontFamily(Font(R.font.hyundai_sans_text_regular), Font(R.font.hyundai_sans_text_bold, FontWeight.Bold))
 * 그 전까지는 차량 시스템의 기본 산세리프를 쓴다.
 */
val AppFont: FontFamily = FontFamily.SansSerif
