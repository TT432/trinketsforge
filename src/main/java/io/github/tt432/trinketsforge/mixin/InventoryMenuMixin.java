package io.github.tt432.trinketsforge.mixin;

import dev.emi.trinkets.TrinketsClient;
import io.github.tt432.trinketsforge.util.TrinketPlayerMenuImpl;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Adds trinket slots to the player's screen handler
 *
 * @author Emi
 */
@Mixin(InventoryMenu.class)
public abstract class InventoryMenuMixin extends AbstractContainerMenu implements TrinketPlayerMenuImpl {
    @Unique
    Data data = new Data();

    @Override
    public Data t$data() {
        return data;
    }

    @Override
    public InventoryMenu self() {
        return (InventoryMenu) (Object) this;
    }

    private InventoryMenuMixin() {
        super(null, 0);
    }

    @Inject(at = @At("RETURN"), method = "<init>")
    private void init(Inventory playerInv, boolean onServer, Player owner, CallbackInfo info) {
        trinkets$updateTrinketSlots(true);
    }

    @Inject(at = @At("HEAD"), method = "removed")
    private void onClosed(Player player, CallbackInfo info) {
        if (player.level.isClientSide) {
            TrinketsClient.clear();
        }
    }

    @Inject(at = @At("HEAD"), method = "quickMoveStack", cancellable = true)
    private void quickMove(Player player, int index, CallbackInfoReturnable<ItemStack> info) {
        qm(player, index, info);
    }
}
