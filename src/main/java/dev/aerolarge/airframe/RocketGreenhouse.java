package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.airframe.GreenhousePlan.Bed;
import dev.aerolarge.airframe.GreenhousePlan.Cell;
import dev.aerolarge.airframe.GreenhousePlan.Planting;
import dev.aerolarge.airframe.GreenhousePlan.Spot;
import dev.aerolarge.block.BeanBagBlock;
import dev.aerolarge.block.GardenBedBlock;
import dev.aerolarge.block.GrowLightBlock;
import dev.aerolarge.block.IrrigationValveBlock;
import dev.aerolarge.block.SprinklerBlock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The greenhouse, among her tallest rooms, a kitchen garden someone tends (laid out by {@link GreenhousePlan}): paths
 * out from the walkway to its windows cut it into four quarters, and in each the raised beds stand in rows a path
 * apart, a quarter turn from the quarter before, no two rows ending alike. Their soil is moist from the sprinklers among
 * them, and they grow carrots, potatoes, beetroots and wheat at every stage, half a bed sometimes another vegetable,
 * berry bushes, pumpkins and melons on their vines, or flowers, a gap here and there where something was picked; a
 * grow light hangs over each on its cable, bright magenta. By the way out from the lift stands the irrigation valve
 * that sets every sprinkler going. A cherry and an azalea grow among the beds, two cherries in a bigger greenhouse; in
 * a corner a composter, a water butt, a barrel and a potting bench with pots on them; herb pots and a bean bag on the
 * spots left over; moss over the paths in patches; and glow berry vines hang from the ceiling, lighting the room warm.
 * The swimming pool's glass-bottomed channel hangs through the ceiling from the story over it, so swimmers are seen
 * overhead from among the beds.
 */
final class RocketGreenhouse extends RocketRoom {
    /** Her greenhouse's story, from the entry story up. */
    static final int STORY = 4;
    /** How many rows over the floor the grow lights hang: over a head, near enough the plants to light them well. */
    private static final int GROW_LIGHT_UP = 5;
    /**
     * How far along the floor from a grow light a bed's block may be and still grow vegetables, in blocks: as far as
     * its light stays bright enough for them at the plants' height.
     */
    private static final int LIT_WITHIN = 2;
    /** How long a stretch of grow light is at most to hang from one cable at its middle, in blocks; longer, two. */
    private static final int SHORT_BAR = 3;
    /** One block of a bed in how many has nothing growing, picked or not yet sown. */
    private static final int PICKED_ONE_IN = 9;
    /** Along a pumpkin or melon bed, one block in how many bears a fruit, and which. */
    private static final int FRUIT_EVERY = 3;
    private static final int FRUIT_AT = 1;
    /** How full the composter is, from and to, of the levels it fills through. */
    private static final int COMPOST_FROM = 2;
    private static final int COMPOST_TO = ComposterBlock.MAX_LEVEL;
    /**
     * How tall a tree's trunk is and how far out from it its crown spreads, in blocks, and how many rows the crown
     * rises over its foot.
     */
    private static final int TRUNK = 5;
    private static final int CROWN = 2;
    private static final int CROWN_ROWS = 3;
    /** One block of a crown in how many is flowering. */
    private static final int FLOWERING_EVERY = 3;
    /** One block of the ceiling in how many has a vine, and how long one hangs at most, in blocks. */
    private static final int VINE_ONE_IN = 5;
    private static final int VINE_LONGEST = 4;
    /** The ways a spot of the plan faces, by their place in {@link GreenhousePlan#SIDES}. */
    private static final Direction[] FACINGS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    /** What grows in a flower bed, and in a bed out of the grow lights' reach. */
    private static final Block[] FLOWERS = {
        Blocks.FERN, Blocks.CORNFLOWER, Blocks.AZALEA, Blocks.OXEYE_DAISY, Blocks.ALLIUM, Blocks.FERN, Blocks.LILY_OF_THE_VALLEY,
        Blocks.FLOWERING_AZALEA, Blocks.BLUE_ORCHID, Blocks.POPPY};
    /** What grows in a herb pot, on the floor, on the barrel or on the potting bench: sometimes nothing yet. */
    private static final Block[] POTTED = {
        Blocks.POTTED_FERN, Blocks.POTTED_CORNFLOWER, Blocks.POTTED_AZALEA, Blocks.POTTED_RED_MUSHROOM, Blocks.POTTED_LILY_OF_THE_VALLEY,
        Blocks.POTTED_CACTUS, Blocks.POTTED_FLOWERING_AZALEA, Blocks.POTTED_BAMBOO, Blocks.FLOWER_POT, Blocks.POTTED_OXEYE_DAISY};

    RocketGreenhouse(RocketLayout layout) {
        super(layout, STORY);
    }

    @Override
    public void build() {
        RandomSource random = scatter();
        GreenhousePlan.Plan plan = GreenhousePlan.of(room(), wall(), random::nextInt);
        List<Cell> sprinklers = plan.beds().stream().map(Bed::sprinkler).filter(Objects::nonNull).toList();
        Set<Long> lit = new HashSet<>();
        for (Bed bed : plan.beds()) {
            growLight(bed, random, lit);
        }
        for (Bed bed : plan.beds()) {
            bed(bed, sprinklers, lit, random);
        }
        for (Spot spot : plan.spots()) {
            spot(spot, random);
        }
        int reach = (int) Math.ceil(wall());
        for (BlockPos at : BlockPos.betweenClosed(-reach, top(), -reach, reach, top(), reach)) {
            vine(at.getX(), at.getZ(), random);
        }
        for (Cell cell : plan.moss()) {
            put(cell.x(), floor() + 1, cell.z(), Blocks.MOSS_CARPET.defaultBlockState());
        }
    }

    /** The floor as the plan sees it: where the room's rule lets something stand, and a bed with its plants. */
    private GreenhousePlan.Room room() {
        int y = floor() + 1;
        return new GreenhousePlan.Room() {
            @Override
            public boolean free(int x, int z) {
                return RocketGreenhouse.this.free(x, y, z);
            }

            @Override
            public boolean plantable(int x, int z) {
                return RocketGreenhouse.this.free(x, y, z) && RocketGreenhouse.this.free(x, y + 1, z);
            }
        };
    }

    private static long column(int x, int z) {
        return BlockPos.asLong(x, 0, z);
    }

    /**
     * A bed's grow light: a bar along it over one of its rows, as much of it as is free, each stretch hung on cables up
     * to whatever is overhead, the ceiling or the pool's glass; a stretch nothing can hang from is left out.
     */
    private void growLight(Bed bed, RandomSource random, Set<Long> lit) {
        int row = random.nextInt(bed.width());
        int y = floor() + GROW_LIGHT_UP;
        List<Cell> stretch = new ArrayList<>();
        for (int along = 0; along <= bed.length(); along++) {
            Cell cell = along < bed.length() ? bed.at(along, row) : null;
            if (cell != null && free(cell.x(), y, cell.z())) {
                stretch.add(cell);
            } else {
                hangBar(stretch, bed.alongX(), lit);
                stretch.clear();
            }
        }
    }

    private void hangBar(List<Cell> stretch, boolean alongX, Set<Long> lit) {
        if (stretch.isEmpty()) {
            return;
        }
        int y = floor() + GROW_LIGHT_UP;
        List<Cell> hangers = hangers(stretch, y + 1);
        if (hangers.isEmpty()) {
            return;
        }
        BlockState bar = AerolargeRegistry.GROW_LIGHT.get().defaultBlockState()
            .setValue(GrowLightBlock.AXIS, alongX ? Direction.Axis.X : Direction.Axis.Z);
        for (Cell cell : stretch) {
            layout.set(cell.x(), y, cell.z(), bar);
            lit.add(column(cell.x(), cell.z()));
        }
        for (Cell cell : hangers) {
            hang(cell, y + 1);
        }
    }

    /**
     * Where a stretch of grow light hangs from, where a cable can go up from it: a short one from its middle, or else
     * an end; a longer one from both its ends.
     */
    private List<Cell> hangers(List<Cell> stretch, int from) {
        if (stretch.size() <= SHORT_BAR) {
            Cell middle = stretch.get(stretch.size() / 2);
            return Stream.of(middle, stretch.getFirst(), stretch.getLast()).filter(cell -> hangable(cell, from)).limit(1).toList();
        }
        return Stream.of(stretch.getFirst(), stretch.getLast()).filter(cell -> hangable(cell, from)).toList();
    }

    /** Whether a cable can hang from a row up to what is overhead, every block of the way free. */
    private boolean hangable(Cell cell, int from) {
        for (int y = from; y <= top(); y++) {
            if (!layout.isOpen(cell.x(), y, cell.z())) {
                return true;
            }
            if (!free(cell.x(), y, cell.z())) {
                return false;
            }
        }
        return true;
    }

    private void hang(Cell cell, int from) {
        BlockState cable = AerolargeRegistry.LIGHT_CABLE.get().defaultBlockState();
        for (int y = from; y <= top() && layout.isOpen(cell.x(), y, cell.z()); y++) {
            layout.set(cell.x(), y, cell.z(), cable);
        }
    }

    /** Whether a spot of the floor is within reach of a grow light's glow. */
    private static boolean lit(Cell cell, Set<Long> lit) {
        int side = 2 * LIT_WITHIN + 1;
        for (int i = 0; i < side * side; i++) {
            int dx = i % side - LIT_WITHIN;
            int dz = i / side - LIT_WITHIN;
            if (Math.abs(dx) + Math.abs(dz) <= LIT_WITHIN && lit.contains(column(cell.x() + dx, cell.z() + dz))) {
                return true;
            }
        }
        return false;
    }

    /** A raised bed: its soil framed round its outside, its sprinkler standing in water, and what grows in it. */
    private void bed(Bed bed, List<Cell> sprinklers, Set<Long> lit, RandomSource random) {
        int y = floor() + 1;
        for (Cell cell : bed.cells()) {
            if (cell.equals(bed.sprinkler())) {
                layout.set(cell.x(), y, cell.z(), AerolargeRegistry.SPRINKLER.get().defaultBlockState().setValue(SprinklerBlock.WATERLOGGED, true));
            } else {
                layout.set(cell.x(), y, cell.z(), soil(bed, cell, sprinklers));
                plant(bed, cell, lit, random);
            }
        }
    }

    /** A block of a bed's soil, joined to the rest of the bed round it, moist within a sprinkler's reach. */
    private static BlockState soil(Bed bed, Cell cell, List<Cell> sprinklers) {
        boolean moist = sprinklers.stream().anyMatch(sprinkler -> sprinkler.reach(cell) <= GreenhousePlan.WATERED);
        BlockState soil = AerolargeRegistry.GARDEN_BED.get().defaultBlockState().setValue(FarmBlock.MOISTURE, moist ? FarmBlock.MAX_MOISTURE : 0);
        for (int side = 0; side < FACINGS.length; side++) {
            soil = GardenBedBlock.joined(soil, FACINGS[side], bed.contains(cell.step(side)));
        }
        return soil;
    }

    /**
     * What grows on a block of a bed, at a stage of its own: the bed's planting, but flowers where a vegetable would be
     * out of the grow lights' reach; and now and then nothing, picked or not yet sown, though a vine always grows.
     */
    private void plant(Bed bed, Cell cell, Set<Long> lit, RandomSource random) {
        Planting planting = bed.planting(cell);
        boolean vine = planting == Planting.PUMPKINS || planting == Planting.MELONS;
        if (!vine && random.nextInt(PICKED_ONE_IN) == 0) {
            return;
        }
        boolean dark = planting.vegetable() && !lit(cell, lit);
        BlockState state = dark ? flower(random) : grown(bed, cell, planting, random);
        put(cell.x(), floor() + 2, cell.z(), state);
    }

    private static BlockState grown(Bed bed, Cell cell, Planting planting, RandomSource random) {
        return switch (planting) {
            case CARROTS -> crop(Blocks.CARROTS, random);
            case POTATOES -> crop(Blocks.POTATOES, random);
            case BEETROOTS -> crop(Blocks.BEETROOTS, random);
            case WHEAT -> crop(Blocks.WHEAT, random);
            case BERRIES -> Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, random.nextInt(SweetBerryBushBlock.MAX_AGE + 1));
            case PUMPKINS -> vine(bed, cell, Blocks.PUMPKIN, Blocks.PUMPKIN_STEM, Blocks.ATTACHED_PUMPKIN_STEM, random);
            case MELONS -> vine(bed, cell, Blocks.MELON, Blocks.MELON_STEM, Blocks.ATTACHED_MELON_STEM, random);
            case FLOWERS -> flower(random);
        };
    }

    private static BlockState crop(Block block, RandomSource random) {
        CropBlock crop = (CropBlock) block;
        return crop.getStateForAge(random.nextInt(crop.getMaxAge() + 1));
    }

    private static BlockState flower(RandomSource random) {
        return FLOWERS[random.nextInt(FLOWERS.length)].defaultBlockState();
    }

    /**
     * A block of a pumpkin or melon bed: a fruit along its far row every few blocks, the vine beside each, on the near
     * row of a wide bed or just before it along a narrow one, grown on to it, and the vine at some stage of its own
     * elsewhere.
     */
    private static BlockState vine(Bed bed, Cell cell, Block fruit, Block stem, Block attached, RandomSource random) {
        if (bearsFruit(bed, cell)) {
            return fruit.defaultBlockState();
        }
        // Toward the far row across a wide bed, on along a narrow one: whichever of x and z that is, the way it grows.
        int toFruit = (bed.width() > 1) == bed.alongX() ? GreenhousePlan.SOUTH : GreenhousePlan.EAST;
        if (bed.contains(cell.step(toFruit)) && bearsFruit(bed, cell.step(toFruit))) {
            return attached.defaultBlockState().setValue(AttachedStemBlock.FACING, FACINGS[toFruit]);
        }
        return stem.defaultBlockState().setValue(StemBlock.AGE, random.nextInt(StemBlock.MAX_AGE + 1));
    }

    /** Whether a block of a pumpkin or melon bed bears a fruit: every few along its far row, but not its sprinkler. */
    private static boolean bearsFruit(Bed bed, Cell cell) {
        return bed.along(cell) % FRUIT_EVERY == FRUIT_AT && bed.across(cell) == bed.width() - 1 && !cell.equals(bed.sprinkler());
    }

    /** What stands on a spot of the floor off the beds. */
    private void spot(Spot spot, RandomSource random) {
        int x = spot.at().x();
        int y = floor() + 1;
        int z = spot.at().z();
        Direction facing = FACINGS[spot.facing()];
        switch (spot.thing()) {
            case VALVE -> put(x, y, z, AerolargeRegistry.IRRIGATION_VALVE.get().defaultBlockState().setValue(IrrigationValveBlock.FACING, facing));
            case CHERRY_TREE -> tree(x, y, z, true);
            case AZALEA_TREE -> tree(x, y, z, false);
            case COMPOSTER -> put(x, y, z, Blocks.COMPOSTER.defaultBlockState()
                .setValue(ComposterBlock.LEVEL, COMPOST_FROM + random.nextInt(COMPOST_TO - COMPOST_FROM + 1)));
            case WATER_BUTT -> put(x, y, z, Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
            case BARREL -> standing(x, y, z, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP), random);
            case BENCH -> standing(x, y, z, Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP), random);
            case SOIL_HEAP -> put(x, y, z, (random.nextBoolean() ? Blocks.COARSE_DIRT : Blocks.ROOTED_DIRT).defaultBlockState());
            case BEAN_BAG -> put(x, y, z, AerolargeRegistry.BEAN_BAG.get().defaultBlockState().setValue(BeanBagBlock.FACING, facing)
                .setValue(BeanBagBlock.COLOR, DyeColor.GREEN));
            case HERB_POT -> put(x, y, z, potted(random));
        }
    }

    /** Something to stand pots on, and a pot on it where there is room. */
    private void standing(int x, int y, int z, BlockState under, RandomSource random) {
        if (put(x, y, z, under)) {
            put(x, y + 1, z, potted(random));
        }
    }

    private static BlockState potted(RandomSource random) {
        return POTTED[random.nextInt(POTTED.length)].defaultBlockState();
    }

    /** A tree with its trunk from a block up, a cherry's or an azalea's crown on it. */
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

    /** At one ceiling block in a few, a glow berry vine hanging down as long as the dice have it, its tip lit. */
    private void vine(int x, int z, RandomSource random) {
        if (random.nextInt(VINE_ONE_IN) != 0 || !free(x, top(), z)) {
            return;
        }
        int lowest = top() - random.nextInt(VINE_LONGEST);
        int tip = top();
        while (tip > lowest && free(x, tip - 1, z)) {
            tip--;
        }
        for (int y = top(); y > tip; y--) {
            layout.set(x, y, z, Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(CaveVinesBlock.BERRIES, random.nextBoolean()));
        }
        layout.set(x, tip, z, Blocks.CAVE_VINES.defaultBlockState().setValue(CaveVinesBlock.BERRIES, true).setValue(CaveVinesBlock.AGE, CaveVinesBlock.MAX_AGE));
    }
}
