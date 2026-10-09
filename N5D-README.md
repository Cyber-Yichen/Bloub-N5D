# Bloub 桌边 · 0.3.0 使用与构建

项目：[Cyber-Yichen/Bloub-N5D](https://github.com/Cyber-Yichen/Bloub-N5D)。上游基线 b4bb3c1b5f93c7b87a2e8d620f667c4093d97749，保留 MIT 许可。

## 使用

安装 [Release](https://github.com/Cyber-Yichen/Bloub-N5D/releases/latest) 的 APK，打开 **Bloub 桌边**。轻触打招呼，长按 650 ms 打开设置；Android 返回键也可打开或收起。

- **陪伴**：自由探索或安静陪伴；灯环互动；亮度 15%–75%；四套配色和自动轮换。默认白底黑 Bot、亮度 42%。手动换色渐变 3 s，自动每三分钟轮换、渐变 12 s。
- **感知**：音乐耳朵、偶尔看看、靠近感应。首次安装均关闭，需要时独立开启。
- **关于**：版本、许可、上游来源，以及本项目 GitHub 入口。交给系统浏览器打开；没有浏览器时复制链接。
- 打开设置时暂停故事并归还灯环；收起后继续。前台保持亮屏，切后台暂停故事、停止感知并归还灯光。
- 一轮六分钟持续循环，不需要电脑。未来本地状态接口目前为 window.companion.setState(state,duration)，支持 idle/thinking/success/attention/sleep；没有电脑端监听器。

## 新的两圈动画

环的光带中线半径 32.8 mm。Bot 在 156–176 s 以约 **20.6 mm/s** 转两圈，每圈 10 s。飞入、圆周、飞出共用一个时钟和整机坐标，交接匹配位置、切线速度和向心加速度。

Bot 在环上表现为黑色缺口：周围点亮，缺口核心同时关闭交错的 RGB 和白灯，边缘逐渐变暗。屏幕与灯环之间的实体间隔按实测保留，不把动画压缩到屏幕边缘。

## 灯环 API

依赖 [RingStudio 公共 API v1](https://github.com/Cyber-Yichen/N5D-RingStudio/blob/main/docs/control-api.md)。开启工坊“关于”中的其他应用控制。每轮仅 149–187 s、219–240 s 请求会话，以 10 fps 完整帧续租，10 s 租约。

退出互动或切后台 RELEASE(resume_local=true)，恢复工坊原效果、Logo 与设置；超过 1.8 s 没有新帧也释放。接管和归还前渐变。24 RGB / 24 白灯以物理顺序生成，白灯偏 7.5°，工坊处理 BGR 接线。检查映射 ID n5d-clockwise-2026-10；忙碌或失去所有权时本段故事不会反复抢占。

## 感知能力

声音与画面只存在本机内存，APK 没有网络权限。

| 功能 | 实现与边界 |
| --- | --- |
| 音乐耳朵 | YAMNet 4.1 MB，16 kHz、0.975 s 窗口，约每 1.1 s 分类。音乐驱动左右摆动和音符，语音驱动倾听表情。普通陪伴时叠加，洞口与灯环故事保持完整。 |
| 偶尔看看 | Camera2 ID 2，每两分钟约 5 s；小尺寸图像做帧差与系统人脸检测、目光方向。无身份识别，不推断真实情绪。 |
| 靠近感应 | 固定 ToF 节点，只接受 status=0 的新鲜有效距离。普通权限失败时尝试固定只读 Magisk helper；需本应用的实际 Root 授权。没有授权时显示不可用，不影响动画。 |
| NPU / APU | 尝试 apunn NNAPI，自测失败回退 CPU。当前实机仅 2/47 算子被 NNAPI 接收，39/47 使用 XNNPACK；不是整模型 NPU 执行。 |

音乐模型真实麦克风推理和相机短时出帧已验证；连续歌曲、人脸方向与 ToF 手掌的现场正样本尚未全部验证，模拟画面检查不等于真实识别成功。

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

每个依赖的来源、版本和 SHA256 固定在 android/dependencies.json 与 android/dependencies.sha256。构建前验证哈希；输出 android/dist/Bloub-N5D-0.3.0.apk 和 SHA256SUMS.txt。minSdk23、targetSdk27，打包 arm64-v8a TFLite。

开发签名密钥会在本机 android/toolchain/development.jks 自动生成，不提交。不同人的本地开发签名不同；发布包使用维护者的同一签名。产物、工具链、模型下载缓存、设备诊断报告均按 .gitignore 排除；模型与附带许可作为明确的运行资源保留。

## 设备诊断

Android 清单保留 WebView 调试，诊断时间轴仅在明确的 ADB diagnostic 参数下开启。需要 Node 22+、ADB：

```text
adb shell am force-stop com.cyberyichen.bloub
adb shell am start -n com.cyberyichen.bloub/.MainActivity --ez diagnostic true
node android/inspect.mjs v03
```

ADB 可执行文件可通过 ADB 环境变量指定；多设备连接时指定 N5D_SERIAL。v03 检查三个页面无裁切、环上六个位置的 RGB/白灯核心输出为零、周围点亮及设置释放会话。旧的 story / themes 检查入口仍可使用。整机预览启动时额外添加 --ez rig true，再运行 inspect.mjs rig。

诊断使用电脑本地端口 19222。结束后执行 adb forward --remove tcp:19222，并重新启动普通入口。

## 验证

223 项测试通过，包含两个完整循环、八种形状、两圈匀速、飞行与圆周 C2 交接、双灯列黑色缺口、泡泡增长、动作最短时长、感知叠加范围。设备验证证据记录在本地忽略目录 n5d-reports，不把现场音频、图像或设备地址上传到项目。

入环前 Bot 轮廓半径缩至约 70 px / 6.6 mm，匹配黑色缺口的核心尺寸；环上位置和大小由同一几何约束决定。
