package dev.dragonofshuu.fps.shared;

import java.util.Optional;
import java.util.function.Supplier;

import dev.dragonofshuu.fps.ShuuFPS;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class SharedArenaSafetyNetworking {
    private static final String PROTOCOL_VERSION = "1.1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ShuuFPS.MOD_ID, "shared_arena"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int nextMessageId = 0;

    private SharedArenaSafetyNetworking() {
    }

    public static void register() {
        CHANNEL.registerMessage(nextMessageId++, ArenaUpdatePacket.class,
                ArenaUpdatePacket::encode,
                ArenaUpdatePacket::decode,
                ArenaUpdatePacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendArenaUpdate(ServerPlayer player, ArenaUpdate update) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ArenaUpdatePacket(update));
    }

    public record ArenaUpdate() {
        // public void encode(net.minecraft.network.FriendlyByteBuf buf) {
        // // Encode the arena update data into the buffer
        // }

        // public static ArenaUpdate decode(net.minecraft.network.FriendlyByteBuf buf) {
        // // Decode the arena update data from the buffer
        // return new ArenaUpdate();
        // }
    }

    public record ArenaUpdatePacket(ArenaUpdate update) {
        public static void encode(ArenaUpdatePacket packet, FriendlyByteBuf buf) {
            // packet.update.encode(buf);
        }

        public static ArenaUpdatePacket decode(FriendlyByteBuf buf) {
            return new ArenaUpdatePacket(new ArenaUpdate());
        }

        public static void handle(ArenaUpdatePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
            var context = contextSupplier.get();
            context.enqueueWork(() -> {
                // Handle the packet on the client side
                SharedArenaSafety.receiveRespawn(); // Replace null with the actual player instance if available
            });
            context.setPacketHandled(true);
        }
    }
}