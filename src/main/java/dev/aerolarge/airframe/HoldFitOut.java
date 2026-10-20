package dev.aerolarge.airframe;

import dev.aerolarge.Aerolarge;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.CargoCrateBlock;
import dev.aerolarge.block.CargoFloorBlock;
import dev.aerolarge.block.CargoNetBlock;
import dev.aerolarge.block.SuitcaseBlock;
import dev.aerolarge.block.SuitcaseBlockEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The furnished airliner's hold, between the moon pool and the drop bay: luggage piled up both sides in bays, each
 * behind a cargo net from the floor to the ceiling, suitcases in four colours with care packages packed with supplies
 * among them, cargo floor along both edges of the walkway down the middle, its tie-down rails along the nets, and ball
 * mats round the drop bay; the walkway stays clear all the way from the pool to the bay, and on from the bay to the
 * hatch and the cargo door. Three golden suitcases are hidden about the hold: one in each pile and one at the far end
 * of the hold aft of the cargo door. Under the walkway, a stretch of the belly is a sky window. It only goes into open
 * hold space, bar the window, which glazes the belly as under the moon pool, and is left out where the hold is too low
 * to walk in.
 */
final class HoldFitOut {
    /** Kept clear of luggage forward of the drop bay, in metres, so it can be loaded from all round. */
    private static final double BAY_CLEARANCE = 2;
    /** How many rows of luggage are piled up the hold's sides, above its floor. */
    private static final int PILE_ROWS = 3;
    /** The luggage is stowed in bays this many stations long, a station left clear between them. */
    private static final int BAY_LENGTH = 4;
    /** The hold has to be at least this many rows tall to stow luggage in: at the smallest scales it is a crawlspace. */
    private static final int LEAST_HEIGHT = 3;
    /** A player's height in blocks: the last golden suitcase is hidden as far aft as there is room to stand. */
    private static final int STANDING_ROOM = 2;
    /**
     * The sky window's stretch of the belly, in metres from the nose: just aft of the moon pool to a little ahead of
     * the beacon under the belly, so well ahead of the main gear behind the centre of mass and of the drop bay and the
     * cargo door behind that.
     */
    private static final double[] SKY_WINDOW = {25, AirframeShape.BEACON_STATION - 2};
    /** How many columns in from the cargo floor along the walkway's edges the sky window's edge is. */
    private static final int WINDOW_INSET = 1;
    private static final DyeColor[] COLOURS = SuitcaseBlock.COLOR.getPossibleValues().toArray(DyeColor[]::new);
    private static final ResourceKey<LootTable> SUITCASE = loot("suitcase");
    private static final ResourceKey<LootTable> GOLDEN_SUITCASE = loot("golden_suitcase");
    private static final ResourceKey<LootTable> CARE_PACKAGE = loot("care_package");

    private final AirframeLayout layout;
    private final int floor;

    HoldFitOut(AirframeLayout layout) {
        this.layout = layout;
        this.floor = layout.floorY();
    }

    private static ResourceKey<LootTable> loot(String name) {
        return ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "chests/" + name));
    }

    void apply() {
        BlockPos bay = layout.bay();
        if (bay == null || floor - (bay.getY() + 1) < LEAST_HEIGHT) {
            return;
        }
        int bottom = bay.getY() + 1;
        // From the bay forward to the moon pool's wall, the first station of the hold left clear against it.
        int fore = bay.getZ();
        while (layout.isOpen(0, bottom, fore - 1)) {
            fore--;
        }
        int from = fore + 1;
        int to = bay.getZ() - (int) Math.ceil(BAY_CLEARANCE * layout.scale());
        if (to - from < BAY_LENGTH) {
            return;
        }
        // Measured while the hold is still empty, before the cargo floor narrows the walkway.
        skyWindow(bottom, from, to);
        List<BlockPos> starboard = new ArrayList<>();
        List<BlockPos> port = new ArrayList<>();
        List<BlockPos> nets = new ArrayList<>();
        for (int z = from; z <= to; z++) {
            stow(z, z - from, bottom, starboard, port, nets);
        }
        lace(nets);
        // One golden suitcase a third of the way along the port pile, halfway up it, and one two thirds of the way
        // along the starboard pile, on top; the last at the far end of the hold.
        hide(port, from + (to - from) / 3, bottom + (PILE_ROWS + 1) / 2, 1);
        hide(starboard, from + 2 * (to - from) / 3, Math.min(floor - 1, bottom + PILE_ROWS), 2);
        BlockPos end = farEnd(bay, bottom);
        if (end.getZ() > bay.getZ()) {
            golden(end, Direction.NORTH, 3);
        }
        // Last, as the far end is found along the bare floor down the middle.
        apron(bay);
    }

    /** Ball mats round the drop bay, where loads are turned onto it, wherever the hold's floor there is plain. */
    private void apron(BlockPos bay) {
        int y = bay.getY() + 1;
        for (int x = -1; x <= 1; x++) {
            for (int z = bay.getZ() - 1; z <= bay.getZ() + 1; z++) {
                if ((x != 0 || z != bay.getZ()) && layout.isPlainHull(x, y - 1, z)) {
                    layout.furnish(x, y, z, AerolargeRegistry.BALL_MAT.get().defaultBlockState());
                }
            }
        }
    }

    /**
     * The sky window: a stretch of the belly under the walkway, inside its cargo floor, glazed with clear hull
     * glass, as under the moon pool, to lie on and look straight down through at the ground or the clouds. The hold's
     * lights set into the belly stay, between its panes. Glazed before the shell pass, which cuts it to the curve.
     */
    private void skyWindow(int bottom, int from, int to) {
        int first = Math.max(from, (int) Math.ceil(SKY_WINDOW[0] * layout.scale()));
        int last = Math.min(to, (int) Math.floor(SKY_WINDOW[1] * layout.scale()));
        for (int z = first; z <= last; z++) {
            int edge = 0;
            while (layout.isOpen(edge + 1, bottom, z)) {
                edge++;
            }
            int half = edge - 1 - WINDOW_INSET;
            for (int x = -half; x <= half; x++) {
                if (layout.isOpen(x, bottom, z) && layout.isPlainHull(x, bottom - 1, z)) {
                    layout.glaze(x, bottom - 1, z);
                }
            }
        }
    }

    /**
     * The far end of the hold aft of the bay, past the cargo door, where its floor has climbed the belly's curve a
     * step at a time: the last spot down the middle with room to stand.
     */
    private BlockPos farEnd(BlockPos bay, int bottom) {
        int y = bottom;
        int z = bay.getZ();
        while (true) {
            int next = y;
            while (next < floor && !layout.isOpen(0, next, z + 1)) {
                next++;
            }
            if (next > y + 1 || floor - next < STANDING_ROOM) {
                return new BlockPos(0, y, z);
            }
            y = next;
            z++;
        }
    }

    /**
     * One station of the hold: on each side, cargo floor along the walkway, and unless the station is a gap between
     * bays, the bay's cargo net along the edge of the floor, from the floor to the ceiling, and the luggage piled
     * behind it up the hull's curve; the net goes on the list of nets, and the suitcases on the side's.
     */
    private void stow(int z, int along, int bottom, List<BlockPos> starboard, List<BlockPos> port, List<BlockPos> nets) {
        boolean gap = along % (BAY_LENGTH + 1) == BAY_LENGTH;
        boolean crates = along / (BAY_LENGTH + 1) % 2 == 0;
        for (int side : new int[]{1, -1}) {
            int edge = 0;
            while (layout.isOpen(side * (edge + 1), bottom, z)) {
                edge++;
            }
            if (edge > 1) {
                layout.furnish(side * (edge - 1), bottom, z, AerolargeRegistry.CARGO_FLOOR.get().defaultBlockState()
                    .setValue(CargoFloorBlock.FACING, side > 0 ? Direction.EAST : Direction.WEST));
            }
            if (gap) {
                continue;
            }
            for (int y = bottom; y < floor; y++) {
                if (layout.isOpen(side * edge, y, z)) {
                    layout.set(side * edge, y, z, AerolargeRegistry.CARGO_NET.get().defaultBlockState());
                    nets.add(new BlockPos(side * edge, y, z));
                }
            }
            Direction facing = side > 0 ? Direction.WEST : Direction.EAST;
            for (int y = bottom + 1; y <= Math.min(floor - 1, bottom + PILE_ROWS); y++) {
                for (int x = side * (edge + 1); layout.isOpen(x, y, z); x += side) {
                    if (crates && y == bottom + 1 && x == side * (edge + 1)) {
                        crate(new BlockPos(x, y, z), facing);
                    } else {
                        suitcase(new BlockPos(x, y, z), facing, COLOURS[Math.floorMod(3 * x + 5 * y + 7 * z, COLOURS.length)]);
                        (side > 0 ? starboard : port).add(new BlockPos(x, y, z));
                    }
                }
            }
        }
    }

    /**
     * Joins the bays' nets as each would join what is round it put up by hand (see {@link CargoNetBlock}): along the
     * bay and from the floor to the ceiling, each bay's net one net, its track along the top and its foot along the
     * floor, an edge strap down either end. The build sets the blocks as they are laid, so this is what stands.
     */
    private void lace(List<BlockPos> nets) {
        CargoNetBlock net = AerolargeRegistry.CARGO_NET.get();
        BlockGetter reader = new LayoutReader();
        for (BlockPos at : nets) {
            layout.set(at.getX(), at.getY(), at.getZ(), net.joined(layout.stateAt(at.getX(), at.getY(), at.getZ()), reader, at));
        }
    }

    /** The layout read as a level, for a block to work out how it joins what is round it. */
    private final class LayoutReader implements BlockGetter {
        @Override
        public BlockState getBlockState(BlockPos pos) {
            return layout.stateAt(pos.getX(), pos.getY(), pos.getZ());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        /** The layout has no build limits: every row of it is in. */
        @Override
        public int getHeight() {
            return Integer.MAX_VALUE;
        }

        @Override
        public int getMinBuildHeight() {
            return Integer.MIN_VALUE / 2;
        }
    }

    /** A care package, packed with supplies when the airliner is built, so it can be loaded into the bay and dropped. */
    private void crate(BlockPos at, Direction facing) {
        layout.set(at.getX(), at.getY(), at.getZ(), AerolargeRegistry.CARGO_CRATE.get().defaultBlockState().setValue(CargoCrateBlock.FACING, facing));
        layout.fittings().put(at, (blockEntity, rotation) -> {
            if (blockEntity instanceof RandomizableContainerBlockEntity crate) {
                crate.setLootTable(CARE_PACKAGE, 0);
                crate.unpackLootTable(null);
            }
        });
    }

    private void suitcase(BlockPos at, Direction facing, DyeColor colour) {
        layout.set(at.getX(), at.getY(), at.getZ(), AerolargeRegistry.SUITCASE.get().defaultBlockState()
            .setValue(SuitcaseBlock.FACING, facing).setValue(SuitcaseBlock.COLOR, colour));
        layout.fittings().put(at, (blockEntity, rotation) -> {
            if (blockEntity instanceof SuitcaseBlockEntity suitcase) {
                suitcase.pack(SUITCASE, 0);
            }
        });
    }

    /**
     * Swaps a suitcase of the pile for golden suitcase number {@code index}: the one nearest the station, then nearest
     * the row, then the furthest out, the deepest in the pile.
     */
    private void hide(List<BlockPos> pile, int z, int row, int index) {
        pile.stream()
            .min(Comparator.comparingInt((BlockPos at) -> Math.abs(at.getZ() - z))
                .thenComparingInt(at -> Math.abs(at.getY() - row))
                .thenComparingInt(at -> -Math.abs(at.getX())))
            .ifPresent(at -> golden(at, layout.stateAt(at.getX(), at.getY(), at.getZ()).getValue(SuitcaseBlock.FACING), index));
    }

    private void golden(BlockPos at, Direction facing, int index) {
        layout.set(at.getX(), at.getY(), at.getZ(), AerolargeRegistry.GOLDEN_SUITCASE.get().defaultBlockState().setValue(SuitcaseBlock.FACING, facing));
        layout.fittings().put(at, (blockEntity, rotation) -> {
            if (blockEntity instanceof SuitcaseBlockEntity suitcase) {
                suitcase.pack(GOLDEN_SUITCASE, index);
            }
        });
    }
}
