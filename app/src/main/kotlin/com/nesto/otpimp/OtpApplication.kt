package com.nesto.otpimp

import android.app.Application
import com.nesto.otpimp.di.ServiceLocator
import com.nesto.otpimp.util.Logger

class OtpApplication : Application() {
    
    companion object {
        private const val TAG = "OtpApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Logger.i(TAG, "Application created")
        
        // Initialize service locator eagerly
        ServiceLocator.getInstance(this)
        
        // Set up global exception handler
        setupUncaughtExceptionHandler()
    }
    
    private fun setupUncaughtExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Logger.e(TAG, "Uncaught exception in thread ${thread.name}", throwable)
            
            // Call default handler to crash the app properly
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}