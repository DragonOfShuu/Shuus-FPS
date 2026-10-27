package dev.dragonofshuu.fps.modes.battleRoyale;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.dragonofshuu.fps.shared.Loadout;
import dev.dragonofshuu.fps.shared.PlayerDrop;

final class BattleRoyalePlayers {

    private BattleRoyalePlayers() {
    }

    static void prepareParticipants(MinecraftServer server, ServerLevel level,
            double centerX, double centerZ, int borderSize, int lives,
            String eliminatedTag,
            Map<UUID, Integer> livesLeft,
            Map<UUID, Loadout.LoadoutClass> playerLoadouts) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isSpectator() || player.isCreative()) {
                continue;
            }

            player.removeTag(eliminatedTag);
            livesLeft.put(player.getUUID(), lives);

            Loadout.LoadoutClass loadoutClass = randomLoadoutClass();
            playerLoadouts.put(player.getUUID(), loadoutClass);

            clearForFreshLoadout(player);
            Loadout.giveLoadout(player, loadoutClass);
            PlayerDrop.drop(level, player, centerX, centerZ, borderSize);
        }
    }

    public static void onPlayerLeave(ServerPlayer player, String eliminatedTag) {
        clearForFreshLoadout(player);
        BattleRoyaleGameSettings.endForPlayer(player);
        restoreEliminatedPlayer(player, eliminatedTag);
    }

    static void respawnToDrop(ServerPlayer player, Map<UUID, Loadout.LoadoutClass> playerLoadouts) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        ServerLevel level = server.overworld();
        WorldBorder border = level.getWorldBorder();
        clearForFreshLoadout(player);

        Loadout.LoadoutClass loadoutClass = playerLoadouts.computeIfAbsent(
                player.getUUID(), key -> randomLoadoutClass());
        Loadout.giveLoadout(player, loadoutClass);
        PlayerDrop.drop(level, player, border.getCenterX(), border.getCenterZ(), border.getSize());
    }

    static void clearForFreshLoadout(ServerPlayer player) {
        player.getInventory().clearContent();
        player.containerMenu.broadcastChanges();
        player.setHealth(player.getMaxHealth());
        player.getFoodData().eat(20, 20.0F);
        player.clearFire();
        player.removeAllEffects();
        player.setDeltaMovement(0.0D, 0.0D, 0.0D);
        player.fallDistance = 0.0F;
    }

    static void eliminatePlayer(ServerPlayer player, String eliminatedTag) {
        player.addTag(eliminatedTag);
        player.setGameMode(GameType.SPECTATOR);
        player.sendSystemMessage(Component.literal("You are out of lives and now spectating.")
                .withStyle(ChatFormatting.RED));
    }

    static void restoreEliminatedPlayers(MinecraftServer server, String eliminatedTag) {
        // ServerLevel level = server.overworld();
        // WorldBorder border = level.getWorldBorder();
        // double centerX = border.getCenterX();
        // double centerZ = border.getCenterZ();
        // int safeY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
        // Mth.floor(centerX), Mth.floor(centerZ)) + 1;

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // if (!player.getTags().contains(eliminatedTag)) {
            // continue;
            // }

            // player.removeTag(eliminatedTag);
            // player.setGameMode(GameType.SURVIVAL);
            // player.teleportTo(level, centerX, safeY, centerZ, player.getYRot(),
            // player.getXRot());
            restoreEliminatedPlayer(player, eliminatedTag);
        }
    }

    static void restoreEliminatedPlayer(ServerPlayer player, String eliminatedTag) {
        if (!player.getTags().contains(eliminatedTag)) {
            return;
        }
        player.removeTag(eliminatedTag);
        player.setGameMode(GameType.SURVIVAL);

        ServerLevel level = player.serverLevel();

        int safeY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(player.getX()), Mth.floor(player.getZ())) + 1;
        player.teleportTo(level, player.getX(), safeY, player.getZ(), player.getYRot(), player.getXRot());
    }

    static List<ServerPlayer> getContenders(MinecraftServer server, Map<UUID, Integer> livesLeft) {
        List<ServerPlayer> contenders = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Integer playerLives = livesLeft.get(player.getUUID());
            if (playerLives == null || playerLives <= 0) {
                continue;
            }
            if (!player.isAlive()) {
                continue;
            }
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            contenders.add(player);
        }
        return contenders;
    }

    private static Loadout.LoadoutClass randomLoadoutClass() {
        Loadout.LoadoutClass[] classes = Loadout.LoadoutClass.values();
        return classes[(int) (Math.random() * classes.length)];
    }

}
