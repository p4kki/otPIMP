package com.nesto.otpimp.domain.parser

import com.nesto.otpimp.data.model.ParseConfidence
import com.nesto.otpimp.data.model.ParsedSms
import com.nesto.otpimp.util.Logger
import java.util.concurrent.CopyOnWriteArrayList

class SmsParserImpl(
    initialEmployees: List<String> = DEFAULT_EMPLOYEES
) : SmsParser {
    
    companion object {
        private const val TAG = "SmsParser"
        
        // Default employee list - should be configurable in production
        val DEFAULT_EMPLOYEES = listOf(
            "Ajmal", "Fatima", "Omar", "Sara", "Hassan",
            "Khalid", "Mariam", "Yousuf", "Layla", "Ali"
        )
        
        // OTP patterns in order of specificity
        private val OTP_PATTERNS = listOf(
            // Explicit OTP mentions: "OTP: 123456", "OTP is 123456", "code: 123456"
            Regex("""(?i)(?:otp|code|pin|password)[:\s]+(\d{4,8})"""),
            // "Your code is 123456"
            Regex("""(?i)(?:your|the)\s+(?:otp|code|pin|password)\s+(?:is|:)\s*(\d{4,8})"""),
            // "123456 is your OTP"
            Regex("""(\d{4,8})\s+(?:is\s+)?(?:your|the)\s+(?:otp|code|pin)"""),
            // Standalone 4-8 digit number (least specific)
            Regex("""(?<!\d)(\d{4,8})(?!\d)""")
        )
    }
    
    private val employees = CopyOnWriteArrayList(initialEmployees)
    
    override fun parse(body: String): ParsedSms {
        Logger.v(TAG, "Parsing SMS: ${body.take(50)}...")
        
        val errors = mutableListOf<String>()
        
        // Extract OTP code
        var otpCode: String? = null
        var patternIndex = -1
        
        for ((index, pattern) in OTP_PATTERNS.withIndex()) {
            val match = pattern.find(body)
            if (match != null) {
                otpCode = match.groupValues.getOrNull(1) ?: match.value
                patternIndex = index
                Logger.d(TAG, "OTP found with pattern $index: $otpCode")
                break
            }
        }
        
        if (otpCode == null) {
            errors.add("No OTP code found in message")
        }
        
        // Extract employee name
        val employeeName = findEmployeeName(body)
        
        if (employeeName == null && employees.isNotEmpty()) {
            errors.add("No employee name matched from ${employees.size} known employees")
        }
        
        // Determine confidence
        val confidence = when {
            otpCode == null -> ParseConfidence.NONE
            employeeName != null && patternIndex < OTP_PATTERNS.size - 1 -> ParseConfidence.HIGH
            patternIndex < OTP_PATTERNS.size - 1 -> ParseConfidence.MEDIUM
            otpCode != null -> ParseConfidence.LOW
            else -> ParseConfidence.NONE
        }
        
        val result = ParsedSms(
            employeeName = employeeName,
            otpCode = otpCode,
            confidence = confidence,
            rawBody = body,
            parseErrors = errors
        )
        
        Logger.d(TAG, "Parse result: employee=$employeeName, otp=$otpCode, confidence=$confidence")
        return result
    }
    
    override fun getEmployeeNames(): List<String> = employees.toList()
    
    override fun setEmployeeNames(names: List<String>) {
        employees.clear()
        employees.addAll(names)
        Logger.i(TAG, "Updated employee list: ${names.size} names")
    }
    
    private fun findEmployeeName(body: String): String? {
        val bodyLower = body.lowercase()
        
        // Try exact word boundary match first
        for (employee in employees) {
            val pattern = Regex("""\b${Regex.escape(employee)}\b""", RegexOption.IGNORE_CASE)
            if (pattern.containsMatchIn(body)) {
                return employee
            }
        }
        
        // Fallback to contains match
        for (employee in employees) {
            if (employee.lowercase() in bodyLower) {
                return employee
            }
        }
        
        return null
    }
}