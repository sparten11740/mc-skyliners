package dev.aerolarge.block;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The block entities of one kind loaded on the server, level by level, so a landing, a radio call or a basket finds
 * the ones it concerns without searching the world. Each block entity adds itself once loaded and drops out when it
 * is removed or its chunk unloads.
 */
public final class Loaded<T extends BlockEntity> {
    private final Class<T> type;
    private final Map<Level, Set<BlockPos>> positions = new WeakHashMap<>();

    public Loaded(Class<T> type) {
        this.type = type;
    }

    /** From the block entity's {@code onLoad}: keeps track of it, on the server. */
    public void add(T blockEntity) {
        if (blockEntity.getLevel() instanceof ServerLevel level) {
            positions.computeIfAbsent(level, ignored -> new HashSet<>()).add(blockEntity.getBlockPos().immutable());
        }
    }

    /** From the block entity's {@code setRemoved} and {@code onChunkUnloaded}. */
    public void remove(T blockEntity) {
        Set<BlockPos> loaded = blockEntity.getLevel() == null ? null : positions.get(blockEntity.getLevel());
        if (loaded != null) {
            loaded.remove(blockEntity.getBlockPos());
        }
    }

    /** Every one loaded in the level. */
    public List<T> in(Level level) {
        Set<BlockPos> loaded = positions.get(level);
        if (loaded == null) {
            return List.of();
        }
        List<T> found = new ArrayList<>();
        for (BlockPos pos : loaded) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (type.isInstance(blockEntity) && !blockEntity.isRemoved()) {
                found.add(type.cast(blockEntity));
            }
        }
        return found;
    }
}
