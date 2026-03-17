package com.nesto.otpimp.domain.parser

import com.nesto.otpimp.data.model.ParsedSms

interface SmsParser {
    
    /**
     * Parse an SMS body to extract OTP code and employee name.
     */
    fun parse(body: String): ParsedSms
    
    /**
     * Get the list of known employee names for matching.
     */
    fun getEmployeeNames(): List<String>
    
    /**
     * Add or update employee names for matching.
     */
    fun setEmployeeNames(names: List<String>)
}