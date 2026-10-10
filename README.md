# Bloub 桌边 · N5D

一个在 N5D 上独立运行的桌边小伙伴。基于 [Jérémy Perret 的 Bloub](https://github.com/jeremy-prt/bloub)，按屏幕、挖孔和实体灯环的实测尺寸重新编排动作。

[下载 APK](https://github.com/Cyber-Yichen/Bloub-N5D/releases/latest) · [使用与构建](N5D-README.md) · [动画设计](N5D-DESIGN.md) · [开源引用](REFERENCES.md) · [免责声明](DISCLAIMER.md) · [局域网图库与 API](docs/gallery-api.md) · [工位统计与 API](docs/presence-api.md) · [监控与对象存储](docs/monitor-api.md) · [上游说明](docs/UPSTREAM-README.md)

![陪伴设置](docs/n5d-assets/companion.png)

## 它会做什么

- 随机组合完整的小故事，持续运行：八种形状、左右瞟眼、钻洞、洞口小伙伴、吞泡泡长大、彗星、光波、DVD 碰边换色、打盹。形态通常保持约 40 秒。
- 飞向屏幕右侧的实体灯环，化成一段**黑色影子**，七点进入，逆时针转过两圈后继续至十一点，再沿切线飞回屏幕。影子经过的位置同时熄灭 RGB 和白灯。
- 白底黑色、深海薄荷、暖沙棕色、黑底银白和自动换色；切换时柔和过渡。
- 可选音乐耳朵、短时相机观察、ToF 靠近反应。音频只在本机内存分析；观察照片可保存到本机图库，保留 7 天；可选局域网下载、实时预览和网页拍照。
- 点击空白处看过去；按住拖动让目光跟随；轻触 Bot 眨眼并轻轻弹一下。松手后停留片刻，再自然回到剧情。
- 长按打开“陪伴 / 感知 / 图库 / 工位 / 关于”；关于中可打开本项目 GitHub。

![实机点击右上方时的目光](docs/n5d-assets/touch.png)

## 0.9 新增

工位有人会醒来打招呼，无人时主要休息；挥手、问号、惊讶和拿相机均为矢量动画。挖孔故事仅在白底黑色主题下随机出现。灯环缺口羽化过渡，返回的宽光波会推动 Bloub。

网页「监控」可配置无声 MP4 分段及 NAS WebDAV / S3、阿里云 OSS、腾讯云 COS 上传，设备端只放开关。默认关闭；上传失败保留队列，预计空间不足暂停录像。配置与验证范围见 [监控文档](docs/monitor-api.md)。启动图标已适配 Android 圆形等启动器遮罩。

## DVD 漂浮

![N5D 实机 DVD 反弹换色](docs/n5d-assets/dvd.png)

随机探索时会玩约 23 秒的 DVD 漂浮，每次起点、方向和落脚点不同：慢慢起漂、斜向匀速移动、撞到四边后反弹换色，再减速回到原位和原配色。只暂时改变 Bot 颜色，背景与保存的主题不变；不会申请灯环控制。安静陪伴模式跳过这段动作。

![灯环黑色缺口的几何预览](docs/n5d-assets/ring-preview.png)

## 灯环协作

需安装 [N5D RingStudio](https://github.com/Cyber-Yichen/N5D-RingStudio)，并在其“关于”启用其他应用控制。仅在互动时申请公共 API 会话；互动结束、打开设置、切后台或关闭灯光后归还，恢复工坊原灯效和设置。没有服务时保持屏幕内的替代动画。RGB 与白灯以 255 为亮度峰值；黑影核心保持熄灭，边缘和交接平滑变化。长按 → 陪伴 → 灯环位置校准，可测试并保存角度偏移；测试六秒后归还控制。屏幕亮度可调到 100%。

## 本机图库

开启相机与保存后，按工位状态间隔保存完整预览尺寸的 JPEG；有人默认一分钟、无人十分钟，设置可调整。在图库里查看、单张删除、清空或关闭“保存观察”。照片和拍摄时间、方向、工位模型状态存放在应用私有目录，JPEG XMP 与 JSON 都携带标签，超过七天清理；系统休眠可能延迟后台扫描，启动和访问图库时也会清理。人脸分析暂不启用，留待后续小模型训练。

图库支持点开大图、两倍缩放与滑动。夜间休息时间可在感知页修改，默认 23:00–08:00；开启麦克风后可由明显声音唤醒短时观察。

局域网电脑打开设备网址，输入六位访问码后可下载照片、开启实时画面、主动拍照；可按日期、时间段和工位状态导出 ZIP，并在网页修改设备设置；AI 接口、分页与元数据见 [局域网图库文档](docs/gallery-api.md)。新照片按保存顺序显示，避免设备时间回拨后排序异常。

本地数据分析可使用 [图库导出工具](android/gallery-export.mjs)，用法见 [使用说明](N5D-README.md#本机图库与本地分析)。实际办公室照片与诊断记录不提交到仓库。

## 工位观察

选择座位区域，分别确认空位和在位后，设备用小型人体检测模型与本地自适应分类层估算在位时间。设备与网页都有全天时间轴和最近七天时长图；未观察 / 待确认时段单独显示。预训练模型权重固定，工位判断参数在稳定样本上缓慢适应；不区分坐在同一座位的不同人。网页实时画面可开启检测框，显示类别和置信度。使用与数据规则见 [工位统计文档](docs/presence-api.md)。

## 快速开始

下载最新 Release 的 APK，安装并打开 **Bloub 桌边**。点空白看过去、拖动跟随、轻触 Bot 打招呼；长按 650 ms 或 Android 返回键打开设置。

```bash
pnpm install --frozen-lockfile
pnpm dev:n5d
pnpm test
```

Android 构建步骤见 [N5D-README.md](N5D-README.md)。网页开发预览没有硬件权限，感知和灯光需在 N5D 运行 APK。

## 许可与来源

本项目及上游 Bloub 使用 [MIT](LICENSE)，保留 Jérémy Perret 的版权声明。新增 N5D 适配由 Cyber-Yichen 维护。音频模型及 TensorFlow Lite 使用 Apache 2.0，许可随 APK 一起分发。灯环通过独立项目的公共接口控制。

本项目与 xAI、Grok 无隶属关系。动画核心来源与原始参考说明见上游文档。


## 引用的开源项目

动画核心实际复用 [jeremy-prt/bloub](https://github.com/jeremy-prt/bloub)，灯环使用独立的 [N5D-RingStudio](https://github.com/Cyber-Yichen/N5D-RingStudio) 公共接口，音乐感知使用 [TensorFlow YAMNet](https://github.com/tensorflow/models/tree/master/research/audioset/yamnet) 与 TensorFlow Lite。

调研参考包括 [iduu/grokbot-animation](https://github.com/iduu/grokbot-animation)、[nasawz/GrokBot](https://github.com/nasawz/GrokBot)、[Eyadkelleh/Grok_bot](https://github.com/Eyadkelleh/Grok_bot)。这些项目的代码或参考素材没有并入本版。完整依赖、许可及引用关系见 [REFERENCES.md](REFERENCES.md)。

## 免责声明

本项目非官方，与 xAI / Grok 或设备制造商无隶属或背书关系。MIT 许可针对相应代码，不授予第三方角色设计、商标或素材权利。软件按现状提供；感知输出可能误判，不代表真实情绪或身份。详见 [DISCLAIMER.md](DISCLAIMER.md)。
