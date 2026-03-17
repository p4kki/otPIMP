package com.nesto.otpimp.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.nesto.otpimp.di.ServiceLocator
import com.nesto.otpimp.service.OtpForegroundService
import com.nesto.otpimp.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "SmsReceiver"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }
        
        val messages = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent)
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to parse SMS intent", e)
            return
        }
        
        if (messages.isNullOrEmpty()) {
            Logger.w(TAG, "Received empty SMS intent")
            return
        }
        
        val sender = messages.firstOrNull()?.displayOriginatingAddress ?: "unknown"
        val body = messages.joinToString("") { it.messageBody ?: "" }
        
        if (body.isBlank()) {
            Logger.w(TAG, "Received SMS with empty body from $sender")
            return
        }
        
        Logger.i(TAG, "SMS received from $sender (${body.length} chars)")
        
        // Update service state
        OtpForegroundService.instance?.let { service ->
            ServiceLocator.getServiceState()?.recordMessageReceived()
        }
        
        // Process asynchronously
        val pendingResult = goAsync()
        
        scope.launch {
            try {
                val locator = ServiceLocator.getInstance(context)
                val result = locator.processIncomingSmsUseCase(sender, body)
                
                result.onSuccess { message ->
                    Logger.i(TAG, "SMS processed: OTP=${message.otpCode}, Employee=${message.employeeName}")
                    ServiceLocator.getServiceState()?.recordMessageBroadcast()
                }.onError { e, msg ->
                    Logger.e(TAG, "Failed to process SMS: $msg", e)
                }
                
            } catch (e: Exception) {
                Logger.e(TAG, "Error in SMS processing", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}