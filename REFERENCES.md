# 开源来源与参考项目

本页说明每个项目与 Bloub-N5D 的实际关系。公开仓库中的“参考”不等于复制了该项目的代码或素材。

## 实际复用与依赖

| 项目 | 作者 / 维护者 | 本项目用途 | 许可 |
| --- | --- | --- | --- |
| [jeremy-prt/bloub](https://github.com/jeremy-prt/bloub) | Jérémy Perret | 实际复用 src/bot 动画核心、形状、表情、Vue 基础工程与上游测试；N5D 故事、硬件适配和触摸交互在其上新增 | [MIT](https://github.com/jeremy-prt/bloub/blob/main/LICENSE)，保留原版权；导入基线 b4bb3c1b5f93c7b87a2e8d620f667c4093d97749 |
| [Cyber-Yichen/N5D-RingStudio](https://github.com/Cyber-Yichen/N5D-RingStudio) | Cyber-Yichen | 独立安装的灯环服务；调用公共 Messenger API，按其灯位文档生成 RGB / 白灯帧；未把灯光驱动嵌入本 APK | [MIT](https://github.com/Cyber-Yichen/N5D-RingStudio/blob/main/LICENSE)；[接口文档](https://github.com/Cyber-Yichen/N5D-RingStudio/blob/main/docs/control-api.md)、[灯位文档](https://github.com/Cyber-Yichen/N5D-RingStudio/blob/main/docs/light-layout.md) |
| [TensorFlow / TensorFlow Lite](https://github.com/tensorflow/tensorflow) | TensorFlow contributors | APK 内的 TensorFlow Lite 2.14.0 音频推理运行时 | [Apache 2.0](https://github.com/tensorflow/tensorflow/blob/v2.14.0/LICENSE)，许可打包在 android/assets/TENSORFLOW-LICENSE.txt |
| [TensorFlow YAMNet](https://github.com/tensorflow/models/tree/master/research/audioset/yamnet) / [官方 Android 音频示例](https://github.com/tensorflow/examples/tree/master/lite/examples/audio_classification/android) | TensorFlow contributors | 本机音乐 / 语音分类模型与官方类目表；固定移动端模型下载来源 | [Apache 2.0](https://github.com/tensorflow/models/blob/master/LICENSE)；资源来源与 SHA256 见 android/dependencies.json |
| [Vue](https://github.com/vuejs/core) | Vue contributors | 设备界面与动画渲染 | MIT |
| [Vite](https://github.com/vitejs/vite) / [Tailwind CSS](https://github.com/tailwindlabs/tailwindcss) | 各项目 contributors | 前端构建及保留的上游工作室样式工具 | MIT |
| [TypeScript](https://github.com/microsoft/TypeScript) | Microsoft / contributors | 前端类型检查 | Apache 2.0 |
| [Mediabunny](https://github.com/Vanilagy/mediabunny) | Vanilagy / contributors | 保留的上游工作室媒体导出功能；N5D APK 入口不使用该导出模块 | MPL 2.0 |

完整依赖及固定版本见 [package.json](package.json)、[pnpm-lock.yaml](pnpm-lock.yaml)。Android 工具链资源见 [android/dependencies.json](android/dependencies.json)。各依赖的原始许可仍然有效，本项目 MIT 许可不覆盖或改变这些许可。

## 调研参考

| 项目 | 参考内容 | 引入情况 |
| --- | --- | --- |
| [pedroSG94/RootEncoder](https://github.com/pedroSG94/RootEncoder) | Android 相机与视频编码方案；支持 RTMP / RTSP / SRT 等传输 | 仅调研，未导入其代码或依赖；本版采用原生 Camera2 与直接浏览器 MJPEG，独立编码线程发送最新帧 |
| [iduu/grokbot-animation](https://github.com/iduu/grokbot-animation) | 完整动作生命周期、共享时钟、状态与形状分离 | 仅调研。其 README 明确第三方参考素材未获开源许可；本项目未导入其数据包、素材或运行时 |
| [nasawz/GrokBot](https://github.com/nasawz/GrokBot) | Flutter 头像中的表情节奏与随机池 | 仅调研，未引入 Flutter 代码或运行时；仓库标注 BSD-3-Clause |
| [Eyadkelleh/Grok_bot](https://github.com/Eyadkelleh/Grok_bot) | SVG 动作工作室与 Bloub 状态展示 | 仅作为展示参考，未并入代码或素材；不对其许可作未经核实的声明 |

角色视觉来源、商标与使用边界见 [免责声明](DISCLAIMER.md)。感谢上游作者；上述项目的作者并不因此为本项目的新增功能或发布包背书。
