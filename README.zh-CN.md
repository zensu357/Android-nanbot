<h1 align="center">Android Nanobot</h1>

<p align="center">
  <strong>一个真正运行在 Android 设备上的本地 AI Agent。</strong>
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
  <a href="https://github.com/HKUDS/nanobot">HKUDS nanobot</a> agent 的 Android 原生复刻项目。<br/>
  目标不是包装一个 Python 运行时，而是在 Android 上实现完整的本地 Agent 核心能力。
</p>

<p align="center">
  <a href="./README.md"><strong>English Docs</strong></a>
</p>

---

## 项目亮点

<table>
<tr>
<td width="50%">

**完整 Agent Loop**

从感知、思考、工具调用到记忆写回，形成完整闭环。包含 tool-loop、上下文预算、渐进式记忆暴露等核心能力。

</td>
<td width="50%">

**手机操控能力**

基于 Accessibility Service 读取 UI 树、点击、输入、滚动、截图，并通过签名技能 + 用户同意实现隐藏能力解锁。

</td>
</tr>
<tr>
<td>

**多 Provider LLM**

支持 OpenAI、Azure OpenAI、OpenRouter 等 Provider，并统一到同一套调用接口中，也支持自定义 base URL。

</td>
<td>

**技能平台**

支持技能发现、Markdown 解析、ZIP 导入、运行时激活。手机控制类技能通过 `phone-control.unlock` sidecar 进行授权。

</td>
</tr>
<tr>
<td>

**分层记忆系统**

包含 session summary、当前会话 facts、长期用户 facts，支持冲突治理、排序检索、实时 consolidation。

</td>
<td>

**可扩展工具体系**

内置 15+ 工具，支持动态 MCP 工具发现、workspace 沙箱与策略控制。

</td>
</tr>
</table>

---

## 目录

- [快速开始](#快速开始)
- [项目架构](#项目架构)
- [手机操控](#手机操控)
- [技能与隐藏解锁](#技能与隐藏解锁)
- [工具体系](#工具体系)
- [技术栈](#技术栈)
- [参与贡献](#参与贡献)
- [开源协议](#开源协议)
- [鸣谢](#鸣谢)

---

## 快速开始

### 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 17 |
| Android SDK | 35（最低 26） |
| Gradle | 仓库已自带 Wrapper |
| IDE | 推荐 Android Studio |

### 构建

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

## 项目架构

```text
app/src/main/java/com/example/nanobot/
├── core/
│   ├── ai/              # Agent loop、prompt 组装、模型路由、tool-loop 执行器
│   │   └── provider/    # OpenAI / Azure / OpenRouter 适配层
│   ├── memory/          # Fact 治理、检索评分、记忆 consolidation
│   ├── phonecontrol/    # 无障碍服务、UI 快照、手机控制动作
│   ├── skills/          # 技能发现、解析、ZIP 导入、隐藏解锁验证
│   ├── tools/           # Tool 接口、注册表、访问策略
│   │   └── impl/        # 15+ 内置工具实现
│   ├── mcp/             # MCP client、注册表、tool adapter
│   ├── web/             # Web 抓取、搜索、DNS 安全、请求守卫
│   ├── worker/          # Heartbeat、记忆合并、提醒、会话清理
│   ├── database/        # Room DAO 与实体
│   └── ...
├── data/                # Repository 实现与 mapper
├── domain/              # Repository 接口与 use case
├── feature/             # Compose 页面与 ViewModel
│   ├── chat/            # 主聊天界面
│   ├── memory/          # 记忆管理 UI
│   ├── settings/        # 设置与解锁同意 UI
│   ├── sessions/        # 会话管理
│   ├── tools/           # 工具管理
│   └── onboarding/      # 初始引导
├── di/                  # Hilt 模块
├── navigation/          # 应用导航
└── ui/                  # 主题与公共组件
```

### 架构特点

- **单模块应用**：对 App 形态更直接，构建成本低。
- **Clean Architecture 分层**：`domain/` 定义契约，`data/` 提供实现，`feature/` 负责消费。
- **全协程化 I/O**：网络、数据库、Agent turn 统一使用 suspend 形式。
- **Hilt 依赖注入**：Provider、Tool、Repository、Service 都可独立注入与替换。

---

## 手机操控

手机操控模块通过 Android 的 Accessibility Service 让 Agent 直接与前台 UI 交互。

### 当前能力

| Tool | 说明 | 状态 |
|------|------|------|
| `read_current_ui` | 读取前台 UI 树，输出结构化节点 | 已实现 |
| `tap_ui_node` | 按节点 ID / 文本 / 类名 / viewId 点击控件 | 已实现 |
| `input_text` | 对可编辑节点执行 `ACTION_SET_TEXT` | 已实现 |
| `scroll_ui` | 向前 / 向后滚动，可自动寻找 scrollable 节点 | 已实现 |
| `press_global_action` | 返回、主页、最近任务、通知栏、快捷设置、锁屏、截图、电源菜单 | 已实现 |
| `launch_app` | 按包名启动任意已安装应用 | 已实现 |
| `wait_for_ui` | 轮询等待指定文本 / contentDescription 出现 | 已实现 |
| `perform_ui_action` | 长按、聚焦、复制、粘贴、剪切、选择等高级动作 | 已实现 |
| `take_screenshot` | 截图并输出 base64 JPEG，供多模态理解（API 30+） | 已实现 |

### 节点选择器

所有节点操作类工具共用统一选择器：

```text
nodeId | text | contentDescription | className | viewIdResourceName
matchMode: exact（默认） | contains | regex
```

### 自动回传 UI 快照

每次成功执行点击、输入、滚动、等待或高级动作后，系统都会自动补一份精简 UI 快照，减少 Agent 反复调用 `read_current_ui` 的次数。

### 安全模型

手机操控工具默认 **不会暴露给模型**。它们只有在满足以下条件时才会出现：

1. 技能包中包含合法的 `phone-control.unlock`
2. 宿主应用完成验证
3. 用户在本地明确同意授权文本
4. 本地保存了解锁 receipt

---

## 技能与隐藏解锁

### 技能导入流程

```text
ZIP 导入  ──>  目录扫描  ──>  Markdown 解析  ──>  资源索引  ──>  Catalog
                    │
          是否存在 phone-control.unlock
                    │
             ┌──────┴──────┐
             │  验证与授权  │
             └──────┬──────┘
                    │
               用户同意弹窗
                    │
              保存本地 receipt
```

### 设计规则

- **`SKILL.md` 负责说明如何做**：模型看到的是技能说明书。
- **`phone-control.unlock` 负责授权**：这是给宿主校验的 sidecar，不会暴露给模型。
- **本地同意负责记录用户选择**：receipt 保存的是本地接受证据。
- **Profile 负责映射能力集**：技能包声明的是 profile ID，而不是任意内部工具名。

详细设计见 [`PHONE_CONTROL_UNLOCK_SKILL_DESIGN.md`](./PHONE_CONTROL_UNLOCK_SKILL_DESIGN.md)。

---

## 工具体系

### 内置工具分类

| 分类 | 工具 |
|------|------|
| **Workspace** | `list_workspace`, `read_file`, `write_file`, `replace_in_file`, `search_workspace` |
| **Web** | `web_fetch`, `web_search` |
| **Memory** | `memory_lookup` |
| **Orchestration** | `delegate_task`, `activate_skill`, `read_skill_resource` |
| **Notification** | `notify_user`, `schedule_reminder` |
| **Device** | `device_time`, `session_snapshot` |
| **Phone Control** | `read_current_ui`, `tap_ui_node`, `input_text`, `scroll_ui`, `press_global_action`, `launch_app`, `wait_for_ui`, `perform_ui_action`, `take_screenshot` |

### 动态 MCP 工具

应用可以从远程 MCP server 中动态发现工具，并把它们和本地工具放入同一个 ToolRegistry 中统一调度。

### 访问策略

| 模式 | 可用范围 |
|------|----------|
| **Unrestricted** | 所有已注册工具 |
| **Workspace-restricted** | 仅本地只读、本地编排、workspace 读写 |

隐藏工具类型 `HIDDEN_UNLOCKABLE` 只有在 `AgentRunContext.unlockedToolNames` 中出现时，才会被真正暴露给模型。

---

## 技术栈

| 层 | 技术 |
|----|------|
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| DI | Hilt (Dagger) |
| 数据库 | Room |
| 偏好存储 | DataStore |
| 网络 | Retrofit + OkHttp |
| 序列化 | kotlinx.serialization + SnakeYAML |
| 后台任务 | WorkManager |
| 平台目标 | SDK 35，最低 SDK 26 |

---

## 参与贡献

1. Fork 本仓库
2. 创建功能分支（例如 `git checkout -b feature/my-feature`）
3. 运行上面的验证命令
4. 提交并推送修改
5. 发起 Pull Request

---

## 开源协议

本项目采用 Apache License 2.0，详见 [`LICENSE`](./LICENSE)。

---

## 鸣谢

- [`nanobot`](https://github.com/HKUDS/nanobot) by HKUDS —— 本项目的设计参考目标
- Android Jetpack、Jetpack Compose、Room、WorkManager、Hilt、OkHttp、kotlinx.serialization
