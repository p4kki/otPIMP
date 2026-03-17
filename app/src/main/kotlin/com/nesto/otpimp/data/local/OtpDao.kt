package com.nesto.otpimp.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OtpDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(otp: OtpEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(otps: List<OtpEntity>)
    
    @Query("SELECT * FROM otp_logs ORDER BY received_at DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<OtpEntity>
    
    @Query("SELECT * FROM otp_logs ORDER BY received_at DESC LIMIT :limit")
    fun getRecentFlow(limit: Int): Flow<List<OtpEntity>>
    
    @Query("SELECT * FROM otp_logs WHERE employee_name = :name ORDER BY received_at DESC LIMIT :limit")
    suspend fun getByEmployee(name: String, limit: Int): List<OtpEntity>
    
    @Query("SELECT * FROM otp_logs WHERE received_at >= :since ORDER BY received_at DESC")
    suspend fun getSince(since: Long): List<OtpEntity>
    
    @Query("SELECT COUNT(*) FROM otp_logs")
    suspend fun getCount(): Int
    
    @Query("DELETE FROM otp_logs WHERE received_at < :before")
    suspend fun deleteOlderThan(before: Long): Int
    
    @Query("DELETE FROM otp_logs")
    suspend fun deleteAll()
}