package dev.aerolarge.airframe;

import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Fits out the main deck of the furnished airliner, front to back between the door aisles: an indoor pool sunk into
 * the hold so its water lies flush with the floor, a full-height gym hall (Turnhalle) over the wing box, and a lounge
 * with a bar in the tail. Everything is vanilla blocks but the seats, which are Create's so they can be sat in, and
 * only ever goes into open cabin space or replaces cabin floor, so the hull, the doors and the space in front of them
 * stay as they were.
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
     * The pool: a basin of water flush with the floor, reaching down into the hold, lined with tiles and lit from
     * below, a quartz rim to stand on, a diving board at the front and steps out at the back.
     */
    private void pool(int fromZ, int toZ) {
        int width = narrowest(fromZ, toZ);
        int basin = width - 2;
        if (basin < 1 || toZ - fromZ < 6) {
            return;
        }
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState tile = Blocks.LIGHT_BLUE_CONCRETE.defaultBlockState();
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
        // Line the basin: every open block in the hold that touches the water becomes tile, every fourth one on
        // the bottom a light, so the water cannot run off into the hold.
        for (int z = fromZ + 1; z <= toZ - 1; z++) {
            for (int x = -basin - 1; x <= basin + 1; x++) {
                for (int y = floor - 4; y < floor; y++) {
                    if (layout.isOpen(x, y, z) && touchesWater(x, y, z)) {
                        boolean light = isWater(x, y + 1, z) && Math.floorMod(x + z, 4) == 0;
                        layout.furnish(x, y, z, light ? Blocks.SEA_LANTERN.defaultBlockState() : tile);
                    }
                }
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
     * hoop at each end, wall bars and mats along the port wall, climbing ropes and a row of seats along starboard.
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
            hoop(fromZ, Direction.SOUTH);
            hoop(toZ, Direction.NORTH);
        }

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

    /** A basketball hoop at the end of the court: a pole, a white backboard and a hopper for the ring. */
    private void hoop(int z, Direction toward) {
        for (int y = floor + 1; y <= floor + 4; y++) {
            layout.furnish(0, y, z, Blocks.IRON_BARS.defaultBlockState());
        }
        for (int x = -1; x <= 1; x++) {
            layout.furnish(x, floor + 5, z, Blocks.WHITE_CONCRETE.defaultBlockState());
            layout.furnish(x, floor + 6, z, Blocks.WHITE_CONCRETE.defaultBlockState());
        }
        layout.furnish(0, floor + 5, z + toward.getStepZ(), Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
    }

    /**
     * The lounge: carpet throughout, a dark oak bar along the starboard wall with barrels behind and stools in front,
     * a row of red seats and low tables along the port wall, and plants in the corners.
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
            layout.set(frontWidth, floor + 1, fromZ, Blocks.POTTED_FERN.defaultBlockState());
        }
        for (int z = fromZ + 1; z <= toZ; z += 4) {
            hangLight(0, z, 5);
        }
    }

    /** A lantern hung on a chain from the crown, its bottom the given height above the floor (or as low as fits). */
    private void hangLight(int x, int z, int height) {
        int top = layout.ceiling(x, z);
        int bottom = Math.max(floor + 3, Math.min(floor + height, top));
        if (!layout.isOpen(x, top, z)) {
            return;
        }
        for (int y = top; y > bottom; y--) {
            layout.furnish(x, y, z, Blocks.CHAIN.defaultBlockState());
        }
        layout.furnish(x, bottom, z, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
    }
}
