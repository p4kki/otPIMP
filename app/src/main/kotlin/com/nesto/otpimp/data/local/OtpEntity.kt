package com.nesto.otpimp.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.nesto.otpimp.data.model.OtpMessage
import com.nesto.otpimp.data.model.ParseConfidence

@Entity(
    tableName = "otp_logs",
    indices = [
        Index(value = ["received_at"]),
        Index(value = ["employee_name"]),
        Index(value = ["sender"])
    ]
)
data class OtpEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    
    @ColumnInfo(name = "employee_name")
    val employeeName: String?,
    
    @ColumnInfo(name = "otp_code")
    val otpCode: String?,
    
    @ColumnInfo(name = "sender")
    val sender: String,
    
    @ColumnInfo(name = "raw_sms")
    val rawSms: String,
    
    @ColumnInfo(name = "received_at")
    val receivedAt: Long,
    
    @ColumnInfo(name = "processed_at")
    val processedAt: Long,
    
    @ColumnInfo(name = "confidence")
    val confidence: String
) {
    fun toOtpMessage(): OtpMessage = OtpMessage(
        id = id,
        employeeName = employeeName,
        otpCode = otpCode,
        sender = sender,
        rawSms = rawSms,
        receivedAt = java.time.Instant.ofEpochMilli(receivedAt),
        processedAt = java.time.Instant.ofEpochMilli(processedAt),
        confidence = ParseConfidence.valueOf(confidence)
    )
    
    companion object {
        fun fromOtpMessage(message: OtpMessage): OtpEntity = OtpEntity(
            id = message.id,
            employeeName = message.employeeName,
            otpCode = message.otpCode,
            sender = message.sender,
            rawSms = message.rawSms,
            receivedAt = message.receivedAt.toEpochMilli(),
            processedAt = message.processedAt.toEpochMilli(),
            confidence = message.confidence.name
        )
    }
}