# DLNA Screen Mirroring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement an Android application that captures screen and audio, encodes them via hardware codecs, multiplexes to MPEG-TS, and serves via a local HTTP server for DLNA renderers.

**Architecture:** Android MediaProjection for video, AudioPlaybackCapture for audio, MediaCodec for hardware encoding, NanoHTTPD for chunked streaming, and Cling Core for DLNA device discovery and control.

**Tech Stack:** Android (Kotlin), MediaCodec, NanoHTTPD, Cling Core

---

### Task 1: Project Setup and Dependencies

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add required dependencies**

```kotlin
// In app/build.gradle.kts
dependencies {
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.fourthline.cling:cling-core:2.1.2")
    implementation("org.fourthline.cling:cling-support:2.1.2")
    // Assuming a lightweight TS muxer or relying on MediaMuxer if supported
}
```

- [ ] **Step 2: Add essential permissions**

```xml
<!-- In AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
```

- [ ] **Step 3: Commit**

```bash
git add app/build.gradle.kts app/src/main/AndroidManifest.xml
git commit -m "chore: setup project dependencies and permissions"
```

### Task 2: Implement MediaCodec Video Encoder

**Files:**
- Create: `app/src/main/java/com/example/dlna/encoder/VideoEncoder.kt`

- [ ] **Step 1: Write the video encoder logic**

```kotlin
package com.example.dlna.encoder

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface

class VideoEncoder {
    private var mediaCodec: MediaCodec? = null
    var inputSurface: Surface? = null
        private set

    fun prepare(width: Int, height: Int, bitRate: Int, frameRate: Int) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // 1 second
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel41)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
        }
        
        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = mediaCodec?.createInputSurface()
    }

    fun start() {
        mediaCodec?.start()
    }

    fun stop() {
        mediaCodec?.stop()
        mediaCodec?.release()
        mediaCodec = null
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/dlna/encoder/VideoEncoder.kt
git commit -m "feat: implement low-latency hardware video encoder"
```

### Task 3: Implement Local HTTP Stream Server

**Files:**
- Create: `app/src/main/java/com/example/dlna/server/StreamServer.kt`

- [ ] **Step 1: Write the HTTP server logic**

```kotlin
package com.example.dlna.server

import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import java.io.PipedInputStream

class StreamServer(port: Int) : NanoHTTPD(port) {
    var streamProvider: (() -> InputStream)? = null

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        if (uri == "/live.ts") {
            val inputStream = streamProvider?.invoke() ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Stream not ready")
            
            return newChunkedResponse(Response.Status.OK, "video/mp2t", inputStream).apply {
                addHeader("Connection", "keep-alive")
                addHeader("Cache-Control", "no-cache")
            }
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/dlna/server/StreamServer.kt
git commit -m "feat: implement chunked HTTP server for TS stream"
```

### Task 4: Setup MediaProjection Capture Service

**Files:**
- Create: `app/src/main/java/com/example/dlna/service/CaptureService.kt`

- [ ] **Step 1: Write the capture service logic**

```kotlin
package com.example.dlna.service

import android.app.Service
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import com.example.dlna.encoder.VideoEncoder

class CaptureService : Service() {
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private val videoEncoder = VideoEncoder()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", -1) ?: -1
        val resultData = intent?.getParcelableExtra<Intent>("RESULT_DATA")
        
        if (resultCode != -1 && resultData != null) {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
            
            startCapture()
        }
        return START_NOT_STICKY
    }

    private fun startCapture() {
        val dm = resources.displayMetrics
        val width = dm.widthPixels
        val height = dm.heightPixels
        val dpi = dm.densityDpi

        videoEncoder.prepare(width, height, 8000000, 30) // 1080p, 8Mbps, 30fps
        videoEncoder.start()

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
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/dlna/service/CaptureService.kt
git commit -m "feat: implement MediaProjection screen capture service"
```

### Task 5: Implement DLNA Controller (Cling Setup)

**Files:**
- Create: `app/src/main/java/com/example/dlna/dlna/DLNAController.kt`

- [ ] **Step 1: Write DLNA discovery and control logic**

```kotlin
package com.example.dlna.dlna

import org.fourthline.cling.UpnpService
import org.fourthline.cling.UpnpServiceImpl
import org.fourthline.cling.controlpoint.ActionCallback
import org.fourthline.cling.model.action.ActionInvocation
import org.fourthline.cling.model.message.UpnpResponse
import org.fourthline.cling.model.meta.Device
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.support.avtransport.callback.Play
import org.fourthline.cling.support.avtransport.callback.SetAVTransportURI

class DLNAController {
    private val upnpService: UpnpService = UpnpServiceImpl()

    fun searchDevices() {
        upnpService.controlPoint.search()
    }

    fun cast(device: Device<*, *, *>, streamUrl: String) {
        val avTransportService = device.findService(org.fourthline.cling.model.types.UDAServiceType("AVTransport")) ?: return

        val setUriCallback = object : SetAVTransportURI(avTransportService, streamUrl, "MockMetadata") {
            override fun success(invocation: ActionInvocation<*>?) {
                val playCallback = object : Play(avTransportService) {
                    override fun success(invocation: ActionInvocation<*>?) {}
                    override fun failure(invocation: ActionInvocation<*>?, operation: UpnpResponse?, defaultMsg: String?) {}
                }
                upnpService.controlPoint.execute(playCallback)
            }
            override fun failure(invocation: ActionInvocation<*>?, operation: UpnpResponse?, defaultMsg: String?) {}
        }
        upnpService.controlPoint.execute(setUriCallback)
    }

    fun stop() {
        upnpService.shutdown()
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/example/dlna/dlna/DLNAController.kt
git commit -m "feat: implement DLNA device discovery and casting via Cling"
```
