package com.nesto.otpimp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nesto.otpimp.databinding.ActivityMainBinding
import com.nesto.otpimp.di.ServiceLocator
import com.nesto.otpimp.service.OtpForegroundService
import com.nesto.otpimp.util.Logger
import com.nesto.otpimp.util.NetworkUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private lateinit var binding: ActivityMainBinding
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        handlePermissionResults(results)
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupViews()
        observeState()
    }
    
    override fun onResume() {
        super.onResume()
        checkAndRequestPermissions()
        updateUi()
    }
    
    private fun setupViews() {
        binding.btnToggle.setOnClickListener {
            toggleService()
        }
        
        binding.btnViewLogs.setOnClickListener {
            showLogs()
        }
    }
    
    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    updateUi()
                    delay(2000) // Refresh every 2 seconds
                }
            }
        }
    }
    
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missing.isNotEmpty()) {
            Logger.d(TAG, "Requesting permissions: $missing")
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            ensureServiceRunning()
        }
    }
    
    private fun handlePermissionResults(results: Map<String, Boolean>) {
        val smsGranted = results[Manifest.permission.RECEIVE_SMS] == true
        
        Logger.i(TAG, "Permission results: SMS=$smsGranted")
        
        if (smsGranted) {
            ensureServiceRunning()
        }
        
        updateUi()
    }
    
    private fun toggleService() {
        if (OtpForegroundService.isRunning) {
            stopService(Intent(this, OtpForegroundService::class.java))
        } else {
            ensureServiceRunning()
        }
        
        // Delay UI update to let service state change
        binding.btnToggle.postDelayed({ updateUi() }, 500)
    }
    
    private fun ensureServiceRunning() {
        if (!OtpForegroundService.isRunning) {
            Logger.i(TAG, "Starting OTP service")
            val intent = Intent(this, OtpForegroundService::class.java)
            startForegroundService(intent)
        }
    }
    
    private fun updateUi() {
        val isRunning = OtpForegroundService.isRunning
        val networkInfo = NetworkUtils.getNetworkInfo(this)
        val smsGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        
        // Status
        binding.tvStatus.text = if (isRunning) "● Server running" else "○ Server stopped"
        binding.tvStatus.setTextColor(
            getColor(if (isRunning) android.R.color.holo_green_dark else android.R.color.holo_red_dark)
        )
        
        // IP Address
        binding.tvIp.text = networkInfo.serverUrl ?: "Not connected to Wi-Fi"
        binding.tvIp.setTextColor(
            getColor(if (networkInfo.serverUrl != null) android.R.color.white else android.R.color.holo_orange_dark)
        )
        
        // Permissions
        binding.tvPermissions.text = if (smsGranted) "✓ SMS permission granted" else "✗ SMS permission required"
        binding.tvPermissions.setTextColor(
            getColor(if (smsGranted) android.R.color.holo_green_dark else android.R.color.holo_red_dark)
        )
        
        // Toggle button
        binding.btnToggle.text = if (isRunning) "Stop Server" else "Start Server"
        binding.btnToggle.isEnabled = smsGranted
        
        // Stats
        val serviceState = ServiceLocator.getServiceState()
        if (serviceState != null && isRunning) {
            binding.tvStats.visibility = View.VISIBLE
            binding.tvStats.text = "Messages: ${serviceState.messagesReceived.get()} received, ${serviceState.messagesBroadcast.get()} broadcast"
        } else {
            binding.tvStats.visibility = View.GONE
        }
    }
    
    private fun showLogs() {
        val logs = Logger.getLogsFormatted(100)
        
        // Simple dialog or navigate to log screen
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Recent Logs")
            .setMessage(logs.ifEmpty { "No logs yet" })
            .setPositiveButton("OK", null)
            .setNeutralButton("Clear") { _, _ ->
                Logger.clear()
            }
            .show()
    }
}