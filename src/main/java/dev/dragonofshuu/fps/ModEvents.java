package dev.dragonofshuu.fps;

import dev.dragonofshuu.fps.arena.ArenaSnapshot;
import dev.dragonofshuu.fps.commands.AtCommands;
import dev.dragonofshuu.fps.commands.BrCommands;
import dev.dragonofshuu.fps.commands.ShuufpsCommands;
import dev.dragonofshuu.fps.modes.arenaTimed.ArenaTimedGame;
import dev.dragonofshuu.fps.modes.battleRoyale.BattleRoyaleGame;
import dev.dragonofshuu.fps.shared.PlayerDrop;
import dev.dragonofshuu.fps.shared.RechargeSystem;
import dev.dragonofshuu.fps.shared.SharedMatchStats;
import net.minecraft.server.MinecraftServer;
import dev.dragonofshuu.fps.utils.DelayedServerTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ShuuFPS.MOD_ID)
public final class ModEvents {

    private ModEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        BrCommands.register(event);
        AtCommands.register(event);
        ShuufpsCommands.register(event);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MinecraftServer server = event.getServer();
            ArenaSnapshot.tick(server);
            BattleRoyaleGame.tick(server);
            ArenaTimedGame.tick(server);
            PlayerDrop.tick(server);
            RechargeSystem.tick(server);
            DelayedServerTask.onServerTick(server);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ArenaSnapshot.loadFromDisk(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Don't let the server shut down halfway through writing a backup
        ArenaSnapshot.awaitPendingIo();
        SharedMatchStats.clear(event.getServer());
        BattleRoyaleGame.stop(event.getServer());
        ArenaTimedGame.stop(event.getServer());
        ArenaSnapshot.clear();
        PlayerDrop.clear();
        SharedMatchStats.clearClientSnapshot();
        RechargeSystem.clear();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        // Static state survives between singleplayer worlds in the same session, so
        // clear it.
        // SharedMatchStats.clear(event.getServer());
        // BattleRoyaleGame.stop(event.getServer());
        // ArenaTimedGame.stop(event.getServer());
        // ArenaSnapshot.clear();
        // PlayerDrop.clear();
        // SharedMatchStats.clearClientSnapshot();
        // RechargeSystem.clear();
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PlayerDrop.onLogin(player);
            SharedMatchStats.syncOne(player);
            ArenaTimedGame.onLogin(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BattleRoyaleGame.onPlayerLeave(player);
            ArenaTimedGame.onPlayerLeave(player);
            PlayerDrop.onLogout(player);
            RechargeSystem.clearPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            RechargeSystem.onRespawn(player);
            BattleRoyaleGame.onPlayerRespawn(player);
            ArenaTimedGame.onPlayerRespawn(player);
            PlayerDrop.onRespawn(player);
        }
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && PlayerDrop.isDropping(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && PlayerDrop.isDropping(player)
                && (event.getSource().is(DamageTypes.FALL) || event.getSource().is(DamageTypes.FLY_INTO_WALL))) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!SharedMatchStats.isMatchActive()) {
            return;
        }

        RechargeSystem.onDamage(player);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (SharedMatchStats.isMatchActive()) {
                SharedMatchStats.recordDeath(player);

                Entity sourceEntity = event.getSource().getEntity();
                if (sourceEntity instanceof ServerPlayer killer && !killer.getUUID().equals(player.getUUID())) {
                    SharedMatchStats.recordKillForPlayer(killer);
                }
            }

            BattleRoyaleGame.onPlayerDeath(player);
            ArenaTimedGame.onPlayerDeath(player);
            PlayerDrop.onDeath(player);

            MinecraftServer server = player.getServer();
            if (server != null && SharedMatchStats.isMatchActive()) {
                SharedMatchStats.syncAll(server);
            }
        }
    }
}
