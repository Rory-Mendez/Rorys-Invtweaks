# Rory's Inventory Tweaks

A fork of the abandoned **Inventory Tweaks 1.42 development snapshot** (original author: Marwane Kalam-Alami, MIT licence).
Targets Minecraft **1.2.5** with **Forge 3.4.9.171** via Prism Launcher.

---

## Features

### From the original Inventory Tweaks

- **Sort inventory or chest** — press `R` (or your configured sort key).
- **Middle-click chest sort** — middle-click a chest to sort it; click again to cycle sort algorithms.
- **Keyboard shortcut transfers** — hold a modifier while clicking a slot:

  | Modifier | Action |
  | --- | --- |
  | `Shift` + click | Move one stack to the opposite section |
  | `Ctrl` + click | Move all stacks of the same item type |
  | `Space` + click | Move everything from the source section |
  | `1`–`9` + click | Move stack directly to that hotbar slot |

- **Auto-refill** — automatically refills a depleted hotbar slot from the inventory.
- **In-game settings** — access via the **"..."** button added to the inventory and chest screens.

### Rory additions

#### Shift+drag transfer (v0.4.0+)

Hold **Shift + left mouse button** and drag across inventory or chest slots. Each slot the cursor
enters is immediately transferred to its default target section — the same transfer that a
Shift-click would perform on that slot individually.

- **CHEST → INVENTORY**: dragging across chest slots moves them to the player inventory.
- **INVENTORY → CHEST**: dragging across inventory slots moves them to the open chest (or between hotbar and main inventory when no chest is open).
- Crafting input/output slots are skipped.
- Each slot is processed at most once per drag gesture (re-entering a slot does nothing).
- **Fast drag recovery** (v0.5.0): if the cursor skips slots between ticks while moving along a
  row or column, those intermediate slots are recovered and processed in order. Diagonal skips
  are not recovered (documented trade-off).

---

## Configuration

Config file: `.minecraft/config/InvTweaks.cfg`

Generated automatically on first launch. All settings can also be changed via the **"..."**
in-game settings screen. The file is rewritten on every config save; comments at the top of the
file document each option.

> **Note on comments:** Java's `Properties` format does not support per-line comments adjacent to
> individual keys. All documentation appears in the header block at the top of the file.

### Rory-specific options

| Property | Default | Description |
| --- | --- | --- |
| `enableDragTransfer` | `true` | Enable Shift+left-drag transfer. Set to `false` to disable the feature entirely while keeping all other behaviour. |
| `enableDragDebug` | `false` | Log drag-gesture events to stdout: slot entry, interpolation, and transfers. Set to `true` only for troubleshooting; leave `false` during normal play. |

### Original options (selected)

| Property | Default | Description |
| --- | --- | --- |
| `enableMiddleClick` | `true` | Middle-click chest sorting. |
| `enableShortcuts` | `true` | Keyboard shortcut transfers. |
| `enableSortingOnPickup` | `false` | Auto-sort inventory when picking up items. |
| `enableAutoRefill` | `true` | Refill depleted hotbar slots automatically. |
| `enableSounds` | `true` | Play click sound on sort/transfer. |

#### How missing properties are handled

On first launch (empty config file) all properties use their defaults. On subsequent launches
the mod reads the user's file and merges it with the defaults: existing user values are
preserved; any property absent from the file falls back to its default. New Rory-specific
properties added in a version update are therefore automatically available with their defaults
without requiring any action from the user.

---

## Drag-debug output reference

When `enableDragDebug=true`, three log prefixes appear on stdout:

| Prefix | When it fires |
| --- | --- |
| `[InvTweaks DragDebug]` | On every input/slot state change (GUI type, LMB, RMB, Shift, slot number). Only fires when something actually changed; silent when idle. |
| `[InvTweaks DragHover]` | Once per newly entered slot while Shift+LMB is held over a valid GUI. |
| `[InvTweaks DragTransfer]` | Once per slot processed or skipped during a drag-transfer gesture. |

---

## Installation

See [docs/INSTALL.md](docs/INSTALL.md).

## Building from source

See [docs/BUILD.md](docs/BUILD.md).

## Licence

MIT — see [src/doc/license.txt](src/doc/license.txt).

Original project by Marwane Kalam-Alami: https://github.com/mkalam-alami/inventory-tweaks
