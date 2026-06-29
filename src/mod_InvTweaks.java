import invtweaks.InvTweaksConst;

import net.minecraft.client.Minecraft;

/**
 * ModLoader entry point for Rory's Inventory Tweaks.
 *
 * Original mod: Inventory Tweaks by Jimeo Wan (Marwane Kalam-Alami)
 * Source: https://github.com/mkalam-alami/inventory-tweaks
 * License: MIT
 */
public class mod_InvTweaks extends BaseMod {

	private InvTweaks instance;

	@Override
	public String getName() {
		return "Rory's Inventory Tweaks";
	}

	@Override
	public void load() {
		
		Minecraft mc = ModLoader.getMinecraftInstance();

		// Register key (listen only for key down events)
		ModLoader.registerKey(this, InvTweaks.SORT_KEY_BINDING, false);

		// Register in game hooks
		ModLoader.setInGameHook(this, true, true);
		ModLoader.setInGUIHook(this, true, false);

		// Instantiate mod core
		instance = new InvTweaks(mc);

	}

	@Override
	public String getVersion() {
		return InvTweaksConst.MOD_VERSION;
	}

	/**
	 * Called by ModLoader each time the sorting key is pressed.
	 */
	public void keyboardEvent(afu keyBinding) {
		instance.onSortingKeyPressed();
	}

	/**
	 * Called by ModLoader for each tick during the game (except when a menu is
	 * open).
	 */
	public boolean onTickInGame(float clock, Minecraft minecraft) {
		instance.onTickInGame();
		return true;
	}

	/**
	 * Called by ModLoader for each tick while the player is in a menu.
	 */
	public boolean onTickInGUI(float clock, Minecraft minecraft, vp guiScreen) {
		if (guiScreen != null) {
			instance.onTickInGUI(guiScreen);
		}
		return true;
	}

	/**
	 * Called by ModLoader when an item has been picked up.
	 */
	public void onItemPickup(yw player, aan item) {
		instance.onItemPickup();
	}

}
