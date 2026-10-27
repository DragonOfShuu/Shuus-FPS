package dev.dragonofshuu.fps.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import dev.dragonofshuu.fps.arena.ArenaSnapshot;
import dev.dragonofshuu.fps.modes.arenaTimed.ArenaTimedGame;
import dev.dragonofshuu.fps.modes.battleRoyale.BattleRoyaleGame;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class BrCommands {

    private static final int MAX_BACKUP_SIZE = 4096;

    private BrCommands() {
    }

    public static void register(net.minecraftforge.event.RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("br")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("start")
                                .executes(BrCommands::startGame))
                        .then(Commands.literal("stop")
                                .executes(BrCommands::stopGame))
                        .then(Commands.literal("settings")
                                .then(Commands.literal("warTime")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                                .executes(BrCommands::setWarTime)))
                                .then(Commands.literal("shrinkAmount")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                                .executes(BrCommands::setShrinkAmount)))
                                .then(Commands.literal("shrinkTime")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                                .executes(BrCommands::setShrinkTime)))
                                .then(Commands.literal("lives")
                                        .then(Commands.argument("value", IntegerArgumentType.integer(0))
                                                .executes(BrCommands::setLives))))
                        .then(Commands.literal("set")
                                .then(Commands.literal("center")
                                        .then(Commands
                                                .argument("size", IntegerArgumentType.integer(11, MAX_BACKUP_SIZE))
                                                .executes(BrCommands::setCenter))))
                        .then(Commands.literal("init")
                                .then(Commands.argument("size", IntegerArgumentType.integer(11, MAX_BACKUP_SIZE))
                                        .executes(BrCommands::initArena)
                                        .then(Commands.argument("warTime", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("shrinkAmount", IntegerArgumentType.integer(1))
                                                        .then(Commands
                                                                .argument("shrinkTime", IntegerArgumentType.integer(1))
                                                                .executes(BrCommands::initArenaWithSettings)
                                                                .then(Commands
                                                                        .argument("lives",
                                                                                IntegerArgumentType.integer(0))
                                                                        .executes(
                                                                                BrCommands::initArenaWithSettings))))))));
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
        if (!requireOverworld(source))
            return 0;

        if (!ArenaSnapshot.hasArenaConfig()) {
            source.sendFailure(
                    Component.literal("Arena center/size not set. Use /br set center <size> or /br init <size>."));
            return 0;
        }
        if (!ArenaSnapshot.hasGameSettings()) {
            source.sendFailure(Component.literal(
                    "Game settings not set. Use /br settings <warTime|shrinkAmount|shrinkTime|lives> <value> or /br init <size> <warTime> <shrinkAmount> <shrinkTime> [lives]."));
            return 0;
        }

        if (BattleRoyaleGame.isRunning()) {
            source.sendFailure(Component.literal("A game is already running."));
            return 0;
        }
        if (ArenaTimedGame.isActive()) {
            source.sendFailure(
                    Component.literal("Arena Timed is currently active. Stop it before starting Battle Royale."));
            return 0;
        }
        if (ArenaSnapshot.isBusy()) {
            source.sendFailure(Component.literal("Wait for the current backup or restore to finish."));
            return 0;
        }

        BattleRoyaleGame.start(
                source.getServer(),
                ArenaSnapshot.getCenterX(), ArenaSnapshot.getCenterZ(),
                ArenaSnapshot.getSize(),
                ArenaSnapshot.getWarTime(),
                ArenaSnapshot.getShrinkAmount(),
                ArenaSnapshot.getShrinkTime(),
                ArenaSnapshot.getLives());
        return 1;
    }

    private static int getLivesArg(CommandContext<CommandSourceStack> ctx) {
        try {
            return IntegerArgumentType.getInteger(ctx, "lives");
        } catch (IllegalArgumentException ignored) {
            return 5;
        }
    }

    private static int setWarTime(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ArenaSnapshot.setWarTime(source.getServer(), IntegerArgumentType.getInteger(ctx, "value"));
        source.sendSuccess(() -> Component.literal("War time set."), true);
        return 1;
    }

    private static int setShrinkAmount(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ArenaSnapshot.setShrinkAmount(source.getServer(), IntegerArgumentType.getInteger(ctx, "value"));
        source.sendSuccess(() -> Component.literal("Shrink amount set."), true);
        return 1;
    }

    private static int setShrinkTime(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ArenaSnapshot.setShrinkTime(source.getServer(), IntegerArgumentType.getInteger(ctx, "value"));
        source.sendSuccess(() -> Component.literal("Shrink time set."), true);
        return 1;
    }

    private static int setLives(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ArenaSnapshot.setLives(source.getServer(), IntegerArgumentType.getInteger(ctx, "value"));
        source.sendSuccess(() -> Component.literal("Lives set."), true);
        return 1;
    }

    private static int stopGame(CommandContext<CommandSourceStack> ctx) {
        if (!BattleRoyaleGame.isActive()) {
            ctx.getSource().sendFailure(Component.literal("No game is running."));
            return 0;
        }
        BattleRoyaleGame.stop(ctx.getSource().getServer());
        return 1;
    }

    private static int setCenter(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOverworld(source))
            return 0;

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
        if (!requireOverworld(source))
            return 0;

        if (BattleRoyaleGame.isRunning()) {
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

    private static int initArenaWithSettings(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!requireOverworld(source))
            return 0;

        if (BattleRoyaleGame.isRunning()) {
            source.sendFailure(Component.literal("Stop the current game before initializing the arena."));
            return 0;
        }
        if (ArenaSnapshot.isBusy()) {
            source.sendFailure(Component.literal("A backup or restore is already in progress."));
            return 0;
        }

        int size = IntegerArgumentType.getInteger(ctx, "size");
        int warTime = IntegerArgumentType.getInteger(ctx, "warTime");
        int shrinkAmount = IntegerArgumentType.getInteger(ctx, "shrinkAmount");
        int shrinkTime = IntegerArgumentType.getInteger(ctx, "shrinkTime");
        int lives = getLivesArg(ctx);

        var pos = source.getPosition();
        ArenaSnapshot.setArenaConfig(source.getServer(), pos.x, pos.z, size);
        ArenaSnapshot.setGameSettings(source.getServer(), warTime, shrinkAmount, shrinkTime, lives);
        ArenaSnapshot.beginCapture(source.getServer(), pos.x, pos.z, size);

        source.sendSuccess(() -> Component.literal("Arena center, settings, and backup initialization started."), true);
        return 1;
    }

}
