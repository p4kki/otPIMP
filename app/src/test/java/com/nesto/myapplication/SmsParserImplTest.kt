package com.nesto.otpimp.domain.parser

import com.google.common.truth.Truth.assertThat
import com.nesto.otpimp.data.model.ParseConfidence
import org.junit.Before
import org.junit.Test

class SmsParserImplTest {
    
    private lateinit var parser: SmsParserImpl
    
    @Before
    fun setup() {
        parser = SmsParserImpl(listOf("Ajmal", "Fatima", "Omar"))
    }
    
    @Test
    fun `parse extracts OTP with explicit OTP keyword`() {
        val result = parser.parse("Your OTP is 123456")
        
        assertThat(result.otpCode).isEqualTo("123456")
        assertThat(result.confidence).isEqualTo(ParseConfidence.MEDIUM)
    }
    
    @Test
    fun `parse extracts OTP with code keyword`() {
        val result = parser.parse("Your verification code: 7890")
        
        assertThat(result.otpCode).isEqualTo("7890")
        assertThat(result.confidence).isEqualTo(ParseConfidence.MEDIUM)
    }
    
    @Test
    fun `parse extracts employee name and OTP`() {
        val result = parser.parse("Ajmal, your OTP is 456789")
        
        assertThat(result.employeeName).isEqualTo("Ajmal")
        assertThat(result.otpCode).isEqualTo("456789")
        assertThat(result.confidence).isEqualTo(ParseConfidence.HIGH)
    }
    
    @Test
    fun `parse matches employee name case-insensitively`() {
        val result = parser.parse("FATIMA your code is 111222")
        
        assertThat(result.employeeName).isEqualTo("Fatima")
        assertThat(result.otpCode).isEqualTo("111222")
    }
    
    @Test
    fun `parse extracts standalone OTP code`() {
        val result = parser.parse("Please use 98765432 to verify")
        
        assertThat(result.otpCode).isEqualTo("98765432")
        assertThat(result.confidence).isEqualTo(ParseConfidence.LOW)
    }
    
    @Test
    fun `parse returns NONE confidence when no OTP found`() {
        val result = parser.parse("Hello, this is a regular message")
        
        assertThat(result.otpCode).isNull()
        assertThat(result.confidence).isEqualTo(ParseConfidence.NONE)
    }
    
    @Test
    fun `parse ignores numbers that are too short`() {
        val result = parser.parse("Your code is 123") // Only 3 digits
        
        assertThat(result.otpCode).isNull()
    }
    
    @Test
    fun `parse ignores numbers that are too long`() {
        val result = parser.parse("Reference: 123456789") // 9 digits
        
        assertThat(result.otpCode).isNull()
    }
    
    @Test
    fun `parse prefers explicit OTP patterns over standalone numbers`() {
        val result = parser.parse("Transaction 999999. Your OTP: 123456")
        
        assertThat(result.otpCode).isEqualTo("123456")
    }
    
    @Test
    fun `getEmployeeNames returns current list`() {
        val employees = parser.getEmployeeNames()
        
        assertThat(employees).containsExactly("Ajmal", "Fatima", "Omar")
    }
    
    @Test
    fun `setEmployeeNames updates the list`() {
        parser.setEmployeeNames(listOf("Alice", "Bob"))
        
        val employees = parser.getEmployeeNames()
        assertThat(employees).containsExactly("Alice", "Bob")
    }
    
    @Test
    fun `parse works with updated employee list`() {
        parser.setEmployeeNames(listOf("NewEmployee"))
        
        val result = parser.parse("NewEmployee, your OTP is 999888")
        
        assertThat(result.employeeName).isEqualTo("NewEmployee")
    }
}