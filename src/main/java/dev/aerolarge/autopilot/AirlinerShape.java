package dev.aerolarge.autopilot;

import dev.aerolarge.compat.SableFlight;
import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The airliner's shape as a landing needs it, measured from its own blocks: how far it reaches ahead of and behind the
 * autopilot and out to either wingtip, and, at every distance out from the middle, how high its lowest block there is
 * above the bottom of its wheels. Under the fuselage that is the belly, a few blocks up; under the wings, the engines
 * and then the wings themselves, higher still toward the tips. The landing gear (the blocks at the very bottom, and
 * the legs above them) is kept apart: where its wheels run, the ground must be level; everywhere else under the
 * airliner, low things may pass beneath it. Each block is measured as the airliner was built, with its nose level, so
 * a nose raised a few degrees on landing drops the tail a little lower than measured: the landing allows for that
 * with a margin (see {@link RunwayCheck}).
 *
 * <p>The blocks are looked down column by column, a share of them each tick (see {@link #COLUMNS_PER_TICK}), once,
 * the first time a landing near the pin needs it; nothing is saved, as the airliner is measured again in a moment
 * after loading.
 */
final class AirlinerShape {
    /**
     * Columns of the airliner's blocks looked down each tick while it is measured: a 160 by 145 block airliner is some
     * 23,000 columns, measured in under a second this way, each column looked down from the bottom only until it meets
     * a block.
     */
    private static final int COLUMNS_PER_TICK = 1500;
    /**
     * A column whose lowest block is no higher than this above the bottom of the wheels, in blocks, is the landing
     * gear's: the wheel mounts at the bottom, the leg over them.
     */
    private static final int GEAR_LAYER = 1;
    /** No block at all this far out from the middle: nothing of the airliner there to run into anything. */
    static final int NOTHING = Integer.MAX_VALUE;

    /** The airliner's blocks in its plot, and the next column of them to look down; null until measuring starts. */
    @Nullable
    private BoundingBox box;
    private int nextX;
    private int nextZ;
    private boolean measured;
    /** Where the autopilot is in the plot, and the plot's directions to the nose and to the right wing. */
    private BlockPos autopilot = BlockPos.ZERO;
    private Direction forward = Direction.NORTH;
    private Direction right = Direction.EAST;
    /**
     * The lowest block's height above the bottom of the wheels at each distance out from the middle (index: the
     * distance plus {@link #reach}), leaving out the landing gear.
     */
    private int[] body = new int[0];
    private int reach;
    /** How far its blocks reach out to the left and right, and ahead of the autopilot (negative: behind it). */
    private int left = Integer.MAX_VALUE;
    private int rightmost = Integer.MIN_VALUE;
    private int front = Integer.MIN_VALUE;
    private int rear = Integer.MAX_VALUE;
    /** The same for the landing gear alone: how far out it stands either side, and ahead of the autopilot. */
    private int gearLeft = Integer.MAX_VALUE;
    private int gearRight = Integer.MIN_VALUE;
    private int gearFront = Integer.MIN_VALUE;
    private int gearRear = Integer.MAX_VALUE;

    /** Whether it has been measured, with landing gear found under it. */
    boolean measured() {
        return measured && gearLeft <= gearRight;
    }

    /**
     * Measures some more of the airliner the autopilot (at a position in its plot, its nose pointing the given way) is
     * part of; nothing once it has been measured, or while it is not assembled.
     */
    void measure(Level level, BlockPos at, Direction nose) {
        if (measured) {
            return;
        }
        if (box == null && !start(level, at, nose)) {
            return;
        }
        BlockPos.MutableBlockPos column = new BlockPos.MutableBlockPos();
        for (int done = 0; done < COLUMNS_PER_TICK && !measured; done++) {
            lookDown(level, column.set(nextX, box.minY(), nextZ));
            advance();
        }
    }

    /** Starts measuring the airliner round the autopilot: whether it is assembled, so it can be. */
    private boolean start(Level level, BlockPos at, Direction nose) {
        BoundingBox blocks = SableFlight.blocks(level, Vec3.atCenterOf(at));
        if (blocks == null) {
            return false;
        }
        box = blocks;
        autopilot = at;
        forward = nose;
        right = nose.getClockWise();
        nextX = blocks.minX();
        nextZ = blocks.minZ();
        reach = Math.max(Math.max(Math.abs(blocks.minX() - at.getX()), Math.abs(blocks.maxX() - at.getX())),
            Math.max(Math.abs(blocks.minZ() - at.getZ()), Math.abs(blocks.maxZ() - at.getZ())));
        body = new int[2 * reach + 1];
        Arrays.fill(body, NOTHING);
        return true;
    }

    /** On to the next column, row by row; past the last, it is measured. */
    private void advance() {
        nextX++;
        if (nextX <= box.maxX()) {
            return;
        }
        nextX = box.minX();
        nextZ++;
        measured = nextZ > box.maxZ();
    }

    /** Looks down a column from the bottom of the wheels until it meets a block, and notes how high that is. */
    private void lookDown(Level level, BlockPos.MutableBlockPos column) {
        for (int y = box.minY(); y <= box.maxY(); y++) {
            if (!level.getBlockState(column.setY(y)).isAir()) {
                note(column.getX(), column.getZ(), y - box.minY());
                return;
            }
        }
    }

    /** Notes the lowest block of a column, at a height above the bottom of the wheels. */
    private void note(int x, int z, int height) {
        int dx = x - autopilot.getX();
        int dz = z - autopilot.getZ();
        int out = dx * right.getStepX() + dz * right.getStepZ();
        int ahead = dx * forward.getStepX() + dz * forward.getStepZ();
        left = Math.min(left, out);
        rightmost = Math.max(rightmost, out);
        front = Math.max(front, ahead);
        rear = Math.min(rear, ahead);
        if (height > GEAR_LAYER) {
            body[out + reach] = Math.min(body[out + reach], height);
            return;
        }
        gearLeft = Math.min(gearLeft, out);
        gearRight = Math.max(gearRight, out);
        gearFront = Math.max(gearFront, ahead);
        gearRear = Math.min(gearRear, ahead);
    }

    /** How far out its furthest wingtip reaches, either side of the middle. */
    int halfSpan() {
        return Math.max(-left, rightmost);
    }

    /** How far its nose reaches ahead of the autopilot. */
    int ahead() {
        return Math.max(0, front);
    }

    /** How far its tail reaches behind the autopilot. */
    int behind() {
        return Math.max(0, -rear);
    }

    /** How far out the landing gear's outermost wheels stand to the left (negative) and right of the middle. */
    int gearLeft() {
        return gearLeft;
    }

    int gearRight() {
        return gearRight;
    }

    /** How far ahead of the autopilot its foremost and rearmost wheels are (negative: behind it). */
    int gearFront() {
        return gearFront;
    }

    int gearRear() {
        return gearRear;
    }

    /** How high its lowest block is above the bottom of the wheels this far out from the middle, leaving out the gear. */
    int body(int out) {
        int index = out + reach;
        return index < 0 || index >= body.length ? NOTHING : body[index];
    }
}
