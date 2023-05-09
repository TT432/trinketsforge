package io.github.tt432.trinketsforge.net;

import dev.emi.trinkets.TrinketsClient;
import io.github.tt432.trinketsforge.Trinketsforge;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * @author DustW
 */
public class TrinketsNetHandler {
    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(Trinketsforge.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id;

    public static void init() {
        INSTANCE.registerMessage(id++, ServerPacket.class, (msg, buf) -> {
            buf.writeResourceLocation(msg.id);
            buf.writeBytes(msg.buf);
        }, buf -> new ServerPacket(
                buf.readResourceLocation(),
                buf
        ), (msg, contentSup) -> {
            NetworkEvent.Context context = contentSup.get();

            TrinketsClient.handlerPacket(msg, context);

            context.setPacketHandled(true);
        });
    }

    public static class ServerPacket {
        public ResourceLocation id;
        public FriendlyByteBuf buf;

        public ServerPacket(ResourceLocation id, FriendlyByteBuf buf) {
            this.id = id;
            this.buf = buf;
        }
    }

    public static void sendToClient(ServerPlayer player, ResourceLocation id, FriendlyByteBuf buf) {
        INSTANCE.sendTo(new ServerPacket(id, buf), player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
