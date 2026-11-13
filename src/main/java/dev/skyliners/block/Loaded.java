package dev.skyliners.block;

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
 * is removed or its chunk unloads. A kind a client asks after too (the autopilots, whose airliners' frames a client
 * tells apart as it mines) is kept on clients as well.
 */
public final class Loaded<T extends BlockEntity> {
    private final Class<T> type;
    private final boolean onClients;
    private final Map<Level, Set<BlockPos>> positions = new WeakHashMap<>();

    public Loaded(Class<T> type) {
        this(type, false);
    }

    public Loaded(Class<T> type, boolean onClients) {
        this.type = type;
        this.onClients = onClients;
    }

    /** From the block entity's {@code onLoad}: keeps track of it, on the server, and on a client too if asked to. */
    public void add(T blockEntity) {
        Level level = blockEntity.getLevel();
        if (level instanceof ServerLevel || level != null && onClients) {
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

    /**
     * Whether one loaded in a level stands within so many blocks of a box of blocks, its lowest and highest corners
     * given, without looking any of them up: for a test run too often to look them up each time, as a ship's collisions
     * ask whether a block whose shape reaches past it is near (see {@code SubLevelEntityCollisionMixin}).
     */
    public boolean anyNear(Level level, BlockPos low, BlockPos high, int reach) {
        Set<BlockPos> loaded = positions.get(level);
        if (loaded == null) {
            return false;
        }
        for (BlockPos at : loaded) {
            boolean near = at.getX() >= low.getX() - reach && at.getX() <= high.getX() + reach
                && at.getY() >= low.getY() - reach && at.getY() <= high.getY() + reach
                && at.getZ() >= low.getZ() - reach && at.getZ() <= high.getZ() + reach;
            if (near) {
                return true;
            }
        }
        return false;
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
