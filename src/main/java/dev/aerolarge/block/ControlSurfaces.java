package dev.aerolarge.block;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.block.AutopilotBlockEntity.Channel;
import dev.aerolarge.compat.SableFlight;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * The autopilot's control surfaces, an experiment switched on by {@code controlSurfaces} in the server config: ailerons
 * at the outer wings' trailing edges, and the tailplane as an elevator, made of the wings' own lifting blocks. Sable
 * pushes every lifting block along the way it faces, as hard as it goes through the air and as thick as the air is,
 * whatever angle the air meets it at, and holds it back most against the air coming at its face. Turned over, from
 * facing up to facing down, a block's push turns round and its drag stays as it was; turned on edge it would push
 * sideways or forward instead, and stop steadying the wing. So a surface is deflected by turning some of its blocks
 * over: the right aileron's, turned down, roll the airliner right, the left's left; the tailplane's that face up,
 * turned down, push the tail down and raise the nose, and those its trim turned down, turned up, lower it. The more
 * are turned over, the harder they push. The strongest go first, the furthest out along the wings and the furthest aft
 * along the tailplane, whose go in mirrored pairs so they do not roll it: the airframe's layout lists them in that
 * order.
 *
 * <p>The autopilot asks for pitch and roll in the control jets' signal (see {@link AutopilotBlockEntity}). The
 * surfaces take as much of it as they can at the speed the airliner flies, and the jets only what is left over; at
 * take-off and landing speeds, and once the airliner is past the attitude the autopilot keeps it within, the jets do it
 * all as before, and the surfaces stand as they were built. Each block turned over is work for Sable, so the surfaces
 * only move once what is asked is {@link #HYSTERESIS} away from what they give, and no more often than every
 * {@link #DWELL} ticks. The clients are never sent the change: a lifting block looks and stands the same either way up,
 * and only the server reads which way it faces.
 */
public final class ControlSurfaces {
    /**
     * How many lifting blocks turned over push about as hard as one step of a control jet channel's signal (its six
     * jets), going {@link #REFERENCE_SPEED} through air as thick as at sea level; a lifting block's push grows with the
     * speed and the air, a jet's does not. Rough, from the share of the airliner's weight each block of the wings holds
     * up at cruise and the thrust that holds the cruise speed; it errs toward the surfaces being the stronger, which
     * turns fewer of them over and at worst makes the autopilot gentler, never wilder.
     */
    private static final double BLOCKS_PER_SIGNAL = 4;
    private static final double REFERENCE_SPEED = 50;
    /**
     * The surfaces fly the airliner from this many times the speed it takes off at, and on until it slows below the
     * second: well clear of the take-off and of the approach to land, which the jets fly as before.
     */
    private static final double FLY_FROM = 1.3;
    private static final double FLY_UNTIL = 1.2;
    /** How far what is asked must be from what the surfaces give before they move, in the jets' signal. */
    private static final double HYSTERESIS = 1.5;
    /** The fewest ticks between two moves of one axis's surfaces. */
    private static final int DWELL = 5;
    /**
     * Turning a block over tells neither the clients nor the blocks round it: its shape stays as it was, and only Sable
     * on the server reads which way it faces.
     */
    private static final int QUIET = Block.UPDATE_KNOWN_SHAPE;
    /** The way a lifting block faces, and so pushes. */
    private static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final String SURFACES = "Surfaces";
    private static final String TURNED = "SurfacesTurned";

    /** A way the surfaces turn the airliner, and the channels of the surfaces that turn it either way. */
    enum Axis {
        PITCH(Channel.PITCH_UP, Channel.PITCH_DOWN), ROLL(Channel.ROLL_RIGHT, Channel.ROLL_LEFT);

        private final Channel positive;
        private final Channel negative;

        Axis(Channel positive, Channel negative) {
            this.positive = positive;
            this.negative = negative;
        }
    }

    /** What is left for the control jets, in their signal: pitching up and rolling right positive. */
    record Jets(int pitch, int roll) {
    }

    /**
     * The lifting blocks of each surface, by the way it turns the airliner, as offsets from the autopilot in the
     * world's directions, in the order they are turned over; none for an airliner built before the surfaces.
     */
    private Map<Channel, List<BlockPos>> blocks = new EnumMap<>(Channel.class);
    /**
     * How many blocks of each axis's surfaces are turned over: of its first channel's while above nought, of its
     * second's while below.
     */
    private final Map<Axis, Integer> turned = new EnumMap<>(Map.of(Axis.PITCH, 0, Axis.ROLL, 0));
    /** The game time each axis's surfaces last moved. */
    private final Map<Axis, Long> moved = new EnumMap<>(Map.of(Axis.PITCH, 0L, Axis.ROLL, 0L));
    /** Whether the airliner has been fast enough for the surfaces, between {@link #FLY_FROM} and {@link #FLY_UNTIL}. */
    private boolean fast;

    /**
     * Which way a surface's blocks face as built, standing still: the tailplane's that lower the nose face down, as its
     * trim turned them; every other surface's face up. Turned over, they face the other way.
     */
    public static Direction rest(Channel channel) {
        return channel == Channel.PITCH_DOWN ? Direction.DOWN : Direction.UP;
    }

    /** Wired when the airliner is built: the lifting blocks of each surface, as offsets from the autopilot. */
    void wire(Map<Channel, List<BlockPos>> surfaces) {
        blocks = new EnumMap<>(Channel.class);
        surfaces.forEach((channel, offsets) -> blocks.put(channel, List.copyOf(offsets)));
        turned.replaceAll((axis, count) -> 0);
    }

    /** Turns the offsets with the autopilot's other links (see {@link Turning}). */
    void turn(Rotation turn) {
        blocks.replaceAll((channel, offsets) -> Turning.turn(offsets, turn));
    }

    /**
     * Splits the autopilot's demand, in the jets' signal, between the surfaces and the jets: sets the surfaces for as
     * much of it as they can give, and says what is left for the jets. While the surfaces do not fly the airliner (see
     * {@link #flies}), among other times while it is upset, past the attitude the autopilot keeps it within, they stand
     * as built and the jets get it all.
     */
    Jets fly(Level level, BlockPos origin, SableFlight.State state, int pitch, int roll, boolean upset) {
        if (!flies(state, upset)) {
            rest(level, origin);
            return new Jets(pitch, roll);
        }
        // How much of the jets' signal each block turned over is worth at this speed, in this air.
        double worth = state.velocity().length() * state.air() / (BLOCKS_PER_SIGNAL * REFERENCE_SPEED);
        long now = level.getGameTime();
        return new Jets(take(level, origin, Axis.PITCH, pitch, worth, now), take(level, origin, Axis.ROLL, roll, worth, now));
    }

    /** Turns every block back the way it was built, if any is turned over. */
    void rest(Level level, BlockPos origin) {
        for (Axis axis : Axis.values()) {
            if (turned.get(axis) != 0) {
                move(level, origin, axis, 0, level.getGameTime());
            }
        }
    }

    /**
     * Whether the surfaces fly the airliner this tick: switched on, fitted, upright enough and fast enough, past
     * {@link #FLY_FROM} times the rotation speed and on until it slows below {@link #FLY_UNTIL} times it.
     */
    private boolean flies(SableFlight.State state, boolean upset) {
        double rotate = AerolargeConfig.AUTOPILOT_ROTATE_SPEED.get();
        double speed = state.velocity().length();
        fast = speed >= (fast ? FLY_UNTIL : FLY_FROM) * rotate;
        return fast && !upset && fitted() && AerolargeConfig.CONTROL_SURFACES.get();
    }

    /** Whether the airliner has any surfaces: none if built before them, or without lifting blocks. */
    private boolean fitted() {
        return blocks.values().stream().anyMatch(offsets -> !offsets.isEmpty());
    }

    /**
     * Sets one axis's surfaces for what is asked of it, in the jets' signal (positive its first channel's way), each
     * block turned over worth the signal given; and says what is left for the jets: what is more than the surfaces on
     * that side can give. They move only past {@link #HYSTERESIS} and {@link #DWELL}.
     */
    private int take(Level level, BlockPos origin, Axis axis, int demand, double worth, long now) {
        int sign = Integer.signum(demand);
        int size = pairs(blocks.getOrDefault(sign >= 0 ? axis.positive : axis.negative, List.of()).size());
        int given = Math.min(Math.abs(demand), (int) Math.floor(size * worth));
        double giving = turned.get(axis) * worth;
        if (Math.abs(sign * given - giving) >= HYSTERESIS && now - moved.get(axis) >= DWELL) {
            // Whole pairs: the tailplane's turn over in mirrored pairs.
            int count = given == 0 ? 0 : Math.min(2 * (int) Math.round(given / worth / 2), size);
            move(level, origin, axis, sign * count, now);
        }
        return demand - sign * given;
    }

    /** The most of a surface's blocks that make whole pairs. */
    private static int pairs(int count) {
        return 2 * (count / 2);
    }

    /**
     * Turns the axis's blocks over or back until as many are turned over as asked, its first channel's for a count
     * above nought and its second's below; counted as moved only once every block it changes stood in a loaded chunk,
     * so one that did not is tried again.
     */
    private void move(Level level, BlockPos origin, Axis axis, int count, long now) {
        int from = turned.get(axis);
        boolean positive = set(level, origin, axis.positive, Math.max(from, 0), Math.max(count, 0));
        boolean negative = set(level, origin, axis.negative, Math.max(-from, 0), Math.max(-count, 0));
        if (positive && negative) {
            turned.put(axis, count);
            moved.put(axis, now);
        }
    }

    /**
     * Turns a surface's blocks from the first {@code from} of them turned over to the first {@code to}: over, those
     * between, if it is more, or back, if it is fewer. Whether every one of them stood in a loaded chunk.
     */
    private boolean set(Level level, BlockPos origin, Channel channel, int from, int to) {
        List<BlockPos> offsets = blocks.getOrDefault(channel, List.of());
        Direction facing = to > from ? rest(channel).getOpposite() : rest(channel);
        boolean loaded = true;
        for (int i = Math.min(from, to); i < Math.min(Math.max(from, to), offsets.size()); i++) {
            loaded &= face(level, origin.offset(offsets.get(i)), facing);
        }
        return loaded;
    }

    /**
     * Turns the lifting block at a position to face the way given, if a lifting block stands there and faces another
     * way; false if its chunk is not loaded, which is never loaded for it.
     */
    private static boolean face(Level level, BlockPos pos, Direction facing) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (SableFlight.lifts(state) && state.hasProperty(FACING) && state.getValue(FACING) != facing) {
            level.setBlock(pos, state.setValue(FACING, facing), QUIET);
        }
        return true;
    }

    /** Saves the surfaces' blocks and how many are turned over, which stay so until they are turned back. */
    void save(CompoundTag tag) {
        CompoundTag surfaces = new CompoundTag();
        blocks.forEach((channel, offsets) -> surfaces.putLongArray(channel.name(), offsets.stream().mapToLong(BlockPos::asLong).toArray()));
        tag.put(SURFACES, surfaces);
        CompoundTag counts = new CompoundTag();
        turned.forEach((axis, count) -> counts.putInt(axis.name(), count));
        tag.put(TURNED, counts);
    }

    /** Loads them, turned as the autopilot has turned since they were saved; none saved by an airliner built before. */
    void load(CompoundTag tag, Rotation turn) {
        blocks = new EnumMap<>(Channel.class);
        CompoundTag surfaces = tag.getCompound(SURFACES);
        for (Channel channel : Channel.values()) {
            if (surfaces.contains(channel.name())) {
                List<BlockPos> offsets = Arrays.stream(surfaces.getLongArray(channel.name())).mapToObj(BlockPos::of).toList();
                blocks.put(channel, Turning.turn(offsets, turn));
            }
        }
        CompoundTag counts = tag.getCompound(TURNED);
        turned.replaceAll((axis, count) -> counts.getInt(axis.name()));
    }
}
