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
  <strong>Android-native recreation of the HKUDS nanobot agent</strong>
  <br />
  Focused on matching the local agent core on Android rather than wrapping a Python runtime.
  <br />
  <a href="./CURRENT_STATUS.md"><strong>Current Status</strong></a>
  ·
  <a href="./DEVELOPMENT_PROGRESS_AND_ROADMAP.md"><strong>Roadmap</strong></a>
  ·
  <a href="./README.zh-CN.md"><strong>中文文档</strong></a>
</p>

## Table of Contents

- [Getting Started](#getting-started)
  - [Requirements](#requirements)
  - [Build and Verification](#build-and-verification)
- [Repository Structure](#repository-structure)
- [Architecture](#architecture)
- [Current Capabilities](#current-capabilities)
- [Current Boundaries](#current-boundaries)
- [Tech Stack](#tech-stack)
- [Contributing](#contributing)
- [Versioning](#versioning)
- [License](#license)
- [Acknowledgements](#acknowledgements)

## Getting Started

Android Nanobot is a single-module Android app that recreates the core local-agent behavior of Python `nanobot` on Android.

### Requirements

- JDK 17
- Android SDK 35
- Gradle wrapper included in the repository
- Android Studio recommended for local development

### Build and Verification

1. Clone the repository:

```bash
git clone https://github.com/zensu357/Android-nanobot.git
```

2. Run the standard verification commands from the repository root:

```bash
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain -Dorg.gradle.cache.internal.locklistener.port=0
```

On macOS or Linux, use `./gradlew` instead of `./gradlew.bat`.

## Repository Structure

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

## Architecture

- Single-module Android application
- `core/`: agent loop, providers, tools, MCP, workspace, web access, workers, persistence models
- `data/`: repository implementations and mappers
- `domain/`: repository interfaces and use cases
- `feature/`: Compose screens and ViewModels
- `navigation/`: app routes and navigation graph

## Current Capabilities

- Persistent chat sessions with Room-backed history
- Prompt presets, runtime metadata, and memory-aware prompting
- Policy-enforced `workspace-restricted mode`
- Workspace sandbox read/write tools inside `workspace:/`
- Public web read-only tools with network safety checks
- Local orchestration through `delegate_task`
- Dynamic MCP tools with cached discovery and partial-refresh retention
- Background heartbeat and reminder automation
- Image attachments stored in app-private storage and delivered through the OpenAI-compatible multimodal path

## Current Boundaries

- Multimodal support is currently image-only
- Provider-side attachment delivery is currently implemented only for the OpenAI-compatible path
- MCP is remote HTTP-oriented only; there is no local stdio server runtime or marketplace flow
- Channels / bridge / CLI ecosystem layers are not implemented in this Android project

## Tech Stack

- Kotlin 2.0.21
- Jetpack Compose + Material 3
- Room
- DataStore
- Hilt
- WorkManager
- Retrofit / OkHttp
- kotlinx.serialization

## Contributing

Contributions are welcome.

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push the branch to your fork
5. Open a pull request

Before submitting changes, run the local verification command shown above.

## Versioning

This project uses Git for version control. The primary development branch is `main`.

## License

This project is licensed under the Apache License 2.0. See `LICENSE` for details.

## Acknowledgements

- [`nanobot`](https://github.com/HKUDS/nanobot) by HKUDS, as the reference design target
- Android Jetpack and Jetpack Compose
- Room, WorkManager, Hilt, OkHttp, and kotlinx.serialization

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
