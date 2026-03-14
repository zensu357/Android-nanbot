# Phone-Control Hidden Skill Unlock Design

## 1. Goal

This document defines a compatibility-first design for shipping phone-control as a hidden host capability while keeping the existing skill ecosystem importable without modification.

The desired result is:

- The host app contains the Android phone-control tools.
- Those tools are hidden by default and are not exposed to the model in normal sessions.
- A skill package can unlock hidden phone-control tools only when its zip contains a valid `phone-control.unlock` sidecar file.
- Existing `SKILL.md` parsing and existing skill field semantics remain unchanged.
- Existing public skill marketplace zip packages continue to import as before.

## 2. Design Principles

### 2.1 Compatibility First

- Do not change the meaning of existing `SKILL.md` fields.
- Do not require new frontmatter fields for ordinary skills.
- Do not break direct import of existing online skill zip packages.

### 2.2 Unlock Metadata Must Be Out of Band

- Hidden feature recognition must not depend on `allowed-tools`, `tags`, `metadata`, or any other existing skill field.
- Hidden feature recognition is decided only by the presence and successful verification of `phone-control.unlock`.

### 2.3 Model Should Not Read the Unlock Artifact

- `phone-control.unlock` is importer metadata, not model context.
- After verification, the app must move it out of the skill package or consume and delete it before skill resource indexing.
- The model should only see `SKILL.md` and normal skill resources.

### 2.4 Responsibility Must Be Separated

- The skill publisher signs the unlock declaration and the rule text.
- The app verifies integrity and enforces capability policy.
- The user explicitly accepts the rule text locally.
- The model only receives the instruction manual and tool descriptions, not the signature or legal receipt.

## 3. Non-Goals

This design does not attempt to:

- Replace the existing skill parser.
- Introduce a new public skill format.
- Trust arbitrary downloaded skill text to unlock hidden tools.
- Let a skill package directly name any internal host tool without app-side policy control.

## 4. High-Level Model

The system has three layers.

### 4.1 Host Capability Layer

The host app ships hidden phone-control tools, for example:

- `read_current_ui`
- `tap_ui_node`
- `input_text`
- `scroll_ui`
- `press_global_action`
- `launch_app`
- `wait_for_ui`

These tools are registered by the host but are not visible to the model unless unlocked.

### 4.2 Skill Content Layer

`SKILL.md` remains the normal skill instruction manual.

It continues to provide:

- instructions
- workflow
- constraints
- examples
- ordinary `allowed-tools`

This layer explains how the model should use the tools, but it does not grant hidden capability by itself.

### 4.3 Unlock Sidecar Layer

`phone-control.unlock` is a sidecar file inside the imported skill package zip.

It provides:

- signed package identity
- signed rule text shown to the user
- signed binding to the target skill package content hash
- signed unlock profile identifiers

This layer grants entitlement to hidden tools only after verification succeeds and the user accepts the rules locally.

## 5. Compatibility Requirements

The following rules are mandatory.

### 5.1 Existing Skill Field Semantics Stay Untouched

The following behavior must stay as-is:

- `SkillMarkdownParser` continues parsing `SKILL.md` exactly as before.
- `SkillDefinition.allowedTools` keeps its current meaning.
- Existing skill activation, recommendation, and selection logic remains valid for all normal skills.

### 5.2 Existing Marketplace Skills Continue Working

If a zip package does not contain `phone-control.unlock`:

- import proceeds exactly through the legacy path
- the skill behaves as a normal skill
- no hidden tools are unlocked

### 5.3 Invalid Unlock Files Must Fail Closed But Not Break Import

If `phone-control.unlock` exists but verification fails:

- the skill package still imports as a normal skill when `SKILL.md` is valid
- hidden phone-control tools are not unlocked
- the app records a warning for the user

This preserves compatibility while preventing unauthorized unlocks.

## 6. Packaging Contract

### 6.1 Skill Package Layout

Recommended layout for an unlockable phone-control skill package:

```text
phone-operator-basic/
  SKILL.md
  phone-control.unlock
  references/
    ui-patterns.md
  scripts/
    sample-workflow.txt
```

`phone-control.unlock` must sit in the same package directory as `SKILL.md`.

Why this layout is preferred:

- it binds one unlock declaration to one skill package
- it matches the current scanner's package-directory grouping model
- it avoids redefining the zip root format

### 6.2 Multiple Skills in One Zip

A zip may contain multiple skill packages.

Example:

```text
bundle-root/
  phone-operator-basic/
    SKILL.md
    phone-control.unlock
  planner-addon/
    SKILL.md
```

In this case:

- `phone-operator-basic` is treated as an unlock-candidate package
- `planner-addon` is treated as a normal package

## 7. `phone-control.unlock` File Specification

## 7.1 Format

`phone-control.unlock` should be UTF-8 JSON.

Recommended structure:

```json
{
  "version": 1,
  "packageId": "com.example.phone-operator-basic",
  "skillId": "phone-operator-basic",
  "skillPath": "phone-operator-basic/SKILL.md",
  "skillSha256": "8e6f...",
  "unlockProfiles": [
    "phone_control_basic_v1"
  ],
  "consent": {
    "title": "Phone Control Hidden Feature Agreement",
    "version": "2026-03-14",
    "text": "By enabling this package, you agree not to use the feature for payment, account takeover, surveillance, spam, or other prohibited automation."
  },
  "signing": {
    "keyId": "publisher-main-2026",
    "algorithm": "Ed25519",
    "signature": "base64-signature-over-canonical-payload"
  }
}
```

## 7.2 Required Fields

- `version`: unlock manifest schema version
- `packageId`: publisher-defined package identifier
- `skillId`: target imported skill id
- `skillPath`: relative path to the bound `SKILL.md`
- `skillSha256`: SHA-256 of the bound `SKILL.md` file content
- `unlockProfiles`: one or more host-recognized unlock profile ids
- `consent.title`: short title shown to the user
- `consent.version`: publisher-defined agreement version
- `consent.text`: rule text shown to the user
- `signing.keyId`: publisher signing key id
- `signing.algorithm`: recommended `Ed25519`
- `signing.signature`: detached signature over the canonical payload

## 7.3 Why Profiles Instead of Raw Tool Names

The unlock file should reference app-known profile ids, not arbitrary raw tool names.

Example:

- good: `phone_control_basic_v1`
- not recommended: `tap_ui_node`, `input_text`, `press_global_action`

Reason:

- the app stays in control of what each hidden capability bundle means
- downloaded packages cannot request arbitrary internal tools
- host-side tool mappings can evolve while keeping the signed manifest stable

## 7.4 Canonical Signature Payload

The signature should cover all fields except `signing.signature` itself.

Recommended rules:

- UTF-8 encoding
- canonical JSON with sorted keys
- arrays preserved in declared order
- no whitespace significance

The app ships a trusted public key registry and verifies `keyId` against that registry.

## 8. Import and Verification Flow

### 8.1 Normal Import Path

1. Extract zip to a temporary app-private directory.
2. Scan for package directories containing `SKILL.md`.
3. For any package without `phone-control.unlock`, import normally.

### 8.2 Unlock-Candidate Import Path

1. Extract zip to a temporary app-private directory.
2. For each package directory containing both `SKILL.md` and `phone-control.unlock`, mark it as an unlock candidate.
3. Read and parse `phone-control.unlock`.
4. Verify schema version.
5. Compute SHA-256 of the bound `SKILL.md` and compare with `skillSha256`.
6. Verify `signing.signature` with the app-bundled trusted public key for `keyId`.
7. Resolve each `unlockProfile` to a host-defined hidden tool set.
8. Show the signed consent text to the user if there is no valid local acceptance receipt yet.
9. On acceptance, store a local unlock receipt.
10. Move `phone-control.unlock` out of the skill package before resource indexing.
11. Import the skill through the normal `SKILL.md` path.

### 8.3 If Verification Fails

If any step fails:

- do not unlock hidden tools
- do not trust the sidecar metadata
- still import `SKILL.md` as a normal skill if valid
- surface a warning like "Unlock verification failed; imported as a normal skill"

## 9. Post-Verification File Handling

`phone-control.unlock` must not remain visible as a normal skill resource.

Recommended behavior:

- move it to an app-private metadata path such as:

```text
files/skill_unlock_receipts/<packageId>/p-c.unlock
```

- or consume it into structured storage and delete the original file

Mandatory effect:

- `SkillResourceIndexer` must not include `phone-control.unlock`
- `read_skill_resource` must not expose it to the model

This satisfies the requirement that the app proactively transfers the unlock artifact away so it does not interfere with model usage.

## 10. Local Acceptance Receipt

The downloaded zip cannot prove that a specific user agreed to the rules.

Therefore, the app must store a separate local acceptance receipt after verification.

Recommended local receipt fields:

- `packageId`
- `skillId`
- `skillSha256`
- `unlockProfiles`
- `signerKeyId`
- `consentVersion`
- `consentTextSha256`
- `acceptedAt`
- `appVersion`

This preserves the intended responsibility boundary:

- publisher signs the rule text
- user accepts locally
- app stores local evidence of acceptance

## 11. Runtime Unlock Model

The unlock sidecar must not replace current skill field behavior.

Instead, it adds an entitlement layer.

### 11.1 Existing Behavior Kept

Current behavior stays valid for ordinary skills:

- `SKILL.md` parsing is unchanged
- `allowedTools` keeps the same meaning
- normal skills remain importable from the online ecosystem

### 11.2 New Entitlement Layer

After a successful unlock import, the app stores hidden-tool entitlements outside `SkillDefinition` parsing.

Conceptually:

- `skill.allowedTools` = ordinary declared tool list from `SKILL.md`
- `hiddenEntitledTools(skill)` = app-derived hidden tool list from verified `phone-control.unlock`
- `effectiveAllowedTools(skill)` = union of both sets

This means hidden unlock behavior is additive and out of band.

### 11.3 Why This Preserves Compatibility

- Existing skills do not need to know about hidden unlock logic.
- Existing imported zips still work with no new fields.
- Hidden unlock skills can be recognized without changing parser rules.

## 12. Recommended Host Profile Registry

The host app should define a fixed mapping from unlock profile id to hidden tools.

Example:

```text
phone_control_basic_v1
  -> read_current_ui
  -> tap_ui_node
  -> input_text
  -> scroll_ui
  -> press_global_action
  -> launch_app
  -> wait_for_ui
```

Future profiles can be added without changing the skill format.

Example reserved profiles:

- `phone_control_basic_v1`
- `phone_control_navigation_v1`
- `phone_control_visual_v1`

The app may refuse any profile id it does not recognize.

## 13. Safety Gates

Unlock verification alone is not enough.

Phone-control tools must still obey host safety policy.

Recommended gates:

- global setting switch for phone control
- hidden-tool unlock verification success
- local user agreement receipt exists
- current session is foreground only
- workspace-restricted mode still blocks phone-control tools
- sensitive action approval still required where applicable

Unlock grants visibility, not unconditional execution.

## 14. Failure and Revalidation Rules

### 14.1 Skill Content Changes

If `SKILL.md` changes and its SHA-256 no longer matches the stored unlock receipt:

- invalidate hidden entitlement
- keep the skill imported as a normal skill
- require unlock verification again

### 14.2 Reimport With Same Signed Package

If the skill content hash and consent version are unchanged:

- the app may reuse the stored local acceptance receipt

### 14.3 Reimport With New Consent Version

If `consent.version` changes:

- require the user to accept again locally

## 15. Responsibility Boundary

The intended responsibility split is:

### Publisher

- writes `SKILL.md`
- writes and signs `phone-control.unlock`
- declares the unlock profile and rule text

### App

- verifies integrity
- maps unlock profiles to real host tools
- stores acceptance receipts
- hides the unlock artifact from the model
- enforces all runtime safety gates

### User

- chooses whether to import the package
- reads and accepts the signed rules locally
- remains responsible for how the unlocked feature is used

### Model

- only sees the instruction manual and approved tools
- does not see the signature artifact or legal receipt

## 16. Recommended Warning Copy

When the app displays the consent dialog, recommended wording is:

"This skill package requests access to hidden phone-control tools. The package signature is valid, but enabling it allows the model to operate parts of your device through host-defined controls. Do not enable this package unless you trust the publisher and agree to the signed usage rules."

## 17. Minimal Implementation Impact

This design intentionally minimizes disruption.

The following areas can remain logically unchanged:

- `SkillMarkdownParser`
- `SkillDefinition`
- ordinary skill import path
- online marketplace compatibility for normal skills

The new logic is concentrated in:

- zip import verification
- local unlock receipt storage
- hidden tool profile registry
- runtime effective tool resolution
- exclusion of `phone-control.unlock` from model-visible resources

## 18. Summary

This design keeps the current skill ecosystem compatible while adding a separate, signed, user-consented unlock path for hidden phone-control tools.

The key rule is simple:

- `SKILL.md` explains behavior
- `phone-control.unlock` grants signed entitlement
- local acceptance records user consent
- the host app stays in control of the real hidden tool mapping

That satisfies all three goals:

- existing marketplace skills still import normally
- unlock skills can be recognized and verified reliably
- responsibility is explicitly separated across publisher, app, user, and model
