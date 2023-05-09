package io.github.tt432.trinketsforge.event;

import dev.emi.trinkets.TrinketPlayerScreenHandler;
import dev.emi.trinkets.TrinketsNetwork;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketsApi;
import dev.emi.trinkets.data.EntitySlotLoader;
import io.github.tt432.trinketsforge.net.TrinketsNetHandler;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;

/**
 * @author DustW
 */
@Mod.EventBusSubscriber
public class EventHandler {
    @SubscribeEvent
    public static void onEvent(PlayerEvent.Clone event) {
        Player player = event.getEntity();
        Player original = event.getOriginal();

        if (player.level.getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            TrinketInventory.copyFrom(original, player);
            ((TrinketPlayerScreenHandler) player.inventoryMenu).trinkets$updateTrinketSlots(false);
        }
    }

    @SubscribeEvent
    public static void onEvent(PlayerEvent.PlayerLoggedInEvent event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();
        EntitySlotLoader.SERVER.sync(player);
        ((TrinketPlayerScreenHandler) player.inventoryMenu).trinkets$updateTrinketSlots(false);
        TrinketsApi.getTrinketComponent(player).ifPresent(trinkets -> {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            buf.writeInt(player.getId());
            CompoundTag tag = new CompoundTag();
            Set<TrinketInventory> inventoriesToSend = trinkets.getTrackingUpdates();

            for (TrinketInventory trinketInventory : inventoriesToSend) {
                tag.put(trinketInventory.getSlotType().getGroup() + "/" + trinketInventory.getSlotType().getName(), trinketInventory.getSyncTag());
            }
            buf.writeNbt(tag);
            buf.writeNbt(new CompoundTag());
            TrinketsNetHandler.sendToClient(player, TrinketsNetwork.SYNC_INVENTORY, buf);
            inventoriesToSend.clear();
        });
    }

    @SubscribeEvent
    public static void onEvent(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            Player player = event.player;

            TrinketsApi.getTrinketComponent(player).ifPresent(trinkets ->
                    trinkets.forEach((slotReference, itemStack) ->
                            TrinketsApi.getTrinket(itemStack.getItem()).tick(itemStack, slotReference, player)));
        }
    }
}
