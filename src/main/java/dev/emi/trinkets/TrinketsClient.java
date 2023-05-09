package dev.emi.trinkets;

import dev.emi.trinkets.api.*;
import dev.emi.trinkets.data.EntitySlotLoader;
import io.github.tt432.trinketsforge.net.TrinketsNetHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;

public class TrinketsClient {
    public static SlotGroup activeGroup;
    public static SlotType activeType;
    public static SlotGroup quickMoveGroup;
    public static SlotType quickMoveType;
    public static int quickMoveTimer;

    public static void clear() {
        TrinketsClient.activeGroup = null;
        TrinketsClient.activeType = null;
        TrinketsClient.quickMoveGroup = null;
    }

    @FunctionalInterface
    public interface PacketHandler {
        void receive(Minecraft client, FriendlyByteBuf buf, NetworkEvent.Context responseSender);
    }

    public static void handlerPacket(TrinketsNetHandler.ServerPacket msg, NetworkEvent.Context context) {
        TrinketsClient.handlers.get(msg.id).receive(Minecraft.getInstance(), msg.buf, context);
    }

    public static final Map<ResourceLocation, PacketHandler> handlers = Map.of(
            TrinketsNetwork.SYNC_INVENTORY, (client, buf, responseSender) -> {
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
            },
            TrinketsNetwork.SYNC_SLOTS, (client, buf, responseSender) -> {
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
            },
            TrinketsNetwork.BREAK, (client, buf, sender) -> {
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
            }
    );
}
