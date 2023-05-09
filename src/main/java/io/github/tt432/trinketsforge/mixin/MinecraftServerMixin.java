package io.github.tt432.trinketsforge.mixin;

import dev.emi.trinkets.data.EntitySlotLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/**
 * @author DustW
 */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Shadow public abstract PlayerList getPlayerList();

    @Inject(method = "reloadResources", at = @At("TAIL"))
    private void tf$reloadResources(Collection<String> p_129862_, CallbackInfoReturnable<CompletableFuture<Void>> cir) {
        EntitySlotLoader.SERVER.sync(getPlayerList().getPlayers());
    }
}
