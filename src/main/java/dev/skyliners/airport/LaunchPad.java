package dev.skyliners.airport;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.airframe.Fitting;
import dev.skyliners.airframe.Layout;
import dev.skyliners.airframe.RocketLayout;
import dev.skyliners.block.FloodlightBlock;
import dev.skyliners.block.LaunchPadBlockEntity;
import dev.skyliners.block.NavLightBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A launch pad for the rocket (see {@link RocketLayout}), sized for her at the scale it is built at, as its blueprint
 * lays it down: a square concrete deck in place of the ground, with a band of hazard stripes round its edge.
 *
 * <p>In its middle a round steel grate as wide as she is covers a flame pit, with the pad's hub at the very middle (see
 * {@link LaunchPadBlockEntity}), under her axis when she stands on it. The grate is solid, so what looks down for the
 * ground from under her, her autopilot's radio altitude among them, finds the deck there and not the pit's floor. A
 * steel deflector in the pit slopes down to the west into a flame trench running to the deck's west edge, where a ramp
 * brings it back up. Her four fins' feet stand on four yellow plates round the grate.
 *
 * <p>Everything else keeps clear of what she does on the ground. Her airstairs come down at her front, the north, onto
 * a painted walkway with chevrons pointing at her door; her rover is lowered at her back, the south, onto open deck; so
 * the trench goes west, and in the east stands the service tower, a white steel lattice as tall as she is under a
 * lightning mast, with a ladder up its core and an arm reaching toward her at her entry floor and at her cockpit.
 * Nothing of the pad touches her above the deck: her assembly takes only what touches her (see {@code
 * ZeppelinAssembly}), and what is left pressed against her would hold her as she flies off. At each corner a mast
 * carries a floodlight.
 *
 * <p>Drawn nose north as every layout is, y 0 being the deck, its front (the way the walkway comes in from) at z 0, and
 * turned to face whoever built it. Only its blocks are kept: the air over the deck is worked out as it is handed over,
 * so that whatever stood there is cleared first.
 */
public final class LaunchPad implements Layout {
    /** How far past her frame the deck reaches each way, in blocks: the service tower and the deck round it. */
    private static final int PAST_HER_FRAME = 17;
    /** How many rows under the deck the foundation goes down, whatever the ground is doing under it. */
    private static final int FOUNDATION = 9;
    /** How deep the flame pit and its trench are under the deck at most, in rows: less where the world ends sooner. */
    private static final int DEEPEST_PIT = 6;
    /** How many rows over the deck everything is cleared. */
    private static final int HEADROOM = 40;
    /** How far past the grate's edge the dark steel ring round it reaches, in blocks. */
    private static final double RING_REACH = 1.5;
    /** How wide the band of hazard stripes round the deck is, and how wide each stripe, in blocks. */
    private static final int HAZARD_BAND = 2;
    private static final int HAZARD_STRIPE = 4;
    /** How far apart the joints between the deck's slabs are, in blocks. */
    private static final int SLAB = 8;
    /** How much narrower than her body the trench is either side of its middle, in blocks: clear of her fins' feet. */
    private static final int TRENCH_NARROWER = 3;
    /** How far apart the lamps along the trench's floor are, in blocks. */
    private static final int TRENCH_LAMPS = 6;
    /**
     * How far out from the grate's edge the scorching reaches, as a share of her radius, and how much of it is black.
     */
    private static final double SCORCH_REACH = 1.2;
    private static final double SCORCH_DENSEST = 0.9;
    private static final double SCORCH_BLACK = 0.45;
    /**
     * The walkway from the front edge to the grate: how far either side of its middle it reaches, three blocks of it
     * under her airstairs and a white edge each side; how far apart its chevrons and its lamps are, in blocks.
     */
    private static final int WALKWAY = 3;
    private static final int CHEVRONS = 6;
    private static final int WALKWAY_LAMPS = 8;
    /** The service tower: how far past her frame its near face is, how wide it is, and how tall one bay of it is. */
    private static final int TOWER_GAP = 3;
    private static final int TOWER_WIDTH = 9;
    private static final int BAY = 8;
    /** How many rows under the tower's top its red bands are painted, and how often. */
    private static final int RED_BANDS = 24;
    private static final int RED_EVERY = 8;
    /** How tall the lightning mast on the tower's top is, in rows. */
    private static final int TOWER_MAST = 12;
    /** How many blocks an arm of the tower stops short of her skin: more than she drifts lifting off. */
    private static final int ARM_GAP = 5;
    /**
     * How far either side of its middle an arm's walkway reaches, in blocks; its floor runs on a block further each
     * way, under its railings.
     */
    private static final int ARM_HALF = 1;
    /** The masts at the deck's corners: how far in from the hazard band they stand, how tall, how long their arms. */
    private static final int MAST_IN = 2;
    private static final int MAST_HEIGHT = 18;
    private static final int MAST_ARM = 3;

    private static final BlockState DECK = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState JOINT = Blocks.POLISHED_ANDESITE.defaultBlockState();
    private static final BlockState SOOT = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState PIT_WALL = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState MAST = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState PAINT = Blocks.WHITE_CONCRETE.defaultBlockState();
    private static final BlockState YELLOW = Blocks.YELLOW_CONCRETE.defaultBlockState();
    private static final BlockState BLACK = Blocks.BLACK_CONCRETE.defaultBlockState();
    private static final BlockState LAMP = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState PIT_LAMP = Blocks.OCHRE_FROGLIGHT.defaultBlockState();
    private static final BlockState FOOTING = Blocks.STONE_BRICKS.defaultBlockState();
    private static final BlockState STEEL = Blocks.IRON_BLOCK.defaultBlockState();
    private static final BlockState PIT_FLOOR = Blocks.POLISHED_BLACKSTONE.defaultBlockState();
    private static final BlockState RING = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState LATTICE = Blocks.WHITE_CONCRETE.defaultBlockState();
    private static final BlockState RED = Blocks.RED_CONCRETE.defaultBlockState();
    private static final BlockState PLATFORM = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState RAILING = Blocks.IRON_BARS.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final double scale;
    /**
     * How far out the deck reaches each way from its middle, the grate's radius, and how far the trench reaches west.
     */
    private final int half;
    private final int grate;
    private final int trenchEnd;
    /** How deep the flame pit and its trench are under the deck, in rows; none where the world has no room for one. */
    private final int pit;
    /** How far either side of its middle the trench reaches. */
    private final int trenchHalf;
    /** The tower's near face, its far face and its core's column, in x. */
    private final int towerNear;
    private final int towerFar;
    private final int towerCore;
    /** Every block but the air over the deck, by position from the deck's middle. */
    private final Map<BlockPos, BlockState> cells = new HashMap<>();
    /** How many blocks of air {@link #inOrder} hands over, found once everything else stands. */
    private int air;

    private LaunchPad(double scale, int roomUnder) {
        this.scale = scale;
        this.pit = Math.min(DEEPEST_PIT, roomUnder);
        this.half = halfAt(scale);
        this.grate = Mth.floor(RocketLayout.radiusAt(scale));
        this.trenchEnd = half - HAZARD_BAND - 1;
        this.trenchHalf = Math.max(1, grate - TRENCH_NARROWER);
        this.towerNear = RocketLayout.reachAt(scale) + TOWER_GAP;
        this.towerFar = towerNear + TOWER_WIDTH - 1;
        this.towerCore = towerNear + TOWER_WIDTH / 2;
    }

    /**
     * The pad for the rocket at the configured scale, with so many rows of the world under its deck: on a superflat
     * world, whose ground is only a few rows thick, its pit stops at the world's bottom and does not open onto the
     * void.
     */
    public static LaunchPad create(int roomUnder) {
        LaunchPad pad = new LaunchPad(SkylinersConfig.SCALE.get(), roomUnder);
        pad.deck();
        pad.flamePit();
        pad.trenchRailings();
        pad.serviceTower();
        pad.cornerMasts();
        pad.joinRailings();
        pad.countAir();
        return pad;
    }

    /** How far out a pad built at a scale reaches each way from its middle, in blocks. */
    public static int halfAt(double scale) {
        return RocketLayout.reachAt(scale) + PAST_HER_FRAME;
    }

    private void set(int x, int y, int z, BlockState state) {
        cells.put(new BlockPos(x, y, z), state);
    }

    /** The deck and the foundation under it. */
    private void deck() {
        Set<Long> feet = new HashSet<>();
        Set<Long> plates = new HashSet<>();
        for (int[] foot : RocketLayout.footColumns(scale)) {
            feet.add(column(foot[0], foot[1]));
            addPlate(plates, foot);
        }
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                deckColumn(x, z, feet, plates);
            }
        }
    }

    /** A foot's plate: its own column and every column round it. */
    private static void addPlate(Set<Long> plates, int[] foot) {
        for (int x = foot[0] - 1; x <= foot[0] + 1; x++) {
            for (int z = foot[1] - 1; z <= foot[1] + 1; z++) {
                plates.add(column(x, z));
            }
        }
    }

    private static long column(int x, int z) {
        return BlockPos.asLong(x, 0, z);
    }

    private void deckColumn(int x, int z, Set<Long> feet, Set<Long> plates) {
        set(x, 0, z, surface(x, z, feet, plates));
        for (int y = -FOUNDATION; y < 0; y++) {
            set(x, y, z, y >= -DEEPEST_PIT - 1 ? PIT_WALL : FOOTING);
        }
    }

    /** What the deck is painted at a column. */
    private BlockState surface(int x, int z, Set<Long> feet, Set<Long> plates) {
        double out = Math.hypot(x, z);
        if (out <= grate) {
            return grateAt(x, z);
        }
        if (out <= grate + RING_REACH) {
            return RING;
        }
        if (Math.abs(x) > half - HAZARD_BAND || Math.abs(z) > half - HAZARD_BAND) {
            return Math.floorMod(x + z, 2 * HAZARD_STRIPE) < HAZARD_STRIPE ? YELLOW : BLACK;
        }
        if (plates.contains(column(x, z))) {
            return feet.contains(column(x, z)) ? BLACK : YELLOW;
        }
        if (z < 0 && Math.abs(x) <= WALKWAY) {
            return walkway(x, z);
        }
        return bareDeck(x, z, out);
    }

    /** The grate, with the pad's hub at its very middle, which knows the pad's size and which way it faces. */
    private static BlockState grateAt(int x, int z) {
        return (x == 0 && z == 0 ? SkylinersRegistry.LAUNCH_PAD_HUB : SkylinersRegistry.LAUNCH_PAD_GRATE).get().defaultBlockState();
    }

    /** The walkway's paint: a white edge with lamps set into it, and yellow chevrons between pointing at her door. */
    private static BlockState walkway(int x, int z) {
        if (Math.abs(x) == WALKWAY) {
            return Math.floorMod(z, WALKWAY_LAMPS) == 0 ? LAMP : PAINT;
        }
        return Math.floorMod(z + Math.abs(x), CHEVRONS) < 2 ? YELLOW : DECK;
    }

    /** The deck's slabs and their joints, scorched black round the grate, the same way every time it is built. */
    private BlockState bareDeck(int x, int z, double out) {
        double scorch = SCORCH_DENSEST * (1 - (out - grate) / (RocketLayout.radiusAt(scale) * SCORCH_REACH));
        if (chance(x, z) < scorch) {
            return chance(z, x) < SCORCH_BLACK ? BLACK : SOOT;
        }
        return Math.floorMod(x, SLAB) == 0 || Math.floorMod(z, SLAB) == 0 ? JOINT : DECK;
    }

    /** A number from 0 to 1 that a column always gets. */
    private static double chance(int x, int z) {
        return Math.floorMod(Mth.getSeed(x, 0, z), 1024) / 1024.0;
    }

    /**
     * The pit under the grate and the trench running west from it, cut out of the foundation: the steel deflector
     * sloping down from the east, the trench's flat floor with its lamps, and the ramp back up at its far end.
     */
    private void flamePit() {
        if (pit < 1) {
            return;
        }
        for (int x = -trenchEnd; x <= grate; x++) {
            for (int z = -grate; z <= grate; z++) {
                pitColumn(x, z);
            }
        }
    }

    private void pitColumn(int x, int z) {
        boolean underGrate = Math.hypot(x, z) <= grate;
        boolean inTrench = x < 0 && Math.abs(z) <= trenchHalf;
        if (!underGrate && !inTrench) {
            return;
        }
        int floor = pitFloorRow(x);
        for (int y = floor + 1; y <= (underGrate ? -1 : 0); y++) {
            set(x, y, z, AIR);
        }
        set(x, floor, z, pitFloor(x, z, floor));
    }

    /** The row of the pit's floor at a column: as high as the deflector or the far end's ramp holds it up. */
    private int pitFloorRow(int x) {
        int deflector = -1 - (grate - x) * (pit - 1) / Math.max(1, grate + grate / 2);
        int ramp = -1 - (x + trenchEnd);
        return Math.max(-pit, Math.max(deflector, ramp));
    }

    private BlockState pitFloor(int x, int z, int floor) {
        if (floor > -pit && x > -grate) {
            return STEEL;
        }
        return z == 0 && Math.floorMod(x, TRENCH_LAMPS) == 0 ? PIT_LAMP : PIT_FLOOR;
    }

    /** A railing along either side of the trench, from the ring round the grate to the hazard band. */
    private void trenchRailings() {
        int from = (int) Math.ceil(Math.hypot(grate + RING_REACH, trenchHalf + 1));
        for (int x = -trenchEnd; x <= -from; x++) {
            set(x, 1, trenchHalf + 1, RAILING);
            set(x, 1, -trenchHalf - 1, RAILING);
        }
    }

    /** The tower east of her: its lattice, its core with the ladder, its platforms and arms, its mast. */
    private void serviceTower() {
        int top = RocketLayout.heightRows(scale);
        for (int x = towerNear; x <= towerFar; x++) {
            for (int z = -TOWER_WIDTH / 2; z <= TOWER_WIDTH / 2; z++) {
                towerColumn(x, z, top);
            }
        }
        for (int y = 1; y <= top; y++) {
            set(towerCore, y, 0, LATTICE);
            set(towerCore - 1, y, 0, ladder());
        }
        int[] stories = RocketLayout.storyRows(scale);
        towerArm(stories[0] + 1);
        towerArm(stories[stories.length - 1] + 1);
        towerPlatform(top);
        towerMast(top);
    }

    private void towerColumn(int x, int z, int top) {
        for (int y = 1; y <= top; y++) {
            set(x, y, z, latticeAt(x, y, z, top));
        }
    }

    /**
     * A block of the tower's lattice: four columns at its corners, a beam round it every bay above the first, the first
     * standing open on its legs, and a cross of bracing on each face in every bay over that; red toward the top.
     */
    private BlockState latticeAt(int x, int y, int z, int top) {
        int across = x - towerNear;
        int along = z + TOWER_WIDTH / 2;
        int last = TOWER_WIDTH - 1;
        boolean edgeX = across == 0 || across == last;
        boolean edgeZ = along == 0 || along == last;
        BlockState paint = top - y < RED_BANDS && Math.floorMod(top - y, 2 * RED_EVERY) < RED_EVERY ? RED : LATTICE;
        if (edgeX && edgeZ) {
            return paint;
        }
        if (!edgeX && !edgeZ || y <= BAY) {
            return AIR;
        }
        int rise = (y - 1) % BAY;
        int run = edgeX ? along : across;
        return rise == 0 || run == rise * last / BAY || run == last - rise * last / BAY ? paint : AIR;
    }

    /** A rung of the ladder up the tower's core, on the core's side toward her. */
    private static BlockState ladder() {
        return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST);
    }

    /**
     * An arm reaching toward her over a floor of the tower's at a row, stopping short of her skin, and its platform.
     */
    private void towerArm(int y) {
        towerPlatform(y);
        int end = (int) Math.ceil(RocketLayout.radiusAt(scale)) + ARM_GAP;
        for (int x = end; x < towerNear; x++) {
            armColumn(x, y);
        }
        for (int z = -ARM_HALF; z <= ARM_HALF; z++) {
            set(towerNear, y, z, PLATFORM);
        }
        for (int z = -ARM_HALF - 1; z <= ARM_HALF + 1; z++) {
            set(end, y + 1, z, RAILING);
        }
        for (int z = -ARM_HALF; z <= ARM_HALF; z++) {
            set(towerNear, y + 1, z, AIR);
            set(towerNear, y + 2, z, AIR);
        }
    }

    /** A row of an arm across its length: its floor, and a railing standing on the floor's edge at either side. */
    private void armColumn(int x, int y) {
        for (int z = -ARM_HALF - 1; z <= ARM_HALF + 1; z++) {
            set(x, y, z, PLATFORM);
        }
        set(x, y + 1, ARM_HALF + 1, RAILING);
        set(x, y + 1, -ARM_HALF - 1, RAILING);
    }

    /** A floor across the tower's inside at a row, the ladder and the core coming up through it. */
    private void towerPlatform(int y) {
        for (int x = towerNear + 1; x < towerFar; x++) {
            for (int z = -TOWER_WIDTH / 2 + 1; z < TOWER_WIDTH / 2; z++) {
                platformBlock(x, y, z);
            }
        }
    }

    private void platformBlock(int x, int y, int z) {
        boolean core = z == 0 && (x == towerCore || x == towerCore - 1);
        if (!core) {
            set(x, y, z, PLATFORM);
        }
    }

    /** The lightning mast on the tower's top, with a flashing beacon over its rod, and a red light at each corner. */
    private void towerMast(int top) {
        for (int y = top + 1; y < top + TOWER_MAST; y++) {
            set(towerCore, y, 0, RAILING);
        }
        set(towerCore, top + TOWER_MAST, 0, STEEL);
        set(towerCore, top + TOWER_MAST + 1, 0, navLight(NavLightBlock.Kind.BEACON));
        for (int x : new int[]{towerNear, towerFar}) {
            set(x, top + 1, -TOWER_WIDTH / 2, navLight(NavLightBlock.Kind.RED));
            set(x, top + 1, TOWER_WIDTH / 2, navLight(NavLightBlock.Kind.RED));
        }
    }

    private static BlockState navLight(NavLightBlock.Kind kind) {
        return SkylinersRegistry.NAV_LIGHT.get().defaultBlockState().setValue(NavLightBlock.FACING, Direction.UP).setValue(NavLightBlock.KIND, kind);
    }

    /** A mast at each corner of the deck, with an arm toward its middle and a floodlight under the arm's end. */
    private void cornerMasts() {
        int out = half - HAZARD_BAND - MAST_IN;
        for (int x : new int[]{-out, out}) {
            cornerMast(x, -out, x < 0 ? Direction.EAST : Direction.WEST);
            cornerMast(x, out, x < 0 ? Direction.EAST : Direction.WEST);
        }
    }

    private void cornerMast(int x, int z, Direction toMiddle) {
        for (int y = 1; y <= MAST_HEIGHT; y++) {
            set(x, y, z, MAST);
        }
        for (int reach = 1; reach <= MAST_ARM; reach++) {
            set(x + toMiddle.getStepX() * reach, MAST_HEIGHT, z, STEEL);
        }
        set(x, MAST_HEIGHT + 1, z, navLight(NavLightBlock.Kind.RED));
        if (SkylinersConfig.FLOODLIGHTS.get()) {
            set(x + toMiddle.getStepX() * MAST_ARM, MAST_HEIGHT - 1, z,
                SkylinersRegistry.FLOODLIGHT.get().defaultBlockState().setValue(FloodlightBlock.FACING, toMiddle));
        } else {
            set(x + toMiddle.getStepX() * MAST_ARM, MAST_HEIGHT - 1, z, LAMP);
        }
    }

    /** Joins every railing to the railings and solid blocks beside it, as they are not updated as they are laid. */
    private void joinRailings() {
        for (Map.Entry<BlockPos, BlockState> cell : cells.entrySet()) {
            if (cell.getValue().is(Blocks.IRON_BARS)) {
                cell.setValue(joined(cell.getKey(), cell.getValue()));
            }
        }
    }

    private BlockState joined(BlockPos at, BlockState bars) {
        BlockState state = bars;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockState next = cells.get(at.relative(side));
            boolean joins = next != null && (next.is(Blocks.IRON_BARS) || next.isSolid());
            state = state.setValue(sideOf(side), joins);
        }
        return state;
    }

    private static BooleanProperty sideOf(Direction side) {
        return switch (side) {
            case NORTH -> CrossCollisionBlock.NORTH;
            case EAST -> CrossCollisionBlock.EAST;
            case SOUTH -> CrossCollisionBlock.SOUTH;
            default -> CrossCollisionBlock.WEST;
        };
    }

    private void countAir() {
        air = 0;
        forEachAir(ignored -> air++);
    }

    /** Hands over the air over the deck, wherever nothing else stands. */
    private void forEachAir(Consumer<BlockPos> cell) {
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                airColumn(x, z, cell);
            }
        }
    }

    private void airColumn(int x, int z, Consumer<BlockPos> cell) {
        for (int y = 1; y <= HEADROOM; y++) {
            BlockPos at = new BlockPos(x, y, z);
            if (!cells.containsKey(at)) {
                cell.accept(at);
            }
        }
    }

    /** A position from the deck's middle as it is handed over, from the front edge at z 0. */
    private BlockPos handed(BlockPos fromMiddle) {
        return fromMiddle.offset(0, 0, half);
    }

    @Override
    public void inOrder(BiConsumer<BlockPos, BlockState> cell) {
        forEachAir(at -> cell.accept(handed(at), AIR));
        List<Map.Entry<BlockPos, BlockState>> sorted = new ArrayList<>(cells.entrySet());
        sorted.sort((a, b) -> Integer.compare(a.getKey().getY(), b.getKey().getY()));
        sorted.forEach(entry -> cell.accept(handed(entry.getKey()), entry.getValue()));
    }

    @Override
    public int size() {
        return air + cells.size();
    }

    @Override
    public Map<BlockPos, Fitting> fittings() {
        return Map.of(handed(BlockPos.ZERO), this::fitHub);
    }

    /** Tells the hub how big the pad is and which way it faces, so a rocket can be set down on it. */
    private void fitHub(BlockEntity blockEntity, Rotation rotation) {
        if (blockEntity instanceof LaunchPadBlockEntity hub) {
            hub.laid(half, rotation.rotate(Direction.SOUTH));
        }
    }

    /** The deck takes the place of the ground, so nothing at or under its row counts as being in the way. */
    @Override
    public boolean intoTheGround(BlockPos cell) {
        return cell.getY() <= 0;
    }

    /** Assembled by nobody: it stays on the ground. */
    @Override
    public boolean glued() {
        return false;
    }

    @Override
    public String message(String what) {
        return "message.skyliners.launch_pad_" + what;
    }
}
