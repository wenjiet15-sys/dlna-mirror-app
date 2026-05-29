package com.example.dlna.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.dlna.encoder.AudioEncoder
import com.example.dlna.encoder.TSMuxerProxy
import com.example.dlna.encoder.VideoEncoder
import com.example.dlna.server.StreamServer
import java.io.PipedInputStream
import java.io.PipedOutputStream

class CaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var audioRecord: AudioRecord? = null
    
    // 编码器与推流服务
    private val videoEncoder = VideoEncoder()
    private val audioEncoder = AudioEncoder()
    private var streamServer: StreamServer? = null
    private var tsMuxer: TSMuxerProxy? = null
    
    // 内存管道，编码后的数据直通 HttpServer
    private val outputStream = PipedOutputStream()
    private val inputStream = PipedInputStream(outputStream)
    private var isRecording = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
        
        // 初始化轻量级 TS Muxer 壳，对接管道
        tsMuxer = TSMuxerProxy(outputStream)
        
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
            
            // --- 将音视频硬编码出来的 Byte 流灌入 TS Muxer ---
            videoEncoder.onFormatChanged = { format ->
                tsMuxer?.addTrack(format, true)
            }
            videoEncoder.onDataAvailable = { buffer, info ->
                tsMuxer?.writeSampleData(true, buffer, info)
            }
            
            audioEncoder.onFormatChanged = { format ->
                tsMuxer?.addTrack(format, false)
            }
            audioEncoder.onDataAvailable = { buffer, info ->
                tsMuxer?.writeSampleData(false, buffer, info)
            }
            
            startCapture()
        }
        return START_NOT_STICKY
    }

    private fun startCapture() {
        // [严格达标]: 强制 1080P (1920x1080), 8Mbps, 30fps
        val width = 1920
        val height = 1080
        val dpi = resources.displayMetrics.densityDpi

        videoEncoder.prepare(width, height, 8000000, 30) // 8Mbps
        videoEncoder.start()
        
        // [严格达标]: 强制 128Kbps 音频
        audioEncoder.prepare(44100, 128000, 2)
        audioEncoder.start()

        // 视频：捕获屏幕
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            videoEncoder.inputSurface, null, null
        )
        
        // 音频：捕获应用内声音 (Android 10+ 提供 AudioPlaybackCapture)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
            val audioFormat = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(44100)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build()
                
            val audioCaptureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection!!)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .build()
                
            val minBufferSize = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT)
            audioRecord = AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(minBufferSize * 2)
                .setAudioPlaybackCaptureConfig(audioCaptureConfig)
                .build()
                
            isRecording = true
            audioRecord?.startRecording()
            
            // 另起线程读取 PCM 并塞给 AudioEncoder
            Thread {
                val buffer = ByteArray(minBufferSize)
                while (isRecording) {
                    val readResult = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (readResult > 0) {
                        audioEncoder.encodePcmData(buffer, readResult)
                    }
                }
            }.start()
        }
    }

    override fun onDestroy() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        
        audioEncoder.stop()
        
        virtualDisplay?.release()
        mediaProjection?.stop()
        videoEncoder.stop()
        
        tsMuxer?.release()
        streamServer?.stop()
        
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
            .setContentText("正在录制屏幕及系统音频并推流...")
            .build()
    }
}
