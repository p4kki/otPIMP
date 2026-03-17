package com.nesto.otpimp.data.repository

import com.nesto.otpimp.data.local.OtpDao
import com.nesto.otpimp.data.local.OtpEntity
import com.nesto.otpimp.data.model.OtpMessage
import com.nesto.otpimp.data.model.Result
import com.nesto.otpimp.util.Logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class OtpRepositoryImpl(
    private val otpDao: OtpDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : OtpRepository {
    
    companion object {
        private const val TAG = "OtpRepository"
    }
    
    private val _otpStream = MutableSharedFlow<OtpMessage>(
        replay = 1,
        extraBufferCapacity = 64
    )
    override val otpStream: SharedFlow<OtpMessage> = _otpStream.asSharedFlow()
    
    override suspend fun saveAndBroadcast(message: OtpMessage): Result<Unit> {
        return withContext(ioDispatcher) {
            Result.runCatching {
                Logger.d(TAG, "Saving OTP message: ${message.id}")
                
                // Save to database
                otpDao.insert(OtpEntity.fromOtpMessage(message))
                
                // Broadcast to subscribers
                _otpStream.emit(message)
                
                Logger.i(TAG, "OTP message saved and broadcast: ${message.id}")
            }
        }
    }
    
    override suspend fun getRecent(limit: Int): Result<List<OtpMessage>> {
        return withContext(ioDispatcher) {
            Result.runCatching {
                otpDao.getRecent(limit).map { it.toOtpMessage() }
            }
        }
    }
    
    override fun getRecentFlow(limit: Int): Flow<List<OtpMessage>> {
        return otpDao.getRecentFlow(limit).map { entities ->
            entities.map { it.toOtpMessage() }
        }
    }
    
    override suspend fun getByEmployee(name: String, limit: Int): Result<List<OtpMessage>> {
        return withContext(ioDispatcher) {
            Result.runCatching {
                otpDao.getByEmployee(name, limit).map { it.toOtpMessage() }
            }
        }
    }
    
    override suspend fun getSince(epochMillis: Long): Result<List<OtpMessage>> {
        return withContext(ioDispatcher) {
            Result.runCatching {
                otpDao.getSince(epochMillis).map { it.toOtpMessage() }
            }
        }
    }
    
    override suspend fun getCount(): Result<Int> {
        return withContext(ioDispatcher) {
            Result.runCatching {
                otpDao.getCount()
            }
        }
    }
    
    override suspend fun cleanupOldMessages(retentionDays: Int): Result<Int> {
        return withContext(ioDispatcher) {
            Result.runCatching {
                val cutoff = System.currentTimeMillis() - 
                    TimeUnit.DAYS.toMillis(retentionDays.toLong())
                val deleted = otpDao.deleteOlderThan(cutoff)
                Logger.i(TAG, "Cleaned up $deleted old messages (retention: $retentionDays days)")
                deleted
            }
        }
    }
}