package com.pleospostbox.car.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pleospostbox.car.CarViewModel
import com.pleospostbox.car.R
import com.pleospostbox.car.vehicle.SpeechInput
import com.pleospostbox.screen.CarScreenState
import com.pleospostbox.screen.LetterSource
import java.time.ZoneId

@Composable
fun PostboxApp(vm: CarViewModel, speech: SpeechInput, now: () -> Long) {
    val model by vm.model.collectAsStateWithLifecycle()
    val pairing by vm.showPairing.collectAsStateWithLifecycle()
    val override by vm.driveOverride.collectAsStateWithLifecycle()
    val played = remember { mutableSetOf<String>() }
    val zone = remember { ZoneId.systemDefault() }

    BoxWithConstraints(Modifier.fillMaxSize().background(Ink.Black)) {
        val unit = minOf(maxWidth.value / 1920f, maxHeight.value / 1080f)
        CompositionLocalProvider(LocalUnit provides unit, LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = AppFont)) {
            if (pairing) {
                PairingScreen(vm.familyCode, vm::confirmPairing)
                BackHandler(enabled = vm.familyCode.isNotEmpty()) { vm.confirmPairing() }
            } else {
                when (val s = model.screen) {
                    CarScreenState.Empty -> EmptyScreen(vm::showPairing)
                    is CarScreenState.Locked -> LockedScreen(s.newCount)
                    CarScreenState.Archive -> ArchiveScreen(model.archive, now(), zone, vm::open, vm::showPairing)
                    is CarScreenState.Reading -> {
                        val l = s.letter
                        val reply = s.reply
                        val meta = if (reply != null) stringResource(R.string.replied, textGlyphs(reply))
                        else TimeLabels.meta(l.createdAt, now(), zone)
                        val position = if (s.source == LetterSource.NEW && s.total > 1) "${s.index + 1} / ${s.total}" else null
                        LetterScreen(
                            l, LetterHeader(l.senderName, meta, position), played,
                            onToArchive = vm::toArchive,
                            onPrev = if (s.olderIndex != null) vm::prev else null,
                            onNext = if (s.newerIndex != null) vm::next else null,
                            onReply = if (s.reply == null) vm::requestReply else null,
                        )
                        BackHandler { vm.toArchive() }
                    }
                    is CarScreenState.Replying -> {
                        ReplyScreen(s.reading.letter, speech, vm::cancelReply, vm::sendReply)
                        BackHandler { vm.cancelReply() }
                    }
                }
            }
            DebugLayer(
                drive = model.drive,
                manual = override != null,
                onIgnition = vm::restartIgnition,
                onDrive = vm::overrideDrive,
                onReset = vm::resetRead,
                onPairing = vm::showPairing,
            )
        }
    }
}
