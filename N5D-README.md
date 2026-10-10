# Bloub 桌边 · 0.7.0 使用与构建

项目：[Cyber-Yichen/Bloub-N5D](https://github.com/Cyber-Yichen/Bloub-N5D)。上游基线 b4bb3c1b5f93c7b87a2e8d620f667c4093d97749，保留 MIT 许可。

## 使用

安装 [Release](https://github.com/Cyber-Yichen/Bloub-N5D/releases/latest) 的 APK，打开 **Bloub 桌边**。轻触打招呼，长按 650 ms 打开设置；Android 返回键也可打开或收起。

- **陪伴**：自由探索或安静陪伴；灯环互动；亮度 15%–100%；四套配色和自动轮换。默认白底黑 Bot、亮度 42%。手动换色渐变 3 s，自动每三分钟轮换、渐变 12 s。
- **感知**：音乐耳朵、偶尔看看、靠近感应。首次安装均关闭，需要时独立开启。
- **图库**：照片保留七天，查看、单张删除、清空或关闭保存；可选局域网电脑下载、实时预览和拍照。
- **关于**：版本、许可、上游来源，以及本项目 GitHub 入口。交给系统浏览器打开；没有浏览器时复制链接。
- 打开设置时暂停故事并归还灯环；收起后继续。前台保持亮屏，切后台暂停故事、停止感知并归还灯光。
- 按洗牌袋随机选择完整段落，中间休息 8–24 秒并更换落脚点，避免立即重复；灯光段落有至少 35 秒间隔。不需要电脑。六分钟时间轴只作为开发诊断的标准片段来源。未来本地状态接口目前为 window.companion.setState(state,duration)，支持 idle/thinking/success/attention/sleep；没有电脑端监听器。

## 屏幕交互

- 点空白处：Bloub 转头看向触点，触点出现淡涟漪。
- 按住拖动：目光跟随手指；松手后停留约 3.2 s，再平滑回到剧情目光。
- 轻触 Bloub：眨眼并轻轻弹一下；已有一次性变形会先完整播放。
- 长按 650 ms 打开设置；取消手势、打开设置或离开屏幕后不会留下持续跟随。
- 触点按实际缩放和留边换算到 1600 × 720 坐标；洞口和画布外的输入忽略。命中按当前实际 SVG 轮廓判断，不把整个透明头像框当成 Bot。
- 跟随只控制目光，不改变灯环轨迹或申请额外灯光会话。

## 新的两圈动画

环的光带中线半径 32.8 mm。Bot 在 156–176 s 以约 **20.6 mm/s** 转两圈，每圈 10 s。飞入、圆周、飞出共用一个时钟和整机坐标，交接匹配位置、切线速度和向心加速度。

Bot 在环上表现为黑色缺口：周围点亮，缺口核心同时关闭交错的 RGB 和白灯，边缘逐渐变暗。屏幕与灯环之间的实体间隔按实测保留，不把动画压缩到屏幕边缘。

## 灯环 API

依赖 [RingStudio 公共 API v1](https://github.com/Cyber-Yichen/N5D-RingStudio/blob/main/docs/control-api.md)。开启工坊“关于”中的其他应用控制。每次仅在随机选中的灯环转圈或光波片段内请求会话（标准诊断时间轴为 149–187 s、219–240 s），以 10 fps 完整帧续租，10 s 租约。

退出互动或切后台 RELEASE(resume_local=true)，恢复工坊原效果、Logo 与设置；超过 1.8 s 没有新帧也释放。接管和归还前渐变。24 RGB / 24 白灯以物理顺序生成，白灯偏 7.5°，工坊处理 BGR 接线。检查映射 ID n5d-clockwise-2026-10；忙碌或失去所有权时本段故事不会反复抢占。

## 感知能力

音频仅在内存分析；相机观察可保存到本机图库，保留七天。可单独开启带六位访问码的局域网图库服务，不主动上传云端。

| 功能 | 实现与边界 |
| --- | --- |
| 音乐耳朵 | YAMNet 4.1 MB，16 kHz、0.975 s 窗口，约每 1.1 s 分类。音乐驱动左右摆动和音符，语音驱动倾听表情。普通陪伴时叠加，洞口与灯环故事保持完整。 |
| 偶尔看看 | Camera2 ID 2 / 3 可选，每两分钟约 5 s；小尺寸图像只做帧差，人脸分析暂不启用。保存使用完整预览尺寸 JPEG，相机 2 为 960×1280，相机 3 为 1280×960。无身份识别，不推断真实情绪。 |
| 靠近感应 | 固定 ToF 节点，只接受 status=0 的新鲜有效距离。普通权限失败时尝试固定只读 Magisk helper；需本应用的实际 Root 授权。没有授权时显示不可用，不影响动画。 |
| NPU / APU | 尝试 apunn NNAPI，自测失败回退 CPU。当前实机仅 2/47 算子被 NNAPI 接收，39/47 使用 XNNPACK；不是整模型 NPU 执行。 |

音乐模型真实麦克风推理和相机短时出帧已验证；连续歌曲与 ToF 手掌的现场正样本尚未全部验证，模拟画面检查不等于真实识别成功。

## 开发预览

需要 Linux / WSL 的 Node 24+、pnpm 11.17.0：

```bash
pnpm install --frozen-lockfile
pnpm dev:n5d
pnpm build:n5d
pnpm test
```

普通入口 /n5d.html；整机几何预览 /n5d.html?debug=1&rig=1。默认画布 1600 × 720，按窗口等比缩放。普通入口不暴露时间轴控制。

## Android 构建

需要 JDK 11、Python 3、Android platform 23、aapt、zipalign、apksigner。build.sh 默认使用 Linux 系统工具；可用 ANDROID_JAR、JAVA_HOME、R8_JAR 指定路径，并把所需 Android build-tools 加入 PATH。

先下载固定模型及运行时：
```bash
python3 android/fetch-dependencies.py
bash android/build.sh
```

WSL 网络不可用时，可以在同一个 checkout 通过 Windows 下载，再回 WSL 构建：
```powershell
powershell -ExecutionPolicy Bypass -File android/fetch-model.ps1
```

每个依赖的来源、版本和 SHA256 固定在 android/dependencies.json 与 android/dependencies.sha256。构建前验证哈希；输出 android/dist/Bloub-N5D-0.7.0.apk 和 SHA256SUMS.txt。minSdk23、targetSdk27，打包 arm64-v8a TFLite。

开发签名密钥会在本机 android/toolchain/development.jks 自动生成，不提交。不同人的本地开发签名不同；发布包使用维护者的同一签名。产物、工具链、模型下载缓存、设备诊断报告均按 .gitignore 排除；模型与附带许可作为明确的运行资源保留。

## 设备诊断

Android 清单保留 WebView 调试，诊断时间轴仅在明确的 ADB diagnostic 参数下开启。需要 Node 22+、ADB：

```text
adb shell am force-stop com.cyberyichen.bloub
adb shell am start -n com.cyberyichen.bloub/.MainActivity --ez diagnostic true
node android/inspect.mjs v03
```

ADB 可执行文件可通过 ADB 环境变量指定；多设备连接时指定 N5D_SERIAL。v03 检查四个页面无裁切、环上六个位置的 RGB/白灯核心输出为零、周围点亮及设置释放会话。旧的 story / themes 检查入口仍可使用。整机预览启动时额外添加 --ez rig true，再运行 inspect.mjs rig。

诊断使用电脑本地端口 19222。结束后执行 adb forward --remove tcp:19222，并重新启动普通入口。

## 验证

236 项测试通过，包含两个完整循环、八种形状、两圈匀速、飞行与圆周 C2 交接、双灯列黑色缺口、泡泡增长、动作最短时长、感知叠加范围。设备验证证据记录在本地忽略目录 n5d-reports，不把现场音频、图像或设备地址上传到项目。

入环前 Bot 轮廓半径缩至约 70 px / 6.6 mm，匹配黑色缺口的核心尺寸；环上位置和大小由同一几何约束决定。

开源引用与第三方许可见 [REFERENCES.md](REFERENCES.md)，项目边界见 [DISCLAIMER.md](DISCLAIMER.md)。

## DVD 碰边换色 · 0.6

随机选择 DVD 段落后，用 2 秒起漂、17 秒反弹、4 秒归位。每次随机起点和斜向速度（水平 145–200、垂直 85–125 px/s），轨迹生成时检查屏幕四边及挖孔安全距离；回到本次出发的落脚点。反弹轨迹按固定时间采样，与帧率无关。身体保留安全边界并避开挖孔；起漂、匀速段和归位采用位置、速度、加速度连续的五次曲线。碰边的 Bot 颜色在 180 ms 内过渡，连续碰到两边时从当前色接续；归位过程中淡回所选主题。背景不变、不保存临时色、不占用灯环。安静模式跳过；音乐额外位移在此期间暂停。

诊断模式可使用 android/inspect.mjs random-dvd（随机轨迹）或 android/inspect.mjs dvd（标准参考轨迹） 验证完整片段的实际渲染、配色恢复及灯环未占用。

## 本机图库与本地分析

图库默认允许保存；相机仍需在“感知”里单独开启。每次约五秒的观察，等图像稳定后保留一张完整预览尺寸 JPEG 和 JSON 元数据（capturedAt、captureOrder、cameraId、rotationClockwise、faceAnalysis、faces、motion、width、height）。每页十二张，支持查看、翻页、单张删除、清空、停止保存。关闭保存不自动删除已有照片。

以设备时钟计算七天期限。保存、启动、列表和图片读取时清理，并安排每小时后台扫描；不开屏、不唤醒设备。关机或系统休眠可能推迟物理删除；再次运行或查看图库时会清理过期文件。卸载应用会删除私有图库。

照片没有上传接口。当前收集照片、时间和画面变化数据，供后续小模型训练。新照片 faceAnalysis=false，faces=0 仅兼容旧接口，不表示画面中无人；当前不做人脸分析。通过已连接的 ADB 导出到电脑本地：

```text
node android/gallery-export.mjs --limit=12
```

不提供 --limit 时导出所有未过期记录；ADB / N5D_SERIAL 的设置与 inspect.mjs 相同。导出位于 Git 忽略的 n5d-reports/gallery/<时间>/，含原始 JPEG 和 manifest.json；分析时按 rotationClockwise 旋转。相机 2 和旧照片为 90°，相机 3 当前横向预览为 0°。图库缩略图与大图按元数据方向显示。导出使用设备时钟筛选，不修改设备图库。电脑副本不会跟随设备自动删除，请自行管理；不要把办公室照片提交到公开仓库。

## 随机与灵动 · 0.6

八种段落从洗牌袋抽取，避免立即重复；休息期间用四秒平滑移动到新落脚点，完整故事从当前落脚点开始并回到那里。洞口、屏幕边缘和灯环交接仍锁定实测坐标。灯光片段间隔至少 35 秒。形状常驻约 40 秒，泡泡后的六边形多停留一会儿；普通陪伴时眼睛随机左右瞟，手指和感知反应平滑接入。

灯环 RGB/白灯最大亮度均为 255；黑影为零，边缘和接管渐变保留。陪伴页底部“灯环位置校准”提供 -180° 至 180° 偏移，RGB 和交错白灯一起移动；面对设备测试左侧 9 点方向，六秒后自动归还，默认零偏移。

## 图库与设置 · 0.7

设置放大字号，保留各项功能说明；图库改为六列两行，点开大图支持两倍缩放与滑动，删除按钮红底白字。感知页可选择相机 2 / 3，并修改夜间休息起止时间，默认 23:00–08:00；麦克风检测到近期明显声音时允许短时自动观察，网页手动拍照不受休息时段限制。

图库 → 电脑下载可开启局域网服务；电脑输入六位访问码即可看图、下载单张或 ZIP、实时预览和拍照。服务跟随前台。接口、方向、游标和流式性能说明见 [图库与相机 API](docs/gallery-api.md)。新照片按递增 captureOrder 排序，设备时间回拨后仍显示在前。

MJPEG 改为独立编码线程和最新帧发送；读取纹理、帧差与编码分开调度。相机 2 单客户端、动画正常运行时实测约 24.2 fps，读取到电脑收到数据中位约 56 ms、P95 约 77 ms；设置打开时约 28.9 fps，另一轮网络波动时 P95 曾达到 221 ms；尚有无线网络波动，不代表完整相机到网页显示延迟。人脸分析保持关闭。
