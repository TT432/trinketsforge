package io.github.tt432.trinketsforge.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.emi.trinkets.TrinketScreenManager;
import dev.emi.trinkets.TrinketSlot;
import dev.emi.trinkets.TrinketsClient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen.SlotWrapper;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws trinket slot backs, adjusts z location of draw calls, and makes non-trinket slots un-interactable while a trinket slot group is focused
 * 
 * @author Emi
 */
@Mixin(AbstractContainerScreen.class)
public abstract class HandledScreenMixin extends Screen {
	@Unique
	private static final ResourceLocation MORE_SLOTS = new ResourceLocation("trinkets", "textures/gui/more_slots.png");
	@Unique
	private static final ResourceLocation BLANK_BACK = new ResourceLocation("trinkets", "textures/gui/blank_back.png");

	private HandledScreenMixin() {
		super(null);
	}

	@Inject(at = @At("HEAD"), method = "removed")
	private void removed(CallbackInfo info) {
		if ((Object)this instanceof InventoryScreen) {
			TrinketScreenManager.removeSelections();
		}
	}

	@Inject(at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"),
		method = "renderSlot")
	private void changeZ(PoseStack matrices, Slot slot, CallbackInfo info) {
		// Items are drawn at z + 150 (normal items are drawn at 250)
		// Item tooltips (count, item bar) are drawn at z + 200 (normal itmes are drawn at 300)
		// Inventory tooltip is drawn at 400
		if (slot instanceof TrinketSlot ts) {
			assert this.minecraft != null;
			ResourceLocation id = ts.getBackgroundIdentifier();

			if (slot.getItem().isEmpty() && id != null) {
				RenderSystem.setShaderTexture(0, id);
			} else {
				RenderSystem.setShaderTexture(0, BLANK_BACK);
			}

			RenderSystem.enableDepthTest();

			if (ts.isTrinketFocused()) {
				// Thus, I need to draw trinket slot backs over normal items at z 300 (310 was chosen)
				blit(matrices, slot.x, slot.y, 310, 0, 0, 16, 16, 16, 16);
				// I also need to draw items in trinket slots *above* 310 but *below* 400, (320 for items and 370 for tooltips was chosen)
				matrices.translate(0, 0, 70);
			} else {
				blit(matrices, slot.x, slot.y, 0, 0, 0, 16, 16, 16, 16);
				RenderSystem.setShaderTexture(0, MORE_SLOTS);
				blit(matrices, slot.x - 1, slot.y - 1, 0, 4, 4, 18, 18, 256, 256);
			}
		}
		if (TrinketsClient.activeGroup != null && TrinketsClient.activeGroup.getSlotId() == slot.index) {
			matrices.translate(0, 0, 70);
		}

	}

	@Inject(at = @At("HEAD"), method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", cancellable = true)
	private void isPointOverSlot(Slot slot, double pointX, double pointY, CallbackInfoReturnable<Boolean> info) {
		if (TrinketsClient.activeGroup != null) {
			if (slot instanceof TrinketSlot ts) {
				if (!ts.isTrinketFocused()) {
					info.setReturnValue(false);
				}
			} else {
				if (slot instanceof SlotWrapper cs) {
					if (cs.target.index != TrinketsClient.activeGroup.getSlotId()) {
						info.setReturnValue(false);
					}
				} else if (slot.index != TrinketsClient.activeGroup.getSlotId()) {
					info.setReturnValue(false);
				}
			}
		}
	}
}
