package io.github.tt432.trinketsforge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.emi.trinkets.TrinketsClient;
import net.minecraft.client.gui.components.AbstractWidget;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes buttons uninteractable while trinket groups are being interacted with
 * 
 * @author Emi
 */
@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetMixin {

	@Shadow
	protected boolean isHovered;
	
	@Inject(at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/components/AbstractWidget;isHovered:Z",
			opcode = Opcodes.PUTFIELD, ordinal = 0, shift = Shift.AFTER), method = "render")
	private void render(PoseStack matrices, int mouseX, int mouseY, float delta, CallbackInfo info) {
		if (TrinketsClient.activeGroup != null) {
			isHovered = false;
		}
	}

	@Inject(at = @At("HEAD"), method = "mouseClicked", cancellable = true)
	private void mouseClicked(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> info) {
		if (TrinketsClient.activeGroup != null) {
			info.setReturnValue(false);
		}
	}
}
