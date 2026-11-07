package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.GalleyUnitBlock;
import dev.skyliners.block.GalleyUnitBlockEntity;
import dev.skyliners.block.HabitatGlassBlock;
import dev.skyliners.block.HabitatHatchBlock;
import dev.skyliners.block.HabitatHatchBlockEntity;
import dev.skyliners.block.LabInstrumentBlock;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The freighter's small tardigrade's tank, in her lounge: a glass tank three blocks wide and five long, standing out
 * from the lounge's north wall into the room on a stand of galley cabinets, so the creature paces at the eye height of
 * anyone standing before it. Its glass is the rocket's tank's (see {@link HabitatGlassBlock}), moss over its floor and
 * lit soft along its top, under a dark steel hood; its feeding hatch in the middle of its south end, facing into the
 * room, the creature's vitals over it. Beside the hatch stands a worktop with a pad of moss on it, plain to see, to
 * show what the creature eats, and the cabinet under the hatch holds more. The hatch keeps the creature (see
 * {@link HabitatHatchBlockEntity}), made a quarter of the rocket's one's size, which paces the tank's middle from its
 * far end to the hatch and turns round halfway.
 *
 * <p>Laid out nose north, as the rest of her: the lounge lies south of the wall it shares with her entry hall, which
 * runs along z 0. Its hood goes over each column where her hull leaves room. Built only where every block of the rest
 * is open over her deck's floor, with room before the hatch to stand, and not at all with the server config's
 * {@code freighter.tardigrade} off.
 */
final class LoungeTank {
    /** How many times the rocket's tardigrade's size the small one is: about a block and a half long. */
    private static final double SIZE = 0.25;
    /**
     * The tank's columns, from west to east, the hatch's in the middle, and from its north end, against the wall, to
     * its south end; and the column of the worktop with the moss pad, beside the tank's south end on its west.
     */
    private static final int WEST = 17;
    private static final int MIDDLE = 18;
    private static final int EAST = 19;
    private static final int NORTH = 1;
    private static final int SOUTH = 5;
    private static final int MOSS_PAD = WEST - 1;
    /** How many moss carpets the cabinet under the hatch holds, to feed the creature with. */
    private static final int FEED = 16;
    /**
     * The creature's line along the middle column's middle, from its north end to its south end, as far in from the
     * tank's ends as the creature's head and tail reach and with its mouth at the hatch at the south end; and where it
     * turns round, halfway along it, where nothing is within its length all round. In the layout's blocks, a block's
     * corner at its whole numbers, its feet on the stand.
     */
    private static final double LINE_X = MIDDLE + 0.5;
    private static final double LINE_FROM = NORTH + 0.9;
    private static final double LINE_TO = SOUTH - 0.575;
    private static final Vec3 TURN = new Vec3(LINE_X, 0, (NORTH + SOUTH + 1) / 2.0);

    private final HullLayout layout;
    private final int deck;

    private LoungeTank(HullLayout layout, int deck) {
        this.layout = layout;
        this.deck = deck;
    }

    /** Builds the tank in a freighter's layout whose deck's floor is the row given, where it fits. */
    static void build(HullLayout layout, int deck) {
        LoungeTank tank = new LoungeTank(layout, deck);
        if (SkylinersConfig.FREIGHTER_TARDIGRADE.get() && tank.fits()) {
            tank.lay();
        }
    }

    /**
     * Whether every column of the tank and the moss pad's has her deck's floor under it and three rows open over that,
     * and there is room before the hatch to stand and feed the creature.
     */
    private boolean fits() {
        boolean free = IntStream.rangeClosed(WEST, EAST).allMatch(x -> IntStream.rangeClosed(NORTH, SOUTH).allMatch(z -> columnFree(x, z)));
        return free && columnFree(MOSS_PAD, SOUTH) && columnFree(MIDDLE, SOUTH + 1);
    }

    private boolean columnFree(int x, int z) {
        boolean open = layout.isOpen(x, deck + 1, z) && layout.isOpen(x, deck + 2, z) && layout.isOpen(x, deck + 3, z);
        return open && !layout.isOpen(x, deck, z);
    }

    private void lay() {
        for (int x = WEST; x <= EAST; x++) {
            for (int z = NORTH; z <= SOUTH; z++) {
                layColumn(x, z);
            }
        }
        BlockPos hatch = new BlockPos(MIDDLE, deck + 2, SOUTH);
        layout.set(hatch.getX(), hatch.getY(), hatch.getZ(), SkylinersRegistry.HABITAT_HATCH.get().defaultBlockState()
            .setValue(HabitatHatchBlock.FACING, Direction.SOUTH));
        layout.set(MIDDLE, deck + 3, SOUTH, SkylinersRegistry.LAB_INSTRUMENT.get().of(LabInstrumentBlock.Kind.VITALS, Direction.SOUTH));
        layout.fit(new BlockPos(MIDDLE, deck + 1, SOUTH), LoungeTank::stockFeed);
        BlockState worktop = SkylinersRegistry.GALLEY_WORKTOP.get().defaultBlockState().setValue(GalleyUnitBlock.FACING, Direction.SOUTH);
        layout.set(MOSS_PAD, deck + 1, SOUTH, worktop);
        layout.set(MOSS_PAD, deck + 2, SOUTH, Blocks.MOSS_CARPET.defaultBlockState());
        keep(hatch);
    }

    /**
     * A column of the tank: its cabinet, facing out of the tank's side or its south end, its two rows of glass, and its
     * hood over them where her hull leaves room.
     */
    private void layColumn(int x, int z) {
        Direction out = z == SOUTH ? Direction.SOUTH : x == WEST ? Direction.WEST : Direction.EAST;
        layout.set(x, deck + 1, z, SkylinersRegistry.GALLEY_STOWAGE.get().defaultBlockState().setValue(GalleyUnitBlock.FACING, out));
        HabitatGlassBlock glass = SkylinersRegistry.HABITAT_GLASS.get();
        Set<Direction> sides = panes(x, z);
        layout.set(x, deck + 2, z, glass.panes(sides, HabitatGlassBlock.Row.FLOOR));
        layout.set(x, deck + 3, z, glass.panes(sides, HabitatGlassBlock.Row.TOP));
        if (layout.isOpen(x, deck + 4, z)) {
            layout.set(x, deck + 4, z, Blocks.POLISHED_DEEPSLATE_SLAB.defaultBlockState());
        }
    }

    /** The ways out of the tank a column of it has glass on: every way that is not another column of it. */
    private static Set<Direction> panes(int x, int z) {
        Set<Direction> sides = EnumSet.noneOf(Direction.class);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            int besideX = x + side.getStepX();
            int besideZ = z + side.getStepZ();
            if (besideX < WEST || besideX > EAST || besideZ < NORTH || besideZ > SOUTH) {
                sides.add(side);
            }
        }
        return sides;
    }

    /** Has the hatch keep the small creature once she stands: its line and where it turns, from the hatch's middle. */
    private void keep(BlockPos hatch) {
        Vec3 middle = Vec3.atCenterOf(hatch);
        double feet = deck + 2;
        Vec3 from = new Vec3(LINE_X, feet, LINE_FROM).subtract(middle);
        Vec3 to = new Vec3(LINE_X, feet, LINE_TO).subtract(middle);
        Vec3 turn = TURN.add(0, feet, 0).subtract(middle);
        layout.fit(hatch, (blockEntity, rotation) -> keeper(blockEntity, rotation, from, to, turn));
    }

    private static void keeper(BlockEntity blockEntity, Rotation rotation, Vec3 from, Vec3 to, Vec3 turn) {
        if (blockEntity instanceof HabitatHatchBlockEntity keeper) {
            keeper.configure(from, to, turn, rotation, SIZE);
        }
    }

    /** The cabinet under the hatch, stocked once it stands with moss to feed the creature through the hatch. */
    private static void stockFeed(BlockEntity blockEntity, Rotation rotation) {
        if (blockEntity instanceof GalleyUnitBlockEntity cabinet) {
            cabinet.setItem(0, new ItemStack(Items.MOSS_CARPET, FEED));
        }
    }
}
