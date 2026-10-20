package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.CockpitGlassBlock;
import dev.aerolarge.block.GiantRubberDuckBlock;
import dev.aerolarge.block.RubberDuckBlock;
import dev.aerolarge.block.RubberDuckBlockEntity;
import dev.aerolarge.block.SuitcaseBlock;
import dev.aerolarge.block.VacuumToiletBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IntSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The duck hunt: five rubber ducks hidden about the furnished airliner, one for each criterion of the Duck Hunt
 * advancement (see {@link RubberDuckBlock}). Their spots are found from the finished layout, from what the rooms hold,
 * rather than laid by each room, so they follow however the rooms are fitted out:
 * <ol>
 * <li>on the flight deck's glareshield, where real pilots keep a mascot;</li>
 * <li>in the WC behind the flight deck, on the floor beside its basin;</li>
 * <li>sitting on the balls of the kids' den's ball pit;</li>
 * <li>on top of a luggage pile in the hold;</li>
 * <li>bobbing on the moon pool's water by the steps out, in plain sight: the one that shows what to look for.</li>
 * </ol>
 * A duck whose spot the airliner does not have at its scale is left out. It runs once the fit-out and the hold are
 * done, and only puts a duck into open space, the pool's water, the ball pit's top or a suitcase's place in a pile.
 * It also finds where the giant duck rises once the last of them is found, afloat in the moon pool, and tells each
 * hidden duck where that is through a fitting; the giant duck itself only goes in then.
 */
final class DuckHunt {
    /** How many rows above the pool's water a duck afloat on it needs open, so it shows from across the pool. */
    private static final int POOL_HEADROOM = 3;
    /** The ways the giant duck may face, the likeliest first: toward the doors' aisle behind the pool, across it, ahead. */
    private static final List<Direction> GIANT_FACINGS = List.of(Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.NORTH);

    private final AirframeLayout layout;
    private final int floor;

    DuckHunt(AirframeLayout layout) {
        this.layout = layout;
        this.floor = layout.floorY();
    }

    /** Where a duck goes, or the giant duck's origin, and the way its beak points. */
    private record Spot(BlockPos pos, Direction facing) {
    }

    void apply() {
        List<Spot> hidden = new ArrayList<>();
        hide(1, glareshield(), hidden);
        hide(2, washroom(), hidden);
        hide(3, ballPit(), hidden);
        hide(4, luggage(), hidden);
        hide(5, pool(), hidden);
        Spot giant = giant();
        if (giant == null) {
            return;
        }
        for (Spot duck : hidden) {
            RubberDuckBlockEntity.GiantSpot seen = seenFrom(duck, giant);
            layout.fittings().put(duck.pos(), (blockEntity, rotation) -> {
                if (blockEntity instanceof RubberDuckBlockEntity hiddenDuck) {
                    hiddenDuck.giant(seen);
                }
            });
        }
    }

    /**
     * Puts hidden duck number {@code duck} at the spot, afloat if the spot is water, in place of whatever was set up
     * there, and adds it to the hidden ones.
     */
    private void hide(int duck, @Nullable Spot spot, List<Spot> hidden) {
        if (spot == null) {
            return;
        }
        BlockPos at = spot.pos();
        layout.fittings().remove(at);
        boolean afloat = layout.stateAt(at.getX(), at.getY(), at.getZ()).is(Blocks.WATER);
        layout.set(at.getX(), at.getY(), at.getZ(), AerolargeRegistry.RUBBER_DUCK.get().defaultBlockState()
            .setValue(RubberDuckBlock.FACING, spot.facing())
            .setValue(RubberDuckBlock.DUCK, duck)
            .setValue(RubberDuckBlock.WATERLOGGED, afloat));
        hidden.add(spot);
    }

    /** Where the giant duck rises seen from a hidden duck, in the duck's own frame, which turns with it. */
    private static RubberDuckBlockEntity.GiantSpot seenFrom(Spot duck, Spot giant) {
        BlockPos away = giant.pos().subtract(duck.pos());
        Direction ahead = duck.facing();
        Direction right = ahead.getClockWise();
        int turns = 0;
        for (Direction facing = ahead; facing != giant.facing(); facing = facing.getClockWise()) {
            turns++;
        }
        return new RubberDuckBlockEntity.GiantSpot(away.getX() * right.getStepX() + away.getZ() * right.getStepZ(), away.getY(),
            away.getX() * ahead.getStepX() + away.getZ() * ahead.getStepZ(), turns);
    }

    /**
     * Where the giant duck rises once the last duck is found (see {@link GiantRubberDuckBlock}): afloat in the moon
     * pool, its origin on the water's surface, as near the middle of the pool as it fits, facing the doors' aisle
     * behind the pool where it can, else across or along it. Every block of it has to be the pool's water on the
     * surface and open space over it, clear of the steps out, a dive from the springboard and the duck afloat there.
     * Null in an airliner without a pool, or with one too small for it.
     */
    @Nullable
    private Spot giant() {
        int from = (int) Math.ceil(CabinFitOut.POOL[0] * layout.scale());
        int to = (int) Math.floor(CabinFitOut.POOL[1] * layout.scale());
        List<BlockPos> water = where(state -> state.is(Blocks.WATER)).stream()
            .filter(pos -> pos.getY() == floor && pos.getZ() >= from && pos.getZ() <= to)
            .toList();
        if (water.isEmpty()) {
            return null;
        }
        double middleX = water.stream().mapToInt(BlockPos::getX).average().orElseThrow();
        double middleZ = water.stream().mapToInt(BlockPos::getZ).average().orElseThrow();
        List<BlockPos> springboards = where(state -> state.is(AerolargeRegistry.SPRINGBOARD.get()));
        List<Spot> fits = new ArrayList<>();
        for (BlockPos origin : water) {
            for (Direction facing : GIANT_FACINGS) {
                if (giantFits(origin, facing, springboards)) {
                    fits.add(new Spot(origin, facing));
                }
            }
        }
        return fits.stream()
            .min(Comparator.comparingDouble((Spot spot) -> Math.pow(spot.pos().getX() - middleX, 2) + Math.pow(spot.pos().getZ() - middleZ, 2))
                .thenComparingInt(spot -> GIANT_FACINGS.indexOf(spot.facing())))
            .orElse(null);
    }

    /** Whether the giant duck fits with its origin there, facing that way. */
    private boolean giantFits(BlockPos origin, Direction facing, List<BlockPos> springboards) {
        for (int part : GiantRubberDuckBlock.solidParts()) {
            BlockPos pos = origin.offset(GiantRubberDuckBlock.offset(part, facing));
            boolean afloat = pos.getY() == origin.getY() ? layout.stateAt(pos.getX(), pos.getY(), pos.getZ()).is(Blocks.WATER) : open(pos);
            if (!afloat || besideSteps(pos) || besideDuck(pos) || nearAny(pos, springboards)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a duck is at the spot or right beside it. */
    private boolean besideDuck(BlockPos pos) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (layout.stateAt(pos.getX() + x, pos.getY(), pos.getZ() + z).is(AerolargeRegistry.RUBBER_DUCK.get())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether any of the positions is in the spot's column, or the one beside it. */
    private static boolean nearAny(BlockPos pos, List<BlockPos> others) {
        return others.stream().anyMatch(other -> Math.abs(other.getX() - pos.getX()) <= 1 && Math.abs(other.getZ() - pos.getZ()) <= 1);
    }

    /**
     * On the glareshield, facing the pilots: on top of the panel they look out over, ahead of the thrust levers'
     * pedestal and the row the pilots' legs take in front of it. Of the open spots on a plain solid block there (not a
     * screen or a control, nor the hull or the windscreen) that the pilots see, nothing between them and it: the
     * nearest the pilots, then the highest (the glareshield over the panel), then the nearest the middle.
     */
    @Nullable
    private Spot glareshield() {
        List<BlockPos> levers = where(state -> state.is(AerolargeRegistry.THRUST_LEVER.get()));
        if (levers.isEmpty()) {
            return null;
        }
        int deckRow = levers.getFirst().getY();
        int pedestal = levers.stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        return layout.cells().keySet().stream()
            .filter(pos -> pos.getY() > deckRow && pos.getZ() < pedestal - 1 && open(pos) && open(pos.south()) && panelTop(pos.below()))
            .min(Comparator.comparingInt((BlockPos pos) -> -pos.getZ())
                .thenComparingInt(pos -> -pos.getY())
                .thenComparingInt(pos -> fromMiddle(pos.getX())))
            .map(pos -> new Spot(pos, Direction.SOUTH))
            .orElse(null);
    }

    /** Whether a block makes the glareshield: plain and solid, with a flat top. */
    private boolean panelTop(BlockPos pos) {
        BlockState state = layout.stateAt(pos.getX(), pos.getY(), pos.getZ());
        AirframePalette palette = layout.palette();
        return !state.isAir() && !(state.getBlock() instanceof EntityBlock) && !(state.getBlock() instanceof CockpitGlassBlock)
            && !state.is(palette.hull().getBlock()) && !state.is(palette.light().getBlock()) && !state.is(palette.floor().getBlock())
            && flatTop(state);
    }

    /**
     * In the WC behind the flight deck, on the floor beside its basin, facing into the room: found from the basin, the
     * filled cauldron on the raised floor ahead of the forward doors (the cabin's other lavatories are on the main
     * deck). Between the basin and the hull if there is room, else in front of the basin, never where the toilet's user
     * stands.
     */
    @Nullable
    private Spot washroom() {
        int doors = (int) Math.round(AirframeShape.PASSENGER_DOORS[0] * layout.scale());
        BlockPos basin = where(state -> state.is(Blocks.WATER_CAULDRON)).stream()
            .filter(pos -> pos.getY() > floor + 1 && pos.getZ() < doors)
            .min(Comparator.comparingInt(BlockPos::getZ))
            .orElse(null);
        if (basin == null) {
            return null;
        }
        int outboard = basin.getX() > 0 ? 1 : -1;
        for (BlockPos spot : new BlockPos[]{basin.east(outboard), basin.south(), basin.east(outboard).south()}) {
            if (open(spot) && flatTop(layout.stateAt(spot.getX(), spot.getY() - 1, spot.getZ())) && !beforeToilet(spot)) {
                return new Spot(spot, Direction.SOUTH);
            }
        }
        return null;
    }

    /** Whether a toilet beside the spot faces it: a duck there would sit where its user stands. */
    private boolean beforeToilet(BlockPos spot) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = spot.relative(side);
            BlockState there = layout.stateAt(next.getX(), next.getY(), next.getZ());
            if (there.is(AerolargeRegistry.VACUUM_TOILET.get()) && there.getValue(VacuumToiletBlock.FACING) == side.getOpposite()) {
                return true;
            }
        }
        return false;
    }

    /**
     * In the kids' den, sitting on the balls in the middle of the ball pit, facing the secret door it is found from;
     * where there is no room over the balls, sunk into them up to its neck.
     */
    @Nullable
    private Spot ballPit() {
        Map<BlockPos, Integer> tops = new HashMap<>();
        for (BlockPos ball : where(state -> state.is(AerolargeRegistry.BALL_PIT.get()))) {
            tops.merge(new BlockPos(ball.getX(), 0, ball.getZ()), ball.getY(), Math::max);
        }
        if (tops.isEmpty()) {
            return null;
        }
        IntSummaryStatistics stations = tops.keySet().stream().mapToInt(BlockPos::getZ).summaryStatistics();
        int middle = (stations.getMin() + stations.getMax()) / 2;
        return tops.entrySet().stream()
            .map(column -> {
                BlockPos top = column.getKey().atY(column.getValue());
                return open(top.above()) ? top.above() : top;
            })
            .min(Comparator.comparingInt((BlockPos pos) -> open(pos) ? 0 : 1)
                .thenComparingInt(pos -> fromMiddle(pos.getX()))
                .thenComparingInt(pos -> Math.abs(pos.getZ() - middle)))
            .map(pos -> new Spot(pos, Direction.NORTH))
            .orElse(null);
    }

    /**
     * In the hold, on top of the starboard luggage pile halfway along it (the port one where there is none), facing
     * the walkway: on a suitcase or a care package, never a golden suitcase, which it would give away, and with the
     * space between it and the walkway open, so it shows from there through the cargo net. Where the piles reach up to
     * the cabin floor, it takes the place of a suitcase in their top row instead, tucked into the pile.
     */
    @Nullable
    private Spot luggage() {
        List<BlockPos> luggage = where(state -> state.getBlock() instanceof SuitcaseBlock suitcase && !suitcase.golden()
            || state.is(AerolargeRegistry.CARGO_CRATE.get()));
        List<BlockPos> spots = luggage.stream().map(BlockPos::above).filter(pos -> pos.getY() < floor && open(pos) && seenFromWalkway(pos)).toList();
        if (spots.isEmpty()) {
            spots = luggage.stream().filter(pos -> pos.getY() == floor - 1 && seenFromWalkway(pos)).toList();
        }
        if (spots.isEmpty()) {
            return null;
        }
        IntSummaryStatistics stations = spots.stream().mapToInt(BlockPos::getZ).summaryStatistics();
        int middle = (stations.getMin() + stations.getMax()) / 2;
        return spots.stream()
            .min(Comparator.comparingInt((BlockPos pos) -> pos.getX() > 0 ? 0 : 1)
                .thenComparingInt(pos -> Math.abs(pos.getZ() - middle))
                .thenComparingInt(BlockPos::getY)
                .thenComparingInt(pos -> Math.abs(pos.getX())))
            .map(pos -> new Spot(pos, pos.getX() > 0 ? Direction.WEST : Direction.EAST))
            .orElse(null);
    }

    /**
     * Afloat on the moon pool, at the back where swimmers climb out and everyone coming in from the doors' aisle sees
     * it first, facing them: on the water's surface with open space over it, beside the steps out but not in their
     * way, and off the line of a dive from the springboard. Nearest the middle of the back of the pool.
     */
    @Nullable
    private Spot pool() {
        List<BlockPos> springboards = where(state -> state.is(AerolargeRegistry.SPRINGBOARD.get()));
        int from = (int) Math.ceil(CabinFitOut.POOL[0] * layout.scale());
        int to = (int) Math.floor(CabinFitOut.POOL[1] * layout.scale());
        return where(state -> state.is(Blocks.WATER)).stream()
            .filter(pos -> pos.getY() == floor && pos.getZ() >= from && pos.getZ() <= to && headroom(pos, POOL_HEADROOM) && !besideSteps(pos)
                && !nearAny(pos, springboards))
            .min(Comparator.comparingInt((BlockPos pos) -> -pos.getZ()).thenComparingInt(pos -> fromMiddle(pos.getX())))
            .map(pos -> new Spot(pos, Direction.SOUTH))
            .orElse(null);
    }

    /** Whether a spot to one side of the hold's walkway has open space between it and the walkway. */
    private boolean seenFromWalkway(BlockPos pos) {
        return pos.getX() != 0 && open(pos.east(pos.getX() > 0 ? -1 : 1));
    }

    /** Whether this many rows over the spot are open. */
    private boolean headroom(BlockPos pos, int rows) {
        for (int up = 1; up <= rows; up++) {
            if (!open(pos.above(up))) {
                return false;
            }
        }
        return true;
    }

    /** Whether one of the pool's steps out is right beside the spot. */
    private boolean besideSteps(BlockPos pos) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos next = pos.relative(side);
            if (layout.stateAt(next.getX(), next.getY(), next.getZ()).getBlock() instanceof StairBlock) {
                return true;
            }
        }
        return false;
    }

    /** Every position of the layout whose block the test accepts. */
    private List<BlockPos> where(Predicate<BlockState> test) {
        return layout.cells().entrySet().stream().filter(cell -> test.test(cell.getValue())).map(Map.Entry::getKey).toList();
    }

    private boolean open(BlockPos pos) {
        return layout.isOpen(pos.getX(), pos.getY(), pos.getZ());
    }

    /** Whether a block's top is flat and whole, to set a duck on. */
    private static boolean flatTop(BlockState state) {
        return Block.isFaceFull(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO), Direction.UP);
    }

    /** How far from the middle of the airliner a column is, for sorting: the middle first, then each side, port first. */
    private static int fromMiddle(int x) {
        return 2 * Math.abs(x) + (x > 0 ? 1 : 0);
    }
}
