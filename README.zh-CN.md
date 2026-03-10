<h1 align="center">Android Nanobot</h1>

<p align="center">
  <a href="https://github.com/zensu357/Android-nanobot/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/zensu357/Android-nanobot?style=flat-square" alt="License" />
  </a>
  <a href="https://github.com/zensu357/Android-nanobot/stargazers">
    <img src="https://img.shields.io/github/stars/zensu357/Android-nanobot?style=flat-square" alt="Stars" />
  </a>
  <a href="https://github.com/zensu357/Android-nanobot/network/members">
    <img src="https://img.shields.io/github/forks/zensu357/Android-nanobot?style=flat-square" alt="Forks" />
  </a>
  <a href="https://github.com/zensu357/Android-nanobot/issues">
    <img src="https://img.shields.io/github/issues/zensu357/Android-nanobot?style=flat-square" alt="Issues" />
  </a>
  <a href="https://kotlinlang.org/">
    <img src="https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&amp;logo=kotlin&amp;logoColor=white" alt="Kotlin" />
  </a>
  <a href="https://developer.android.com/">
    <img src="https://img.shields.io/badge/Android-SDK%2035-3DDC84?style=flat-square&amp;logo=android&amp;logoColor=white" alt="Android" />
  </a>
</p>

<p align="center">
  <strong>HKUDS nanobot agent 的 Android 原生复刻</strong>
  <br />
  项目聚焦在 Android 上复刻本地 agent 核心行为，而不是包装一个 Python 运行程序。
  <br />
  <a href="./CURRENT_STATUS.zh-CN.md"><strong>当前状态</strong></a>
  ·
  <a href="./DEVELOPMENT_PROGRESS_AND_ROADMAP.zh-CN.md"><strong>路线图</strong></a>
  ·
  <a href="./README.md"><strong>English Docs</strong></a>
</p>

## 目录

- [上手指南](#上手指南)
  - [开发环境要求](#开发环境要求)
  - [构建与验证](#构建与验证)
- [仓库结构](#仓库结构)
- [项目架构](#项目架构)
- [当前能力](#当前能力)
- [当前边界](#当前边界)
- [技术栈](#技术栈)
- [参与贡献](#参与贡献)
- [版本管理](#版本管理)
- [开源协议](#开源协议)
- [鸣谢](#鸣谢)

## 上手指南

Android Nanobot 是一个单模块 Android 应用，目标是在 Android 上复刻 Python `nanobot` 的本地 agent 核心能力。

### 开发环境要求

- JDK 17
- Android SDK 35
- 仓库内已自带 Gradle Wrapper
- 推荐使用 Android Studio 进行本地开发

### 构建与验证

1. 克隆仓库：

```bash
git clone https://github.com/zensu357/Android-nanobot.git
```

2. 在仓库根目录执行标准验证命令：

```bash
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain -Dorg.gradle.cache.internal.locklistener.port=0
```

在 macOS 或 Linux 上，请把 `./gradlew.bat` 替换为 `./gradlew`。

## 仓库结构

```text
.
├── app/
├── gradle/
├── scripts/
├── CURRENT_STATUS.md
├── CURRENT_STATUS.zh-CN.md
├── DEVELOPMENT_PROGRESS_AND_ROADMAP.md
├── DEVELOPMENT_PROGRESS_AND_ROADMAP.zh-CN.md
├── LICENSE
├── README.md
├── README.zh-CN.md
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
└── settings.gradle.kts
```

## 项目架构

- 单模块 Android 应用
- `core/`：agent loop、provider、tool、MCP、workspace、web access、worker、持久化模型
- `data/`：repository 实现与 mapper
- `domain/`：repository 接口与 use case
- `feature/`：Compose 页面与 ViewModel
- `navigation/`：应用路由与导航图

## 当前能力

- 基于 Room 的持久化聊天会话历史
- Prompt preset、runtime metadata 与 memory-aware prompting
- 由策略层强制执行的 `workspace-restricted mode`
- `workspace:/` 内的沙箱读写工具
- 带网络安全约束的公网只读 web 工具
- 通过 `delegate_task` 实现的本地编排能力
- 带缓存发现与局部刷新保留能力的动态 MCP 工具
- Heartbeat 与 reminder 后台自动化
- 存储于应用私有目录、并能走 OpenAI-compatible 多模态路径的图片附件

## 当前边界

- 多模态目前只支持图片
- Provider 侧附件投递目前只接通了 OpenAI-compatible 路径
- MCP 目前只支持远程 HTTP 形态，不支持本地 stdio server 或 marketplace 流程
- Channels / bridge / CLI 生态层尚未在这个 Android 项目中实现

## 技术栈

- Kotlin 2.0.21
- Jetpack Compose + Material 3
- Room
- DataStore
- Hilt
- WorkManager
- Retrofit / OkHttp
- kotlinx.serialization

## 参与贡献

欢迎贡献。

1. Fork 本仓库
2. 创建功能分支
3. 提交你的修改
4. 推送到你的 fork
5. 发起 Pull Request

在提交修改前，请先运行上面的本地验证命令。

## 版本管理

本项目使用 Git 进行版本管理，主开发分支为 `main`。

## 开源协议

本项目采用 Apache License 2.0，详情见 `LICENSE`。

## 鸣谢

- HKUDS 的 [`nanobot`](https://github.com/HKUDS/nanobot)，作为本项目的设计参考目标
- Android Jetpack 与 Jetpack Compose
- Room、WorkManager、Hilt、OkHttp 与 kotlinx.serialization

[license-shield]: https://img.shields.io/github/license/zensu357/Android-nanobot?style=flat-square
[license-url]: https://github.com/zensu357/Android-nanobot/blob/main/LICENSE
[stars-shield]: https://img.shields.io/github/stars/zensu357/Android-nanobot?style=flat-square
[stars-url]: https://github.com/zensu357/Android-nanobot/stargazers
[forks-shield]: https://img.shields.io/github/forks/zensu357/Android-nanobot?style=flat-square
[forks-url]: https://github.com/zensu357/Android-nanobot/network/members
[issues-shield]: https://img.shields.io/github/issues/zensu357/Android-nanobot?style=flat-square
[issues-url]: https://github.com/zensu357/Android-nanobot/issues
[kotlin-shield]: https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white
[kotlin-url]: https://kotlinlang.org/
[android-shield]: https://img.shields.io/badge/Android-SDK%2035-3DDC84?style=flat-square&logo=android&logoColor=white
[android-url]: https://developer.android.com/
