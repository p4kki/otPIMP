package com.nesto.otpimp

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

private const val TAG = "SmsReceiver"

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.firstOrNull()?.displayOriginatingAddress ?: "unknown"
        val body = messages.joinToString("") { it.messageBody ?: "" }

        if (body.isBlank()) return

        Log.d(TAG, "SMS from $sender: $body")

        // Direct call — no HTTP, server runs in same process
        OtpServer.onSmsReceived(sender, body)
    }
}
