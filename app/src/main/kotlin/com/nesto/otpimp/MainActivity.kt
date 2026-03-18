package com.nesto.otpimp

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

        binding.btnCopyIp.setOnClickListener {
            copyIpToClipboard()
        }

        // Show notification permission row on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            binding.layoutNotificationPermission.visibility = View.VISIBLE
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    updateUi()
                    delay(2000)
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

        val notificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        // Status indicator and text
        binding.statusIndicator.setBackgroundResource(
            if (isRunning) R.drawable.status_indicator_active 
            else R.drawable.status_indicator_inactive
        )
        binding.tvStatus.text = if (isRunning) "Server Running" else "Server Stopped"
        binding.tvStatusSubtitle.text = if (isRunning) {
            "Listening for SMS messages"
        } else {
            "Tap button below to start"
        }

        // Toggle button
        binding.btnToggle.apply {
            text = if (isRunning) "Stop Server" else "Start Server"
            setBackgroundColor(
                getColor(if (isRunning) R.color.accent_red else R.color.accent_green)
            )
            setIconResource(if (isRunning) R.drawable.ic_stop else R.drawable.ic_power)
            isEnabled = smsGranted
            alpha = if (smsGranted) 1f else 0.5f
        }

        // IP Address
        val serverUrl = networkInfo.serverUrl
        binding.tvIp.text = serverUrl ?: "Not connected to Wi-Fi"
        binding.btnCopyIp.visibility = if (serverUrl != null) View.VISIBLE else View.GONE

        // Stats
        val serviceState = ServiceLocator.getServiceState()
        if (serviceState != null && isRunning) {
            binding.cardStats.visibility = View.VISIBLE
            binding.tvReceived.text = serviceState.messagesReceived.get().toString()
            binding.tvBroadcast.text = serviceState.messagesBroadcast.get().toString()
        } else {
            binding.cardStats.visibility = View.GONE
        }

        // Permissions
        updatePermissionUi(
            binding.ivSmsPermission,
            binding.tvSmsPermission,
            smsGranted,
            "SMS Permission"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            updatePermissionUi(
                binding.ivNotificationPermission,
                binding.tvNotificationPermission,
                notificationGranted,
                "Notification Permission"
            )
        }
    }

    private fun updatePermissionUi(
        icon: android.widget.ImageView,
        text: android.widget.TextView,
        granted: Boolean,
        label: String
    ) {
        icon.setImageResource(
            if (granted) R.drawable.ic_check_circle else R.drawable.ic_cancel
        )
        icon.imageTintList = ContextCompat.getColorStateList(
            this,
            if (granted) R.color.accent_green else R.color.accent_red
        )
        text.text = label
        text.setTextColor(getColor(R.color.text_primary))
    }

    private fun copyIpToClipboard() {
        val networkInfo = NetworkUtils.getNetworkInfo(this)
        networkInfo.serverUrl?.let { url ->
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Server URL", url)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showLogs() {
        val logs = Logger.getLogsFormatted(100)

        MaterialAlertDialogBuilder(this, R.style.LogsDialogTheme)
            .setTitle("Recent Logs")
            .setMessage(logs.ifEmpty { "No logs yet" })
            .setPositiveButton("Close", null)
            .setNeutralButton("Clear") { _, _ ->
                Logger.clear()
                Toast.makeText(this, "Logs cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Copy") { _, _ ->
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Logs", logs)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Logs copied", Toast.LENGTH_SHORT).show()
            }
            .show()
    }
}