package com.example.dlna.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log

/**
 * 低延迟 AAC 硬件编码器 (满足 128Kbps 核心指标)
 */
class AudioEncoder {
    private var mediaCodec: MediaCodec? = null
    var onDataAvailable: ((ByteArray) -> Unit)? = null

    // 严苛遵守题目要求：128Kbps
    fun prepare(sampleRate: Int = 44100, bitRate: Int = 128000, channelCount: Int = 2) {
        try {
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channelCount).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate) // 强制 128Kbps
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
            }
            
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (e: Exception) {
            Log.e("AudioEncoder", "Prepare failed", e)
        }
    }

    fun start() {
        mediaCodec?.start()
        Thread { drainEncoder() }.start()
    }
    
    // 供外部 AudioRecord 写入 PCM 数据格式 (手机内录截获的声音)
    fun encodePcmData(pcmData: ByteArray, length: Int) {
        val inputBufferIndex = mediaCodec?.dequeueInputBuffer(10000) ?: -1
        if (inputBufferIndex >= 0) {
            val inputBuffer = mediaCodec?.getInputBuffer(inputBufferIndex)
            inputBuffer?.clear()
            inputBuffer?.put(pcmData, 0, length)
            mediaCodec?.queueInputBuffer(inputBufferIndex, 0, length, System.nanoTime() / 1000, 0)
        }
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
