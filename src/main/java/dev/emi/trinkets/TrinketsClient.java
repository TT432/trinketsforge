package dev.emi.trinkets;

import dev.emi.trinkets.data.EntitySlotLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import dev.emi.trinkets.api.SlotGroup;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.SlotType;
import dev.emi.trinkets.api.Trinket;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketsApi;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class TrinketsClient implements ClientModInitializer {
	public static SlotGroup activeGroup;
	public static SlotType activeType;
	public static SlotGroup quickMoveGroup;
	public static SlotType quickMoveType;
	public static int quickMoveTimer;

	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(TrinketsNetwork.SYNC_INVENTORY, (client, handler, buf, responseSender) -> {
			int entityId = buf.readInt();
			CompoundTag inventoryData = buf.readNbt();
			CompoundTag contentData = buf.readNbt();
			List<Tuple<String, ItemStack>> contentUpdates = new ArrayList<>();
			List<Triple<String, String, CompoundTag>> inventoryUpdates = new ArrayList<>();

			if (inventoryData != null) {
				for (String key : inventoryData.getAllKeys()) {
					String[] split = key.split("/");
					String group = split[0];
					String slot = split[1];
					CompoundTag tag = inventoryData.getCompound(key);
					inventoryUpdates.add(new ImmutableTriple<>(group, slot, tag));
				}
			}

			if (contentData != null) {
				for (String key : contentData.getAllKeys()) {
					ItemStack stack = ItemStack.of(contentData.getCompound(key));
					contentUpdates.add(new Tuple<>(key, stack));
				}
			}

			client.execute(() -> {
				Entity entity = client.level.getEntity(entityId);
				if (entity instanceof LivingEntity) {
					TrinketsApi.getTrinketComponent((LivingEntity) entity).ifPresent(trinkets -> {
						for (Triple<String, String, CompoundTag> entry : inventoryUpdates) {
							Map<String, TrinketInventory> slots = trinkets.getInventory().get(entry.getLeft());
							if (slots != null) {
								TrinketInventory inv = slots.get(entry.getMiddle());
								if (inv != null) {
									inv.applySyncTag(entry.getRight());
								}
							}
						}

						if (entity instanceof Player && ((Player) entity).inventoryMenu instanceof TrinketPlayerScreenHandler screenHandler) {
							screenHandler.trinkets$updateTrinketSlots(false);
							if (TrinketScreenManager.currentScreen != null) {
								TrinketScreenManager.currentScreen.trinkets$updateTrinketSlots();
							}
						}

						for (Tuple<String, ItemStack> entry : contentUpdates) {
							String[] split = entry.getA().split("/");
							String group = split[0];
							String slot = split[1];
							int index = Integer.parseInt(split[2]);
							Map<String, TrinketInventory> slots = trinkets.getInventory().get(group);
							if (slots != null) {
								TrinketInventory inv = slots.get(slot);
								if (inv != null && index < inv.getContainerSize()) {
									inv.setItem(index, entry.getB());
								}
							}
						}
					});
				}
			});
		});
		ClientPlayNetworking.registerGlobalReceiver(TrinketsNetwork.SYNC_SLOTS, (client, handler, buf, responseSender) -> {
			CompoundTag data = buf.readNbt();

			if (data != null) {
				Map<EntityType<?>, Map<String, SlotGroup>> slots = new HashMap<>();

				for (String id : data.getAllKeys()) {
					Optional<EntityType<?>> maybeType = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.tryParse(id));
					maybeType.ifPresent(type -> {
						CompoundTag groups = data.getCompound(id);

						if (groups != null) {

							for (String groupId : groups.getAllKeys()) {
								CompoundTag group = groups.getCompound(groupId);

								if (group != null) {
									SlotGroup slotGroup = SlotGroup.read(group);
									slots.computeIfAbsent(type, (k) -> new HashMap<>()).put(groupId, slotGroup);
								}
							}
						}
					});
				}
				client.execute(() -> {
					EntitySlotLoader.CLIENT.setSlots(slots);
					LocalPlayer player = client.player;

					if (player != null) {
						((TrinketPlayerScreenHandler) player.inventoryMenu).trinkets$updateTrinketSlots(true);

						if (client.screen instanceof TrinketScreen trinketScreen) {
							trinketScreen.trinkets$updateTrinketSlots();
						}

						for (AbstractClientPlayer clientWorldPlayer : player.clientLevel.players()) {
							((TrinketPlayerScreenHandler) clientWorldPlayer.inventoryMenu).trinkets$updateTrinketSlots(true);
						}
					}
				});
			}
		});
		ClientPlayNetworking.registerGlobalReceiver(TrinketsNetwork.BREAK, (client, handler, buf, sender) -> {
			int entityId = buf.readInt();
			String[] split = buf.readUtf().split("/");
			String group = split[0];
			String slot = split[1];
			int index = buf.readInt();
			client.execute(() -> {
				Entity e = client.level.getEntity(entityId);
				if (e instanceof LivingEntity entity) {
					TrinketsApi.getTrinketComponent(entity).ifPresent(comp -> {
						var groupMap = comp.getInventory().get(group);
						if (groupMap != null) {
							TrinketInventory inv = groupMap.get(slot);
							if (index < inv.getContainerSize()) {
								ItemStack stack = inv.getItem(index);
								SlotReference ref = new SlotReference(inv, index);
								Trinket trinket = TrinketsApi.getTrinket(stack.getItem());
								trinket.onBreak(stack, ref, entity);
							}
						}
					});
				}
			});
		});
	}
}
