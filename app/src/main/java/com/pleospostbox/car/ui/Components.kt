package com.pleospostbox.car.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pleospostbox.car.R

enum class Look { Glass, GlassPrimary, Solid, SolidPrimary }

private fun Look.colors(enabled: Boolean): Pair<Color, Color> = when (this) {
    Look.Glass -> Ink.Glass to Ink.White
    Look.GlassPrimary -> if (enabled) Ink.GlassHi to Ink.Black else Ink.Glass to Ink.Off
    Look.Solid -> Ink.Surface to Ink.White
    Look.SolidPrimary -> if (enabled) Ink.White to Ink.Black else Ink.Surface to Ink.Off
}

/** 높이 104, 글자 34의 알약 버튼. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    look: Look = Look.Glass,
    enabled: Boolean = true,
    @DrawableRes icon: Int? = null,
) {
    val (bg, fg) = look.colors(enabled)
    Row(
        modifier
            .height(104.u)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 40.u),
        horizontalArrangement = Arrangement.spacedBy(14.u, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Image(painterResource(icon), null, Modifier.size(36.u), colorFilter = ColorFilter.tint(fg))
        Text(text, color = fg, fontSize = 34.su, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** 아이콘만 있는 원형 버튼(뒤로, 다시 보기). */
@Composable
fun RoundIconButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(104.u)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(CircleShape)
            .background(Ink.Glass)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(icon), label, Modifier.size(42.u), colorFilter = ColorFilter.tint(Ink.White))
    }
}

/** 종이 위 반투명 칩: 이름(굵게) + 보조 글. */
@Composable
fun InfoChip(name: String, meta: String?, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(88.u)
            .clip(RoundedCornerShape(50))
            .background(Ink.Glass)
            .padding(horizontal = 36.u),
        horizontalArrangement = Arrangement.spacedBy(20.u),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, color = Ink.White, fontSize = 40.su, fontWeight = FontWeight.Bold, maxLines = 1)
        if (meta != null) {
            Text(meta, color = Ink.Meta, fontSize = 32.su, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun GlassChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier.height(88.u).clip(RoundedCornerShape(50)).background(Ink.Glass).padding(horizontal = 32.u),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Ink.White, fontSize = 32.su, style = TextStyle(fontFeatureSettings = "tnum"))
    }
}

/** 앱 이름 "플레오스 우체통". height는 예전 로고 높이 기준이라 글자 크기만 쓴다. */
@Composable
fun Brand(modifier: Modifier = Modifier, @Suppress("UNUSED_PARAMETER") height: Int = 26, textSize: Int = 32) {
    Text(
        stringResource(R.string.brand), modifier = modifier,
        color = Ink.White, fontSize = textSize.su, fontWeight = FontWeight.SemiBold, maxLines = 1,
    )
}
