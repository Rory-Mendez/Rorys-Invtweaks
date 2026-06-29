# Shortcut Flow — Rory's Inventory Tweaks

This document traces the exact path from raw mouse/keyboard input to item movement execution,
identifies the slot detection and click execution mechanisms, and pinpoints where a future
drag-transfer feature should be hooked in.

---

## Overview

Every InvTweaks shortcut (Shift+Click, right-click item move, hotbar-slot transfer, etc.) follows
the same pipeline:

```
ModLoader tick hook (mod_InvTweaks.onTickInGUI)
  └─ InvTweaks.onTickInGUI
       └─ InvTweaks.handleShortcuts          ← edge-detection, gate
            └─ InvTweaksHandlerShortcuts.handleShortcut
                 ├─ updatePressedKeys         ← keyboard state snapshot
                 ├─ computeShortcutToTrigger  ← slot lookup + type resolution
                 └─ runShortcut               ← item movement execution
                      └─ InvTweaksContainerManager.move / moveSome / moveAll
                           └─ InvTweaksContainerManager.click
                                └─ PlayerController.windowClick  (obf: ki.a())
```

---

## Involved Classes

| Class | Role |
|---|---|
| `mod_InvTweaks` | ModLoader entry point; wires tick hooks and key binding |
| `InvTweaks` | Main coordinator; owns per-tick state (`mouseWasDown`, hotbar clone) |
| `InvTweaksHandlerShortcuts` | Shortcut logic: key polling, type resolution, execution dispatch |
| `InvTweaksContainerManager` | Slot map, slot lookup, click execution wrapper |
| `InvTweaksContainerSectionManager` | Simpler per-section accessor (used by sorting/auto-refill, not shortcuts) |
| `InvTweaksShortcutMapping` | Holds a set of key codes; knows if it is currently triggered |
| `InvTweaksShortcutType` | Enum of all shortcut action types |
| `InvTweaksContainerSection` | Enum of named slot regions (CHEST, INVENTORY_HOTBAR, CRAFTING_IN, …) |
| `InvTweaksObfuscation` | Wraps all obfuscated MC/Forge symbols (slot access, click dispatch, …) |
| `InvTweaksConfig` / `InvTweaksConfigManager` | Configuration properties; vends the shortcuts handler |

---

## Method-Level Flow

### 1. Entry — `mod_InvTweaks.onTickInGUI` (mod_InvTweaks.java:66)

ModLoader calls this every client tick while any GUI screen is open.
It delegates immediately to `InvTweaks.onTickInGUI(guiScreen)`.

### 2. Tick dispatcher — `InvTweaks.onTickInGUI` (InvTweaks.java:123)

Calls, in order:

1. `handleMiddleClick(guiScreen)` — middle-click chest sorting; unrelated to shortcuts
2. `onTick()` — config polling, hotbar clone, config-switch logic
3. `handleGUILayout(guiScreen)` — injects sort buttons; unrelated to shortcuts
4. **`handleShortcuts(guiScreen)`** — the shortcut entry point

### 3. Shortcut gate — `InvTweaks.handleShortcuts` (InvTweaks.java:642)

```java
private void handleShortcuts(vp guiScreen) {
    if (!(isValidChest(guiScreen) || isStandardInventory(guiScreen))) return;

    if (Mouse.isButtonDown(0) || Mouse.isButtonDown(1)) {
        if (!mouseWasDown) {              // ← rising-edge guard: fires ONCE per click
            mouseWasDown = true;
            if (shortcutsEnabled) {
                cfgManager.getShortcutsHandler().handleShortcut((gb) guiScreen);
            }
        }
    } else {
        mouseWasDown = false;
    }
}
```

**Key constraint:** `mouseWasDown` is a rising-edge detector. The shortcut fires exactly once
per button press, regardless of how long the button is held. This is the central point that
a drag-transfer feature must augment (not replace).

### 4. Handler — `InvTweaksHandlerShortcuts.handleShortcut` (InvTweaksHandlerShortcuts.java:117)

```java
public void handleShortcut(gb guiScreen) {
    updatePressedKeys();
    ShortcutConfig shortcutToTrigger = computeShortcutToTrigger();
    if (shortcutToTrigger != null) {
        int ex = Mouse.getEventX(), ey = Mouse.getEventY();
        runShortcut(shortcutToTrigger);
        Mouse.destroy();   // ← resets the click so vanilla code doesn't also act on it
        Mouse.create();
        Mouse.setCursorPosition(ex, ey);
    }
}
```

**Key side-effect:** `Mouse.destroy()` + `Mouse.create()` resets the mouse device after every
shortcut to suppress the vanilla container click. For drag-transfer, this reset must be
deferred until the drag ends, or the mouse state will break mid-drag.

### 5. Keyboard state snapshot — `updatePressedKeys` (InvTweaksHandlerShortcuts.java:145)

Iterates every key code registered in `pressedKeys` and calls `Keyboard.isKeyDown(keyCode)`.
Result is stored in the `pressedKeys` map (Map\<Integer, Boolean\>).
If the player has rebound movement keys, `haveControlsChanged()` triggers a `loadShortcuts()`
reset to keep MOVE_UP / MOVE_DOWN in sync.

### 6. Shortcut resolution — `computeShortcutToTrigger` (InvTweaksHandlerShortcuts.java:170)

a. Creates a fresh `InvTweaksContainerManager` bound to the current GUI.  
b. Calls `container.getSlotAtMousePosition()` — returns the `Slot` (`yu`) under the cursor,
   or `null` if none.  
c. Bails out early if the slot is null or empty (`!hasStack(slot)`).  
d. Resolves `fromSection` and `fromIndex` via `container.getSlotSection()` /
   `container.getSlotIndex()`.  
e. Walks `InvTweaksShortcutType` values in priority order and calls `isShortcutDown()` for each.
   Priority: MOVE_TO_SPECIFIC_HOTBAR_SLOT > MOVE_ALL_ITEMS > MOVE_EVERYTHING > MOVE_ONE_ITEM >
   (MOVE_UP / MOVE_DOWN / DROP → resolves to MOVE_ONE_STACK).  
f. Resolves `toSection` based on `fromSection` and whether UP/DOWN keys are held.  
g. Sets `forceEmptySlot` (`true` when right-click) and `drop` flag.

### 7. Shortcut execution — `runShortcut` (InvTweaksHandlerShortcuts.java:309)

Dispatches by `ShortcutConfig.type`:

| Type | Behaviour |
|---|---|
| `MOVE_TO_SPECIFIC_HOTBAR_SLOT` | Single `container.move()` to a specific hotbar index |
| `MOVE_ONE_STACK` | Repeated `container.move()` until the source slot is empty |
| `MOVE_ONE_ITEM` | Single `container.moveSome(..., 1)` |
| `MOVE_ALL_ITEMS` | `moveAll()` matching items by type |
| `MOVE_EVERYTHING` | `moveAll()` with `stackToMatch = null` |

---

## Slot Detection

`InvTweaksContainerManager.getSlotAtMousePosition()` (InvTweaksContainerManager.java:294):

1. Reads raw LWJGL event coordinates via `Mouse.getEventX()` / `Mouse.getEventY()`.
2. Converts to GUI space:
   - `x = (eventX * guiWidth) / displayWidth`
   - `y = guiHeight - (eventY * guiHeight) / displayHeight - 1`
3. Subtracts the GUI container's screen offset `(width - xSize) / 2`, `(height - ySize) / 2`.
4. Checks each slot against pixel bounds: `[xDisplayPos-1, xDisplayPos+17)` × `[yDisplayPos-1, yDisplayPos+17)`.

This replicates the vanilla `GuiContainer.getSlotAtPosition` algorithm entirely in InvTweaks code.

**Real-time polling note:** `Mouse.getEventX()` / `getEventY()` return the position of the
*last queued event*, not necessarily the current cursor position. For drag-transfer this is
acceptable because the feature should trigger once per slot entry, not continuously.
Alternatively, `Mouse.getX()` / `Mouse.getY()` give the current position without an event,
which may be more appropriate for drag-polling.

---

## Click Execution

`InvTweaksContainerManager.click()` (InvTweaksContainerManager.java:280):

```java
public void click(InvTweaksContainerSection section, int index, boolean rightClick) {
    int slot = indexToSlot(section, index);   // section+index → container slot number
    if (slot != -1) {
        clickInventory(getPlayerController(),
            getWindowId(container),            // window ID (0 = player inventory)
            slot,                              // target slot number
            (rightClick) ? 1 : 0,             // button (0=left, 1=right)
            false,                             // shiftHeld — always false
            getThePlayer());
    }
}
```

`clickInventory` wraps the obfuscated `PlayerController.windowClick` (`ki.a()`), which:
- In single-player: directly manipulates the server-side container.
- In multi-player: sends a `Packet102WindowClick` to the server and polls for acknowledgment.

**Important:** `shiftHeld` is always `false`. InvTweaks does not use Minecraft's native
shift-transfer. It simulates transfers with two sequential left-clicks: pick up from source,
place at destination. Three-click sequences are used for tool/map swaps via an intermediate slot.

---

## Shortcut Type Details

### Shift-click transfers (MOVE_ONE_STACK / MOVE_ALL_ITEMS)

Triggered when any mapping registered under `MOVE_ONE_STACK` shortcuts is detected (e.g. Shift
key from MOVE_UP or MOVE_DOWN), or when `MOVE_ALL_ITEMS` mapping is triggered (default: Shift+Click).
Internally two left-clicks are issued, not a native shift-click.

### Left-click shortcuts

The primary shortcut path. `Mouse.isButtonDown(0)` triggers `handleShortcuts`. Type is resolved by
keyboard modifiers held at the time of the click.

### Right-click shortcuts

`Mouse.isButtonDown(1)` also enters `handleShortcuts`. Right-click sets `forceEmptySlot = true`
in `computeShortcutToTrigger`, which skips partial-stack merging and forces a move to an empty slot.

### Mouse button state

Tracked by `InvTweaks.mouseWasDown` (boolean). Prevents repeat-firing while the button is held.
Set back to `false` when neither button 0 nor button 1 is pressed.

### Keyboard modifier state

All modifier keys (Shift, Ctrl, Alt, W, S, 1-9, …) are polled via `Keyboard.isKeyDown()` every
call to `updatePressedKeys()`. The result map (`pressedKeys`) is consulted by
`InvTweaksShortcutMapping.isTriggered()` to decide if a mapping is active.

---

## Drag-Hover Detection Layer (v0.3.0)

`InvTweaks.handleDragHover(vp guiScreen)` (InvTweaks.java) is called every GUI tick
immediately after `handleDragDebug`. It detects when the cursor enters a new inventory slot
while a Shift+LMB drag gesture is active, and logs the event. No items are moved.

### State

| Field | Type | Description |
|---|---|---|
| `dragHoverCurrentSlot` | `int` | Slot number under the cursor this tick (`-1` = none) |
| `dragHoverPrevSlot` | `int` | Slot number from the previous slot-change tick |
| `dragHoverEnteredNew` | `boolean` | `true` for exactly one tick when slot changes |
| `dragHoverGestureActive` | `boolean` | `true` while Shift+LMB+validGUI conditions hold |

### Activation conditions

All three must be true simultaneously:
1. `Mouse.isButtonDown(0)` — left mouse button held
2. `Keyboard.isKeyDown(KEY_LSHIFT) || Keyboard.isKeyDown(KEY_RSHIFT)` — Shift held
3. `isGuiContainer(guiScreen) && (isValidChest || isStandardInventory)` — valid GUI open

### Slot detection

Same mechanism as `handleDragDebug`: constructs a short-lived `InvTweaksContainerManager`,
calls `getSlotAtMousePosition()`, then `getSlotSection(slotNumber)`. Wrapped in try/catch
so a transient exception never crashes the detection layer.

### Per-slot deduplication

`dragHoverCurrentSlot` is compared to the current slot each tick. An event fires only when
the value changes. While the cursor remains on the same slot `dragHoverEnteredNew` is `false`
and no output is produced.

### Reset conditions

`dragHoverGestureActive` and all tracked slot state reset to defaults (false / -1) when any
activation condition becomes false: LMB released, Shift released, GUI closes (method not called),
or cursor leaves all slots (slot = -1 propagates to `dragHoverCurrentSlot`).

With `enableDragDebug=false` the method resets state immediately and returns — zero overhead.

### Log format

```
[InvTweaks DragHover] entered slot #<slotNumber> section=<sectionName>
```

Fired once per slot entry; suppressed when slot = -1 (cursor not over any slot).

---

## Drag-Transfer Layer (v0.4.0)

`InvTweaks.handleDragTransfer(vp guiScreen)` (InvTweaks.java) is called every GUI tick
immediately after `handleDragHover`. It performs item movement for Shift+LMB drags.
Controlled by `enableDragTransfer` (default `true`); debug output gated on `enableDragDebug`.

### State

| Field | Type | Description |
|---|---|---|
| `dragTransferCurrentSlot` | `int` | Slot number last seen under the cursor (`-1` = none/reset) |
| `dragTransferVisited` | `Set<Integer>` | Slot numbers already processed in the current gesture |

### Activation conditions

1. `Mouse.isButtonDown(0)` — left mouse button held  
2. `Keyboard.isKeyDown(KEY_LSHIFT) || Keyboard.isKeyDown(KEY_RSHIFT)` — Shift held  
3. `isGuiContainer(guiScreen) && (isValidChest || isStandardInventory)` — valid GUI open  
4. `enableDragTransfer=true` in config

### Why `handleShortcut()` is NOT called

`handleShortcut()` calls `Mouse.destroy()` + `Mouse.create()` after each shortcut execution to
consume the click and suppress the vanilla container action. During a drag this would reset the
mouse button state on every slot entry, breaking the continuous hold detection. The drag-transfer
layer calls `InvTweaksContainerManager.move()` directly instead.

### Conflict with the existing rising-edge shortcut

Plain Shift+LMB (no Ctrl, no Alt, no W/S, no 1-9) resolves to `shortcutToTrigger = null` in
`computeShortcutToTrigger` because none of the configured shortcut mappings match. Therefore
`handleShortcut()` is never called and `Mouse.destroy()` never runs for a plain Shift drag.
The two systems operate on different input patterns with no conflict.

### Per-slot deduplication

`dragTransferVisited` records every slot number processed in the current gesture. When the cursor
re-enters a previously visited slot, the method returns immediately without moving anything.
The set is cleared when the gesture ends (LMB up, Shift up, GUI closes, or feature disabled).

### Transfer target resolution — `resolveTransferTarget`

Mirrors `computeShortcutToTrigger`'s implicit `toSection` switch (no new algorithm):

| From section | To section |
|---|---|
| `CHEST` | `INVENTORY` |
| `INVENTORY_HOTBAR` | `CHEST` (if open), else `INVENTORY_NOT_HOTBAR` |
| Any other | `CHEST` (if open), else `INVENTORY_HOTBAR` |

### Destination index resolution — `findDragDestIndex`

Mirrors `getNextTargetIndex` from `InvTweaksHandlerShortcuts`:
1. Scan `toSection` for a partial stack of the same item type (no data tags, not full).
2. Fall back to `container.getFirstEmptyIndex(toSection)`.
3. Return `-1` if `toSection` is absent or no space available.

### Movement loop

Mirrors the `MOVE_ONE_STACK` case from `runShortcut`:

```java
while (hasStack(fromSlot) && toIndex != -1) {
    boolean success = xferContainer.move(fromSection, fromIndex, toSection, toIndex);
    prevToIndex = toIndex;
    toIndex = findDragDestIndex(xferContainer, toSection, fromStack);
    if (!success && toIndex == prevToIndex) break; // destination full
}
```

### Skipped slot reasons

| Reason | Condition |
|---|---|
| `empty` | Slot has no item stack |
| `no_section` | `getSlotSection` or `getSlotIndex` returned null/-1 |
| `unsafe_section` | Section listed in `isUnsafeSection` (see v0.7.0 below) |
| `no_target` | `resolveTransferTarget` returned null |
| `dest_full` | Move loop ran but no items transferred |

### Log format (when `enableDragDebug=true`)

```
[InvTweaks DragTransfer] moved slot #<n> section=<section>
[InvTweaks DragTransfer] skipped slot #<n> reason=<reason>
```

---

## Drag-Transfer Interpolation (v0.5.0)

`InvTweaks.handleDragTransfer` (v0.5.0) adds intermediate-slot recovery to the v0.4.0 layer.

### Problem being solved

The detection fires once per GUI tick (≈20 ms). If the mouse crosses two or more slots between
ticks, only the slot under the cursor at the end of the tick is processed; any slots passed
through mid-tick are silently skipped.

### State added (v0.5.0)

| Field | Type | Description |
|---|---|---|
| `dragTransferCurrentSlotX` | `int` | `xDisplayPosition` of the last seen slot (`-1` = none) |
| `dragTransferCurrentSlotY` | `int` | `yDisplayPosition` of the last seen slot (`-1` = none) |

Existing v0.4.0 fields (`dragTransferCurrentSlot`, `dragTransferVisited`) are unchanged.

### Algorithm — safe row/column sweep

Each slot's pixel position in GUI space is read via `getXDisplayPosition(slot)` and
`getYDisplayPosition(slot)` (both are `InvTweaksObfuscation` helpers wrapping `slot.d` and
`slot.e`). Slots within the same row share an identical `yDisplayPosition`; slots within the
same column share an identical `xDisplayPosition`. Standard grids use 18-pixel pitch (16 px
slot + 2 px gap).

When `handleDragTransfer` detects that the cursor moved to a new slot:

1. Compare `prevX = dragTransferCurrentSlotX`, `prevY = dragTransferCurrentSlotY` with
   `curX = getXDisplayPosition(newSlot)`, `curY = getYDisplayPosition(newSlot)`.
2. **Same row**: `prevY == curY`. Iterate all container sections; collect slots whose
   `yDisplayPosition == prevY` and whose `xDisplayPosition` is strictly between `prevX` and
   `curX`. Pass each to `doTransferSlot` in section-list order.
3. **Same column**: `prevX == curX`. Same process along the Y axis.
4. **Diagonal**: `prevY != curY && prevX != curX`. Skip interpolation, log
   `reason=diagonal` when `enableDragDebug=true`. The current slot is still processed normally.

### Helper: `processIntermediateSlots`

```java
private void processIntermediateSlots(InvTweaksContainerManager xferContainer,
        int prevX, int prevY, int curX, int curY, boolean debugEnabled)
```

Called from `handleDragTransfer` whenever the cursor enters a new slot and a valid previous
position is known. Iterates `InvTweaksContainerSection.values()` — all sections present in the
current container — and delegates each qualifying candidate to `doTransferSlot`.

### Helper: `doTransferSlot`

```java
private void doTransferSlot(InvTweaksContainerManager xferContainer,
        int slotNum, yu slotObj, boolean debugEnabled)
```

Extracted from the v0.4.0 inline processing block. Contains all safeguards in one place:
`dragTransferVisited` deduplication, `getHoldStack()` null check, empty-slot check,
section/index resolution, crafting-slot skip, target-section resolution, MOVE_ONE_STACK loop.

### Helper: `isBetween`

```java
private static boolean isBetween(int from, int to, int val)
```

Returns `true` iff `val` is strictly between `from` and `to` (direction-independent).

### Helper: `resetDragTransfer`

```java
private void resetDragTransfer()
```

Clears all four drag-transfer state fields atomically; called on gesture end or feature disable.

### Trade-off

Only row-aligned or column-aligned interpolation is performed. A fast diagonal sweep across a
corner of the grid may leave one slot unprocessed if it was approached diagonally. This is
accepted because diagonal path reconstruction is ambiguous — it is not clear which of two corner
slots the cursor actually passed through first. The logged `reason=diagonal` message makes the
skip visible when `enableDragDebug=true`.

### Log format (when `enableDragDebug=true`)

```
[InvTweaks DragTransfer] interp slot #<n> axis=row
[InvTweaks DragTransfer] interp slot #<n> axis=col
[InvTweaks DragTransfer] interp skipped reason=diagonal prev=[<x>,<y>] cur=[<x>,<y>]
[InvTweaks DragTransfer] skipped slot #<n> reason=hand_busy
```

The first two lines fire once per interpolated slot (before `moved` or `skipped` from the
transfer attempt). The third fires once per diagonal jump when debug is on.
`hand_busy` is a new skip reason added in v0.5.0 (cursor holding an item stack).

---

## Risks and Safeguards

### 1. Repeated actions on the same slot

**Risk:** The cursor may hover over the same slot for multiple ticks, triggering the transfer
repeatedly and undoing the item placement.

**Safeguard:** Maintain `draggedSlots` (Set\<Integer\> of container slot numbers). Skip any slot
already in the set for the duration of the current drag gesture.

### 2. Crafting grids

**Risk:** Dragging over crafting input/output slots. Crafting output refills automatically;
dragging across it could interact unexpectedly with the recipe.

**Safeguard:** Filter out `CRAFTING_OUT` and `CRAFTING_IN` from drag targets, matching the
existing guard in `runShortcut()` at InvTweaksHandlerShortcuts.java:326.

### 3. Chest vs player inventory

Both are handled by `InvTweaksContainerManager`'s section map, which is built from the actual
container slots at construction time. No special-casing needed; section resolution is automatic.

### 4. Modded containers

Unknown container types fall through to the generic CHEST heuristic in
`InvTweaksContainerManager` (line 96-110). The slot pixel-bound detection works the same.
The `mods.getSpecialContainerSlots()` path provides custom section maps for known mod containers.
Risk is low because the transfer logic is section-agnostic.

### 5. Conflicts with existing shortcuts

The drag-transfer must activate **only** on Shift+LMB hold, not on any other shortcut
combination. The existing shortcut system fires once on the rising edge of LMB. The drag check
runs on the continuing hold. Both can coexist if implemented as separate branches inside
`handleShortcuts`.

Right-click, middle-click, hotbar-key, and drop shortcuts all have different activation paths
and are unaffected.

### 6. Mouse.destroy() suppression mid-drag

The click-consume mechanism (`Mouse.destroy()` + `Mouse.create()`) must not run while a drag
is in progress, or the cursor position resets on every slot entry. This is addressable by
tracking drag state and skipping the destroy/create when `draggedSlots` is non-empty.

### 7. Server lag in SMP

`POLLING_DELAY = 3` ticks. Fast drags across many slots may outrun server acknowledgment and
leave items in unexpected states. A per-slot delay or the existing `TimeoutException` handling
in `move()` provides a safety net.

### 8. Empty source slots

`computeShortcutToTrigger` returns `null` if the hovered slot is empty. The drag feature must
silently skip empty slots rather than erroring.

---

## Container Compatibility and Unsafe Sections (v0.7.0)

`handleDragTransfer` is activated for any GUI that passes the `validGui` gate:

```java
boolean validGui = isGuiContainer(guiScreen)
        && (isValidChest(guiScreen) || isStandardInventory(guiScreen));
```

`isStandardInventory` includes furnace (`isGuiFurnace`), brewing stand (`isGuiBrewingStand`),
workbench (`isGuiWorkbench`), and enchantment table (`isGuiEnchantmentTable`) — not just the
player inventory and chests. Each of these containers exposes special-purpose slots that must
not be auto-transferred during a drag gesture.

### Helper: `isUnsafeSection` (v0.7.0)

```java
private static boolean isUnsafeSection(InvTweaksContainerSection section)
```

Replaces the v0.4.0 crafting-only guard. Returns `true` for sections that `doTransferSlot`
must skip regardless of context:

| Section | Reason skipped |
|---|---|
| `CRAFTING_OUT` | Output auto-refills; grabbing it mid-recipe is risky |
| `CRAFTING_IN` | Removing crafting inputs mid-recipe is unexpected |
| `ARMOR` | Armor slots are managed by auto-equip, not drag-transfer |
| `FURNACE_OUT` | Smelting output auto-fills like crafting output |
| `ENCHANTMENT` | Single slot; removing the item cancels the enchantment |
| `BREWING_INGREDIENT` | Removing the ingredient mid-brew silently cancels the brew |

Sections **not** in the unsafe list — `FURNACE_IN`, `FURNACE_FUEL`, `BREWING_BOTTLES`,
`CHEST`, `INVENTORY_HOTBAR`, `INVENTORY_NOT_HOTBAR` — behave like standard inventory slots
and are allowed to be transferred from.

### Container-type matrix

| Container | Safe sections | Skipped sections |
|---|---|---|
| Player inventory | `INVENTORY_HOTBAR`, `INVENTORY_NOT_HOTBAR` | `CRAFTING_OUT`, `CRAFTING_IN`, `ARMOR` |
| Chest / Dispenser | `CHEST`, `INVENTORY_*` | _(none extra)_ |
| Workbench | `INVENTORY_*` | `CRAFTING_OUT`, `CRAFTING_IN` |
| Furnace | `FURNACE_IN`, `FURNACE_FUEL`, `INVENTORY_*` | `FURNACE_OUT` |
| Brewing stand | `BREWING_BOTTLES`, `INVENTORY_*` | `BREWING_INGREDIENT` |
| Enchantment table | `INVENTORY_*` | `ENCHANTMENT` |
| Modded (unknown) | `CHEST` (heuristic), `INVENTORY_*` | _(unsafe-section check applies; no_target if no CHEST or INVENTORY)_ |

### Debug log format (when `enableDragDebug=true`)

```
[InvTweaks DragTransfer] skipped slot #<n> reason=unsafe_section section=<section>
```

Replaces the former `reason=crafting` log line. All formerly-crafting skips now appear
with `reason=unsafe_section section=CRAFTING_OUT` or `reason=unsafe_section section=CRAFTING_IN`.
