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

Download `rorys-invtweaks-0.0.1.zip` from the repository releases page.

### Option B — Build from source

Follow [BUILD.md](BUILD.md). Requires a Java 8 JDK. The output is `build/libs/rorys-invtweaks-0.0.1.zip`.

---

## Installing into Prism

1. Open Prism Launcher.
2. Right-click the `1.2.5 com mods` instance → **Edit** → **Mods** tab.
3. Click **Add file** and select `rorys-invtweaks-0.0.1.zip`.

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

## Verifying the installation

Press **R** (or your configured sort key) while in your inventory. Items should sort.

Open the inventory and look for the **"..."** settings button near the top-right of the inventory grid. This button is added by the mod; if it appears, the mod is loaded.

In the Forge mod list (if accessible), the mod shows as **"Rory's InvTweaks 0.0.1 (1.2.5)"**.

---

## Uninstalling

Delete `rorys-invtweaks-0.0.1.zip` from the instance `mods/` folder.

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

All shortcuts are configurable in `.minecraft/config/InvTweaks.cfg` or via the in-game **"..."** settings screen.
