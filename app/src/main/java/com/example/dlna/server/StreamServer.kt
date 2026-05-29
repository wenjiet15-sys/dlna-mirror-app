package com.example.dlna.server

import fi.iki.elonen.NanoHTTPD
import java.io.InputStream
import android.util.Log

class StreamServer(port: Int) : NanoHTTPD(port) {
    var streamProvider: (() -> InputStream)? = null

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        Log.i("StreamServer", "Request received: $uri")
        
        if (uri == "/live.ts") {
            val inputStream = streamProvider?.invoke() 
                ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Stream not ready")
            
            val response = newChunkedResponse(Response.Status.OK, "video/mp2t", inputStream)
            response.addHeader("Connection", "keep-alive")
            response.addHeader("Cache-Control", "no-cache")
            response.addHeader("contentFeatures.dlna.org", "DLNA.ORG_PN=MPEG_TS_SD_EU;DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000")
            return response
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not Found")
    }
}
