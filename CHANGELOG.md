# Changelog

## v1.3.0 — 2026-11-21

### Added

- A string-ID command registry with labels, categories, enable conditions, exact shortcuts and
  runtime shortcut rebinding with atomic conflict checks.
- Scoped editor extensions with deterministic unload, resource ownership and failed-install rollback.
- An optional Scene2D command palette with search, keyboard/touch navigation, live command
  availability, shortcut hints and focus/selection preservation.
- Standard editor commands, text-cleanup commands and whole-line move, duplicate and delete commands.
- A shared completion registry for independent host and extension sources, with conditional
  activation, synchronous/asynchronous result merging, deduplication and cancellation checks.
- Reusable snippet providers and snippet-pack extensions, plus a completion-controller extension
  with a command-palette entry point.
- An optional find/replace toolbar with literal, whole-word and regular-expression search,
  selection-scoped matching, match counts and regex error feedback.
- Diagnostic navigation and retained navigation-result browsing.
- Exact batch edits which reject the entire operation if input filtering or newline normalization
  would alter a replacement.
- An opt-in command setting to reserve shortcuts on unavailable/no-op actions, preserving selections
  at line-editing boundaries.

### Fixed

- Unicode identifier scanning, including supplementary letters and scanner progress on non-keywords.
- Stale completion and navigation responses after edits, selection changes, dismissal or controller
  removal; removed extension contributions cannot reopen stale completion results.
- Search/replace boundaries, filtered mutations, compound undo bookkeeping and undo-history limits.
- Reentrant input-interceptor dispatch and key-release/focus handling for command character suppression.
- Command-palette layout in short windows and focus restoration when callbacks unload or replace UI.

### Examples and validation

- Updated desktop and Android examples with command-palette, completion, snippet and line-editing
  extension integration, plus README usage and lifecycle documentation.
- Added 237 regression tests across document editing, Unicode, search, commands, extensions,
  completion, navigation, diagnostics and Scene2D interaction.
- Verified the core test suite, desktop compilation and Android debug compilation. GUI rendering and
  Android device behavior have not been manually verified for this release.

### Distribution

- Git tag and JitPack dependency version: `v1.3.0`.
- Gradle publication version: `1.3.0`.
- Existing explicit completion-provider constructors and native keymap defaults remain available;
  the new extension features are opt-in for library consumers.
