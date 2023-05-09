package dev.emi.trinkets;

import dev.emi.trinkets.api.TrinketInventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * @author DustW
 */
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
}
