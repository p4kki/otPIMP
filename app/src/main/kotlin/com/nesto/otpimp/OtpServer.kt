package com.nesto.otpimp

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentLinkedQueue
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "OtpServer"

object OtpServer {

    private val subscribers = ConcurrentLinkedQueue< suspend (String) -> Unit >()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val employees = listOf("Ajmal", "Fatima", "Omar", "Sara", "Hassan",
                                    "Khalid", "Mariam", "Yousuf", "Layla", "Ali")

    fun start() {
        Log.d(TAG, "OtpServer initialized (in-process)")
    }

    fun stop() {
        scope.cancel()
        Log.d(TAG, "OtpServer stopped")
    }

    fun onSmsReceived(sender: String, rawBody: String) {
        val parsed = parseSms(rawBody)
        val now = java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        Log.d(TAG, "Parsed: employee=${parsed.first}, otp=${parsed.second}")

        // Broadcast to all subscribers
        val payload = JSONObject().apply {
            put("employee_name", parsed.first)
            put("otp_code", parsed.second)
            put("received_at", now)
            put("sender", sender)
            put("raw_sms", rawBody)
        }

        subscribers.forEach { callback ->
            scope.launch {
                try {
                    callback(payload.toString())
                } catch (e: Exception) {
                    Log.e(TAG, "Subscriber error: ${e.message}")
                }
            }
        }
    }

    fun subscribe(callback: suspend (String) -> Unit) {
        subscribers.add(callback)
    }

    fun unsubscribe(callback: suspend (String) -> Unit) {
        subscribers.remove(callback)
    }

    fun getEmployees() = employees

    private fun parseSms(body: String): Pair<String?, String?> {
        val otpRegex = Regex("(?<!\\d)(\\d{4,8})(?!\\d)")
        val otpMatch = otpRegex.find(body)
        val otpCode = otpMatch?.value

        var employeeName: String? = null
        val bodyLower = body.lowercase()
        for (emp in employees) {
            if (emp.lowercase() in bodyLower) {
                employeeName = emp
                break
            }
        }

        return Pair(employeeName, otpCode)
    }
}
