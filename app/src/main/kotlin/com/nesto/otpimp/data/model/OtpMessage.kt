package com.nesto.otpimp.data.model

import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Represents a complete OTP message ready for distribution.
 */
data class OtpMessage(
    val id: String = UUID.randomUUID().toString(),
    val employeeName: String?,
    val otpCode: String?,
    val sender: String,
    val rawSms: String,
    val receivedAt: Instant = Instant.now(),
    val confidence: ParseConfidence,
    val processedAt: Instant = Instant.now()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("employee_name", employeeName ?: JSONObject.NULL)
        put("otp_code", otpCode ?: JSONObject.NULL)
        put("sender", sender)
        put("raw_sms", rawSms)
        put("received_at", receivedAt.atZone(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_INSTANT))
        put("processed_at", processedAt.atZone(ZoneOffset.UTC)
            .format(DateTimeFormatter.ISO_INSTANT))
        put("confidence", confidence.name)
    }
    
    fun toJsonString(): String = toJson().toString()
    
    fun toSseEvent(): String = "event: otp\ndata: ${toJsonString()}\n\n"
}