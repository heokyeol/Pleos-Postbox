package com.sidongletter.car.ui

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
import com.sidongletter.car.CarViewModel
import com.sidongletter.car.R
import com.sidongletter.car.vehicle.SpeechInput
import com.sidongletter.screen.CarScreenState
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
                    is CarScreenState.Revealing -> {
                        LetterScreen(
                            s.letter,
                            LetterHeader(s.letter.senderName, TimeLabels.meta(s.letter.createdAt, now(), zone),
                                if (s.total > 1) "${s.index} / ${s.total}" else null),
                            played, vm::back, vm::requestReply,
                        )
                        BackHandler { vm.back() }
                    }
                    is CarScreenState.Viewing -> {
                        LetterScreen(
                            s.letter,
                            LetterHeader(s.letter.senderName, TimeLabels.meta(s.letter.createdAt, now(), zone), null),
                            played, vm::back, vm::requestReply,
                        )
                        BackHandler { vm.back() }
                    }
                    is CarScreenState.Replied -> {
                        played += s.letter.id
                        LetterScreen(
                            s.letter,
                            LetterHeader(s.letter.senderName, stringResource(R.string.replied, textGlyphs(s.reply)), null),
                            played, vm::back, null,
                        )
                        BackHandler { vm.back() }
                    }
                    is CarScreenState.Replying -> {
                        ReplyScreen(s.letter, speech, vm::cancelReply, vm::sendReply)
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
