package com.pleospostbox.car.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pleospostbox.car.R
import com.pleospostbox.car.vehicle.SpeechInput
import com.pleospostbox.letter.Letter
import com.pleospostbox.replay.replayDurationMs

/** 편지 뷰 위쪽에 띄울 것. */
data class LetterHeader(val name: String, val meta: String?, val position: String?)

/** 그린 시간보다 이만큼 빠르게 다시 그린다. */
private const val REPLAY_SPEED = 1.3f

/**
 * 화면 가득한 편지. 처음 열면 그린 순서대로 다시 그린다.
 * played에 들어 있는 편지는 다 그린 모습으로 바로 보여준다(답장 취소로 돌아왔을 때 등).
 * onPrev·onNext가 null이면 그쪽 끝이라 버튼이 꺼진다.
 */
@Composable
fun LetterScreen(
    letter: Letter,
    header: LetterHeader,
    played: MutableSet<String>,
    onToArchive: () -> Unit,
    onPrev: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onReply: (() -> Unit)?,
) {
    val duration = remember(letter.id) { (replayDurationMs(letter.strokes, letter.texts) / REPLAY_SPEED).toLong() }
    var token by remember(letter.id) { mutableIntStateOf(0) }
    val elapsed = remember(letter.id, token) {
        Animatable(if (token == 0 && letter.id in played) duration.toFloat() else 0f)
    }
    LaunchedEffect(letter.id, token) {
        val left = (duration - elapsed.value).toInt()
        if (left > 0) elapsed.animateTo(duration.toFloat(), tween(left, easing = LinearEasing))
        played += letter.id
    }
    val playing = elapsed.value < duration
    var saving by remember(letter.id) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LetterCanvas(letter, if (playing) (elapsed.value * REPLAY_SPEED).toLong() else null, Modifier.fillMaxSize())

        Row(Modifier.padding(40.u), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.u)) {
            PillButton(stringResource(R.string.to_archive), onToArchive, icon = R.drawable.ic_back)
            InfoChip(header.name, header.meta)
        }
        if (header.position != null) GlassChip(header.position, Modifier.align(Alignment.TopEnd).padding(40.u))

        Column(
            Modifier.align(Alignment.BottomStart).padding(40.u),
            verticalArrangement = Arrangement.spacedBy(16.u),
        ) {
            if (!playing) RoundIconButton(R.drawable.ic_replay, stringResource(R.string.replay), { token++ })
            Row(horizontalArrangement = Arrangement.spacedBy(16.u)) {
                if (onReply != null) {
                    // 재생 중에 답장을 눌러도, 돌아오면 다 그린 모습으로 남게 한다
                    PillButton(
                        stringResource(R.string.reply), { played += letter.id; onReply() },
                        Modifier.width(260.u), look = Look.GlassPrimary,
                    )
                }
                RoundIconButton(R.drawable.ic_download, stringResource(R.string.save), { saving = true })
            }
        }

        Row(Modifier.align(Alignment.BottomEnd).padding(40.u), horizontalArrangement = Arrangement.spacedBy(16.u)) {
            RoundIconButton(R.drawable.ic_back, stringResource(R.string.prev), { onPrev?.invoke() }, enabled = onPrev != null)
            RoundIconButton(R.drawable.ic_next, stringResource(R.string.next), { onNext?.invoke() }, enabled = onNext != null)
        }

        if (playing && duration > 0) {
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth(elapsed.value / duration).height(8.u)
                    .background(Ink.Glass)
            )
        }

        if (saving) SaveToPhoneOverlay(letter) { saving = false }
    }
}

/** 휴대폰에 저장: QR을 찍으면 휴대폰 웹에서 이 편지 그림을 연다. 바깥을 누르면 닫힌다. */
@Composable
private fun SaveToPhoneOverlay(letter: Letter, onClose: () -> Unit) {
    val url = stringResource(R.string.letter_url, stringResource(R.string.web_url), letter.id)
    Box(
        Modifier.fillMaxSize().background(Ink.GlassStrong).tapAnywhere(onClose),
        contentAlignment = Alignment.Center,
    ) {
        RoundIconButton(R.drawable.ic_back, stringResource(R.string.close), onClose, Modifier.align(Alignment.TopStart).padding(40.u))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(40.u)) {
            Box(Modifier.size(480.u).clip(RoundedCornerShape(36.u)).background(Ink.Paper).padding(40.u)) {
                QrCode(url, Modifier.fillMaxSize())
            }
            Text(stringResource(R.string.save_how), color = Ink.White, fontSize = 36.su, fontWeight = FontWeight.Medium)
        }
    }
}

/** 물결 효과 없이 아무 곳이나 누르면. 위에 놓인 버튼이 먼저 받는다. */
private fun Modifier.tapAnywhere(onTap: () -> Unit): Modifier =
    pointerInput(onTap) { detectTapGestures { onTap() } }

/** V3·V4: 답장 고르기와 음성 답장. 그림은 다 그린 모습으로 멈춰 있다. */
@Composable
fun ReplyScreen(
    letter: Letter,
    speech: SpeechInput,
    onCancel: () -> Unit,
    onSend: (text: String, preset: Boolean) -> Unit,
) {
    var voice by remember(letter.id) { mutableStateOf(false) }
    var heard by remember(letter.id) { mutableStateOf<String?>(null) }
    fun listen() {
        heard = null
        speech.listen { heard = it }
    }
    DisposableEffect(letter.id) { onDispose { speech.cancel() } }

    // 원형 뒤로가기와 버튼 바깥 터치: 음성 중이면 답장 고르기로, 아니면 답장 취소
    val back: () -> Unit = { if (voice) { speech.cancel(); voice = false } else onCancel() }

    Box(Modifier.fillMaxSize().tapAnywhere(back)) {
        LetterCanvas(letter, null, Modifier.fillMaxSize())
        Row(Modifier.padding(40.u), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.u)) {
            RoundIconButton(R.drawable.ic_back, stringResource(R.string.cancel), back)
            InfoChip(stringResource(R.string.reply_to, letter.senderName), null)
        }

        Column(
            Modifier.align(Alignment.BottomStart).padding(40.u).width(260.u),
            verticalArrangement = Arrangement.spacedBy(16.u),
        ) {
            if (!voice) {
                stringArrayResource(R.array.reply_presets).forEach { p ->
                    PillButton(textGlyphs(p), { onSend(p, true) }, Modifier.fillMaxWidth())
                }
                PillButton(
                    stringResource(R.string.speak), { voice = true; listen() }, Modifier.fillMaxWidth(),
                    look = Look.GlassPrimary, icon = R.drawable.ic_mic,
                )
            } else {
                PillButton(stringResource(R.string.speak_again), { listen() }, Modifier.fillMaxWidth())
                PillButton(
                    stringResource(R.string.send), { heard?.let { onSend(it, false) } }, Modifier.fillMaxWidth(),
                    look = Look.GlassPrimary, enabled = heard != null,
                )
            }
            // 답장 버튼이 있던 자리(맨 아래)는 늘 취소
            PillButton(stringResource(R.string.cancel), back, Modifier.fillMaxWidth())
        }

        if (voice) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = 340.u, end = 40.u, bottom = 40.u)
                    .fillMaxWidth().height(104.u).clip(RoundedCornerShape(50)).background(Ink.GlassStrong)
                    .padding(horizontal = 44.u),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(24.u),
            ) {
                ListenBars()
                val text = heard
                Text(
                    if (text != null) "“$text”" else stringResource(R.string.listening),
                    color = if (text != null) Ink.White else Ink.Meta,
                    fontSize = 40.su,
                    fontWeight = if (text != null) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ListenBars() {
    Row(horizontalArrangement = Arrangement.spacedBy(7.u), verticalAlignment = Alignment.CenterVertically) {
        listOf(20, 44, 60, 32, 48).forEach { h ->
            Box(Modifier.width(8.u).height(h.u).clip(RoundedCornerShape(50)).background(Ink.White))
        }
    }
}


/** ♥ 같은 기호가 컬러 이모지로 바뀌지 않고 글자처럼 보이게 한다(U+FE0E). */
fun textGlyphs(s: String): String = s.replace("♥", "♥\uFE0E")
