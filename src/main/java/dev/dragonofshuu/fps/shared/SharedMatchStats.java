package dev.dragonofshuu.fps.shared;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Shared match stats cache. Server updates this, client reads from synced
 * snapshot.
 */
public final class SharedMatchStats {

    private static MatchPhase phase = MatchPhase.IDLE;
    private static String modeId = "";
    private static int timerSeconds = -1;

    private static final Map<UUID, Integer> KILLS = new HashMap<>();
    private static final Map<UUID, Integer> DEATHS = new HashMap<>();
    private static final Map<UUID, Integer> LIVES = new HashMap<>();

    private static volatile MatchStatsSnapshot clientSnapshot = MatchStatsSnapshot.empty();

    private SharedMatchStats() {
    }

    public static void startMatch(MinecraftServer server, String mode,
            List<ServerPlayer> participants, int startingLives) {
        phase = MatchPhase.RUNNING;
        modeId = mode == null ? "" : mode;
        timerSeconds = -1;
        KILLS.clear();
        DEATHS.clear();
        LIVES.clear();

        for (ServerPlayer player : participants) {
            UUID id = player.getUUID();
            KILLS.put(id, 0);
            DEATHS.put(id, 0);
            LIVES.put(id, startingLives);
        }

        syncAll(server);
    }

    public static void setPhase(MinecraftServer server, MatchPhase newPhase) {
        phase = newPhase;
        syncAll(server);
    }

    public static void setTimerSeconds(MinecraftServer server, int seconds) {
        timerSeconds = Math.max(-1, seconds);
        syncAll(server);
    }

    // public static void setShowFinalResults(MinecraftServer server, boolean show)
    // {
    // showFinalResults = show;
    // syncAll(server);
    // }
    // public static void setShowFinalResults(MinecraftServer server) {
    // showFinalResults = true;
    // syncAll(server);
    // }

    public static void clear(MinecraftServer server) {
        phase = MatchPhase.IDLE;
        modeId = "";
        timerSeconds = -1;
        KILLS.clear();
        DEATHS.clear();
        LIVES.clear();
        syncAll(server);
    }

    public static void ensurePlayerTracked(ServerPlayer player, int defaultLives) {
        UUID id = player.getUUID();
        KILLS.putIfAbsent(id, 0);
        DEATHS.putIfAbsent(id, 0);
        LIVES.putIfAbsent(id, defaultLives);
    }

    public static void setLives(ServerPlayer player, int livesLeft) {
        if (phase != MatchPhase.RUNNING) {
            return;
        }
        LIVES.put(player.getUUID(), Math.max(0, livesLeft));
    }

    public static void recordDeath(ServerPlayer victim) {
        if (phase != MatchPhase.RUNNING) {
            return;
        }
        UUID victimId = victim.getUUID();
        DEATHS.merge(victimId, 1, Integer::sum);
    }

    public static void recordKillForPlayer(ServerPlayer killer) {
        if (phase != MatchPhase.RUNNING) {
            return;
        }
        KILLS.merge(killer.getUUID(), 1, Integer::sum);
    }

    public static boolean isMatchActive() {
        return phase != MatchPhase.IDLE;
    }

    public static int getKills(UUID playerId) {
        return KILLS.getOrDefault(playerId, 0);
    }

    public static List<PlayerMatchStats> getServerRows(MinecraftServer server) {
        return buildServerSnapshot(server, null).players();
    }

    public static void syncAll(MinecraftServer server) {
        MatchStatsSnapshot snapshot = buildServerSnapshot(server, null);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SharedUiNetworking.sendStatsSnapshot(player, withLocalPlayer(snapshot, player.getUUID()));
        }
    }

    public static void sendFinalResults(MinecraftServer server) {
        MatchStatsSnapshot snapshot = buildServerSnapshot(server, null, true);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            SharedUiNetworking.sendStatsSnapshot(player, withLocalPlayer(snapshot, player.getUUID()));
        }
    }

    public static void syncOne(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        MatchStatsSnapshot snapshot = buildServerSnapshot(server, player.getUUID());
        SharedUiNetworking.sendStatsSnapshot(player, snapshot);
    }

    private static MatchStatsSnapshot buildServerSnapshot(MinecraftServer server, UUID localPlayerId) {
        return buildServerSnapshot(server, localPlayerId, false);
    }

    private static MatchStatsSnapshot buildServerSnapshot(MinecraftServer server, UUID localPlayerId,
            boolean shouldShowFinalResults) {
        List<PlayerMatchStats> rows = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID id = player.getUUID();
            PlayerTeam team = (PlayerTeam) player.getTeam();
            Integer teamColor = TeamColors.getTeamColor(team);
            if (team != null && team.getColor() != null && team.getColor().getColor() != null) {
                teamColor = team.getColor().getColor();
            }
            rows.add(new PlayerMatchStats(
                    id,
                    player.getGameProfile().getName(),
                    team,
                    team == null ? "" : team.getDisplayName().getString(),
                    teamColor,
                    KILLS.getOrDefault(id, 0),
                    DEATHS.getOrDefault(id, 0),
                    LIVES.getOrDefault(id, 0)));
        }

        rows.sort(Comparator
                .comparing(PlayerMatchStats::teamName)
                .thenComparing(Comparator.comparingInt(PlayerMatchStats::kills).reversed())
                .thenComparing(Comparator.comparingInt(PlayerMatchStats::deaths))
                .thenComparing(Comparator.comparingInt(PlayerMatchStats::livesLeft).reversed())
                .thenComparing(PlayerMatchStats::playerName, String.CASE_INSENSITIVE_ORDER));

        return new MatchStatsSnapshot(phase, modeId, timerSeconds, localPlayerId, List.copyOf(rows),
                shouldShowFinalResults);
    }

    private static MatchStatsSnapshot withLocalPlayer(MatchStatsSnapshot source, UUID localPlayerId) {
        return new MatchStatsSnapshot(source.phase(), source.modeId(), source.timerSeconds(),
                localPlayerId, source.players(), source.shouldShowFinalResults());
    }

    public static void receiveClientSnapshot(MatchStatsSnapshot snapshot) {
        setClientSnapshot(snapshot);
        if (snapshot.shouldShowFinalResults()) {
            SharedHudOverlay.handleFinalResultsPopup(snapshot);
        }
    }

    public static void setClientSnapshot(MatchStatsSnapshot snapshot) {
        clientSnapshot = snapshot;
    }

    public static MatchStatsSnapshot getClientSnapshot() {
        return clientSnapshot;
    }

    public static void clearClientSnapshot() {
        clientSnapshot = MatchStatsSnapshot.empty();
    }
}
