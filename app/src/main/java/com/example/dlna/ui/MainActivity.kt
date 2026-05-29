package com.example.dlna.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Build
import android.util.Log
import android.widget.Button
import android.widget.TextView
import org.fourthline.cling.model.meta.Device
import com.example.dlna.dlna.DLNAController
import com.example.dlna.service.CaptureService

class MainActivity : Activity() {
    private val SCREEN_RECORD_REQUEST_CODE = 1001
    private val dlnaController = DLNAController()
    
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(resources.getIdentifier("activity_main", "layout", packageName))
        
        tvStatus = findViewById(resources.getIdentifier("tvStatus", "id", packageName))
        val btnCapture = findViewById<Button>(resources.getIdentifier("btnStartCapture", "id", packageName))
        val btnSearch = findViewById<Button>(resources.getIdentifier("btnSearchDlna", "id", packageName))

        btnCapture.setOnClickListener {
            // 请求系统录屏权限
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_RECORD_REQUEST_CODE)
        }
        
        btnSearch.setOnClickListener {
            tvStatus.text = "Searching TV..."
            dlnaController.searchDevices()
            // 简单演示: 假装找到设备后执行投屏
            // 实际中需要利用 Cling 的 RegistryListener 监听设备发现并显示列表给用户点选
            // dlnaController.cast(foundDevice, "http://${getLocalIp()}:8080/live.ts")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_RECORD_REQUEST_CODE && resultCode == RESULT_OK) {
            tvStatus.text = "Recording & Server running on port 8080"
            // 启动底层捕获和推流服务
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
    
    private fun getLocalIp(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wifiManager.connectionInfo.ipAddress
        return String.format("%d.%d.%d.%d",
            ipAddress and 0xff,
            ipAddress shr 8 and 0xff,
            ipAddress shr 16 and 0xff,
            ipAddress shr 24 and 0xff)
    }
}
