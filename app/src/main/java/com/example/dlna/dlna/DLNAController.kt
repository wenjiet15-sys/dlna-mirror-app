package com.example.dlna.dlna

import android.util.Log
import org.fourthline.cling.UpnpService
import org.fourthline.cling.UpnpServiceImpl
import org.fourthline.cling.controlpoint.ActionCallback
import org.fourthline.cling.model.action.ActionInvocation
import org.fourthline.cling.model.message.UpnpResponse
import org.fourthline.cling.model.meta.Device
import org.fourthline.cling.support.avtransport.callback.Play
import org.fourthline.cling.support.avtransport.callback.SetAVTransportURI

class DLNAController {
    // 这里需在 Android 线程或者后台 Service 里初始化绑定
    val upnpService: UpnpService = UpnpServiceImpl()

    fun searchDevices() {
        // 向局域网广播 SSDP，发现电视机
        upnpService.controlPoint.search()
    }

    fun cast(device: Device<*, *, *>, streamUrl: String) {
        val avTransportService = device.findService(org.fourthline.cling.model.types.UDAServiceType("AVTransport")) 
        if (avTransportService == null) {
            Log.e("DLNA", "Device does not support AVTransport")
            return
        }

        Log.i("DLNA", "Sending URI ($streamUrl) to Device: ${device.details.friendlyName}")
        
        // 发送播放地址和元数据，伪装成 video/mp2t 类型
        val metadata = """
            <DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
                <item id="1" parentID="0" restricted="1">
                    <dc:title>Screen Mirror</dc:title>
                    <upnp:class>object.item.videoItem</upnp:class>
                    <res protocolInfo="http-get:*:video/mp2t:*">$streamUrl</res>
                </item>
            </DIDL-Lite>
        """.trimIndent()

        val setUriCallback = object : SetAVTransportURI(avTransportService, streamUrl, metadata) {
            override fun success(invocation: ActionInvocation<*>?) {
                // URI 设置成功后，发送 Play 指令
                upnpService.controlPoint.execute(object : Play(avTransportService) {
                    override fun success(invocation: ActionInvocation<*>?) {
                        Log.i("DLNA", "Play commanded successfully!")
                    }
                    override fun failure(invocation: ActionInvocation<*>?, operation: UpnpResponse?, defaultMsg: String?) {
                        Log.e("DLNA", "Play failed: $defaultMsg")
                    }
                })
            }
            override fun failure(invocation: ActionInvocation<*>?, operation: UpnpResponse?, defaultMsg: String?) {
                Log.e("DLNA", "Set URI failed: $defaultMsg")
            }
        }
        upnpService.controlPoint.execute(setUriCallback)
    }
}
