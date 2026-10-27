package dev.dragonofshuu.fps.modes.battleRoyale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.scores.Team;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import dev.dragonofshuu.fps.arena.ArenaSnapshot;
import dev.dragonofshuu.fps.shared.SharedArenaSafety;
import dev.dragonofshuu.fps.shared.Loadout;
import dev.dragonofshuu.fps.shared.MatchPhase;
import dev.dragonofshuu.fps.shared.ModBroadcasts;
import dev.dragonofshuu.fps.shared.SharedMatchStats;

public final class BattleRoyaleGame {

    private static final double MIN_SIZE = 10.0;
    private static final int TICKS_PER_SECOND = 20;
    private static final long MILLIS_PER_TICK = 50L;

    public enum Phase {
        IDLE, WAR, SHRINKING, FINISHED, ENDING
    }

    private static Phase phase = Phase.IDLE;
    private static int warTicks;
    private static int shrinkTicks;
    private static int shrinkAmount;
    private static int timer;
    private static double targetSize;
    private static int postWinResetTicks;
    private static boolean noWinningConditionAtStart;

    private static final String ELIMINATED_TAG = "br_eliminated";
    private static final Map<UUID, Integer> LIVES_LEFT = new HashMap<>();
    private static final Map<UUID, Loadout.LoadoutClass> PLAYER_LOADOUTS = new HashMap<>();

    private BattleRoyaleGame() {
    }

    /** True while the border cycle is active (war or shrinking). */
    public static boolean isRunning() {
        return phase == Phase.WAR || phase == Phase.SHRINKING;
    }

    /**
     * True whenever a game exists, including after the border has finished
     * shrinking.
     */
    public static boolean isActive() {
        return phase != Phase.IDLE;
    }

    public static void start(MinecraftServer server, double centerX, double centerZ,
            int borderSize, int warTime, int shrinkAmount, int shrinkTime, int lives) {
        ServerLevel level = server.overworld();
        WorldBorder border = level.getWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(borderSize);
        border.setWarningBlocks(5);

        LIVES_LEFT.clear();
        PLAYER_LOADOUTS.clear();

        BattleRoyaleGame.warTicks = warTime * TICKS_PER_SECOND;
        BattleRoyaleGame.shrinkTicks = shrinkTime * TICKS_PER_SECOND;
        BattleRoyaleGame.shrinkAmount = shrinkAmount;
        BattleRoyaleGame.targetSize = borderSize;

        phase = Phase.WAR;
        timer = warTicks;
        postWinResetTicks = 0;
        noWinningConditionAtStart = false;
        SharedArenaSafety.respawnPlayers(server);
        SharedArenaSafety.setAllPlayersSurvival(server);
        BattleRoyaleGameSettings.initiate(server);

        // Drop participants into the arena and hand out starting gear
        BattleRoyalePlayers.prepareParticipants(server, level, centerX, centerZ, borderSize, lives,
                ELIMINATED_TAG, LIVES_LEFT, PLAYER_LOADOUTS);

        SharedMatchStats.startMatch(server,
                "battle_royale",
                server.getPlayerList().getPlayers().stream()
                        .filter(player -> LIVES_LEFT.containsKey(player.getUUID()))
                        .collect(Collectors.toList()),
                lives);

        updateBar();
        addAllPlayers(server);

        broadcast(server, "The battle has begun! Border: " + borderSize
                + " blocks. First shrink in " + warTime + " seconds. Lives: " + lives + ".");

        if (!BattleRoyaleWinConditions.hasWinningConditionPossible(server, LIVES_LEFT)) {
            noWinningConditionAtStart = true;
            BattleRoyaleAnnouncements.showActionBarToAll(server,
                    "No Winning Condition Possible: Only One Player/Team");
        }
    }

    public static void stop(MinecraftServer server) {
        if (!isActive()) {
            return;
        }
        BattleRoyaleGameSettings.end(server);
        BattleRoyalePlayers.restoreEliminatedPlayers(server, ELIMINATED_TAG);
        SharedArenaSafety.clearGroundItems(server);
        SharedArenaSafety.clearPlayerInventories(server);
        SharedArenaSafety.setAllPlayersAdventure(server);
        resetBorder(server);
        SharedMatchStats.clear(server);
        reset();
        broadcast(server, "The game has been stopped.");
    }

    /** Clears game state and hides the boss bar without touching the world. */
    public static void reset() {
        phase = Phase.IDLE;
        BattleRoyaleHud.reset();
        LIVES_LEFT.clear();
        PLAYER_LOADOUTS.clear();
        postWinResetTicks = 0;
        noWinningConditionAtStart = false;
    }

    public static void resetBorder(MinecraftServer server) {
        WorldBorder border = server.overworld().getWorldBorder();
        border.setSize(border.getAbsoluteMaxSize());
        border.setCenter(0, 0);
    }

    public static void onPlayerLeave(ServerPlayer player) {
        BattleRoyaleHud.removePlayer(player);
        BattleRoyalePlayers.onPlayerLeave(player, ELIMINATED_TAG);
    }

    public static void onPlayerDeath(ServerPlayer player) {
        if (!isActive() || phase == Phase.ENDING || player.isSpectator() || player.isCreative()) {
            return;
        }

        UUID id = player.getUUID();
        if (!LIVES_LEFT.containsKey(id)) {
            return;
        }

        int livesLeft = Math.max(0, LIVES_LEFT.get(id) - 1);
        LIVES_LEFT.put(id, livesLeft);
        SharedMatchStats.setLives(player, livesLeft);

        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        SharedMatchStats.syncAll(server);

        if (livesLeft > 0) {
            return;
        }

        BattleRoyaleWinConditions.Outcome outcome = BattleRoyaleWinConditions.evaluate(
                server, LIVES_LEFT, noWinningConditionAtStart);
        handleOutcome(server, outcome);
    }

    private static void handleOutcome(MinecraftServer server, BattleRoyaleWinConditions.Outcome outcome) {
        switch (outcome.type()) {
            case NICE_DEATH -> announceNiceDeathAndReset(server);
            case TEAM_WIN -> announceTeamWin(server, outcome.team(), outcome.contenders());
            case PLAYER_WIN -> announcePlayerWin(server, outcome.winner());
            case NONE -> {
            }
        }
    }

    public static void onPlayerRespawn(ServerPlayer player) {
        if (!isActive() || phase == Phase.ENDING || player.isCreative()) {
            return;
        }

        BattleRoyaleGameSettings.applyRevisedMaxHealth(player);

        UUID id = player.getUUID();
        Integer livesLeft = LIVES_LEFT.get(id);
        if (livesLeft == null) {
            return;
        }

        if (livesLeft > 0) {
            BattleRoyalePlayers.respawnToDrop(player, PLAYER_LOADOUTS);
            player.sendSystemMessage(Component.literal("Lives remaining: " + livesLeft)
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }

        BattleRoyalePlayers.eliminatePlayer(player, ELIMINATED_TAG);
    }

    public static void onLogin(ServerPlayer player) {
        if (!isActive()) {
            return;
        }

        player.sendSystemMessage(
                Component.literal("You must wait for the next game to join!").withStyle(ChatFormatting.RED));
        BattleRoyalePlayers.eliminatePlayer(player, ELIMINATED_TAG);
    }

    public static void tick(MinecraftServer server) {
        if (phase == Phase.IDLE)
            return;

        if (phase == Phase.ENDING) {
            if (--postWinResetTicks <= 0) {
                resetArenaAndGame(server);
            }
            return;
        }

        // Make sure players who joined mid-game can see the bar
        if (server.getTickCount() % TICKS_PER_SECOND == 0) {
            addAllPlayers(server);
        }

        if (phase == Phase.FINISHED)
            return;

        if (--timer <= 0) {
            WorldBorder border = server.overworld().getWorldBorder();
            if (phase == Phase.WAR) {
                beginShrink(server, border);
            } else {
                endShrink(server);
            }
        }

        updateBar();
    }

    private static void beginShrink(MinecraftServer server, WorldBorder border) {
        double current = targetSize;
        targetSize = Math.max(MIN_SIZE, current - shrinkAmount);

        border.lerpSizeBetween(current, targetSize, shrinkTicks * MILLIS_PER_TICK);

        phase = Phase.SHRINKING;
        timer = shrinkTicks;
    }

    private static void endShrink(MinecraftServer server) {
        if (targetSize <= MIN_SIZE) {
            phase = Phase.FINISHED;
            broadcast(server, "The border has reached its final size of " + (int) MIN_SIZE + " blocks!");
            return;
        }

        phase = Phase.WAR;
        timer = warTicks;
    }

    private static void announceNiceDeathAndReset(MinecraftServer server) {
        BattleRoyaleAnnouncements.announceNiceDeath(server);
        phase = Phase.ENDING;
        postWinResetTicks = 5 * TICKS_PER_SECOND;
        SharedMatchStats.setPhase(server, MatchPhase.POST_GAME);
    }

    private static void announceTeamWin(MinecraftServer server, Team team, List<ServerPlayer> contenders) {
        BattleRoyaleAnnouncements.announceTeamWin(server, team, contenders);
        phase = Phase.ENDING;
        postWinResetTicks = 5 * TICKS_PER_SECOND;
        SharedMatchStats.setPhase(server, MatchPhase.POST_GAME);
    }

    private static void announcePlayerWin(MinecraftServer server, ServerPlayer winner) {
        BattleRoyaleAnnouncements.announcePlayerWin(server, winner);
        phase = Phase.ENDING;
        postWinResetTicks = 5 * TICKS_PER_SECOND;
        SharedMatchStats.setPhase(server, MatchPhase.POST_GAME);
    }

    private static void resetArenaAndGame(MinecraftServer server) {
        if (ArenaSnapshot.isBusy()) {
            return;
        }

        SharedArenaSafety.clearGroundItems(server);
        SharedArenaSafety.clearPlayerInventories(server);
        SharedArenaSafety.setAllPlayersAdventure(server);

        if (isActive()) {
            stop(server);
        }

        if (!ArenaSnapshot.hasBackup()) {
            broadcast(server, "No arena backup exists to restore.");
            return;
        }

        ArenaSnapshot.beginRestore(server);
    }

    private static void updateBar() {
        BattleRoyaleHud.update(phase, timer, warTicks, shrinkTicks, targetSize, MIN_SIZE);
    }

    private static void addAllPlayers(MinecraftServer server) {
        BattleRoyaleHud.addAllPlayers(server);
    }

    public static void broadcast(MinecraftServer server, String message) {
        ModBroadcasts.broadcast(server, message, "BR", ChatFormatting.GOLD);
    }
}
