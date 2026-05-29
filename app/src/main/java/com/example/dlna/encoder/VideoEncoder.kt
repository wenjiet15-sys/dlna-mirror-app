package com.example.dlna.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

class VideoEncoder {
    private var mediaCodec: MediaCodec? = null
    var inputSurface: Surface? = null
        private set

    var onFormatChanged: ((MediaFormat) -> Unit)? = null
    var onDataAvailable: ((ByteBuffer, MediaCodec.BufferInfo) -> Unit)? = null
    
    private var isRunning = false

    fun prepare(width: Int, height: Int, bitRate: Int, frameRate: Int) {
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel41)
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
        isRunning = true
        mediaCodec?.start()
        Thread { drainEncoder() }.start()
    }

    private fun drainEncoder() {
        val bufferInfo = MediaCodec.BufferInfo()
        while (isRunning && mediaCodec != null) {
            try {
                val outputBufferIndex = mediaCodec!!.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    onFormatChanged?.invoke(mediaCodec!!.outputFormat)
                } else if (outputBufferIndex >= 0) {
                    val outputBuffer = mediaCodec!!.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size != 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        onDataAvailable?.invoke(outputBuffer, bufferInfo)
                    }
                    mediaCodec!!.releaseOutputBuffer(outputBufferIndex, false)
                }
            } catch (e: Exception) {
                break
            }
        }
    }

    fun stop() {
        isRunning = false
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (e: Exception) {}
        mediaCodec = null
    }
}
