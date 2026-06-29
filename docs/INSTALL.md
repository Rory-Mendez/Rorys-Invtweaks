# Installing Rory's Inventory Tweaks

Target: Minecraft **1.2.5** with **Forge 3.4.9.171** via **Prism Launcher**

---

## Requirements

| Requirement | Notes |
| --- | --- |
| Prism Launcher | The mod is tested against the `1.2.5 com mods` Prism instance. |
| Forge 3.4.9.171 | The instance must have Forge 3.4.9.171 as a component. This is already the case for the `1.2.5 com mods` instance. |
| No pre-existing InvTweaks | Remove any earlier version of Inventory Tweaks from the instance `mods/` folder before installing. |

---

## Getting the ZIP

### Option A — Use the pre-built release

Download `rorys-invtweaks-0.9.0.zip` from the repository releases page.

### Option B — Build from source

Follow [BUILD.md](BUILD.md). Requires a Java 8 JDK. The output is `build/libs/rorys-invtweaks-0.9.0.zip`.

---

## Installing into Prism

1. Open Prism Launcher.
2. Right-click the `1.2.5 com mods` instance → **Edit** → **Mods** tab.
3. Click **Add file** and select `rorys-invtweaks-0.9.0.zip`.

Alternatively, copy the ZIP directly into the instance `mods/` folder:

```
%APPDATA%\PrismLauncher\instances\1.2.5 com mods\minecraft\mods\
```

If an older `InvTweaks-*.zip` is present in that folder, **delete it first**.

---

## First launch

On first launch with Rory's Inventory Tweaks installed:

- The mod creates two config files in `.minecraft/config/`:
  - `InvTweaks.cfg` — feature toggles and shortcut bindings.
  - `InvTweaksRules.txt` — sorting rules (one rule per line).
- It also writes `InvTweaksTree.txt` — the item category hierarchy (XML format).

If you previously ran **Inventory Tweaks 1.41b**, you may already have these files. Because this build carries **tree version 1.3.0** (vs 1.1.0 in 1.41b), the mod will automatically back up and replace the existing `InvTweaksTree.txt` on first launch. Your rules file and settings are preserved.

---

## Configuration

### Config file location

```
%APPDATA%\.minecraft\config\InvTweaks.cfg
```

The file is created automatically on first launch. It is a standard Java properties file
(`key=value`, one per line). A documented header block at the top of the file describes
each option. All settings can also be changed via the in-game **"..."** settings button.

### Rory-specific options

| Property | Default | Description |
| --- | --- | --- |
| `enableDragTransfer` | `true` | Hold **Shift + left mouse button** and drag across slots to transfer each one. Set to `false` to disable while keeping all other features. |
| `enableDragArmorEquip` | `true` | While drag-transferring, automatically equip armor items to the matching empty armor slot (helmet/chestplate/leggings/boots); drag over an equipped armor slot to unequip it back to inventory. Works with vanilla and modded armor. Set to `false` to restore v0.7.0 behavior. Requires `enableDragTransfer=true`. |
| `enableDragDebug` | `false` | Log drag-gesture events to stdout for troubleshooting. Leave `false` during normal play. |

### How missing properties are handled

All properties have built-in defaults. If a property is absent from your `InvTweaks.cfg`
(for example after upgrading from an older version), it silently uses its default value.
**No manual editing is required when upgrading.**

---

## Verifying the installation

Press **R** (or your configured sort key) while in your inventory. Items should sort.

Open the inventory and look for the **"..."** settings button near the top-right of the
inventory grid. This button is added by the mod; if it appears, the mod is loaded.

To verify drag-transfer: open a chest with items, hold **Shift**, press and hold
**left mouse button**, and drag slowly across several chest slots. Each slot should transfer
to your inventory as the cursor passes over it.

In the Forge mod list (if accessible), the mod shows as **"Rory's InvTweaks 0.9.0 (1.2.5)"**.

---

## Uninstalling

Delete `rorys-invtweaks-0.9.0.zip` from the instance `mods/` folder.

Config files in `.minecraft/config/` are left behind. Delete them manually if desired:

```
.minecraft/config/InvTweaks.cfg
.minecraft/config/InvTweaksRules.txt
.minecraft/config/InvTweaksTree.txt
```

---

## Shortcut reference

| Shortcut | Default binding | Action |
| --- | --- | --- |
| Sort key | **R** | Sort inventory or chest. Hold to cycle rulesets. |
| Middle click | Mouse button 3 | Sort chest (click again to cycle sort algorithms). |
| Shift + click | Held while clicking slot | Move one stack to opposite section. |
| Ctrl + click | Held while clicking slot | Move all stacks of the same item type. |
| Space + click | Held while clicking slot | Move everything from the source section. |
| 1–9 + click | Number key held while clicking | Move stack directly to that hotbar slot. |
| **Shift + left drag** | **Hold Shift + LMB, drag** | **Transfer each dragged slot (Rory addition).** |

All shortcuts are configurable in `.minecraft/config/InvTweaks.cfg` or via the in-game **"..."** settings screen.
