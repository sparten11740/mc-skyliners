package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.BallRackBlock;
import dev.aerolarge.block.GalleyBlock;
import dev.aerolarge.block.HoopBlock;
import dev.aerolarge.block.ScoreboardBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Fits out the main deck of the furnished airliner, front to back between the door aisles: an indoor pool sunk into
 * the hold so its water lies flush with the floor, a full-height gym hall (Turnhalle) over the wing box, and a lounge
 * with a bar in the tail, lit by pendant lights. Everything is vanilla blocks but the lights, the pool's glass and the
 * seats, which are Create's so they can be sat in. It only ever goes into open cabin space or replaces cabin floor, so
 * the doors and the space in front of them stay as they were, and so does the hull, but for the belly under the pool,
 * which is glazed.
 */
final class CabinFitOut {
    private static final double[] POOL = {13.5, 23.0};
    private static final double[] GYM = {27.5, 47.5};
    private static final double[] LOUNGE = {52.0, 58.5};

    private final AirframeLayout layout;
    private final int floor;

    CabinFitOut(AirframeLayout layout) {
        this.layout = layout;
        this.floor = layout.floorY();
    }

    void apply() {
        pool(from(POOL), to(POOL));
        gym(from(GYM), to(GYM));
        lounge(from(LOUNGE), to(LOUNGE));
    }

    /** Whether overhead lockers go along the walls at this station, in metres from the nose: everywhere but the gym hall. */
    static boolean lockersFit(double metres) {
        return metres < GYM[0] || metres > GYM[1];
    }

    private int from(double[] zone) {
        return (int) Math.ceil(zone[0] * layout.scale());
    }

    private int to(double[] zone) {
        return (int) Math.floor(zone[1] * layout.scale());
    }

    /** The narrowest the cabin gets at standing height over a stretch, so a rectangular room fits everywhere in it. */
    private int narrowest(int fromZ, int toZ) {
        int width = Integer.MAX_VALUE;
        for (int z = fromZ; z <= toZ; z++) {
            width = Math.min(width, layout.openHalfWidth(floor + 1, z));
        }
        return width;
    }

    /**
     * The pool: a basin of water flush with the floor, reaching down into the hold, lined with tiles and lit from its
     * walls just under the water line, a quartz rim to stand on, a diving board at the front and steps out at the
     * back. It is a moon pool: its bottom is glass, and so is the belly of the hull under it, so swimmers look straight
     * down through the airliner at the ground, or the clouds, below.
     */
    private void pool(int fromZ, int toZ) {
        int width = narrowest(fromZ, toZ);
        int basin = width - 2;
        if (basin < 1 || toZ - fromZ < 6) {
            return;
        }
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState tile = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
        // Clear glass, the same as the belly's under it, so the two join without a seam.
        BlockState glass = AerolargeRegistry.HULL_GLASS.get().defaultBlockState();
        for (int z = fromZ; z <= toZ; z++) {
            for (int x = -width; x <= width; x++) {
                boolean inBasin = Math.abs(x) <= basin && z >= fromZ + 2 && z <= toZ - 2;
                boolean rim = !inBasin && Math.abs(x) <= basin + 1 && z >= fromZ + 1 && z <= toZ - 1;
                if (inBasin) {
                    layout.refloor(x, floor, z, water);
                    for (int y = floor - 1; y >= floor - 3 && layout.isOpen(x, y, z); y--) {
                        layout.furnish(x, y, z, water);
                    }
                } else {
                    layout.refloor(x, floor, z, rim ? Blocks.SMOOTH_QUARTZ.defaultBlockState() : Blocks.POLISHED_DIORITE.defaultBlockState());
                }
            }
        }
        // Line the basin: every open block in the hold that touches the water becomes glass under it and tile beside
        // it, every fourth one of the walls just under the water line a light, so the water cannot run off into the
        // hold. The lights are cabin lights, so the flight deck dims them with the rest and the pool goes dark at
        // night too.
        BlockState light = AerolargeRegistry.CABIN_LIGHT.get().defaultBlockState();
        for (int z = fromZ + 1; z <= toZ - 1; z++) {
            for (int x = -basin - 1; x <= basin + 1; x++) {
                for (int y = floor - 4; y < floor; y++) {
                    if (layout.isOpen(x, y, z) && touchesWater(x, y, z)) {
                        boolean lit = y == floor - 1 && Math.floorMod(Math.abs(x) + z, 4) == 0;
                        layout.furnish(x, y, z, isWater(x, y + 1, z) ? glass : lit ? light : tile);
                    }
                }
            }
        }
        // The view down: under each column of water, whatever of the hold is still open below the glass bottom is
        // glazed too, and the belly skin it ends on turns to glass, which the shell cuts to the curve with the skin
        // around it. The pool lies between the nose gear and the main gear, so no gear leg meets the belly under it.
        for (int z = fromZ + 2; z <= toZ - 2; z++) {
            for (int x = -basin; x <= basin; x++) {
                int y = floor;
                while (isWater(x, y, z) || layout.stateAt(x, y, z).is(glass.getBlock())) {
                    y--;
                }
                for (; layout.isOpen(x, y, z); y--) {
                    layout.set(x, y, z, glass);
                }
                layout.glaze(x, y, z);
            }
        }
        BlockState board = Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        layout.furnish(0, floor + 1, fromZ + 1, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        layout.furnish(0, floor + 1, fromZ + 2, board);
        layout.furnish(0, floor + 1, fromZ + 3, board);
        // Steps rising toward the back of the pool, out onto the rim.
        BlockState step = Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        for (int x = -1; x <= 1; x++) {
            if (isWater(x, floor, toZ - 2)) {
                layout.set(x, floor, toZ - 2, step);
            }
        }
        for (int x : new int[]{-width, width}) {
            layout.furnish(x, floor + 1, fromZ, Blocks.POTTED_FERN.defaultBlockState());
            layout.furnish(x, floor + 1, toZ, Blocks.POTTED_FERN.defaultBlockState());
        }
        for (int z = fromZ + 2; z <= toZ - 2; z += 5) {
            hangLight(-basin / 2, z, 6);
            hangLight(basin / 2, z, 6);
        }
    }

    private boolean isWater(int x, int y, int z) {
        return layout.stateAt(x, y, z).is(Blocks.WATER);
    }

    private boolean touchesWater(int x, int y, int z) {
        return isWater(x + 1, y, z) || isWater(x - 1, y, z) || isWater(x, y + 1, z)
            || isWater(x, y, z + 1) || isWater(x, y, z - 1);
    }

    /**
     * The gym hall (Turnhalle): the cabin's whole height over a birch parquet court with white lines, a basketball
     * hoop at each end with its scoreboard above and a rack of balls in the corner by it, wall bars and mats along the
     * port wall, climbing ropes and a row of seats along starboard.
     */
    private void gym(int fromZ, int toZ) {
        int width = narrowest(fromZ, toZ);
        if (width < 3 || toZ - fromZ < 10) {
            return;
        }
        int middle = (fromZ + toZ) / 2;
        BlockState parquet = Blocks.BIRCH_PLANKS.defaultBlockState();
        BlockState line = Blocks.WHITE_CONCRETE.defaultBlockState();
        double circle = Math.min(3, width - 1);
        for (int z = fromZ; z <= toZ; z++) {
            for (int x = -width; x <= width; x++) {
                boolean boundary = (Math.abs(x) == width - 1 && z >= fromZ + 1 && z <= toZ - 1)
                    || ((z == fromZ + 1 || z == toZ - 1) && Math.abs(x) <= width - 1);
                boolean centre = z == middle || Math.abs(Math.hypot(x, z - middle) - circle) < 0.5;
                layout.refloor(x, floor, z, boundary || centre ? line : parquet);
            }
        }

        if (layout.ceiling(0, fromZ) >= floor + 7) {
            hoop(fromZ, Direction.SOUTH, HoopBlock.Team.HOME);
            hoop(toZ, Direction.NORTH, HoopBlock.Team.GUEST);
        }
        BlockState rack = AerolargeRegistry.BALL_RACK.get().defaultBlockState().setValue(BallRackBlock.FACING, Direction.EAST);
        layout.furnish(-width, floor + 1, fromZ + 1, rack);
        layout.furnish(-width, floor + 1, toZ - 1, rack);

        // Wall bars (Sprossenwand): ladders up the port wall, following its curve, with mats in front.
        for (int z = middle - 4; z <= middle + 4; z++) {
            for (int y = floor + 1; y <= floor + 5; y++) {
                int x = layout.openHalfWidth(y, z);
                if (x >= 0) {
                    layout.furnish(-x, y, z, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST));
                }
            }
            for (int x = -width + 1; x <= -width + 3; x++) {
                layout.furnish(x, floor + 1, z, Blocks.BLUE_CARPET.defaultBlockState());
            }
        }

        // Climbing ropes hanging from the crown to just above the floor.
        for (int z = middle - 2; z <= middle + 2; z += 2) {
            int x = width - 3;
            for (int y = layout.ceiling(x, z); y >= floor + 2; y--) {
                layout.furnish(x, y, z, Blocks.CHAIN.defaultBlockState());
            }
        }

        BlockState bench = AirframePalette.seat(DyeColor.BLUE).orElse(Blocks.SPRUCE_SLAB.defaultBlockState());
        for (int z = fromZ + 3; z <= toZ - 3; z++) {
            if (Math.abs(z - middle) > 4) {
                layout.furnish(width, floor + 1, z, bench);
            }
        }

        for (int z = fromZ + 3; z <= toZ - 3; z += 6) {
            hangLight(-width / 2, z, 7);
            hangLight(width / 2, z, 7);
        }
    }

    /**
     * A basketball hoop at the end of the court, scoring for the given side: a pole, a white backboard with the ring
     * on it facing the court, and the scoreboard above.
     */
    private void hoop(int z, Direction toward, HoopBlock.Team team) {
        for (int y = floor + 1; y <= floor + 4; y++) {
            layout.furnish(0, y, z, Blocks.IRON_BARS.defaultBlockState());
        }
        for (int x = -1; x <= 1; x++) {
            layout.furnish(x, floor + 5, z, Blocks.WHITE_CONCRETE.defaultBlockState());
            layout.furnish(x, floor + 6, z, Blocks.WHITE_CONCRETE.defaultBlockState());
        }
        layout.furnish(0, floor + 5, z + toward.getStepZ(),
            AerolargeRegistry.HOOP.get().defaultBlockState().setValue(HoopBlock.FACING, toward.getOpposite()).setValue(HoopBlock.TEAM, team));
        layout.furnish(0, floor + 7, z, AerolargeRegistry.SCOREBOARD.get().defaultBlockState().setValue(ScoreboardBlock.FACING, toward));
    }

    /**
     * The lounge: carpet throughout, a dark oak bar along the starboard wall with barrels behind and stools in front,
     * the galley that stocks the cabin crew's trolley at its head, a row of red seats and low tables along the port
     * wall, and a plant in the other corner.
     */
    private void lounge(int fromZ, int toZ) {
        if (toZ - fromZ < 4) {
            return;
        }
        int frontWidth = layout.openHalfWidth(floor + 1, fromZ);
        BlockState sofa = AirframePalette.seat(DyeColor.RED).orElse(Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST));
        // The tail cone narrows through the lounge, so it is fitted station by station, following the walls in.
        for (int z = fromZ; z <= toZ; z++) {
            int width = layout.openHalfWidth(floor + 1, z);
            if (width < 3) {
                continue;
            }
            boolean inner = z > fromZ && z < toZ;
            if (inner) {
                layout.furnish(width, floor + 1, z, Blocks.BARREL.defaultBlockState());
                layout.furnish(width - 1, floor + 1, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
                if ((z - fromZ) % 2 == 1) {
                    layout.furnish(width - 2, floor + 1, z, Blocks.DARK_OAK_FENCE.defaultBlockState());
                }
                layout.furnish(-width, floor + 1, z, sofa);
                if (Math.floorMod(z - fromZ, 4) == 1 || Math.floorMod(z - fromZ, 4) == 2) {
                    layout.furnish(-width + 2, floor + 1, z, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP));
                }
            }
            for (int x = -width; x <= width; x++) {
                layout.furnish(x, floor + 1, z, Blocks.RED_CARPET.defaultBlockState());
            }
        }
        if (frontWidth >= 3) {
            layout.set(-frontWidth, floor + 1, fromZ, Blocks.POTTED_FERN.defaultBlockState());
            layout.set(frontWidth, floor + 1, fromZ, AerolargeRegistry.GALLEY.get().defaultBlockState().setValue(GalleyBlock.FACING, Direction.WEST));
        }
        for (int z = fromZ + 1; z <= toZ; z += 4) {
            hangLight(0, z, 5);
        }
    }

    /** A pendant light hung on its cable from the crown, its bottom the given height above the floor (or as low as fits). */
    private void hangLight(int x, int z, int height) {
        int top = layout.ceiling(x, z);
        int bottom = Math.max(floor + 3, Math.min(floor + height, top));
        if (!layout.isOpen(x, top, z)) {
            return;
        }
        for (int y = top; y > bottom; y--) {
            layout.furnish(x, y, z, AerolargeRegistry.LIGHT_CABLE.get().defaultBlockState());
        }
        layout.furnish(x, bottom, z, AerolargeRegistry.PENDANT_LIGHT.get().defaultBlockState());
    }
}
