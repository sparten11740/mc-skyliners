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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;

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
 * The clients are never sent the blocks turned over: a lifting block looks and stands the same either way up, and only
 * the server reads which way it faces. They are told instead how far to swing the panels drawn for the surfaces (see
 * {@link Panel} and {@link #showMoved}).
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
    private static final String PANELS = "SurfacePanels";
    private static final String SHOWN = "SurfacesShown";
    /**
     * How finely the clients are told how far the panels swing, in steps of their whole travel either way, and
     * the fewest ticks between two tellings: each is a block entity update to everyone near the airliner.
     */
    private static final int SHOWN_STEPS = 40;
    private static final int SHOW_EVERY = 2;
    /**
     * How much the surfaces give beyond what keeps the airliner trimmed, in the jets' signal, for the panels to swing
     * all the way over; and the share of the way the trim follows what they give each tick: about ten seconds behind.
     */
    private static final double FULL_SWING = 4;
    private static final double TRIM_SHARE = 1 / 200.0;

    /** A way the surfaces turn the airliner, the channels of the surfaces that turn it either way, and their worth. */
    public enum Axis {
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
     * A panel drawn where a real airliner's aileron or elevator hangs off a wing's trailing edge, to show how hard the
     * surfaces it stands for steer (the blocks turned over themselves look no different either way up): a row
     * of the wing's own lifting blocks, so many rows deep, swung as one about its hinge. Its hinge runs from its inner
     * end to its outer, a whole number of blocks apart, in blocks from the middle of the autopilot's block, to
     * starboard, up and ahead along the nose, level with the middle of the blocks; then the rows aft of the hinge, the
     * block it is drawn with, the axis whose surfaces it shows, and which way it swings for them, its trailing edge up
     * (1) or down (-1) for steering the axis's first way, as an airliner's right aileron goes up to roll it right and its
     * left one down.
     */
    public record Panel(Axis axis, int way, Vec3 inner, Vec3 outer, int rows, BlockState look) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Axis", axis.name());
            tag.putInt("Way", way);
            saveVec(tag, "Inner", inner);
            saveVec(tag, "Outer", outer);
            tag.putInt("Rows", rows);
            tag.put("Look", NbtUtils.writeBlockState(look));
            return tag;
        }

        static Panel load(CompoundTag tag) {
            return new Panel(Axis.valueOf(tag.getString("Axis")), tag.getInt("Way"), loadVec(tag, "Inner"), loadVec(tag, "Outer"),
                tag.getInt("Rows"), NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("Look")));
        }

        private static void saveVec(CompoundTag tag, String key, Vec3 vec) {
            tag.putDouble(key + "X", vec.x);
            tag.putDouble(key + "Y", vec.y);
            tag.putDouble(key + "Z", vec.z);
        }

        private static Vec3 loadVec(CompoundTag tag, String key) {
            return new Vec3(tag.getDouble(key + "X"), tag.getDouble(key + "Y"), tag.getDouble(key + "Z"));
        }
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
    /** The panels drawn for the surfaces; none for an airliner built before them. */
    private List<Panel> panels = List.of();
    /**
     * How much each axis's surfaces give as they stand, in the jets' signal, as of the last tick; and that averaged
     * over the last while (see {@link #TRIM_SHARE}): the steady part of it, which holds the airliner trimmed. Not yet
     * known once loaded, so an airliner loaded in flight takes what its surfaces give then as its trim.
     */
    private final Map<Axis, Double> giving = new EnumMap<>(Map.of(Axis.PITCH, 0.0, Axis.ROLL, 0.0));
    private final Map<Axis, Double> trimmed = new EnumMap<>(Map.of(Axis.PITCH, Double.NaN, Axis.ROLL, Double.NaN));
    /**
     * How far each axis's panels swing, as the clients were last told, in {@link #SHOWN_STEPS} of their whole travel
     * either way; and when they were told.
     */
    private final Map<Axis, Integer> shown = new EnumMap<>(Map.of(Axis.PITCH, 0, Axis.ROLL, 0));
    private long shownAt;

    /**
     * Which way a surface's blocks face as built, standing still: the tailplane's that lower the nose face down, as its
     * trim turned them; every other surface's face up. Turned over, they face the other way.
     */
    public static Direction rest(Channel channel) {
        return channel == Channel.PITCH_DOWN ? Direction.DOWN : Direction.UP;
    }

    /**
     * Wired when the airliner is built: the lifting blocks of each surface, as offsets from the autopilot, and the
     * panels drawn for them.
     */
    void wire(Map<Channel, List<BlockPos>> surfaces, List<Panel> panels) {
        blocks = new EnumMap<>(Channel.class);
        surfaces.forEach((channel, offsets) -> blocks.put(channel, List.copyOf(offsets)));
        this.panels = List.copyOf(panels);
        turned.replaceAll((axis, count) -> 0);
        reach.clear();
    }

    /** The panels drawn for the surfaces (see {@link Panel}). */
    public List<Panel> panels() {
        return panels;
    }

    /** How far an axis's panels swing, as the clients were last told: -1 all the way its second way to 1 its first. */
    public double deflection(Axis axis) {
        return shown.get(axis) / (double) SHOWN_STEPS;
    }

    /**
     * Whether how far the panels swing has moved on since the clients were last told, by a step of
     * {@link #SHOWN_STEPS} and no sooner than {@link #SHOW_EVERY} ticks after: then it is the new one to tell them.
     * They show how hard the surfaces steer, over and above the steady push that keeps the airliner trimmed, as an
     * airliner's ailerons and elevators move while its trimmed tailplane holds it level: all the way over at
     * {@link #FULL_SWING} of the jets' signal.
     */
    boolean showMoved(long now) {
        if (panels.isEmpty() || now - shownAt < SHOW_EVERY) {
            return false;
        }
        boolean moved = false;
        for (Axis axis : Axis.values()) {
            double steering = Mth.clamp((giving.get(axis) - trimmed.get(axis)) / FULL_SWING, -1, 1);
            int step = (int) Math.round(steering * SHOWN_STEPS);
            if (step != shown.get(axis)) {
                shown.put(axis, step);
                moved = true;
            }
        }
        if (moved) {
            shownAt = now;
        }
        return moved;
    }

    /**
     * Notes how much an axis's surfaces give as they stand, in the jets' signal, and the steady part of it; while they
     * do not fly the airliner, there is none to steady, and the panels rest.
     */
    private void note(Axis axis, double gives, boolean flies) {
        giving.put(axis, gives);
        double trim = trimmed.get(axis);
        trimmed.put(axis, flies && !Double.isNaN(trim) ? trim + (gives - trim) * TRIM_SHARE : gives);
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
            note(axis, 0, false);
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
        double gives = worth * given(axis, turned.get(axis));
        note(axis, gives, flies);
        return (int) Math.round(demand - gives);
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

    /**
     * Saves what the clients draw the surfaces with: the panels, and how far they swing, as last told.
     * Both in the save and in the update the clients are sent.
     */
    void saveShown(CompoundTag tag) {
        ListTag list = new ListTag();
        panels.forEach(panel -> list.add(panel.save()));
        tag.put(PANELS, list);
        tag.putIntArray(SHOWN, new int[]{shown.get(Axis.PITCH), shown.get(Axis.ROLL)});
    }

    /** Loads them; none for an airliner built before the panels, which draws none. */
    void loadShown(CompoundTag tag) {
        if (tag.contains(PANELS)) {
            panels = tag.getList(PANELS, Tag.TAG_COMPOUND).stream().map(entry -> Panel.load((CompoundTag) entry)).toList();
        }
        int[] steps = tag.getIntArray(SHOWN);
        if (steps.length == 2) {
            shown.put(Axis.PITCH, steps[0]);
            shown.put(Axis.ROLL, steps[1]);
        }
    }
}
