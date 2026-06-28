# Roadmap — Rory's Inventory Tweaks

| Version | Goal | Status |
|---|---|---|
| v0.0.1 | Buildable upstream import (1.42 snapshot, MC 1.2.5) | done |
| v0.1.0 | Shortcut flow documentation | done |
| v0.2.0 | Input instrumentation / logging (keyboard, mouse, slot events) | planned |
| v0.3.0 | Hover-slot detection (live slot tracking while mouse moves) | planned |
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

Goal: add optional debug logging to observe keyboard/mouse state and slot events at runtime
without affecting gameplay. Foundation for validating the drag-transfer design before implementation.

Planned scope:
- A compile-time or config-time debug flag in `InvTweaksConst`.
- Log entries in `handleShortcuts` for raw button state and `mouseWasDown` transitions.
- Log entries in `computeShortcutToTrigger` for the resolved slot, section, index, and shortcut type.
- Log entries in `click()` for each window-click issued.

## v0.3.0 — Hover-slot detection

Goal: prove that per-tick slot tracking works correctly across all container types, before
attempting item movement.

Planned scope:
- Add `lastHoveredSlot` tracking to `InvTweaks` (updated every GUI tick).
- Log or display (chat/overlay) the section and index of the slot under the cursor.
- Verify correct behavior across: player inventory, chest, dispenser, furnace, workbench,
  brewing stand, enchantment table.

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
