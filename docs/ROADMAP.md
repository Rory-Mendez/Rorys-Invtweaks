# Roadmap — Rory's Inventory Tweaks

| Version | Goal | Status |
|---|---|---|
| v0.0.1 | Buildable upstream import (1.42 snapshot, MC 1.2.5) | done |
| v0.1.0 | Shortcut flow documentation | done |
| v0.2.0 | Input instrumentation / logging (keyboard, mouse, slot events) | done |
| v0.3.0 | Hover-slot detection (live slot tracking while mouse moves) | done |
| v0.4.0 | Drag-transfer prototype (Shift+LMB drag across slots) | done |
| v0.5.0 | Drag-transfer polish (row/column interpolation for fast drags) | done |
| v0.6.0 | Configuration polish and user-facing documentation | done |
| v1.0.0 | Stable release | planned |

---

## v0.0.1 — Buildable upstream import

- Forked the unreleased 1.42 development snapshot from Marwane Kalam-Alami's repository.
- Set `InvTweaksConst.MOD_VERSION` to `"Rory's InvTweaks 0.0.1 (1.2.5)"`.
- Created `build.bat` using Prism Launcher cached jars (no Maven).
- Wrote `docs/ARCHITECTURE.md`, `docs/BUILD.md`, `docs/INSTALL.md`, `docs/UPSTREAM.md`.
- **Blocker:** No JDK on this machine. Build cannot run until Eclipse Temurin JDK 8 (or 8–21) is installed.

## v0.1.0 — Shortcut flow documentation

- No source code changes.
- `docs/SHORTCUT_FLOW.md`: full method-level trace of the shortcut pipeline, slot detection,
  click execution, proposed drag-transfer hook site, and risk catalogue.
- `docs/ROADMAP.md`: this file.

## v0.2.0 — Input instrumentation / logging

- No gameplay behavior changes.
- New `.cfg` property `enableDragDebug=false` (added to `InvTweaksConfig`, persists in `InvTweaks.cfg`).
- New `InvTweaks.handleDragDebug(vp guiScreen)` method called every GUI tick after `handleShortcuts`.
- When disabled: single property equality check per tick, zero overhead.
- When enabled: logs to stdout (prefixed `[InvTweaks DragDebug]`) on every state change:
  GUI type, LMB held, RMB held, Shift held, current slot number + section, previous slot on change.
- Slot detection uses existing `InvTweaksContainerManager.getSlotAtMousePosition()` wrapped in try/catch.
- Safe for all containers (inventory, chest, furnace, workbench, brewing, enchanting, modded).
- JDK discovered: Eclipse Temurin JDK 8 at `%LOCALAPPDATA%\Programs\Eclipse Adoptium\jdk-8.0.492.9-hotspot`.
- `build.bat` updated to set `VERSION=0.2.0`; build produces `build\libs\rorys-invtweaks-0.2.0.zip`.

## v0.3.0 — Hover-slot detection

Goal: prove that per-tick slot tracking works correctly across all container types, before
attempting item movement.

- New `InvTweaks.handleDragHover(vp guiScreen)` method, called every GUI tick after `handleDragDebug`.
- Tracks four internal state fields: `dragHoverCurrentSlot`, `dragHoverPrevSlot`,
  `dragHoverEnteredNew`, `dragHoverGestureActive`.
- A gesture is active when Shift+LMB is held over a valid chest or inventory GUI.
- Fires `[InvTweaks DragHover] entered slot #<n> section=<section>` to stdout exactly once per
  newly entered slot; suppresses repeated events while the cursor stays on the same slot.
- Resets cleanly when LMB is released, Shift is released, GUI closes, or the cursor leaves all slots.
- All output gated on `enableDragDebug=true`; zero extra output when disabled.
- No items are moved; existing shortcut behavior is unchanged.
- `build.bat VERSION=0.3.0`; build produces `build\libs\rorys-invtweaks-0.3.0.zip`.

## v0.4.0 — Drag-transfer prototype

Goal: implement Shift+LMB drag that transfers each newly entered slot to its default target.

- New config property `enableDragTransfer=true` in `InvTweaksConfig` / `InvTweaks.cfg`.
  Set to `false` to disable drag-transfer while preserving all other behaviour.
- New state in `InvTweaks`: `dragTransferCurrentSlot` (int), `dragTransferVisited` (Set<Integer>).
- New `InvTweaks.handleDragTransfer(vp guiScreen)` called every GUI tick after `handleDragHover`.
- New private helpers `resolveTransferTarget` and `findDragDestIndex` mirror the target-section
  and merge-first logic from `InvTweaksHandlerShortcuts` without reinventing the algorithm.
- Does NOT call `handleShortcut()` — calls `InvTweaksContainerManager.move()` directly to avoid
  the `Mouse.destroy()` + `Mouse.create()` side-effect that would break mouse state mid-drag.
- Transfer executes the MOVE_ONE_STACK loop (move until source slot is empty or no space left).
- Target section follows the same implicit rule as existing shortcuts: CHEST→INVENTORY,
  HOTBAR→CHEST (or NOT_HOTBAR if no chest), NOT_HOTBAR→CHEST (or HOTBAR if no chest).
- `CRAFTING_IN` / `CRAFTING_OUT` slots are explicitly skipped.
- Each slot processed at most once per gesture via `dragTransferVisited`.
- Resets cleanly when LMB is released, Shift is released, GUI closes, or feature is disabled.
- Debug output (`[InvTweaks DragTransfer] moved/skipped …`) gated on `enableDragDebug=true`.
- `build.bat VERSION=0.4.0`; build produces `build\libs\rorys-invtweaks-0.4.0.zip`.

## v0.5.0 — Drag-transfer polish

Goal: reduce skipped slots when the mouse moves quickly across a chest row or column.

- No new config properties; `enableDragTransfer` and `enableDragDebug` govern behavior as before.
- New state: `dragTransferCurrentSlotX`, `dragTransferCurrentSlotY` track the display-position
  of the last seen slot, enabling axis detection between successive slots.
- New `processIntermediateSlots`: when the cursor jumps from slot A to slot B between ticks,
  scans every slot in the container for ones whose display position is strictly between A and B
  along the same row (equal `yDisplayPosition`) or same column (equal `xDisplayPosition`).
  Each qualifying slot is passed to `doTransferSlot` before B is processed.
- New `doTransferSlot`: extracted from the v0.4.0 inline block. All safeguards in one place:
  visited-set deduplication, `getHoldStack()` null check (new in v0.5.0), empty-slot skip,
  section/index validation, crafting-slot skip, target-section resolution, MOVE_ONE_STACK loop.
- New `resetDragTransfer`: clears all four drag-transfer state fields atomically.
- New `isBetween` static helper: strict between-check, direction-independent.
- **Trade-off**: diagonal jumps are not interpolated — path reconstruction is ambiguous for
  corner slots. When `enableDragDebug=true`, a `reason=diagonal` log line marks the skip.
  Extremely fast diagonal movement may still miss a corner slot; this is documented.
- New debug log lines: `interp slot #<n> axis=row/col`, `interp skipped reason=diagonal`,
  `skipped slot #<n> reason=hand_busy`.
- `build.bat VERSION=0.5.0`; build produces `build\libs\rorys-invtweaks-0.5.0.zip`.

## v0.6.0 — Configuration polish and user-facing documentation

Goal: ensure the mod is understandable and safe to configure without reading source code.

- `InvTweaksConst.MOD_VERSION` updated to `"Rory's InvTweaks 0.6.0 (1.2.5)"`.
- **No drag-transfer behavior changes.** All v0.5.0 transfer logic is preserved exactly.
- **No new config properties.** `enableDragTransfer` and `enableDragDebug` defaults confirmed:
  - `enableDragTransfer=true` (set in `InvTweaksConfig.reset()` since v0.4.0). ✓
  - `enableDragDebug=false` (set in `InvTweaksConfig.reset()` since v0.2.0). ✓
- **Config safety confirmed:** `InvTweaksConfig.load()` calls `reset()` (all defaults) then
  `loadProperties()` (overlays user file). Keys absent from the user's file keep their defaults.
  No user value is ever overwritten during an upgrade.
- **Config file header updated** (`InvTweaksConfig.saveProperties()`): the `Properties.store()`
  comment block now documents `enableDragTransfer` and `enableDragDebug` with their defaults and
  descriptions. Per-property inline comments are not possible with Java's `Properties` API
  (the whole file is rewritten on save with no per-key annotation support). The header block is
  the only supported mechanism.
- **`handleDragDebug` noise audit:** already state-change-only (`if (stateChanged)` gate); no
  per-tick output when nothing changes. No code change needed. Documented in README.
- **README.md** rewritten: Rory-specific features, config table, drag-debug log reference,
  link to INSTALL.md and BUILD.md. Replaces the upstream placeholder.
- **docs/INSTALL.md** updated to v0.6.0: version references corrected; new config options
  section documenting `enableDragTransfer` and `enableDragDebug`; upgrade note on missing
  properties; drag-transfer added to shortcut reference table.
- **docs/BUILD.md** version references updated from 0.0.1 to 0.6.0.
- `build.bat VERSION=0.6.0`; build produces `build\libs\rorys-invtweaks-0.6.0.zip`.

## v1.0.0 — Stable release

Goals:
- All v0.x features stable and tested on MC 1.2.5 via Prism Launcher.
- Build produces a distributable `.zip` compatible with the `1.2.5 com mods` Prism instance.
- Remove the JDK blocker note (JDK installed by then).
