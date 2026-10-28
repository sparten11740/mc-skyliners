package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.BeanBagBlock;
import dev.aerolarge.block.PoolCopingBlock;
import dev.aerolarge.block.RubberDuckBlock;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The swimming pool: a channel of water two deep curving round the shaft from one side of her front to the other by
 * way of her back, sunk into the floor, its coping round its edges and the rest of the floor laid with non-slip deck
 * tiles. Its walls are glass mosaic lit from under the water line, and its bottom is clear glass: the channel hangs
 * into the greenhouse's ceiling under it, and from among the leaves there swimmers are seen overhead. Rubber ducks
 * float round it; on the deck at her front, between the channel's two ends, bean bags look out of the front window.
 */
final class RocketPool extends RocketRoom {
    /** Her swimming pool's story, from the entry story up. */
    static final int STORY = 5;
    /** How far round from her front the channel runs from and to, in degrees, by way of her back. */
    private static final double FROM = 48;
    private static final double TO = 312;
    /** How many blocks clear of the walkway round the shaft its inner edge is: a block for the coping. */
    private static final int INNER_CLEAR = 1;
    /** How deep the water is, in blocks, its top row the floor's. */
    private static final int DEPTH = 2;
    /** How often a block of its walls just under the water line is a light, in blocks. */
    private static final int LIGHT_EVERY = 4;
    /** How far round from her front the ducks float, in degrees, and how far in from the skin's inside, in blocks. */
    private static final double[] DUCKS = {100, 180, 260};
    private static final double DUCKS_IN = 3;
    /** How far round from her front the bean bags on the deck are, in degrees, and how far in from the skin. */
    private static final double[] BEAN_BAGS = {18, 34, 326, 342};
    private static final double BEAN_BAGS_IN = 3;

    private final Set<BlockPos> water = new HashSet<>();

    RocketPool(RocketLayout layout) {
        super(layout, STORY);
    }

    @Override
    public void build() {
        int floor = floor();
        int reach = (int) Math.ceil(wall());
        for (BlockPos at : BlockPos.betweenClosed(-reach, floor, -reach, reach, floor, reach)) {
            if (inChannel(at.getX(), at.getZ())) {
                dig(at.getX(), at.getZ());
            }
        }
        for (BlockPos at : BlockPos.betweenClosed(-reach, floor - DEPTH + 1, -reach, reach, floor, reach)) {
            line(at.getX(), at.getY(), at.getZ());
        }
        for (double round : DUCKS) {
            duck(across(wall() - DUCKS_IN, round), along(wall() - DUCKS_IN, round), clockwise(round));
        }
        double out = wall() - BEAN_BAGS_IN;
        for (double round : BEAN_BAGS) {
            put(across(out, round), floor + 1, along(out, round), AerolargeRegistry.BEAN_BAG.get().defaultBlockState()
                .setValue(BeanBagBlock.FACING, inward(round).getOpposite()).setValue(BeanBagBlock.COLOR, DyeColor.WHITE));
        }
    }

    /** Whether the channel runs through a spot of the floor plan: in from the skin, clear of the walkway, round. */
    private boolean inChannel(int x, int z) {
        double round = roundOf(x, z);
        return round >= FROM && round <= TO && offTheWays(x, z, INNER_CLEAR) && inRoom(x, floor(), z, 0)
            && layout.isOpen(x, floor() - 1, z) && layout.isOpen(x, floor() - DEPTH, z);
    }

    /** The channel's water at a spot, from the floor down, and its glass bottom under it. */
    private void dig(int x, int z) {
        for (int y = floor() - DEPTH + 1; y <= floor(); y++) {
            layout.set(x, y, z, Blocks.WATER.defaultBlockState());
            water.add(new BlockPos(x, y, z));
        }
        layout.set(x, floor() - DEPTH, z, AerolargeRegistry.HULL_GLASS.get().defaultBlockState());
    }

    /**
     * What a block beside the channel becomes: at the floor, deck tiles, or coping where it meets the water; under the
     * floor, the channel's mosaic wall, a light every so often.
     */
    private void line(int x, int y, int z) {
        BlockPos at = new BlockPos(x, y, z);
        if (water.contains(at)) {
            return;
        }
        Direction toWater = toWater(at);
        if (y == floor() && layout.stateAt(x, y, z).is(AerolargeRegistry.CABIN_FLOOR.get())) {
            layout.set(x, y, z, toWater == null ? AerolargeRegistry.POOL_DECK_TILE.get().defaultBlockState() : coping(at, toWater));
        } else if (y < floor() && toWater != null && layout.isOpen(x, y, z)) {
            boolean light = y == floor() - 1 && Math.floorMod(x + z, LIGHT_EVERY) == 0;
            layout.set(x, y, z, (light ? AerolargeRegistry.CABIN_LIGHT : AerolargeRegistry.POOL_MOSAIC).get().defaultBlockState());
        }
    }

    /** The way from a block to water beside it, if any is beside it. */
    private Direction toWater(BlockPos at) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (water.contains(at.relative(side))) {
                return side;
            }
        }
        return null;
    }

    /** The coping at a block beside the water: its round edge over it, or a corner where water lies on two sides. */
    private BlockState coping(BlockPos at, Direction toWater) {
        BlockState coping = AerolargeRegistry.POOL_COPING.get().defaultBlockState();
        boolean twoSides = water.contains(at.relative(toWater.getClockWise())) || water.contains(at.relative(toWater.getCounterClockWise()));
        return twoSides ? coping.setValue(PoolCopingBlock.CORNER, true) : coping.setValue(PoolCopingBlock.FACING, toWater);
    }

    /** A rubber duck afloat on the water at a spot, facing the way given, where the channel runs. */
    private void duck(int x, int z, Direction facing) {
        if (water.contains(new BlockPos(x, floor(), z))) {
            layout.set(x, floor(), z, AerolargeRegistry.RUBBER_DUCK.get().defaultBlockState().setValue(RubberDuckBlock.FACING, facing)
                .setValue(RubberDuckBlock.WATERLOGGED, true));
        }
    }
}
