package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.CabinLightBlock;
import dev.aerolarge.block.CurtainBlock;
import dev.aerolarge.block.ExitSignBlock;
import dev.aerolarge.block.VacuumToiletBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * The furnished cabin's rooms walled off from the doors' aisles between them, the way an airliner's are: a cream
 * bulkhead right across the cabin at each end of a room that faces a door's aisle, and between business class and the
 * pool, its archway over the middle aisle hung with a curtain in the room's colour (navy for business class, cyan for
 * the pool, lime for the gym hall, red for the lounge), drawn open. Each archway rounds off over the aisle and is
 * framed in wood, a head's height clear right across it; over it, on the side facing the doors, a picture in coloured
 * wool of what lies through it (a seat, a drop of water, a basketball, a cup), for passengers who cannot read yet.
 * Business class is walled off at its front too, or, where no wall fits there, a curtain on its own crosses the aisle;
 * then lavatories either side of the archway into the gym hall, built onto its bulkhead as an airliner's are into the
 * monuments by its doors, the gym hall's hoops mounted on its end walls, its own dividing curtain (Trennvorhang) right
 * across the middle of the court, and a green running man over every passenger door.
 *
 * <p>Each wall stands at the station nearest where it is meant to that leaves the hull beside and over it plain skin,
 * so no window, light or seatbelt sign is walled in, and never in a door's aisle; a curtain on its own only keeps
 * clear of the lights and signs. One that finds no such station is left out. Only open cabin and carpet are built
 * over: a seat back in the way becomes part of a wall, and anything else in the way (a seat, a rope, a light hanging
 * there) rules the station out.
 */
final class CabinDividers {
    /** How far either side of the middle the aisle reaches: three blocks wide, as a curtain alone across it hangs. */
    private static final int AISLE = 1;
    /**
     * How far either side of the middle an archway reaches, in metres, and at least the aisle: wider at the larger
     * scales, where it would otherwise be a slot in the wall.
     */
    private static final double ARCH_REACH = 0.5;
    /**
     * How tall an archway is at its sides, in metres, and at least in blocks: a head's height clear right across it.
     * Over that it rounds off, a row higher for each block it steps in, up to its crown over the middle.
     */
    private static final double ARCH_HEIGHT = 1.5;
    private static final int LEAST_ARCH = 2;
    /** How many rows of trim head an archway, between its crown and the picture over it. */
    private static final int LINTEL = 1;
    /**
     * The archways' frame: a wooden architrave round each, its jambs either side and its head over the crown, as an
     * airliner's bulkhead openings are trimmed; and where it rounds off, fillets of smooth quartz (as near the
     * partition's cream as a stair comes) turned upside down, stepping in to the crown.
     */
    private static final BlockState TRIM = Blocks.SPRUCE_PLANKS.defaultBlockState();
    private static final BlockState FILLET = Blocks.SMOOTH_QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP);
    /** How far a divider may stand from where it is meant to, in stations, to find one that suits it. */
    private static final int SNAP = 2;
    /** How many blocks of headroom the lavatories have under their ceilings, as the WC behind the flight deck has. */
    private static final int HEADROOM = 3;
    /**
     * The pictures, each row from the top as seen from the doors' side of its wall, '.' the wall itself and each letter
     * a colour of wool (see WOOL): a business class seat seen from the side, a drop of water, a basketball, a cup with
     * its steam, and a person for the lavatories.
     */
    private static final String[] SEAT = {"B....", "B....", "BBBB.", "BBBB.", "B..B."};
    private static final String[] DROP = {"..B..", ".BBB.", "BBLBB", "BBBBB", ".BBB."};
    private static final String[] BALL = {".OKO.", "OOKOO", "KKKKK", "OOKOO", ".OKO."};
    private static final String[] CUP = {"G.G..", "RRRR.", "RRRRR", "RRRR.", ".RR.."};
    private static final String[] PERSON = {".B.", "BBB", "B.B"};
    private static final Map<Character, BlockState> WOOL = Map.of(
        'B', Blocks.BLUE_WOOL.defaultBlockState(),
        'L', Blocks.LIGHT_BLUE_WOOL.defaultBlockState(),
        'O', Blocks.ORANGE_WOOL.defaultBlockState(),
        'K', Blocks.BLACK_WOOL.defaultBlockState(),
        'R', Blocks.RED_WOOL.defaultBlockState(),
        'G', Blocks.LIGHT_GRAY_WOOL.defaultBlockState()
    );

    private final AirframeLayout layout;
    private final int floor;
    /** How many rows tall the archways are at this scale at their sides, the full width clear. */
    private final int arch;
    /** How far either side of the middle the archways reach at this scale, in blocks: as many rows as they round off. */
    private final int span;
    private final BlockState partition = AerolargeRegistry.CABIN_PARTITION.get().defaultBlockState();
    /** Every piece of curtain hung, joined into curtains once they all hang. */
    private final List<BlockPos> curtains = new ArrayList<>();

    CabinDividers(AirframeLayout layout) {
        this.layout = layout;
        this.floor = layout.floorY();
        this.arch = Math.max(LEAST_ARCH, (int) Math.round(ARCH_HEIGHT * layout.scale()));
        this.span = Math.max(AISLE, (int) Math.round(ARCH_REACH * layout.scale()));
    }

    /**
     * Whether a divider stands at the station, once they are up: each has a piece of curtain over the middle of the
     * aisle, its archway's or its own. For what is fitted into the cabin round them.
     */
    static boolean standsAt(AirframeLayout layout, int z) {
        return CurtainBlock.isCurtain(layout.stateAt(0, layout.floorY() + 1, z));
    }

    /**
     * The cabin's cross-section at a station: for each row from the floor up, how far it reaches to port and to
     * starboard, measured in from the hull so nothing standing in the cabin is taken for its wall.
     */
    private record Section(int z, int bottom, List<int[]> reach) {
        boolean contains(int x, int y) {
            int row = y - bottom;
            return row >= 0 && row < reach.size() && x >= reach.get(row)[0] && x <= reach.get(row)[1];
        }

        int top() {
            return bottom + reach.size() - 1;
        }
    }

    /** Divides off the rooms the fit-out has furnished; at the smallest scales it furnishes none, so none is. */
    void apply() {
        if (furnished(CabinFitOut.BUSINESS)) {
            // Ahead of business class its wall to the doors' aisle, or the curtain alone where no wall fits; behind,
            // its wall to the pool.
            if (divide(CabinFitOut.BUSINESS[0] - 0.5, DyeColor.BLUE, SEAT, true) == null) {
                Section front = near(CabinFitOut.BUSINESS[0] - 0.5, this::curtainFits);
                if (front != null) {
                    aisleCurtain(front, DyeColor.BLUE);
                }
            }
            divide((CabinFitOut.BUSINESS[1] + CabinFitOut.POOL[0]) / 2, DyeColor.BLUE, SEAT, false);
        }
        if (furnished(CabinFitOut.POOL)) {
            divide(CabinFitOut.POOL[1] + 0.5, DyeColor.CYAN, DROP, false);
        }
        if (furnished(CabinFitOut.GYM)) {
            // A metre out from either end of the court, so the walls stop the balls missing the hoops; the room in
            // front of the court is the lavatories'.
            int first = (int) Math.ceil(CabinFitOut.GYM[0] * layout.scale());
            int last = (int) Math.floor(CabinFitOut.GYM[1] * layout.scale());
            Section entrance = divide(CabinFitOut.GYM[0] - 1, DyeColor.LIME, BALL, true);
            if (entrance != null) {
                lavatories(entrance, first);
                mountHoop(entrance.z(), first);
            }
            Section exit = divide(CabinFitOut.GYM[1] + 1, DyeColor.LIME, BALL, false);
            if (exit != null) {
                mountHoop(exit.z(), last);
            }
            Section middle = near((CabinFitOut.GYM[0] + CabinFitOut.GYM[1]) / 2, this::acrossFits);
            if (middle != null) {
                across(middle, DyeColor.LIME);
            }
        }
        if (furnished(CabinFitOut.LOUNGE)) {
            divide(CabinFitOut.LOUNGE[0] - 0.5, DyeColor.RED, CUP, true);
        }
        joinCurtains();
        exitSigns();
    }

    /** A green running man on the wall just over every passenger door, over its forward half, facing into the cabin. */
    private void exitSigns() {
        int y = floor + 1 + layout.passengerDoorRows();
        for (double station : AirframeShape.PASSENGER_DOORS) {
            int z = (int) Math.round(station * layout.scale());
            for (int side : new int[]{1, -1}) {
                int x = layout.besideWall(side, y, z);
                if (x != 0) {
                    layout.furnish(x, y, z, AerolargeRegistry.EXIT_SIGN.get().defaultBlockState()
                        .setValue(ExitSignBlock.FACING, side > 0 ? Direction.WEST : Direction.EAST));
                }
            }
        }
    }

    /**
     * The hoop at this end of the court mounted on the wall behind it, as a school gym's are, rather than standing on
     * its pole in the way of everyone coming through the archway: the pole taken away, and the backboard and the
     * scoreboard over it held off the wall on a bracket of the wall's own panels. Left standing where there is no pole
     * or something is in the bracket's way.
     */
    private void mountHoop(int wall, int hoop) {
        if (!layout.stateAt(0, floor + 1, hoop).is(Blocks.IRON_BARS)) {
            return;
        }
        int board = floor + 1;
        while (layout.stateAt(0, board, hoop).is(Blocks.IRON_BARS)) {
            board++;
        }
        if (layout.isOpen(0, board, hoop)) {
            return;
        }
        int crown = section(hoop).top();
        int top = board;
        while (top < crown && !layout.isOpen(0, top + 1, hoop)) {
            top++;
        }
        List<BlockPos> bracket = new ArrayList<>();
        int step = Integer.signum(wall - hoop);
        for (int z = hoop + step; z != wall; z += step) {
            for (int y = board; y <= top; y++) {
                for (int x = -AISLE; x <= AISLE; x++) {
                    if (!layout.isOpen(x, y, z)) {
                        return;
                    }
                    bracket.add(new BlockPos(x, y, z));
                }
            }
        }
        bracket.forEach(at -> layout.set(at.getX(), at.getY(), at.getZ(), partition));
        for (int y = floor + 1; y < board; y++) {
            layout.set(0, y, hoop, Blocks.AIR.defaultBlockState());
        }
    }

    /** Whether the fit-out has furnished the room between these metres: anything down its middle, or another floor. */
    private boolean furnished(double[] room) {
        for (int z = (int) Math.ceil(room[0] * layout.scale()); z <= room[1] * layout.scale(); z++) {
            if (!layout.isCabinFloor(0, floor, z) || !layout.isOpen(0, floor + 1, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A wall across the cabin near the station so many metres from the nose, with its curtained archway and the
     * picture over it, seen from ahead of the wall or from behind it; the wall's section, or null if none fits.
     */
    @Nullable
    private Section divide(double metres, DyeColor color, String[] picture, boolean seenFromAhead) {
        Section wall = near(metres, this::wallFits);
        if (wall == null) {
            return null;
        }
        for (int y = wall.bottom(); y <= wall.top(); y++) {
            for (int x = wall.reach().get(y - wall.bottom())[0]; x <= wall.reach().get(y - wall.bottom())[1]; x++) {
                if (inArch(x, y)) {
                    hang(x, y, wall.z(), color);
                } else if (!inCrown(x, y) && fillable(x, y, wall.z())) {
                    layout.set(x, y, wall.z(), frame(x, y));
                }
            }
        }
        int over = crown() + LINTEL + picture.length;
        picture(wall.z(), 0, over, picture, seenFromAhead);
        return wall;
    }

    /** A curtain across the aisle alone, from the floor up to the crown, its sides open to the seats either side. */
    private void aisleCurtain(Section section, DyeColor color) {
        for (int y = section.bottom(); y <= section.top(); y++) {
            for (int x = -AISLE; x <= AISLE; x++) {
                if (section.contains(x, y)) {
                    hang(x, y, section.z(), color);
                }
            }
        }
    }

    /**
     * The gym hall's dividing curtain (Trennvorhang): right across the cabin from the floor to the crown, so the hall
     * can be split into two courts, hung over the mats and round whatever is fixed to the walls.
     */
    private void across(Section section, DyeColor color) {
        for (int y = section.bottom(); y <= section.top(); y++) {
            for (int x = section.reach().get(y - section.bottom())[0]; x <= section.reach().get(y - section.bottom())[1]; x++) {
                if (fillable(x, y, section.z())) {
                    hang(x, y, section.z(), color);
                }
            }
        }
    }

    /**
     * Whether a curtain fits right across the section: clear of the doors' aisles, the seatbelt signs and the lights in
     * the walls, and nothing in its way but carpet and what stands against the walls (wall bars, a bench), so no rope
     * or light hangs in it. Its ends may stand against windows, which it meets edge on.
     */
    private boolean acrossFits(Section section) {
        int z = section.z();
        if (!clearOfDoorsAndSigns(z) || section.top() < floor + arch) {
            return false;
        }
        for (int y = section.bottom(); y <= section.top(); y++) {
            int[] reach = section.reach().get(y - section.bottom());
            if (isLight(reach[0] - 1, y, z) || isLight(reach[1] + 1, y, z)) {
                return false;
            }
            for (int x = reach[0] + 1; x < reach[1]; x++) {
                if (!fillable(x, y, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The section of the station nearest so many metres from the nose, within SNAP, that passes the test; or null. */
    @Nullable
    private Section near(double metres, Predicate<Section> fits) {
        int at = (int) Math.floor(metres * layout.scale());
        for (int off = 0; off <= SNAP; off++) {
            for (int z : off == 0 ? new int[]{at} : new int[]{at - off, at + off}) {
                Section section = section(z);
                if (fits.test(section)) {
                    return section;
                }
            }
        }
        return null;
    }

    private Section section(int z) {
        List<int[]> reach = new ArrayList<>();
        for (int y = floor + 1; ; y++) {
            int port = layout.besideWall(-1, y, z);
            int starboard = layout.besideWall(1, y, z);
            if (port == 0 && starboard == 0 && !layout.isOpen(0, y, z)) {
                return new Section(z, floor + 1, reach);
            }
            reach.add(new int[]{port, starboard});
        }
    }

    /**
     * Whether a wall fits right across the section: clear of the doors' aisles and the seatbelt signs, nothing in it
     * but open cabin, carpet and whole blocks it can be built round, the aisle under the archway free to walk, and
     * every block of hull beside it and over it plain skin.
     */
    private boolean wallFits(Section section) {
        int z = section.z();
        if (!clearOfDoorsAndSigns(z) || section.top() < crown() + LINTEL) {
            return false;
        }
        for (int y = section.bottom(); y <= section.top(); y++) {
            for (int x = section.reach().get(y - section.bottom())[0]; x <= section.reach().get(y - section.bottom())[1]; x++) {
                boolean passable = fillable(x, y, z) && !layout.stateAt(x, floor, z).is(Blocks.WATER);
                if (inArch(x, y) || inCrown(x, y) ? !passable : !fillable(x, y, z) && !whole(x, y, z)) {
                    return false;
                }
                if (!plainAround(section, x, y)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Whether a curtain fits across the aisle alone at the section: clear of the doors' aisles and the seatbelt signs,
     * nothing in the aisle but open cabin and carpet, and plain skin over it.
     */
    private boolean curtainFits(Section section) {
        int z = section.z();
        if (!clearOfDoorsAndSigns(z) || section.top() < floor + arch) {
            return false;
        }
        for (int y = section.bottom(); y <= section.top(); y++) {
            for (int x = -AISLE; x <= AISLE; x++) {
                if (section.contains(x, y) && (!fillable(x, y, z) || !section.contains(x, y + 1) && !layout.isPlainHull(x, y + 1, z))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Whether the station is clear of every door and the aisle in front of it, and of the seatbelt signs. */
    private boolean clearOfDoorsAndSigns(int z) {
        return !layout.doorAt(z) && !layout.doorAt(z + 1) && !layout.seatbeltSignAt(z);
    }

    /** Whether every block of hull beside, over or under this block of the section, but the floor, is plain skin. */
    private boolean plainAround(Section section, int x, int y) {
        int[][] around = {{x - 1, y}, {x + 1, y}, {x, y - 1}, {x, y + 1}};
        for (int[] at : around) {
            if (at[1] > floor && !section.contains(at[0], at[1]) && !layout.isPlainHull(at[0], at[1], section.z())) {
                return false;
            }
        }
        return true;
    }

    private boolean isLight(int x, int y, int z) {
        return layout.stateAt(x, y, z).is(layout.palette().light().getBlock());
    }

    /** Whether the block is in an archway's curtained opening: all of its width, up to where it rounds off. */
    private boolean inArch(int x, int y) {
        return Math.abs(x) <= span && y <= floor + arch;
    }

    /** The archway's top row, its crown over the middle, where it has stepped in a block for every row over its sides. */
    private int crown() {
        return floor + arch + span;
    }

    /** Whether the block is open in the archway's rounded top, over the curtain's rail. */
    private boolean inCrown(int x, int y) {
        int over = y - floor - arch;
        return over >= 1 && over <= span && Math.abs(x) <= span - over;
    }

    /**
     * What a wall is built of here: the fillets that round off its archway's top, a block in at each row up; the
     * archway's trim either side of it and over it; and the partition everywhere else.
     */
    private BlockState frame(int x, int y) {
        int over = y - floor - arch;
        if (over >= 1 && over <= span && Math.abs(x) == span - over + 1) {
            // Upside down, its full half outward (starboard is east), so the opening steps in toward the crown.
            return FILLET.setValue(StairBlock.FACING, x > 0 ? Direction.EAST : Direction.WEST);
        }
        boolean jamb = Math.abs(x) == span + 1 && y <= crown();
        boolean head = Math.abs(x) <= span + 1 && y > crown() && y <= crown() + LINTEL;
        return jamb || head ? TRIM : partition;
    }

    /** Whether a divider can go into this block of the cabin: open, or only carpet, which it takes the place of. */
    private boolean fillable(int x, int y, int z) {
        return layout.isOpen(x, y, z) || layout.stateAt(x, y, z).getBlock() instanceof CarpetBlock;
    }

    /** Whether the block here fills it whole, like a seat back, so a wall can be built round it. */
    private boolean whole(int x, int y, int z) {
        return layout.stateAt(x, y, z).isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    /** A piece of curtain hung across the cabin, drawn open, in the colour given; it is joined to the rest later. */
    private void hang(int x, int y, int z, DyeColor color) {
        layout.set(x, y, z, AerolargeRegistry.CABIN_CURTAIN.get().defaultBlockState()
            .setValue(CurtainBlock.FACING, Direction.NORTH).setValue(CurtainBlock.OPEN, true).setValue(CurtainBlock.COLOR, color));
        curtains.add(new BlockPos(x, y, z));
    }

    /**
     * A picture set into a wall, its middle column at x and its top row at y, the right way round from ahead of the
     * wall or from behind it; only where every block of it is the wall, so it is left out where the cabin is too small.
     */
    private void picture(int z, int middle, int top, String[] rows, boolean seenFromAhead) {
        int half = rows[0].length() / 2;
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length(); column++) {
                if (!layout.stateAt(middle + column - half, top - row, z).is(partition.getBlock())) {
                    return;
                }
            }
        }
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length(); column++) {
                BlockState wool = WOOL.get(rows[row].charAt(column));
                // Looking aft from ahead of the wall, the viewer's left is to starboard.
                int x = middle + (seenFromAhead ? half - column : column - half);
                if (wool != null) {
                    layout.set(x, top - row, z, wool);
                }
            }
        }
    }

    /**
     * Lavatories either side of the archway into the gym hall, built onto the back of its wall the way an airliner's
     * are into the monuments by its doors: each a cubicle from the archway's side out to the hull, between the wall and
     * the gym hall, under a ceiling at a room's height with a cabin light in it, kept on at night. A toilet stands
     * against the hull and a basin with its tap by the archway, the door between them opening onto the doors' aisle
     * under a picture of a person. The door is a wooden one, as no hull door is: one opened in flight does not let the
     * air out. Only on a side with room for all of it.
     */
    private void lavatories(Section wall, int gym) {
        int front = wall.z();
        int ceiling = floor + HEADROOM + 1;
        int inner = span + 1;
        int basin = inner + 1;
        int door = basin + 1;
        if (gym - front < 2) {
            return;
        }
        for (int side : new int[]{1, -1}) {
            int outer = Integer.MAX_VALUE;
            for (int z = front + 1; z <= gym; z++) {
                for (int y = floor + 1; y <= ceiling; y++) {
                    outer = Math.min(outer, Math.abs(layout.besideWall(side, y, z)));
                }
            }
            if (outer <= door || !clear(side, inner, front + 1, gym, ceiling)) {
                continue;
            }
            // The corner where the cubicle's side meets its back is left out: it is closed without it, and the gym
            // hall's first station keeps the room beside the hoop free (the hide and seek button stands there).
            for (int z = front + 1; z < gym; z++) {
                for (int y = floor + 1; y <= ceiling; y++) {
                    layout.set(side * inner, y, z, partition);
                }
            }
            for (int y = floor + 1; y <= ceiling; y++) {
                for (int x = inner + 1; x <= Math.abs(layout.besideWall(side, y, gym)); x++) {
                    layout.set(side * x, y, gym, partition);
                }
            }
            for (int z = front + 1; z < gym; z++) {
                for (int x = basin; x <= Math.abs(layout.besideWall(side, ceiling, z)); x++) {
                    layout.set(side * x, ceiling, z, partition);
                }
            }
            layout.set(side * (basin + outer) / 2, ceiling, (front + gym) / 2, CabinLightBlock.keptOn(layout.palette().light()));
            layout.set(side * outer, floor + 1, front + 1,
                AerolargeRegistry.VACUUM_TOILET.get().defaultBlockState().setValue(VacuumToiletBlock.FACING, side > 0 ? Direction.WEST : Direction.EAST));
            layout.set(side * basin, floor + 1, front + 1,
                Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, LayeredCauldronBlock.MAX_FILL_LEVEL));
            layout.set(side * basin, floor + 2, front + 1, Blocks.TRIPWIRE_HOOK.defaultBlockState().setValue(TripWireHookBlock.FACING, Direction.SOUTH));
            // Facing out of the cubicle, so it closes flush with the cubicle's side of the wall; hinged hull side.
            BlockState leaf = Blocks.BIRCH_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HINGE, side > 0 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT);
            layout.set(side * door, floor + 1, front, leaf.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            layout.set(side * door, floor + 2, front, leaf.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            picture(front, side * door, floor + 2 + PERSON.length, PERSON, true);
        }
    }

    /**
     * Whether the cabin is open all through a cubicle's walls and inside: from x out to the hull, z to z (its back,
     * at the last, from a block further out), up to y.
     */
    private boolean clear(int side, int inner, int fromZ, int toZ, int top) {
        for (int z = fromZ; z <= toZ; z++) {
            for (int y = floor + 1; y <= top; y++) {
                for (int x = z == toZ ? inner + 1 : inner; x <= Math.abs(layout.besideWall(side, y, z)); x++) {
                    if (!layout.isOpen(side * x, y, z)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Joins every piece of curtain hung to its neighbours in the same curtain, as placing them by hand would. */
    private void joinCurtains() {
        for (BlockPos pos : curtains) {
            BlockState piece = layout.stateAt(pos.getX(), pos.getY(), pos.getZ());
            BlockPos left = pos.relative(CurtainBlock.leftOf(piece.getValue(CurtainBlock.FACING)));
            BlockPos right = pos.relative(CurtainBlock.leftOf(piece.getValue(CurtainBlock.FACING)).getOpposite());
            layout.set(pos.getX(), pos.getY(), pos.getZ(), CurtainBlock.joinedTo(piece,
                layout.stateAt(left.getX(), left.getY(), left.getZ()), layout.stateAt(right.getX(), right.getY(), right.getZ()),
                layout.stateAt(pos.getX(), pos.getY() + 1, pos.getZ()), layout.stateAt(pos.getX(), pos.getY() - 1, pos.getZ())));
        }
    }
}
