package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.BeanBagBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * The greenhouse, among her tallest rooms: a ring of raised moss beds round the shaft, broken by a path out to each
 * window, planted with ferns, flowers, azaleas and bamboo; a tree at each of the four diagonals, two cherries and two
 * azaleas, their crowns spreading over the beds; and glow berry vines hanging from the ceiling, lighting the room warm
 * among the strips' grow light. The swimming pool's glass-bottomed channel hangs through the ceiling from the story
 * over it, so from under the vines swimmers are seen overhead against the light. At the end of each path, a bean bag
 * looks out through the plants.
 */
final class RocketGreenhouse extends RocketRoom {
    /** Her greenhouse's story, from the entry story up. */
    static final int STORY = 4;
    /** How far in from the skin's inside the beds' outer and inner edges are, in blocks. */
    private static final double BEDS_OUT = 2.5;
    private static final double BEDS_IN = 4.5;
    /** How far either side of each of the four ways the path out to its window is, in blocks. */
    private static final int PATH_HALF = 1;
    /** How far in from the skin's inside the trees stand, and how tall their trunks are, in blocks. */
    private static final double TREES_IN = 3.5;
    private static final int TRUNK = 5;
    /** How far out from its trunk a tree's crown spreads, in blocks, and how many rows it rises over its foot. */
    private static final int CROWN = 2;
    private static final int CROWN_ROWS = 3;
    /** How far out from her axis the bean bags at the paths' ends are, in blocks. */
    private static final double BEAN_BAGS_OUT = 4.5;
    /** How tall a stem of bamboo grows, from and to, in blocks, and one in how many blocks of a crown is flowering. */
    private static final int BAMBOO_FROM = 2;
    private static final int BAMBOO_TO = 4;
    private static final int FLOWERING_EVERY = 3;
    /** One in how many blocks of the ceiling has a vine, and how long one hangs at most, in blocks. */
    private static final int VINE_EVERY = 5;
    private static final int VINE_LONGEST = 4;
    /** What grows in the beds, by a spot's own number: some left as bare moss. */
    private static final Block[] PLANTS = {
        Blocks.FERN, Blocks.AZALEA, Blocks.ALLIUM, Blocks.LARGE_FERN, Blocks.CORNFLOWER, Blocks.FLOWERING_AZALEA, Blocks.BAMBOO,
        Blocks.LILY_OF_THE_VALLEY, Blocks.MOSS_CARPET, Blocks.OXEYE_DAISY, Blocks.FERN, Blocks.BLUE_ORCHID, Blocks.AIR};

    RocketGreenhouse(RocketLayout layout) {
        super(layout, STORY);
    }

    @Override
    public void build() {
        int y = floor() + 1;
        for (int round = 45; round < 360; round += 90) {
            boolean cherry = round == 45 || round == 225;
            tree(across(wall() - TREES_IN, round), y, along(wall() - TREES_IN, round), cherry);
        }
        int reach = (int) Math.ceil(wall());
        for (BlockPos at : BlockPos.betweenClosed(-reach, y, -reach, reach, y, reach)) {
            if (inBed(at.getX(), at.getZ()) && free(at.getX(), y, at.getZ())) {
                bed(at.getX(), y, at.getZ());
            }
        }
        for (BlockPos at : BlockPos.betweenClosed(-reach, top(), -reach, reach, top(), reach)) {
            vine(at.getX(), at.getZ());
        }
        for (int round = 0; round < 360; round += 90) {
            put(across(BEAN_BAGS_OUT, round), y, along(BEAN_BAGS_OUT, round), AerolargeRegistry.BEAN_BAG.get().defaultBlockState()
                .setValue(BeanBagBlock.FACING, inward(round).getOpposite()).setValue(BeanBagBlock.COLOR, DyeColor.GREEN));
        }
    }

    /** Whether the beds' ring takes in a spot of the floor plan: between its edges and off the paths to the windows. */
    private boolean inBed(int x, int z) {
        double out = Math.hypot(x, z);
        boolean onPath = Math.abs(x) <= PATH_HALF || Math.abs(z) <= PATH_HALF;
        return out >= wall() - BEDS_IN && out <= wall() - BEDS_OUT && !onPath;
    }

    /** A block of raised bed, moss, and what grows on it, by the spot's own number. */
    private void bed(int x, int y, int z) {
        layout.set(x, y, z, Blocks.MOSS_BLOCK.defaultBlockState());
        Block plant = PLANTS[Math.floorMod(x * 31 + z * 17 + x * z, PLANTS.length)];
        if (plant == Blocks.AIR || !free(x, y + 1, z)) {
            return;
        }
        if (plant == Blocks.LARGE_FERN && free(x, y + 2, z)) {
            layout.set(x, y + 1, z, plant.defaultBlockState());
            layout.set(x, y + 2, z, plant.defaultBlockState().setValue(DoublePlantBlock.HALF, DoubleBlockHalf.UPPER));
        } else if (plant == Blocks.BAMBOO) {
            bamboo(x, y + 1, z);
        } else if (plant != Blocks.LARGE_FERN) {
            layout.set(x, y + 1, z, plant.defaultBlockState());
        }
    }

    /** A stem of bamboo from a block up, as tall as the spot's own number has it and the room above lets it grow. */
    private void bamboo(int x, int y, int z) {
        int tall = BAMBOO_FROM + Math.floorMod(x + 2 * z, BAMBOO_TO - BAMBOO_FROM + 1);
        for (int up = 0; up < tall && free(x, y + up, z); up++) {
            layout.set(x, y + up, z, Blocks.BAMBOO.defaultBlockState());
        }
    }

    /** A tree with its trunk from a block up, out of the bed round it, a cherry's or an azalea's crown on it. */
    private void tree(int x, int y, int z, boolean cherry) {
        if (!free(x, y, z)) {
            return;
        }
        BlockState log = (cherry ? Blocks.CHERRY_LOG : Blocks.OAK_LOG).defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (int up = 0; up < TRUNK && free(x, y + up, z); up++) {
            layout.set(x, y + up, z, log);
        }
        BlockState leaves = (cherry ? Blocks.CHERRY_LEAVES : Blocks.AZALEA_LEAVES).defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        BlockState flowering = cherry ? leaves : Blocks.FLOWERING_AZALEA_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
        int crownFrom = y + TRUNK - 2;
        for (BlockPos at : BlockPos.betweenClosed(x - CROWN, crownFrom, z - CROWN, x + CROWN, crownFrom + CROWN_ROWS, z + CROWN)) {
            if (inCrown(at, x, crownFrom, z) && free(at.getX(), at.getY(), at.getZ())) {
                layout.set(at.getX(), at.getY(), at.getZ(), Math.floorMod(at.getX() + at.getY() + at.getZ(), FLOWERING_EVERY) == 0 ? flowering : leaves);
            }
        }
    }

    /** Whether a block is in the round crown of a tree over a trunk, its corners left off, narrower at its top. */
    private static boolean inCrown(BlockPos at, int x, int crownFrom, int z) {
        int across = Math.abs(at.getX() - x);
        int along = Math.abs(at.getZ() - z);
        int spread = at.getY() == crownFrom + CROWN_ROWS ? CROWN - 1 : CROWN;
        return across + along <= spread + 1 && across <= spread && along <= spread;
    }

    /** At one ceiling block in a few, a glow berry vine hanging down as long as its own number has it, its tip lit. */
    private void vine(int x, int z) {
        int number = Math.floorMod(x * 7 + z * 13 + x * z * 3, VINE_EVERY * VINE_LONGEST);
        if (number % VINE_EVERY != 0 || !free(x, top(), z)) {
            return;
        }
        int lowest = top() - number / VINE_EVERY;
        int tip = top();
        while (tip > lowest && free(x, tip - 1, z)) {
            tip--;
        }
        for (int y = top(); y > tip; y--) {
            layout.set(x, y, z, Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(CaveVinesBlock.BERRIES, (x + y + z) % 2 == 0));
        }
        layout.set(x, tip, z, Blocks.CAVE_VINES.defaultBlockState().setValue(CaveVinesBlock.BERRIES, true).setValue(CaveVinesBlock.AGE, CaveVinesBlock.MAX_AGE));
    }
}
