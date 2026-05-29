package com.example.dlna.encoder

import android.media.MediaCodec
import android.media.MediaFormat
import android.util.Log
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * 这是一个轻量级的 TS Muxer 代理壳。
 * 
 * 在真实的上架 App 中，由于 Android SDK 没有自带 MediaMuxer 导出 TS 流的功能 (只支持 MP4/WebM)，
 * 这里的真正实现通常是：
 * 1. 引入 FFmpeg 的 libavformat (C++ / JNI)
 * 2. 或者集成专门的 pure-Java 库类如 `mpeg-ts-android` 或 `jcodec`
 * 
 * 为了证明整套架构的联通性并且能在不依赖庞大 NDK/C++ 环境下演示推流：
 * 此代理将接管 Encoder 层吐出的 H264 NALU 和 AAC ADTS，并将其通过 outputStream 喂给 Http Server。
 * 
 * （注：如果直接向网络写 H264 裸流，部分播放器仍然可以借助探测机制播出来，但在 DLNA 标准里这很不稳定，
 * 所以真正的商业交付代码必须在这里挂载真正的 ts_muxer 库引擎。）
 */
class TSMuxerProxy(private val outputStream: OutputStream) {
    
    private var videoFormat: MediaFormat? = null
    private var audioFormat: MediaFormat? = null
    
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    
    private var isMuxerStarted = false
    
    @Synchronized
    fun addTrack(format: MediaFormat, isVideo: Boolean) {
        if (isVideo) {
            videoFormat = format
            videoTrackIndex = 0
            Log.i("TSMuxer", "Added Video Track: $format")
        } else {
            audioFormat = format
            audioTrackIndex = 1
            Log.i("TSMuxer", "Added Audio Track: $format")
        }
        
        // 简单策略：只要视频就绪了，就假装可以开始 Mux 了 (视音频最好都齐了)
        if (videoFormat != null) {
            isMuxerStarted = true
        }
    }
    
    @Synchronized
    fun writeSampleData(isVideo: Boolean, byteBuf: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (!isMuxerStarted) return
        
        try {
            // [占位]: 真实的 TS 封包逻辑
            // 此处需要把传入的 byteBuf 封装成 PAT/PMT/PES 数据包流
            // TS packet size 固定为 188 字节。
            
            // 下方代码为直传裸流（回退模式），供 HTTP HTTPD 的 Chunked 传输出去。
            // 真实项目中这里应该调用类似于 `TsMuxer.writeSampleData(...)`
            
            val data = ByteArray(bufferInfo.size)
            byteBuf.get(data)
            
            // 为了防止阻塞编码器，最好在这加个带界限的任务队列，由单独的 Sender 线程去写
            outputStream.write(data)
            outputStream.flush()
            
        } catch (e: Exception) {
            Log.e("TSMuxer", "Error writing stream data to output", e)
        }
    }
    
    fun release() {
        isMuxerStarted = false
        try {
            outputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
