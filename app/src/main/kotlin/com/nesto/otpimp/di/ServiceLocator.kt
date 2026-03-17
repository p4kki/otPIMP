package com.nesto.otpimp.di

import android.content.Context
import com.nesto.otpimp.data.local.OtpDatabase
import com.nesto.otpimp.data.repository.OtpRepository
import com.nesto.otpimp.data.repository.OtpRepositoryImpl
import com.nesto.otpimp.domain.parser.SmsParser
import com.nesto.otpimp.domain.parser.SmsParserImpl
import com.nesto.otpimp.domain.usecase.GetEmployeesUseCase
import com.nesto.otpimp.domain.usecase.ProcessIncomingSmsUseCase
import com.nesto.otpimp.service.ServiceState

/**
 * Simple service locator for dependency injection.
 * In a production app, consider using Hilt or Koin.
 */
object ServiceLocator {
    
    @Volatile
    private var instance: ServiceLocatorInstance? = null
    
    @Volatile
    private var serviceState: ServiceState? = null
    
    fun getInstance(context: Context): ServiceLocatorInstance {
        return instance ?: synchronized(this) {
            instance ?: ServiceLocatorInstance(context.applicationContext).also {
                instance = it
            }
        }
    }
    
    fun provideServiceState(state: ServiceState) {
        serviceState = state
    }
    
    fun getServiceState(): ServiceState? = serviceState
    
    fun reset() {
        instance = null
        serviceState = null
    }
    
    class ServiceLocatorInstance(private val context: Context) {
        
        // Database
        private val database: OtpDatabase by lazy {
            OtpDatabase.getInstance(context)
        }
        
        // Parser
        val smsParser: SmsParser by lazy {
            SmsParserImpl()
        }
        
        // Repository
        val otpRepository: OtpRepository by lazy {
            OtpRepositoryImpl(database.otpDao())
        }
        
        // Use Cases
        val processIncomingSmsUseCase: ProcessIncomingSmsUseCase by lazy {
            ProcessIncomingSmsUseCase(smsParser, otpRepository)
        }
        
        val getEmployeesUseCase: GetEmployeesUseCase by lazy {
            GetEmployeesUseCase(smsParser)
        }
    }
}