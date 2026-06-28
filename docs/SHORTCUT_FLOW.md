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

## Proposed Future Drag-Transfer Hook

### What it must do

While Shift+LMB is held and the cursor moves across inventory slots, each newly entered slot
should execute the same item transfer as a normal Shift+Click on that slot.

### Where to hook

**Primary hook site:** `InvTweaks.handleShortcuts` (InvTweaks.java:642).

The existing rising-edge block should remain untouched. Immediately after it (still inside the
`if (Mouse.isButtonDown(0) || Mouse.isButtonDown(1))` branch), add a drag-transfer secondary block:

```java
// Pseudocode — do not implement yet
if (Mouse.isButtonDown(0) && Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) {
    // Check which slot is currently under the cursor
    // If it differs from lastDraggedSlot, execute a transfer and record it
}
```

**Execution path to reuse:**

The drag-transfer needs to call `InvTweaksHandlerShortcuts.handleShortcut()` for each new slot, OR
call `InvTweaksContainerManager.move()` directly after computing `fromSection`/`fromIndex`/`toSection`
from the hovered slot. The latter gives finer control and avoids the `Mouse.destroy()` side-effect.

**Key infrastructure available:**

- Slot lookup: `InvTweaksContainerManager.getSlotAtMousePosition()` or poll with `Mouse.getX()` / `Mouse.getY()`
- Section/index from slot: `container.getSlotSection(slotNumber)` + `container.getSlotIndex(slotNumber)`
- Item movement: `container.move(fromSection, fromIndex, toSection, toIndex)`
- Default transfer target: same logic as `computeShortcutToTrigger` → `toSection` based on `fromSection`

**State to add to `InvTweaks`:**

```java
private yu lastDragSlot = null;         // slot object hovered at last tick during a drag
private Set<Integer> draggedSlots = new HashSet<>();  // slot numbers transferred this drag
```

Reset both when `mouseWasDown` returns to `false`.

**Mouse.destroy() interaction:**

Do NOT call `Mouse.destroy()` / `Mouse.create()` per-slot during a drag. Suppress the vanilla
click differently, or accept that vanilla also sees the click (the vanilla Shift+Click behaves
identically to what we want anyway for drag). Alternatively, verify that the vanilla container
click on a slot we already processed is a no-op when the slot is empty post-transfer.

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
