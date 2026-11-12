package com.sidongletter.car.vehicle

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.sidongletter.screen.DriveState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 주차인지 아닌지. 주차가 아니면 그림을 보여주지 않는다. */
interface DriveStateSource {
    val state: StateFlow<DriveState>
    fun close() {}
}

class FakeDriveSource(initial: DriveState = DriveState.PARKED) : DriveStateSource {
    override val state = MutableStateFlow(initial)
    fun set(s: DriveState) { state.value = s }
}

/**
 * AAOS의 UX 제한으로 판단한다. 시스템이 "주의 분산 최소화"를 요구하면 주행 중으로 본다.
 * 기어 P가 아닌 정차(IDLING)도 차 설정에 따라 제한이 걸리면 DRIVING으로 들어온다.
 */
class UxRestrictionsDriveSource(context: Context) : DriveStateSource {
    override val state = MutableStateFlow(DriveState.PARKED)
    private val car: Car = Car.createCar(context)
    private val manager = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager

    init {
        manager.currentCarUxRestrictions?.let(::apply)
        manager.registerListener { apply(it) }
    }

    private fun apply(r: CarUxRestrictions) {
        state.value = if (r.isRequiresDistractionOptimization) DriveState.DRIVING else DriveState.PARKED
    }

    override fun close() {
        manager.unregisterListener()
        car.disconnect()
    }
}

/** 차량이면 UX 제한, 아니면(폰 에뮬레이터 등) 늘 주차인 가짜. */
fun createDriveSource(context: Context): DriveStateSource {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) return FakeDriveSource()
    return try {
        UxRestrictionsDriveSource(context)
    } catch (e: Throwable) {
        Log.w("Postbox", "차량 주행 신호를 못 읽어 수동 모드로 둔다", e)
        FakeDriveSource()
    }
}
