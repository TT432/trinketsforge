package dev.emi.trinkets.api;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.mojang.datafixers.util.Function3;
import dev.emi.trinkets.TrinketsMain;
import dev.emi.trinkets.TrinketsNetwork;
import dev.emi.trinkets.data.EntitySlotLoader;
import dev.onyxstudios.cca.api.v3.component.ComponentKey;
import dev.onyxstudios.cca.api.v3.component.ComponentRegistryV3;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.util.TriState;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class TrinketsApi {
	public static final ComponentKey<TrinketComponent> TRINKET_COMPONENT = ComponentRegistryV3.INSTANCE
			.getOrCreate(new ResourceLocation(TrinketsMain.MOD_ID, "trinkets"), TrinketComponent.class);
	private static final Map<ResourceLocation, Function3<ItemStack, SlotReference, LivingEntity, TriState>> PREDICATES = new HashMap<>();
	
	private static final Map<Item, Trinket> TRINKETS = new HashMap<>();
	private static final Trinket DEFAULT_TRINKET;

	/**
	 * Registers a trinket for the provided item, {@link TrinketItem} will do this
	 * automatically.
	 */
	public static void registerTrinket(Item item, Trinket trinket) {
		TRINKETS.put(item, trinket);
	}

	public static Trinket getTrinket(Item item) {
		return TRINKETS.getOrDefault(item, DEFAULT_TRINKET);
	}

	public static Trinket getDefaultTrinket() {
		return DEFAULT_TRINKET;
	}

	/**
	 * @return The trinket component for this entity, if available
	 */
	public static Optional<TrinketComponent> getTrinketComponent(LivingEntity livingEntity) {
		return TRINKET_COMPONENT.maybeGet(livingEntity);
	}

	/**
	 * Called to sync a trinket breaking event with clients. Should generally be
	 * called in the callback of {@link ItemStack#hurtAndBreak(int, LivingEntity, Consumer)}
	 */
	public static void onTrinketBroken(ItemStack stack, SlotReference ref, LivingEntity entity) {
		if (!entity.level.isClientSide) {
			FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
			buf.writeInt(entity.getId());
			buf.writeUtf(ref.inventory().getSlotType().getGroup() + "/" + ref.inventory().getSlotType().getName());
			buf.writeInt(ref.index());
			if (entity instanceof ServerPlayer player) {
				ServerPlayNetworking.send(player, TrinketsNetwork.BREAK, buf);
			}
			PlayerLookup.tracking(entity).forEach(watcher -> {
				ServerPlayNetworking.send(watcher, TrinketsNetwork.BREAK, buf);
			});
		}
	}

	/**
	 * @deprecated Use world-sensitive alternative {@link TrinketsApi#getPlayerSlots(Level)}
	 * @return A map of slot group names to slot groups available for players
	 */
	@Deprecated
	public static Map<String, SlotGroup> getPlayerSlots() {
		return getEntitySlots(EntityType.PLAYER);
	}

	/**
	 * @return A sided map of slot group names to slot groups available for players
	 */
	public static Map<String, SlotGroup> getPlayerSlots(Level world) {
		return getEntitySlots(world, EntityType.PLAYER);
	}

	/**
	 * @return A sided map of slot group names to slot groups available for players
	 */
	public static Map<String, SlotGroup> getPlayerSlots(Player player) {
		return getEntitySlots(player);
	}

	/**
	 * @deprecated Use world-sensitive alternative {@link TrinketsApi#getEntitySlots(Level, EntityType)}
	 * @return A map of slot group names to slot groups available for the provided
	 * entity type
	 */
	@Deprecated
	public static Map<String, SlotGroup> getEntitySlots(EntityType<?> type) {
		EntitySlotLoader loader = FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT ? EntitySlotLoader.CLIENT : EntitySlotLoader.SERVER;
		return loader.getEntitySlots(type);
	}

	/**
	 * @return A sided map of slot group names to slot groups available for the provided
	 * entity type
	 */
	public static Map<String, SlotGroup> getEntitySlots(Level world, EntityType<?> type) {
		EntitySlotLoader loader = world.isClientSide() ? EntitySlotLoader.CLIENT : EntitySlotLoader.SERVER;
		return loader.getEntitySlots(type);
	}

	/**
	 * @return A sided map of slot group names to slot groups available for the provided
	 * entity
	 */
	public static Map<String, SlotGroup> getEntitySlots(Entity entity) {
		if (entity != null) {
			return getEntitySlots(entity.getLevel(), entity.getType());
		}
		return ImmutableMap.of();
	}

	/**
	 * Registers a predicate to be referenced in slot data
	 */
	public static void registerTrinketPredicate(ResourceLocation id, Function3<ItemStack, SlotReference, LivingEntity, TriState> predicate) {
		PREDICATES.put(id, predicate);
	}

	public static Optional<Function3<ItemStack, SlotReference, LivingEntity, TriState>> getTrinketPredicate(ResourceLocation id) {
		return Optional.ofNullable(PREDICATES.get(id));
	}

	public static boolean evaluatePredicateSet(Set<ResourceLocation> set, ItemStack stack, SlotReference ref, LivingEntity entity) {
		TriState state = TriState.DEFAULT;
		for (ResourceLocation id : set) {
			var function = getTrinketPredicate(id);
			if (function.isPresent()) {
				state = function.get().apply(stack, ref, entity);
			}
			if (state != TriState.DEFAULT) {
				break;
			}
		}
		return state.get();
	}

	static {
		TrinketsApi.registerTrinketPredicate(new ResourceLocation("trinkets", "all"), (stack, ref, entity) -> TriState.TRUE);
		TrinketsApi.registerTrinketPredicate(new ResourceLocation("trinkets", "none"), (stack, ref, entity) -> TriState.FALSE);
		TagKey<Item> trinketsAll = TagKey.create(Registries.ITEM, new ResourceLocation("trinkets", "all"));

		TrinketsApi.registerTrinketPredicate(new ResourceLocation("trinkets", "tag"), (stack, ref, entity) -> {
			SlotType slot = ref.inventory().getSlotType();
			TagKey<Item> tag = TagKey.create(Registries.ITEM, new ResourceLocation("trinkets", slot.getGroup() + "/" + slot.getName()));

			if (stack.is(tag) || stack.is(trinketsAll)) {
				return TriState.TRUE;
			}
			return TriState.DEFAULT;
		});
		TrinketsApi.registerTrinketPredicate(new ResourceLocation("trinkets", "relevant"), (stack, ref, entity) -> {
			var map = TrinketsApi.getTrinket(stack.getItem()).getModifiers(stack, ref, entity, SlotAttributes.getUuid(ref));
			if (!map.isEmpty()) {
				return TriState.TRUE;
			}
			return TriState.DEFAULT;
		});
		DEFAULT_TRINKET = new Trinket() {

		};
	}
}
