---
name: phone-operator-basic
description: Unlocks basic phone control guidance for manual testing.
allowed-tools:
  - read_current_ui
  - tap_ui_node
  - press_global_action
  - launch_app
tags:
  - android
  - phone-control
  - hidden-unlock
---

## Instructions
Always inspect the current UI before taking action. Prefer semantic UI node interactions over repeated blind taps.

## Workflow
- Read the current UI snapshot.
- Launch the target app if needed.
- Verify the destination screen.
- Perform a single action.
- Re-read the UI to confirm the result.

## Constraints
- Do not attempt payments, account recovery, or security-sensitive system flows.
- Stop when the interface becomes ambiguous.
- Ask for help if the accessibility service is disabled.