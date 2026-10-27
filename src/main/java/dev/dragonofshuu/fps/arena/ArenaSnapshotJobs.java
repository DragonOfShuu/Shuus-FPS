package dev.dragonofshuu.fps.arena;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;

import java.util.HashMap;
import java.util.Map;

import dev.dragonofshuu.fps.shared.SharedArenaSafety;
import dev.dragonofshuu.fps.shared.ModBroadcasts;

final class ArenaSnapshotJobs {

    private ArenaSnapshotJobs() {
    }

    static void beginCapture(MinecraftServer server, double cx, double cz, int arenaSize) {
        ArenaSnapshot.clearBackupState();
        ArenaSnapshot.centerX = cx;
        ArenaSnapshot.centerZ = cz;
        ArenaSnapshot.size = arenaSize;
        ArenaSnapshot.arenaConfigured = true;

        int paddedArenaSize = arenaSize + 16; // Add padding to ensure full coverage of the arena
        int minChunkX = ArenaSnapshot.minChunkCoord(cx, paddedArenaSize);
        int maxChunkX = ArenaSnapshot.maxChunkCoord(cx, paddedArenaSize);
        int minChunkZ = ArenaSnapshot.minChunkCoord(cz, paddedArenaSize);
        int maxChunkZ = ArenaSnapshot.maxChunkCoord(cz, paddedArenaSize);

        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                ArenaSnapshot.QUEUE.add(new ChunkPos(x, z));
            }
        }

        ArenaSnapshot.startJob(ArenaSnapshot.State.CAPTURING);
        ModBroadcasts.broadcastToOps(server, "Backing up arena (" + ArenaSnapshot.totalChunks + " chunks)...");
    }

    static void beginRestore(MinecraftServer server) {
        ArenaSnapshot.QUEUE.clear();
        for (long key : ArenaSnapshot.SAVED.keySet()) {
            ArenaSnapshot.QUEUE.add(new ChunkPos(key));
        }

        ArenaSnapshot.startJob(ArenaSnapshot.State.RESTORING);
        ModBroadcasts.broadcastToOps(server, "Restoring arena (" + ArenaSnapshot.totalChunks + " chunks)...");
    }

    static void tick(MinecraftServer server) {
        if (ArenaSnapshot.state != ArenaSnapshot.State.CAPTURING
                && ArenaSnapshot.state != ArenaSnapshot.State.RESTORING) {
            return;
        }

        ServerLevel level = server.overworld();
        int budget = ArenaSnapshot.state == ArenaSnapshot.State.CAPTURING
                ? ArenaSnapshot.CAPTURE_CHUNKS_PER_TICK
                : ArenaSnapshot.RESTORE_CHUNKS_PER_TICK;

        for (int i = 0; i < budget && !ArenaSnapshot.QUEUE.isEmpty(); i++) {
            ChunkPos pos = ArenaSnapshot.QUEUE.poll();
            if (ArenaSnapshot.state == ArenaSnapshot.State.CAPTURING) {
                captureChunk(level, pos);
            } else {
                restoreChunk(level, pos);
            }
        }

        reportProgress(server);

        if (ArenaSnapshot.QUEUE.isEmpty()) {
            finish(server, level);
        }
    }

    private static void captureChunk(ServerLevel level, ChunkPos pos) {
        LevelChunk chunk = level.getChunk(pos.x, pos.z);
        LevelChunkSection[] sections = chunk.getSections();

        @SuppressWarnings("unchecked")
        PalettedContainer<BlockState>[] copies = new PalettedContainer[sections.length];
        for (int i = 0; i < sections.length; i++) {
            copies[i] = sections[i].getStates().copy();
        }

        Map<BlockPos, CompoundTag> blockEntities = new HashMap<>();
        for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
            blockEntities.put(blockEntity.getBlockPos().immutable(), blockEntity.saveWithFullMetadata());
        }

        ArenaSnapshot.SAVED.put(pos.toLong(), new ArenaSnapshot.ChunkData(copies, blockEntities));
    }

    private static void restoreChunk(ServerLevel level, ChunkPos pos) {
        ArenaSnapshot.ChunkData data = ArenaSnapshot.SAVED.get(pos.toLong());
        if (data == null)
            return;

        LevelChunk chunk = level.getChunk(pos.x, pos.z);
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int baseX = pos.getMinBlockX();
        int baseZ = pos.getMinBlockZ();
        int sectionCount = Math.min(sections.length, data.sections().length);

        // Restore block states, only touching blocks that actually changed.
        for (int i = 0; i < sectionCount; i++) {
            PalettedContainer<BlockState> saved = data.sections()[i];
            if (saved == null)
                continue; // Section failed to load from disk; leave it alone.

            LevelChunkSection current = sections[i];
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState wanted = saved.get(x, y, z);
                        if (current.getBlockState(x, y, z) != wanted) {
                            cursor.set(baseX + x, baseY + y, baseZ + z);
                            level.setBlock(cursor, wanted, ArenaSnapshot.RESTORE_FLAGS);
                        }
                    }
                }
            }
        }

        // Restore block entity data (chest contents, sign text, etc.).
        for (Map.Entry<BlockPos, CompoundTag> entry : data.blockEntities().entrySet()) {
            BlockPos bePos = entry.getKey();
            BlockEntity blockEntity = level.getBlockEntity(bePos);
            if (blockEntity != null) {
                blockEntity.load(entry.getValue().copy());
                blockEntity.setChanged();
                BlockState blockState = level.getBlockState(bePos);
                level.sendBlockUpdated(bePos, blockState, blockState, Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void finish(MinecraftServer server, ServerLevel level) {
        if (ArenaSnapshot.state == ArenaSnapshot.State.CAPTURING) {
            ArenaSnapshotPersistence.saveAsync(server);
            return;
        }

        SharedArenaSafety.clearGroundItems(level, ArenaSnapshot.centerX, ArenaSnapshot.centerZ, ArenaSnapshot.size);
        SharedArenaSafety.clearPlayerInventories(server);
        SharedArenaSafety.setAllPlayersAdventure(server);

        ArenaSnapshot.state = ArenaSnapshot.State.READY;
        ModBroadcasts.broadcastToOps(server, "Arena restored.");
    }

    private static void reportProgress(MinecraftServer server) {
        if (ArenaSnapshot.totalChunks == 0)
            return;

        int done = ArenaSnapshot.totalChunks - ArenaSnapshot.QUEUE.size();
        int step = done * 10 / ArenaSnapshot.totalChunks; // Report every 10%
        if (step > ArenaSnapshot.lastReportedStep && step < 10) {
            ArenaSnapshot.lastReportedStep = step;
            String verb = ArenaSnapshot.state == ArenaSnapshot.State.CAPTURING ? "Backup" : "Restore";
            ModBroadcasts.broadcastToOps(server, verb + " progress: " + (step * 10) + "%");
        }
    }
}
