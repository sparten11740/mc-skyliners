package dev.aerolarge.autopilot;

import dev.aerolarge.block.BurnerLeverBlock;
import dev.aerolarge.block.HelmBlock;
import dev.aerolarge.block.TelegraphBlock;
import dev.aerolarge.block.Turning;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The hand controls in a zeppelin's control car, as its autopilot works them (see {@link ZeppelinPilot}): the rudder
 * wheel, the elevator wheel, the port and the starboard engines' telegraphs and the burner lever. While the autopilot
 * flies, it moves them itself to show what it does, the telegraphs ringing as their orders change: how hard it climbs
 * or sinks on the lever, how it trims the nose on the elevator wheel, how hard each side's engines drive on their
 * telegraph and how hard it turns on the rudder wheel. Switched off in the air, it hands the zeppelin over to them,
 * set to float it where it is; resting on the ground, whoever moves one of them flies the zeppelin by them. Either
 * way, until the autopilot is engaged again.
 */
final class ZeppelinControls {
    /** How much nearer to the thrust, as a share of full thrust, another order must be to ring the telegraph to it. */
    private static final double RING_MARGIN = 0.05;

    /**
     * What the controls are set to: the lever from -1 (burners low, sinking) to 1 (burners high, climbing), the
     * elevator wheel from -1 (nose up) to 1 (nose down), each telegraph as a share of full thrust (less than nothing
     * astern), and the rudder wheel from -1 (to port) to 1 (to starboard).
     */
    record Setting(double lever, double elevator, double port, double starboard, double rudder) {
        /** Resting on the ground: the burners low, the engines stopped. */
        static final Setting AT_REST = new Setting(-1, 0, 0, 0, 0);
        /** Let go in the air: floating where it is, the engines stopped. */
        static final Setting FLOATING = new Setting(0, 0, 0, 0, 0);
    }

    /** The controls, as offsets from the autopilot; null where the zeppelin has none. */
    @Nullable
    private BlockPos rudder;
    @Nullable
    private BlockPos elevator;
    @Nullable
    private BlockPos port;
    @Nullable
    private BlockPos starboard;
    @Nullable
    private BlockPos lever;
    /** What each control was last seen or set to, by its offset, to tell a hand on it from the autopilot's own. */
    private final Map<BlockPos, BlockState> seen = new HashMap<>();
    /** Whether someone has moved a control since the autopilot let go, and flies by them. */
    private boolean byHand;

    /** Wired when the zeppelin is built. */
    void wire(BlockPos rudder, BlockPos elevator, BlockPos port, BlockPos starboard, BlockPos lever) {
        this.rudder = rudder;
        this.elevator = elevator;
        this.port = port;
        this.starboard = starboard;
        this.lever = lever;
        seen.clear();
    }

    /** What to fly by hand, the autopilot switched off: what the controls say once someone has moved one; else null. */
    @Nullable
    Setting byHand(Level level, BlockPos origin) {
        if (lever == null) {
            return null;
        }
        if (!byHand && moved(level, origin)) {
            byHand = true;
        }
        return byHand ? read(level, origin) : null;
    }

    /** Whether any control stands otherwise than it was last seen; one not seen before is noted as it stands. */
    private boolean moved(Level level, BlockPos origin) {
        boolean moved = false;
        for (BlockPos offset : offsets()) {
            BlockState now = level.getBlockState(origin.offset(offset));
            BlockState was = seen.put(offset, now);
            moved |= was != null && !was.equals(now);
        }
        return moved;
    }

    private Setting read(Level level, BlockPos origin) {
        return new Setting(lever(level.getBlockState(origin.offset(lever))), helm(level, origin, elevator), telegraph(level, origin, port),
            telegraph(level, origin, starboard), helm(level, origin, rudder));
    }

    private static double lever(BlockState state) {
        return state.getBlock() instanceof BurnerLeverBlock ? BurnerLeverBlock.heat(state) : 0;
    }

    private static double helm(Level level, BlockPos origin, @Nullable BlockPos offset) {
        BlockState state = offset == null ? null : level.getBlockState(origin.offset(offset));
        return state != null && state.getBlock() instanceof HelmBlock ? HelmBlock.turn(state) : 0;
    }

    private static double telegraph(Level level, BlockPos origin, @Nullable BlockPos offset) {
        BlockState state = offset == null ? null : level.getBlockState(origin.offset(offset));
        return state != null && state.getBlock() instanceof TelegraphBlock ? state.getValue(TelegraphBlock.ORDER).thrust : 0;
    }

    /** Sets the controls to show what the autopilot does, taking them out of anyone's hands. */
    void show(Level level, BlockPos origin, Setting setting) {
        byHand = false;
        if (lever == null) {
            return;
        }
        showLever(level, origin.offset(lever), setting.lever());
        showHelm(level, origin, elevator, setting.elevator());
        showHelm(level, origin, rudder, setting.rudder());
        showTelegraph(level, origin, port, setting.port());
        showTelegraph(level, origin, starboard, setting.starboard());
        for (BlockPos offset : offsets()) {
            seen.put(offset, level.getBlockState(origin.offset(offset)));
        }
    }

    /** Hands the controls over, set to what the zeppelin does now: it is flown by them from now on. */
    void handOver(Level level, BlockPos origin, Setting setting) {
        show(level, origin, setting);
        byHand = lever != null;
    }

    private static void showLever(Level level, BlockPos pos, double heat) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BurnerLeverBlock) {
            int notch = BurnerLeverBlock.FLOAT + (int) Math.round(Mth.clamp(heat, -1, 1) * BurnerLeverBlock.FLOAT);
            setQuietly(level, pos, state, state.setValue(BurnerLeverBlock.HEAT, notch));
        }
    }

    private static void showHelm(Level level, BlockPos origin, @Nullable BlockPos offset, double turn) {
        BlockState state = offset == null ? null : level.getBlockState(origin.offset(offset));
        if (state != null && state.getBlock() instanceof HelmBlock) {
            int notch = HelmBlock.MIDDLE + (int) Math.round(Mth.clamp(turn, -1, 1) * HelmBlock.MOST);
            setQuietly(level, origin.offset(offset), state, state.setValue(HelmBlock.TURN, notch));
        }
    }

    /**
     * Rings a telegraph to the order nearest the thrust, once that is nearer by a margin than the order it stands at,
     * so a thrust between two orders does not ring it back and forth every second.
     */
    private static void showTelegraph(Level level, BlockPos origin, @Nullable BlockPos offset, double thrust) {
        BlockState state = offset == null ? null : level.getBlockState(origin.offset(offset));
        if (state == null || !(state.getBlock() instanceof TelegraphBlock)) {
            return;
        }
        TelegraphBlock.Order now = state.getValue(TelegraphBlock.ORDER);
        TelegraphBlock.Order nearest = TelegraphBlock.Order.nearest(thrust);
        if (Math.abs(thrust - nearest.thrust) + RING_MARGIN < Math.abs(thrust - now.thrust)) {
            TelegraphBlock.ring(level, origin.offset(offset), state.setValue(TelegraphBlock.ORDER, nearest));
        }
    }

    private static void setQuietly(Level level, BlockPos pos, BlockState was, BlockState now) {
        if (!was.equals(now)) {
            level.setBlock(pos, now, Block.UPDATE_ALL);
        }
    }

    private List<BlockPos> offsets() {
        List<BlockPos> offsets = new ArrayList<>();
        for (BlockPos offset : new BlockPos[] {rudder, elevator, port, starboard, lever}) {
            if (offset != null) {
                offsets.add(offset);
            }
        }
        return offsets;
    }

    /** Turns the controls as the autopilot's block has turned (see {@link Turning}). */
    void turn(Rotation turn) {
        rudder = Turning.turn(rudder, turn);
        elevator = Turning.turn(elevator, turn);
        port = Turning.turn(port, turn);
        starboard = Turning.turn(starboard, turn);
        lever = Turning.turn(lever, turn);
        seen.clear();
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        put(tag, "Rudder", rudder);
        put(tag, "Elevator", elevator);
        put(tag, "Port", port);
        put(tag, "Starboard", starboard);
        put(tag, "Lever", lever);
        tag.putBoolean("ByHand", byHand);
        return tag;
    }

    private static void put(CompoundTag tag, String key, @Nullable BlockPos offset) {
        if (offset != null) {
            tag.putLong(key, offset.asLong());
        }
    }

    /** Loads what was saved, turned as the autopilot's block has turned since; one built without controls has none. */
    void load(CompoundTag tag, Rotation turn) {
        rudder = Turning.turn(get(tag, "Rudder"), turn);
        elevator = Turning.turn(get(tag, "Elevator"), turn);
        port = Turning.turn(get(tag, "Port"), turn);
        starboard = Turning.turn(get(tag, "Starboard"), turn);
        lever = Turning.turn(get(tag, "Lever"), turn);
        byHand = tag.getBoolean("ByHand");
        seen.clear();
    }

    @Nullable
    private static BlockPos get(CompoundTag tag, String key) {
        return tag.contains(key) ? BlockPos.of(tag.getLong(key)) : null;
    }
}
