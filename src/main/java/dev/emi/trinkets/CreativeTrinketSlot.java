package dev.emi.trinkets;

import dev.emi.trinkets.api.SlotType;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.SlotWrapper;
import net.minecraft.resources.ResourceLocation;

/**
 * A gui slot for a trinket slot in the creative inventory
 */
public class CreativeTrinketSlot extends SlotWrapper implements TrinketSlot {
	private final SurvivalTrinketSlot original;

	public CreativeTrinketSlot(SurvivalTrinketSlot original, int s, int x, int y) {
		super(original, s, x, y);
		this.original = original;
	}

	@Override
	public boolean isTrinketFocused() {
		return original.isTrinketFocused();
	}

	@Override
	public ResourceLocation getBackgroundIdentifier() {
		return original.getBackgroundIdentifier();
	}

	@Override
	public SlotType getType() {
		return original.getType();
	}
}
