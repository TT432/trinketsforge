package io.github.tt432.trinketsforge.mixin;

import dev.emi.trinkets.TrinketFeatureRenderer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the trinket feature renderer to the list of living entity features
 *
 * @author C4
 * @author powerboat9
 */
// @Environment(EnvType.CLIENT)
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Invoker("addLayer")
    public abstract boolean invokeAddFeature(RenderLayer feature);

	@Inject(at = @At("RETURN"), method = "<init>")
	public void init(EntityRendererProvider.Context ctx, EntityModel model, float shadowRadius, CallbackInfo ci) {
        this.invokeAddFeature(new TrinketFeatureRenderer<>((LivingEntityRenderer) (Object) this));
	}
}
