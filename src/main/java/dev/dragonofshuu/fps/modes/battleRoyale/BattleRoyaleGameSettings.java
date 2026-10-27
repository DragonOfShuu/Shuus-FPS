package dev.dragonofshuu.fps.modes.battleRoyale;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BattleRoyaleGameSettings {

    private static final double EXTRA_HEARTS = 20.0D;

    private static final Map<UUID, Double> ORIGINAL_MAX_HEALTH = new HashMap<>();
    private static final double DEFAULT_MAX_HEALTH = 20.0D;
    private static boolean priorNaturalRegeneration;
    private static boolean priorKeepInventory;
    private static boolean priorDoMobSpawning;
    private static boolean priorImmediateRespawn;
    private static Difficulty priorDifficulty = Difficulty.NORMAL;

    private BattleRoyaleGameSettings() {
    }

    public static void initiate(MinecraftServer server) {
        GameRules rules = server.getGameRules();
        priorNaturalRegeneration = rules.getRule(GameRules.RULE_NATURAL_REGENERATION).get();
        priorKeepInventory = rules.getRule(GameRules.RULE_KEEPINVENTORY).get();
        priorDoMobSpawning = rules.getRule(GameRules.RULE_DOMOBSPAWNING).get();
        priorImmediateRespawn = rules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).get();
        priorDifficulty = server.getWorldData().getDifficulty();

        rules.getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, server);
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(true, server);
        server.getWorldData().setDifficulty(Difficulty.NORMAL);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ORIGINAL_MAX_HEALTH.put(player.getUUID(), player.getAttribute(Attributes.MAX_HEALTH).getBaseValue());
            applyRevisedMaxHealth(player);
        }
    }

    public static void applyRevisedMaxHealth(ServerPlayer player) {
        Double originalMaxHealth = ORIGINAL_MAX_HEALTH.get(player.getUUID());
        if (originalMaxHealth == null) {
            originalMaxHealth = DEFAULT_MAX_HEALTH;
            ORIGINAL_MAX_HEALTH.put(player.getUUID(), originalMaxHealth);
        }

        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20d + EXTRA_HEARTS);
        player.setHealth(player.getMaxHealth());
    }

    public static void end(MinecraftServer server) {
        GameRules rules = server.getGameRules();
        rules.getRule(GameRules.RULE_NATURAL_REGENERATION).set(priorNaturalRegeneration, server);
        rules.getRule(GameRules.RULE_KEEPINVENTORY).set(priorKeepInventory, server);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(priorDoMobSpawning, server);
        rules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(priorImmediateRespawn, server);
        server.getWorldData().setDifficulty(priorDifficulty);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            endForPlayer(player);
        }
    }

    public static void endForPlayer(ServerPlayer player) {
        Double originalMaxHealth = ORIGINAL_MAX_HEALTH.remove(player.getUUID());
        if (originalMaxHealth != null) {
            player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(originalMaxHealth);
        }
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
    }
}
