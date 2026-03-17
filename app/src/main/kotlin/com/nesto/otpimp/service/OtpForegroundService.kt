package com.nesto.otpimp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.nesto.otpimp.MainActivity
import com.nesto.otpimp.R
import com.nesto.otpimp.di.ServiceLocator
import com.nesto.otpimp.network.OtpHttpServer
import com.nesto.otpimp.util.Constants
import com.nesto.otpimp.util.Logger
import com.nesto.otpimp.util.NetworkUtils
import kotlinx.coroutines.*

class OtpForegroundService : Service() {
    
    companion object {
        private const val TAG = "OtpForegroundService"
        private const val WAKELOCK_TAG = "OtpForwarder:ServerWakeLock"
        
        @Volatile
        var instance: OtpForegroundService? = null
            private set
        
        val isRunning: Boolean
            get() = instance?.serviceState?.isRunning == true
    }
    
    private lateinit var serviceState: ServiceState
    private var httpServer: OtpHttpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        Logger.i(TAG, "Service onCreate")
        
        serviceState = ServiceState()
        ServiceLocator.provideServiceState(serviceState)
        
        createNotificationChannel()
        acquireWakeLock()
        startHttpServer()
        
        serviceState.setRunning(true)
        
        // Start foreground with notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(Constants.NOTIFICATION_ID, createNotification())
        }
        
        // Schedule periodic cleanup
        scheduleCleanup()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.d(TAG, "Service onStartCommand")
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        Logger.i(TAG, "Service onDestroy")
        
        serviceState.setRunning(false)
        
        serviceScope.cancel()
        stopHttpServer()
        releaseWakeLock()
        
        instance = null
        
        super.onDestroy()
    }
    
    private fun startHttpServer() {
        try {
            val locator = ServiceLocator.getInstance(applicationContext)
            
            httpServer = OtpHttpServer(
                context = applicationContext,    
                port = Constants.SERVER_PORT,
                otpRepository = locator.otpRepository,
                getEmployeesUseCase = locator.getEmployeesUseCase,
                serviceState = serviceState,
                processIncomingSmsUseCase = locator.processIncomingSmsUseCase

            )
            httpServer?.start()
            
            val networkInfo = NetworkUtils.getNetworkInfo(this)
            Logger.i(TAG, "Server started at ${networkInfo.serverUrl ?: "unknown"}")
            
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to start HTTP server", e)
            stopSelf()
        }
    }
    
    private fun stopHttpServer() {
        try {
            httpServer?.stop()
            httpServer = null
            Logger.i(TAG, "HTTP server stopped")
        } catch (e: Exception) {
            Logger.e(TAG, "Error stopping HTTP server", e)
        }
    }
    
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKELOCK_TAG
        ).apply {
            acquire(24 * 60 * 60 * 1000L) // 24 hours max
        }
        Logger.d(TAG, "WakeLock acquired")
    }
    
    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Logger.d(TAG, "WakeLock released")
            }
        }
        wakeLock = null
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                "OTP Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps OTP forwarding server running"
                setShowBadge(false)
            }
            
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val networkInfo = NetworkUtils.getNetworkInfo(this)
        val contentText = networkInfo.serverUrl ?: "Wi-Fi not connected"
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("OTP Forwarder")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
    
    private fun scheduleCleanup() {
        serviceScope.launch {
            while (isActive) {
                delay(6 * 60 * 60 * 1000L) // Every 6 hours
                
                try {
                    val locator = ServiceLocator.getInstance(applicationContext)
                    locator.otpRepository.cleanupOldMessages(retentionDays = 7)
                } catch (e: Exception) {
                    Logger.e(TAG, "Cleanup failed", e)
                }
            }
        }
    }
    
    fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(Constants.NOTIFICATION_ID, createNotification())
    }
}