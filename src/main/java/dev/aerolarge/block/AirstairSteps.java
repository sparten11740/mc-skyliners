package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The blocks integral airstairs are walked on once they are down (see {@link AirstairStepBlock}), and the ground they
 * come down to, the same for an airliner's out of its door, a zeppelin's out of her belly and a rocket's out of her
 * front door: how far down the ground lets them reach, the steps laid under the flight with their guards and a foot
 * below, taken up again, and whether anyone is on them. Each flight says where its steps go ({@link StepAt}), in as
 * many columns as it is wide.
 */
final class AirstairSteps {
    /**
     * How far over the ground the foot's plate stands at least, in blocks: lower, and the bottom tread is within a step
     * of the ground anyway, and the airliner might come to rest on the plate.
     */
    private static final double FOOT_CLEARANCE = 0.05;
    /** What stands in the stairs' way, in the world or aboard: anything solid but their own steps. */
    static final Predicate<BlockState> IN_THE_WAY =
        state -> !state.isAir() && !state.canBeReplaced() && !(state.getBlock() instanceof AirstairStepBlock);

    private AirstairSteps() {
    }

    /**
     * Where a flight's step goes: {@code down} blocks below the sill, as far out as it is down, in one of the flight's
     * columns (0 the left, seen from outside); null while the flight has nowhere to be.
     */
    @FunctionalInterface
    interface StepAt {
        @Nullable
        BlockPos at(int down, int column);
    }

    /**
     * How far down a flight so many blocks wide could come this time: blocks clear all the way from its sill, up to its
     * whole rise. A block the flight's own hatch will take away as it opens is no hindrance.
     */
    static int reach(ServerLevel level, StepAt steps, int width, int rise, Predicate<BlockPos> cleared) {
        for (int down = 1; down <= rise; down++) {
            if (steps.at(down, 0) == null) {
                return 0;
            }
            if (rowBlocked(level, steps, width, down, cleared)) {
                return down - 1;
            }
        }
        return rise;
    }

    /** Whether anything stands where a row of steps would go, or in the room over it. */
    private static boolean rowBlocked(ServerLevel level, StepAt steps, int width, int down, Predicate<BlockPos> cleared) {
        for (int column = 0; column < width; column++) {
            BlockPos step = steps.at(down, column);
            if (step == null || blocked(level, step, cleared) || blocked(level, step.above(), cleared)) {
                return true;
            }
        }
        return false;
    }

    private static boolean blocked(ServerLevel level, BlockPos cell, Predicate<BlockPos> cleared) {
        return !cleared.test(cell) && SableFlight.find(level, SableFlight.toWorld(level, Vec3.atCenterOf(cell)), IN_THE_WAY) != null;
    }

    /**
     * Lays the steps under a flight so many blocks wide and this many long, the steps rising toward {@code up}, a guard
     * over each step at either side holding its side's handrail; and the foot below the bottom step where it is wanted.
     * Gives whether a foot was laid.
     */
    static boolean lay(ServerLevel level, StepAt steps, int width, int length, Direction up) {
        BlockState block = AerolargeRegistry.AIRSTAIR_STEP.get().defaultBlockState();
        for (int down = 1; down <= length; down++) {
            layRow(level, steps, width, down, up, block);
        }
        return layFoot(level, steps, width, length, AirstairStepBlock.foot(block, up));
    }

    /**
     * Lays a row of steps, the guards over the outer two holding the handrails on the left and on the right; the
     * steps between them, under a wide flight, are open over them, as nothing runs along its middle.
     */
    private static void layRow(ServerLevel level, StepAt steps, int width, int down, Direction up, BlockState block) {
        for (int column = 0; column < width; column++) {
            BlockPos step = steps.at(down, column);
            if (step == null) {
                continue;
            }
            place(level, step, AirstairStepBlock.step(block, up));
            if (column == 0 || column == width - 1) {
                AirstairStepBlock.Edge edge = column == 0 ? AirstairStepBlock.Edge.LEFT : AirstairStepBlock.Edge.RIGHT;
                place(level, step.above(), AirstairStepBlock.guard(block, up, edge));
            }
        }
    }

    /**
     * Lays the foot below the bottom step in each column where it is wanted (see {@link AirstairStepBlock}): an
     * assembled airliner rides on its suspension, which can stand the bottom tread higher over the ground than anyone
     * steps up without a jump, and the foot's plate, half way between them, bridges it. Only where the ground fills the
     * middle of the foot's block but stays below its top, the plate at least {@link #FOOT_CLEARANCE} over it; never on
     * the ground's own blocks, under stairs that stand on them. Gives whether any was laid.
     */
    private static boolean layFoot(ServerLevel level, StepAt steps, int width, int length, BlockState foot) {
        boolean laidAny = false;
        for (int column = 0; column < width; column++) {
            BlockPos at = steps.at(length + 1, column);
            if (at != null && wantsFoot(level, at)) {
                laidAny |= place(level, at, foot);
            }
        }
        return laidAny;
    }

    /** Whether the ground under the foot's block, in the world, stands high enough and low enough for its plate. */
    private static boolean wantsFoot(ServerLevel level, BlockPos at) {
        Vec3 plate = SableFlight.toWorld(level, Vec3.atCenterOf(at).add(0, 0.5, 0));
        BlockPos below = BlockPos.containing(plate.x, plate.y - 0.5, plate.z);
        BlockState ground = level.getBlockState(below);
        if (!IN_THE_WAY.test(ground)) {
            return false;
        }
        double top = below.getY() + ground.getCollisionShape(level, below).max(Direction.Axis.Y);
        return plate.y - top >= FOOT_CLEARANCE;
    }

    /** Puts a block where there is room for it: air, or something as easily replaced as grass. Gives whether it did. */
    private static boolean place(ServerLevel level, BlockPos at, BlockState state) {
        BlockState there = level.getBlockState(at);
        if (there.isAir() || there.canBeReplaced()) {
            level.setBlock(at, state, Block.UPDATE_ALL);
            return true;
        }
        return false;
    }

    /**
     * Takes up the steps laid this many blocks down under a flight so many blocks wide, the foot included, and the
     * guards with them; only the steps' own blocks, whatever stands in their place.
     */
    static void takeUp(ServerLevel level, StepAt steps, int width, int laidDown) {
        for (int down = 1; down <= laidDown; down++) {
            for (int column = 0; column < width; column++) {
                takeUp(level, steps.at(down, column));
            }
        }
    }

    /**
     * Takes up the steps a flight aboard an assembled airframe left standing in the world as she was assembled, those
     * out past the box her blocks were gathered from, as a rocket's long flight reaches: the steps' own blocks
     * wherever a step this many blocks down lies in the world now. Taken up before she moves off, as her stairs fold
     * away, that is where she left them; a flight not aboard leaves none.
     */
    static void takeUpLeftBehind(ServerLevel level, StepAt steps, int width, int laidDown) {
        for (int down = 1; down <= laidDown; down++) {
            for (int column = 0; column < width; column++) {
                takeUp(level, inWorld(level, steps.at(down, column)));
            }
        }
    }

    /** Where a block aboard an assembled airframe lies in the world now, if loaded; null for one not aboard. */
    @Nullable
    private static BlockPos inWorld(ServerLevel level, @Nullable BlockPos pos) {
        if (pos == null) {
            return null;
        }
        BlockPos world = BlockPos.containing(SableFlight.toWorld(level, Vec3.atCenterOf(pos)));
        return world.equals(pos) || !level.isLoaded(world) ? null : world;
    }

    /** Takes up a step and the guard over it, if they are still the steps' own blocks. */
    private static void takeUp(ServerLevel level, @Nullable BlockPos step) {
        if (step == null) {
            return;
        }
        for (BlockPos cell : new BlockPos[]{step.above(), step}) {
            if (level.getBlockState(cell).getBlock() instanceof AirstairStepBlock) {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** Whether a block is one of the steps laid this many blocks down under a flight so wide, the foot included. */
    static boolean holds(StepAt steps, int width, int laidDown, BlockPos pos) {
        for (int down = 1; down <= laidDown; down++) {
            if (inRow(steps, width, down, pos)) {
                return true;
            }
        }
        return false;
    }

    private static boolean inRow(StepAt steps, int width, int down, BlockPos pos) {
        for (int column = 0; column < width; column++) {
            if (pos.equals(steps.at(down, column))) {
                return true;
            }
        }
        return false;
    }

    /** The room over each row of a flight so many blocks wide and this many long, where anyone on it stands. */
    static List<AABB> spots(StepAt steps, int width, int length) {
        List<AABB> spots = new ArrayList<>();
        for (int down = 1; down <= length; down++) {
            BlockPos left = steps.at(down, 0);
            BlockPos right = steps.at(down, width - 1);
            if (left != null && right != null) {
                spots.add(AABB.encapsulatingFullBlocks(left, right.above(2)));
            }
        }
        return spots;
    }
}
