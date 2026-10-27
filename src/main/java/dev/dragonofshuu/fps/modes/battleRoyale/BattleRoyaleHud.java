package dev.dragonofshuu.fps.modes.battleRoyale;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;

public final class BattleRoyaleHud {

    private static final int TICKS_PER_SECOND = 20;

    private static final ServerBossEvent BAR = new ServerBossEvent(
            Component.literal("Battle Royale"),
            BossEvent.BossBarColor.YELLOW,
            BossEvent.BossBarOverlay.PROGRESS);

    private BattleRoyaleHud() {
    }

    static void removePlayer(ServerPlayer player) {
        BAR.removePlayer(player);
    }

    static void reset() {
        BAR.removeAllPlayers();
    }

    static void addAllPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            BAR.addPlayer(player);
        }
    }

    static void update(BattleRoyaleGame.Phase phase, int timer, int warTicks, int shrinkTicks,
            double targetSize, double minSize) {
        switch (phase) {
            case WAR -> {
                BAR.setColor(BossEvent.BossBarColor.YELLOW);
                BAR.setName(Component.literal("Border shrinks in " + formatTime(timer)));
                BAR.setProgress(Mth.clamp((float) timer / warTicks, 0.0F, 1.0F));
            }
            case SHRINKING -> {
                BAR.setColor(BossEvent.BossBarColor.RED);
                BAR.setName(Component.literal("Border shrinking to " + (int) targetSize
                        + " blocks: " + formatTime(timer)));
                BAR.setProgress(Mth.clamp((float) timer / shrinkTicks, 0.0F, 1.0F));
            }
            case FINISHED -> {
                BAR.setColor(BossEvent.BossBarColor.PURPLE);
                BAR.setName(Component.literal("Final border: " + (int) minSize + " blocks"));
                BAR.setProgress(1.0F);
            }
            default -> {
            }
        }
    }

    private static String formatTime(int ticks) {
        int seconds = (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
