package com.nesto.otpimp.domain.usecase

import com.nesto.otpimp.data.model.OtpMessage
import com.nesto.otpimp.data.model.ParseConfidence
import com.nesto.otpimp.data.model.Result
import com.nesto.otpimp.data.repository.OtpRepository
import com.nesto.otpimp.domain.parser.SmsParser
import com.nesto.otpimp.util.Logger

/**
 * Use case for processing incoming SMS messages.
 * Parses the SMS, creates an OtpMessage, and stores/broadcasts it.
 */
class ProcessIncomingSmsUseCase(
    private val smsParser: SmsParser,
    private val otpRepository: OtpRepository
) {
    companion object {
        private const val TAG = "ProcessIncomingSmsUseCase"
    }
    
    /**
     * Process an incoming SMS message.
     * 
     * @param sender The sender's phone number or ID
     * @param body The SMS body text
     * @return Result containing the processed OtpMessage, or error
     */
    suspend operator fun invoke(sender: String, body: String): Result<OtpMessage> {
        Logger.d(TAG, "Processing SMS from $sender")
        
        return try {
            // Parse the SMS
            val parsed = smsParser.parse(body)
            
            // Create OTP message even if confidence is low (for audit)
            val otpMessage = OtpMessage(
                employeeName = parsed.employeeName,
                otpCode = parsed.otpCode,
                sender = sender,
                rawSms = body,
                confidence = parsed.confidence
            )
            
            // Log parse result
            when (parsed.confidence) {
                ParseConfidence.HIGH -> 
                    Logger.i(TAG, "High confidence OTP: ${parsed.otpCode} for ${parsed.employeeName}")
                ParseConfidence.MEDIUM -> 
                    Logger.i(TAG, "Medium confidence OTP: ${parsed.otpCode} (no employee match)")
                ParseConfidence.LOW -> 
                    Logger.w(TAG, "Low confidence OTP: ${parsed.otpCode}")
                ParseConfidence.NONE -> 
                    Logger.w(TAG, "No OTP found in SMS from $sender")
            }
            
            // Save and broadcast
            otpRepository.saveAndBroadcast(otpMessage)
                .onError { e, msg ->
                    Logger.e(TAG, "Failed to save OTP message: $msg", e)
                }
            
            Result.Success(otpMessage)
            
        } catch (e: Exception) {
            Logger.e(TAG, "Error processing SMS", e)
            Result.Error(e, "Failed to process SMS: ${e.message}")
        }
    }
}