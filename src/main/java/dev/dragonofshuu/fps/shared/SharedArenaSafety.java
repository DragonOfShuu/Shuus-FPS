package dev.dragonofshuu.fps.shared;

import java.util.ArrayList;
import java.util.List;

import dev.dragonofshuu.fps.ShuuFPS;
import dev.dragonofshuu.fps.arena.ArenaSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

public final class SharedArenaSafety {

    private SharedArenaSafety() {
    }

    public static void setAllPlayersAdventure(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.setGameMode(GameType.ADVENTURE);
        }
    }

    public static void setAllPlayersSurvival(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.setGameMode(GameType.SURVIVAL);
        }
    }

    public static void respawnPlayers(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(serverPlayer -> {
            if (!serverPlayer.isAlive()) {
                // serverPlayer.server.getPlayerList().respawn(serverPlayer, false);
                SharedArenaSafetyNetworking.sendArenaUpdate(serverPlayer,
                        new SharedArenaSafetyNetworking.ArenaUpdate());
            }
        });
    }

    public static boolean areAllPlayersAlive(MinecraftServer server, boolean shouldAttemptRespawn) {
        List<ServerPlayer> deadPlayers = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive()) {
                deadPlayers.add(player);
            }
        }
        if (shouldAttemptRespawn) {
            deadPlayers.forEach(player -> {
                // Attempt to respawn the player
                // server.getPlayerList().respawn(player, false);
                SharedArenaSafetyNetworking.sendArenaUpdate(player,
                        new SharedArenaSafetyNetworking.ArenaUpdate());
                ShuuFPS.LOGGER.info("Attempted to respawn player: " + player.getName().getString());
            });
        }
        return deadPlayers.isEmpty();
    }

    public static void receiveRespawn() {
        Minecraft.getInstance().player.respawn();
    }

    public static void clearPlayerInventories(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.getInventory().clearContent();
            player.containerMenu.broadcastChanges();
            player.setHealth(player.getMaxHealth());
            player.getFoodData().eat(20, 20.0F);
            player.clearFire();
            player.removeAllEffects();
            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
            player.fallDistance = 0.0F;
        }
    }

    public static void clearGroundItems(MinecraftServer server) {
        clearGroundItems(server.overworld(), ArenaSnapshot.getCenterX(), ArenaSnapshot.getCenterZ(),
                ArenaSnapshot.getSize());
    }

    public static void clearGroundItems(ServerLevel level, double centerX, double centerZ, int arenaSize) {
        double half = arenaSize / 2.0 + 16.0;
        AABB area = new AABB(
                centerX - half, level.getMinBuildHeight(), centerZ - half,
                centerX + half, level.getMaxBuildHeight(), centerZ + half);

        List<Entity> leftovers = level.getEntities(
                (Entity) null,
                area,
                entity -> entity instanceof ItemEntity
                        || entity instanceof PrimedTnt
                        || entity instanceof FallingBlockEntity
                        || entity instanceof ExperienceOrb
                        || entity instanceof Projectile);

        leftovers.forEach(Entity::discard);
    }
}
