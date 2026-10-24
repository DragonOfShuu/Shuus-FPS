package dev.dragonofshuu.fps.modes.arenaTimed;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.dragonofshuu.fps.arena.ArenaSafety;
import dev.dragonofshuu.fps.arena.ArenaSnapshot;
import dev.dragonofshuu.fps.shared.Loadout;
import dev.dragonofshuu.fps.shared.MatchPhase;
import dev.dragonofshuu.fps.shared.PlayerMatchStats;
import dev.dragonofshuu.fps.shared.SharedMatchStats;

import java.util.Comparator;

public final class ArenaTimedGame {

    private static final int TICKS_PER_SECOND = 20;
    private static final int POST_TITLE_TICKS = 3 * TICKS_PER_SECOND;
    private static final int POST_RESULTS_TICKS = 6 * TICKS_PER_SECOND;

    private enum Phase {
        IDLE,
        RUNNING,
        RESULTS_DELAY,
        RESULTS_SHOWN,
        ENDING
    }

    private record TimedOutcome(Set<UUID> winners, Set<UUID> draws) {
    }

    private record CompetitorScore(int kills, int deaths, int lives, Set<UUID> members) {
    }

    private static Phase phase = Phase.IDLE;
    private static int timerTicks;
    private static int phaseTicks;

    private static final Map<UUID, Loadout.LoadoutClass> PLAYER_LOADOUTS = new HashMap<>();

    private ArenaTimedGame() {
    }

    public static boolean isRunning() {
        return phase == Phase.RUNNING;
    }

    public static boolean isActive() {
        return phase != Phase.IDLE;
    }

    public static void start(MinecraftServer server, double centerX, double centerZ,
            int borderSize, int durationSeconds) {
        ServerLevel level = server.overworld();
        WorldBorder border = level.getWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(borderSize);
        border.setWarningBlocks(5);

        PLAYER_LOADOUTS.clear();

        timerTicks = durationSeconds * TICKS_PER_SECOND;
        phaseTicks = 0;
        phase = Phase.RUNNING;
        ArenaSafety.respawnPlayers(server);
        ArenaSafety.setAllPlayersSurvival(server);
        ArenaTimedGameSettings.initiate(server);

        ArenaTimedPlayers.prepareParticipants(server, level, centerX, centerZ, borderSize, PLAYER_LOADOUTS);

        SharedMatchStats.startMatch(server, "arena_timed", eligiblePlayers(server), -1);
        SharedMatchStats.setTimerSeconds(server, durationSeconds);

        ArenaTimedAnnouncements.broadcast(server,
                "Arena Timed started. Border: " + borderSize + " blocks. Duration: " + durationSeconds + "s.");
    }

    public static void stop(MinecraftServer server) {
        ArenaTimedGameSettings.end(server);
        ArenaSafety.clearGroundItems(server);
        ArenaSafety.clearPlayerInventories(server);
        ArenaSafety.setAllPlayersAdventure(server);
        resetBorder(server);
        SharedMatchStats.clear(server);
        reset();
        ArenaTimedAnnouncements.broadcast(server, "Arena Timed stopped.");
    }

    public static void reset() {
        phase = Phase.IDLE;
        timerTicks = 0;
        phaseTicks = 0;
        PLAYER_LOADOUTS.clear();
    }

    public static void resetBorder(MinecraftServer server) {
        WorldBorder border = server.overworld().getWorldBorder();
        border.setSize(border.getAbsoluteMaxSize());
        border.setCenter(0, 0);
    }

    public static void onPlayerDeath(ServerPlayer player) {
        if (!isActive()) {
            return;
        }

        MinecraftServer server = player.getServer();
        if (server != null) {
            SharedMatchStats.syncAll(server);
        }
    }

    public static void onPlayerRespawn(ServerPlayer player) {
        if (!isActive() || phase == Phase.ENDING) {
            return;
        }

        ArenaTimedGameSettings.applyRevisedMaxHealth(player);
        ArenaTimedPlayers.respawnToDrop(player, PLAYER_LOADOUTS);
    }

    public static void tick(MinecraftServer server) {
        if (phase == Phase.IDLE) {
            return;
        }

        if (phase == Phase.RUNNING) {
            if (--timerTicks <= 0) {
                finishByTimer(server);
                return;
            }

            if (server.getTickCount() % TICKS_PER_SECOND == 0) {
                SharedMatchStats.setTimerSeconds(server, timerTicks / TICKS_PER_SECOND);
            }
            return;
        }

        if (phase == Phase.RESULTS_DELAY) {
            if (++phaseTicks >= POST_TITLE_TICKS) {
                phase = Phase.RESULTS_SHOWN;
                phaseTicks = 0;
                SharedMatchStats.setShowFinalResults(server, true);
            }
            return;
        }

        if (phase == Phase.RESULTS_SHOWN) {
            if (++phaseTicks >= POST_RESULTS_TICKS) {
                phase = Phase.ENDING;
                resetArenaAndGame(server);
            }
        }
    }

    private static void finishByTimer(MinecraftServer server) {
        TimedOutcome outcome = determineOutcome(server);
        ArenaTimedAnnouncements.announceWinnersLosersAndDraws(server, outcome.winners(), outcome.draws());

        phase = Phase.RESULTS_DELAY;
        phaseTicks = 0;

        SharedMatchStats.setTimerSeconds(server, 0);
        SharedMatchStats.setPhase(server, MatchPhase.POST_GAME);
        SharedMatchStats.setShowFinalResults(server, false);
    }

    private static TimedOutcome determineOutcome(MinecraftServer server) {
        List<PlayerMatchStats> rows = SharedMatchStats.getServerRows(server);
        Map<String, CompetitorScore> competitors = new HashMap<>();

        for (PlayerMatchStats row : rows) {
            if (row.teamName().isBlank()) {
                Set<UUID> members = new HashSet<>();
                members.add(row.playerId());
                competitors.put("solo:" + row.playerId(),
                        new CompetitorScore(row.kills(), row.deaths(), row.livesLeft(), members));
                continue;
            }

            String key = "team:" + row.teamName();
            CompetitorScore prior = competitors.get(key);
            Set<UUID> members = prior == null ? new HashSet<>() : new HashSet<>(prior.members());
            members.add(row.playerId());

            int kills = row.kills() + (prior == null ? 0 : prior.kills());
            int deaths = row.deaths() + (prior == null ? 0 : prior.deaths());
            int lives = row.livesLeft() + (prior == null ? 0 : prior.lives());
            competitors.put(key, new CompetitorScore(kills, deaths, lives, members));
        }

        if (competitors.isEmpty()) {
            return new TimedOutcome(Set.of(), Set.of());
        }

        Comparator<CompetitorScore> rankOrder = Comparator
                .comparingInt(CompetitorScore::kills).reversed()
                .thenComparingInt(CompetitorScore::deaths)
                .thenComparing(Comparator.comparingInt(CompetitorScore::lives).reversed());

        CompetitorScore best = competitors.values().stream()
                .max(rankOrder)
                .orElse(null);
        if (best == null) {
            return new TimedOutcome(Set.of(), Set.of());
        }

        Set<UUID> topMembers = new HashSet<>();
        int topCount = 0;
        for (CompetitorScore score : competitors.values()) {
            if (rankOrder.compare(score, best) == 0) {
                topCount++;
                topMembers.addAll(score.members());
            }
        }

        if (topCount > 1) {
            return new TimedOutcome(Set.of(), topMembers);
        }

        return new TimedOutcome(topMembers, Set.of());
    }

    private static List<ServerPlayer> eligiblePlayers(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> !player.isSpectator() && !player.isCreative())
                .toList();
    }

    private static void resetArenaAndGame(MinecraftServer server) {
        if (ArenaSnapshot.isBusy()) {
            return;
        }

        ArenaSafety.clearGroundItems(server);
        ArenaSafety.clearPlayerInventories(server);
        ArenaSafety.setAllPlayersAdventure(server);

        if (isActive()) {
            stop(server);
        }

        if (!ArenaSnapshot.hasBackup()) {
            ArenaTimedAnnouncements.broadcast(server, "No arena backup exists to restore.");
            return;
        }

        ArenaSnapshot.beginRestore(server);
    }
}
