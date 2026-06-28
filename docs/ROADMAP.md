# Roadmap — Rory's Inventory Tweaks

| Version | Goal | Status |
|---|---|---|
| v0.0.1 | Buildable upstream import (1.42 snapshot, MC 1.2.5) | done |
| v0.1.0 | Shortcut flow documentation | done |
| v0.2.0 | Input instrumentation / logging (keyboard, mouse, slot events) | done |
| v0.3.0 | Hover-slot detection (live slot tracking while mouse moves) | done |
| v0.4.0 | Drag-transfer prototype (Shift+LMB drag across slots) | planned |
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

See `docs/SHORTCUT_FLOW.md` → *Proposed Future Drag-Transfer Hook* and *Risks and Safeguards*
for the full design.

Hook site: `InvTweaks.handleShortcuts` (InvTweaks.java:642), inside the existing
`Mouse.isButtonDown(0)` branch, parallel to the `mouseWasDown` rising-edge block.

## v1.0.0 — Stable release

Goals:
- All v0.x features stable and tested on MC 1.2.5 via Prism Launcher.
- Build produces a distributable `.zip` compatible with the `1.2.5 com mods` Prism instance.
- Remove the JDK blocker note (JDK installed by then).
