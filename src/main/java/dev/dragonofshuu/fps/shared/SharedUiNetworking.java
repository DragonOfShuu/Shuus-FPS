package dev.dragonofshuu.fps.shared;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import dev.dragonofshuu.fps.ShuuFPS;

public final class SharedUiNetworking {

    private static final String PROTOCOL_VERSION = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ShuuFPS.MOD_ID, "shared_ui"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int nextMessageId = 0;

    private SharedUiNetworking() {
    }

    public static void register() {
        CHANNEL.registerMessage(nextMessageId++, StatsSnapshotPacket.class,
                StatsSnapshotPacket::encode,
                StatsSnapshotPacket::decode,
                StatsSnapshotPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendStatsSnapshot(ServerPlayer player, MatchStatsSnapshot snapshot) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new StatsSnapshotPacket(snapshot));
    }

    private record StatsSnapshotPacket(MatchStatsSnapshot snapshot) {

        static void encode(StatsSnapshotPacket packet, FriendlyByteBuf buf) {
            buf.writeEnum(packet.snapshot.phase());
            buf.writeUtf(packet.snapshot.modeId());
            buf.writeVarInt(packet.snapshot.timerSeconds());
            buf.writeBoolean(packet.snapshot.showFinalResults());

            boolean hasLocalPlayer = packet.snapshot.localPlayerId() != null;
            buf.writeBoolean(hasLocalPlayer);
            if (hasLocalPlayer) {
                buf.writeUUID(packet.snapshot.localPlayerId());
            }

            List<PlayerMatchStats> players = packet.snapshot.players();
            buf.writeVarInt(players.size());
            for (PlayerMatchStats player : players) {
                buf.writeUUID(player.playerId());
                buf.writeUtf(player.playerName());
                buf.writeUtf(player.teamDisplayName().getString());
                buf.writeBoolean(player.teamColor() != null);
                if (player.teamColor() != null) {
                    buf.writeVarInt(player.teamColor());
                }
                buf.writeVarInt(player.kills());
                buf.writeVarInt(player.deaths());
                buf.writeVarInt(player.livesLeft());
            }
        }

        static StatsSnapshotPacket decode(FriendlyByteBuf buf) {
            MatchPhase phase = buf.readEnum(MatchPhase.class);
            String modeId = buf.readUtf();
            int timerSeconds = buf.readVarInt();
            boolean showFinalResults = buf.readBoolean();

            UUID localPlayerId = null;
            if (buf.readBoolean()) {
                localPlayerId = buf.readUUID();
            }

            int size = buf.readVarInt();
            List<PlayerMatchStats> players = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                UUID playerId = buf.readUUID();
                String playerName = buf.readUtf();
                String teamName = buf.readUtf();
                Integer teamColor = buf.readBoolean() ? buf.readVarInt() : null;
                players.add(new PlayerMatchStats(
                        playerId,
                        playerName,
                        null,
                        teamName,
                        teamColor,
                        buf.readVarInt(),
                        buf.readVarInt(),
                        buf.readVarInt()));
            }

            return new StatsSnapshotPacket(new MatchStatsSnapshot(
                    phase,
                    modeId,
                    timerSeconds,
                    showFinalResults,
                    localPlayerId,
                    List.copyOf(players)));
        }

        static void handle(StatsSnapshotPacket packet,
                Supplier<net.minecraftforge.network.NetworkEvent.Context> contextSupplier) {
            net.minecraftforge.network.NetworkEvent.Context context = contextSupplier.get();
            context.enqueueWork(() -> SharedMatchStats.setClientSnapshot(packet.snapshot));
            context.setPacketHandled(true);
        }
    }
}
