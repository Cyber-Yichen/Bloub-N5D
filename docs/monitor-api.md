# 监控录像与对象存储 · 0.9.3

网页「监控」配置，设备图库提供监控开关。默认关闭录像与自动上传。保持应用前台，实际录制设备支持的 H.264 或 H.265 / MP4；可选 AAC 16 kHz 单声道麦克风音轨。切后台停止录制并保留已完成分段。监控与照片保存互斥，网页与设备切换前提醒；关闭监控后需自行重新开启照片保存。

## 存储连接

| 类型 | 输入 | 凭证 |
| --- | --- | --- |
| NAS · WebDAV | NAS WebDAV 地址、存放目录；不填地域或存储桶 | 用户名、密码 |
| S3 / MinIO | 服务地址、存储桶、目录；高级签名地域默认 us-east-1 | AccessKey ID、Secret、可选临时令牌 |
| 阿里云 OSS | 存储桶、地域、目录；自动生成地址，高级项可填已绑定的 HTTPS 自定义域名 | AccessKey ID、Secret、可选 STS token |
| 腾讯云 COS | 完整存储桶（含 APPID）、地域、目录；自动生成地址 | SecretId、SecretKey、可选临时 token |

NAS 需启用 WebDAV 或提供 S3 服务；不直接挂载 SMB/NFS。S3 使用 path-style。HTTPS 验证系统证书和主机名；NAS/S3 的局域网 HTTP 需显式允许。请求拒绝重定向，避免凭证随跳转发送。

各服务独立保留配置与凭证，切换不会清除其他服务。密钥与令牌使用 Android Keystore AES-GCM 存储；API 只返回 secretConfigured / tokenConfigured。留空保留，clearSecret / clearToken 才清除。网页本身为局域网 HTTP，应在可信网络填写凭证，不映射公网。

### 测试连接

S3、OSS、COS 的按钮使用当前填写的草稿；密钥留空时使用该服务已保存的凭证，不保存草稿、不更改活动服务。以真实签名上传小 JSON 到 `<prefix>/.bloub-connection-test/<随机ID>.json`，再尝试删除。结果分别表示上传成功、删除成功或清理失败；失败不伪装成功。

至少需要创建对象权限。删除被拒绝时仍报告上传通过，并返回测试对象路径供清理。开启云端版本控制的存储桶可能保留对象历史版本或删除标记，应按存储桶生命周期管理；测试不改变云端版本策略。

## 录像设置

| 字段 | 默认 | 范围 / 含义 |
| --- | --- | --- |
| videoCodec | h264 | h264 / h265；仅选择设备实际可用编码 |
| resolution | auto | 自动或 capabilities 返回的 WIDTHxHEIGHT |
| frameRate | 30 | 10/15/24/25/30 fps，受所选编码和分辨率能力限制 |
| bitrateKbps | 2000 | 128–20000 kbps，且须在所选设备格式支持范围内 |
| segmentSeconds | 60 | 15–300 秒，每段独立 MP4 |
| storageMiB | 1024 | 128–8192 MiB，本机容量预算 |
| localDays | 1 | 1–7 天，上传成功后本机保留时长 |
| recordAudio | false | 保存麦克风 AAC 音轨，需要麦克风权限 |
| timestamp | true | 将设备本地日期与时间烧录到画面 |
| mirror | false | 镜像录像画面与几何叠加 |
| rotationEnabled / rotation | false / 0 | 基于转正画面再顺时针旋转 0/90/180/270 度 |
| overlayObjects | false | 烧录物体检测框 |
| overlayFaces / overlayHands / overlayHolistic | false | 烧录面部、手部、全身关键点与框 |
| overlayYamnet | false | 烧录当前声音类别与分数；需要麦克风，但不自动保存音轨 |
| motionOnly | false | 只保存画面变化前后窗口 |
| motionBeforeSeconds / motionAfterSeconds | 60 / 60 | 独立设置变化前、后保留秒数；各 0–1800 秒，启用时不能均为 0 |
| motionBeforeUnit / motionAfterUnit | minutes / minutes | 网页显示单位 seconds / minutes，存储时长始终为秒 |
| motionWindowMinutes | 1 | 旧接口兼容字段；只传此字段时更新前后两个时长 |
| uploadMode | manual | after_recording / scheduled / manual，对应每段录完、每日定时、手动 |
| autoUpload | false | 旧接口兼容字段，由 uploadMode 推导 |
| uploadScheduleEnabled / uploadTime | false / 20:00 | 定时标记由 uploadMode 推导；HH:mm 使用设备本地时间 |

旋转与镜像作用于实际编码像素，物体框、关键点同样变换；最终时间戳、声音文字与检测标签保持正常朝向。实时预览和录制叠加独立选择。关键点只显示当前模型结果，不识别身份或情绪、不参与工位学习或宠物互动。模型异步处理最新帧，结果过期后隐藏；录像不要求逐帧推理。

录像和 YAMNet 共用一条 PCM 输入，避免重复占用麦克风。音轨封装失败时保留视频并标记 audioMissing，不能据开关推断实际有音轨。

## 监控预览与录像质量

网页将预览、录像画面和编码放在相邻区域，录像画面不折叠。预览可测试未保存的时间戳、镜像、旋转及各叠加开关，选项不写入已保存配置，也不启用录像或照片保存。离开页面 / 浏览器隐藏时关闭预览，正在录制的视频继续。预览是低分辨率 MJPEG，不代表码率编码后的画质。

`/api/monitor` 的 `capabilities.codecs[]` 返回 id、label、automaticResolution 和 formats[]。每种格式包含 value、width、height、frameRates、minBitrateKbps / maxBitrateKbps。设备无对应编码器或配置时不展示可选项，服务器再次验证。当前实测 N5D 仅有可用 H.264 配置。

估算大小为 `(时长 * (码率 * 1000 + 音轨开启时 64000) / 8 * 1.03) + 4096` 字节。网页显示每段及每小时估算；实际文件随画面复杂度、编码器策略和分段间隙变化。帧率通过保留源时间戳、均匀跳过多余帧实现，不改变视频播放速度。

预览 options 只接受 timestamp、mirror、rotationEnabled、rotation、overlayObjects、overlayFaces、overlayHands、overlayHolistic、overlayYamnet、videoCodec、resolution、frameRate、bitrateKbps。无效类型返回 400；相机未开启、应用不在前台或设备配置不可用返回 409。流同时最多两路，每连接 70 秒，网页约 60 秒重连；租约 90 秒，由流心跳续期。关闭预览不修改实时观察的叠加选项，也不停止录像。

## 画面变化、分段与保留

- motionOnly=false 保存完整分段。切换分段会短暂停顿，不是无缝连续视频。
- motionOnly=true 连续编码到私有滚动缓冲；静止缓冲不出现在视频列表、不上传。干净相机画面的局部亮度变化触发窗口，补偿整体曝光变化，叠加文字和检测框不会触发。
- 触发后按独立的变化前 / 变化后秒数保留；接近的变化合并，持续变化延长结束时间。应用刚开始、休眠或分段间隙没有历史帧，不能补出不存在的前段。按 H.264 关键帧裁剪，起点可能比指定边界稍早。
- 正常录像与变化片段保存到应用私有 `files/monitor-video/`，附带同名 `.mp4.json`。临时缓冲不通过公开文件接口暴露。
- 开始新段前预留预计空间并检查剩余容量；满时暂停，待上传文件不自动删。变化窗口较大时按前后时长、分段时长、码率、音轨和容器开销计算容量预算，并增加 32 MiB 余量。
- 已上传分段按 localDays 清理；未成功上传不自动删除。网页手动删除只作用本机，不删除 NAS/云端副本。
- 异常退出后尝试恢复已完成可读 MP4；无法完成容器的片段不保证恢复。更换配置在下一相机会话/分段生效，当前分段记录自身配置快照。

## 上传

每段录完上传模式会在完整视频或变化片段完成封装后立即唤醒队列，继续处理余下待上传文件；定时与手动模式保留各自的开始条件。队列每 15 秒检查恢复状态，逐段上传 MP4 与 JSON，两者成功才标记已上传。失败退避 30 秒至 1 小时；新设置重置重试状态。定时上传按本地时间开始处理已有队列，全部完成后等待下一天；失败队列继续重试。「立即上传」可跳过定时并处理当前队列，也可在自动上传关闭时手动执行一次。

路径为 `<prefix>/<UTC开始日期>/<分段名>.mp4`，重试使用相同路径覆盖。更换存储位置会产生新位置的副本。签名使用 S3 Signature V4、OSS V4、COS XML V5；尝试参考服务端 Date，不修改设备时钟。

## API

沿用六位 Bearer 鉴权与同源 POST，配置 JSON 最大 16 KiB。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/monitor` | 实际录制/上传状态、容量、缓冲段数、capabilities、配置、profiles 和最近 100 段 |
| POST | `/api/settings` | `{ "monitorEnabled": true, "camera": true }` 开启 |
| POST | `/api/monitor/config` | 保存指定 provider 的配置和凭证，未提供字段保留 |
| POST | `/api/monitor/preview` | `{ "enabled": true, "options": { ... } }` 开启 / 更新草稿预览；false 关闭 |
| GET | `/api/monitor/preview/stream` | 监控草稿的 MJPEG，预览前须开启；同样 Bearer / key 鉴权 |
| POST | `/api/monitor/test` | 测试当前草稿的 S3/OSS/COS 上传与清理 |
| POST | `/api/monitor/retry` | 手动安排上传并清除失败退避 |
| GET | `/api/videos/{id}.mp4` | 已完成视频；支持单个 HTTP Range、206 与 416 |
| GET | `/api/videos/{id}.mp4?download=1` | 附件下载 |
| POST | `/api/videos/{id}.mp4/delete` | 删除本机视频及其标签 |

除上述录像/上传字段，还接受 provider、endpoint、endpointOverride、bucket、region、prefix、accessId、username、allowHttp；写入接受 secret、token、clearSecret、clearToken。严格校验类型、整数范围、时间、路径及服务地址，非法请求返回 400。GET 不返回密钥/令牌。

测试成功示例：

```json
{"connected":true,"uploadVerified":true,"cleanup":true,"message":"连接成功，上传验证通过"}
```

删除失败返回 cleanup=false、testObject 和说明；上传失败返回 400、connection_test_failed 和简短原因。地址无法解析、连接超时、HTTPS 验证失败、服务权限拒绝分别报告。

videos[].metadata 包含 schemaVersion、startedAt、endedAt、durationMs、silent、audioMissing、uploaded、options；变化录像还含 motionEvent、requestedStart、requestedEnd；恢复记录含 recovered。options 是该段配置快照，不包含 secret/token。

## 验证与限制

见 [0.9.3 实测](verification-0.9.3.md)。没有真实云端账号或 NAS 固件环境，实测模拟服务和签名不等于验证实际存储桶权限。本应用依赖前台运行和有限本机容量，不提供无限存储或安防级可用性保证。

参考：[S3 DeleteObject](https://docs.aws.amazon.com/AmazonS3/latest/API/API_DeleteObject.html)、[OSS DeleteObject](https://www.alibabacloud.com/help/en/oss/developer-reference/deleteobject)、[COS 签名](https://www.tencentcloud.com/document/product/436/7778)、[群晖 WebDAV](https://kb.synology.com/en-global/DSM/help/WebDAVServer/webdav_server)、[OSS 地域](https://www.alibabacloud.com/help/en/oss/user-guide/regions-and-endpoints)。
