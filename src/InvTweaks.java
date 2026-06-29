import invtweaks.InvTweaksConst;
import invtweaks.InvTweaksItemTree;
import invtweaks.InvTweaksItemTreeItem;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.minecraft.client.Minecraft;

import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;


/**
 * Main class for Inventory Tweaks, which maintains various hooks
 * and dispatches the events to the correct handlers.
 * 
 * @author Jimeo Wan
 *
 * Contact: jimeo.wan (at) gmail (dot) com
 * Website: {@link http://wan.ka.free.fr/?invtweaks}
 * Source code: {@link https://github.com/jimeowan/inventory-tweaks}
 * License: MIT
 * 
 */
public class InvTweaks extends InvTweaksObfuscation {

    private static final Logger log = Logger.getLogger("InvTweaks");

    /**
     * Key binding to trigger sorting. 
     * Maintained by Minecraft so that its keycode is actually
     * what has been configured by the player (not always the R key).
     */
    public static final afu SORT_KEY_BINDING = 
        new afu("Sort inventory", Keyboard.KEY_R); /* KeyBinding */
    
    
    private static InvTweaks instance;

    /**
     * The configuration loader.
     */
    private InvTweaksConfigManager cfgManager = null;
    
    /**
     * Attributes to remember the status of chest sorting
     * while using middle clicks.
     */
    private int chestAlgorithm = InvTweaksHandlerSorting.ALGORITHM_DEFAULT;
    private long chestAlgorithmClickTimestamp = 0;
    private boolean chestAlgorithmButtonDown = false;
    
    /**
     * Various information concerning the context, stored on
     * each tick to allow for certain features (auto-refill,
     * sorting on pick up...)
     */
    private int storedStackId = 0, storedStackDamage = -1, storedFocusedSlot = -1;
    private aan[] hotbarClone = new aan[InvTweaksConst.INVENTORY_HOTBAR_SIZE];
    private boolean mouseWasInWindow = true, mouseWasDown = false;;

    // Drag debug instrumentation state (v0.2.0) — only used when enableDragDebug=true
    private int     dbgLastSlotNumber = -1;
    private String  dbgLastGuiType    = "";
    private boolean dbgLastLmb = false, dbgLastRmb = false, dbgLastShift = false;

    // Drag-hover detection layer (v0.3.0) — only active when enableDragDebug=true
    private int     dragHoverCurrentSlot   = -1;
    private int     dragHoverPrevSlot      = -1;
    private boolean dragHoverEnteredNew    = false;
    private boolean dragHoverGestureActive = false;

    // Drag-transfer layer (v0.4.0/v0.5.0) — active when enableDragTransfer=true
    private int          dragTransferCurrentSlot  = -1;
    private int          dragTransferCurrentSlotX = -1; // xDisplayPosition of last seen slot
    private int          dragTransferCurrentSlotY = -1; // yDisplayPosition of last seen slot
    private Set<Integer> dragTransferVisited       = new HashSet<Integer>();
    
    /**
     * Allows to trigger some logic only every Const.POLLING_DELAY.
     */
    private int tickNumber = 0, lastPollingTickNumber = -InvTweaksConst.POLLING_DELAY;
    
    /**
    * Stores when the sorting key was last pressed (allows to detect long key holding)
    */
    private long sortingKeyPressedDate = 0;
    
    /**
     * Creates an instance of the mod, and loads the configuration
     * from the files, creating them if necessary.
     * @param mc
     */
    public InvTweaks(Minecraft mc) {
        super(mc);

        log.setLevel(InvTweaksConst.DEFAULT_LOG_LEVEL);

        // Store instance
        instance = this;

        // Load config files
        cfgManager = new InvTweaksConfigManager(mc);
        if (cfgManager.makeSureConfigurationIsLoaded()) {
            log.info("Mod initialized");
        } else {
            log.severe("Mod failed to initialize!");
        }
        

    }

    /**
     * To be called on each tick during the game (except when in a menu).
     * Handles the auto-refill.
     */
    public void onTickInGame() {
        synchronized (this) {
            if (!onTick()) {
                return;
            }
            handleAutoRefill();
        }
    }
    
    /**
     * To be called on each tick when a menu is open.
     * Handles the GUI additions and the middle clicking.
     * @param guiScreen
     */
    public void onTickInGUI(vp guiScreen) {
        synchronized (this) {
            handleMiddleClick(guiScreen); // Called before the rest to be able to trigger config reload 
            if (!onTick()) {
                return;
            }
            if (isTimeForPolling()) {
                unlockKeysIfNecessary();
            }
            handleGUILayout(guiScreen);
            handleShortcuts(guiScreen);
            handleDragDebug(guiScreen);
            handleDragHover(guiScreen);
            handleDragTransfer(guiScreen);
        }
    }

    /**
     * To be called every time the sorting key is pressed.
     * Sorts the inventory.
     */
    public final void onSortingKeyPressed() {
        synchronized (this) {
            
            // Check config loading success
            if (!cfgManager.makeSureConfigurationIsLoaded()) {
                return;
            }
            
            // Check current GUI
            vp guiScreen = getCurrentScreen();
            if (guiScreen == null || (isValidChest(guiScreen) || isValidInventory(guiScreen))) {
                // Sorting!
                handleSorting(guiScreen);
            }
        }
    }

    /**
     * To be called everytime a stack has been picked up.
     * Moves the picked up item in another slot that matches best the current configuration.
     */
    public void onItemPickup() {
    
        if (!cfgManager.makeSureConfigurationIsLoaded()) {
            return;
        }
        InvTweaksConfig config = cfgManager.getConfig();
        // Handle option to disable this feature
        if (cfgManager.getConfig().getProperty(InvTweaksConfig.PROP_ENABLE_SORTING_ON_PICKUP).equals("false")) {
            return;
        }
    
        try {
            InvTweaksContainerSectionManager containerMgr = new InvTweaksContainerSectionManager(mc, InvTweaksContainerSection.INVENTORY);
    
            // Find stack slot (look in hotbar only).
            // We're looking for a brand new stack in the hotbar
            // (not an existing stack whose amount has been increased)
            int currentSlot = -1;
            do {
                // In SMP, we have to wait first for the inventory update
                if (isMultiplayerWorld() && currentSlot == -1) {
                    try {
                        Thread.sleep(InvTweaksConst.POLLING_DELAY);
                    } catch (InterruptedException e) {
                        // Do nothing (sleep interrupted)
                    }
                }
                for (int i = 0; i < InvTweaksConst.INVENTORY_HOTBAR_SIZE; i++) {
                    aan currentHotbarStack = containerMgr.getItemStack(i + 27);
                    // Don't move already started stacks
                    if (currentHotbarStack != null && getAnimationsToGo(currentHotbarStack) == 5 && hotbarClone[i] == null) {
                        currentSlot = i + 27;
                    }
                }
    
                // The loop is only relevant in SMP (polling)
            } while (isMultiplayerWorld() && currentSlot == -1);
    
            if (currentSlot != -1) {
    
                // Find preffered slots
                List<Integer> prefferedPositions = new LinkedList<Integer>();
                InvTweaksItemTree tree = config.getTree();
                aan stack = containerMgr.getItemStack(currentSlot);
                List<InvTweaksItemTreeItem> items = tree.getItems(getItemID(stack),
                        getItemDamage(stack));
                for (InvTweaksConfigSortingRule rule : config.getRules()) {
                    if (tree.matches(items, rule.getKeyword())) {
                        for (int slot : rule.getPreferredSlots()) {
                            prefferedPositions.add(slot);
                        }
                    }
                }
    
                // Find best slot for stack
                boolean hasToBeMoved = true;
                if (prefferedPositions != null) {
                    for (int newSlot : prefferedPositions) {
                        try {
                            // Already in the best slot!
                            if (newSlot == currentSlot) {
                                hasToBeMoved = false;
                                break;
                            }
                            // Is the slot available?
                            else if (containerMgr.getItemStack(newSlot) == null) {
                                // TODO: Check rule level before to move
                                if (containerMgr.move(currentSlot, newSlot)) {
                                    break;
                                }
                            }
                        } catch (TimeoutException e) {
                            logInGameError("Failed to move picked up stack", e);
                        }
                    }
                }
    
                // Else, put the slot anywhere
                if (hasToBeMoved) {
                    for (int i = 0; i < containerMgr.getSize(); i++) {
                        if (containerMgr.getItemStack(i) == null) {
                            if (containerMgr.move(currentSlot, i)) {
                                break;
                            }
                        }
                    }
                }
    
            }
            
        } catch (Exception e) {
            logInGameError("Failed to move picked up stack", e);
        }
    }

    public void logInGame(String message) {
    	logInGame(message, false);
    }

    public void logInGame(String message, boolean alreadyTranslated) {
        String formattedMsg = buildlogString(Level.INFO, (alreadyTranslated) ? message : InvTweaksLocalization.get(message));
        addChatMessage(formattedMsg);
        log.info(formattedMsg);
    }
    
    public void logInGameError(String message, Exception e) {
        String formattedMsg = buildlogString(Level.SEVERE, InvTweaksLocalization.get(message), e);
        addChatMessage(formattedMsg);
        log.severe(formattedMsg);
    }

    public static void logInGameStatic(String message) {
        InvTweaks.getInstance().logInGame(message);
    }

    public static void logInGameErrorStatic(String message, Exception e) {
        InvTweaks.getInstance().logInGameError(message, e);
    }

    /**
     * Returns the mods single instance.
     * @return
     */
    public static InvTweaks getInstance() {
        return instance;
    }

    public static Minecraft getMinecraftInstance() {
        return instance.mc;
    }

    public static boolean classExists(String className) {
		try {
			return Class.forName(className) != null;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	private boolean onTick() {

        tickNumber++;
        
        // Not calling "cfgManager.makeSureConfigurationIsLoaded()" for performance reasons
        InvTweaksConfig config = cfgManager.getConfig();
        if (config == null) { 
            return false;
        }
        
        // Clone the hotbar to be able to monitor changes on it
        vp currentScreen = getCurrentScreen();
        if (currentScreen == null || isGuiInventory(currentScreen)) {
            cloneHotbar();
        }
        
        handleConfigSwitch();
        
        return true;
        
    }
    
    private void handleConfigSwitch() {
        
        InvTweaksConfig config = cfgManager.getConfig();
        vp currentScreen = getCurrentScreen();

        // Switch between configurations (shortcut)
        InvTweaksShortcutMapping switchMapping = cfgManager.getShortcutsHandler()
                .isShortcutDown(InvTweaksShortcutType.MOVE_TO_SPECIFIC_HOTBAR_SLOT);
        if (isSortingShortcutDown() && switchMapping != null) {
            String newRuleset = null;
            int pressedKey = switchMapping.getKeyCodes().get(0);
            if (pressedKey >= Keyboard.KEY_1 && pressedKey <= Keyboard.KEY_9) {
                newRuleset = config.switchConfig(pressedKey - Keyboard.KEY_1);
            }
            else {
                switch (pressedKey) {
                case Keyboard.KEY_NUMPAD1: newRuleset = config.switchConfig(0); break;
                case Keyboard.KEY_NUMPAD2: newRuleset = config.switchConfig(1); break;
                case Keyboard.KEY_NUMPAD3: newRuleset = config.switchConfig(2); break;
                case Keyboard.KEY_NUMPAD4: newRuleset = config.switchConfig(3); break;
                case Keyboard.KEY_NUMPAD5: newRuleset = config.switchConfig(4); break;
                case Keyboard.KEY_NUMPAD6: newRuleset = config.switchConfig(5); break;
                case Keyboard.KEY_NUMPAD7: newRuleset = config.switchConfig(6); break;
                case Keyboard.KEY_NUMPAD8: newRuleset = config.switchConfig(7); break;
                case Keyboard.KEY_NUMPAD9: newRuleset = config.switchConfig(8); break;
                }
            }
            
            if (newRuleset != null) {
                logInGame(String.format(InvTweaksLocalization.get("invtweaks.loadconfig.enabled"), newRuleset), true);
                // Hack to prevent 2nd way to switch configs from being enabled
                sortingKeyPressedDate = Integer.MAX_VALUE; 
            }
        }

        // Switch between configurations (by holding the sorting key)
        if (isSortingShortcutDown()) {
            long currentTime = System.currentTimeMillis();
            if (sortingKeyPressedDate == 0) {
                sortingKeyPressedDate = currentTime;
            } else if (currentTime - sortingKeyPressedDate > InvTweaksConst.RULESET_SWAP_DELAY
                    && sortingKeyPressedDate != Integer.MAX_VALUE) {
                String previousRuleset = config.getCurrentRulesetName();
                String newRuleset = config.switchConfig();
                // Log only if there is more than 1 ruleset
                if (previousRuleset != null && newRuleset != null && !previousRuleset.equals(newRuleset)) {
                    logInGame(String.format(InvTweaksLocalization.get("invtweaks.loadconfig.enabled"), newRuleset), true);
                    handleSorting(currentScreen);
                }
                sortingKeyPressedDate = currentTime;
            }
        } else {
            sortingKeyPressedDate = 0;
        }

    }

    private void handleSorting(vp guiScreen) {
    	
        aan selectedItem = null;
        int focusedSlot = getFocusedSlot();
        aan[] mainInventory = getMainInventory();
        if (focusedSlot < mainInventory.length && focusedSlot >= 0) {
            selectedItem = mainInventory[focusedSlot];
        }

        // Sorting
        try {
            new InvTweaksHandlerSorting(mc, cfgManager.getConfig(),
                    InvTweaksContainerSection.INVENTORY,
                    InvTweaksHandlerSorting.ALGORITHM_INVENTORY,
                    InvTweaksConst.INVENTORY_ROW_SIZE).sort();
        } catch (Exception e) {
            logInGameError("invtweaks.sort.inventory.error", e);
            e.printStackTrace();
        }

        playClick();

        // This needs to be remembered so that the
        // auto-refill feature doesn't trigger
        if (selectedItem != null && mainInventory[focusedSlot] == null) {
            storedStackId = 0;
        }

    }

    private void handleAutoRefill() {
    
        aan currentStack = getFocusedStack();
        int currentStackId = (currentStack == null) ? 0 : getItemID(currentStack);
        int currentStackDamage = (currentStack == null) ? 0 : getItemDamage(currentStack);
        int focusedSlot = getFocusedSlot() + 27; // Convert to container slots index
        InvTweaksConfig config = cfgManager.getConfig();
        
        if (currentStackId != storedStackId || currentStackDamage != storedStackDamage) {
    
            if (storedFocusedSlot != focusedSlot) { // Filter selection change
                storedFocusedSlot = focusedSlot;
            } else if ((currentStack == null || getItemID(currentStack) == 281 && storedStackId == 282)  // Handle eaten mushroom soup
                    && (getCurrentScreen() == null || // Filter open inventory or other window
                    isGuiEditSign(getCurrentScreen()))) {
    
                if (config.isAutoRefillEnabled(storedStackId, storedStackId)) {
                    try {
                        cfgManager.getAutoRefillHandler().autoRefillSlot(focusedSlot, storedStackId, storedStackDamage);
                    } catch (Exception e) {
                        logInGameError("invtweaks.sort.autorefill.error", e);
                    }
                }
            }
        }
    
        storedStackId = currentStackId;
        storedStackDamage = currentStackDamage;
    
    }

    private void handleMiddleClick(vp guiScreen) {
    
        if (Mouse.isButtonDown(2)) {
    
            if (!cfgManager.makeSureConfigurationIsLoaded()) {
                return;
            }
            InvTweaksConfig config = cfgManager.getConfig();
    
            // Check that middle click sorting is allowed
            if (config.getProperty(InvTweaksConfig.PROP_ENABLE_MIDDLE_CLICK)
                    .equals(InvTweaksConfig.VALUE_TRUE)) {
    
                if (!chestAlgorithmButtonDown) {
                    chestAlgorithmButtonDown = true;

                    InvTweaksContainerManager containerMgr = new InvTweaksContainerManager(mc);
                    yu slotAtMousePosition = containerMgr.getSlotAtMousePosition();
                    InvTweaksContainerSection target = null;
                    if (slotAtMousePosition != null) {
                    	target = containerMgr.getSlotSection(getSlotNumber(slotAtMousePosition));
                    }
    
                    if (isValidChest(guiScreen)) {
    
                        // Check if the middle click target the chest or the inventory
                        // (copied GuiContainer.getSlotAtPosition algorithm)
                    	gb guiContainer = (gb) guiScreen;
                        
                        if (InvTweaksContainerSection.CHEST.equals(target)) {
    
                            // Play click
                            playClick();
    
                            long timestamp = System.currentTimeMillis();
                            if (timestamp - chestAlgorithmClickTimestamp > 
                                    InvTweaksConst.CHEST_ALGORITHM_SWAP_MAX_INTERVAL) {
                                chestAlgorithm = InvTweaksHandlerSorting.ALGORITHM_DEFAULT;
                            }
                            try {
                                new InvTweaksHandlerSorting(mc, cfgManager.getConfig(),
                                        InvTweaksContainerSection.CHEST,
                                        chestAlgorithm,
                                        getContainerRowSize(guiContainer)).sort();
                            } catch (Exception e) {
                                logInGameError("invtweaks.sort.chest.error", e);
                                e.printStackTrace();
                            }
                            chestAlgorithm = (chestAlgorithm + 1) % 3;
                            chestAlgorithmClickTimestamp = timestamp;

                        } else if (InvTweaksContainerSection.INVENTORY_HOTBAR.equals(target)
                        		|| (InvTweaksContainerSection.INVENTORY_NOT_HOTBAR.equals(target))) {
                            handleSorting(guiScreen);
                        }
    
                    } else if (isValidInventory(guiScreen)) {

                    	/*
                    	 // Crafting stacks evening (hook ready, TODO implement algorithm)
                    	 if (InvTweaksContainerSection.CRAFTING_IN.equals(target)) {
                            try {
								new InvTweaksHandlerSorting(mc, cfgManager.getConfig(),
								        InvTweaksContainerSection.CRAFTING_IN,
								        InvTweaksHandlerSorting.ALGORITHM_EVEN_STACKS,
								        (containerMgr.getSize(target) == 9) ? 3 : 2).sort();
							} catch (Exception e) {
                                logInGameError("invtweaks.sort.crafting.error", e);
                                e.printStackTrace();
							}
                        }*/
                    	
                    	handleSorting(guiScreen);
                    	
                    }
                }
            }
        } else {
            chestAlgorithmButtonDown = false;
        }
    }

    private void handleGUILayout(vp guiScreen) {

        InvTweaksConfig config = cfgManager.getConfig();
        boolean isValidChest = isValidChest(guiScreen);

        if (isValidChest || (isStandardInventory(guiScreen) && !isGuiEnchantmentTable(guiScreen))) {

            int w = 10, h = 10;

            // Look for the mods buttons
            boolean customButtonsAdded = false;
            List<Object> controlList = getControlList(guiScreen);
            for (Object o : controlList) {
                if (isGuiButton(o)) {
                    abp button = (abp) o;
                    if (getId(button) == InvTweaksConst.JIMEOWAN_ID) {
                        customButtonsAdded = true;
                        break;
                    }
                }
            }

            if (!customButtonsAdded) {

                // Inventory button
                if (!isValidChest) {
                    controlList.add(new InvTweaksGuiSettingsButton(
                            cfgManager, InvTweaksConst.JIMEOWAN_ID,
                            getWidth(guiScreen) / 2 + 73, getHeight(guiScreen) / 2 - 78,
                            w, h, "...",
                            InvTweaksLocalization.get("invtweaks.button.settings.tooltip")));
                }

                // Chest buttons
                else {
                    
                    // Reset sorting algorithm selector
                    chestAlgorithmClickTimestamp = 0;

                    gb guiContainer = (gb) guiScreen;
                    int id = InvTweaksConst.JIMEOWAN_ID,
                        x = getWidth(guiContainer) / 2 + getXSize(guiContainer) / 2 - 17,
                        y = (getHeight(guiContainer) - getYSize(guiContainer)) / 2 + 5;
                    boolean isChestWayTooBig = mods.isChestWayTooBig(guiScreen);

                    // NotEnoughItems compatibility
                    if (isChestWayTooBig && classExists("mod_NotEnoughItems")) {
                    	if (isNotEnoughItemsEnabled()) {
                        	x = getWidth(guiContainer) / 2 - getXSize(guiContainer) / 2 - 35;
                        	y += 50;
                    	}
                    }
                    
                    // Settings button
                    controlList.add(new InvTweaksGuiSettingsButton(
                            cfgManager, id++, 
                            (isChestWayTooBig) ? x + 22 : x - 1,
                            (isChestWayTooBig) ? y - 3 : y,
                            w, h, "...", 
                            InvTweaksLocalization.get("invtweaks.button.settings.tooltip")));

                    // Sorting buttons
                    if (!config.getProperty(InvTweaksConfig.PROP_SHOW_CHEST_BUTTONS).equals("false")) {

                        int rowSize = getContainerRowSize(guiContainer);
                        
                        InvTweaksObfuscationGuiButton button = new InvTweaksGuiSortingButton(
                                cfgManager, id++,
                                (isChestWayTooBig) ? x + 22 : x - 13,
                                (isChestWayTooBig) ? y + 12 : y,
                                w, h, "h", InvTweaksLocalization.get("invtweaks.button.chest3.tooltip"),
                                InvTweaksHandlerSorting.ALGORITHM_HORIZONTAL,
                                rowSize);
                        controlList.add(button);

                        button = new InvTweaksGuiSortingButton(
                                cfgManager, id++,
                                (isChestWayTooBig) ? x + 22 : x - 25,
                                (isChestWayTooBig) ? y + 25 : y,
                                w, h, "v", InvTweaksLocalization.get("invtweaks.button.chest2.tooltip"),
                                InvTweaksHandlerSorting.ALGORITHM_VERTICAL,
                                rowSize);
                        controlList.add(button);

                        button = new InvTweaksGuiSortingButton(
                                cfgManager, id++,
                                (isChestWayTooBig) ? x + 22 : x - 37,
                                (isChestWayTooBig) ? y + 38 : y,
                                w, h, "s", InvTweaksLocalization.get("invtweaks.button.chest1.tooltip"),
                                InvTweaksHandlerSorting.ALGORITHM_DEFAULT,
                                rowSize);
                        controlList.add(button);

                    }
                }
            }
        }

    }
    
    /**
     * Hacky parsing of the NEI configuration file to see if the mod is enabled or not.
     */
    private boolean isNotEnoughItemsEnabled() {
    	BufferedReader neiCfgFile;
		try {
			neiCfgFile = new BufferedReader(new FileReader(new File(InvTweaksConst.MINECRAFT_CONFIG_DIR + "NEI.cfg")));
	    	String line;
	    	while ((line = neiCfgFile.readLine()) != null) {
	    		if (line.contains("enable=true")) {
	    			return true;
	    		}
	    	}
	    	return false;
		} catch (IOException e) {
			return false;
		}
	}

	private void handleShortcuts(vp guiScreen) {
        
        // Check open GUI
        if (!(isValidChest(guiScreen) || isStandardInventory(guiScreen))) {
            return;
        }
        
        // Configurable shortcuts
        if (Mouse.isButtonDown(0) || Mouse.isButtonDown(1)) {
            if (!mouseWasDown) {
                mouseWasDown = true;
                
                // The mouse has just been clicked,
                // trigger a shortcut according to the pressed keys.
                if (cfgManager.getConfig().getProperty(
                        InvTweaksConfig.PROP_ENABLE_SHORTCUTS).equals("true")) {
                    cfgManager.getShortcutsHandler().handleShortcut((gb) guiScreen);
                }
            }
        }
        else {
            mouseWasDown = false;
        }
        
    }

    /**
     * Observes input state and hovered slot each GUI tick.
     * Logs to stdout only when state changes; never moves items.
     * Enabled via enableDragDebug=true in InvTweaks.cfg.
     */
    private void handleDragDebug(vp guiScreen) {
        InvTweaksConfig config = cfgManager.getConfig();
        if (config == null || !config.getProperty(InvTweaksConfig.PROP_ENABLE_DRAG_DEBUG)
                .equals(InvTweaksConfig.VALUE_TRUE)) {
            return;
        }

        boolean lmb   = Mouse.isButtonDown(0);
        boolean rmb   = Mouse.isButtonDown(1);
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                     || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);

        String guiType;
        if (guiScreen == null)                    guiType = "none";
        else if (isGuiChest(guiScreen))           guiType = "chest";
        else if (isGuiDispenser(guiScreen))       guiType = "dispenser";
        else if (isGuiInventory(guiScreen))       guiType = "inventory";
        else if (isGuiWorkbench(guiScreen))       guiType = "workbench";
        else if (isGuiFurnace(guiScreen))         guiType = "furnace";
        else if (isGuiBrewingStand(guiScreen))    guiType = "brewing";
        else if (isGuiEnchantmentTable(guiScreen))guiType = "enchanting";
        else                                      guiType = guiScreen.getClass().getSimpleName();

        int currentSlotNumber = -1;
        InvTweaksContainerSection currentSection = null;
        if (isGuiContainer(guiScreen)) {
            try {
                InvTweaksContainerManager dbgContainer = new InvTweaksContainerManager(mc);
                yu slot = dbgContainer.getSlotAtMousePosition();
                if (slot != null) {
                    currentSlotNumber = getSlotNumber(slot);
                    currentSection    = dbgContainer.getSlotSection(currentSlotNumber);
                }
            } catch (Exception e) {
                // slot detection must never crash debug logging
            }
        }

        boolean slotChanged  = currentSlotNumber != dbgLastSlotNumber;
        boolean stateChanged = lmb != dbgLastLmb || rmb != dbgLastRmb || shift != dbgLastShift
                            || !guiType.equals(dbgLastGuiType) || slotChanged;

        if (stateChanged) {
            String slotDesc = currentSlotNumber == -1
                    ? "none"
                    : "#" + currentSlotNumber + "[" + (currentSection != null ? currentSection : "?") + "]";
            String prevSlotDesc = dbgLastSlotNumber == -1 ? "none" : "#" + dbgLastSlotNumber;

            StringBuilder msg = new StringBuilder("[InvTweaks DragDebug]");
            msg.append(" gui=").append(guiType);
            msg.append(" lmb=").append(lmb);
            msg.append(" rmb=").append(rmb);
            msg.append(" shift=").append(shift);
            msg.append(" slot=").append(slotDesc);
            if (slotChanged) {
                msg.append(" prev=").append(prevSlotDesc);
            }
            System.out.println(msg.toString());

            dbgLastLmb        = lmb;
            dbgLastRmb        = rmb;
            dbgLastShift      = shift;
            dbgLastGuiType    = guiType;
            dbgLastSlotNumber = currentSlotNumber;
        }
    }

    /**
     * Detects when the cursor enters a new slot while Shift+LMB is held over a valid GUI.
     * Logs one [InvTweaks DragHover] line per newly entered slot; never moves items.
     * Controlled entirely by enableDragDebug; resets cleanly on gesture end.
     */
    private void handleDragHover(vp guiScreen) {
        InvTweaksConfig config = cfgManager.getConfig();
        if (config == null || !config.getProperty(InvTweaksConfig.PROP_ENABLE_DRAG_DEBUG)
                .equals(InvTweaksConfig.VALUE_TRUE)) {
            dragHoverGestureActive = false;
            dragHoverCurrentSlot   = -1;
            dragHoverPrevSlot      = -1;
            dragHoverEnteredNew    = false;
            return;
        }

        boolean lmb   = Mouse.isButtonDown(0);
        boolean shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                     || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        boolean validGui = isGuiContainer(guiScreen)
                        && (isValidChest(guiScreen) || isStandardInventory(guiScreen));

        if (lmb && shift && validGui) {
            dragHoverGestureActive = true;

            int currentSlot = -1;
            InvTweaksContainerSection currentSection = null;
            try {
                InvTweaksContainerManager hoverContainer = new InvTweaksContainerManager(mc);
                yu slot = hoverContainer.getSlotAtMousePosition();
                if (slot != null) {
                    currentSlot    = getSlotNumber(slot);
                    currentSection = hoverContainer.getSlotSection(currentSlot);
                }
            } catch (Exception e) {
                // slot detection must never crash the detection layer
            }

            dragHoverEnteredNew = (currentSlot != dragHoverCurrentSlot);
            if (dragHoverEnteredNew) {
                dragHoverPrevSlot    = dragHoverCurrentSlot;
                dragHoverCurrentSlot = currentSlot;
                if (currentSlot != -1) {
                    String sectionStr = currentSection != null ? currentSection.toString() : "?";
                    System.out.println("[InvTweaks DragHover] entered slot #" + currentSlot
                            + " section=" + sectionStr);
                }
            }
        } else {
            dragHoverGestureActive = false;
            dragHoverCurrentSlot   = -1;
            dragHoverPrevSlot      = -1;
            dragHoverEnteredNew    = false;
        }
    }

    /**
     * Executes a Shift+LMB-drag transfer for each newly entered slot while the
     * gesture is active.  Calls InvTweaksContainerManager.move() directly —
     * never calls handleShortcut(), which would destroy the mouse state mid-drag.
     * Controlled by enableDragTransfer; optional debug output via enableDragDebug.
     *
     * v0.5.0: when the cursor jumps from one slot to another in the same row or
     * column, intermediate slots that were skipped between ticks are processed
     * in order before the newly entered slot.  Diagonal jumps are skipped
     * (documented trade-off — extremely fast diagonal movement may still miss slots).
     */
    private void handleDragTransfer(vp guiScreen) {
        InvTweaksConfig config = cfgManager.getConfig();
        if (config == null || !config.getProperty(InvTweaksConfig.PROP_ENABLE_DRAG_TRANSFER)
                .equals(InvTweaksConfig.VALUE_TRUE)) {
            resetDragTransfer();
            return;
        }

        boolean lmb      = Mouse.isButtonDown(0);
        boolean shift    = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)
                        || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT);
        boolean validGui = isGuiContainer(guiScreen)
                        && (isValidChest(guiScreen) || isStandardInventory(guiScreen));

        if (!lmb || !shift || !validGui) {
            resetDragTransfer();
            return;
        }

        boolean debugEnabled = config.getProperty(InvTweaksConfig.PROP_ENABLE_DRAG_DEBUG)
                .equals(InvTweaksConfig.VALUE_TRUE);

        // Detect current slot
        int currentSlot = -1;
        yu slotObj = null;
        InvTweaksContainerManager xferContainer = null;
        try {
            xferContainer = new InvTweaksContainerManager(mc);
            slotObj = xferContainer.getSlotAtMousePosition();
            if (slotObj != null) {
                currentSlot = getSlotNumber(slotObj);
            }
        } catch (Exception e) {
            return;
        }

        // Cursor has not moved to a new slot — nothing to do this tick
        if (currentSlot == dragTransferCurrentSlot) {
            return;
        }

        // We entered a new slot — fill in any intermediate slots that were skipped
        if (dragTransferCurrentSlotX >= 0 && currentSlot != -1 && slotObj != null) {
            processIntermediateSlots(xferContainer,
                    dragTransferCurrentSlotX, dragTransferCurrentSlotY,
                    getXDisplayPosition(slotObj), getYDisplayPosition(slotObj),
                    debugEnabled);
        }

        // Update slot tracking (before doTransferSlot so prevX/Y are correct next tick)
        dragTransferCurrentSlot = currentSlot;
        if (currentSlot != -1 && slotObj != null) {
            dragTransferCurrentSlotX = getXDisplayPosition(slotObj);
            dragTransferCurrentSlotY = getYDisplayPosition(slotObj);
        } else {
            dragTransferCurrentSlotX = -1;
            dragTransferCurrentSlotY = -1;
        }

        // No slot under the cursor
        if (currentSlot == -1) {
            return;
        }

        // Process the newly entered slot
        doTransferSlot(xferContainer, currentSlot, slotObj, debugEnabled);
    }

    private void resetDragTransfer() {
        dragTransferCurrentSlot  = -1;
        dragTransferCurrentSlotX = -1;
        dragTransferCurrentSlotY = -1;
        dragTransferVisited.clear();
    }

    /**
     * Scans all slots in the container for ones whose display position falls
     * strictly between prevX/prevY and curX/curY along the same row or column.
     * Diagonal jumps are skipped entirely (too ambiguous to interpolate safely).
     */
    private void processIntermediateSlots(InvTweaksContainerManager xferContainer,
            int prevX, int prevY, int curX, int curY, boolean debugEnabled) {
        boolean sameRow = prevY == curY;
        boolean sameCol = !sameRow && prevX == curX;

        if (!sameRow && !sameCol) {
            if (debugEnabled) {
                System.out.println("[InvTweaks DragTransfer] interp skipped"
                        + " reason=diagonal prev=[" + prevX + "," + prevY + "]"
                        + " cur=[" + curX + "," + curY + "]");
            }
            return;
        }

        for (InvTweaksContainerSection sec : InvTweaksContainerSection.values()) {
            if (!xferContainer.hasSection(sec)) continue;
            List<yu> sSlots = xferContainer.getSlots(sec);
            if (sSlots == null) continue;
            for (yu candidate : sSlots) {
                int cNum = getSlotNumber(candidate);
                if (dragTransferVisited.contains(cNum)) continue;
                int cx = getXDisplayPosition(candidate);
                int cy = getYDisplayPosition(candidate);
                boolean between = sameRow
                        ? cy == prevY && isBetween(prevX, curX, cx)
                        : cx == prevX && isBetween(prevY, curY, cy);
                if (between) {
                    if (debugEnabled) {
                        System.out.println("[InvTweaks DragTransfer] interp slot #" + cNum
                                + (sameRow ? " axis=row" : " axis=col"));
                    }
                    doTransferSlot(xferContainer, cNum, candidate, debugEnabled);
                }
            }
        }
    }

    /**
     * Validates and executes a single-slot Shift+LMB-drag transfer.
     * All safeguards (visited, empty, hand-busy, crafting, no-target) are checked here.
     */
    private void doTransferSlot(InvTweaksContainerManager xferContainer,
            int slotNum, yu slotObj, boolean debugEnabled) {
        // Per-gesture deduplication
        if (dragTransferVisited.contains(slotNum)) {
            return;
        }

        // Do not act while the cursor is holding a stack
        if (getHoldStack() != null) {
            if (debugEnabled) {
                System.out.println("[InvTweaks DragTransfer] skipped slot #" + slotNum
                        + " reason=hand_busy");
            }
            return;
        }

        // Skip empty slots
        if (!hasStack(slotObj)) {
            if (debugEnabled) {
                System.out.println("[InvTweaks DragTransfer] skipped slot #" + slotNum
                        + " reason=empty");
            }
            return;
        }

        InvTweaksContainerSection fromSection = xferContainer.getSlotSection(slotNum);
        int fromIndex = xferContainer.getSlotIndex(slotNum);

        if (fromSection == null || fromIndex == -1) {
            if (debugEnabled) {
                System.out.println("[InvTweaks DragTransfer] skipped slot #" + slotNum
                        + " reason=no_section");
            }
            return;
        }

        // Skip sections that must never be auto-transferred (see isUnsafeSection for rationale)
        if (isUnsafeSection(fromSection)) {
            if (debugEnabled) {
                System.out.println("[InvTweaks DragTransfer] skipped slot #" + slotNum
                        + " reason=unsafe_section section=" + fromSection);
            }
            return;
        }

        InvTweaksContainerSection toSection = resolveTransferTarget(xferContainer, fromSection);
        if (toSection == null) {
            if (debugEnabled) {
                System.out.println("[InvTweaks DragTransfer] skipped slot #" + slotNum
                        + " reason=no_target section=" + fromSection);
            }
            return;
        }

        // Mark visited before executing so a partial failure does not re-trigger
        dragTransferVisited.add(slotNum);

        // Execute MOVE_ONE_STACK transfer — mirrors runShortcut MOVE_ONE_STACK logic
        try {
            aan fromStack   = copy(getStack(slotObj));
            yu  fromSlot    = xferContainer.getSlot(fromSection, fromIndex);
            int toIndex     = findDragDestIndex(xferContainer, toSection, fromStack);
            int prevToIndex = -1;
            boolean anythingMoved = false;

            while (hasStack(fromSlot) && toIndex != -1) {
                boolean success = xferContainer.move(fromSection, fromIndex, toSection, toIndex);
                if (success) anythingMoved = true;
                prevToIndex = toIndex;
                toIndex = findDragDestIndex(xferContainer, toSection, fromStack);
                if (!success && toIndex == prevToIndex) break; // destination full, avoid spin
            }

            if (debugEnabled) {
                if (anythingMoved) {
                    System.out.println("[InvTweaks DragTransfer] moved slot #" + slotNum
                            + " section=" + fromSection);
                } else {
                    System.out.println("[InvTweaks DragTransfer] skipped slot #" + slotNum
                            + " reason=dest_full section=" + fromSection);
                }
            }
        } catch (Exception e) {
            // never crash the GUI tick
        }
    }

    /**
     * Returns true for sections that drag-transfer must never act on.
     *
     * Rationale per section:
     *   CRAFTING_OUT      — output slot auto-refills; grabbing it mid-recipe is risky
     *   CRAFTING_IN       — crafting grid inputs; unexpected mid-recipe removal
     *   ARMOR             — armor slots; auto-equip is a separate feature, not drag-transfer
     *   FURNACE_OUT       — like CRAFTING_OUT: auto-fills when smelting completes
     *   ENCHANTMENT       — single-slot; removing the item cancels the enchantment
     *   BREWING_INGREDIENT — removing the ingredient mid-brew silently cancels the brew
     *
     * Allowed: FURNACE_IN, FURNACE_FUEL, BREWING_BOTTLES — all behave like normal inventory
     * slots that the player routinely shift-clicks in and out of.
     */
    private static boolean isUnsafeSection(InvTweaksContainerSection section) {
        switch (section) {
        case CRAFTING_OUT:
        case CRAFTING_IN:
        case ARMOR:
        case FURNACE_OUT:
        case ENCHANTMENT:
        case BREWING_INGREDIENT:
            return true;
        default:
            return false;
        }
    }

    private static boolean isBetween(int from, int to, int val) {
        return from < to ? val > from && val < to : val < from && val > to;
    }

    /**
     * Mirrors the implicit toSection logic from
     * InvTweaksHandlerShortcuts.computeShortcutToTrigger for left-click transfers.
     */
    private InvTweaksContainerSection resolveTransferTarget(
            InvTweaksContainerManager container,
            InvTweaksContainerSection fromSection) {
        boolean hasChest = container.hasSection(InvTweaksContainerSection.CHEST);
        switch (fromSection) {
        case CHEST:
            return InvTweaksContainerSection.INVENTORY;
        case INVENTORY_HOTBAR:
            return hasChest ? InvTweaksContainerSection.CHEST
                            : InvTweaksContainerSection.INVENTORY_NOT_HOTBAR;
        default:
            return hasChest ? InvTweaksContainerSection.CHEST
                            : InvTweaksContainerSection.INVENTORY_HOTBAR;
        }
    }

    /**
     * Mirrors InvTweaksHandlerShortcuts.getNextTargetIndex: tries to merge into
     * a partial stack of the same type, then falls back to the first empty slot.
     */
    private int findDragDestIndex(
            InvTweaksContainerManager container,
            InvTweaksContainerSection toSection,
            aan fromStack) {
        if (!container.hasSection(toSection)) {
            return -1;
        }
        // Try to merge with a partial stack of the same item type
        if (fromStack != null && !hasDataTags(fromStack)) {
            int i = 0;
            List<yu> slots = container.getSlots(toSection);
            if (slots != null) {
                for (yu slot : slots) {
                    if (hasStack(slot)) {
                        aan stack = getStack(slot);
                        if (!hasDataTags(stack) && areItemsEqual(stack, fromStack)
                                && getStackSize(stack) < getMaxStackSize(stack)) {
                            return i;
                        }
                    }
                    i++;
                }
            }
        }
        // Fall back to first empty slot
        return container.getFirstEmptyIndex(toSection);
    }

    private int getContainerRowSize(gb guiContainer) {
        if (isGuiChest(guiContainer)) {
            return InvTweaksConst.CHEST_ROW_SIZE;
        }
        else if (isGuiDispenser(guiContainer)) {
            return InvTweaksConst.DISPENSER_ROW_SIZE;
        }
        else {
            return getSpecialChestRowSize(guiContainer, InvTweaksConst.CHEST_ROW_SIZE);
        }
    }

    private boolean isSortingShortcutDown() {
    	int keyCode = getKeyCode(SORT_KEY_BINDING);
    	if (keyCode > 0) {
    		return Keyboard.isKeyDown(keyCode);
    	}
    	else {
    		return Mouse.isButtonDown(100 + keyCode);
    	}
	}

    private boolean isTimeForPolling() {
        if (tickNumber - lastPollingTickNumber >= InvTweaksConst.POLLING_DELAY) {
            lastPollingTickNumber = tickNumber;
        }
        return tickNumber - lastPollingTickNumber == 0;
    }

    /**
     * When the mouse gets inside the window, reset pressed keys
     * to avoid the "stuck keys" bug.
     */
    private void unlockKeysIfNecessary() {
        boolean mouseInWindow = Mouse.isInsideWindow();
        if (!mouseWasInWindow && mouseInWindow) {
            Keyboard.destroy();
            boolean firstTry = true;
            while (!Keyboard.isCreated()) {
                try {
                    Keyboard.create();
                } catch (LWJGLException e) {
                    if (firstTry) {
                        logInGameError("invtweaks.keyboardfix.error", e);
                        firstTry = false;
                    }
                }
            }
            if (!firstTry) {
                logInGame("invtweaks.keyboardfix.recover");
            }
        }
        mouseWasInWindow = mouseInWindow;
    }

    /**
     * Allows to maintain a clone of the hotbar contents to track changes
     * (especially needed by the "on pickup" features).
     */
    private void cloneHotbar() {
        aan[] mainInventory = getMainInventory();
        for (int i = 0; i < 9; i++) {
            if (mainInventory[i] != null) {
                hotbarClone[i] = copy(mainInventory[i]);
            } else {
                hotbarClone[i] = null;
            }
        }
    }

    private void playClick() {
        if (!cfgManager.getConfig().getProperty(InvTweaksConfig.PROP_ENABLE_SOUNDS).equals(InvTweaksConfig.VALUE_FALSE)) {
            playSoundAtEntity(getTheWorld(), getThePlayer(), "random.click", 0.2F, 1.8F);
        }
    }

    private String buildlogString(Level level, String message, Exception e) {
        if (e != null) {
            return buildlogString(level, message) + ": " + e.getMessage();
        } else {
            return buildlogString(level, message) + ": (unknown error)";
        }
    }

    private String buildlogString(Level level, String message) {
        return InvTweaksConst.INGAME_LOG_PREFIX + ((level.equals(Level.SEVERE)) ? "[ERROR] " : "") + message;
    }
    
}
