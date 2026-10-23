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
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * The autopilot's control surfaces, an experiment switched on by {@code controlSurfaces} in the server config: ailerons
 * over the outer wings, and the tailplane as an elevator, made of the wings' own lifting blocks. Sable pushes every
 * lifting block along the way it faces, as hard as it goes through the air and as thick as the air is, whatever angle
 * the air meets it at, and holds it back most against the air coming at its face. Turned over, from facing up to facing
 * down, a block's push turns round and its drag stays as it was; turned on edge it would push sideways or forward
 * instead, and stop steadying the wing. So a surface is deflected by turning some of its blocks over: the right
 * aileron's, turned down, roll the airliner right, the left's left; the tailplane's that face up, turned down, push the
 * tail down and raise the nose, and those its trim turned down, turned up, lower it. The more are turned over, the
 * harder they push. The strongest go first, the furthest out along the wings and the furthest aft along the tailplane,
 * whose go in mirrored pairs so they do not roll it: the airframe's layout lists them in that order.
 *
 * <p>The autopilot asks for pitch and roll in the control jets' signal (see {@link AutopilotBlockEntity}). The surfaces
 * follow what it asks, smoothed, as far as they reach at the speed the airliner flies through the air there: more of
 * their blocks turned over pushes one way and fewer the other, the way a trimmed tailplane moves about its trim. The
 * jets give exactly what the surfaces do not give as they stand that tick, so between them the autopilot always gets
 * what it asked: the surfaces only ever take work off the jets, and never leave a gap the jets do not fill. At take-off
 * and landing speeds, and once the airliner is past the attitude the autopilot keeps it within, the surfaces sweep back
 * to how they were built and the jets take it all again. Each block turned over is work for Sable, so the surfaces sweep
 * at most {@link #MOST_PER_TICK} blocks a tick and stand still within {@link #DEADBAND} blocks of where they should be.
 * The clients are never sent the change: a lifting block looks and stands the same either way up, and only the server
 * reads which way it faces.
 */
public final class ControlSurfaces {
    /**
     * How much of a control jet channel's signal one block turned over is worth, for each block a second the airliner
     * flies through air as thick as at sea level: measured in flight at scale 2, by the roll and the pitch a few dozen
     * to a few hundred blocks turned over started against a step of the jets', at 50 blocks a second in air 0.64 as
     * thick. For the ailerons it is a block's at the outermost of them; one further in turns the airliner on a shorter
     * arm and is worth as much less as it stands nearer the middle. The tailplane's blocks all stand about as far aft.
     * Both hold at any scale, as the jets' arms grow with the airliner as the surfaces' do.
     */
    private static final double AILERON_WORTH = 2.8e-4;
    private static final double ELEVATOR_WORTH = 3.9e-4;
    /**
     * The surfaces fly the airliner from this many times the speed it takes off at, and on until it slows below the
     * second: well clear of the take-off and of the approach to land, which the jets fly as before.
     */
    private static final double FLY_FROM = 1.3;
    private static final double FLY_UNTIL = 1.2;
    /**
     * The share of the way from what they followed to what is asked now that the surfaces follow each tick, so a request
     * that flickers from one tick to the next does not set them sweeping to and fro; the jets meanwhile give the rest.
     */
    private static final double FOLLOW = 0.3;
    /** The most blocks of one axis's surfaces turned over or back in a tick. */
    private static final int MOST_PER_TICK = 40;
    /** How many blocks from where they should stand the surfaces must be before they move. */
    private static final int DEADBAND = 6;
    /**
     * Turning a block over tells neither the clients nor the blocks round it: its shape stays as it was, and only Sable
     * on the server reads which way it faces.
     */
    private static final int QUIET = Block.UPDATE_KNOWN_SHAPE;
    /** The way a lifting block faces, and so pushes. */
    private static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final String SURFACES = "Surfaces";
    private static final String TURNED = "SurfacesTurned";

    /** A way the surfaces turn the airliner, the channels of the surfaces that turn it either way, and their worth. */
    enum Axis {
        PITCH(Channel.PITCH_UP, Channel.PITCH_DOWN, ELEVATOR_WORTH, true), ROLL(Channel.ROLL_RIGHT, Channel.ROLL_LEFT, AILERON_WORTH, false);

        private final Channel positive;
        private final Channel negative;
        private final double worth;
        /** Whether its blocks turn over in mirrored pairs. */
        private final boolean paired;

        Axis(Channel positive, Channel negative, double worth, boolean paired) {
            this.positive = positive;
            this.negative = negative;
            this.worth = worth;
            this.paired = paired;
        }

        Channel channel(double way) {
            return way >= 0 ? positive : negative;
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
    /** What each axis's surfaces follow: what the autopilot asked, smoothed (see {@link #FOLLOW}). */
    private final Map<Axis, Double> followed = new EnumMap<>(Map.of(Axis.PITCH, 0.0, Axis.ROLL, 0.0));
    /**
     * For each surface, how much the first so many of its blocks turned over are worth together, in blocks at full
     * worth: the tailplane's one each, an aileron's as far out as it stands. Worked out from the way the airliner's nose
     * points once it first flies, and afresh once its blocks change.
     */
    private final Map<Channel, double[]> reach = new EnumMap<>(Channel.class);
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
        reach.clear();
    }

    /** Turns the offsets with the autopilot's other links (see {@link Turning}). */
    void turn(Rotation turn) {
        blocks.replaceAll((channel, offsets) -> Turning.turn(offsets, turn));
        reach.clear();
    }

    /**
     * Splits the autopilot's demand, in the jets' signal, between the surfaces and the jets, the airliner's nose
     * pointing the given way among its blocks: sets the surfaces toward as much of it as they can give, and says what
     * the jets must give besides. While the surfaces do not fly the airliner (see {@link #flies}), among other times
     * while it is upset, past the attitude the autopilot keeps it within, they sweep back to how they were built, and
     * the jets give all but what they still give on the way.
     */
    Jets fly(Level level, BlockPos origin, Direction nose, SableFlight.State state, double pitch, double roll, boolean upset) {
        if (reach.isEmpty()) {
            measure(nose);
        }
        boolean flies = flies(state, upset);
        // A block at full worth turned over gives each axis's worth of the jets' signal times this, at this speed in this air.
        double push = state.velocity().length() * state.air();
        return new Jets(take(level, origin, Axis.PITCH, pitch, push, flies), take(level, origin, Axis.ROLL, roll, push, flies));
    }

    /** Turns every block back the way it was built, at once, if any is turned over. */
    void rest(Level level, BlockPos origin) {
        for (Axis axis : Axis.values()) {
            followed.put(axis, 0.0);
            if (turned.get(axis) != 0) {
                move(level, origin, axis, 0);
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
     * Sweeps one axis's surfaces toward what is asked of it (positive its first channel's way), followed smoothly, as
     * far as they reach, or back to how they were built while they do not fly the airliner; and says what the jets must
     * give besides: what is asked, less what the surfaces give as they stand.
     */
    private int take(Level level, BlockPos origin, Axis axis, double demand, double push, boolean flies) {
        double worth = axis.worth * push;
        double follow = followed.get(axis) + FOLLOW * ((flies ? demand : 0) - followed.get(axis));
        followed.put(axis, follow);
        int now = turned.get(axis);
        int wanted = flies && worth > 0 ? blocksFor(axis, follow / worth) : 0;
        // Back to how they were built they go all the way, however few are left over.
        if (Math.abs(wanted - now) >= DEADBAND || wanted == 0 && now != 0) {
            move(level, origin, axis, now + Mth.clamp(wanted - now, -MOST_PER_TICK, MOST_PER_TICK));
        }
        return (int) Math.round(demand - worth * given(axis, turned.get(axis)));
    }

    /**
     * How many of an axis's blocks turned over give so much, in blocks at full worth (positive its first channel's way):
     * the fewest that give at least that, or all of them; whole pairs for an axis that turns them so.
     */
    private int blocksFor(Axis axis, double amount) {
        double[] worths = reach.getOrDefault(axis.channel(amount), new double[1]);
        int found = Arrays.binarySearch(worths, Math.abs(amount));
        int count = Math.min(found >= 0 ? found : -found - 1, worths.length - 1);
        if (axis.paired) {
            count -= count % 2;
        }
        return (int) Math.signum(amount) * count;
    }

    /** What so many of an axis's blocks turned over give, in blocks at full worth (positive its first channel's way). */
    private double given(Axis axis, int count) {
        double[] worths = reach.getOrDefault(axis.channel(count), new double[1]);
        return Math.signum(count) * worths[Math.min(Math.abs(count), worths.length - 1)];
    }

    /**
     * Works out what each surface's blocks are worth turned over together, the airliner's nose pointing the given way:
     * an aileron's as far from the middle as it stands, against the outermost; the tailplane's all alike.
     */
    private void measure(Direction nose) {
        Direction right = nose.getClockWise();
        reach.clear();
        blocks.forEach((channel, offsets) -> {
            boolean aileron = channel == Channel.ROLL_RIGHT || channel == Channel.ROLL_LEFT;
            double outermost = offsets.stream().mapToDouble(offset -> across(offset, right)).max().orElse(1);
            double[] worths = new double[offsets.size() + 1];
            for (int i = 0; i < offsets.size(); i++) {
                worths[i + 1] = worths[i] + (aileron ? across(offsets.get(i), right) / outermost : 1);
            }
            reach.put(channel, worths);
        });
    }

    /** How far out from the middle an offset from the autopilot, on the middle, stands. */
    private static double across(BlockPos offset, Direction right) {
        return Math.abs(offset.getX() * right.getStepX() + offset.getZ() * right.getStepZ());
    }

    /**
     * Turns the axis's blocks over or back until as many are turned over as asked, its first channel's for a count
     * above nought and its second's below; counted as done only once every block it changes stood in a loaded chunk, so
     * one that did not is tried again.
     */
    private void move(Level level, BlockPos origin, Axis axis, int count) {
        int from = turned.get(axis);
        boolean positive = set(level, origin, axis.positive, Math.max(from, 0), Math.max(count, 0));
        boolean negative = set(level, origin, axis.negative, Math.max(-from, 0), Math.max(-count, 0));
        if (positive && negative) {
            turned.put(axis, count);
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
        reach.clear();
    }
}
