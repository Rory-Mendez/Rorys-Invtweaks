# Architecture

Inventory Tweaks is structured as a client-side Minecraft mod for the **ModLoader** framework (Minecraft 1.2.x era, pre-Forge/FML). All code lives in the default (unnamed) package, with a small `invtweaks` sub-package for pure-Java support classes.

---

## Layer Map

```
┌──────────────────────────────────────────────────────────┐
│                    mod_InvTweaks.java                    │  ← ModLoader entry point
└──────────────────────┬───────────────────────────────────┘
                       │  delegates all events to:
                       ▼
┌──────────────────────────────────────────────────────────┐
│                      InvTweaks.java                      │  ← Central dispatcher
│  onTickInGame · onTickInGUI · onSortingKeyPressed        │
│  onItemPickup · handleMiddleClick · handleShortcuts      │
└──────┬────────────┬──────────────┬────────────┬──────────┘
       │            │              │            │
       ▼            ▼              ▼            ▼
  Handler       Handler        Handler      GUI Layout
  Sorting       Shortcuts      AutoRefill   (adds buttons
       │            │                        to GUIs)
       └────────────┴──────────┐
                               ▼
                   InvTweaksContainerManager
                   InvTweaksContainerSection­Manager
                               │
                               ▼
                   InvTweaksObfuscation
                   (thin wrappers over obf MC types)
```

---

## Packages

| Package | Contents |
| --- | --- |
| `(default)` | All classes that touch obfuscated Minecraft runtime types (`vp`, `gb`, `aan`, `afu`, `yu`, `dd`, …). Java does not allow named-package code to import default-package classes, so everything that references raw MC objects must live here. |
| `invtweaks` | Pure-Java support classes: constants, item tree model, item tree loader. No Forge, no LWJGL, no obfuscated types. |
| `invtweaks.lang` | Localization `.properties` files (14 languages). |

---

## Default-Package Constraint

Obfuscated Minecraft game classes (`vp`, `gb`, `aan`, `afu`, `yu`, `dd`, `yw`, `vq`, `xd`, etc.) live in the default (unnamed) package. Any class that needs to call a method or read a field on a raw Minecraft object must also live in the default package.

All handler, GUI, container, and config classes therefore live in the default package. Only `InvTweaksConst`, the item tree model, and the item tree loader are clean enough to live in the `invtweaks` sub-package.

---

## Entry Point — `mod_InvTweaks.java`

ModLoader entry point. Discovered by ModLoader because it is named `mod_*` in the default package and extends `BaseMod`. On `load()`:

1. Registers the sort key binding (`R` by default) via `ModLoader.registerKey`.
2. Registers game-tick and GUI-tick hooks via `ModLoader.setInGameHook` / `setInGUIHook`.
3. Instantiates `InvTweaks(mc)`.

ModLoader calls back into this class on every relevant event, which `mod_InvTweaks` delegates to `InvTweaks`.

---

## Central Dispatcher — `InvTweaks.java`

Extends `InvTweaksObfuscation`. Receives every ModLoader event and routes it to the correct subsystem. Owns:

- `InvTweaksConfigManager cfgManager` — configuration loading and handler lifecycle.
- `int chestAlgorithm` — tracks which chest-sort algorithm to use next on middle click.
- `aan[] hotbarClone` — snapshot of the hotbar taken each tick to detect item pickups.

Key methods:

| Method | Trigger | Responsibility |
| --- | --- | --- |
| `onTickInGame()` | Every game tick (no GUI open) | Clones hotbar, handles config switch, auto-refill. |
| `onTickInGUI(guiScreen)` | Every tick while a GUI is open | Middle click sort, GUI button layout, shortcut dispatch. |
| `onSortingKeyPressed()` | Sort key pressed | Triggers inventory/chest sort. |
| `onItemPickup()` | Item picked up | Moves newly arrived hotbar item to its preferred slot. |

---

## Obfuscation Layer — `InvTweaksObfuscation.java`

Single class that wraps every obfuscated Minecraft field and method access behind a readable Java method. Examples: `getMainInventory()` reads `InventoryPlayer.a`, `clickInventory()` calls `PlayerController.a(...)`, `getFocusedStack()` calls `InventoryPlayer.b()`.

All handlers extend this class to gain access to Minecraft internals without scattering `mc.h.ap.a[i]`-style expressions through business logic.

The class also contains `is*` type-check helpers (`isGuiChest`, `isContainerPlayer`, `isItemArmor`, etc.) that compare against the obfuscated class objects (`zn.class`, `y.class`, `ql.class`, …).

---

## Configuration System

### Files on disk

| File | Purpose |
| --- | --- |
| `.minecraft/config/InvTweaks.cfg` | Key-value properties (enable/disable features, shortcut bindings). |
| `.minecraft/config/InvTweaksRules.txt` | Sorting rules — one rule per line, e.g. `A1 sword`. |
| `.minecraft/config/InvTweaksTree.txt` | Item hierarchy tree (XML format, `.xml` extension on older versions). |

### Classes

- **`InvTweaksConfigManager`** — owns the config, reloads it when files change, holds the three handler instances (sorting, shortcuts, auto-refill).
- **`InvTweaksConfig`** — the parsed config: properties map, item tree, and ordered list of rulesets. Provides `switchConfig()` to cycle through multiple named rulesets.
- **`InvTweaksConfigProperties`** — reads/writes the `.cfg` properties file.
- **`InvTweaksConfigInventoryRuleset`** — one named ruleset (a section of the rules file delimited by a heading).
- **`InvTweaksConfigSortingRule`** — one parsed sorting rule: keyword + preferred slots array.
- **`InvTweaksConfigSortingRuleType`** — enum of rule shape types (column, row, tile, rectangle, slot).

---

## Item Tree — `invtweaks` package

The item tree is an XML hierarchy of item categories and items. It defines the canonical ordering of all Minecraft items and is used by the sorting algorithm to determine where an item should go.

- **`InvTweaksItemTree`** — in-memory tree. Provides `getItems(id, damage)` to look up `InvTweaksItemTreeItem`s and `matches(items, keyword)` to test whether an item belongs to a named category.
- **`InvTweaksItemTreeCategory`** — internal node (a named category, e.g. `sword`).
- **`InvTweaksItemTreeItem`** — leaf node (a concrete item with ID, damage value, and tree-order integer).
- **`InvTweaksItemTreeLoader`** — SAX XML parser that produces an `InvTweaksItemTree` from the `.txt` config file.

The `order` integer on each leaf is assigned sequentially by the loader during parse. Lower order = higher sorting priority.

---

## Sorting — `InvTweaksHandlerSorting.java`

Constructs a new `InvTweaksContainerSectionManager` over the target section and sorts it in one of five modes:

| Constant | Mode | Used for |
| --- | --- | --- |
| `ALGORITHM_DEFAULT` | Item-tree order, no spatial structure | Middle-click chest (first click) |
| `ALGORITHM_VERTICAL` | Item types arranged in columns | Chest sort button "v" |
| `ALGORITHM_HORIZONTAL` | Item types arranged in rows | Chest sort button "h" |
| `ALGORITHM_INVENTORY` | Item-tree order + armor equip + crafting slot clear | Player inventory sort |
| `ALGORITHM_EVEN_STACKS` | Spread stacks evenly | (Prepared, not yet triggered) |

The `sort()` method:
1. Puts down any held item.
2. For `ALGORITHM_INVENTORY`: clears crafting slots, merges stackable items into locked slots, equips best available armor.
3. For all non-DEFAULT modes: iterates rules by descending priority, moves matching items to their preferred slots using an internal `move(i, j, priority)` that handles merging and swapping.
4. Falls through to `defaultSorting()` for any unmoved items (fills remaining gaps by item-tree order).

The spatial (VERTICAL/HORIZONTAL) modes do not use the user's rules file. Instead, `computeLineSortingRules()` synthesises temporary rules from the current container contents, assigning rectangular regions to each distinct item type.

---

## Shortcut Handling — `InvTweaksHandlerShortcuts.java`

Called by `InvTweaks.handleShortcuts()` on every GUI tick when a mouse button is down. Flow:

1. `updatePressedKeys()` — reads current keyboard state into a `Map<keyCode, Boolean>`.
2. `computeShortcutToTrigger()` — detects the slot under the cursor, checks which shortcut key combination is held, resolves target section (CHEST ↔ INVENTORY based on context and UP/DOWN modifiers), returns a `ShortcutConfig` describing the move.
3. `runShortcut()` — executes the move via `InvTweaksContainerManager`.
4. `Mouse.destroy()` / `Mouse.create()` — resets mouse state to suppress the default Minecraft click that would otherwise follow.

### Shortcut types (`InvTweaksShortcutType.java`)

| Type | Default binding | Behavior |
| --- | --- | --- |
| `MOVE_ONE_STACK` | Shift+click | Move one full stack to the opposite section. |
| `MOVE_ONE_ITEM` | Configurable | Move one item from the stack. Uses right-click repeat. |
| `MOVE_ALL_ITEMS` | Ctrl+click | Move all stacks of the same item type. |
| `MOVE_EVERYTHING` | Space+click | Move all items from the source section. |
| `MOVE_TO_SPECIFIC_HOTBAR_SLOT` | 1–9 + click | Move stack directly to that hotbar slot. |
| `MOVE_UP` | Forward key + click | Move toward the top section. |
| `MOVE_DOWN` | Back key + click | Move toward the bottom section. |
| `DROP` | Configurable | Drop the item. |

---

## Container Management

### `InvTweaksContainerSection.java` (enum)

Names for every logical region of a Minecraft container:
`INVENTORY`, `INVENTORY_HOTBAR`, `INVENTORY_NOT_HOTBAR`, `CHEST`, `CRAFTING_IN`, `CRAFTING_OUT`, `ARMOR`, `FURNACE_IN`, `FURNACE_OUT`, `FURNACE_FUEL`, `ENCHANTMENT`, `BREWING_BOTTLES`, `BREWING_INGREDIENT`.

### `InvTweaksContainerManager.java`

Detects the currently open container by type-checking the active `GuiScreen`/`Container`, then maps the flat slot list to named sections. For unknown containers it delegates to `InvTweaksModCompatibility`, and if that returns nothing it guesses by size (anything ending in 36 slots gets an inventory section appended).

All actual inventory manipulation goes through `click(section, index, rightClick)`, which calls `PlayerController.clickInventory()` — the same code path as a real player click, so it works in multiplayer.

`move(src, srcIdx, dst, dstIdx)` handles three cases:
- Empty destination: two left-clicks (pick up + place).
- Stackable same item: two left-clicks with merge.
- Non-stackable swap of identical items (e.g. two different tools): three-step swap via an intermediate empty slot.

### `InvTweaksContainerSectionManager.java`

Thin wrapper over `InvTweaksContainerManager` scoped to a single `InvTweaksContainerSection`. Used by `InvTweaksHandlerSorting` to work exclusively within one section without needing to pass a section argument on every call.

---

## GUI

All GUI classes extend `InvTweaksObfuscationGuiButton` (which itself extends Minecraft's `abp` = `GuiButton`).

| Class | Role |
| --- | --- |
| `InvTweaksGuiSettingsButton` | The "..." button added to every supported inventory/chest screen. Opens the settings screen. |
| `InvTweaksGuiSortingButton` | One of the three chest sort buttons (h/v/s). Triggers sort on click. |
| `InvTweaksGuiSettings` | In-game settings screen — toggles for all `InvTweaks.cfg` options plus links to config files and documentation. |
| `InvTweaksGuiSettingsAbstract` | Base class for settings screens. |
| `InvTweaksGuiSettingsAdvanced` | Advanced settings sub-screen. |
| `InvTweaksGuiShortcutsHelp` | Help screen listing all shortcut key bindings. |
| `InvTweaksGuiTooltipButton` | Button that shows a tooltip on hover. |
| `InvTweaksGuiIconButton` | Button that renders an icon instead of text. |

`InvTweaks.handleGUILayout()` runs every GUI tick and adds these buttons to the `controlList` of the current `GuiScreen` if they are not already present (identified by `InvTweaksConst.JIMEOWAN_ID`).

---

## Auto-Refill — `InvTweaksHandlerAutoRefill.java`

Tracks the focused hotbar slot each game tick. When the item in that slot disappears (consumed, broken), searches the inventory for a replacement matching the item ID and damage value (or configured substitutes), then moves it into the slot using `InvTweaksContainerSectionManager`.

---

## Mod Compatibility — `InvTweaksModCompatibility.java`

Contains lists of known third-party mod GUI class names. Used by `InvTweaksObfuscation.isValidChest()` and `isValidInventory()` to recognise containers from mods such as Equivalent Exchange, IronChests, RedPower 2, IndustrialCraft 2, Multi Page Chest, and More Storage.

Also provides `getSpecialContainerSlots()` and `getSpecialChestRowSize()` for mods whose containers need custom section mapping or non-standard row widths.

---

## Mouse and Slot Detection

Slot detection under the mouse cursor is implemented in `InvTweaksContainerManager.getSlotAtMousePosition()`. It replicates the `GuiContainer.getSlotAtPosition` algorithm: transforms the raw LWJGL `Mouse.getEventX/Y` into GUI-space coordinates, then iterates the slot list checking the 18×18 pixel hit boxes.

`getIsMouseOverSlot(slot, x, y)` is the hit test: `x ∈ [slotX-1, slotX+17)` and `y ∈ [slotY-1, slotY+17)`.

---

## Data Flow for a Shortcut

```
ModLoader tick
  └─ mod_InvTweaks.onTickInGUI()
       └─ InvTweaks.onTickInGUI()
            └─ handleShortcuts(guiScreen)
                 └─ if Mouse.isButtonDown(0 or 1) and !mouseWasDown:
                      cfgManager.getShortcutsHandler().handleShortcut(guiContainer)
                           │
                           ├─ updatePressedKeys()   ← LWJGL Keyboard state
                           ├─ computeShortcutToTrigger()
                           │    ├─ getSlotAtMousePosition()  ← slot detection
                           │    ├─ isShortcutDown(type)      ← key combination check
                           │    └─ returns ShortcutConfig{type, fromSection, toSection, ...}
                           ├─ runShortcut(shortcutConfig)
                           │    └─ InvTweaksContainerManager.move() / moveSome()
                           │         └─ click(section, index, rightClick)
                           │              └─ PlayerController.clickInventory()  ← MC packet
                           └─ Mouse.destroy() / Mouse.create()   ← suppress default click
```
