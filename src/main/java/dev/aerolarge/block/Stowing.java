package dev.aerolarge.block;

import dev.aerolarge.AerolargeConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * What an airliner has down on the ground beside it: its airstairs, and any evacuation slide out. Resting on the
 * ground they hold the airliner where it stands, and they only put themselves away once it moves, so set to take off,
 * the autopilot has them put away first, as a crew would, and holds the brakes till they are, or till it has waited
 * long enough (someone standing on the stairs keeps them out).
 */
final class Stowing {
    /** How far round the autopilot they are looked for, in blocks per unit of scale: the whole of a 73 m airliner. */
    private static final double REACH_PER_SCALE = 80;
    /** The longest the autopilot waits for them, in ticks. */
    private static final int MOST_WAIT = 20 * 20;

    private final List<BlockPos> stairs = new ArrayList<>();
    private final List<BlockPos> slides = new ArrayList<>();
    private long until;

    /** Sets everything down beside the airliner going away, as its autopilot round {@code autopilot} is set to take off. */
    void start(ServerLevel level, BlockPos autopilot) {
        stairs.clear();
        slides.clear();
        List<BlockEntity> found = new ArrayList<>();
        int reach = (int) Math.ceil(REACH_PER_SCALE * AerolargeConfig.SCALE.get());
        for (int chunkX = (autopilot.getX() - reach) >> 4; chunkX <= (autopilot.getX() + reach) >> 4; chunkX++) {
            for (int chunkZ = (autopilot.getZ() - reach) >> 4; chunkZ <= (autopilot.getZ() + reach) >> 4; chunkZ++) {
                // Never load a chunk just to look into it: all of an airliner's plot is loaded.
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk != null) {
                    found.addAll(chunk.getBlockEntities().values());
                }
            }
        }
        // Put away once all are found: putting them away changes the chunks' block entities.
        for (BlockEntity blockEntity : found) {
            if (blockEntity instanceof AirstairBlockEntity airstairs && !airstairs.folded()) {
                if (airstairs.unfolding()) {
                    airstairs.toggle();
                }
                stairs.add(airstairs.getBlockPos());
            } else if (blockEntity instanceof SlideControlBlockEntity slide && !slide.packed()) {
                slide.stow();
                slides.add(slide.getBlockPos());
            }
        }
        until = level.getGameTime() + MOST_WAIT;
    }

    /** Whether any of them is still going away, and the autopilot still waiting for it. */
    boolean waiting(ServerLevel level) {
        if (stairs.isEmpty() && slides.isEmpty()) {
            return false;
        }
        if (level.getGameTime() >= until) {
            stairs.clear();
            slides.clear();
            return false;
        }
        stairs.removeIf(pos -> !(level.getBlockEntity(pos) instanceof AirstairBlockEntity airstairs) || airstairs.folded());
        slides.removeIf(pos -> !(level.getBlockEntity(pos) instanceof SlideControlBlockEntity slide) || slide.packed());
        return !stairs.isEmpty() || !slides.isEmpty();
    }
}
