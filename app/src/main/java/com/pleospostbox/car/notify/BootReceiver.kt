package com.pleospostbox.car.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 차량 전원이 켜져 시스템이 부팅되면(= 시동) 새 편지 알림을 띄운다. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) IgnitionNotice.postForCurrentIgnition(context)
    }
}
