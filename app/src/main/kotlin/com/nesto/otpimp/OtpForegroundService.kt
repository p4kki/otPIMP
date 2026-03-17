package com.nesto.otpimp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class OtpForegroundService : Service() {

    companion object {
        var isRunning = false
        const val CHANNEL_ID = "otp_server_channel"
        const val NOTIFICATION_ID = 1
        private const val TAG = "OtpForegroundService"
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        try {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, createNotification())
            OtpServer.start(this)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start: ${e.message}", e)
            isRunning = false
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        OtpServer.stop()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "OTP Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps OTP server running"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OTP Forwarder")
            .setContentText("Running on port 8080")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
