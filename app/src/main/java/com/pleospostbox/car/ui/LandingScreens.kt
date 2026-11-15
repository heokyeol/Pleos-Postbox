package com.pleospostbox.car.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.em
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.pleospostbox.car.R

/** C1 가족 연결: 큰 가족 코드와 QR. 처음 실행할 때, '가족 초대'를 눌렀을 때. */
@Composable
fun PairingScreen(code: String, onConfirm: () -> Unit) {
    val url = stringResource(R.string.web_url) + "?code=" + code
    Row(Modifier.fillMaxSize().background(Ink.Black).padding(start = 48.u, top = 48.u, bottom = 48.u, end = 96.u)) {
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Brand(height = 30, textSize = 36)
            Column {
                Text(stringResource(R.string.pair_label), color = Ink.Dim, fontSize = 34.su)
                Spacer(Modifier.height(8.u))
                Text(
                    code, color = Ink.White, fontSize = 200.su, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.06.em, maxLines = 1, modifier = Modifier.offset(x = (-6).u),
                )
                Spacer(Modifier.height(36.u))
                Text(stringResource(R.string.pair_how), color = Ink.Dim, fontSize = 36.su)
            }
            PillButton(stringResource(R.string.pair_confirm), onConfirm, Modifier.width(320.u), look = Look.SolidPrimary)
        }
        Box(
            Modifier.align(Alignment.CenterVertically).size(560.u).clip(RoundedCornerShape(36.u))
                .background(Ink.Paper).padding(44.u)
        ) { QrCode(url, Modifier.fillMaxSize()) }
    }
}

@Composable
fun QrCode(content: String, modifier: Modifier) {
    val matrix = remember(content) {
        QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    }
    Canvas(modifier) {
        val n = matrix.width
        val cell = size.minDimension / n
        for (y in 0 until n) for (x in 0 until n) {
            if (matrix[x, y]) drawRect(Ink.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/** C2 편지 없음: 점선 빈 종이와 한 줄. */
@Composable
fun EmptyScreen(onInvite: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink.Black)) {
        Column(
            Modifier.offset(48.u, 48.u).width(240.u).height(984.u),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Brand()
            PillButton(stringResource(R.string.invite), onInvite, Modifier.width(240.u), look = Look.Solid)
        }
        val radius = 28.u
        val line = 2.u
        val dash = 14.u
        val gap = 10.u
        Box(
            Modifier.offset(336.u, 48.u).size(1536.u, 864.u).drawBehind {
                drawRoundRect(
                    Ink.Line, cornerRadius = CornerRadius(radius.toPx()),
                    style = Stroke(line.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash.toPx(), gap.toPx()))),
                )
            }
        )
        Box(Modifier.offset(336.u, 912.u).height(120.u), contentAlignment = Alignment.CenterStart) {
            Text(
                stringResource(R.string.empty_title), color = Ink.White, fontSize = 50.su,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** 주행 중 잠금. 그림·버튼·움직임 없음. */
@Composable
fun LockedScreen(newCount: Int) {
    Box(Modifier.fillMaxSize().background(Ink.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(28.u)) {
            Box(Modifier.size(140.u).clip(CircleShape).background(Ink.Surface), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_lock), null, Modifier.size(60.u), colorFilter = ColorFilter.tint(Ink.White))
            }
            if (newCount > 0) {
                Text(
                    stringResource(R.string.locked_waiting, TimeLabels.countWord(newCount)),
                    color = Ink.White, fontSize = 56.su, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
                Text(stringResource(R.string.locked_waiting_sub), color = Ink.Dim, fontSize = 36.su)
            } else {
                Text(
                    stringResource(R.string.locked_archive),
                    color = Ink.White, fontSize = 56.su, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 시동 전에 온 새 편지: 봉투, "새로운 편지가 n통 왔어요.", 읽기 / 나중에 읽기. */
@Composable
fun ArrivedScreen(count: Int, onRead: () -> Unit, onLater: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink.Black)) {
        Brand(Modifier.offset(64.u, 76.u), textSize = 34)
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Envelope(Modifier.size(560.u, 360.u).clip(RoundedCornerShape(24.u)))
            Spacer(Modifier.height(64.u))
            Text(
                stringResource(R.string.arrived_title, count),
                color = Ink.White, fontSize = 56.su, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(56.u))
            Row(horizontalArrangement = Arrangement.spacedBy(24.u)) {
                PillButton(stringResource(R.string.arrived_read), onRead, Modifier.width(320.u), look = Look.SolidPrimary)
                PillButton(stringResource(R.string.arrived_later), onLater, Modifier.width(320.u), look = Look.Solid)
            }
        }
    }
}
