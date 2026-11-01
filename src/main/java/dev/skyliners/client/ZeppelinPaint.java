package dev.skyliners.client;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.airframe.Livery;
import dev.skyliners.airframe.RocketSilhouette;
import dev.skyliners.airframe.ZeppelinSize;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import dev.skyliners.block.AutopilotBlock;
import dev.skyliners.block.OvalWindowBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The zeppelins and rockets as this client paints them, each in its livery (see {@link Livery}): the tint each block
 * of a cover and of the fabric (and the frame round a rocket's windows) is drawn with, from the livery of the ship
 * whose box it lies in, silver where it is in none. Each tick it takes a fresh look at their autopilots, as their
 * blocks send them, into a list the threads that mesh the world's sections read as it stands; and as a repaint sweeps
 * over a ship, along a zeppelin from its nose to its tail and down a rocket from her nose to her foot, it has the
 * sections the new paint has reached since the tick before meshed again, so the paint moves over it a slice at a time,
 * and the rest is never meshed again for it; down a rocket the new paint runs on ahead of its edge in drips. Over a zeppelin's livery go what its features paint on it (see
 * {@link PaintOverlay}), the ship's colours on its fins among them, each meshed again where it is as it comes and goes.
 */
public final class ZeppelinPaint {
    /**
     * A zeppelin or a rocket as this client paints it, how far from its nose the new paint has reached, in blocks (aft
     * along a zeppelin, down a rocket), and what its features paint over it.
     */
    private record Painted(Craft craft, BoundingBox box, BlockPos min, BlockPos max, Direction nose, ZeppelinSize size, Livery livery,
                           Livery before, double reached, List<PaintOverlay> overlays, int[] stories) {
        int colour(BlockPos pos, boolean fabric, boolean window) {
            if (craft == Craft.ROCKET) {
                return rocketColour(pos, fabric, window);
            }
            Livery.Spot spot = Livery.Spot.of(pos, min, max, nose, size);
            Livery under = spot.along() < reached ? livery : before;
            for (PaintOverlay overlay : overlays) {
                int colour = overlay.colour(spot, fabric, under);
                if (colour >= 0) {
                    return colour;
                }
            }
            return under.colour(spot, fabric);
        }

        /**
         * A rocket's block's colour: by how far up her it is, in which of her stories, and how far round her from her
         * front, clockwise seen from above, her axis up the middle of her box.
         */
        private int rocketColour(BlockPos pos, boolean fin, boolean window) {
            double down = max.getY() + 1 - (pos.getY() + 0.5);
            double height = 1 - down / (max.getY() + 1 - min.getY());
            double ahead = along(pos, nose);
            double right = along(pos, nose.getClockWise());
            double round = (Math.toDegrees(Math.atan2(right, ahead)) + FULL_TURN) % FULL_TURN;
            int band = Livery.RocketSpot.band(pos.getY() - min.getY(), stories);
            int cockpit = Livery.RocketSpot.band(stories[stories.length - 1], stories);
            Livery under = down < reached + drip(pos, reached) ? livery : before;
            return under.rocketColour(new Livery.RocketSpot(height, round, band, cockpit, window, pos.asLong()), fin);
        }

        /** How far a block's middle lies out from the middle of the box the way given, in blocks. */
        private double along(BlockPos pos, Direction way) {
            double middle = (min.getX() + max.getX() + 1) / 2.0 * way.getStepX() + (min.getZ() + max.getZ() + 1) / 2.0 * way.getStepZ();
            return (pos.getX() + 0.5) * way.getStepX() + (pos.getZ() + 0.5) * way.getStepZ() - middle;
        }
    }

    /** A whole turn round a rocket, in degrees. */
    private static final double FULL_TURN = 360;
    /**
     * The new paint's runs down a rocket ahead of its edge: how far the longest runs ahead, in blocks; how much of its
     * length a run always keeps; and how fast its spurts come, in radians a block the edge comes down. A run never
     * draws back up faster than the edge comes down, so no block once painted goes back to the old paint.
     */
    private static final double DRIP_LONGEST = 5;
    private static final double DRIP_STEADY = 0.7;
    private static final double DRIP_SPURTS = 0.5;

    /**
     * How far the new paint has run ahead of its edge down a column of a rocket's blocks, in blocks, as the edge has
     * come down so far: a few columns' runs long, most short, each running on in spurts of its own.
     */
    private static double drip(BlockPos pos, double reached) {
        long seed = Mth.getSeed(pos.getX(), 0, pos.getZ());
        double length = (seed >>> 20 & 0xFFFF) / (double) 0xFFFF;
        double phase = (seed >>> 40 & 0xFFFF) / (double) 0xFFFF * Mth.TWO_PI;
        double run = DRIP_LONGEST * length * length * length;
        return run * (DRIP_STEADY + (1 - DRIP_STEADY) * Math.sin(reached * DRIP_SPURTS + phase));
    }

    /** What the features paint over a zeppelin's livery, one line each: taking a feature out is taking out its line. */
    private static final List<PaintOverlay.Source> OVERLAYS = List.of(
        ShipsColours::fins,
        ChristeningPaint::flanks);

    /** The zeppelins as last seen, read by the meshing threads while the client's thread puts the next in its place. */
    private static volatile List<Painted> painted = List.of();
    /** The same, by where each one's autopilot is: what was meshed for it, to mesh what changes. */
    private static final Map<BlockPos, Painted> MESHED = new HashMap<>();

    private ZeppelinPaint() {
    }

    /**
     * The tint a block of a ship's cover or fabric, or a rocket's window's frame, is drawn with where it stands: a
     * window's frame all one colour, as the cover is where the window's middle is.
     */
    public static int tint(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        boolean fabric = state.is(SkylinersRegistry.ZEPPELIN_FABRIC.get());
        boolean window = state.getBlock() instanceof OvalWindowBlock;
        BlockPos at = window && pos != null ? windowMiddle(state, pos) : pos;
        if (at != null) {
            for (Painted zeppelin : painted) {
                if (zeppelin.box().isInside(at)) {
                    return zeppelin.colour(at, fabric, window);
                }
            }
        }
        return Livery.SILVER.colour(new Livery.Spot(0, 0, 0, 0, ZeppelinSize.FULL), fabric);
    }

    /**
     * Where the middle of the window a piece of a rocket's oval window is part of goes by: its middle column, in its
     * top row (see {@link OvalWindowBlock}).
     */
    private static BlockPos windowMiddle(BlockState piece, BlockPos pos) {
        Direction left = piece.getValue(OvalWindowBlock.FACING).getClockWise();
        return pos.relative(left, piece.getValue(OvalWindowBlock.COLUMN) - OvalWindowBlock.COLUMNS / 2).above(piece.getValue(OvalWindowBlock.ROW));
    }

    /** The tint the cover and the fabric are drawn with in hand: silver. */
    public static int itemTint(ItemStack stack, int tintIndex) {
        return Livery.SILVER.colour(new Livery.Spot(0, 0, 0, 0, ZeppelinSize.FULL), false);
    }

    /** Once a tick: the zeppelins' paint as their autopilots have it now, and what has to be meshed again for it. */
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            painted = List.of();
            MESHED.clear();
            return;
        }
        List<Painted> now = new ArrayList<>();
        Map<BlockPos, Painted> seen = new HashMap<>();
        for (AutopilotBlockEntity autopilot : AutopilotBlockEntity.loaded(level)) {
            Painted zeppelin = paint(level, autopilot);
            if (zeppelin != null) {
                now.add(zeppelin);
                seen.put(autopilot.getBlockPos().immutable(), zeppelin);
            }
        }
        painted = List.copyOf(now);
        seen.forEach((at, zeppelin) -> remesh(MESHED.get(at), zeppelin));
        MESHED.clear();
        MESHED.putAll(seen);
    }

    /** A zeppelin's or a rocket's paint as its autopilot has it now; null for an airliner's, or one not laid out. */
    @Nullable
    private static Painted paint(ClientLevel level, AutopilotBlockEntity autopilot) {
        Craft craft = autopilot.craft();
        BoundingBox box = craft != Craft.AIRLINER ? autopilot.frame() : null;
        if (box == null) {
            return null;
        }
        long start = autopilot.repaintStart();
        double share = start < 0 ? 1 : Math.min(1, (DrawingClock.gameTime(0) - start) / Livery.REPAINT_TICKS);
        double reached = share >= 1 ? Double.MAX_VALUE : share * sweep(craft, box, autopilot.zeppelinSize());
        Direction nose = autopilot.getBlockState().getValue(AutopilotBlock.FACING);
        List<PaintOverlay> overlays = craft == Craft.ZEPPELIN ? overlays(level, box, autopilot.zeppelinSize()) : List.of();
        return new Painted(craft, box, new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ()), nose,
            autopilot.zeppelinSize(), autopilot.livery(), autopilot.repaintedFrom(), reached, overlays, stories(autopilot));
    }

    /**
     * A rocket's stories' floors, rows up from her fins' feet, as her autopilot keeps them; for one built before it
     * kept them, as one is built at the configured scale.
     */
    private static int[] stories(AutopilotBlockEntity autopilot) {
        int[] kept = autopilot.rocketStories();
        return kept.length > 0 ? kept : RocketSilhouette.stories(SkylinersConfig.SCALE.get());
    }

    /** How far from its nose a repaint sweeps over a ship, in blocks: aft along a zeppelin, down all of a rocket. */
    private static double sweep(Craft craft, BoundingBox box, ZeppelinSize size) {
        return craft == Craft.ROCKET ? box.getYSpan() : Livery.sweep(size);
    }

    /** What the features paint over a zeppelin's livery now, in its box, on a hull of its size. */
    private static List<PaintOverlay> overlays(ClientLevel level, BoundingBox box, ZeppelinSize size) {
        return OVERLAYS.stream().flatMap(overlays -> overlays.overlays(level, box, size).stream()).toList();
    }

    /**
     * Has the sections of a zeppelin meshed again where its paint differs from what was meshed for it: where its
     * livery has changed, and where anything painted over it has come or gone.
     */
    private static void remesh(@Nullable Painted before, Painted after) {
        remeshLivery(before, after);
        List<PaintOverlay> was = before == null ? List.of() : before.overlays();
        remeshGone(was, after.overlays(), after);
        remeshGone(after.overlays(), was, after);
    }

    /** Has a zeppelin meshed again where each of one set of things painted over it is that is not among another. */
    private static void remeshGone(List<PaintOverlay> these, List<PaintOverlay> others, Painted zeppelin) {
        for (PaintOverlay overlay : these) {
            if (!others.contains(overlay)) {
                remesh(zeppelin, overlay.from(), overlay.to());
            }
        }
    }

    /**
     * Has the sections of a zeppelin meshed again where its livery differs from what was meshed for it: the slice the
     * new paint has swept over since, or all of it when it is new to this client, or painted anew at once.
     */
    private static void remeshLivery(@Nullable Painted before, Painted after) {
        double sweep = sweep(after.craft(), after.box(), after.size());
        if (before == null) {
            if (after.livery() != Livery.SILVER || after.before() != Livery.SILVER) {
                remesh(after, 0, sweep);
            }
            return;
        }
        if (before.livery() != after.livery() && after.reached() == Double.MAX_VALUE) {
            remesh(after, 0, sweep);
            return;
        }
        double from = before.livery() == after.livery() ? Math.min(before.reached(), sweep) : 0;
        double ahead = after.craft() == Craft.ROCKET ? DRIP_LONGEST : 0;
        double to = Math.min(after.reached() + ahead, sweep);
        if (to > from) {
            remesh(after, from, to);
        }
    }

    /**
     * Has the sections of a ship meshed again from so far from its nose to so far, in blocks: aft along a zeppelin,
     * down a rocket.
     */
    private static void remesh(Painted zeppelin, double from, double to) {
        BoundingBox box = zeppelin.box();
        if (zeppelin.craft() == Craft.ROCKET) {
            remeshRows(box, box.maxY() + 1 - (int) Math.ceil(to), box.maxY() + 1 - (int) Math.floor(from));
            return;
        }
        Direction nose = zeppelin.nose();
        int tip = nose.getAxisDirection() == Direction.AxisDirection.POSITIVE ? (nose.getAxis() == Direction.Axis.X ? box.maxX() : box.maxZ())
            : (nose.getAxis() == Direction.Axis.X ? box.minX() : box.minZ());
        int sign = nose.getAxisDirection() == Direction.AxisDirection.POSITIVE ? -1 : 1;
        int a = tip + sign * (int) Math.floor(from);
        int b = tip + sign * (int) Math.ceil(to);
        boolean alongX = nose.getAxis() == Direction.Axis.X;
        int x0 = alongX ? Math.min(a, b) : box.minX();
        int x1 = alongX ? Math.max(a, b) : box.maxX();
        int z0 = alongX ? box.minZ() : Math.min(a, b);
        int z1 = alongX ? box.maxZ() : Math.max(a, b);
        for (BlockPos section : BlockPos.betweenClosed(SectionPos.blockToSectionCoord(x0), SectionPos.blockToSectionCoord(box.minY()),
            SectionPos.blockToSectionCoord(z0), SectionPos.blockToSectionCoord(x1), SectionPos.blockToSectionCoord(box.maxY()),
            SectionPos.blockToSectionCoord(z1))) {
            Minecraft.getInstance().levelRenderer.setSectionDirty(section.getX(), section.getY(), section.getZ());
        }
    }

    /** Has the sections of a ship's box meshed again from one row up to another, in blocks. */
    private static void remeshRows(BoundingBox box, int from, int to) {
        int low = SectionPos.blockToSectionCoord(Math.max(from, box.minY()));
        int high = SectionPos.blockToSectionCoord(Math.min(to, box.maxY()));
        for (BlockPos section : BlockPos.betweenClosed(SectionPos.blockToSectionCoord(box.minX()), low, SectionPos.blockToSectionCoord(box.minZ()),
            SectionPos.blockToSectionCoord(box.maxX()), high, SectionPos.blockToSectionCoord(box.maxZ()))) {
            Minecraft.getInstance().levelRenderer.setSectionDirty(section.getX(), section.getY(), section.getZ());
        }
    }
}
