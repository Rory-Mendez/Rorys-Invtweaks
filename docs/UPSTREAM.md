# Upstream

## Original Project

**Inventory Tweaks** — a client-side Minecraft mod that lets players sort their inventory and chests with a single key press, automatically replaces broken tools and consumed items, and provides a set of configurable keyboard shortcuts for moving items between containers.

---

## Upstream Repository

- **GitHub**: `https://github.com/mkalam-alami/inventory-tweaks`
  (also mirrored under the earlier handle `jimeowan`)
- **Documentation**: `http://modding.kalam-alami.net/invtweaks`

---

## Imported Version

| Field | Value |
| --- | --- |
| Git import commit | `bcfc092` — *chore: import upstream Inventory Tweaks 1.41b source* |
| Import commit label | **1.41b** (see version analysis below — the label is inaccurate) |
| Actual source version | **1.42-SNAPSHOT targeting Minecraft 1.2.5** |
| `InvTweaksConst.MOD_VERSION` | `"1.42 (1.2.5) SNAPSHOT"` |
| `InvTweaksConst.TREE_VERSION` | `"1.3.0"` |
| `pom.xml` artifact version | `1.42-1.2.5-SNAPSHOT` |
| Target Minecraft version | **1.2.5** |

---

## Version Analysis: 1.41b vs 1.42-SNAPSHOT

The git import commit is labelled **"1.41b"** but the imported source is the **1.42 development snapshot**, not the 1.41b release. This was confirmed by comparing the imported source directly against the compiled `InvTweaks-1.41b-1.2.5.zip` that runs in the local Prism Launcher instance.

### Evidence

| Indicator | 1.41b binary (running mod) | Imported source (our repo) |
| --- | --- | --- |
| `InvTweaksConst.MOD_VERSION` | `"1.41b (1.2.4)"` | `"1.42 (1.2.5) SNAPSHOT"` |
| `InvTweaksConst.TREE_VERSION` | `"1.1.0"` | `"1.3.0"` |
| `InvTweaksShortcutType` | Inner class of `InvTweaksHandlerShortcuts` (`ShortcutType`) | Top-level enum (`InvTweaksShortcutType.java`) |
| `InvTweaksShortcutMapping` | Does not exist | Separate top-level class |
| Minecraft target | 1.2.4 | 1.2.5 |
| Changelog last entry | 1.41 (1.2.4) dated 2012-03-27 | No 1.42 entry (never shipped) |

### What happened

The upstream repository tagged the last stable release as **1.41b** for Minecraft 1.2.4. After that release, the main branch was updated to prepare **1.42** for Minecraft 1.2.5 — the version string was bumped, the item tree was updated to tree version 1.3.0, and `ShortcutType` was refactored from an inner class to a top-level enum. This 1.42 development work was **never shipped as a public release**; the project was abandoned shortly after.

The import took the **HEAD of the main branch** at the time of import, which was this in-progress 1.42 state. The commit message mislabelled it as "1.41b" since that was the last known public release. The label was corrected in this document.

### What this means for the fork

Rory's Inventory Tweaks is based on the **1.42 development snapshot** of the upstream code, not the released 1.41b. The differences are small but real:

- The mod targets **Minecraft 1.2.5**, not 1.2.4.
- The item tree (`DefaultTree.dat`) is the updated 1.3.0 tree, which includes MC 1.2.5 content.
- If upgrading from a running 1.41b installation, the tree will be auto-replaced on first launch (the mod detects the version mismatch via `TREE_VERSION` and replaces the tree file with a backup).
- The refactored shortcut classes (`InvTweaksShortcutType`, `InvTweaksShortcutMapping`) have identical observable behavior to the 1.41b inner class — the refactor was purely structural.

---

## Original License

**MIT License**

```
Copyright (c) 2011-2012 Marwane Kalam-Alami

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

Full text preserved in [`LICENSE.md`](../LICENSE.md) and [`src/doc/license.txt`](../src/doc/license.txt).

---

## Original Authors

| Name | Role |
| --- | --- |
| **Marwane Kalam-Alami** (`Jimeo Wan`) | Creator and primary author. All source files carry the header `@author Jimeo Wan` with contact `jimeo.wan (at) gmail (dot) com`. |

Community contributors credited in the changelog (translations and bug reports):
GazCore (zh_TW), Ryo567 & Aledrobt (es_ES), 0l1vR (de_DE), TH3steven (nl_NL), Ezspecial & Ricalou (pt_PT), Hugsim, Doyle3694 & Brott (sv_SE), JonathanHertz (da_DK), Fishy (pl_PL), Necrontoend & VADemon (ru_RU), Ricalou & NTWalker (ja_JP), IvyMichael & NTWalker (zh_CN), and others.

---

## Purpose of Rory's Inventory Tweaks

**Rory's Inventory Tweaks** is a fork of the original mod maintained within the RoryLab workspace alongside:

- `rorys-utilities` — quality-of-life utilities mod
- `rorys-excavation` — vein-mining / area-excavation mod
- `rorys-mod-core` — shared infrastructure for the Rory's Mod suite

The fork exists to:

1. **Preserve and run the 1.42-SNAPSHOT codebase on MC 1.2.5** — the upstream project was abandoned in 2012 and never shipped 1.42.
2. **Integrate with the RoryLab toolchain** — adopt the same build pipeline (`javac` + Prism jars), documentation standards, and project conventions used across all Rory's Mod projects.
3. **Extend and customise** — future versions may add features, fix bugs, or adjust behaviours while respecting the MIT licence and attributing the original work.

The source is imported verbatim from the upstream main branch. The only change applied for v0.0.1 is `InvTweaksConst.MOD_VERSION` — updated from the upstream snapshot placeholder `"1.42 (1.2.5) SNAPSHOT"` to `"Rory's InvTweaks 0.0.1 (1.2.5)"` to identify the fork in-game. No gameplay logic, shortcut behaviour, sorting algorithms, or configuration formats have been modified.
