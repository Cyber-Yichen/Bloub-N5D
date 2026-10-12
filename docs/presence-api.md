# 工位观察与时间统计 · 0.9.1

## 开始使用

1. 在感知页开启相机，选择能完整看见座位和上半身的通道。
2. 进入 **工位 → 选择区域 / 纠正结果**，在实际横向画面中拖动框选座位，点击保存区域。
3. 可直接开始观察。需要纠正背景判断时，座位空着点击「记为空位」，坐下后点击「记为在位」；这是可选校准，两次均须使用对应的真实新画面。
4. 返回工位页，开启「工位观察」。连续稳定观察后开始统计。

设备和局域网网页都显示全天状态时间轴与最近七天在位时长；点击某一天查看该日。蓝色表示座位有人，淡绿色表示无人，条纹表示未观察 / 待确认。灰色不等于离岗。统计只反映所选座位区域是否有人，不区分你和坐在同一个座位的其他人，不做人脸或身份分析。

模型内置约 4.4 MiB 的 Google 官方 EfficientDet-Lite0，320×320 量化输入，仅在设备 CPU 上推理，使用两个线程。当前 N5D 实机推理约 170–200 ms，有实际人体检测结果；这不是完整工位识别准确率的验证。人体遮挡、经过的同事、逆光、画面过小、移动设备和相机角度都可能影响结果。

## 如何在设备学习

预训练检测器的权重固定；设备学习的是工位空 / 有人的轻量分类层和背景特征。空位、在位样本作为锚点，判断阈值随两种人工样本的区分程度调整；稳定且与人体检测一致的样本小幅更新本地原型。更新受锚点距离限制，模糊结果不用于学习；没有以模型自己的所有猜测不断重训检测器。光线的小变化会逐步适应，更换相机或重新选择区域后重置背景锚点，仍可直接使用人体检测；可重新确认样本改善背景适应。误判时可再次记为空位 / 在位，替换相应锚点。

只保存小尺寸特征与统计时间片，校准画面在内存中。图库保存仍由「保存观察」独立控制，有人默认每分钟、无人默认每十分钟保存一张，间隔可在设置修改；网页手动照片单独保存。照片保留七天，统计保留九十天，累计时长不依赖照片是否被删除。过期统计在启动、保存和读取统计时清理；持续读取时至多每小时清理一次。卸载会删除本机数据。

## 计时规则

- 开启工位观察后约每二十秒进行一次短时相机观察；不常驻录制视频。
- 三次间隔至少十秒的一致观察确认状态；只有前后均确认同一状态的连续采样区间才计入有人 / 无人时间。刚到位或离开时存在确认窗口，时长是保守估计，不是精确考勤。
- 模糊、遮挡、暗光、采样间隔超过六十秒、相机关闭、切后台、程序停止和设备时间跳变都不补算为在位。
- 夜间自动观察继续遵守自定义休息时间，默认 23:00–08:00；近期明显声音可短时唤醒。没有观察的夜间时段显示为未观察。
- 按设备本地日期和时区分天；真实持续时间使用单调时钟。设备日期错误时图上的日期也会错误；本应用不会修改系统时间。

## 网页检测框

在实时观察中勾选「检测框」，然后开启实时画面；播放过程中也可以开关。框、类别和置信度直接绘入 MJPEG 输出。这个开关显示模型支持的物体类别；工位判断仅使用所选区域内的人体结果。框约每半秒更新一次，画面编码独立运行，不要求每个视频帧都推理。超过约 1.5 秒的结果不会继续画框。本次局域网短测开启检测框后约 29.6 帧/秒，画面读取至电脑接收的中位数约 46 ms、P95 约 52 ms；该数值不含传感器曝光和浏览器显示耗时，也不保证不同网络及动画负载下相同。框与最新画面可能存在更新间隔，不承诺快速运动时逐帧对齐。

检测开关和实时相机由网页共享；停止实时画面、切后台或租约到期停止检测框。原尺寸图库照片不带检测框。

## API

沿用 [图库鉴权](gallery-api.md)：GET 接受 Bearer 或 key；POST 只接受 Bearer，同源网页控制。以下地址与访问码是占位符。

| 方法 | 路径 | 含义 |
| --- | --- | --- |
| GET | `/api/presence?days=7` | 当前工位状态与最近 1–365 天统计；默认七天 |
| GET | `/api/detections` | 当前新鲜检测框、类别、分数和采样启动时间 |
| POST | `/api/camera/detection/on` | 在已开启实时相机时启用检测框 |
| POST | `/api/camera/detection/off` | 关闭流式检测框 |

`/api/presence` 的 days 必须为 1–365 的整数，否则 HTTP 400。网页通过 `/api/seat/region`、`/api/seat/label` 选择区域与纠正结果，详见图库 API；不提供清空统计接口。

```bash
N5D_URL='http://<设备 IP>:8768'
N5D_CODE='<六位访问码>'
curl -H "Authorization: Bearer $N5D_CODE" "$N5D_URL/api/presence?days=7"
curl -X POST -H "Authorization: Bearer $N5D_CODE" "$N5D_URL/api/camera/start"
curl -X POST -H "Authorization: Bearer $N5D_CODE" "$N5D_URL/api/camera/detection/on"
curl -H "Authorization: Bearer $N5D_CODE" "$N5D_URL/api/detections"
```

### 工位返回字段

- enabled、active、ready：是否开启、是否处于前台、是否完成两种样本校准。
- state：occupied / empty / unknown；status 为中文当前状态。
- roi：正确方向画面的归一化 `[left,top,right,bottom]`，范围 0–1。
- cameraId、lastObservedAt、personScore、inferenceMs、adaptations：相机、最近观察时间、区域人体分数、最近推理毫秒数、本地原型更新次数。
- deviceTime、timezone、retentionDays、sampleIntervalSeconds：设备时间 / 时区与保存和采样规则。
- days：按设备日期从旧到新排列。每项包含 date、start、end、occupiedMs、emptyMs、uncertainMs、observedMs、firstOccupiedAt、lastOccupiedAt、intervals。
- intervals：已记录时间片，含 start、end、state。时间戳为 Unix 毫秒，毫秒时长单独记录；没有时间片覆盖的部分表示未观察。

occupiedMs / emptyMs 只累计连续确认区间；uncertainMs 是已采样但无法确认的区间；observedMs 为三者之和。首次 / 最后在位取确认在位区间的边界，没有时为零。不可用整日跨度减去 occupiedMs 推导离岗时间。

### 检测框返回字段

`/api/detections` 包含 enabled、captureUptime、boxes。captureUptime 是读取该画面时的设备启动毫秒数，不能当成 Unix 时间。boxes 包含 label、classId、score、left、top、right、bottom，坐标基于已经转正的横向画面，范围 0–1。未知类别与低于 0.35 的结果不输出；没有新鲜框时 boxes 为空。人体类别为 classId=0，与模型附带 labelmap 相同。

接受检测控制请求不等于模型已完成加载；可读取相机状态的 detectionEnabled 和检测接口。与相机 API 一样，控制成功只表示操作已排队。

## 0.9.1 状态修复

- 校准不再是观察的前置条件。有效画面里，区域内人体分数达到 0.35 判为有人，低于阈值判为无人；已有明确的背景锚点仍参与判断。固定预训练模型不变，背景学习仍有边界约束。
- 状态需要三次稳定观察才确认，不把一次没框到人马上算作离开。相机无画面、图像过暗/过曝、后台、休息期未观察或确认过渡保留 unknown；页面用明确原因显示，不伪装成无人。
- 新字段 stateReason 为 observed、confirming、initializing、stale、invalid_frame、disabled、background；requiresCalibration 恒为 false，ready 仍只表示完成可选背景校准。
- 旧图片和历史 unknown 区间不自动改写为无人，避免给训练数据制造不存在的真值。

## 0.9.2 自选时间查询

`GET /api/presence?start=<Unix毫秒>&end=<Unix毫秒>` 以设备时区拆分自然日，start 包含、end 不包含，最长 365 天。两参数必须同时存在且 end>start。每日时间片与累计时长裁剪到查询范围，start/end 仍表示日期边界；返回 rangeStart、rangeEndExclusive 和 todayDate。记录默认保留 90 天，可通过 presenceRetentionDays 修改；查询不会产生或恢复已删除记录。

网页可选开始/结束日期，结束日期包含整日；设备页仍显示最近七天。默认每分钟观察时，工位开启后的观察窗口约 24.5 秒，每 10 秒至多计一次独立统计样本，三次一致才确认。新鲜期随 observationMinutes 调整到间隔加 20 秒，避免正常观察间隔中反复退为未知；无有效画面仍保持 unknown。

关键点与声音叠加仅用于显示，不改变现有工位判断或历史照片标签。
