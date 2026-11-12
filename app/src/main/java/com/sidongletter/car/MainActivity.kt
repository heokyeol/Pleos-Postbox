package com.sidongletter.car

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sidongletter.car.data.DummyLetterRepository
import com.sidongletter.car.data.PairingStore
import com.sidongletter.car.data.SharedPrefsStore
import com.sidongletter.car.ui.PostboxApp
import com.sidongletter.car.vehicle.DriveStateSource
import com.sidongletter.car.vehicle.FakeSpeechInput
import com.sidongletter.car.vehicle.createDriveSource
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var drive: DriveStateSource

    private val vm: CarViewModel by viewModels {
        viewModelFactory {
            initializer {
                val store = SharedPrefsStore(applicationContext.getSharedPreferences("postbox", Context.MODE_PRIVATE))
                CarViewModel(
                    repo = DummyLetterRepository({ assets.open("letters.json").bufferedReader().use { it.readText() } }, store),
                    pairing = PairingStore(store),
                    now = System::currentTimeMillis,
                    elapsedSinceBoot = SystemClock::elapsedRealtime,
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        drive = createDriveSource(this)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { drive.state.collect(vm::onSystemDrive) }
        }
        val speech = FakeSpeechInput(lifecycleScope, getString(R.string.fake_speech))
        setContent { PostboxApp(vm, speech, System::currentTimeMillis) }
    }

    override fun onDestroy() {
        drive.close()
        super.onDestroy()
    }
}
