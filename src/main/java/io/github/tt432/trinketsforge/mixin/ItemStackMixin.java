package dev.emi.trinkets.mixin;

import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import dev.emi.trinkets.TrinketSlot;
import dev.emi.trinkets.api.SlotAttributes;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.SlotType;
import dev.emi.trinkets.api.Trinket;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketsApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Adds a tooltip for trinkets describing slots and attributes
 * 
 * @author Emi
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
	
	@Inject(at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isSectionVisible(ILnet/minecraft/item/ItemStack$TooltipSection;)Z",
		ordinal = 4, shift = Shift.BEFORE), method = "getTooltip", locals = LocalCapture.CAPTURE_FAILHARD)
	private void getTooltip(Player player, TooltipFlag context, CallbackInfoReturnable<List<Component>> info, List<Component> list) {
		TrinketsApi.getTrinketComponent(player).ifPresent(comp -> {
			ItemStack self = (ItemStack) (Object) this;
			boolean canEquipAnywhere = true;
			Set<SlotType> slots = Sets.newHashSet();
			Map<SlotType, Multimap<Attribute, AttributeModifier>> modifiers = Maps.newHashMap();
			Multimap<Attribute, AttributeModifier> defaultModifier = null;
			boolean allModifiersSame = true;
			int slotCount = 0;

			for (Map.Entry<String, Map<String, TrinketInventory>> group : comp.getInventory().entrySet()) {
				outer:
				for (Map.Entry<String, TrinketInventory> inventory : group.getValue().entrySet()) {
					TrinketInventory trinketInventory = inventory.getValue();
					SlotType slotType = trinketInventory.getSlotType();
					slotCount++;
					boolean anywhereButHidden = false;
					for (int i = 0; i < trinketInventory.getContainerSize(); i++) {
						SlotReference ref = new SlotReference(trinketInventory, i);
						boolean res = TrinketsApi.evaluatePredicateSet(slotType.getTooltipPredicates(), self, ref, player);
						boolean canInsert = TrinketSlot.canInsert(self, ref, player);
						if (res && canInsert) {
							boolean sameTranslationExists = false;
							for (SlotType t : slots) {
								if (t.getTranslation().getString().equals(slotType.getTranslation().getString())) {
									sameTranslationExists = true;
									break;
								}
							}
							if (!sameTranslationExists) {
								slots.add(slotType);
							}
							Trinket trinket = TrinketsApi.getTrinket((self).getItem());

							Multimap<Attribute, AttributeModifier> map =
									trinket.getModifiers(self, ref, player, SlotAttributes.getUuid(ref));
							
							if (defaultModifier == null) {
								defaultModifier = map;
							} else if (allModifiersSame) {
								allModifiersSame = areMapsEqual(defaultModifier, map);
							}

							boolean duplicate = false;
							for (var entry : modifiers.entrySet()) {
								if (entry.getKey().getTranslation().getString().equals(slotType.getTranslation().getString())) {
									if (areMapsEqual(entry.getValue(), map)) {
										duplicate = true;
										break;
									}
								}
							}

							if (!duplicate) {
								modifiers.put(slotType, map);
							}
							continue outer;
						} else if (canInsert) {
							anywhereButHidden = true;
						}
					}
					if (!anywhereButHidden) {
						canEquipAnywhere = false;
					}
				}
			}

			if (canEquipAnywhere && slotCount > 1) {
				list.add(Component.translatable("trinkets.tooltip.slots.any").withStyle(ChatFormatting.GRAY));
			} else if (slots.size() > 1) {
				list.add(Component.translatable("trinkets.tooltip.slots.list").withStyle(ChatFormatting.GRAY));
				for (SlotType type : slots) {
					list.add(type.getTranslation().withStyle(ChatFormatting.BLUE));
				}
			} else if (slots.size() == 1) {
				// Should only run once
				for (SlotType type : slots) {
					list.add(Component.translatable("trinkets.tooltip.slots.single",
							type.getTranslation().withStyle(ChatFormatting.BLUE)).withStyle(ChatFormatting.GRAY));
				}
			}

			if (modifiers.size() > 0) {
				if (allModifiersSame) {
					if (defaultModifier != null && !defaultModifier.isEmpty()) {
						list.add(Component.translatable("trinkets.tooltip.attributes.all").withStyle(ChatFormatting.GRAY));
						addAttributes(list, defaultModifier);
					}
				} else {
					for (SlotType type : modifiers.keySet()) {
						list.add(Component.translatable("trinkets.tooltip.attributes.single",
								type.getTranslation().withStyle(ChatFormatting.BLUE)).withStyle(ChatFormatting.GRAY));
						addAttributes(list, modifiers.get(type));
					}
				}
			}
		});
	}

	@Unique
	private void addAttributes(List<Component> list, Multimap<Attribute, AttributeModifier> map) {
		if (!map.isEmpty()) {
			for (Map.Entry<Attribute, AttributeModifier> entry : map.entries()) {
				Attribute attribute = entry.getKey();
				AttributeModifier modifier = entry.getValue();
				double g = modifier.getAmount();

				if (modifier.getOperation() != AttributeModifier.Operation.MULTIPLY_BASE && modifier.getOperation() != AttributeModifier.Operation.MULTIPLY_TOTAL) {
					if (entry.getKey().equals(Attributes.KNOCKBACK_RESISTANCE)) {
						g *= 10.0D;
					}
				} else {
					g *= 100.0D;
				}

				Component text = Component.translatable(attribute.getDescriptionId());
				if (attribute instanceof SlotAttributes.SlotEntityAttribute) {
					text = Component.translatable("trinkets.tooltip.attributes.slots", text);
				}
				if (g > 0.0D) {
					list.add(Component.translatable("attribute.modifier.plus." + modifier.getOperation().toValue(),
						ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(g), text).withStyle(ChatFormatting.BLUE));
				} else if (g < 0.0D) {
					g *= -1.0D;
					list.add(Component.translatable("attribute.modifier.take." + modifier.getOperation().toValue(),
						ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(g), text).withStyle(ChatFormatting.RED));
				}
			}
		}
	}

	// `equals` doesn't test thoroughly
	@Unique
	private boolean areMapsEqual(Multimap<Attribute, AttributeModifier> map1, Multimap<Attribute, AttributeModifier> map2) {
		if (map1.size() != map2.size()) {
			return false;
		} else {
			for (Attribute attribute : map1.keySet()) {
				if (!map2.containsKey(attribute)) {
					return false;
				}

				Collection<AttributeModifier> col1 = map1.get(attribute);
				Collection<AttributeModifier> col2 = map2.get(attribute);

				if (col1.size() != col2.size()) {
					return false;
				} else {
					Iterator<AttributeModifier> iter = col2.iterator();

					for (AttributeModifier modifier : col1) {
						AttributeModifier eam = iter.next();

						if (!modifier.save().equals(eam.save())) {
							return false;
						}
					}
				}
			}
		}
		return true;
	}
}
