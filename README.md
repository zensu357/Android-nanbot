<h1 align="center">Android Nanobot</h1>

<p align="center">
  <strong>A local AI agent that lives on your Android device.</strong>
</p>

<p align="center">
  <a href="https://github.com/zensu357/Android-nanobot/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/zensu357/Android-nanobot?style=flat-square" alt="License" />
  </a>
  <a href="https://github.com/zensu357/Android-nanobot/stargazers">
    <img src="https://img.shields.io/github/stars/zensu357/Android-nanobot?style=flat-square" alt="Stars" />
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
  Android-native recreation of the <a href="https://github.com/HKUDS/nanobot">HKUDS nanobot</a> agent.<br/>
  Focused on matching the local agent core on Android &mdash; not wrapping a Python runtime.
</p>

<p align="center">
  <a href="./README.zh-CN.md"><strong>中文文档</strong></a>
</p>

---

## Highlights

<table>
<tr>
<td width="50%">

**Complete Agent Loop**

Sense &rarr; Think &rarr; Act &rarr; Remember. A full orchestration cycle with tool-loop execution, context budgeting, and progressive memory disclosure.

</td>
<td width="50%">

**Phone Control**

Operate your device through an Accessibility Service &mdash; read the UI tree, tap, type, scroll, take screenshots, and more. All gated behind signed skill unlock verification.

</td>
</tr>
<tr>
<td>

**Multi-Provider LLM**

OpenAI, Azure OpenAI, OpenRouter &mdash; switch providers on the fly with a unified interface. Custom base URL supported.

</td>
<td>

**Skill Platform**

Discover, import (ZIP), parse (Markdown), and activate skills at runtime. Hidden phone-control skills require publisher-signed manifests and user consent.

</td>
</tr>
<tr>
<td>

**Layered Memory**

Session summaries, current-session facts, long-term user facts. Conflict-aware updates, ranked recall, realtime consolidation with periodic worker fallback.

</td>
<td>

**Extensible Tools**

15+ built-in tools, dynamic MCP tool discovery, workspace sandboxing, and a policy-enforced access control layer.

</td>
</tr>
</table>

---

## Table of Contents

- [Getting Started](#getting-started)
- [Architecture](#architecture)
- [Phone Control](#phone-control)
- [Skills & Hidden Unlock](#skills--hidden-unlock)
- [Tool Catalog](#tool-catalog)
- [Tech Stack](#tech-stack)
- [Contributing](#contributing)
- [License](#license)
- [Acknowledgements](#acknowledgements)

---

## Getting Started

### Requirements

| Dependency | Version |
|------------|---------|
| JDK | 17 |
| Android SDK | 35 (min 26) |
| Gradle | Wrapper included |
| IDE | Android Studio recommended |

### Build

```bash
git clone https://github.com/zensu357/Android-nanobot.git
cd Android-nanobot
```

```bash
# Windows
./gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain

# macOS / Linux
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain
```

---

## Architecture

```
app/src/main/java/com/example/nanobot/
├── core/
│   ├── ai/              # Agent loop, prompt composer, model router, tool-loop executor
│   │   └── provider/    # OpenAI, Azure, OpenRouter adapters
│   ├── memory/          # Fact governance, search scoring, consolidation
│   ├── phonecontrol/    # Accessibility service, UI snapshot, device actions
│   ├── skills/          # Skill discovery, parser, ZIP importer, unlock verification
│   ├── tools/           # Tool interface, registry, access policy
│   │   └── impl/        # 15+ built-in tool implementations
│   ├── mcp/             # MCP client, registry, tool adapter
│   ├── web/             # Web fetch, search, DNS safety, request guard
│   ├── worker/          # Heartbeat, memory consolidation, reminder, session cleanup
│   ├── database/        # Room DAOs & entities
│   └── ...
├── data/                # Repository implementations & mappers
├── domain/              # Repository interfaces & use cases
├── feature/             # Compose UI screens & ViewModels
│   ├── chat/            # Main chat interface
│   ├── memory/          # Memory management UI
│   ├── settings/        # Settings & unlock consent UI
│   ├── sessions/        # Session management
│   ├── tools/           # Tool management
│   └── onboarding/      # First-run guide
├── di/                  # Hilt modules
├── navigation/          # App routes
└── ui/                  # Theme & shared components
```

**Key design choices:**
- **Single module** &mdash; keeps the build simple for an app-scoped project.
- **Clean Architecture** &mdash; `domain/` defines contracts, `data/` implements them, `feature/` consumes them.
- **Coroutines everywhere** &mdash; all I/O and agent turns are suspend functions.
- **Hilt DI** &mdash; every service, tool, and repository is injectable.

---

## Phone Control

The phone control module lets the agent interact with the device UI through Android's Accessibility Service.

### Capabilities

| Tool | Description | Status |
|------|-------------|--------|
| `read_current_ui` | Read the foreground UI tree as structured nodes | Implemented |
| `tap_ui_node` | Tap a node by ID, text, class, view ID (exact / contains / regex) | Implemented |
| `input_text` | Set text on an editable field via `ACTION_SET_TEXT` | Implemented |
| `scroll_ui` | Scroll a container forward / backward; auto-finds scrollable nodes | Implemented |
| `press_global_action` | Back, Home, Recents, Notifications, Quick Settings, Screenshot, Lock, Power | Implemented |
| `launch_app` | Launch any installed app by package name | Implemented |
| `wait_for_ui` | Poll until a text / content-description appears (substring, case-insensitive) | Implemented |
| `perform_ui_action` | Long-click, focus, copy, paste, cut, select, and more | Implemented |
| `take_screenshot` | Capture screen as base64 JPEG for multimodal analysis (API 30+) | Implemented |

### Node Selector

All node-targeting tools share a unified selector with 6 fields and 3 match modes:

```
nodeId | text | contentDescription | className | viewIdResourceName
matchMode: exact (default) | contains | regex
```

### Auto-Snapshot

After every successful action (tap, input, scroll, wait, perform), the tool automatically appends a compact UI snapshot &mdash; eliminating the need for the agent to call `read_current_ui` after each step.

### Security

Phone control tools are **hidden by default**. They are only exposed when:

1. A skill package includes a signed `phone-control.unlock` manifest
2. The app verifies the manifest integrity
3. The user explicitly accepts the consent dialog
4. A local acceptance receipt is stored

See [Skills & Hidden Unlock](#skills--hidden-unlock) for details.

---

## Skills & Hidden Unlock

### Skill Lifecycle

```
ZIP Import  ──>  Directory Scan  ──>  Markdown Parse  ──>  Resource Index  ──>  Catalog
                       │
              phone-control.unlock?
                       │
              ┌────────┴────────┐
              │  Verify & Gate  │
              └────────┬────────┘
                       │
              User Consent Dialog
                       │
              Store Unlock Receipt
```

### Key Design Rules

- **`SKILL.md` explains behavior** &mdash; the agent reads this as its instruction manual.
- **`phone-control.unlock` grants entitlement** &mdash; a signed sidecar that the agent never sees.
- **Local acceptance records user consent** &mdash; the app stores evidence of agreement.
- **Profile-based mapping** &mdash; unlock files reference profile IDs (e.g. `phone_control_basic_v1`), not raw tool names. The host controls what each profile unlocks.

See [`PHONE_CONTROL_UNLOCK_SKILL_DESIGN.md`](./PHONE_CONTROL_UNLOCK_SKILL_DESIGN.md) for the full specification.

---

## Tool Catalog

### Built-in Tools

| Category | Tools |
|----------|-------|
| **Workspace** | `list_workspace`, `read_file`, `write_file`, `replace_in_file`, `search_workspace` |
| **Web** | `web_fetch`, `web_search` |
| **Memory** | `memory_lookup` |
| **Orchestration** | `delegate_task`, `activate_skill`, `read_skill_resource` |
| **Notification** | `notify_user`, `schedule_reminder` |
| **Device** | `device_time`, `session_snapshot` |
| **Phone Control** | `read_current_ui`, `tap_ui_node`, `input_text`, `scroll_ui`, `press_global_action`, `launch_app`, `wait_for_ui`, `perform_ui_action`, `take_screenshot` |

### Dynamic MCP Tools

The app discovers and caches tools from remote MCP servers. MCP tools appear alongside built-in tools and follow the same access policy.

### Access Policy

| Mode | Allowed |
|------|---------|
| **Unrestricted** | All registered tools |
| **Workspace-restricted** | Local read-only, local orchestration, workspace read/write only |

Hidden tools (`HIDDEN_UNLOCKABLE`) require an active unlock receipt in `AgentRunContext.unlockedToolNames` to become visible.

---

## Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt (Dagger) |
| Database | Room |
| Preferences | DataStore |
| Networking | Retrofit + OkHttp |
| Serialization | kotlinx.serialization + SnakeYAML |
| Background | WorkManager |
| Target | SDK 35 &mdash; Min SDK 26 |

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Run the verification command above
4. Commit and push
5. Open a Pull Request

---

## License

Apache License 2.0 &mdash; see [`LICENSE`](./LICENSE).

---

## Acknowledgements

- [`nanobot`](https://github.com/HKUDS/nanobot) by HKUDS &mdash; the reference design target
- Android Jetpack, Jetpack Compose, Room, WorkManager, Hilt, OkHttp, kotlinx.serialization
