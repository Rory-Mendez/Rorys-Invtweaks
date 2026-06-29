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
| v0.7.0 | Compatibility and edge-case hardening | done |
| v0.8.0 | Drag armor equip / unequip | done |
| v0.9.0 | Branding, polish, release preparation | done |
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

## v0.7.0 — Compatibility and edge-case hardening

Goal: ensure drag-transfer is safe across all vanilla container types; never act on slots where
auto-transfer would produce unexpected behavior.

- `InvTweaksConst.MOD_VERSION` updated to `"Rory's InvTweaks 0.7.0 (1.2.5)"`.
- **No new config properties.** `enableDragTransfer` and `enableDragDebug` unchanged.
- **No new drag-transfer behavior for safe slots.** Chest, inventory, furnace input/fuel, and
  brewing bottles continue to work exactly as in v0.5.0/v0.6.0.
- **New `isUnsafeSection` helper** in `InvTweaks`: replaces the v0.4.0 crafting-only guard
  with a broader check covering all sections that must never be auto-transferred:
  - `CRAFTING_OUT`, `CRAFTING_IN` — previously guarded; now unified under `isUnsafeSection`
  - `ARMOR` — armor slots; auto-equip is a separate feature
  - `FURNACE_OUT` — smelting output auto-fills like crafting output
  - `ENCHANTMENT` — single slot; removing the item cancels the enchantment
  - `BREWING_INGREDIENT` — removing the ingredient mid-brew silently cancels the brew
- **Debug log updated:** former `reason=crafting` log lines now appear as
  `reason=unsafe_section section=<sectionName>` for all skipped sections.
- **Confirmed safeguards (unchanged since v0.5.0):**
  - Empty slots skipped (`reason=empty`)
  - Invalid slots skipped (`reason=no_section`)
  - Cursor-holding-item skipped (`reason=hand_busy`)
  - Per-slot deduplication via `dragTransferVisited`
  - Clean reset on Shift/LMB release or GUI close
- **docs/SHORTCUT_FLOW.md** updated: v0.7.0 section with container-type compatibility matrix
  and full `isUnsafeSection` rationale.
- `build.bat VERSION=0.7.0`; build produces `build\libs\rorys-invtweaks-0.7.0.zip`.

## v0.8.0 — Drag armor equip / unequip

Goal: when dragging over armor items in the inventory, automatically equip them to the
matching armor slot if it is empty; when dragging over an occupied armor slot, unequip
the armor piece back to the player inventory.

- `InvTweaksConst.MOD_VERSION` updated to `"Rory's InvTweaks 0.8.0 (1.2.5)"`.
- New config property `enableDragArmorEquip=true` (default on) in `InvTweaksConfig` /
  `InvTweaks.cfg`. Controls both equip and unequip. Set to `false` to restore v0.7.0 behavior.
- **Reuses existing game logic:** armor slot matching uses `Slot.isItemValid()`
  (wrapped as `InvTweaksObfuscation.isItemValid()`, same as `InvTweaksHandlerSorting`).
  No item IDs hardcoded; works with vanilla and modded armor.
- New `InvTweaks.tryArmorEquip(...)` helper: called from `doTransferSlot` after the
  unsafe-section guard, before normal `resolveTransferTarget` / transfer logic.
  - Returns `true` → item equipped; caller marks visited and returns.
  - Returns `false` → not armor, no ARMOR section, or all matching slots occupied;
    caller falls through to normal drag transfer.
- New `InvTweaks.tryArmorUnequip(...)` helper: called from `doTransferSlot` BEFORE the
  unsafe-section guard whenever `fromSection == ARMOR` and `armorEquipEnabled=true`.
  - Tries `INVENTORY_NOT_HOTBAR` first, then `INVENTORY_HOTBAR`.
  - If inventory is full: armor stays in the armor slot — nothing is dropped or deleted.
  - Slot is always marked visited before the call (prevents re-triggering on failure).
- `ARMOR` section gate: `InvTweaksContainerManager.hasSection(ARMOR)` is only true in
  `ContainerPlayer` (player inventory screen). Both features are therefore silently inactive
  in chest, furnace, brewing, and workbench GUIs.
- `doTransferSlot` / `processIntermediateSlots` signatures extended with
  `boolean armorEquipEnabled`; `handleDragTransfer` reads the new property and threads it
  through the call chain alongside `debugEnabled`.
- New `armorSlotName(int)` helper: maps armor section index (0-3) to the type name for
  debug logging (helmet / chestplate / leggings / boots).
- **With `enableDragArmorEquip=false`**: behavior is identical to v0.7.0. Neither armor path
  is taken; ARMOR section blocked by `isUnsafeSection`.
- **With `enableDragArmorEquip=true` and occupied armor slot (equip)**: `tryArmorEquip`
  returns false; normal drag transfer runs (armor item moves to chest or hotbar as before).
- **Debug log** (`enableDragDebug=true`):
  ```
  [InvTweaks DragArmor] equipped slot #<n> as <helmet|chestplate|leggings|boots>
  [InvTweaks DragArmor] skipped slot #<n> reason=equip_failed
  [InvTweaks DragArmor] skipped slot #<n> reason=slot_occupied
  [InvTweaks DragArmor] unequipped slot #<n> as <helmet|chestplate|leggings|boots>
  [InvTweaks DragArmor] skipped slot #<n> reason=inv_full
  [InvTweaks DragArmor] skipped slot #<n> reason=unequip_failed
  ```
- `docs/SHORTCUT_FLOW.md`, `docs/INSTALL.md`, `README.md` updated.
- `build.bat VERSION=0.8.0`; produces `build\libs\rorys-invtweaks-0.8.0.zip`.

## v0.9.0 — Branding, polish, release preparation

Goal: clean up and document the project for a stable v1.0.0 release. No new gameplay features.

- `InvTweaksConst.MOD_VERSION` updated to `"Rory's InvTweaks 0.9.0 (1.2.5)"`.
- **Branding**: version string consistently says "Rory's InvTweaks"; upstream attribution preserved in
  class-level javadoc, `docs/UPSTREAM.md`, and `src/doc/license.txt`.
- **Code cleanup:**
  - `isUnsafeSection` ARMOR comment updated to reflect that the ARMOR section is now intercepted
    before the guard (by `tryArmorUnequip`) when `armorEquipEnabled=true`.
  - `saveProperties()` header updated to describe both equip and unequip behaviors for
    `enableDragArmorEquip`.
  - No behavior changes; all v0.8.0 logic preserved exactly.
- **Documentation polish:**
  - `docs/BUILD.md`: stale `0.6.0` zip name references updated to `0.9.0`.
  - `docs/INSTALL.md`: all `0.8.0` version references updated to `0.9.0`.
  - `docs/ROADMAP.md`: v0.8.0 table entry title corrected; v0.9.0 entry and Future Ideas section added.
  - `README.md`, `docs/UPSTREAM.md`, `docs/SHORTCUT_FLOW.md`: already accurate; no changes.
- `build.bat VERSION=0.9.0`; produces `build\libs\rorys-invtweaks-0.9.0.zip`.

---

## Future Ideas

Features considered for post-v1.0.0. Not committed — each needs evaluation before implementation.

| Idea | Notes |
|---|---|
| Optional right-click drag | Shift+RMB drag transfers one item per slot instead of the whole stack. Needs careful gesture distinction from vanilla right-drag. |
| In-game config GUI | Expose Rory-specific options (`enableDragTransfer`, `enableDragArmorEquip`, `enableDragDebug`) in the existing "..." settings screen. |
| Config / package migration | Rename config files and packages from `InvTweaks.*` to `rorys-invtweaks.*`; provide a one-time migration shim. Breaking change — defer until after v1.0.0. |
| Better modded container compatibility | Detect and handle non-standard container layouts (e.g. extra chest rows, modded furnaces). Needs a real modded test environment. |
| Smarter furnace / brewing routing | Shift+drag into a furnace routes fuel to FURNACE_FUEL and smelting input to FURNACE_IN. Currently allowed but without slot-type awareness. Only implement if proven safe across all cases. |

---

## v1.0.0 — Stable release

Goals:
- All v0.x features stable and tested on MC 1.2.5 via Prism Launcher.
- Build produces a distributable `.zip` compatible with the `1.2.5 com mods` Prism instance.
