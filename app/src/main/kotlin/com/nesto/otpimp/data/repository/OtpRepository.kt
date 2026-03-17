package com.nesto.otpimp.data.repository

import com.nesto.otpimp.data.model.OtpMessage
import com.nesto.otpimp.data.model.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

interface OtpRepository {
    
    /**
     * Flow of incoming OTP messages for real-time subscribers.
     */
    val otpStream: SharedFlow<OtpMessage>
    
    /**
     * Save an OTP message to the database and emit to subscribers.
     */
    suspend fun saveAndBroadcast(message: OtpMessage): Result<Unit>
    
    /**
     * Get recent OTP messages from the database.
     */
    suspend fun getRecent(limit: Int = 50): Result<List<OtpMessage>>
    
    /**
     * Get OTP messages as a Flow for reactive UI.
     */
    fun getRecentFlow(limit: Int = 50): Flow<List<OtpMessage>>
    
    /**
     * Get messages for a specific employee.
     */
    suspend fun getByEmployee(name: String, limit: Int = 20): Result<List<OtpMessage>>
    
    /**
     * Get messages received since a timestamp.
     */
    suspend fun getSince(epochMillis: Long): Result<List<OtpMessage>>
    
    /**
     * Get total message count.
     */
    suspend fun getCount(): Result<Int>
    
    /**
     * Clean up old messages (retention policy).
     */
    suspend fun cleanupOldMessages(retentionDays: Int = 7): Result<Int>
}