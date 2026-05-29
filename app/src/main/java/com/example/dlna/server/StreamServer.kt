package com.example.dlna.server

import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import android.util.Log

/**
 * 轻量级本地 HTTP 服务器，用于向电视喂流
 */
class StreamServer(port: Int) : NanoHTTPD(port) {
    // 数据源，管道流的消费者端，TS 流从这里读出
    var streamProvider: (() -> InputStream)? = null

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        Log.i("StreamServer", "Request received: $uri")
        
        // 电视请求播放地址 (例如 http://192.168.1.100:8080/live.ts)
        if (uri == "/live.ts") {
            val inputStream = streamProvider?.invoke() 
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Stream not ready")
            
            // 核心: 使用 Chunked分块响应，避免电视等待文件尾，实现边录边播秒开
            return newChunkedResponse(Response.Status.OK, "video/mp2t", inputStream).apply {
                addHeader("Connection", "keep-alive")
                addHeader("Cache-Control", "no-cache")
                // 有些老电视需要伪装 content features 才能播放 DLNA
                addHeader("contentFeatures.dlna.org", "DLNA.ORG_PN=MPEG_TS_SD_EU;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000")
            }
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
    }
}
