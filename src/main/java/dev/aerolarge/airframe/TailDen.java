package dev.aerolarge.airframe;

import dev.aerolarge.Aerolarge;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.LightStripBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The kids' den in the tail of the furnished airliner, behind a secret door. The lounge ends behind its bar at a wall
 * of bookshelves across the cabin, hull panels above them up to the crown; one of the bookshelves is a door, its
 * glowing book the hint. Behind it the tail room and the tail cone over it are the den, all the way up to where the
 * tail closes in: a landing with two cushions, a pit of coloured balls to sink into, then the cone's steps up, each
 * carpeted its own colour of the rainbow, wool mattresses for bunks along the walls where a step is wide enough and a
 * cushion where it is not, fairy lights along both walls (on the dimmer, like every light aboard) and a treasure chest
 * at the very top. Bar the bookcase it is all wool, carpet and light fittings, kept light because it sits far aft of
 * the centre of mass; the gear and the tailplane's trim go on after the fit-out, so they balance it. It only ever goes
 * into open cabin space, but for taking down a lounge pendant light hanging over it, and is left out where the tail
 * is too small to hold it.
 */
final class TailDen {
    /** How many rows of shelves the bookcase has; above them the wall is the hull's panels, a quarter the weight. */
    private static final int BOOKCASE_ROWS = 4;
    /** A player's height in blocks: the den runs on up the tail as long as it has this much room over its floor. */
    private static final int HEADROOM = 2;
    /** The fewest stations behind the wall that make a den: a landing, the ball pit and a step up. */
    private static final int LEAST_STATIONS = 4;
    /** How many blocks deep the ball pit is. */
    private static final int PIT_DEPTH = 2;
    /** How far above the den's floor the fairy lights hang, at most. */
    private static final int LIGHTS_ABOVE = 3;
    /** How far out from the middle a step's wall has to be, in blocks, for a bunk along it with a way up between. */
    private static final int BUNK_ROOM = 2;
    /** The landing is carpeted in the first colour, each step up the tail in the next. */
    private static final DyeColor[] RAINBOW = {
        DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.LIGHT_BLUE, DyeColor.PURPLE, DyeColor.MAGENTA
    };
    /** The cushions' and the bunks' blankets' colours. */
    private static final DyeColor[] SOFT_COLOURS = {DyeColor.PINK, DyeColor.CYAN, DyeColor.YELLOW, DyeColor.LIME};
    /** The treasure chest's loot: cookies, a golden apple, a signal flare, a basketball and fireworks. */
    private static final ResourceKey<LootTable> TREASURE =
        ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "chests/den_treasure"));

    private final AirframeLayout layout;
    private final int floor;
    /** Far enough out from the middle to reach past either wall, in blocks. */
    private final int reach;
    /** How many soft colours have been taken so far. */
    private int softColours;

    TailDen(AirframeLayout layout) {
        this.layout = layout;
        this.floor = layout.floorY();
        this.reach = (int) Math.ceil(AirframeShape.WIDTH / 2 * layout.scale()) + 1;
    }

    /** A station of the den, and the row its floor is on: the first row of open space, over the belly's step. */
    private record Station(int z, int y) {
    }

    /**
     * Fits the den behind the lounge, which runs from station {@code loungeFrom} to {@code loungeTo}. The wall goes
     * just behind the lounge's last furnished row: the tail narrows through the lounge, so its last rows can be too
     * narrow to furnish, and those are the den's.
     */
    void apply(int loungeFrom, int loungeTo) {
        int wall = loungeTo + 1;
        while (wall > loungeFrom && layout.isOpen(0, floor + 1, wall - 1)) {
            wall--;
        }
        List<Station> den = stations(wall + 1);
        if (wall <= loungeFrom || den.size() < LEAST_STATIONS || !layout.isOpen(0, floor + 1, wall) || !layout.isOpen(0, floor + 2, wall)) {
            return;
        }
        bookcase(wall);
        den.forEach(station -> unhang(station.z()));
        // The pit fills the first stretch past the landing that keeps one level.
        int pitEnd = 1;
        while (pitEnd + 1 < den.size() && den.get(pitEnd + 1).y() == den.get(1).y()) {
            pitEnd++;
        }
        Station landing = den.getFirst();
        for (int side : new int[]{1, -1}) {
            cushion(layout.besideWall(side, landing.y(), landing.z()), landing);
        }
        carpet(landing, RAINBOW[0]);
        for (int i = 1; i <= pitEnd; i++) {
            pit(den.get(i));
        }
        List<Station> steps = den.subList(pitEnd + 1, den.size());
        treasure(den.getLast());
        for (int side : new int[]{1, -1}) {
            bunks(side, steps);
        }
        int step = 0;
        for (int i = 0; i < steps.size(); i++) {
            if (i == 0 || steps.get(i).y() != steps.get(i - 1).y()) {
                step++;
            }
            carpet(steps.get(i), RAINBOW[step % RAINBOW.length]);
        }
        den.forEach(this::fairyLights);
    }

    /** The den's stations from {@code from} aft, while its floor climbs no more than a step at a time and has headroom. */
    private List<Station> stations(int from) {
        List<Station> den = new ArrayList<>();
        int y = floor + 1;
        for (int z = from; ; z++) {
            if (!layout.isOpen(0, y, z)) {
                y++;
            }
            int room = 0;
            while (layout.isOpen(0, y + room, z)) {
                room++;
            }
            if (room < HEADROOM) {
                return den;
            }
            den.add(new Station(z, y));
        }
    }

    /** The wall across the cabin at station z, bookshelves at the bottom and hull above, and the secret door in it. */
    private void bookcase(int z) {
        int crown = layout.ceiling(0, z);
        BlockState door = AerolargeRegistry.SECRET_BOOKSHELF.get().defaultBlockState()
            .setValue(DoorBlock.FACING, Direction.SOUTH)
            .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
        layout.furnish(0, floor + 1, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        layout.furnish(0, floor + 2, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        for (int y = floor + 1; y <= crown; y++) {
            BlockState shelf = y <= floor + BOOKCASE_ROWS ? Blocks.BOOKSHELF.defaultBlockState() : layout.palette().hull();
            for (int x = -reach; x <= reach; x++) {
                layout.furnish(x, y, z, shelf);
            }
        }
    }

    /**
     * Takes down a pendant light hanging down the middle of a station of the den, cable and all: the lounge's row of
     * them runs on over the tail's narrowest rows, which are the den's, and the den is lit by its fairy lights.
     */
    private void unhang(int z) {
        int top = floor + (int) Math.ceil(AirframeShape.HEIGHT * layout.scale());
        for (int y = floor + 1; y <= top; y++) {
            BlockState state = layout.stateAt(0, y, z);
            if (state.is(AerolargeRegistry.PENDANT_LIGHT.get()) || state.is(AerolargeRegistry.LIGHT_CABLE.get())) {
                layout.set(0, y, z, Blocks.AIR.defaultBlockState());
            }
        }
    }

    /** Carpet over the station's floor, wherever it has the belly or the floor under it. */
    private void carpet(Station station, DyeColor colour) {
        BlockState carpet = carpet(colour);
        for (int x = -reach; x <= reach; x++) {
            if (!layout.isOpen(x, station.y() - 1, station.z())) {
                layout.furnish(x, station.y(), station.z(), carpet);
            }
        }
    }

    /** The ball pit at a station: its floor's row and the row over it filled with balls. */
    private void pit(Station station) {
        BlockState balls = AerolargeRegistry.BALL_PIT.get().defaultBlockState();
        for (int y = station.y(); y < station.y() + PIT_DEPTH; y++) {
            for (int x = -reach; x <= reach; x++) {
                if (!layout.isOpen(x, y - 1, station.z())) {
                    layout.furnish(x, y, station.z(), balls);
                }
            }
        }
    }

    /**
     * Bunks along one wall of the steps: a wool mattress the length of each run of stations, at one level, wide enough
     * for one along the wall with a way up beside it, a blanket on it and a pillow at its aft end; a run too short for
     * a bunk gets a cushion instead.
     */
    private void bunks(int side, List<Station> steps) {
        List<Station> run = new ArrayList<>();
        for (Station station : steps) {
            boolean roomy = Math.abs(layout.besideWall(side, station.y(), station.z())) >= BUNK_ROOM;
            if (!run.isEmpty() && (!roomy || station.y() != run.getFirst().y())) {
                bunk(side, run);
                run.clear();
            }
            if (roomy) {
                run.add(station);
            }
        }
        if (!run.isEmpty()) {
            bunk(side, run);
        }
    }

    private void bunk(int side, List<Station> run) {
        if (run.size() == 1) {
            Station station = run.getFirst();
            cushion(layout.besideWall(side, station.y(), station.z()), station);
            return;
        }
        BlockState blanket = carpet(softColour());
        for (Station station : run) {
            int x = layout.besideWall(side, station.y(), station.z());
            if (layout.isOpen(x, station.y(), station.z()) && layout.isOpen(x, station.y() + 1, station.z()) && layout.isOpen(x, station.y() + 2, station.z())) {
                layout.furnish(x, station.y(), station.z(), Blocks.LIGHT_BLUE_WOOL.defaultBlockState());
                layout.furnish(x, station.y() + 1, station.z(), station == run.getLast() ? Blocks.WHITE_CARPET.defaultBlockState() : blanket);
            }
        }
    }

    /** One of Create's seats in the next soft colour, to sit on, against the wall at x (none where the wall closes in). */
    private void cushion(int x, Station station) {
        DyeColor colour = softColour();
        if (x != 0) {
            AirframePalette.seat(colour).ifPresent(seat -> layout.furnish(x, station.y(), station.z(), seat));
        }
    }

    /** The cushions' and blankets' colours, taken in turn. */
    private DyeColor softColour() {
        return SOFT_COLOURS[softColours++ % SOFT_COLOURS.length];
    }

    /** A string of fairy lights along each wall at the station, as high as the lights hang where there is room for it. */
    private void fairyLights(Station station) {
        for (int side : new int[]{1, -1}) {
            BlockState lights = AerolargeRegistry.FAIRY_LIGHTS.get().defaultBlockState().setValue(LightStripBlock.FACING, side > 0 ? Direction.EAST : Direction.WEST);
            for (int y = station.y() + LIGHTS_ABOVE; y > station.y(); y--) {
                int x = layout.besideWall(side, y, station.z());
                if (x != 0 && layout.isOpen(x, y, station.z())) {
                    layout.furnish(x, y, station.z(), lights);
                    break;
                }
            }
        }
    }

    /** The treasure chest at the very top of the den, facing the way up, filled when the airliner is built. */
    private void treasure(Station top) {
        if (!layout.isOpen(0, top.y(), top.z())) {
            return;
        }
        layout.set(0, top.y(), top.z(), Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH));
        layout.fittings().put(new BlockPos(0, top.y(), top.z()), (blockEntity, rotation) -> {
            if (blockEntity instanceof RandomizableContainerBlockEntity chest) {
                chest.setLootTable(TREASURE, 0);
            }
        });
    }

    private static BlockState carpet(DyeColor colour) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.withDefaultNamespace(colour.getSerializedName() + "_carpet")).defaultBlockState();
    }
}
