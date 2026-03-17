package com.nesto.otpimp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nesto.otpimp.service.OtpForegroundService
import com.nesto.otpimp.util.Logger

class BootReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "BootReceiver"
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        
        if (action != Intent.ACTION_BOOT_COMPLETED && 
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            return
        }
        
        Logger.i(TAG, "Device booted (action: $action) — starting OTP service")
        
        try {
            val serviceIntent = Intent(context, OtpForegroundService::class.java)
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start service on boot", e)
        }
    }
}