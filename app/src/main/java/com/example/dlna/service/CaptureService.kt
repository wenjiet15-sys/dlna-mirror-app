package com.example.dlna.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.dlna.encoder.VideoEncoder
import com.example.dlna.server.StreamServer
import java.io.PipedInputStream
import java.io.PipedOutputStream

class CaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    
    // 编码器与推流服务
    private val videoEncoder = VideoEncoder()
    private var streamServer: StreamServer? = null
    
    // 内存管道，编码后的数据直通 HttpServer
    private val outputStream = PipedOutputStream()
    private val inputStream = PipedInputStream(outputStream)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
        
        // 启动本地 Http Server (端口 8080)
        streamServer = StreamServer(8080).apply {
            streamProvider = { inputStream }
            start(5000, false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", -1) ?: -1
        val resultData = intent?.getParcelableExtra<Intent>("RESULT_DATA")
        
        if (resultCode != -1 && resultData != null) {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
            
            // 组装连线: 编码器出的 Nal 数据通过 Muxer(此处用伪代码简写) 写入管道
            videoEncoder.onDataAvailable = { h264Data ->
                // [工业实现重点]: 这里需要调用 TsMuxer 把 H264 打成 TS 后再 write
                // outputStream.write(tsData) 
            }
            
            startCapture()
        }
        return START_NOT_STICKY
    }

    private fun startCapture() {
        val dm = resources.displayMetrics
        val width = dm.widthPixels
        val height = dm.heightPixels
        val dpi = dm.densityDpi

        // 配置编码器 1080P, 8Mbps, 30fps
        videoEncoder.prepare(width, height, 8000000, 30) 
        videoEncoder.start()

        // 开始捕获屏幕
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            videoEncoder.inputSurface, null, null
        )
    }

    override fun onDestroy() {
        virtualDisplay?.release()
        mediaProjection?.stop()
        videoEncoder.stop()
        streamServer?.stop()
        outputStream.close()
        inputStream.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // 录屏需要前台服务
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("DLNA_CHANNEL", "Screen Capture", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, "DLNA_CHANNEL")
            .setContentTitle("DLNA 镜像中")
            .setContentText("正在录制屏幕并投射...")
            .build()
    }
}
