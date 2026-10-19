package dev.aerolarge.airframe;

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.aerolarge.AerolargeConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * An airliner going up: its layout turned to face the right way and set at a spot in the world, then placed a few
 * thousand blocks a tick. Blocks are set without updating their neighbours: the layout already holds each one's
 * final state (both halves of every door, the curtains and tall windows joined up, the water at rest), and a
 * neighbour update halfway through would tear down a door whose other half is not placed yet.
 */
public final class AirframeBuild {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final Map<ServerLevel, List<AirframeBuild>> ACTIVE = new IdentityHashMap<>();
    /**
     * The size of each stretch of glue across and up, in blocks. Create and the Physics Assembler look for glue only
     * around the blocks they are joining, which finds a glue entity only when its position (the middle of its base) is
     * within about 16 blocks; so the airliner is glued in overlapping stretches no bigger than that reach.
     */
    private static final int GLUE_ACROSS = 24;
    private static final int GLUE_UP = 16;

    private final List<BlockPos> positions;
    private final List<BlockState> states;
    private final Map<BlockPos, Fitting> fittings;
    private final Rotation rotation;
    @Nullable
    private final UUID builder;
    private int next;

    private AirframeBuild(Placement placement, @Nullable UUID builder) {
        this.positions = placement.positions();
        this.states = placement.states();
        this.fittings = placement.fittings();
        this.rotation = placement.rotation();
        this.builder = builder;
    }

    /**
     * Where a layout lands in the world: its nose tip at {@code nose}, the tail stretching away along {@code along},
     * its tires on nose's y.
     */
    public record Placement(
        List<BlockPos> positions, List<BlockState> states, Map<BlockPos, Fitting> fittings, Rotation rotation, int blocked, BlockPos firstBlocked,
        int top
    ) {
        public static Placement of(ServerLevel level, AirframeLayout layout, BlockPos nose, Direction along) {
            // The layout's tail runs south; turning that onto `along` turns every position and block state with it.
            Rotation rotation = switch (along) {
                case WEST -> Rotation.CLOCKWISE_90;
                case NORTH -> Rotation.CLOCKWISE_180;
                case EAST -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };
            List<BlockPos> positions = new ArrayList<>(layout.cells().size());
            List<BlockState> states = new ArrayList<>(layout.cells().size());
            int blocked = 0;
            BlockPos firstBlocked = null;
            int top = Integer.MIN_VALUE;
            // Air first, so the cabin is cleared before anything goes in it, then bottom to top.
            List<Map.Entry<BlockPos, BlockState>> cells = new ArrayList<>(layout.cells().entrySet());
            cells.sort((a, b) -> a.getValue().isAir() != b.getValue().isAir()
                ? (a.getValue().isAir() ? -1 : 1)
                : Integer.compare(a.getKey().getY(), b.getKey().getY()));
            for (Map.Entry<BlockPos, BlockState> cell : cells) {
                BlockPos pos = nose.offset(cell.getKey().rotate(rotation));
                positions.add(pos);
                // Through the level, so blocks that turn themselves with their surroundings in mind (other mods') can.
                states.add(cell.getValue().rotate(level, pos, rotation));
                top = Math.max(top, pos.getY());
                if (!level.isInWorldBounds(pos)) {
                    continue;
                }
                BlockState there = level.getBlockState(pos);
                if (!there.isAir() && !there.canBeReplaced()) {
                    blocked++;
                    if (firstBlocked == null) {
                        firstBlocked = pos;
                    }
                }
            }
            Map<BlockPos, Fitting> fittings = new HashMap<>();
            layout.fittings().forEach((pos, fitting) -> fittings.put(nose.offset(pos.rotate(rotation)), fitting));
            return new Placement(positions, states, fittings, rotation, blocked, firstBlocked, top);
        }
    }

    public static boolean isRunning(ServerLevel level) {
        List<AirframeBuild> builds = ACTIVE.get(level);
        return builds != null && !builds.isEmpty();
    }

    /** Starts placing; progress goes to the builder, if a player started it. */
    public static void start(ServerLevel level, Placement placement, @Nullable ServerPlayer builder) {
        ACTIVE.computeIfAbsent(level, ignored -> new ArrayList<>())
            .add(new AirframeBuild(placement, builder != null ? builder.getUUID() : null));
    }

    public static void tick(ServerLevel level) {
        List<AirframeBuild> builds = ACTIVE.get(level);
        if (builds != null) {
            builds.removeIf(build -> build.step(level));
        }
    }

    public static void drop(ServerLevel level) {
        ACTIVE.remove(level);
    }

    /**
     * Glues the finished airliner together with Create's Super Glue, which is what the Physics Assembler takes as the
     * structure to assemble. The glue goes on as a grid of stretches over the airliner's bounds, each overlapping the
     * next by a block so every pair of neighbouring blocks shares one, and only where the airliner has blocks. Anything
     * built inside the hull later is under the same glue and flies along.
     */
    private void glue(ServerLevel level) {
        BlockPos min = null;
        BlockPos max = null;
        for (int i = 0; i < positions.size(); i++) {
            if (!states.get(i).isAir()) {
                BlockPos pos = positions.get(i);
                min = min == null ? pos : BlockPos.min(min, pos);
                max = max == null ? pos : BlockPos.max(max, pos);
            }
        }
        if (min == null) {
            return;
        }
        Set<BlockPos> stretches = new HashSet<>();
        for (int i = 0; i < positions.size(); i++) {
            if (!states.get(i).isAir()) {
                BlockPos pos = positions.get(i).subtract(min);
                for (int x : stretchesOver(pos.getX(), GLUE_ACROSS)) {
                    for (int y : stretchesOver(pos.getY(), GLUE_UP)) {
                        for (int z : stretchesOver(pos.getZ(), GLUE_ACROSS)) {
                            stretches.add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
        }
        for (BlockPos stretch : stretches) {
            BlockPos from = min.offset(stretch.getX() * (GLUE_ACROSS - 1), stretch.getY() * (GLUE_UP - 1), stretch.getZ() * (GLUE_ACROSS - 1));
            BlockPos to = BlockPos.min(max, from.offset(GLUE_ACROSS - 1, GLUE_UP - 1, GLUE_ACROSS - 1));
            level.addFreshEntity(new SuperGlueEntity(level, AABB.encapsulatingFullBlocks(from, to)));
        }
    }

    /**
     * The stretches of glue along one axis that cover a block this far from the start, each {@code size} long and
     * starting {@code size - 1} after the one before: one, or two where neighbouring stretches overlap.
     */
    private static int[] stretchesOver(int offset, int size) {
        int stretch = offset / (size - 1);
        return stretch > 0 && offset % (size - 1) == 0 ? new int[]{stretch - 1, stretch} : new int[]{stretch};
    }

    /** Places the next batch; true once the whole airliner stands. */
    private boolean step(ServerLevel level) {
        int end = Math.min(positions.size(), next + AerolargeConfig.BLOCKS_PER_TICK.get());
        for (; next < end; next++) {
            BlockPos pos = positions.get(next);
            if (level.isInWorldBounds(pos)) {
                level.setBlock(pos, states.get(next), FLAGS);
                Fitting fitting = fittings.get(pos);
                BlockEntity blockEntity = fitting != null ? level.getBlockEntity(pos) : null;
                if (blockEntity != null) {
                    fitting.fit(blockEntity, rotation);
                    blockEntity.setChanged();
                    level.sendBlockUpdated(pos, blockEntity.getBlockState(), blockEntity.getBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        }
        boolean done = next >= positions.size();
        if (done) {
            glue(level);
        }
        if (builder != null && level.getPlayerByUUID(builder) instanceof ServerPlayer player) {
            if (done) {
                player.displayClientMessage(Component.translatable("message.aerolarge.built"), false);
            } else if (level.getGameTime() % 10 == 0) {
                player.displayClientMessage(Component.translatable("message.aerolarge.building", 100 * next / positions.size()), true);
            }
        }
        return done;
    }
}
