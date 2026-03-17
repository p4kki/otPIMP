package com.nesto.otpimp.data.model

/**
 * Represents a parsed SMS message containing OTP information.
 */
data class ParsedSms(
    val employeeName: String?,
    val otpCode: String?,
    val confidence: ParseConfidence,
    val rawBody: String,
    val parseErrors: List<String> = emptyList()
) {
    val isValid: Boolean
        get() = otpCode != null && confidence != ParseConfidence.NONE
    
    val hasEmployeeMatch: Boolean
        get() = employeeName != null
}

enum class ParseConfidence {
    HIGH,    // Both employee name and OTP found
    MEDIUM,  // Only OTP found (common pattern)
    LOW,     // OTP found but unusual format
    NONE     // No OTP detected
}