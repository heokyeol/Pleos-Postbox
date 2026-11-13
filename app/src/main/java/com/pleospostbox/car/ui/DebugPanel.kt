package com.pleospostbox.car.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import com.pleospostbox.car.R
import com.pleospostbox.screen.DriveState

/** 시연 영상용 패널. 오른쪽 위 모서리를 길게 누르면 열린다. */
@Composable
fun DebugLayer(
    drive: DriveState,
    manual: Boolean,
    onIgnition: () -> Unit,
    onDrive: (DriveState?) -> Unit,
    onReset: () -> Unit,
    onPairing: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.TopEnd).size(140.u)
                .pointerInput(Unit) { detectTapGestures(onLongPress = { open = true }) }
        )
        if (open) {
            Column(
                Modifier.align(Alignment.TopEnd).padding(40.u).width(420.u)
                    .clip(RoundedCornerShape(28.u)).background(Ink.Surface).padding(24.u),
                verticalArrangement = Arrangement.spacedBy(12.u),
            ) {
                Text(stringResource(R.string.debug_title), color = Ink.Dim, fontSize = 32.su)
                Text(
                    stringResource(if (manual) R.string.debug_source_manual else R.string.debug_source_car),
                    color = Ink.Meta, fontSize = 32.su,
                )
                val m = Modifier.fillMaxWidth()
                PillButton(stringResource(R.string.debug_ignition), onIgnition, m, look = Look.SolidPrimary)
                if (drive == DriveState.PARKED) {
                    PillButton(stringResource(R.string.debug_drive), { onDrive(DriveState.DRIVING) }, m, look = Look.Solid)
                } else {
                    PillButton(stringResource(R.string.debug_park), { onDrive(DriveState.PARKED) }, m, look = Look.Solid)
                }
                if (manual) PillButton(stringResource(R.string.debug_use_car), { onDrive(null) }, m, look = Look.Solid)
                PillButton(stringResource(R.string.debug_reset), onReset, m, look = Look.Solid)
                PillButton(stringResource(R.string.debug_pairing), { onPairing(); open = false }, m, look = Look.Solid)
                PillButton(stringResource(R.string.debug_close), { open = false }, m, look = Look.Solid)
            }
        }
    }
}
