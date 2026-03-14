package com.nesto.otpimp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "BootReceiver"

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        Log.d(TAG, "Device booted — starting OTP service")

        val serviceIntent = Intent(context, OtpForegroundService::class.java)
        context.startForegroundService(serviceIntent)
    }
}
