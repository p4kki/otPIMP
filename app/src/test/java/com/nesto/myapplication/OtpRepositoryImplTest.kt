package com.nesto.otpimp.data.repository

import com.google.common.truth.Truth.assertThat
import com.nesto.otpimp.data.local.OtpDao
import com.nesto.otpimp.data.local.OtpEntity
import com.nesto.otpimp.data.model.OtpMessage
import com.nesto.otpimp.data.model.ParseConfidence
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OtpRepositoryImplTest {
    
    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mockDao: OtpDao
    private lateinit var repository: OtpRepositoryImpl
    
    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mockDao = mockk(relaxed = true)
        repository = OtpRepositoryImpl(mockDao, testDispatcher)
    }
    
    @After
    fun teardown() {
        Dispatchers.resetMain()
    }
    
    @Test
    fun `saveAndBroadcast inserts entity to database`() = runTest {
        val message = OtpMessage(
            employeeName = "Test",
            otpCode = "123456",
            sender = "sender",
            rawSms = "raw",
            confidence = ParseConfidence.HIGH
        )
        
        repository.saveAndBroadcast(message)
        testDispatcher.scheduler.advanceUntilIdle()
        
        coVerify { mockDao.insert(any()) }
    }
    
    @Test
    fun `saveAndBroadcast emits to otpStream`() = runTest {
        val message = OtpMessage(
            employeeName = "Test",
            otpCode = "123456",
            sender = "sender",
            rawSms = "raw",
            confidence = ParseConfidence.HIGH
        )
        
        repository.saveAndBroadcast(message)
        testDispatcher.scheduler.advanceUntilIdle()
        
        val emitted = repository.otpStream.first()
        assertThat(emitted.id).isEqualTo(message.id)
    }
    
    @Test
    fun `getRecent returns mapped messages`() = runTest {
        val entities = listOf(
            OtpEntity(
                id = "1",
                employeeName = "Test",
                otpCode = "123",
                sender = "sender",
                rawSms = "raw",
                receivedAt = System.currentTimeMillis(),
                processedAt = System.currentTimeMillis(),
                confidence = "HIGH"
            )
        )
        coEvery { mockDao.getRecent(any()) } returns entities
        
        val result = repository.getRecent(10)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()?.size).isEqualTo(1)
        assertThat(result.getOrNull()?.first()?.employeeName).isEqualTo("Test")
    }
    
    @Test
    fun `cleanupOldMessages calls dao delete`() = runTest {
        coEvery { mockDao.deleteOlderThan(any()) } returns 5
        
        val result = repository.cleanupOldMessages(7)
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrNull()).isEqualTo(5)
    }
}