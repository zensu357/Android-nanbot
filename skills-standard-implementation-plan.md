# Agent Skills Standard Development Plan

## Goal

This document defines the development plan for upgrading the current Nanobot `skills` feature into a complete Agent Skills-compatible runtime.

The target is not to preserve the current prompt-only behavior. The target is to implement the standard lifecycle described by Agent Skills:

1. Discover skills from supported scopes
2. Parse and validate `SKILL.md`
3. Disclose a compact skill catalog to the model
4. Activate skills on demand
5. Expose bundled resources lazily
6. Preserve activated skill context over time

This plan also records the places where a direct copy of desktop/CLI implementations would be wrong for Android, why that would be risky, and what the correct Android-friendly solution should be.

## Current State Summary

The current implementation already has a useful foundation, but it is still a custom prompt skill system rather than a full Agent Skills runtime.

### Already implemented

- Skill model and storage: `app/src/main/java/com/example/nanobot/core/skills/SkillDefinition.kt`
- Builtin skills: `app/src/main/java/com/example/nanobot/core/skills/SkillCatalog.kt`
- Import from user-selected directory: `app/src/main/java/com/example/nanobot/core/skills/SkillDirectoryScanner.kt`
- Custom skill persistence: `app/src/main/java/com/example/nanobot/core/database/entity/CustomSkillEntity.kt`
- Repository merge layer: `app/src/main/java/com/example/nanobot/data/repository/SkillRepositoryImpl.kt`
- Settings UI for enable/disable/import/remove: `app/src/main/java/com/example/nanobot/feature/settings/SettingsViewModel.kt`
- Skill exposure in system prompt: `app/src/main/java/com/example/nanobot/core/ai/SystemPromptBuilder.kt`
- Keyword-based relevance selection: `app/src/main/java/com/example/nanobot/core/ai/SkillSelector.kt`

### Not yet implemented as standard behavior

- Multi-scope discovery (`project`, `user`, `.agents/skills`, client-specific locations)
- Standard YAML frontmatter support and validation
- Dedicated `activate_skill` tool
- Lazy access to `scripts/`, `references/`, `assets/`
- Session-level activated skill tracking and deduplication
- Protection of activated skill content from prompt trimming/compression
- Trust gating for project-provided skills
- Standard diagnostics and validation reporting
- Explicit user activation (`/skill-name` or equivalent)

## Core Architectural Decision

### Decision

Nanobot should implement Agent Skills as a runtime capability, not as a system-prompt decoration feature.

### Why the current model is insufficient

Today the app mostly does this:

1. Load enabled skills
2. Predict which skills look relevant
3. Inject selected skill text into the system prompt

That is useful, but it is not the standard interaction model.

The standard model is:

1. Tell the model which skills exist
2. Let the model or user activate a specific skill
3. Return the skill content in a structured way
4. Let the model read referenced resources only when needed

### Required outcome

The current selector/injection flow should become an optional optimization layer, not the primary activation path.

## Important Constraints and "Do Not Copy Blindly" Notes

These are the places where a literal desktop/CLI implementation would be wrong.

### 1. Do not assume normal filesystem absolute paths

#### Why this is wrong

Many Agent Skills examples assume paths like `/home/user/.agents/skills/foo/SKILL.md` and a general file read tool.

This project runs on Android. Imported skills come from SAF `Uri`s, not guaranteed stable POSIX paths. The current workspace tools are sandboxed to workspace storage, not arbitrary external filesystem locations.

#### Risk

- Broken activation flow on Android
- Security regressions if arbitrary file access is opened to imitate desktop behavior
- A mismatch between skill storage and existing workspace sandbox rules

#### Correct solution

Implement semantic equivalence, not path-shape equivalence:

- Use `activate_skill` for standard activation
- Use a dedicated `read_skill_resource` tool for lazy resource reads
- Keep skill access bound to validated skill roots and persisted SAF permissions
- Optionally mirror imported skills into app-private storage later, but do not make that a prerequisite

### 2. Do not let `allowed-tools` bypass global tool policy

#### Why this is wrong

The standard allows skills to declare `allowed-tools`, but this is not a permission escalation mechanism.

This project already has `ToolAccessPolicy` and workspace-restricted mode.

#### Risk

- Skills could silently weaken security policy
- Imported third-party skills could grant themselves unsafe capabilities

#### Correct solution

Treat `allowed-tools` as a narrowing hint or pre-approval annotation only.

Effective tool access must remain:

`global policy` intersect `tool runtime availability` intersect `skill-declared allowed tools`

Never let a skill expand beyond global policy.

### 3. Do not keep the current prompt-only activation as the only path

#### Why this is wrong

If framework code always decides which skill is active and injects it directly, the model never actually performs standard activation.

#### Risk

- Not compatible with the standard mental model
- Harder to debug activation behavior
- Harder to support explicit user activation and lazy resources

#### Correct solution

Introduce a first-class activation runtime:

- skill catalog disclosure
- `activate_skill`
- `read_skill_resource`
- activated skill session state

The current `SkillSelector` can remain, but only as optional guidance or auto-suggestion.

### 4. Do not make the parser depend on custom section names

#### Why this is wrong

The standard does not require sections like `## Workflow` or `## Constraints`.

The current `SkillPromptAssembler` assumes those sections for partial rendering.

#### Risk

- Standard-compliant skills may load but behave inconsistently
- The runtime becomes dependent on one authoring style

#### Correct solution

- Parse standard frontmatter independently from body rendering
- Preserve full raw body
- Treat section extraction as optional enhancement, not schema

### 5. Do not add project-level skill discovery without trust gating

#### Why this is wrong

Project skills may come from untrusted repositories.

#### Risk

- A newly cloned repository could inject hidden instructions into the model
- Users would have no clear boundary between trusted and untrusted skill sources

#### Correct solution

- Add a project trust gate before loading project-scoped skills
- Show discovered-but-blocked skills in diagnostics
- Require explicit user trust for project skill activation

## Target Architecture

## Layer 1: Discovery and validation

Purpose: discover skill packages across supported scopes, parse `SKILL.md`, validate metadata, and build a canonical record.

### New types

- `SkillDiscoveryScope`
- `DiscoveredSkill`
- `SkillPackageLocation`
- `SkillValidationIssue`
- `SkillValidationResult`
- `SkillDiagnosticsEntry`

### New services

- `SkillDiscoveryService`
- `SkillFrontmatterParser`
- `SkillValidator`
- `SkillResourceIndexer`

### Existing code to refactor

- Refactor `app/src/main/java/com/example/nanobot/core/skills/SkillDirectoryScanner.kt`
- Refactor `app/src/main/java/com/example/nanobot/core/skills/SkillMarkdownParser.kt`
- Refactor `app/src/main/java/com/example/nanobot/data/repository/SkillRepositoryImpl.kt`

## Layer 2: Catalog disclosure

Purpose: disclose available skills compactly without injecting full instructions.

### New types

- `SkillCatalogEntry`
- `SkillCatalogExposure`

### Existing code to refactor

- Refactor `app/src/main/java/com/example/nanobot/core/ai/SystemPromptBuilder.kt`
- Refactor `app/src/main/java/com/example/nanobot/core/ai/SkillPromptAssembler.kt`

### Important behavior change

The system prompt should disclose skills and activation behavior, but should stop auto-injecting full skill bodies as the primary mechanism.

## Layer 3: Activation runtime

Purpose: return a skill's full content when the model or user activates it.

### New tools

- `ActivateSkillTool`
- `ReadSkillResourceTool`

### New services

- `SkillActivationService`
- `ActivatedSkillSessionStore`

### Existing code to refactor

- Update `app/src/main/java/com/example/nanobot/core/tools/ToolRegistry.kt`
- Update `app/src/main/java/com/example/nanobot/di/AppModule.kt`
- Update `app/src/main/java/com/example/nanobot/core/ai/PromptComposer.kt`
- Update tool diagnostics screens if needed

## Layer 4: Session and context lifecycle

Purpose: preserve activated skill instructions over time without repeated reinjection or accidental trimming.

### New services

- `SkillContextProtector`
- `ActivatedSkillMessageFormatter`

### Existing code to refactor

- Review `app/src/main/java/com/example/nanobot/core/ai/PromptComposer.kt`
- Review `app/src/main/java/com/example/nanobot/core/ai/ContextBudgetPlanner.kt`
- Review any future or existing history trimming/compression logic

## Development Tasks By Module

## Phase 0 - Data model redesign

### Task 0.1 - Introduce standard-first skill metadata model

#### Goal

Stop treating `SkillDefinition` as the only source of truth.

#### New classes

- `StandardSkillMetadata`
- `SkillPackage`
- `SkillResourceEntry`
- `SkillSourceScope`

#### Required fields

- `name`
- `description`
- `license`
- `compatibility`
- `metadata: Map<String, String>`
- `allowedTools: List<String>`
- `locationUri`
- `skillRootUri`
- `bodyMarkdown`
- `rawFrontmatter`
- `sourceScope`
- `isTrusted`
- `validationIssues`

#### What to do with existing custom fields

Keep these as Nanobot extensions, not as the primary schema:

- `activationKeywords`
- `recommendedTools`
- `priority`
- `maxPromptChars`
- `whenToUse`
- `summaryPrompt`

#### Files to update

- `app/src/main/java/com/example/nanobot/core/skills/SkillDefinition.kt`
- `app/src/main/java/com/example/nanobot/data/mapper/SkillMapper.kt`
- `app/src/main/java/com/example/nanobot/core/database/entity/CustomSkillEntity.kt`

#### Notes

Do not remove current fields in the first pass. Introduce the new model and migrate incrementally.

### Task 0.2 - Redesign Room schema for standard metadata and diagnostics

#### Goal

Persist enough information to support discovery, diagnostics, activation, and resource lookup.

#### Database additions

- `license`
- `compatibility`
- `metadataJson`
- `allowedToolsJson`
- `locationUri`
- `skillRootUri`
- `sourceScope`
- `trusted`
- `validationIssuesJson`
- `rawFrontmatter`
- `bodyMarkdown`

#### Files to update

- `app/src/main/java/com/example/nanobot/core/database/entity/CustomSkillEntity.kt`
- `app/src/main/java/com/example/nanobot/core/database/dao/CustomSkillDao.kt`
- `app/src/main/java/com/example/nanobot/data/mapper/SkillMapper.kt`
- database definition and migration files

#### Risk

The app currently uses destructive migration fallback. That is acceptable for fast iteration, but not ideal for a user-facing skill library feature.

#### Solution

Move toward explicit Room migrations before shipping the full runtime.

## Phase 1 - Discovery system

### Task 1.1 - Implement multi-scope discovery service

#### Goal

Support the standard discovery model instead of a single imported tree.

#### Discovery scopes to support

- builtin packaged skills
- imported SAF-backed user skill roots
- workspace/project skill roots
- compatibility root `.agents/skills/`
- optional client-specific roots for future use

#### New classes

- `SkillDiscoveryService`
- `SkillDiscoveryScope`
- `SkillDiscoveryConfig`

#### Expected behavior

- scan configured scopes
- detect child directories containing `SKILL.md`
- skip noisy directories when relevant
- produce stable ordering
- detect name conflicts
- apply scope priority rules
- emit diagnostics

#### Files to update

- `app/src/main/java/com/example/nanobot/domain/repository/SkillRepository.kt`
- `app/src/main/java/com/example/nanobot/data/repository/SkillRepositoryImpl.kt`
- settings storage and settings UI state files

### Task 1.2 - Add trust gating for project/workspace skills

#### Goal

Prevent untrusted repositories from silently injecting instructions.

#### New behavior

- project skill roots are scanned only when workspace trust is granted
- untrusted skills appear in diagnostics but not in active catalog exposure

#### Files to add or modify

- a workspace trust state provider or repository
- settings / diagnostics UI
- skill repository filtering logic

### Task 1.3 - Support more than one imported root

#### Why

The current config stores only one `skillsDirectoryUri`. Standard-compatible discovery will need multiple sources.

#### Required changes

- replace single `skillsDirectoryUri` with a list of persisted roots
- support enable/disable/remove per root
- store root label, URI, type, trust state, last scan time

#### Files to update

- `app/src/main/java/com/example/nanobot/core/preferences/SettingsDataStore.kt`
- `app/src/main/java/com/example/nanobot/feature/settings/SettingsUiState.kt`
- `app/src/main/java/com/example/nanobot/feature/settings/SettingsViewModel.kt`

## Phase 2 - Parser and validator

### Task 2.1 - Replace ad hoc frontmatter parsing with a real YAML parser

#### Goal

Support standard frontmatter fields robustly.

#### Standard fields to support

- `name`
- `description`
- `license`
- `compatibility`
- `metadata`
- `allowed-tools`

#### Compatibility behavior

- support loose fallback for malformed YAML when practical
- record warnings instead of failing on cosmetic issues
- fail only when critical fields are missing or unreadable

#### Files to update

- replace or heavily refactor `app/src/main/java/com/example/nanobot/core/skills/SkillMarkdownParser.kt`

### Task 2.2 - Add standard validation rules

#### Rules

- `name` required
- `description` required
- `name` length <= 64 warning/error depending on mode
- `name` lowercase alphanumeric and hyphen rule
- no leading/trailing hyphen
- no consecutive hyphen
- compare `name` with parent directory name

#### Modes

- runtime load mode: permissive with warnings
- validation mode: strict diagnostics

#### New classes

- `SkillValidator`
- `SkillValidationMode`

### Task 2.3 - Separate body parsing from Nanobot extensions

#### Goal

Keep standard body support independent from optional Nanobot optimizations.

#### Required changes

- preserve raw body markdown
- add optional post-processing for section extraction
- keep `workflow`, `constraints`, `examples` extraction as best-effort only

## Phase 3 - Catalog disclosure redesign

### Task 3.1 - Replace expanded prompt injection with standard disclosure text

#### Goal

System prompt should disclose available skills and how to activate them, but not preload full instructions by default.

#### Required changes

- refactor `SkillPromptAssembler`
- refactor `SystemPromptBuilder`
- include compact entries with `name`, `description`, and activatable identifier
- include activation guidance in system prompt

#### Desired output shape

Something conceptually equivalent to:

```text
Available Skills:
- pdf-processing: Extract PDF text, fill forms, merge files.
- data-analysis: Analyze datasets and generate reports.

When a task matches a skill, call `activate_skill` with the skill name before continuing.
```

### Task 3.2 - Keep `SkillSelector` only as optional assistive logic

#### Goal

Avoid conflicting activation paths.

#### New role

- optionally prioritize catalog ordering
- optionally suggest likely skills in diagnostics or UI
- do not replace explicit activation flow

#### Files to review

- `app/src/main/java/com/example/nanobot/core/ai/SkillSelector.kt`
- `app/src/main/java/com/example/nanobot/core/ai/SystemPromptBuilder.kt`

## Phase 4 - Activation tools

### Task 4.1 - Implement `ActivateSkillTool`

#### Goal

Provide first-class skill activation.

#### Tool contract

- input: `name`
- output:
  - skill name
  - description
  - activation status
  - full body or full `SKILL.md` according to selected mode
  - skill root reference
  - resource list summary
  - warnings

#### Output format

Return a structured wrapper, for example:

```xml
<skill_content name="pdf-processing">
...
</skill_content>
```

The exact wrapper format may be plain text, XML-like text, or another stable tagged format. It must be recognizable for deduplication and context protection.

#### Files to add

- `app/src/main/java/com/example/nanobot/core/tools/impl/ActivateSkillTool.kt`
- activation service and supporting model classes

#### Files to update

- `app/src/main/java/com/example/nanobot/di/AppModule.kt`
- `app/src/main/java/com/example/nanobot/core/tools/ToolRegistry.kt`

### Task 4.2 - Implement `ReadSkillResourceTool`

#### Goal

Support the standard third layer: lazy access to bundled resources.

#### Tool contract

- input:
  - `skillName`
  - `relativePath`
  - optional `maxChars`
- behavior:
  - resolve resource relative to the activated skill root
  - block traversal outside root
  - read only resources belonging to that skill package

#### Why a dedicated tool is preferable here

The current `read_file` tool is restricted to workspace sandbox paths. Skills imported from SAF roots do not naturally fit that model.

#### Files to add

- `app/src/main/java/com/example/nanobot/core/tools/impl/ReadSkillResourceTool.kt`
- supporting repository/service for skill package resource access

### Task 4.3 - List skill resources without eager loading

#### Goal

Activation should reveal available resources, not preload them.

#### Required behavior

- index `scripts/`, `references/`, `assets/`, and other relevant files
- include a capped list in activation output
- read content only on explicit request

## Phase 5 - Session lifecycle and context protection

### Task 5.1 - Track activated skills per session

#### Goal

Prevent repeated reinjection.

#### New classes

- `ActivatedSkillSessionStore`
- `ActivatedSkillRecord`

#### Required behavior

- track activated skill names per session
- record activation source: model or user
- record hash/version for invalidation if content changes
- skip or short-circuit repeat activation when still valid

### Task 5.2 - Protect activated skill content from trimming

#### Goal

Skill instructions are durable behavior context.

#### Required changes

- mark activated skill content as protected content
- exclude it from ordinary pruning/trimming logic
- if summarization is ever added for tool outputs, do not summarize away active skill instructions

#### Files to review

- `app/src/main/java/com/example/nanobot/core/ai/ContextBudgetPlanner.kt`
- `app/src/main/java/com/example/nanobot/core/ai/PromptComposer.kt`
- history exposure and message persistence code paths

### Task 5.3 - Decide where activated skills live in prompt assembly

#### Recommended approach

Activated skill content should appear as a durable system-side or tool-side artifact, not be re-generated fresh from selector logic every turn.

#### Implementation options

- store activation as a protected synthetic message in session history
- or inject from activated skill session store on each turn in a stable protected section

#### Recommendation

Prefer protected synthetic session messages because they are easier to inspect and debug.

## Phase 6 - User-facing activation and diagnostics

### Task 6.1 - Add explicit user activation syntax

#### Goal

Support standard-style explicit activation without waiting for model judgment.

#### Options

- slash commands such as `/pdf-processing`
- mention syntax such as `$pdf-processing`

#### Files to update

- `app/src/main/java/com/example/nanobot/feature/chat/ChatViewModel.kt`
- input parsing / send pipeline

#### Recommended first step

Implement slash command handling before adding autocomplete.

### Task 6.2 - Add skills diagnostics UI

#### Goal

Make discovery and validation debuggable.

#### UI should show

- discovered skills by scope
- overridden skills
- blocked untrusted skills
- validation warnings
- parse failures
- activation history
- resource inventory summary

#### Files to update

- settings UI and/or tool debug UI

## Phase 7 - Permissions, policy, and safety integration

### Task 7.1 - Integrate `allowed-tools` safely

#### Goal

Honor standard metadata without weakening policy.

#### Required behavior

- parse `allowed-tools`
- expose it in activation output
- optionally feed it into runtime tool filtering
- never widen global policy

#### Files to update

- `app/src/main/java/com/example/nanobot/core/tools/ToolAccessPolicy.kt`
- activation/runtime context integration points

### Task 7.2 - Keep resource reads sandboxed to skill roots

#### Goal

Do not allow `../` or sibling package escape.

#### Required behavior

- normalize relative path input
- reject traversal attempts
- resolve only under the activated skill root
- report clear errors

## Phase 8 - Testing

### Task 8.1 - Parser and validator tests

Add tests for:

- valid standard frontmatter
- malformed YAML fallback
- missing `description`
- parent directory mismatch
- `allowed-tools` parsing
- metadata map parsing

### Task 8.2 - Discovery tests

Add tests for:

- multi-scope ordering
- conflict resolution
- trust-gated project skills
- empty scopes
- ignored non-skill directories

### Task 8.3 - Activation tests

Add tests for:

- successful `activate_skill`
- repeated activation deduplication
- activation of invalid or blocked skills
- resource listing without eager content load
- resource reading within root
- traversal rejection

### Task 8.4 - Prompt/runtime tests

Add tests for:

- catalog disclosure without full-body preload
- protected activated skill retention
- user explicit activation path
- `allowed-tools` policy intersection behavior

## Recommended File-Level Work Breakdown

## Existing files likely to change heavily

- `app/src/main/java/com/example/nanobot/core/skills/SkillDefinition.kt`
- `app/src/main/java/com/example/nanobot/core/skills/SkillMarkdownParser.kt`
- `app/src/main/java/com/example/nanobot/core/skills/SkillDirectoryScanner.kt`
- `app/src/main/java/com/example/nanobot/data/repository/SkillRepositoryImpl.kt`
- `app/src/main/java/com/example/nanobot/domain/repository/SkillRepository.kt`
- `app/src/main/java/com/example/nanobot/core/database/entity/CustomSkillEntity.kt`
- `app/src/main/java/com/example/nanobot/data/mapper/SkillMapper.kt`
- `app/src/main/java/com/example/nanobot/core/preferences/SettingsDataStore.kt`
- `app/src/main/java/com/example/nanobot/feature/settings/SettingsViewModel.kt`
- `app/src/main/java/com/example/nanobot/feature/settings/SettingsUiState.kt`
- `app/src/main/java/com/example/nanobot/core/ai/SystemPromptBuilder.kt`
- `app/src/main/java/com/example/nanobot/core/ai/SkillPromptAssembler.kt`
- `app/src/main/java/com/example/nanobot/core/ai/PromptComposer.kt`
- `app/src/main/java/com/example/nanobot/di/AppModule.kt`

## New files likely needed

- `app/src/main/java/com/example/nanobot/core/skills/SkillDiscoveryService.kt`
- `app/src/main/java/com/example/nanobot/core/skills/SkillValidator.kt`
- `app/src/main/java/com/example/nanobot/core/skills/SkillFrontmatterParser.kt`
- `app/src/main/java/com/example/nanobot/core/skills/SkillResourceIndexer.kt`
- `app/src/main/java/com/example/nanobot/core/skills/SkillDiagnostics.kt`
- `app/src/main/java/com/example/nanobot/core/skills/ActivatedSkillSessionStore.kt`
- `app/src/main/java/com/example/nanobot/core/skills/SkillActivationService.kt`
- `app/src/main/java/com/example/nanobot/core/tools/impl/ActivateSkillTool.kt`
- `app/src/main/java/com/example/nanobot/core/tools/impl/ReadSkillResourceTool.kt`
- supporting tests in `app/src/test/java/...`

## Migration Strategy

### Stage A - Add new runtime without deleting current behavior

- introduce new models and parser
- add discovery diagnostics
- add activation tools
- keep current prompt selector path behind a compatibility flag

### Stage B - Switch primary behavior

- system prompt exposes catalog only
- activation happens through runtime tools
- current expanded skill injection becomes optional or debug-only

### Stage C - Remove obsolete assumptions

- reduce reliance on `workflow` and other custom prompt fragments
- retire single-root import assumptions
- stop treating imported skills as only a Settings feature

## Priority Order

### P0

- standard metadata model
- multi-root discovery foundation
- YAML parser and validator
- `activate_skill`
- `read_skill_resource`

### P1

- trust gating
- activated skill session store
- context protection
- diagnostics UI
- explicit user activation

### P2

- policy integration for `allowed-tools`
- compatibility root scanning polish
- autocomplete and richer UX

## Acceptance Criteria

The implementation is considered complete only when all of the following are true:

- the app can discover skills from more than one scope
- the app supports standard frontmatter fields from `SKILL.md`
- the model sees a compact skill catalog rather than preloaded full instructions by default
- the model can activate a skill via a dedicated tool
- activated skills can expose bundled resources lazily
- repeated activation is deduplicated within a session
- activated skill context survives normal trimming behavior
- project-provided skills are trust-gated
- diagnostics explain why a skill was loaded, skipped, overridden, or blocked
- explicit user skill activation works

## Final Recommendation

The implementation should be developed as a runtime refactor, not a parser patch.

If the team only adds standard YAML fields on top of the current prompt injection path, the project will still not behave like a full Agent Skills client.

The minimum correct path is:

1. standardize discovery and metadata
2. add real activation tools
3. add lazy resource access
4. add session lifecycle protection
5. then simplify prompt injection into a catalog disclosure layer

Anything less will improve compatibility on paper, but not in actual runtime behavior.
