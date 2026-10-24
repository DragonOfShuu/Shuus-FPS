package dev.dragonofshuu.fps.arena;

import com.mojang.serialization.Codec;
import org.slf4j.Logger;

import dev.dragonofshuu.fps.ShuuFPS;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Captures the blocks of an arena area, saves them to disk, and restores them
 * later. Capture and restore are spread across ticks; disk I/O runs on a
 * background thread.
 */
public final class ArenaSnapshot {

    static final Logger LOGGER = ShuuFPS.LOGGER;

    enum State {
        EMPTY, CAPTURING, SAVING, LOADING, READY, RESTORING
    }

    static final int CAPTURE_CHUNKS_PER_TICK = 4;
    static final int RESTORE_CHUNKS_PER_TICK = 2;

    // Update clients, skip neighbor shape updates (prevents cascading physics),
    // never drop items.
    static final int RESTORE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE
            | Block.UPDATE_SUPPRESS_DROPS;

    static final String BACKUP_FOLDER = "battleroyale_backup";
    static final String META_FILE = "meta.dat";
    static final String CONFIG_FILE = "arena_config.dat";

    // Same codec vanilla uses to store chunk sections in region files.
    static final Codec<PalettedContainer<BlockState>> SECTION_CODEC = PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY, BlockState.CODEC,
            PalettedContainer.Strategy.SECTION_STATES, Blocks.AIR.defaultBlockState());

    static record ChunkData(PalettedContainer<BlockState>[] sections,
            Map<BlockPos, CompoundTag> blockEntities) {
    }

    static record Backup(double centerX, double centerZ, int size, Map<Long, ChunkData> chunks) {
    }

    static final Map<Long, ChunkData> SAVED = new HashMap<>();
    static final ArrayDeque<ChunkPos> QUEUE = new ArrayDeque<>();

    static State state = State.EMPTY;
    static int totalChunks;
    static int lastReportedStep;
    static double centerX;
    static double centerZ;
    static int size;
    static boolean arenaConfigured;
    static int warTime;
    static int shrinkAmount;
    static int shrinkTime;
    static int lives;
    static boolean gameSettingsConfigured;
    static CompletableFuture<?> pendingIo = CompletableFuture.completedFuture(null);

    private ArenaSnapshot() {
    }

    public static boolean isBusy() {
        return state == State.CAPTURING || state == State.SAVING
                || state == State.LOADING || state == State.RESTORING;
    }

    public static boolean hasBackup() {
        return state == State.READY;
    }

    public static boolean hasArenaConfig() {
        return arenaConfigured;
    }

    public static boolean hasGameSettings() {
        return gameSettingsConfigured;
    }

    public static double getCenterX() {
        return centerX;
    }

    public static double getCenterZ() {
        return centerZ;
    }

    public static int getSize() {
        return size;
    }

    public static int getWarTime() {
        return warTime;
    }

    public static int getShrinkAmount() {
        return shrinkAmount;
    }

    public static int getShrinkTime() {
        return shrinkTime;
    }

    public static int getLives() {
        return lives;
    }

    public static void setArenaConfig(MinecraftServer server, double cx, double cz, int arenaSize) {
        centerX = cx;
        centerZ = cz;
        size = arenaSize;
        arenaConfigured = true;
        ArenaSnapshotPersistence.saveConfigAsync(server);
    }

    public static void setWarTime(MinecraftServer server, int value) {
        warTime = value;
        gameSettingsConfigured = true;
        ArenaSnapshotPersistence.saveConfigAsync(server);
    }

    public static void setShrinkAmount(MinecraftServer server, int value) {
        shrinkAmount = value;
        gameSettingsConfigured = true;
        ArenaSnapshotPersistence.saveConfigAsync(server);
    }

    public static void setShrinkTime(MinecraftServer server, int value) {
        shrinkTime = value;
        gameSettingsConfigured = true;
        ArenaSnapshotPersistence.saveConfigAsync(server);
    }

    public static void setLives(MinecraftServer server, int value) {
        lives = value;
        gameSettingsConfigured = true;
        ArenaSnapshotPersistence.saveConfigAsync(server);
    }

    public static void setGameSettings(MinecraftServer server, int war, int shrink, int shrinkDuration,
            int configuredLives) {
        warTime = war;
        shrinkAmount = shrink;
        shrinkTime = shrinkDuration;
        lives = configuredLives;
        gameSettingsConfigured = true;
        ArenaSnapshotPersistence.saveConfigAsync(server);
    }

    public static void clear() {
        clearBackupState();
        arenaConfigured = false;
        gameSettingsConfigured = false;
    }

    static void clearBackupState() {
        SAVED.clear();
        QUEUE.clear();
        state = State.EMPTY;
    }

    public static void beginCapture(MinecraftServer server, double cx, double cz, int arenaSize) {
        ArenaSnapshotJobs.beginCapture(server, cx, cz, arenaSize);
    }

    public static void beginRestore(MinecraftServer server) {
        ArenaSnapshotJobs.beginRestore(server);
    }

    static void startJob(State newState) {
        totalChunks = QUEUE.size();
        lastReportedStep = 0;
        state = newState;
    }

    public static void tick(MinecraftServer server) {
        ArenaSnapshotJobs.tick(server);
    }

    public static void loadFromDisk(MinecraftServer server) {
        ArenaSnapshotPersistence.loadFromDisk(server);
    }

    /**
     * Blocks until any in-progress save or load finishes. Call while the server is
     * stopping.
     */
    public static void awaitPendingIo() {
        try {
            pendingIo.join();
        } catch (Exception e) {
            LOGGER.error("Arena backup I/O failed during shutdown", e);
        }
    }

    static int minChunkCoord(double center, int arenaSize) {
        double half = arenaSize / 2.0;
        return net.minecraft.core.SectionPos.blockToSectionCoord(Mth.floor(center - half));
    }

    static int maxChunkCoord(double center, int arenaSize) {
        double half = arenaSize / 2.0;
        return net.minecraft.core.SectionPos.blockToSectionCoord(Mth.floor(center + half));
    }
}
