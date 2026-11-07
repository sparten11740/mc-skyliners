package dev.skyliners.console;

import dev.skyliners.block.Mount;
import dev.skyliners.block.MountedModule;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Where a console module stands in its block, as the server reads a touch on it and the client draws it (see {@link
 * ConsoleBlock}). On a floor it is a desk seen across the floor plan as four corners, the near edge toward whoever
 * works it and the far edge away, left and right as they see it: its front a low fascia, its top sloping up from the
 * near edge toward the far one where a screen leans back over it, so the desk carries the controls and the screen the
 * pictures. Put up by hand its desk fills its block, facing whoever put it there; in a console laid round a ship's
 * seats (see {@link Facet}) it is its slice of the console's ring, its edges on the ring's two circles and its ends on
 * the ring's spokes, so that side by side the slices make one smooth half-round desk curving round the seats. Fixed to
 * a wall it is a plate there, its screen over its controls; hung under a ceiling, a wedge whose face leans down toward
 * whoever stands before it. FACING is the way toward them.
 *
 * @param innerLeft  the near edge's left end, in the block's own frame, across the floor plan (y unused)
 * @param innerRight the near edge's right end
 * @param outerLeft  the far edge's left end
 * @param outerRight the far edge's right end
 * @param leftEnd    whether the desk ends at its left, its side closed off, rather than running on into the next slice
 * @param rightEnd   whether it ends at its right
 * @param middle     the middle of the ring it is a slice of, in the block's own frame across the floor plan; null for one
 *                   standing on its own, straight
 */
public record ConsoleShape(Mount mount, Direction facing, Vec3 innerLeft, Vec3 innerRight, Vec3 outerLeft, Vec3 outerRight,
                           boolean leftEnd, boolean rightEnd, @Nullable Vec3 middle) {
    /** The desk's fascia's top, its sloping top's near edge, over the floor, in blocks. */
    public static final double FRONT_TOP = 0.66;
    /** The desk's sloping top's far edge, where the screen stands, over the floor, in blocks. */
    public static final double DESK_BACK = 0.84;
    /** How far back from the near edge toward the far one the desk's top reaches before the screen, as a share. */
    public static final double DESK_SHARE = 0.55;
    /** The screen's top edge over the floor, in blocks, just under a seated pilot's eyes, who looks out over it. */
    public static final double SCREEN_TOP = 1.28;
    /** How far a plate on a wall stands out from it, in blocks. */
    public static final double PLATE_DEPTH = 0.16;
    /** How far under a ceiling a hung wedge's near edge reaches down from the ceiling, in blocks. */
    public static final double HUNG_DROP = 0.5;
    /** How far up a plate on a wall or the face of a hung wedge its screen begins, as a share of the face. */
    public static final double SCREEN_FROM = 0.45;

    /**
     * A console module's slice of the ring of a console laid round a ship's seats: the ring's middle, from the middle of
     * the block, in the world's directions, across the floor plan; its inner and outer circles' radii, in blocks; the
     * bearings its slice runs between, in degrees clockwise from north, its left end first as seen from the middle; and
     * whether the desk ends at either side.
     */
    public record Facet(double middleX, double middleZ, double inner, double outer, double from, double to, boolean leftEnd,
                        boolean rightEnd) {
        /** The slice turned with its ship by a quarter turn about the middle of its block. */
        public Facet turned(Rotation turn) {
            int quarters = switch (turn) {
                case NONE -> 0;
                case CLOCKWISE_90 -> 1;
                case CLOCKWISE_180 -> 2;
                case COUNTERCLOCKWISE_90 -> 3;
            };
            double x = middleX;
            double z = middleZ;
            for (int quarter = 0; quarter < quarters; quarter++) {
                double turnedX = -z;
                z = x;
                x = turnedX;
            }
            return new Facet(x, z, inner, outer, from + 90 * quarters, to + 90 * quarters, leftEnd, rightEnd);
        }

        /** A point of the ring, so far out from its middle at a bearing, in the block's own frame across the floor plan. */
        Vec3 at(double radius, double bearing) {
            double turn = Math.toRadians(bearing);
            return new Vec3(0.5 + middleX + radius * Math.sin(turn), 0, 0.5 + middleZ - radius * Math.cos(turn));
        }
    }

    /** Where a console module stands in its block: its slice of a ring where it has one, its block's own desk else. */
    public static ConsoleShape of(BlockState state, @Nullable Facet facet) {
        Mount mount = MountedModule.of(state);
        Direction facing = state.hasProperty(HorizontalDirectionalBlock.FACING) ? state.getValue(HorizontalDirectionalBlock.FACING) : Direction.SOUTH;
        if (facet != null && mount == Mount.FLOOR) {
            return new ConsoleShape(mount, facing, facet.at(facet.inner(), facet.from()), facet.at(facet.inner(), facet.to()),
                facet.at(facet.outer(), facet.from()), facet.at(facet.outer(), facet.to()), facet.leftEnd(), facet.rightEnd(), facet.at(0, 0));
        }
        Vec3 middle = new Vec3(0.5, 0, 0.5);
        Vec3 toward = Vec3.atLowerCornerOf(facing.getNormal()).scale(0.5);
        Vec3 right = Vec3.atLowerCornerOf(facing.getCounterClockWise().getNormal()).scale(0.5);
        return new ConsoleShape(mount, facing, middle.add(toward).subtract(right), middle.add(toward).add(right),
            middle.subtract(toward).subtract(right), middle.subtract(toward).add(right), true, true, null);
    }

    /** A point of the desk across the floor plan: so far across from its left end, and so far back from its near edge. */
    public Vec3 plan(double across, double back) {
        Vec3 near = innerLeft.lerp(innerRight, across);
        Vec3 far = outerLeft.lerp(outerRight, across);
        return near.lerp(far, back);
    }

    /**
     * How far across a point in the block lies from the module's left end to its right, as whoever works it sees it,
     * from 0 to 1: where a touch on it falls among the controls in a row along its desk.
     */
    public double across(Vec3 point) {
        if (mount != Mount.FLOOR) {
            Vec3 right = Vec3.atLowerCornerOf(facing.getCounterClockWise().getNormal());
            return Mth.clamp(point.subtract(0.5, 0.5, 0.5).dot(right) + 0.5, 0, 1);
        }
        Vec3 edge = innerRight.subtract(innerLeft);
        Vec3 off = new Vec3(point.x - innerLeft.x, 0, point.z - innerLeft.z);
        return Mth.clamp(off.dot(edge) / Math.max(edge.lengthSqr(), 1e-6), 0, 1);
    }

    /** Which of so many controls in a row along its desk a point falls at. */
    public int control(Vec3 point, int controls) {
        return Mth.clamp((int) Math.floor(across(point) * controls), 0, controls - 1);
    }
}
