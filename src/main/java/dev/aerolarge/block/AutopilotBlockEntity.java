package dev.aerolarge.block;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The autopilot: flies the assembled airliner off the ground and up to a cruising height by setting its thrusters.
 * The four engines push it along and, split left against right, hold it on the heading it started on; jets on the
 * tailplane pitch it, standing in for elevators, and jets at the wingtips hold its wings level, standing in for
 * ailerons. It reads where the airliner is and how it moves from Sable each tick.
 *
 * <p>It works in phases: the take-off roll at full thrust with the nose held level; rotation, raising the nose once
 * the airliner stops gaining speed or reaches rotation speed, since its flat wings only lift when they meet the air at
 * an angle; the climb; and the cruise. Once airborne the height is held by the flight path, climbing below the
 * cruising height and level at it: Sable's wings lift by how far they meet the air at an angle, whichever side it
 * comes from, so the nose is held that angle (the angle of attack) above the way the airliner is really going, more
 * while it flies below its path and toward none while above it. The engines hold a speed that also follows the
 * height, so a high airliner sheds energy rather than being pushed further up. Switching it off hands the airliner
 * back to its controls: the thrust levers, the tiller and the parking brake.
 */
public class AutopilotBlockEntity extends BlockEntity {
    /** Everything the autopilot sets, each a group of relays next to thrusters. */
    public enum Channel {
        ENGINE_1, ENGINE_2, ENGINE_3, ENGINE_4, PITCH_UP, PITCH_DOWN, ROLL_LEFT, ROLL_RIGHT, STEER_LEFT, STEER_RIGHT, BRAKES
    }

    public enum Phase {
        OFF, TAKEOFF, CLIMB, CRUISE
    }

    /** Nose attitude at rotation, in degrees: what lifts the airliner off the runway. */
    private static final double ROTATION_PITCH = 9;
    /**
     * Flight path, in degrees above the horizon per block of height still to climb, and the steepest climb and descent
     * it asks for.
     */
    private static final double PATH_GAIN = 0.12;
    private static final double MAX_CLIMB_PATH = 8;
    private static final double MAX_DESCENT_PATH = 6;
    /** The angle of attack trimmed in at lift-off, the most it trims to, and how fast, per degree off the path a tick. */
    private static final double CRUISE_ANGLE_OF_ATTACK = 2;
    private static final double MAX_ANGLE_OF_ATTACK = 10;
    private static final double TRIM_RATE = 0.02;
    /** Angle of attack added per degree the airliner flies below its path. */
    private static final double ATTACK_GAIN = 0.5;
    /** Share of the cruising speed added per block below the cruising height (taken off above it). */
    private static final double SPEED_PER_HEIGHT = 0.008;
    /** The engines' signal at the speed being held, and how much it changes per block a second off that speed. */
    private static final double CRUISE_THRUST = 6;
    private static final double SPEED_GAIN = 0.6;
    /** Signal per degree of pitch or bank error, and per degree a second it is changing at (damping). */
    private static final double PITCH_GAIN = 1.5;
    private static final double PITCH_DAMPING = 0.8;
    private static final double ROLL_GAIN = 1.2;
    private static final double ROLL_DAMPING = 0.6;
    /** Engine signal split per degree off the heading, and the most it splits by. */
    private static final double YAW_GAIN = 0.8;
    private static final int YAW_SPLIT = 6;
    /** Nose wheel steering signal per degree off the heading on the ground. */
    private static final double STEER_GAIN = 3;
    /** Height gained before the take-off counts as airborne, and how close to the cruising height the climb levels off. */
    private static final double AIRBORNE = 3;
    private static final double LEVEL_OFF = 8;
    /** The roll is still gaining speed while it gains more than this, in blocks per second every second. */
    private static final double STILL_ACCELERATING = 0.4;

    private Map<Channel, List<BlockPos>> relays = new EnumMap<>(Channel.class);
    /** The flight deck controls (thrust levers, tiller, parking brake) it hands back to when it lets go. */
    private List<BlockPos> controls = List.of();
    private Phase phase = Phase.OFF;
    private double targetHeading;
    private double startAltitude;
    private double cruiseSpeed;
    /** The angle of attack the nose is held at above the flight path, trimmed as the flight goes. */
    private double trim;
    private double lastPitch;
    private double lastRoll;
    private double lastSpeed;
    private int slowTicks;
    /** Whether last tick's readings are real; false after loading, so the first tick does not see a jump. */
    private boolean primed;

    public AutopilotBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.AUTOPILOT_ENTITY.get(), pos, state);
    }

    /** Hands the autopilot the relays it drives and the controls it gives the airliner back to. */
    public void configure(Map<Channel, List<BlockPos>> relays, List<BlockPos> controls) {
        this.relays = new EnumMap<>(Channel.class);
        this.relays.putAll(relays);
        this.controls = List.copyOf(controls);
        setChanged();
    }

    /** Engages the autopilot for a take-off, or switches it off; says why when it cannot engage. */
    Component toggle() {
        if (phase != Phase.OFF) {
            disengage();
            return Component.translatable("message.aerolarge.autopilot.off");
        }
        SableFlight.State state = SableFlight.read(this, nose());
        if (state == null) {
            return Component.translatable("message.aerolarge.autopilot.not_assembled");
        }
        phase = Phase.TAKEOFF;
        targetHeading = state.heading();
        startAltitude = state.position().y;
        lastPitch = state.pitch();
        lastRoll = state.roll();
        lastSpeed = state.airspeed();
        slowTicks = 0;
        primed = true;
        setChanged();
        return Component.translatable("message.aerolarge.autopilot.engaged", AerolargeConfig.AUTOPILOT_CLIMB.get());
    }

    private Direction nose() {
        return getBlockState().getValue(AutopilotBlock.FACING);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AutopilotBlockEntity autopilot) {
        if (autopilot.phase != Phase.OFF && level instanceof ServerLevel serverLevel) {
            autopilot.fly(serverLevel);
        }
    }

    private void fly(ServerLevel level) {
        SableFlight.State state = SableFlight.read(this, nose());
        if (state == null) {
            // Disassembled under it: nothing left to fly.
            disengage();
            return;
        }
        double pitch = state.pitch();
        double roll = state.roll();
        double speed = state.airspeed();
        if (!primed) {
            lastPitch = pitch;
            lastRoll = roll;
            lastSpeed = speed;
            primed = true;
        }
        double height = state.position().y - startAltitude;
        double cruiseHeight = AerolargeConfig.AUTOPILOT_CLIMB.get();

        double rotateSpeed = AerolargeConfig.AUTOPILOT_ROTATE_SPEED.get();
        double targetPitch;
        int thrust;
        switch (phase) {
            case TAKEOFF -> {
                boolean accelerating = speed - lastSpeed > STILL_ACCELERATING / 20;
                slowTicks = accelerating || speed < 2 ? 0 : slowTicks + 1;
                boolean rotate = speed >= rotateSpeed || slowTicks > 40;
                targetPitch = rotate ? ROTATION_PITCH : 0;
                // Full thrust down the runway; from rotation on, the speed it will climb at.
                cruiseSpeed = Math.max(AerolargeConfig.AUTOPILOT_CRUISE_SPEED.get(), 1.25 * Math.min(speed, rotateSpeed));
                thrust = rotate ? holdSpeed(speed, cruiseSpeed) : 15;
                if (height > AIRBORNE) {
                    phase = Phase.CLIMB;
                    trim = CRUISE_ANGLE_OF_ATTACK;
                }
            }
            default -> {
                // The path to fly follows the height still to go: climbing below the cruising height, level at it,
                // descending above it. Sable's wings lift by how far they meet the air at an angle, whichever side it
                // comes from, so the nose is held that angle (the angle of attack) above the way the airliner is
                // really going: more while it flies below its path, toward none while above it, plus a slow trim.
                // The engines hold a speed that also follows the height, so a high airliner sheds energy rather than
                // being pushed further up.
                double heightToGo = cruiseHeight - height;
                double path = Mth.clamp(PATH_GAIN * heightToGo, -MAX_DESCENT_PATH, MAX_CLIMB_PATH);
                double pathError = path - state.flightPath();
                trim = Mth.clamp(trim + TRIM_RATE * pathError, 0, MAX_ANGLE_OF_ATTACK);
                double angleOfAttack = Mth.clamp(trim + ATTACK_GAIN * pathError, 0, MAX_ANGLE_OF_ATTACK);
                targetPitch = state.flightPath() + angleOfAttack;
                thrust = holdSpeed(speed, cruiseSpeed * Mth.clamp(1 + SPEED_PER_HEIGHT * heightToGo, 0.6, 1.2));
                if (phase == Phase.CLIMB && height > cruiseHeight - LEVEL_OFF) {
                    phase = Phase.CRUISE;
                }
            }
        }

        double pitchRate = (pitch - lastPitch) * 20;
        double rollRate = (roll - lastRoll) * 20;
        int pitchCommand = (int) Math.round(PITCH_GAIN * (targetPitch - pitch) - PITCH_DAMPING * pitchRate);
        int rollCommand = (int) Math.round(ROLL_GAIN * -roll - ROLL_DAMPING * rollRate);
        boolean airborne = phase != Phase.TAKEOFF || height > 0.5;
        drive(level, Channel.PITCH_UP, Mth.clamp(pitchCommand, 0, 15));
        drive(level, Channel.PITCH_DOWN, Mth.clamp(-pitchCommand, 0, 15));
        drive(level, Channel.ROLL_RIGHT, airborne ? Mth.clamp(rollCommand, 0, 15) : 0);
        drive(level, Channel.ROLL_LEFT, airborne ? Mth.clamp(-rollCommand, 0, 15) : 0);

        // The brakes are off while it flies; on the ground it also steers the nose wheel to hold the heading.
        drive(level, Channel.BRAKES, 0);
        int steer = airborne ? 0 : Mth.clamp((int) Math.round(STEER_GAIN * Mth.wrapDegrees(targetHeading - state.heading())), -15, 15);
        drive(level, Channel.STEER_RIGHT, Math.max(0, steer));
        drive(level, Channel.STEER_LEFT, Math.max(0, -steer));

        // Turning right means more thrust on the left engines; the heading grows turning right.
        int split = Mth.clamp((int) Math.round(YAW_GAIN * Mth.wrapDegrees(targetHeading - state.heading())), -YAW_SPLIT, YAW_SPLIT);
        drive(level, Channel.ENGINE_1, thrust - Math.max(0, -split));
        drive(level, Channel.ENGINE_2, thrust - Math.max(0, -split));
        drive(level, Channel.ENGINE_3, thrust - Math.max(0, split));
        drive(level, Channel.ENGINE_4, thrust - Math.max(0, split));

        lastPitch = pitch;
        lastRoll = roll;
        lastSpeed = speed;
        if (level.getGameTime() % 10 == 0) {
            report(level, state, height);
        }
        setChanged();
    }

    /** The engines' signal to hold a speed: down to idle when too fast, full when too slow. */
    private static int holdSpeed(double speed, double target) {
        return Mth.clamp((int) Math.round(CRUISE_THRUST + SPEED_GAIN * (target - speed)), 0, 15);
    }

    /** Shows the phase, speed, height and attitude to everyone near the flight deck. */
    private void report(ServerLevel level, SableFlight.State state, double height) {
        Component status = Component.translatable("message.aerolarge.autopilot.status",
            Component.translatable("message.aerolarge.autopilot.phase." + phase.name().toLowerCase(Locale.ROOT)),
            Math.round(state.airspeed()), String.format(Locale.ROOT, "%+d", Math.round(height)), Math.round(state.pitch()), Math.round(state.roll()));
        level.players().stream()
            .filter(player -> player.position().distanceToSqr(state.position()) < 24 * 24)
            .forEach(player -> player.displayClientMessage(status, true));
    }

    /** Switches off: the jets go quiet, and the engines, the nose wheel and the brakes follow their controls again. */
    private void disengage() {
        phase = Phase.OFF;
        setChanged();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (Channel channel : Channel.values()) {
            drive(serverLevel, channel, 0);
        }
        for (BlockPos offset : controls) {
            if (serverLevel.getBlockEntity(worldPosition.offset(offset)) instanceof Control control) {
                control.resync();
            }
        }
        serverLevel.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 1.0F, 0.6F);
    }

    private void drive(ServerLevel level, Channel channel, int power) {
        Relays.drive(level, worldPosition, relays.getOrDefault(channel, List.of()), power);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        CompoundTag channels = new CompoundTag();
        relays.forEach((channel, offsets) -> channels.put(channel.name(), Relays.write(offsets)));
        tag.put("Relays", channels);
        tag.put("Controls", Relays.write(controls));
        tag.putString("Phase", phase.name());
        tag.putDouble("TargetHeading", targetHeading);
        tag.putDouble("StartAltitude", startAltitude);
        tag.putDouble("CruiseSpeed", cruiseSpeed);
        tag.putDouble("Trim", trim);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        relays = new EnumMap<>(Channel.class);
        CompoundTag channels = tag.getCompound("Relays");
        for (Channel channel : Channel.values()) {
            relays.put(channel, Relays.read(channels.getList(channel.name(), Tag.TAG_INT_ARRAY)));
        }
        // Saved as "Levers" before the tiller and the parking brake joined them.
        controls = Relays.read(tag.getList(tag.contains("Controls") ? "Controls" : "Levers", Tag.TAG_INT_ARRAY));
        try {
            phase = Phase.valueOf(tag.getString("Phase"));
        } catch (IllegalArgumentException e) {
            phase = Phase.OFF;
        }
        targetHeading = tag.getDouble("TargetHeading");
        startAltitude = tag.getDouble("StartAltitude");
        cruiseSpeed = tag.getDouble("CruiseSpeed");
        trim = tag.getDouble("Trim");
    }

}
