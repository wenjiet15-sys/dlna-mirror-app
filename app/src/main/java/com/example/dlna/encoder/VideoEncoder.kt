package com.example.dlna.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Surface

/**
 * 低延迟 H.264 硬件编码器
 */
class VideoEncoder {
    private var mediaCodec: MediaCodec? = null
    var inputSurface: Surface? = null
        private set
    
    // 假设通过回调把编码后的 H264 NALU 数据丢给 Muxer
    var onDataAvailable: ((ByteArray) -> Unit)? = null

    fun prepare(width: Int, height: Int, bitRate: Int, frameRate: Int) {
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate) // 8Mbps
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1秒1个I帧，极致版可配 0
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel41)
                // Android 10+ 提升编码器优先级，降低延迟
                setInteger(MediaFormat.KEY_PRIORITY, 0)
            }
            
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            inputSurface = mediaCodec?.createInputSurface()
        } catch (e: Exception) {
            Log.e("VideoEncoder", "Prepare failed", e)
        }
    }

    fun start() {
        mediaCodec?.start()
        // 实际工业代码中，这里需要另起线程循环调用 dequeueOutputBuffer 
        // 抓取编码好的数据，加上 ADTS/SPS/PPS 头后交给 Muxer
        Thread {
            drainEncoder()
        }.start()
    }

    private fun drainEncoder() {
        val bufferInfo = MediaCodec.BufferInfo()
        while (mediaCodec != null) {
            try {
                val outputBufferIndex = mediaCodec!!.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex >= 0) {
                    val outputBuffer = mediaCodec!!.getOutputBuffer(outputBufferIndex)
                    outputBuffer?.let {
                        val outData = ByteArray(bufferInfo.size)
                        it.get(outData)
                        onDataAvailable?.invoke(outData)
                        mediaCodec!!.releaseOutputBuffer(outputBufferIndex, false)
                    }
                }
            } catch (e: Exception) {
                break
            }
        }
    }

    fun stop() {
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (e: Exception) {}
        mediaCodec = null
    }
}
