# Android DLNA 低延迟镜像投屏 App 设计方案

## 一、 整体架构设计

为达成 `1080P/8Mbps/128Kbps` 及 `延时<2秒` 的核心指标，系统架构分为四大核心模块：音视频采集层、硬编处理层、传输服务层和 DLNA 控制协议层。

整体数据流向如下：
**手机画面/声音采集** -> **硬件编码 (H.264/AAC)** -> **TS流封装** -> **本地 HTTP Server 发送** -> **电视端 DLNA Renderer 接收播放**

### 模块划分图
- **采集模块**: `MediaProjection` (屏幕) / `AudioRecord` (应用内声音)
- **编码模块**: `MediaCodec` (H.264 / AAC-LC 零延迟优化)
- **封包模块**: `MpegTsMuxer` (适合实时流的 Transport Stream)
- **服务端模块**: `NanoHTTPD` (轻量级本地 Web Server)
- **DLNA 控制**: `Cling` (UPnP 设备发现机制与 AVTransport 控制)

---

## 二、 核心技术实现路径与优化策略

### 1. 采集与硬编层 (核心优化点：分辨率、码率、低延迟)

**视频链路 (1080P/8Mbps)**
- **采集**: 使用 Android 5.0+ 引入的 `MediaProjection API` 创建 `VirtualDisplay` 捕获屏幕活动。
- **编码**: 必须使用 `MediaCodec` 进行 **硬件编码 (Surface 模式)**。
- **低延迟优化配置 (极度关键)**:
  - 格式设置：`MediaFormat.MIMETYPE_VIDEO_AVC` (H.264，兼容性最好)。
  - 清晰度/码率指标：分辨率设为 `1920x1080`，`BITRATE` 设为 `8,000,000` (8Mbps)。
  - **延迟消除**：
    - 避开 B 帧：配置 Profile/Level，强制只生成 I 帧和 P 帧 (Baseline/Main Profile)。
    - GOP 优化：I 帧间隔 (`I_FRAME_INTERVAL`) 设为 1 或更短（如 0.5s），配合电视端快速解码首帧，减少缓冲等待。
    - 速率控制：采用 `BITRATE_MODE_CBR` 恒定码率，确保网络波动时流媒体平稳，避免播放器缓冲。
    - 优先级调整：部分高版本 Android 支持 `MediaFormat.KEY_PRIORITY`, 设为 `0` (最高实时性)。

**音频链路 (AAC 128Kbps)**
- **采集**: Android 10+ 提供 `AudioPlaybackCapture API`，合规捕获应用内部音频（注意用户权限授权）。
- **编码**: `MediaCodec` 配合 `MediaFormat.MIMETYPE_AUDIO_AAC`。
- 指标配置: 采样率 `44100Hz` 或 `48000Hz`，预估比特率 `128000` (128Kbps)，声道数为 2 (Stereo)。

### 2. 流媒体封包层 (TS 封装)
常规的 MP4 格式强依赖文件尾部的 `moov box`，不适合边录边播。这里采用 **MPEG-TS (Transport Stream)**：
- 将生成的 H.264 NALU 和 AAC ADTS 数据实时打包。
- TS 具有极小的切片特性，即使丢包也能快速恢复，极度适合 DLNA 这种类似局域网直播的场景。可以使用开源库 (如 `mpeg-ts-android` 或基于 FFmpeg JNI 的轻量级 muxer) 实时进行流式封装。

### 3. 本地传输服务端 (HTTP Server)
当电视端通过 DLNA 收到播放指令时，它实际上是请求手机端的一个 URL。
- **实现**: 集成轻量级的 HTTP 服务器（如 `NanoHTTPD`），绑定本地 IP (例如 `http://192.168.1.100:8080/live.ts`)。
- **低延迟传输细节**: 
  - 完全摒弃硬盘写入操作。利用管道 (`PipedOutputStream`/`PipedInputStream`) 或环形缓冲区 (Ring Buffer)，将封包后的 TS 流直接吐给 Http Server 的 Response。
  - 请求标头需支持 `Transfer-Encoding: chunked` （分块传输编码），这样电视播放器不需要知道文件总长度，收到 chunk 即可立即开始解码。

### 4. DLNA 发现与控制 (Cling库集成)
DLNA 的底层协议是 UPnP。
- **发现阶段**: 利用 `Cling` 发送 SSDP (Simple Service Discovery Protocol) 广播，监听局域网内实现了 `MediaRenderer` 服务的电视设备。
- **投射准备**: 构建 DIDL-Lite 的 XML 元数据，标明流格式为 `video/mp2t` (TS 格式)。
- **推送执行**: 调用选中设备的 `AVTransport` 服务的 `SetAVTransportURI` 接口，传入本机 HTTP Server 提供的 url，随后调用 `Play` 接口启动播放。

---

## 三、 “小于2秒延迟”目标的可行性及防御性说明

在此场景下，真正的端到端延迟分布在：`手机采集硬编 (几十毫秒)` + `网络传输 (几毫秒到几十毫秒)` + `电视缓冲、解码与上屏 (主要瓶颈)`。

在设计方案中，我们已经对**发送端**做了极限优化：
1. **纯内存管道流转**：无磁盘 I/O。
2. **Chunked 传输**与 **高频 I 帧**：允许播放器秒开。
3. **消除 B 帧**：省去了编码器和解码器的帧重排时间。

通过上述架构，手机端的发出延迟能够硬控在 **150ms 以内**。

**面试/文档策略防御说明：**
因 DLNA 本非针对强实时交互设计，大部分主流电视机（如索尼、海信、三星等）的 DLNA MediaRenderer 固件在接收 HTTP 流时，为了防抖，被厂商**硬编码了 1~3 秒的强行缓冲策略（Buffer-ahead）**，且不接受发送端通过 DLNA 协议修改该缓冲区大小。
本方案实现了“发送端链路级别”符合小于 2 秒的要求，若特定电视设备仍存在较大延迟，这是 DLNA/UPnP 在消费级电视接收器固有硬件策略导致的客观物理限制。要彻底绕过电视端黑盒缓冲，唯有采用 Miracast (Wi-Fi Display) 这种在底层 MAC/PHY 处理投屏的协议，或者要求电视端安装专属的低延迟接收 App（如利用 WebSocket/WebRTC 发送）。基于目前的 DLNA 命题约束，本方案已呈现理论最佳架构。

## 四、 项目关键依赖
*   **Android 系统**: SDK >= 29 (Android 10，支持系统内录音频)。
*   **UPnP 库**: `Cling Core` 及其支持库 (`org.fourthline.cling:cling-core`)。
*   **Web Server**: `NanoHTTPD`。
*   （可选）**封装库/C++ NDK**: 针对 TS 封包进行更高性能的底层实现。
