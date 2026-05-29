package com.example.dlna.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer

class AudioEncoder {
    private var mediaCodec: MediaCodec? = null

    var onFormatChanged: ((MediaFormat) -> Unit)? = null
    var onDataAvailable: ((ByteBuffer, MediaCodec.BufferInfo) -> Unit)? = null

    private var isRunning = false

    fun prepare(sampleRate: Int = 44100, bitRate: Int = 128000, channelCount: Int = 2) {
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate) 
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (e: Exception) {
            Log.e("AudioEncoder", "Prepare failed", e)
        }
    }

    fun start() {
        isRunning = true
        mediaCodec?.start()
        Thread { drainEncoder() }.start()
    }
    
    fun encodePcmData(pcmData: ByteArray, length: Int) {
        if (!isRunning) return
        try {
            val inputBufferIndex = mediaCodec?.dequeueInputBuffer(10000) ?: -1
            if (inputBufferIndex >= 0) {
                val inputBuffer = mediaCodec?.getInputBuffer(inputBufferIndex)
                inputBuffer?.clear()
                inputBuffer?.put(pcmData, 0, length)
                mediaCodec?.queueInputBuffer(inputBufferIndex, 0, length, System.nanoTime() / 1000, 0)
            }
        } catch (e: Exception) {
            Log.e("AudioEncoder", "Encode PCM failed", e)
        }
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
