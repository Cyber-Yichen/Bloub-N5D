# 监控录像与对象存储 · 0.9.0

网页进入「监控」配置，设备图库只显示「监控」开关。默认关闭录像和自动上传。开启后保持 Bloub 在前台，录制无声 H.264 / MP4 分段；相机关闭或权限未授予时不会录制。设备切后台停止录像与感知，已完成的分段保留。

## 连接方式

| 位置 | 类型 | 地址示例 | 其他字段 |
| --- | --- | --- | --- |
| 群晖 / 威联通等 NAS | WebDAV | `https://nas.example.com:5006/录像` | NAS 用户名、密码；先启用 WebDAV，并授予目录创建与写入权限 |
| MinIO / 提供 S3 服务的 NAS | S3 | `https://s3.example.com:9000` | bucket、地域、访问 ID、密钥；使用 path-style 请求 |
| 阿里云 OSS | OSS | `https://my-bucket.oss-cn-hangzhou.aliyuncs.com` | bucket=`my-bucket`，AccessKey ID / Secret；可填 STS token |
| 腾讯云 COS | COS | `https://my-bucket-1250000000.cos.ap-beijing.myqcloud.com` | 完整 bucket、SecretId / SecretKey；可填临时 token |

NAS 通过 WebDAV 或 S3 适配，未实现直接 SMB / NFS 挂载。TrueNAS 需另行提供 S3 服务，例如运行 MinIO。云地址使用官方存储桶域名，不包含目录；目录填写在「目录前缀」。HTTPS 使用系统证书与主机名验证，不接受任意自签名证书。只有明确打开「允许局域网 HTTP」才接受 HTTP 地址。

密钥和临时令牌由 Android Keystore AES-GCM 加密后存储。读取接口只返回 secretConfigured / tokenConfigured，不返回凭证。保存时密码留空代表保留；「清除」才删除。访问 ID 和 NAS 用户名属于连接配置，会在网页显示。此局域网网页本身使用 HTTP，配置凭证时应使用可信局域网，勿把服务映射到公网。

## 分段、队列与清理

- 分段默认 60 秒，可调 15–300 秒。分段切换会短暂停顿；没有合成为无缝长视频。
- 完成的 MP4 及同名 `.mp4.json` 存入设备应用私有 `files/monitor-video/`。拍摄朝向写入 MP4 rotation；音频不录制。
- 默认本地容量预算 1024 MiB，可调 128–8192 MiB。开始下一段前预留预计编码空间，并检查设备剩余空间；不足时暂停，保留待上传文件。
- 开启自动上传后，每十五秒检查队列，依次上传视频与 JSON；两者都成功才将本机记录标为已上传。失败退避 30 秒至 1 小时；可点「立即重试上传」。上传配置变更后重新安排重试。
- 上传没有成功的分段不自动删除。已上传分段默认本机再保留一天，可调 1–7 天。网页允许手动删除本机分段；删除不作用于 NAS 或云端副本。
- 对象路径为 `<前缀>/<UTC 开始日期>/<分段名>.mp4`；默认前缀 bloub。重复重试使用相同路径覆盖，避免产生多个副本。更改前缀、日期或存储位置会产生新位置的副本。
- 异常退出留下的临时片段，下次启动尝试恢复可读 MP4；未能完成容器的片段无法保证恢复。
- 开启监控是明确的连续录像操作，因此不受自动照片的夜间休息规则限制。停止网页实时画面不会停止已启用的监控；照片仍按工位状态间隔保存。

云端上传签名使用 OSS V1、COS XML V5、S3 Signature V4。云签名时间会尝试参考服务端 HTTPS Date 头，不修改设备时钟；仍需保持设备时间正确。请求拒绝重定向；连接、读取及整次上传有超时限制。

## API

所有 POST 使用六位访问码的 Bearer 请求头与同源网页请求。配置请求使用 `Content-Type: application/json`，正文最多 16 KiB。示例地址、账号和密钥均为占位符。

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| GET | `/api/monitor` | 开关、录制状态、上传状态、待上传数、容量、公开配置、最近 100 段 |
| POST | `/api/settings` | `{"monitorEnabled":true,"camera":true}` 开启；false 停止 |
| POST | `/api/monitor/config` | 更新配置，未提供的字段保留 |
| POST | `/api/monitor/retry` | 重新安排上传 |
| GET | `/api/videos/{id}.mp4` | 下载已完成的视频 |
| POST | `/api/videos/{id}.mp4/delete` | 删除本机视频与标签 |

配置字段：provider、endpoint、bucket、region、prefix、accessId、username、autoUpload、allowHttp、segmentSeconds、storageMiB、localDays；写入另外接受 secret、token、clearSecret、clearToken。非法类型、范围、地址和路径返回 400；凭证不会放进 URL。

```json
{
  "provider": "webdav",
  "endpoint": "https://nas.example.com:5006/录像",
  "username": "<NAS 用户名>",
  "secret": "<NAS 密码>",
  "prefix": "bloub",
  "autoUpload": true,
  "allowHttp": false,
  "segmentSeconds": 60,
  "storageMiB": 1024,
  "localDays": 1
}
```

`recording` 表示实际录制，`enabled` 只表示请求开启；两者不相等时查看 status。videos[].metadata 包含 startedAt、endedAt、durationMs、silent、uploaded、uploadedAt 等字段；崩溃恢复的记录可能缺少开始/结束时间，并标记 recovered。

## 本版验证范围

在 N5D 相机 2 实测分段录像、网页下载、关闭预览不停止录像，以及本机 WebDAV 模拟端的 HTTP 503 保留、重试和逐字节一致性。纯 Java 校验独立对照官方 SDK 的九个签名样例，包括中文/空格路径和临时令牌，并检查 JPEG XMP 与 WebDAV 状态码处理。

没有提供实际 OSS/COS 账号及 NAS 环境，因此未验证真实存储桶权限、云服务区域配置或具体 NAS 固件。使用前在网页确认首次上传完成；本应用不是安防录像系统，也未提供网络故障下的无限存储保证。

参考：[OSS 签名](https://www.alibabacloud.com/help/en/oss/include-signatures-in-the-authorization-header)、[COS 签名](https://www.tencentcloud.com/document/product/436/7778)、[S3 Signature V4](https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-presigned-url.html)。
