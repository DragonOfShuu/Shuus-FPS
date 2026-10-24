package dev.dragonofshuu.fps.shared;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.dragonofshuu.fps.ShuuFPS;

/**
 * Handles dropping players into the arena from the sky with a temporary elytra
 * and fall damage immunity until they land.
 */
public final class PlayerDrop {

    /** Absolute Y level players are teleported to. */
    private static final double DROP_HEIGHT = 400.0;
    /**
     * Players spawn within this fraction of the border radius, keeping them away
     * from the edge.
     */
    private static final double SPREAD_FRACTION = 0.8;
    /** Ticks to wait after teleporting before checking for landing. */
    private static final int GRACE_TICKS = 30;

    private static final String DATA_KEY = "BattleRoyaleDrop";
    private static final String ELYTRA_MARKER = "BattleRoyaleDropElytra";

    /** Online players currently dropping, mapped to ticks spent in the drop. */
    private static final Map<UUID, Integer> DROPPING = new HashMap<>();

    private PlayerDrop() {
    }

    public static boolean isDropping(ServerPlayer player) {
        return DROPPING.containsKey(player.getUUID());
    }

    public static void clear() {
        DROPPING.clear();
    }

    public static void drop(ServerLevel level, ServerPlayer player,
            double centerX, double centerZ, double borderSize) {
        if (isDropping(player)) {
            finishDrop(player);
        }

        RandomSource random = level.getRandom();
        double radius = borderSize / 2.0 * SPREAD_FRACTION;
        double x = centerX + (random.nextDouble() * 2.0 - 1.0) * radius;
        double z = centerZ + (random.nextDouble() * 2.0 - 1.0) * radius;

        player.stopRiding();
        player.teleportTo(level, x, DROP_HEIGHT, z, player.getYRot(), player.getXRot());
        player.fallDistance = 0.0F;

        // Remember the chest slot item so it can be returned on landing.
        // Stored in persistent player data so it survives logouts and restarts.
        CompoundTag dropData = new CompoundTag();
        dropData.put("PreviousChest", player.getItemBySlot(EquipmentSlot.CHEST).copy().save(new CompoundTag()));
        player.getPersistentData().put(DATA_KEY, dropData);

        player.setItemSlot(EquipmentSlot.CHEST, createDropElytra());
        DROPPING.put(player.getUUID(), 0);

        player.sendSystemMessage(Component.literal("Press jump while falling to open your elytra!")
                .withStyle(ChatFormatting.AQUA));
    }

    public static void tick(MinecraftServer server) {
        if (DROPPING.isEmpty())
            return;

        for (UUID id : List.copyOf(DROPPING.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null || !player.isAlive())
                continue;

            int ticks = DROPPING.merge(id, 1, Integer::sum);
            if (ticks < GRACE_TICKS)
                continue;

            if (player.onGround() || player.isInWater() || player.isInLava()
                    || player.isPassenger() || player.isSpectator()) {
                ShuuFPS.LOGGER.info("Finishing drop for player {}", player.getName().getString());
                // Now for why the drop is being finished:
                if (player.onGround()) {
                    ShuuFPS.LOGGER.info("Player {} landed on the ground.", player.getName().getString());
                } else if (player.isInWater()) {
                    ShuuFPS.LOGGER.info("Player {} landed in water.", player.getName().getString());
                } else if (player.isInLava()) {
                    ShuuFPS.LOGGER.info("Player {} landed in lava.", player.getName().getString());
                } else if (player.isPassenger()) {
                    ShuuFPS.LOGGER.info("Player {} is a passenger.", player.getName().getString());
                } else if (player.isSpectator()) {
                    ShuuFPS.LOGGER.info("Player {} is a spectator.", player.getName().getString());
                }
                finishDrop(player);
            }
        }
    }

    /**
     * Removes the drop elytra, returns the previous chest item, and ends fall
     * immunity.
     */
    public static void finishDrop(ServerPlayer player) {
        DROPPING.remove(player.getUUID());
        removeDropElytras(player);

        CompoundTag persistent = player.getPersistentData();
        if (persistent.contains(DATA_KEY)) {
            ItemStack previous = readPreviousChest(persistent);
            persistent.remove(DATA_KEY);

            if (!previous.isEmpty()) {
                if (player.getItemBySlot(EquipmentSlot.CHEST).isEmpty()) {
                    player.setItemSlot(EquipmentSlot.CHEST, previous);
                } else if (!player.getInventory().add(previous)) {
                    // player.drop(previous, false);
                    // This is for giving the previous chest item back to the player if their chest
                    // slot is occupied.
                    // We are avoiding clutter though, so dropping is unnecessary.
                }
            }
        }

        player.fallDistance = 0.0F;
    }

    public static void onLogin(ServerPlayer player) {
        if (player.getPersistentData().contains(DATA_KEY)) {
            // Player logged out mid-drop; resume protection
            DROPPING.put(player.getUUID(), 0);
        } else {
            removeDropElytras(player);
        }
    }

    public static void onLogout(ServerPlayer player) {
        // Persistent data keeps the drop state; it resumes on next login
        DROPPING.remove(player.getUUID());
    }

    public static void onRespawn(ServerPlayer player) {
        // if (!isDropping(player)) {
        // removeDropElytras(player); // Covers keepInventory
        // }
    }

    public static void onDeath(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(DATA_KEY))
            return;

        ItemStack previous = readPreviousChest(persistent);
        persistent.remove(DATA_KEY);
        DROPPING.remove(player.getUUID());

        if (!previous.isEmpty()) {
            // player.drop(previous, true, false);
        }
    }

    private static ItemStack readPreviousChest(CompoundTag persistent) {
        return ItemStack.of(persistent.getCompound(DATA_KEY).getCompound("PreviousChest"));
    }

    private static ItemStack createDropElytra() {
        ItemStack elytra = new ItemStack(Items.ELYTRA);
        elytra.enchant(Enchantments.BINDING_CURSE, 1);
        elytra.enchant(Enchantments.VANISHING_CURSE, 1);
        elytra.getOrCreateTag().putBoolean("Unbreakable", true);
        elytra.getOrCreateTag().putBoolean(ELYTRA_MARKER, true);
        elytra.setHoverName(Component.literal("Drop Elytra").withStyle(ChatFormatting.AQUA));
        return elytra;
    }

    private static boolean isDropElytra(ItemStack stack) {
        return stack.is(Items.ELYTRA) && stack.hasTag() && stack.getTag().getBoolean(ELYTRA_MARKER);
    }

    private static void removeDropElytras(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isDropElytra(inventory.getItem(i))) {
                inventory.setItem(i, ItemStack.EMPTY);
            }
        }
        if (isDropElytra(player.containerMenu.getCarried())) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }
}
