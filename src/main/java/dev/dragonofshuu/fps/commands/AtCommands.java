package dev.dragonofshuu.fps.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import dev.dragonofshuu.fps.arena.ArenaSnapshot;
import dev.dragonofshuu.fps.modes.arenaTimed.ArenaTimedGame;
import dev.dragonofshuu.fps.modes.battleRoyale.BattleRoyaleGame;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class AtCommands {

    private static final int MAX_BACKUP_SIZE = 4096;

    private AtCommands() {
    }

    public static void register(net.minecraftforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("at")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("start")
                                .then(Commands.argument("duration", IntegerArgumentType.integer(1))
                                        .executes(AtCommands::startGame)))
                        .then(Commands.literal("stop")
                                .executes(AtCommands::stopGame))
                        .then(Commands.literal("set")
                                .then(Commands.literal("center")
                                        .then(Commands
                                                .argument("size", IntegerArgumentType.integer(11, MAX_BACKUP_SIZE))
                                                .executes(AtCommands::setCenter))))
                        .then(Commands.literal("init")
                                .then(Commands.argument("size", IntegerArgumentType.integer(11, MAX_BACKUP_SIZE))
                                        .executes(AtCommands::initArena))));
    }

    private static boolean requireOverworld(CommandSourceStack source) {
        if (source.getLevel() != source.getServer().overworld()) {
            source.sendFailure(Component.literal("This command must be run in the Overworld."));
            return false;
        }
        return true;
    }

    private static int startGame(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOverworld(source)) {
            return 0;
        }

        if (!ArenaSnapshot.hasArenaConfig()) {
            source.sendFailure(
                    Component.literal("Arena center/size not set. Use /at set center <size> or /at init <size>."));
            return 0;
        }

        if (ArenaTimedGame.isRunning()) {
            source.sendFailure(Component.literal("An Arena Timed game is already running."));
            return 0;
        }
        if (BattleRoyaleGame.isActive()) {
            source.sendFailure(
                    Component.literal("Battle Royale is currently active. Stop it before starting Arena Timed."));
            return 0;
        }
        if (ArenaSnapshot.isBusy()) {
            source.sendFailure(Component.literal("Wait for the current backup or restore to finish."));
            return 0;
        }

        int duration = IntegerArgumentType.getInteger(ctx, "duration");
        ArenaTimedGame.start(
                source.getServer(),
                ArenaSnapshot.getCenterX(), ArenaSnapshot.getCenterZ(),
                ArenaSnapshot.getSize(),
                duration);
        return 1;
    }

    private static int stopGame(CommandContext<CommandSourceStack> ctx) {
        if (!ArenaTimedGame.isActive()) {
            ctx.getSource().sendFailure(Component.literal("No Arena Timed game is running."));
            return 0;
        }
        ArenaTimedGame.stop(ctx.getSource().getServer());
        return 1;
    }

    private static int setCenter(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOverworld(source)) {
            return 0;
        }

        if (ArenaSnapshot.isBusy()) {
            source.sendFailure(Component.literal("A backup or restore is already in progress."));
            return 0;
        }

        int size = IntegerArgumentType.getInteger(ctx, "size");
        var pos = source.getPosition();
        ArenaSnapshot.setArenaConfig(source.getServer(), pos.x, pos.z, size);
        source.sendSuccess(() -> Component.literal("Arena center set to "
                + String.format("%.1f, %.1f", pos.x, pos.z) + " with size " + size + "."), true);
        return 1;
    }

    private static int initArena(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOverworld(source)) {
            return 0;
        }

        if (ArenaTimedGame.isRunning()) {
            source.sendFailure(Component.literal("Stop the current game before initializing the arena."));
            return 0;
        }
        if (ArenaSnapshot.isBusy()) {
            source.sendFailure(Component.literal("A backup or restore is already in progress."));
            return 0;
        }

        int size = IntegerArgumentType.getInteger(ctx, "size");
        var pos = source.getPosition();
        ArenaSnapshot.setArenaConfig(source.getServer(), pos.x, pos.z, size);
        ArenaSnapshot.beginCapture(source.getServer(), pos.x, pos.z, size);
        source.sendSuccess(() -> Component.literal("Arena center set and backup initialization started."), true);
        return 1;
    }

}
