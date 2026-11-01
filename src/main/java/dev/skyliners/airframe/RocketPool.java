package dev.skyliners.airframe;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.BeanBagBlock;
import dev.skyliners.block.PlanterBlock;
import dev.skyliners.block.PoolCopingBlock;
import dev.skyliners.block.RubberDuckBlock;
import dev.skyliners.block.ShelfUnitBlock;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The swimming pool: a channel of water two deep curving round the shaft from one side of her front to the other by
 * way of her back, sunk into the floor, its coping round its edges and the rest of the floor laid with non-slip deck
 * tiles. Its walls are glass mosaic lit from under the water line, and its bottom is clear glass: the channel hangs
 * into the greenhouse's ceiling under it, and from among the leaves there swimmers are seen overhead. Rubber ducks
 * float round it. The deck at her front, between the channel's two ends, is the sunbathers': on her right, loungers
 * looking out of the front window with a side table between them; on her left, the wave machine by the way in from the
 * lift, to set the channel churning (see {@link dev.skyliners.block.WaveMachineBlock}), and a towel shelf beyond it;
 * and bamboo in planters, the pool's palms, at the channel's ends.
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
    static final int DEPTH = 2;
    /** How often a block of its walls just under the water line is a light, in blocks. */
    private static final int LIGHT_EVERY = 4;
    /** How far round from her front the ducks float, in degrees, and how far in from the skin's inside, in blocks. */
    private static final double[] DUCKS = {100, 180, 260};
    private static final double DUCKS_IN = 3;
    /** The loungers' colours, taken in turn. */
    private static final DyeColor[] LOUNGERS = {DyeColor.WHITE, DyeColor.LIGHT_BLUE, DyeColor.YELLOW, DyeColor.WHITE};
    /** How far out from the walkway the room's corner reaches, in blocks, from which a lounger stands out at it too. */
    private static final int CORNER_LOUNGER = 2;

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
        RandomSource random = scatter();
        int corner = corner();
        loungers(RocketQuarter.FRONT_RIGHT, floor + 1, corner, random);
        towels(RocketQuarter.FRONT_LEFT, floor + 1);
        standing(RocketQuarter.FRONT_LEFT.x(RocketQuarter.INNER - 2), floor + 1, RocketQuarter.FRONT_LEFT.z(RocketQuarter.INNER),
            SkylinersRegistry.WAVE_MACHINE.get().defaultBlockState());
        for (RocketQuarter quarter : new RocketQuarter[]{RocketQuarter.FRONT_RIGHT, RocketQuarter.FRONT_LEFT}) {
            palm(quarter, floor + 1, corner);
        }
    }

    /** Whether the channel runs through a spot of the floor plan: in from the skin, clear of the walkway, round. */
    private boolean inChannel(int x, int z) {
        double round = roundOf(x, z);
        return round >= FROM && round <= TO && offTheWays(x, z, INNER_CLEAR) && inRoom(x, floor(), z, 0)
            && layout.isOpen(x, floor() - 1, z) && layout.isOpen(x, floor() - DEPTH, z);
    }

    /**
     * The channel's water at a spot, from the floor down, and its glass bottom under it: clear glass that is cut out
     * rather than see-through, so that, seen from the greenhouse under it, it never hides the water over it, as two
     * see-through layers do wherever the nearer is drawn first, as a ship in flight may draw them.
     */
    private void dig(int x, int z) {
        for (int y = floor() - DEPTH + 1; y <= floor(); y++) {
            layout.set(x, y, z, Blocks.WATER.defaultBlockState());
            water.add(new BlockPos(x, y, z));
        }
        layout.set(x, floor() - DEPTH, z, Blocks.GLASS.defaultBlockState());
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
        if (y == floor() && layout.stateAt(x, y, z).is(SkylinersRegistry.CABIN_FLOOR.get())) {
            layout.set(x, y, z, toWater == null ? SkylinersRegistry.POOL_DECK_TILE.get().defaultBlockState() : coping(at, toWater));
        } else if (y < floor() && toWater != null && layout.isOpen(x, y, z)) {
            boolean light = y == floor() - 1 && Math.floorMod(x + z, LIGHT_EVERY) == 0;
            layout.set(x, y, z, (light ? SkylinersRegistry.CABIN_LIGHT : SkylinersRegistry.POOL_MOSAIC).get().defaultBlockState());
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
        BlockState coping = SkylinersRegistry.POOL_COPING.get().defaultBlockState();
        boolean twoSides = water.contains(at.relative(toWater.getClockWise())) || water.contains(at.relative(toWater.getCounterClockWise()));
        return twoSides ? coping.setValue(PoolCopingBlock.CORNER, true) : coping.setValue(PoolCopingBlock.FACING, toWater);
    }

    /** A rubber duck afloat on the water at a spot, facing the way given, where the channel runs. */
    private void duck(int x, int z, Direction facing) {
        if (water.contains(new BlockPos(x, floor(), z))) {
            layout.set(x, floor(), z, SkylinersRegistry.RUBBER_DUCK.get().defaultBlockState().setValue(RubberDuckBlock.FACING, facing)
                .setValue(RubberDuckBlock.WATERLOGGED, true));
        }
    }

    /**
     * The sunbathers' corner: loungers in a row looking out of the front window, a low table between the first two, one
     * of the others turned toward the water; as many as the deck has room for, out from beside the way in; and where
     * the deck reaches far enough, an orange one out at its corner by the window.
     */
    private void loungers(RocketQuarter quarter, int y, int corner, RandomSource random) {
        int along = RocketQuarter.INNER + 1;
        int turned = 1 + random.nextInt(LOUNGERS.length - 1);
        for (int lounger = 0; lounger < LOUNGERS.length; lounger++) {
            int across = RocketQuarter.INNER - 2 + 2 * lounger;
            Direction facing = lounger == turned ? quarter.side() : quarter.end();
            standing(quarter.x(across), y, quarter.z(along), SkylinersRegistry.BEAN_BAG.get().defaultBlockState()
                .setValue(BeanBagBlock.FACING, facing).setValue(BeanBagBlock.COLOR, LOUNGERS[lounger]));
        }
        standing(quarter.x(RocketQuarter.INNER - 1), y, quarter.z(along), SkylinersRegistry.LOUNGE_TABLE.get().defaultBlockState());
        if (corner - RocketQuarter.INNER >= CORNER_LOUNGER) {
            standing(quarter.x(RocketQuarter.INNER - 1), y, quarter.z(corner), SkylinersRegistry.BEAN_BAG.get().defaultBlockState()
                .setValue(BeanBagBlock.FACING, quarter.end()).setValue(BeanBagBlock.COLOR, DyeColor.ORANGE));
        }
    }

    /**
     * A shelf of rolled towels beside the way in from the lift, facing it, two long, a step on from the wave machine,
     * which stands first beside the way in where whoever comes out of the lift sees and reaches it.
     */
    private void towels(RocketQuarter quarter, int y) {
        BlockState shelf = ShelfUnitBlock.stocked(SkylinersRegistry.SHELF_UNIT.get(), ShelfUnitBlock.Kind.TOWELS, quarter.side().getOpposite());
        for (int along = RocketQuarter.INNER + 1; along <= RocketQuarter.INNER + 2; along++) {
            standing(quarter.x(RocketQuarter.INNER - 2), y, quarter.z(along), shelf);
        }
    }

    /**
     * Bamboo in a planter, a palm by the pool, at the first of a few spots on the deck by the channel's end that is
     * free.
     */
    private void palm(RocketQuarter quarter, int y, int corner) {
        BlockState planter = PlanterBlock.planted(SkylinersRegistry.PLANTER.get(), PlanterBlock.Plant.BAMBOO, quarter.end().getOpposite());
        int[][] spots = {{RocketQuarter.INNER, RocketQuarter.INNER}, {RocketQuarter.INNER - 1, corner + 1}, {RocketQuarter.INNER - 2, corner + 1},
            {RocketQuarter.INNER - 1, RocketQuarter.INNER}};
        for (int[] spot : spots) {
            if (standing(quarter.x(spot[0]), y, quarter.z(spot[1]), planter)) {
                return;
            }
        }
    }
}
