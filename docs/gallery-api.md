# 局域网图库与相机 API

适用于 Bloub-N5D 0.8.0。照片保留七天；音频不保存，人脸分析暂不启用，当前不识别身份或真实情绪。工位时间统计与流式检测框见 [工位 API](presence-api.md)。

## 打开图库

1. 在 N5D 长按打开设置，进入 **图库 → 电脑下载**。
2. 开启「局域网图库」，查看电脑网址和六位访问码。
3. 电脑与 N5D 连接同一局域网，打开 `http://<设备 IP>:8768/`，输入六位访问码。也可用「复制访问链接」直接进入。

网页支持缩略图、点击看大图、两倍缩放、翻页、单张下载、全部 ZIP 下载，以及手动开启实时画面和拍照。旧的无效链接会回到访问码输入页。

服务随 Bloub 前台运行，切后台或关闭服务时断开连接。无需电脑 ADB。访问码随机生成，更新安装保持不变；从旧长密钥版本升级时自动换成六位。这里只提供本地 HTTP 服务，适用于自己的局域网。

## 实时画面与拍照

先在设备「感知」开启相机并授予权限；保存照片还需开启「保存观察」。

- **开启实时画面**：640×480 MJPEG，按正确方向输出。使用独立编码线程，发送最新帧，跳过积压画面；网页自动续接流连接。实际帧率取决于设备负载与局域网。
- **拍一张**：保存当前完整预览尺寸的 JPEG 和元数据；不开启实时画面也能拍照。
- **停止实时画面**：关闭持续相机观察，恢复自动观察规则。
- 网页隐藏或离开时请求停止预览；连接意外断开后，相机最多约 90 秒自动停止。设备切后台会立即停止。
- 网页手动操作可在夜间使用；自动拍摄遵守设备设置中的夜间时段。默认 23:00–08:00，有近期明显声音时允许短时观察。需要开启麦克风；夜间无声音时不拍摄。
- 夜间时段按设备本地时间计算，可修改开始和结束时间；相同时表示不设置休息时段。自动观察间隔至少两分钟。

N5D 相机 2 没有声明 JPEG 输出，相机 3 的 JPEG 会话实测会卡住。本版使用完整预览尺寸编码 JPEG，质量参数 95：相机 2 为 960×1280，相机 3 为 1280×960。旧照片保留原清晰度。相机 3 在当前办公室自动曝光偏暗，通道选择可在设备修改。

## 鉴权

受保护的 GET 接口接受 `Authorization: Bearer <六位访问码>`，或查询参数 `?key=<六位访问码>`。POST 相机操作仅接受 Bearer 请求头，网页使用同源请求。没有跨域开放、照片删除或文件上传接口。

以下示例中的地址和访问码均为占位符：

```bash
N5D_URL='http://<设备 IP>:8768'
N5D_CODE='<六位访问码>'
curl -H "Authorization: Bearer $N5D_CODE" "$N5D_URL/api/photos"
```

## 接口

| 方法 | 路径 | 返回 |
| --- | --- | --- |
| GET | `/` | 登录页或已授权的图库网页 |
| GET | `/api/health` | 版本、设备时间、保留天数及功能标记 |
| GET | `/api/photos?before=<游标>` | 每页最多十二张照片 |
| GET | `/api/photos/{id}/image` | 原尺寸 JPEG |
| GET | `/api/photos/{id}/image?download=1` | 带附件下载头的 JPEG |
| GET | `/api/photos/{id}/thumbnail` | 缩略图；旧记录回退原图 |
| GET | `/api/photos/{id}/metadata` | 单张照片元数据 |
| GET | `/api/archive.zip` | 全部未过期照片及 manifest.json |
| GET | `/api/camera` | 相机状态、当前通道、最近保存的照片 ID |
| POST | `/api/camera/start` | 请求开启实时画面 |
| GET | `/api/camera/stream` | 已开启相机的 MJPEG 流 |
| POST | `/api/camera/capture` | 请求拍照 |
| POST | `/api/camera/stop` | 停止实时观察 |

### 分页与元数据

`/api/photos` 返回 `total`、`enabled`、`retentionDays`、`items`、`nextBefore`。首次省略 before；继续读取时原样传入 nextBefore，零表示结束。游标是递增拍摄顺序，不能把它当作时间戳。新照片按保存顺序排在前面，避免设备时钟回拨后新照片被旧照片挡住。

items 中包含：

| 字段 | 含义 |
| --- | --- |
| id | 数字字符串，供图片和元数据接口使用 |
| capturedAt | 设备拍摄时间，Unix 毫秒 |
| captureOrder | 单调递增的排序值；旧照片以拍摄时间兼容 |
| cameraId | 相机通道，新照片为 2 或 3 |
| width / height | JPEG 原始像素尺寸 |
| rotationClockwise | 显示和分析时顺时针旋转角度 |
| faceAnalysis | 新照片固定为 false，表示没有执行人脸分析 |
| faces | 为旧接口保留；新照片为 0，不能据此判断有没有人 |
| motion | 低分辨率帧差的画面变化程度 |
| url / thumbnailUrl / downloadUrl | 带访问码的相对地址 |

相机 2 和旧照片顺时针旋转 90°；相机 3 当前预览为横向，角度为 0°。下载 JPEG 保留原始方向，AI 应读取 rotationClockwise 后再分析；不要用图片的宽高猜方向。

ZIP 包含 `{id}.jpg` 与 `manifest.json`；manifest 的 items 附带 file 字段和方向信息。电脑副本不受设备七天清理影响。设备时钟决定过期时间，请保持时钟正确。

### 主动拍照

```bash
curl -X POST -H "Authorization: Bearer $N5D_CODE" "$N5D_URL/api/camera/capture"
curl -H "Authorization: Bearer $N5D_CODE" "$N5D_URL/api/camera"
```

控制请求成功返回 HTTP 200 和 `{"accepted":true}`，表示操作已排队。读取相机状态的 lastCaptureId，等它变化后再读取对应图片或元数据；接受请求不等于拍照已完成。

相机状态包括 detectionEnabled、available、saving、live、cameraOn、cameraId、status、lastCaptureId、quietStart、quietEnd、automaticAllowed、faceAnalysis、uptimeMs、streamFrames、streamFps、streamEncodeMs。streamFps 是本次预览的设备编码平均帧率，不代表网页显示帧率；streamEncodeMs 是最近一帧从读取纹理到编码完成的耗时。休息时间使用一天中的分钟数（0–1439）。同一个设备的实时相机由各网页共享，停止操作会停止当前实时观察。

### 流式性能与测量

每个 MJPEG 帧附带 X-Capture-Uptime（读取纹理时的设备启动毫秒数）、X-Frame-Sequence、X-Encode-Millis。通过 /api/camera 的 uptimeMs 可估算设备与电脑的时钟偏移；这些字段用于计算读取到接收的耗时，不是从真实场景变化到浏览器显示的完整延迟。

2026-10-10 单客户端、相机 2 的八秒实测：动画正常运行时，接收约 24.2 fps，读取与编码中位 38 ms / P95 53 ms，读取到电脑接收中位 56 ms / P95 77 ms；设置打开、动画暂停时约 28.9 fps，中位 48 ms / P95 58 ms。另一轮动画运行测试约 22.8 fps、接收耗时 P95 221 ms。无线网络仍有波动；该结果不是固定帧率或延迟保证。最多两个实时流；网速不足时各连接跳过旧帧，避免持续排队。

### 错误

| 状态 | 情况 |
| --- | --- |
| 400 | 无效游标、不同来源的控制请求 |
| 401 | API 缺少或使用了错误访问码；POST 没有 Bearer |
| 404 | 照片不存在、已过期或路径不支持 |
| 405 | 不支持的方法或控制操作 |
| 409 | 相机未启用、拍照保存关闭、实时画面未开启或并发流已满 |
| 429 | 同一地址连续输入错误访问码，稍后重试 |

接口响应和照片禁用缓存；不把办公室照片、真实访问码或设备地址提交到公开仓库。用于 AI 分析时，由你决定读取哪些照片；本项目不会把数据主动发到云端。
