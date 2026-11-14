package com.pleospostbox.car

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pleospostbox.car.data.PairingStore
import com.pleospostbox.car.data.SharedPrefsStore
import com.pleospostbox.car.notify.IgnitionNotice
import com.pleospostbox.car.ui.PostboxApp
import com.pleospostbox.car.vehicle.DriveStateSource
import com.pleospostbox.car.vehicle.FakeSpeechInput
import com.pleospostbox.car.vehicle.createDriveSource
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var drive: DriveStateSource
    /** 시연 알림을 띄운 뒤 다시 앞으로 오면 시동을 새로 켠 것처럼 편지를 보여준다. */
    private var restartOnReturn = false

    private val vm: CarViewModel by viewModels {
        viewModelFactory {
            initializer {
                val store = SharedPrefsStore(applicationContext.getSharedPreferences("postbox", Context.MODE_PRIVATE))
                CarViewModel(
                    repo = IgnitionNotice.repository(applicationContext),
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
        setContent { PostboxApp(vm, speech, System::currentTimeMillis, ::demoIgnitionNotice) }
        askNotificationPermission()
    }

    override fun onStart() {
        super.onStart()
        // 앱을 열면 편지를 바로 보여주므로 시동 알림은 지운다
        IgnitionNotice.cancel(this)
        if (restartOnReturn) {
            restartOnReturn = false
            vm.restartIgnition()
        }
    }

    /** 시동(부팅) 알림을 띄우려면 알림 권한이 필요하다. 처음 한 번 묻는다. */
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    /** 시연용: 읽음을 지우고 앱을 뒤로 보낸 뒤, 지금 시동을 켠 것처럼 새 편지 알림을 띄운다. */
    private fun demoIgnitionNotice() {
        vm.clearReadMarks()
        restartOnReturn = true
        moveTaskToBack(true)
        Handler(Looper.getMainLooper()).postDelayed({
            IgnitionNotice.post(this, IgnitionNotice.arrivals(IgnitionNotice.repository(this), System.currentTimeMillis()))
        }, 1000)
    }

    override fun onDestroy() {
        drive.close()
        super.onDestroy()
    }
}
