package dev.emi.trinkets;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.emi.trinkets.api.TrinketsApi;
import dev.emi.trinkets.api.client.TrinketRendererRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;

public class TrinketFeatureRenderer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {

	public TrinketFeatureRenderer(RenderLayerParent<T, M> context) {
		super(context);
	}

	@Override
	public void render(PoseStack matrices, MultiBufferSource vertexConsumers, int light, T entity, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
		TrinketsApi.getTrinketComponent(entity).ifPresent(component ->
				component.forEach((slotReference, stack) ->
						TrinketRendererRegistry.getRenderer(stack.getItem()).ifPresent(renderer -> {
							matrices.pushPose();
							renderer.render(stack, slotReference, this.getParentModel(), matrices, vertexConsumers,
									light, entity, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
							matrices.popPose();
						})
				)
		);
	}
}
