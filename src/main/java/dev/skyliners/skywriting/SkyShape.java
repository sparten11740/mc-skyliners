package dev.skyliners.skywriting;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleFunction;
import net.minecraft.world.phys.Vec3;

/**
 * A shape a zeppelin can write across the sky (see {@link Skywriting}): a smooth path at her height, no bend tighter
 * than she can turn at her writing speed, and none that a ship 490 blocks long would wipe out flying the next stretch.
 * Each is laid out in blocks in its own frame, across to starboard (x) and along ahead (z), from where she begins it,
 * setting off straight ahead.
 */
public enum SkyShape {
    /** A circle, turning to starboard all the way round. */
    CIRCLE(SkyShape::round),
    /**
     * A heart, begun and ended in the dip between its lobes: round the right lobe, down to the point, rounded as
     * tightly as she turns, and up round the left lobe back to the dip.
     */
    HEART(SkyShape::heart),
    /** A figure of eight: a circle to starboard, then one to port. */
    EIGHT(SkyShape::eight),
    /** A wave, rising and falling to either side of her course one and a half times. */
    WAVE(SkyShape::wave);

    /** The circles' radius, in blocks: well over the tightest she turns at her writing speed. */
    private static final double RADIUS = 150;
    /**
     * The heart's lobes: their radius, and how far either side of the heart's middle their centres are, in blocks. As
     * far apart as they are big, they touch at the dip, which she leaves heading straight up the heart. Its sides run
     * down from them at 45 degrees to its point.
     */
    private static final double LOBE_RADIUS = 150;
    private static final double LOBE_APART = 150;
    /** The wave: how far it swings to either side, how long one rise and fall is, and how many it makes, in blocks. */
    private static final double WAVE_HEIGHT = 90;
    private static final double WAVE_LENGTH = 700;
    private static final double WAVES = 1.5;
    /** How finely the curve is followed to lay the path, and how far apart the path's points are, in blocks. */
    private static final int SAMPLES = 2000;
    private static final double STEP = 8;
    /** How far along the curve its first heading is taken, in samples. */
    private static final int TANGENT_SAMPLES = 20;

    /** The heart's outline, worked out once, and its length, in blocks. */
    private static final List<Stretch> HEART_OUTLINE = heartOutline();
    private static final double HEART_LENGTH = HEART_OUTLINE.stream().mapToDouble(Stretch::length).sum();

    private final DoubleFunction<Vec3> curve;

    SkyShape(DoubleFunction<Vec3> curve) {
        this.curve = curve;
    }

    /** The shape's path, in its own frame: a point every few blocks, from where she begins it, setting off ahead. */
    public List<Vec3> path() {
        List<Vec3> traced = new ArrayList<>();
        for (int k = 0; k <= SAMPLES; k++) {
            traced.add(curve.apply((double) k / SAMPLES));
        }
        return spaced(aligned(traced));
    }

    /** The circle's curve, so far along it, a share of the way round. */
    private static Vec3 round(double t) {
        return circle(t * 2 * Math.PI, 1);
    }

    /** The figure of eight's curve: the first half round to starboard, the second round to port. */
    private static Vec3 eight(double t) {
        return t < 0.5 ? circle(t * 4 * Math.PI, 1) : circle((t - 0.5) * 4 * Math.PI, -1);
    }

    /** The wave's curve, so far along it. */
    private static Vec3 wave(double t) {
        return new Vec3(WAVE_HEIGHT * Math.sin(t * WAVES * 2 * Math.PI), 0, t * WAVES * WAVE_LENGTH);
    }

    /** A circle round to starboard (one) or to port (minus one), so far round it, from its start setting off ahead. */
    private static Vec3 circle(double angle, int side) {
        return new Vec3(side * (RADIUS - RADIUS * Math.cos(angle)), 0, RADIUS * Math.sin(angle));
    }

    /** The heart's curve, so far along its outline; drawn upright, and set ahead by {@link #aligned}. */
    private static Vec3 heart(double t) {
        double along = t * HEART_LENGTH;
        for (Stretch stretch : HEART_OUTLINE) {
            if (along <= stretch.length()) {
                return stretch.at(along);
            }
            along -= stretch.length();
        }
        Stretch last = HEART_OUTLINE.get(HEART_OUTLINE.size() - 1);
        return last.at(last.length());
    }

    /**
     * The heart's outline, drawn upright and clockwise from the dip between its lobes, where they meet: round the
     * right lobe to where its side leaves it, down the side, round the point, up the left side and round the left
     * lobe back to the dip.
     */
    private static List<Stretch> heartOutline() {
        double diagonal = Math.sqrt(0.5);
        double dip = Math.atan2(Math.sqrt(LOBE_RADIUS * LOBE_RADIUS - LOBE_APART * LOBE_APART), -LOBE_APART);
        double lobeSweep = dip + Math.PI / 4;
        double sideX = LOBE_APART + LOBE_RADIUS * diagonal;
        double sideY = -LOBE_RADIUS * diagonal;
        double pointY = -LOBE_APART - LOBE_RADIUS * Math.sqrt(2);
        double roundX = RADIUS * diagonal;
        double roundY = pointY + RADIUS * diagonal;
        return List.of(
            new Arc(LOBE_APART, 0, LOBE_RADIUS, dip, lobeSweep),
            new Straight(sideX, sideY, roundX, roundY),
            new Arc(0, pointY + RADIUS * Math.sqrt(2), RADIUS, -Math.PI / 4, Math.PI / 2),
            new Straight(-roundX, roundY, -sideX, sideY),
            new Arc(-LOBE_APART, 0, LOBE_RADIUS, -3 * Math.PI / 4, lobeSweep));
    }

    /** A stretch of a shape's outline as it is drawn upright: x across to the right and z up the picture, in blocks. */
    private interface Stretch {
        double length();

        /** The point so far along it, in blocks. */
        Vec3 at(double along);
    }

    /** A straight stretch, from one point to another. */
    private record Straight(double fromX, double fromZ, double toX, double toZ) implements Stretch {
        @Override
        public double length() {
            return Math.hypot(toX - fromX, toZ - fromZ);
        }

        @Override
        public Vec3 at(double along) {
            double share = along / length();
            return new Vec3(fromX + (toX - fromX) * share, 0, fromZ + (toZ - fromZ) * share);
        }
    }

    /** An arc round a centre, clockwise from an angle through a sweep, in radians, angles turning up from across. */
    private record Arc(double centreX, double centreZ, double radius, double from, double sweep) implements Stretch {
        @Override
        public double length() {
            return radius * sweep;
        }

        @Override
        public Vec3 at(double along) {
            double angle = from - along / radius;
            return new Vec3(centreX + radius * Math.cos(angle), 0, centreZ + radius * Math.sin(angle));
        }
    }

    /** The curve moved and turned so it begins where she is and sets off straight ahead, along z. */
    private static List<Vec3> aligned(List<Vec3> traced) {
        Vec3 start = traced.get(0);
        Vec3 tangent = traced.get(TANGENT_SAMPLES).subtract(start).normalize();
        double cos = tangent.z;
        double sin = tangent.x;
        List<Vec3> aligned = new ArrayList<>();
        for (Vec3 point : traced) {
            Vec3 off = point.subtract(start);
            aligned.add(new Vec3(off.x * cos - off.z * sin, 0, off.x * sin + off.z * cos));
        }
        return aligned;
    }

    /** The points of a finely followed curve a step apart along it, and its last. */
    private static List<Vec3> spaced(List<Vec3> traced) {
        List<Vec3> spaced = new ArrayList<>();
        spaced.add(traced.get(0));
        for (Vec3 point : traced) {
            if (point.distanceTo(spaced.get(spaced.size() - 1)) >= STEP) {
                spaced.add(point);
            }
        }
        spaced.add(traced.get(traced.size() - 1));
        return spaced;
    }
}
