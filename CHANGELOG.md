# Changelog

## Unreleased

## v1.5.2 — 2026-11-26

### Fixed

- The editor now honours Scene2D `parentAlpha` and its actor colour, like other widgets. Fading a
  parent `Table`/`Window`, or calling `editor.setColor(...)` / `addAction(Actions.fadeOut(...))`,
  previously left the text, gutter numbers, whitespace markers, rulers, selection, squiggles and inline
  images fully opaque while only sibling widgets faded. Every colour the editor sets is now multiplied by
  the actor colour times `parentAlpha`; an opaque white editor keeps its exact style colours.
- Touch selection handles live on a stage-level overlay and now fade together with the editor that owns
  them, including the magnifier frame.
- `CodeCompletionPopup` and `CodeHintPanel` (completion list, documentation, hover and signature hints)
  apply their own actor colour and `parentAlpha` to backgrounds and text.
- The built-in default-style drawables multiply their fill by the current batch colour instead of
  replacing it, so custom skins and the defaults behave the same under fades.
- The caller's batch colour is restored after drawing, so a tinted sibling drawn earlier no longer leaks
  into the editor and the editor's tint no longer leaks into the next actor.

### Compatibility

- Row renderer callbacks now see the editor's tint as the batch colour (opaque white unless the editor or
  a parent is faded or tinted) rather than whatever colour the previous actor left. Renderers that set
  their own colours are unaffected; to fade with the editor, multiply by `batch.getColor()`.
- `CodeCompletionPopup` subclasses overriding `drawItem` can call the new protected `setFontColor(Color)`
  to get the same fade for their text.

### Validation and distribution

- All 573 core tests passed across 46 classes, including 3 new rendering regressions that record glyph
  vertex colours, image tint, whitespace markers, gutter numbers and the restored caller colour under
  `parentAlpha` and actor tint, plus hint panel and completion popup fades.
- Core build and local Maven publication, and desktop and Android Java compilation passed.
- Git tag and JitPack version: `v1.5.2`; Gradle publication version: `1.5.2`.

## v1.5.1 — 2026-11-26

### Fixed

- Inline images now scale with zoom. Their width was used as a fixed advance while the surrounding text
  grew or shrank, so at 2x zoom icons were half-size, wrapping was computed from the wrong widths, and
  hit areas no longer matched the text. Width, height and baseline offset are now declared at 100% zoom
  and multiplied by the zoom scale for measurement, hit testing, wrapping and drawing alike. Images
  shrunk to fit the row stay centred in their measured column.
- Fonts that already carry a scale (for example a skin font set to 1.5x/2x) got rows sized for the
  unscaled font, so taller glyphs overlapped neighbouring rows. Row height now uses the font's actual
  line height at 100% zoom.
- Characters the font cannot draw are measured consistently: the font's `missingGlyph` replacement is
  used for advance and kerning when present, and the fallback space reservation no longer applies the
  font's base scale twice. When the font has no replacement, the character is skipped during drawing at
  its measured width, so following glyphs no longer drift left of the caret and hit-test columns.
- `GRADIENT` text keeps one continuous ramp across the whole token. Previously it restarted after every
  tab, inline image or soft wrap, and wrapped headings reset to the start colour on each row. Fake-bold
  passes repeat the same per-glyph colours.

### Validation and distribution

- All 570 core tests passed across 46 classes, including 9 new rendering regressions that record actual
  image rectangles and glyph vertex positions/colours under zoom, pre-scaled fonts, missing glyphs,
  unsupported spaces with tabs/rulers, and gradients across tabs, images, wraps and bold passes.
- Core build and local Maven publication, desktop and Android Java compilation, and the hidden-window
  OpenGL reference sheet passed; the snapshot was visually inspected.
- Physical Android rendering/touch behavior and IME composition still require device validation.
- Git tag and JitPack version: `v1.5.1`; Gradle publication version: `1.5.1`.

## v1.5.0 — 2026-11-26

### Added

- Opt-in whitespace markers with ALL, TRAILING and SELECTION modes, plus independent trailing-space
  tint and actual line-break symbols. Spaces use dots and tabs use measured arrows; no special font
  glyphs are required. Password mode suppresses these text decorations.
- Multiple configurable column rulers, using space advances from the unindented text origin. Rulers
  follow horizontal scroll and zoom. All display options work with shared editor settings.
- Composable background/foreground row renderer callbacks for host-defined diff, execution or review
  overlays. Contexts expose measured UTF-16 column geometry, wrapped segments, empty lines and visible
  collapsed-fold suffixes. Callback tint and font state are restored even after exceptions.
- Desktop and Android Rendering samples with effect controls, and an optional hidden-window OpenGL
  reference-sheet task: `:lwjgl3:renderingSnapshot`.

### Improved

- Visible fold suffixes now retain diagnostic squiggles as well as their existing search/selection and
  rich-text decorations. Synthetic fold placeholders do not acquire source-text whitespace markers.
- Whitespace rendering uses binary search to skip the horizontally hidden prefix of long lines, and
  submits markers only for cells near the viewport. Display toggles reuse layout/highlight caches.
- Theme-derived marker colors, copy-safe style fields and README integration examples for custom layers.

### Fixed

- Rich-text backgrounds and underlines, diagnostic squiggles and inline images now restore the batch's
  original packed tint. Keeping a reference to the mutable batch color previously let decoration
  colors leak into subsequent custom layers or sibling actors.

### Validation and distribution

- All 561 core tests passed across 46 classes, including 31 rendering regression tests covering actual
  draw-call geometry, modes, wraps, folds, scroll/zoom, Unicode/images, state restoration and culling.
- Core build and local Maven publication, desktop Java compilation and Android debug Java compilation
  passed. Generated and visually inspected a real OpenGL reference sheet for dark/light themes,
  continuation indentation, zoom and visible fold suffixes; included it in the README.
- Physical Android rendering/touch behavior and IME composition still require device validation.
- Git tag and JitPack version: `v1.5.0`; Gradle publication version: `1.5.0`.

## v1.4.2 — 2026-11-25

- Restore the Gradle wrapper's executable bit so GitHub Actions can run verification on Linux.
- Extend regex replacement validation against Java's own template expansion, including multi-digit
  groups, escaping, malformed templates, no-op history preservation and lookaround captures.
- All 530 core tests passed; core packaging and local Maven publication, desktop Java compilation and
  Android debug Java compilation passed.
- Includes the `v1.4.1` replacement performance improvements below. Git/Gradle/JitPack versions are
  `v1.4.2` / `1.4.2` / `v1.4.2` respectively.

## v1.4.1 — 2026-11-25

### Improved

- Regex replacement expands captured and named groups directly into a replacement-sized buffer and
  reuses one matcher across Replace All. Dense matches on long lines no longer allocate a full-line
  buffer and copy every prefix once per match.
- Added a warmed median benchmark task for dense regex replacements, alongside the position-mapping
  benchmark.

### Validation

- Added replacement regressions for named groups, unmatched optional groups, escaped dollar signs and
  invalid group references.
- All 528 core tests passed. Verified the core JAR and sources JAR, desktop Java compilation and Android
  debug Java compilation.
- GUI rendering, IME composition and physical Android touch behavior remain unverified on devices.

### Distribution

- Git tag and JitPack dependency version: `v1.4.1`.
- Gradle publication version: `1.4.1`.
- Existing public APIs and default shortcuts are unchanged.

## v1.4.0 — 2026-11-22

### Added

- Optional diagnostic quick fixes and current-document code actions with immutable snapshots,
  asynchronous discovery, full-text preview, explicit application and one named undo step. A scoped
  extension offers Alt+Enter discovery and an unbound command for a unique preferred action.
- Generic code-based diagnostic replacement rules, plus an independent diagnostic-batch revision and
  listener which invalidate old actions even when a fresh analysis describes unchanged text.
- An optional document/selection formatting provider and controller with immutable snapshots,
  asynchronous result delivery, cancellation, exact range validation and one named undo step.
  Scoped formatting commands integrate with the command palette and Ctrl/Command+Alt+L.
- A strict token-preserving JSON formatter which edits whitespace gaps, honors indentation options,
  retains numeric and string spellings, and rejects invalid input or excessive nesting atomically.
- A guarded exact-batch overload which checks cancellation/owner lifetime after input filtering,
  preventing a cancelled or unloaded formatter from committing its pending edits.
- An optional go-to-line/column dialog with 1-based input validation, Ctrl/Command+G and command-palette
  integration, fold expansion, keyboard/touch actions and scoped focus/lifecycle cleanup. Desktop and
  Android examples expose the dialog through an action button.
- An optional line-comment extension with configurable prefixes, Ctrl/Command+/, command-palette
  integration, selection-direction preservation and one named undo step. Desktop and Android
  examples select the prefix for their current language and expose an action button.
- An optional Unicode-aware word-editing extension with Ctrl-based Windows/Linux shortcuts,
  Option-based macOS shortcuts, selection extension, word deletion and named undo.
- Unicode-aware document/offset mapping with a lazy Fenwick line-length index and incremental
  single-line updates, plus a repeatable position-mapping benchmark task.
- Exact saved-content tracking through `isDirty()` and `markSaved()`, including asynchronous save
  snapshots, newline normalization and returning to a saved state through undo/redo.
- An implementation review with prioritized follow-up work for platform input validation,
  search performance and additional language tools.

### Fixed

- Regex replacements now expand group references before filtering the actual inserted text.
  Replace All filters each insertion once, including independent calls to stateful filters.
- Identical range, batch and search replacements preserve history/version instead of deleting and
  reinserting unchanged text; batch caret tracking skips those edits. Replace All counts changed matches.
- Range, batch and search replacement preflight now rejects stale coordinates or a newly blocked
  editor after input-filter callbacks.
- Snippets can navigate placeholders and final stops over identical text, including empty stops,
  without rewriting the body or discarding redo. Direct starts reject blocked modes and invalid ranges.
- Completion distinguishes a successfully finished snippet from a rejected insertion, so an unrelated
  document change inside an input filter is not reported as successful snippet acceptance.
- Snippet insertion normalizes expanded CRLF/CR text and placeholder offsets together, snapshots and
  validates parsed templates, and rejects the complete insertion when input filtering removes a
  character. Insertion listeners see the first selected stop; callback rewrites end stale sessions.
- Snippet mirrors use one exact batch, keeping later stops and the edited caret aligned across
  multiline, adjacent and empty fields. Editing any copy also updates affected ancestor mirrors;
  overlapping child/parent changes are merged before filtering. A rejected mirror leaves every other
  copy unchanged and ends navigation while preserving the user's edit.
- Snippets use exact replacement spans to distinguish repeated text and remove overwritten child
  stops while retaining surviving copies. Invalid overlaps and circular parent/mirror dependencies
  reject insertion before editing; nested final stops follow their parent's replacement endpoint.
- Deferred content notifications preserve exact splice coordinates and their previous document
  version, including same-frame typing and reentrant callbacks. Snippet navigation flushes pending
  input before switching stops, follows independent surrounding edits, and ends on stale events.

### Examples and validation

- Updated desktop and Android examples with line-comment and go-to-line actions, plus README
  integration examples and lifecycle documentation for the optional extensions and providers.
- Expanded the regression suite from 237 to 526 tests across 45 test classes, with no failures,
  errors or skipped tests. Coverage includes snippets, exact edits, save points, Unicode mapping,
  diagnostics, code actions, formatting, commands and extension lifecycle/reentry.
- Verified core library packaging, desktop compilation and Android debug compilation. GUI rendering
  and Android device behavior have not been manually verified for this release.

### Distribution

- Git tag and JitPack dependency version: `v1.4.0`.
- Gradle publication version: `1.4.0`.
- Release assets include the core library JAR, source JAR and SHA-256 checksums.
- New editing extensions remain opt-in; native keymap defaults and existing provider integrations
  remain available. This release applies code actions only to the current document.

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
