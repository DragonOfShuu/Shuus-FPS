package dev.dragonofshuu.fps.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

import net.minecraft.server.MinecraftServer;

public class DelayedServerTask {
    private static final List<PendingTask> pendingTasks = new ArrayList<>();

    public static void onServerTick(MinecraftServer server) {
        Iterator<PendingTask> iterator = pendingTasks.iterator();
        while (iterator.hasNext()) {
            PendingTask task = iterator.next();
            task.RemainingTicks--;
            task.waitingFor++;
            if (task.RemainingTicks <= 0 && task.canRun.apply(task.waitingFor)) {
                task.action.run();
                iterator.remove();
            }
        }
    }

    public static void runAfterTicks(int ticks, Runnable action) {
        pendingTasks.add(new PendingTask(ticks, (waiting) -> true, action));
    }

    public static void runAfterTicks(int ticks, Function<Integer, Boolean> canRun, Runnable action) {
        pendingTasks.add(new PendingTask(ticks, canRun, action));
    }

    public static void runAfterTicks(Function<Integer, Boolean> canRun, Runnable action) {
        pendingTasks.add(new PendingTask(0, canRun, action));
    }

    private static class PendingTask {
        int RemainingTicks;
        Function<Integer, Boolean> canRun;
        Runnable action;
        /* Number of ticks this task has been waiting for */
        int waitingFor;

        PendingTask(int remainingTicks, Function<Integer, Boolean> canRun, Runnable action) {
            this.RemainingTicks = remainingTicks;
            this.canRun = canRun;
            this.action = action;
            this.waitingFor = 0;
        }
    }
}
