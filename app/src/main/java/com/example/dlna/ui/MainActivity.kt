package com.example.dlna.ui

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Build
import android.os.IBinder
import android.widget.Button
import android.widget.TextView
import com.example.dlna.R
import com.example.dlna.dlna.DLNAController
import com.example.dlna.service.CaptureService
import org.fourthline.cling.android.AndroidUpnpService
import org.fourthline.cling.android.AndroidUpnpServiceImpl

class MainActivity : Activity() {
    private val SCREEN_RECORD_REQUEST_CODE = 1001
    private val AUDIO_PERMISSION_CODE = 1002

    private lateinit var tvStatus: TextView
    private var dlnaController: DLNAController? = null
    private var upnpService: AndroidUpnpService? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            upnpService = service as AndroidUpnpService
            dlnaController = DLNAController(upnpService!!.get())
        }
        override fun onServiceDisconnected(name: ComponentName) {
            upnpService = null
            dlnaController = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        val btnCapture = findViewById<Button>(R.id.btnStartCapture)
        val btnSearch = findViewById<Button>(R.id.btnSearchDlna)

        bindService(
            Intent(this, AndroidUpnpServiceImpl::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )

        checkPermissions()

        btnCapture.setOnClickListener {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_RECORD_REQUEST_CODE)
        }

        btnSearch.setOnClickListener {
            val controller = dlnaController
            if (controller == null) {
                tvStatus.text = "DLNA service not ready, please wait..."
            } else {
                tvStatus.text = "Searching TV..."
                try {
                    controller.searchDevices()
                } catch (e: Exception) {
                    tvStatus.text = "Search Error: ${e.message}"
                }
            }
        }
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            val missing = perms.filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                requestPermissions(missing.toTypedArray(), AUDIO_PERMISSION_CODE)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_RECORD_REQUEST_CODE && resultCode == RESULT_OK) {
            tvStatus.text = "Recording & Server running on port 8080"
            val intent = Intent(this, CaptureService::class.java).apply {
                putExtra("RESULT_CODE", resultCode)
                putExtra("RESULT_DATA", data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(serviceConnection)
    }
}
