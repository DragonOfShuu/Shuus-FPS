package dev.dragonofshuu.fps.arena;

import dev.dragonofshuu.fps.ShuuFPS;
import org.slf4j.Logger;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import dev.dragonofshuu.fps.modes.battleRoyale.BattleRoyaleGame;

final class ArenaSnapshotPersistence {

    static final Logger LOGGER = ShuuFPS.LOGGER;

    private ArenaSnapshotPersistence() {
    }

    static Path backupDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve(ArenaSnapshot.BACKUP_FOLDER).normalize();
    }

    static void saveAsync(MinecraftServer server) {
        ArenaSnapshot.state = ArenaSnapshot.State.SAVING;
        BattleRoyaleGame.broadcast(server, "Arena captured. Saving backup to disk...");

        ArenaSnapshot.Backup backup = new ArenaSnapshot.Backup(
                ArenaSnapshot.centerX,
                ArenaSnapshot.centerZ,
                ArenaSnapshot.size,
                new HashMap<>(ArenaSnapshot.SAVED));
        Path dir = backupDir(server);

        ArenaSnapshot.pendingIo = java.util.concurrent.CompletableFuture
                .runAsync(() -> writeToDisk(dir, backup), Util.ioPool())
                .whenComplete((ignored, error) -> server.execute(() -> {
                    ArenaSnapshot.state = ArenaSnapshot.State.READY;
                    if (error != null) {
                        LOGGER.error("Failed to save arena backup", error);
                        BattleRoyaleGame.broadcast(server,
                                "Saving the backup failed; it is only kept in memory. Check the server log.");
                    } else {
                        BattleRoyaleGame.broadcast(server, "Arena backup saved.");
                    }
                }));
    }

    static void saveConfigAsync(MinecraftServer server) {
        Path dir = backupDir(server);
        double cx = ArenaSnapshot.centerX;
        double cz = ArenaSnapshot.centerZ;
        int arenaSize = ArenaSnapshot.size;

        ArenaSnapshot.pendingIo = java.util.concurrent.CompletableFuture
                .runAsync(() -> writeConfigToDisk(dir, cx, cz, arenaSize), Util.ioPool())
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        LOGGER.error("Failed to save arena config", error);
                    }
                });
    }

    /**
     * Called when the server starts. Loads an existing backup in the background.
     */
    static void loadFromDisk(MinecraftServer server) {
        Path dir = backupDir(server);
        loadConfigFromDisk(dir);

        if (!Files.exists(dir.resolve(ArenaSnapshot.META_FILE))) {
            return;
        }

        ArenaSnapshot.state = ArenaSnapshot.State.LOADING;
        LOGGER.info("Loading arena backup from {}", dir);

        ArenaSnapshot.pendingIo = java.util.concurrent.CompletableFuture
                .supplyAsync(() -> readFromDisk(dir), Util.ioPool())
                .whenComplete((backup, error) -> server.execute(() -> {
                    if (error != null || backup == null) {
                        LOGGER.error("Failed to load arena backup", error);
                        ArenaSnapshot.state = ArenaSnapshot.State.EMPTY;
                        return;
                    }

                    ArenaSnapshot.SAVED.clear();
                    ArenaSnapshot.SAVED.putAll(backup.chunks());
                    ArenaSnapshot.centerX = backup.centerX();
                    ArenaSnapshot.centerZ = backup.centerZ();
                    ArenaSnapshot.size = backup.size();
                    ArenaSnapshot.arenaConfigured = true;
                    ArenaSnapshot.state = ArenaSnapshot.State.READY;
                    LOGGER.info("Loaded arena backup: {} chunks, size {}",
                            backup.chunks().size(), backup.size());
                }));
    }

    private static void writeToDisk(Path dir, ArenaSnapshot.Backup backup) {
        try {
            Files.createDirectories(dir);

            // Remove the metadata first so a half-written backup is never loaded.
            Files.deleteIfExists(dir.resolve(ArenaSnapshot.META_FILE));
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    String fileName = file.getFileName().toString();
                    if (fileName.endsWith(".dat") && !fileName.equals(ArenaSnapshot.CONFIG_FILE)) {
                        Files.delete(file);
                    }
                }
            }

            writeConfigToDisk(dir, backup.centerX(), backup.centerZ(), backup.size());

            // Group chunks into one file per 32x32-chunk region.
            Map<Long, ListTag> regions = new HashMap<>();
            for (Map.Entry<Long, ArenaSnapshot.ChunkData> entry : backup.chunks().entrySet()) {
                ChunkPos pos = new ChunkPos(entry.getKey());
                long regionKey = ChunkPos.asLong(pos.getRegionX(), pos.getRegionZ());
                regions.computeIfAbsent(regionKey, key -> new ListTag())
                        .add(ArenaSnapshotCodec.encodeChunk(pos, entry.getValue()));
            }

            for (Map.Entry<Long, ListTag> entry : regions.entrySet()) {
                ChunkPos region = new ChunkPos(entry.getKey());
                CompoundTag root = new CompoundTag();
                root.put("Chunks", entry.getValue());
                ArenaSnapshotCodec.writeTag(dir.resolve("r." + region.x + "." + region.z + ".dat"), root);
            }

            CompoundTag meta = new CompoundTag();
            meta.putInt("DataVersion", ArenaSnapshotCodec.currentDataVersion());
            meta.putDouble("CenterX", backup.centerX());
            meta.putDouble("CenterZ", backup.centerZ());
            meta.putInt("Size", backup.size());
            meta.putInt("ChunkCount", backup.chunks().size());
            ArenaSnapshotCodec.writeTag(dir.resolve(ArenaSnapshot.META_FILE), meta);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeConfigToDisk(Path dir, double cx, double cz, int arenaSize) {
        try {
            Files.createDirectories(dir);
            CompoundTag config = new CompoundTag();
            config.putInt("DataVersion", ArenaSnapshotCodec.currentDataVersion());
            config.putDouble("CenterX", cx);
            config.putDouble("CenterZ", cz);
            config.putInt("Size", arenaSize);
            config.putInt("WarTime", ArenaSnapshot.warTime);
            config.putInt("ShrinkAmount", ArenaSnapshot.shrinkAmount);
            config.putInt("ShrinkTime", ArenaSnapshot.shrinkTime);
            config.putInt("Lives", ArenaSnapshot.lives);
            config.putBoolean("GameSettingsConfigured", ArenaSnapshot.gameSettingsConfigured);
            ArenaSnapshotCodec.writeTag(dir.resolve(ArenaSnapshot.CONFIG_FILE), config);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void loadConfigFromDisk(Path dir) {
        Path configPath = dir.resolve(ArenaSnapshot.CONFIG_FILE);
        if (!Files.exists(configPath)) {
            return;
        }

        try {
            CompoundTag config = ArenaSnapshotCodec.readTag(configPath);
            ArenaSnapshot.centerX = config.getDouble("CenterX");
            ArenaSnapshot.centerZ = config.getDouble("CenterZ");
            ArenaSnapshot.size = config.getInt("Size");
            ArenaSnapshot.arenaConfigured = ArenaSnapshot.size > 0;

            if (config.contains("GameSettingsConfigured", Tag.TAG_BYTE)) {
                ArenaSnapshot.gameSettingsConfigured = config.getBoolean("GameSettingsConfigured");
            } else {
                ArenaSnapshot.gameSettingsConfigured = config.contains("WarTime", Tag.TAG_INT)
                        && config.contains("ShrinkAmount", Tag.TAG_INT)
                        && config.contains("ShrinkTime", Tag.TAG_INT)
                        && config.contains("Lives", Tag.TAG_INT);
            }

            if (ArenaSnapshot.gameSettingsConfigured) {
                ArenaSnapshot.warTime = config.getInt("WarTime");
                ArenaSnapshot.shrinkAmount = config.getInt("ShrinkAmount");
                ArenaSnapshot.shrinkTime = config.getInt("ShrinkTime");
                ArenaSnapshot.lives = config.getInt("Lives");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load arena config", e);
        }
    }

    private static ArenaSnapshot.Backup readFromDisk(Path dir) {
        try {
            CompoundTag meta = ArenaSnapshotCodec.readTag(dir.resolve(ArenaSnapshot.META_FILE));
            int dataVersion = meta.getInt("DataVersion");
            if (dataVersion != ArenaSnapshotCodec.currentDataVersion()) {
                LOGGER.warn(
                        "Arena backup was made with data version {} (current {}). Some blocks may not restore correctly.",
                        dataVersion, ArenaSnapshotCodec.currentDataVersion());
            }

            Map<Long, ArenaSnapshot.ChunkData> chunks = new HashMap<>();
            try (Stream<Path> files = Files.list(dir)) {
                for (Path file : files.toList()) {
                    String name = file.getFileName().toString();
                    if (!name.startsWith("r.") || !name.endsWith(".dat"))
                        continue;

                    ListTag list = ArenaSnapshotCodec.readTag(file).getList("Chunks", Tag.TAG_COMPOUND);
                    for (int i = 0; i < list.size(); i++) {
                        CompoundTag chunkTag = list.getCompound(i);
                        ChunkPos pos = new ChunkPos(chunkTag.getInt("X"), chunkTag.getInt("Z"));
                        chunks.put(pos.toLong(), ArenaSnapshotCodec.decodeChunk(chunkTag));
                    }
                }
            }

            int expected = meta.getInt("ChunkCount");
            if (chunks.size() != expected) {
                LOGGER.warn("Arena backup expected {} chunks but found {}", expected, chunks.size());
            }

            return new ArenaSnapshot.Backup(meta.getDouble("CenterX"), meta.getDouble("CenterZ"), meta.getInt("Size"),
                    chunks);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
