package dev.aerolarge.client;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.airframe.Livery;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.Craft;
import dev.aerolarge.block.AutopilotBlock;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The zeppelins as this client paints them, each in its livery (see {@link Livery}): the tint each block of a cover
 * and of the fabric is drawn with, from the livery of the zeppelin whose box it lies in, silver where it is in none.
 * Each tick it takes a fresh look at the zeppelins' autopilots, as their blocks send them, into a list the threads
 * that mesh the world's sections read as it stands; and as a repaint sweeps along a zeppelin, from its nose to its
 * tail, it has the sections the new paint has reached since the tick before meshed again, so the paint moves along it
 * a slice at a time, and the rest is never meshed again for it.
 */
public final class ZeppelinPaint {
    /** A zeppelin as this client paints it, and how far aft of its nose the new paint has reached, in blocks. */
    private record Painted(BoundingBox box, BlockPos min, BlockPos max, Direction nose, Livery livery, Livery before, double reached) {
        int colour(BlockPos pos, boolean fabric) {
            Livery.Spot spot = Livery.Spot.of(pos, min, max, nose);
            return (spot.along() < reached ? livery : before).colour(spot, fabric);
        }
    }

    /** How far past the hull's length a repaint sweeps, in blocks: over the fins, the last of it. */
    private static final double SWEEP_LENGTH = 520;

    /** The zeppelins as last seen, read by the meshing threads while the client's thread puts the next in its place. */
    private static volatile List<Painted> painted = List.of();
    /** The same, by where each one's autopilot is: what was meshed for it, to mesh what changes. */
    private static final Map<BlockPos, Painted> MESHED = new HashMap<>();

    private ZeppelinPaint() {
    }

    /** The tint a block of a zeppelin's cover or fabric is drawn with where it stands. */
    public static int tint(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        boolean fabric = state.is(AerolargeRegistry.ZEPPELIN_FABRIC.get());
        if (pos != null) {
            for (Painted zeppelin : painted) {
                if (zeppelin.box().isInside(pos)) {
                    return zeppelin.colour(pos, fabric);
                }
            }
        }
        return Livery.SILVER.colour(new Livery.Spot(0, 0, 0, 0), fabric);
    }

    /** The tint the cover and the fabric are drawn with in hand: silver. */
    public static int itemTint(ItemStack stack, int tintIndex) {
        return Livery.SILVER.colour(new Livery.Spot(0, 0, 0, 0), false);
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
            Painted zeppelin = paint(autopilot, level.getGameTime());
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

    /** A zeppelin's paint as its autopilot has it at a game time; null for an airliner's, or one not built from a layout. */
    @Nullable
    private static Painted paint(AutopilotBlockEntity autopilot, long time) {
        BoundingBox box = autopilot.craft() == Craft.ZEPPELIN ? autopilot.frame() : null;
        if (box == null) {
            return null;
        }
        long start = autopilot.repaintStart();
        double share = start < 0 ? 1 : Math.min(1, (time - start) / (double) Livery.REPAINT_TICKS);
        double reached = share >= 1 ? Double.MAX_VALUE : share * SWEEP_LENGTH;
        Direction nose = autopilot.getBlockState().getValue(AutopilotBlock.FACING);
        return new Painted(box, new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ()), nose,
            autopilot.livery(), autopilot.repaintedFrom(), reached);
    }

    /**
     * Has the sections of a zeppelin meshed again where its paint differs from what was meshed for it: the slice the
     * new paint has swept over since, or all of it when it is new to this client, or painted anew at once.
     */
    private static void remesh(@Nullable Painted before, Painted after) {
        if (before == null) {
            if (after.livery() != Livery.SILVER || after.before() != Livery.SILVER) {
                remesh(after, 0, SWEEP_LENGTH);
            }
            return;
        }
        if (before.livery() != after.livery() && after.reached() == Double.MAX_VALUE) {
            remesh(after, 0, SWEEP_LENGTH);
            return;
        }
        double from = before.livery() == after.livery() ? Math.min(before.reached(), SWEEP_LENGTH) : 0;
        double to = Math.min(after.reached(), SWEEP_LENGTH);
        if (to > from) {
            remesh(after, from, to);
        }
    }

    /** Has the sections of a zeppelin meshed again from so far aft of its nose to so far, in blocks. */
    private static void remesh(Painted zeppelin, double from, double to) {
        BoundingBox box = zeppelin.box();
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
}
