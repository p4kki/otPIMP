package com.nesto.otpimp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvIp: TextView
    private lateinit var tvPermissions: TextView
    private lateinit var btnToggle: Button

    // Runtime permission launcher — fires the system dialog, handles result
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        updatePermissionStatus(results)
        // If SMS permission granted, start the service immediately
        if (results[Manifest.permission.RECEIVE_SMS] == true) {
            startOtpService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus      = findViewById(R.id.tvStatus)
        tvIp          = findViewById(R.id.tvIp)
        tvPermissions = findViewById(R.id.tvPermissions)
        btnToggle     = findViewById(R.id.btnToggle)

        tvIp.text = "http://${getLocalIp()}:8080"

        btnToggle.setOnClickListener {
            if (OtpForegroundService.isRunning) {
                stopService(Intent(this, OtpForegroundService::class.java))
            } else {
                startOtpService()
            }
            updateUi()
        }

        // Check permissions on every resume so status stays accurate
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestPermissions()
        updateUi()
    }

    private fun checkAndRequestPermissions() {
        val needed = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS
        )
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            // All permissions already granted — make sure service is running
            startOtpService()
            updateUi()
        }
    }

    private fun updatePermissionStatus(results: Map<String, Boolean>) {
        val sms = results[Manifest.permission.RECEIVE_SMS] == true
        tvPermissions.text = if (sms) "✓ SMS permission granted" else "✗ SMS permission denied — OTPs will not be received"
        tvPermissions.setTextColor(
            if (sms) getColor(android.R.color.holo_green_dark)
            else     getColor(android.R.color.holo_red_dark)
        )
    }

    private fun updateUi() {
        val running = OtpForegroundService.isRunning
        tvStatus.text    = if (running) "● Server running on port 8080" else "○ Server stopped"
        tvStatus.setTextColor(
            if (running) getColor(android.R.color.holo_green_dark)
            else         getColor(android.R.color.holo_red_dark)
        )
        btnToggle.text = if (running) "Stop" else "Start"

        // Keep permissions display current
        val smsGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        tvPermissions.text = if (smsGranted) "✓ SMS permission granted" else "✗ SMS permission denied"
        tvPermissions.setTextColor(
            if (smsGranted) getColor(android.R.color.holo_green_dark)
            else            getColor(android.R.color.holo_red_dark)
        )
    }

    private fun startOtpService() {
        val intent = Intent(this, OtpForegroundService::class.java)
        startForegroundService(intent)
    }

    private fun getLocalIp(): String {
        return try {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val ip   = wifi.connectionInfo.ipAddress
            // ipAddress is an int in little-endian — format it as dotted decimal
            String.format(
                "%d.%d.%d.%d",
                ip and 0xff,
                ip shr 8  and 0xff,
                ip shr 16 and 0xff,
                ip shr 24 and 0xff
            )
        } catch (e: Exception) {
            "unknown — check Wi-Fi"
        }
    }
}