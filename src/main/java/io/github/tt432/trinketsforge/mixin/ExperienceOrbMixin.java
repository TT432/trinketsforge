package io.github.tt432.trinketsforge.mixin;

import com.google.common.collect.Maps;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketsApi;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Applies mending to trinkets (fairly)
 * 
 * @author Emi
 */
@Mixin(ExperienceOrb.class)
public class ExperienceOrbMixin {
	@Unique
	private Player mendingPlayer;
	
	@Inject(at = @At("HEAD"), method = "repairPlayerItems")
	private void repairPlayerGears(Player player, int amount, CallbackInfoReturnable<Integer> info) {
		mendingPlayer = player;
	}

	@ModifyVariable(at = @At(value = "INVOKE_ASSIGN", target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;getRandomItemWith(Lnet/minecraft/world/item/enchantment/Enchantment;Lnet/minecraft/world/entity/LivingEntity;Ljava/util/function/Predicate;)Ljava/util/Map$Entry;"),
		method = "repairPlayerItems")
	private Entry<EquipmentSlot, ItemStack> modifyEntry(Entry<EquipmentSlot, ItemStack> entry) {
		Optional<TrinketComponent> optional = TrinketsApi.getTrinketComponent(mendingPlayer);
		if (optional.isPresent()) {
			TrinketComponent comp = optional.get();
			Predicate<ItemStack> predicate = stack -> !stack.isEmpty() && stack.isDamaged() && EnchantmentHelper.getItemEnchantmentLevel(Enchantments.MENDING, stack) > 0;
			List<Tuple<SlotReference, ItemStack>> list = comp.getEquipped(predicate);
			int totalSize = list.size();

			if (entry != null) {
				Map<EquipmentSlot, ItemStack> map = Enchantments.MENDING.getSlotItems(mendingPlayer);
				// The map contains ALL equipped items, so we need to filter for Mending candidates specifically
				ArrayList<Entry<EquipmentSlot, ItemStack>> originalList = new ArrayList<>();
				for (Entry<EquipmentSlot, ItemStack> ent : map.entrySet()) {
					if (predicate.test(ent.getValue())) {
						originalList.add(ent);
					}
				}
				totalSize += originalList.size();
			}

			if (totalSize == 0) {
				return entry;
			}
			int selected = mendingPlayer.getRandom().nextInt(totalSize);
			if (selected < list.size()) {
				Tuple<SlotReference, ItemStack> pair = list.get(selected);
				Map<EquipmentSlot, ItemStack> dummyMap = Maps.newHashMap();
				dummyMap.put(EquipmentSlot.MAINHAND, pair.getB());
				entry = dummyMap.entrySet().iterator().next();
			}
		}
		mendingPlayer = null;
		return entry;
	}
}
