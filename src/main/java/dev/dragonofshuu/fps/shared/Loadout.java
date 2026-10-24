package dev.dragonofshuu.fps.shared;

import java.util.HashMap;
import java.util.Optional;

import org.slf4j.Logger;

import dev.dragonofshuu.fps.ShuuFPS;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;
import net.minecraftforge.items.wrapper.RangedWrapper;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Starting gear handed to every participant when the game begins.
 */
public final class Loadout {

    static final Logger LOGGER = ShuuFPS.LOGGER;

    private Loadout() {
    }

    public enum LoadoutClass {
        MACHINE,
        SNIPER,
        BOMBER,
        CLOSE_COMBAT
    }

    public static void giveLoadout(ServerPlayer player, LoadoutClass loadoutClass) {
        giveTeamArmor(player);
        giveBaseLoadout(player);

        switch (loadoutClass) {
            case MACHINE -> giveMachineClassLoadout(player);
            case SNIPER -> giveSniperClassLoadout(player);
            case BOMBER -> giveBomberClassLoadout(player);
            case CLOSE_COMBAT -> giveCloseCombatClassLoadout(player);
        }

        giveTouchUps(player);
    }

    private static void giveTeamArmor(ServerPlayer player) {
        Integer armorColor = TeamColors.getTeamColor(player.getTeam());

        player.setItemSlot(EquipmentSlot.HEAD, createLeatherArmor(Items.LEATHER_HELMET, armorColor));
        player.setItemSlot(EquipmentSlot.LEGS, createLeatherArmor(Items.LEATHER_LEGGINGS, armorColor));
        player.setItemSlot(EquipmentSlot.FEET, createLeatherArmor(Items.LEATHER_BOOTS, armorColor));

        ItemStack chestplate = createLeatherArmor(Items.LEATHER_CHESTPLATE, armorColor);
        ItemStack currentChest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (currentChest.isEmpty()) {
            player.setItemSlot(EquipmentSlot.CHEST, chestplate);
        } else if (!player.getInventory().add(chestplate) && !player.level().isClientSide()) {
            player.drop(chestplate, false);
        }
    }

    private static ItemStack createLeatherArmor(Item item, Integer color) {
        ItemStack stack = new ItemStack(item);
        if (color != null && item instanceof DyeableLeatherItem dyeable) {
            dyeable.setColor(stack, color);
        }
        return stack;
    }

    private static void giveBaseLoadout(ServerPlayer player) {
        HashMap<String, CompoundTag> baseItems = new HashMap<>() {
            {
                put("cgm:assault_rifle", LoadoutNBT
                        .addTacticalStock(LoadoutNBT.addMediumScope(LoadoutNBT.loadedAssaultRifle(Optional.empty()))));
                put("cgm:pistol", LoadoutNBT.loadedPistol(Optional.empty()));
            }
        };
        for (String itemId : baseItems.keySet()) {
            give(player, itemId, 1, false, baseItems.get(itemId));
        }

        String[] baseOutsideHotbar = {
                "cgm:basic_bullet",
                "cgm:advanced_bullet",
        };
        for (String itemId : baseOutsideHotbar) {
            giveOutsideHotbar(player, itemId, 256, false);
        }
        String[] baseAttachments = {
                "cgm:short_scope",
                "cgm:medium_scope",
                "cgm:long_scope",
                "cgm:silencer",
                "cgm:light_grip",
                "cgm:tactical_stock",
                "cgm:weighted_stock"
        };
        for (String itemId : baseAttachments) {
            giveOutsideHotbar(player, itemId, 1, false);
        }
    }

    private static void giveTouchUps(ServerPlayer player) {
        var inventory = player.getInventory();

        var random = player.getRandom();
        Item randomThrowable = ForgeRegistries.ITEMS
                .getValue(ResourceLocation.parse(random.nextInt(3) == 0 ? "cgm:stun_grenade" : "cgm:grenade"));
        inventory.add(5, new ItemStack(randomThrowable, 8));
        inventory.add(6, new ItemStack(Items.DIAMOND_PICKAXE));
        inventory.add(7, new ItemStack(Items.TNT, 128));
        inventory.add(8, new ItemStack(Items.FLINT_AND_STEEL));
    }

    /**
     * They have 4 random classes that they could get: machine, sniper, bomber, and
     * close
     * combat.
     */
    public static void giveMachineClassLoadout(ServerPlayer player) {
        HashMap<String, CompoundTag> machineClassItems = new HashMap<>() {
            {
                put("cgm:mini_gun", LoadoutNBT.loadedMiniGun(Optional.empty()));
            }
        };
        for (String itemId : machineClassItems.keySet()) {
            give(player, itemId, 1, false, machineClassItems.get(itemId));
        }

        String[] machineClassOutsideHotbar = {
                "cgm:basic_bullet"
        };
        for (String itemId : machineClassOutsideHotbar) {
            giveOutsideHotbar(player, itemId, 128, false);
        }
    }

    public static void giveSniperClassLoadout(ServerPlayer player) {
        HashMap<String, CompoundTag> sniperClassItems = new HashMap<>() {
            {
                put("cgm:heavy_rifle", LoadoutNBT.addLongScope(LoadoutNBT.loadedHeavyRifle(Optional.empty())));
            }
        };
        for (String itemId : sniperClassItems.keySet()) {
            give(player, itemId, 1, false, sniperClassItems.get(itemId));
        }
    }

    public static void giveCloseCombatClassLoadout(ServerPlayer player) {
        HashMap<String, CompoundTag> closeCombatClassItems = new HashMap<>() {
            {
                put("cgm:shotgun", LoadoutNBT.loadedShotgun(Optional.empty()));
                put("minecraft:diamond_sword", new CompoundTag());
            }
        };
        for (String itemId : closeCombatClassItems.keySet()) {
            give(player, itemId, 1, false, closeCombatClassItems.get(itemId));
        }

        String[] closeCombatClassOutsideHotbar = {
                "cgm:shell"
        };
        for (String itemId : closeCombatClassOutsideHotbar) {
            giveOutsideHotbar(player, itemId, 64, false);
        }
    }

    public static void giveBomberClassLoadout(ServerPlayer player) {
        HashMap<String, CompoundTag> bomberClassItems = new HashMap<>() {
            {
                put("cgm:bazooka", LoadoutNBT.loadedBazooka(Optional.empty()));
            }
        };
        for (String itemId : bomberClassItems.keySet()) {
            give(player, itemId, 1, false, bomberClassItems.get(itemId));
        }

        String[] bomberClassOutsideHotbar = {
                "cgm:missile"
        };
        for (String itemId : bomberClassOutsideHotbar) {
            giveOutsideHotbar(player, itemId, 20, false);
        }
    }

    /**
     * Gives an item by registry ID (e.g. "minecraft:arrow").
     * Looks the item up at runtime, so the gun mod is not a compile-time
     * dependency.
     */
    private static void give(ServerPlayer player, String itemId, int count, boolean canDrop) {
        give(player, itemId, count, canDrop, Optional.empty());
    }

    private static void give(ServerPlayer player, String itemId, int count, boolean canDrop, CompoundTag tag) {
        give(player, itemId, count, canDrop, Optional.of(tag));
    }

    private static void give(ServerPlayer player, String itemId, int count, boolean canDrop,
            Optional<CompoundTag> tag) {
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);

        if (item == null || item == Items.AIR) {
            LOGGER.warn("Loadout item '{}' does not exist; skipping", itemId);
            return;
        }

        var inventory = player.getInventory();

        ItemStack stack = new ItemStack(item, count);
        if (tag.isPresent()) {
            stack.setTag(tag.get());
        }
        if (!inventory.add(stack) && canDrop && !player.level().isClientSide()) {
            player.drop(stack, false); // Inventory full; drop what's left at their feet
        }
    }

    private static void giveOutsideHotbar(ServerPlayer player, String itemId, int count, boolean canDrop) {
        var inventory = player.getInventory();
        IItemHandler mainInventory = new RangedWrapper(new PlayerMainInvWrapper(inventory), 9, 36);

        ResourceLocation id = ResourceLocation.tryParse(itemId);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) {
            LOGGER.warn("Loadout item '{}' does not exist; skipping", itemId);
            return;
        }
        ItemStack stack = new ItemStack(item, count);
        ItemStack remainder = stack.copy();

        // Attempt to insert the item into the main inventory slots
        for (int i = 0; i < mainInventory.getSlots(); i++) {
            remainder = mainInventory.insertItem(i, remainder, false);
            if (remainder.isEmpty()) {
                break; // Successfully inserted everything
            }
        }

        // If the main inventory is full, drop the remaining items on the ground
        if (!remainder.isEmpty() && canDrop && !player.level().isClientSide()) {
            player.drop(remainder, false);
        }
    }
}
