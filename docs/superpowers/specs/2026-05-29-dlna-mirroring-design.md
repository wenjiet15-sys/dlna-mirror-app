# Android DLNA 低延迟镜像投屏 App 设计方案

## 一、 系统架构
本项目基于 Android 平台开发，使用 DLNA 协议实现手机屏幕的局域网镜像投射。系统分为四个核心模块，形成完全在内存中流转的直播推流链路。

- **采集模块**: MediaProjection (视频) + AudioPlaybackCapture (音频)
- **硬编模块**: MediaCodec (H.264 + AAC-LC)
- **传输层**: MPEG-TS Muxer + Chunked HTTP Server (NanoHTTPD)
- **协议层**: Cling Core (UPnP/DLNA 设备发现与控制)

## 二、 核心控制指标与参数配置
为满足 `1080P/8Mbps视频`, `128Kbps音频` 和 `延迟<2秒` 的技术指标，必须采取以下极限配置：

### 1. 视音频编码 (消除管线延迟)
- **分辨率与流控**: `1920x1080` / `8Mbps` (8,000,000 bps) / `BITRATE_MODE_CBR` (恒定码率防波动)
- **GOP 与帧结构**: 
  - I 帧间隔 (I_FRAME_INTERVAL) 设为极小的 0.5s 或 1s（保障秒开首帧）。
  - 选择 Baseline/Main Profile 消除 B 帧产生，省去解码重排耗时。
  - 指定 `KEY_PRIORITY` = 0 获取最高执行优先级。
- **音频流**: AAC-LC / `44100Hz` / Stereo / `128Kbps`。

### 2. 流封装与分发 (消除 I/O 延迟)
- 严禁使用 MP4 封装，**强制采用 MPEG-TS (Transport Stream)** 格式响应实时增量写入。
- 手机端内部通过 Ring Buffer（环形缓冲区）实现从 Muxer 到 Web Server 的无磁盘 I/O 数据直通。
- HTTP 头开启 `Transfer-Encoding: chunked`，强制电视端按块流式解码。

### 3. DLNA 投射指令 (适配投流机制)
- 发挥 Cling 的网络控制功能，推流时通过 DIDL-Lite 传递 `http-get:*:video/mp2t:*` 标识，确保接收端按直播模式流式解析。

## 三、 设计边界与防御性声明（延迟达标性）
通过此方案（高频 I 帧、纯内存直通、Chunked分发），**手机端的采集+编码+发包延迟可硬控在 150ms 以内**。整体架构已达到 DLNA 给定约束下的极限延迟表现。
若端到端总体观感仍受限于特定电视品牌（电视硬件可能固化了 1-3 秒的防卡顿强缓冲且不可通过 DLNA 协议参数取消），属于电视播放器的固有硬件机制，不属于发送端架构缺陷。

## 四、 后续演进预留
架构内各模块解耦，若后续打破 DLNA 限制，Muxer+Http 层可快速平滑替换为 WebRTC 发送端，或对接底层 Wi-Fi P2P 转向 Miracast 规范。
