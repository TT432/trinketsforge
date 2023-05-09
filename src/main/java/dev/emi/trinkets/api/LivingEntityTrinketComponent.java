package dev.emi.trinkets.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import dev.emi.trinkets.TrinketPlayerScreenHandler;
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent;
import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class LivingEntityTrinketComponent implements TrinketComponent, AutoSyncedComponent {

	public Map<String, Map<String, TrinketInventory>> inventory = new HashMap<>();
	public Set<TrinketInventory> trackingUpdates = new HashSet<>();
	public Map<String, SlotGroup> groups = new HashMap<>();
	public int size;
	public LivingEntity entity;

	private boolean syncing;

	public LivingEntityTrinketComponent(LivingEntity entity) {
		this.entity = entity;
		this.update();
	}

	@Override
	public LivingEntity getEntity() {
		return this.entity;
	}

	@Override
	public Map<String, SlotGroup> getGroups() {
		return this.groups;
	}

	@Override
	public Map<String, Map<String, TrinketInventory>> getInventory() {
		return inventory;
	}

	@Override
	public void update() {
		Map<String, SlotGroup> entitySlots = TrinketsApi.getEntitySlots(this.entity);
		int count = 0;
		groups.clear();
		Map<String, Map<String, TrinketInventory>> inventory = new HashMap<>();
		for (Map.Entry<String, SlotGroup> group : entitySlots.entrySet()) {
			String groupKey = group.getKey();
			SlotGroup groupValue = group.getValue();
			Map<String, TrinketInventory> oldGroup = this.inventory.get(groupKey);
			groups.put(groupKey, groupValue);
			for (Map.Entry<String, SlotType> slot : groupValue.getSlots().entrySet()) {
				TrinketInventory inv = new TrinketInventory(slot.getValue(), this, e -> this.trackingUpdates.add(e));
				if (oldGroup != null) {
					TrinketInventory oldInv = oldGroup.get(slot.getKey());
					if (oldInv != null) {
						inv.copyFrom(oldInv);
						for (int i = 0; i < oldInv.getContainerSize(); i++) {
							ItemStack stack = oldInv.getItem(i).copy();
							if (i < inv.getContainerSize()) {
								inv.setItem(i, stack);
							} else {
								if (this.entity instanceof Player player) {
									player.getInventory().placeItemBackInInventory(stack);
								} else {
									this.entity.spawnAtLocation(stack);
								}
							}
						}
					}
				}
				inventory.computeIfAbsent(group.getKey(), (k) -> new HashMap<>()).put(slot.getKey(), inv);
				count += inv.getContainerSize();
			}
		}
		size = count;
		this.inventory = inventory;
	}

	@Override
	public void clearCachedModifiers() {
		for (Map.Entry<String, Map<String, TrinketInventory>> group : this.getInventory().entrySet()) {
			for (Map.Entry<String, TrinketInventory> slotType : group.getValue().entrySet()) {
				slotType.getValue().clearCachedModifiers();
			}
		}
	}

	@Override
	public Set<TrinketInventory> getTrackingUpdates() {
		return this.trackingUpdates;
	}

	@Override
	public void addTemporaryModifiers(Multimap<String, AttributeModifier> modifiers) {
		for (Map.Entry<String, Collection<AttributeModifier>> entry : modifiers.asMap().entrySet()) {
			String[] keys = entry.getKey().split("/");
			String group = keys[0];
			String slot = keys[1];
			for (AttributeModifier modifier : entry.getValue()) {
				Map<String, TrinketInventory> groupInv = this.inventory.get(group);
				if (groupInv != null) {
					TrinketInventory inv = groupInv.get(slot);
					if (inv != null) {
						inv.addModifier(modifier);
					}
				}
			}
		}
	}

	@Override
	public void addPersistentModifiers(Multimap<String, AttributeModifier> modifiers) {
		for (Map.Entry<String, Collection<AttributeModifier>> entry : modifiers.asMap().entrySet()) {
			String[] keys = entry.getKey().split("/");
			String group = keys[0];
			String slot = keys[1];
			for (AttributeModifier modifier : entry.getValue()) {
				Map<String, TrinketInventory> groupInv = this.inventory.get(group);
				if (groupInv != null) {
					TrinketInventory inv = groupInv.get(slot);
					if (inv != null) {
						inv.addPersistentModifier(modifier);
					}
				}
			}
		}
	}

	@Override
	public void removeModifiers(Multimap<String, AttributeModifier> modifiers) {
		for (Map.Entry<String, Collection<AttributeModifier>> entry : modifiers.asMap().entrySet()) {
			String[] keys = entry.getKey().split("/");
			String group = keys[0];
			String slot = keys[1];
			for (AttributeModifier modifier : entry.getValue()) {
				Map<String, TrinketInventory> groupInv = this.inventory.get(group);
				if (groupInv != null) {
					TrinketInventory inv = groupInv.get(slot);
					if (inv != null) {
						inv.removeModifier(modifier.getId());
					}
				}
			}
		}
	}

	@Override
	public Multimap<String, AttributeModifier> getModifiers() {
		Multimap<String, AttributeModifier> result = HashMultimap.create();
		for (Map.Entry<String, Map<String, TrinketInventory>> group : this.getInventory().entrySet()) {
			for (Map.Entry<String, TrinketInventory> slotType : group.getValue().entrySet()) {
				result.putAll(group.getKey() + "/" + slotType.getKey(), slotType.getValue().getModifiers().values());
			}
		}

		return result;
	}

	@Override
	public void clearModifiers() {
		for (Map.Entry<String, Map<String, TrinketInventory>> group : this.getInventory().entrySet()) {
			for (Map.Entry<String, TrinketInventory> slotType : group.getValue().entrySet()) {
				slotType.getValue().clearModifiers();
			}
		}
	}

	@Override
	public void readFromNbt(CompoundTag tag) {
		NonNullList<ItemStack> dropped = NonNullList.create();
		for (String groupKey : tag.getAllKeys()) {
			CompoundTag groupTag = tag.getCompound(groupKey);
			if (groupTag != null) {
				Map<String, TrinketInventory> groupSlots = this.inventory.get(groupKey);
				if (groupSlots != null) {
					for (String slotKey : groupTag.getAllKeys()) {
						CompoundTag slotTag = groupTag.getCompound(slotKey);
						ListTag list = slotTag.getList("Items", NbtType.COMPOUND);
						TrinketInventory inv = groupSlots.get(slotKey);

						if (inv != null) {
							inv.fromTag(slotTag.getCompound("Metadata"));
						}

						for (int i = 0; i < list.size(); i++) {
							CompoundTag c = list.getCompound(i);
							ItemStack stack = ItemStack.of(c);
							if (inv != null && i < inv.getContainerSize()) {
								inv.setItem(i, stack);
							} else {
								dropped.add(stack);
							}
						}
					}
				} else {
					for (String slotKey : groupTag.getAllKeys()) {
						CompoundTag slotTag = groupTag.getCompound(slotKey);
						ListTag list = slotTag.getList("Items", NbtType.COMPOUND);
						for (int i = 0; i < list.size(); i++) {
							CompoundTag c = list.getCompound(i);
							dropped.add(ItemStack.of(c));
						}
					}
				}
			}
		}
		for (ItemStack itemStack : dropped) {
			this.entity.spawnAtLocation(itemStack);
		}
		Multimap<String, AttributeModifier> slotMap = HashMultimap.create();
		this.forEach((ref, stack) -> {
			if (!stack.isEmpty()) {
				UUID uuid = SlotAttributes.getUuid(ref);
				Trinket trinket = TrinketsApi.getTrinket(stack.getItem());
				Multimap<Attribute, AttributeModifier> map = trinket.getModifiers(stack, ref, entity, uuid);
				for (Attribute entityAttribute : map.keySet()) {
					if (entityAttribute instanceof SlotAttributes.SlotEntityAttribute slotEntityAttribute) {
						slotMap.putAll(slotEntityAttribute.slot, map.get(entityAttribute));
					}
				}
			}
		});
		for (Map.Entry<String, Map<String, TrinketInventory>> groupEntry : this.getInventory().entrySet()) {
			for (Map.Entry<String, TrinketInventory> slotEntry : groupEntry.getValue().entrySet()) {
				String group = groupEntry.getKey();
				String slot = slotEntry.getKey();
				String key = group + "/" + slot;
				Collection<AttributeModifier> modifiers = slotMap.get(key);
				TrinketInventory inventory = slotEntry.getValue();
				for (AttributeModifier modifier : modifiers) {
					inventory.removeCachedModifier(modifier);
				}
				inventory.clearCachedModifiers();
			}
		}
	}

	@Override
	public void applySyncPacket(FriendlyByteBuf buf) {
		CompoundTag tag = buf.readNbt();

		if (tag != null) {

			for (String groupKey : tag.getAllKeys()) {
				CompoundTag groupTag = tag.getCompound(groupKey);

				if (groupTag != null) {
					Map<String, TrinketInventory> groupSlots = this.inventory.get(groupKey);

					if (groupSlots != null) {

						for (String slotKey : groupTag.getAllKeys()) {
							CompoundTag slotTag = groupTag.getCompound(slotKey);
							ListTag list = slotTag.getList("Items", NbtType.COMPOUND);
							TrinketInventory inv = groupSlots.get(slotKey);

							if (inv != null) {
								inv.applySyncTag(slotTag.getCompound("Metadata"));
							}

							for (int i = 0; i < list.size(); i++) {
								CompoundTag c = list.getCompound(i);
								ItemStack stack = ItemStack.of(c);
								if (inv != null && i < inv.getContainerSize()) {
									inv.setItem(i, stack);
								}
							}
						}
					}
				}
			}

			if (this.entity instanceof Player player) {
				((TrinketPlayerScreenHandler) player.inventoryMenu).trinkets$updateTrinketSlots(false);
			}
		}
	}

	@Override
	public void writeToNbt(CompoundTag tag) {
		for (Map.Entry<String, Map<String, TrinketInventory>> group : this.getInventory().entrySet()) {
			CompoundTag groupTag = new CompoundTag();
			for (Map.Entry<String, TrinketInventory> slot : group.getValue().entrySet()) {
				CompoundTag slotTag = new CompoundTag();
				ListTag list = new ListTag();
				TrinketInventory inv = slot.getValue();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					CompoundTag c = inv.getItem(i).save(new CompoundTag());
					list.add(c);
				}
				slotTag.put("Metadata", this.syncing ? inv.getSyncTag() : inv.toTag());
				slotTag.put("Items", list);
				groupTag.put(slot.getKey(), slotTag);
			}
			tag.put(group.getKey(), groupTag);
		}
	}

	@Override
	public void writeSyncPacket(FriendlyByteBuf buf, ServerPlayer recipient) {
		this.syncing = true;
		CompoundTag tag = new CompoundTag();
		this.writeToNbt(tag);
		this.syncing = false;
		buf.writeNbt(tag);
	}

	@Override
	public boolean isEquipped(Predicate<ItemStack> predicate) {
		for (Map.Entry<String, Map<String, TrinketInventory>> group : this.getInventory().entrySet()) {
			for (Map.Entry<String, TrinketInventory> slotType : group.getValue().entrySet()) {
				TrinketInventory inv = slotType.getValue();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					if (predicate.test(inv.getItem(i))) {
						return true;
					}
				}
			}
		}
		return false;
	}

	@Override
	public List<Tuple<SlotReference, ItemStack>> getEquipped(Predicate<ItemStack> predicate) {
		List<Tuple<SlotReference, ItemStack>> list = new ArrayList<>();
		forEach((slotReference, itemStack) -> {
			if (predicate.test(itemStack)) {
				list.add(new Tuple<>(slotReference, itemStack));
			}
		});
		return list;
	}

	@Override
	public void forEach(BiConsumer<SlotReference, ItemStack> consumer) {
		for (Map.Entry<String, Map<String, TrinketInventory>> group : this.getInventory().entrySet()) {
			for (Map.Entry<String, TrinketInventory> slotType : group.getValue().entrySet()) {
				TrinketInventory inv = slotType.getValue();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					consumer.accept(new SlotReference(inv, i), inv.getItem(i));
				}
			}
		}
	}
}