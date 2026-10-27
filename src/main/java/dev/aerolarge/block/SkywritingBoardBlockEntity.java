package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.Course;
import dev.aerolarge.autopilot.SetAutopilot;
import dev.aerolarge.skywriting.SkyShape;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The skywriting board's block entity: the shape its zeppelin is writing across the sky, if any, laid out as a path of
 * points in the world ahead of her as she begins it and turned with her heading, and how far along it she is; and where
 * the smoke streams from, under the middle of her belly, set as she is built (turned as she was placed). While she
 * writes, her pilot steers her along the path, a little ahead of her, at her writing speed (see {@link
 * dev.aerolarge.skywriting.Skywriting}); any new order on her autopilot, switching it off, or landing gives it up.
 * Done, she holds the heading she ends on. Every client is told when a shape begins and ends and when its smoke starts,
 * and draws the smoke meanwhile (see the client's {@code SkyTrails}). The shape is not saved: a world closed while she
 * writes finds her flying as she was set to.
 */
public class SkywritingBoardBlockEntity extends BlockEntity {
    private static final Loaded<SkywritingBoardBlockEntity> LOADED = new Loaded<>(SkywritingBoardBlockEntity.class, true);
    /** How fast she writes, in blocks a second: slow enough that she turns tightly enough for the shapes. */
    private static final double WRITING_SPEED = 4;
    /** How far ahead of her the shape begins, in blocks, so she sets off into it straight. */
    private static final double LEAD = 30;
    /**
     * How near a point of the path she comes to have passed it, how far ahead along the path she steers for, and how
     * far off it she may stray before the shape is given up, in blocks.
     */
    private static final double CAPTURE = 24;
    private static final double LOOKAHEAD = 60;
    private static final double ASTRAY = 200;
    /**
     * How long the shape may go unsteered before it is given up, in ticks: her autopilot switched off, or the server's
     * switch, as she writes it.
     */
    private static final long ABANDONED = 20;

    @Nullable
    private SkyShape shape;
    /** Whether the shape is still to be laid out ahead of her, on her pilot's next tick. */
    private boolean pending;
    private final List<Vec3> path = new ArrayList<>();
    private int next;
    /** Whether the smoke streams, from the shape's first point on. */
    private boolean smoking;
    /** The game time her pilot last asked how to steer her along the shape, or it was pressed. */
    private long steered;
    /** What her autopilot was set to as she began, to give the shape up for anything set afresh. */
    @Nullable
    private Orders orders;
    @Nullable
    private BlockPos smoke;

    /** What an autopilot is set to fly: the height, speed and heading, and the way it steers, round what pin. */
    private record Orders(double altitude, double speed, double heading, AutopilotBlockEntity.Lateral lateral, double pinX, double pinZ,
                          double radius) {
        static Orders of(AutopilotBlockEntity autopilot) {
            return new Orders(autopilot.targetAltitude(), autopilot.targetSpeed(), autopilot.targetHeading(), autopilot.lateral(), autopilot.orbitX(),
                autopilot.orbitZ(), autopilot.orbitRadius());
        }
    }

    public SkywritingBoardBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.SKYWRITING_BOARD_ENTITY.get(), pos, state);
    }

    /** The boards loaded in a level, on the server or on this client. */
    public static List<SkywritingBoardBlockEntity> loaded(Level level) {
        return LOADED.in(level);
    }

    /** Tells it where the smoke streams from, from the board, as the zeppelin is built. */
    public void rig(BlockPos smoke) {
        this.smoke = smoke;
        setChanged();
    }

    /** Where the smoke streams from, from the board; null for a board put down by hand. */
    @Nullable
    public BlockPos smoke() {
        return smoke;
    }

    /** The shape she writes now, or is about to; null for none. */
    @Nullable
    public SkyShape shape() {
        return shape;
    }

    /** Whether she is writing a shape now, past its first point: what the clients draw smoke for. */
    public boolean writing() {
        return shape != null && smoking;
    }

    /**
     * A shape's button pressed, on the server: she begins it on her pilot's next tick, if she flies level; pressed
     * again while she writes it, it is given up. A chime, or a low note for a shape she cannot fly now.
     */
    void press(ServerLevel level, SkyShape pressed) {
        if (pressed == shape) {
            stop(level, false);
            return;
        }
        AutopilotBlockEntity autopilot = AutopilotBlockEntity.zeppelinFraming(level, worldPosition);
        if (autopilot == null || !flyingLevel(autopilot)) {
            play(level, SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F);
            return;
        }
        shape = pressed;
        pending = true;
        smoking = false;
        steered = level.getGameTime();
        play(level, SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2F);
        changed();
    }

    /** Whether the autopilot flies her level, neither taking off, landing, nor switched off. */
    private static boolean flyingLevel(AutopilotBlockEntity autopilot) {
        AutopilotBlockEntity.Phase phase = autopilot.phase();
        return (phase == AutopilotBlockEntity.Phase.CLIMB || phase == AutopilotBlockEntity.Phase.CRUISE || phase == AutopilotBlockEntity.Phase.DESCENT)
            && !autopilot.comingIn();
    }

    /**
     * Steers her along the shape this tick, on the server, from where the middle of her lift is and the heading she
     * has; null once there is no shape, or it is done or given up.
     */
    @Nullable
    public Course.Steer steer(ServerLevel level, AutopilotBlockEntity autopilot, Vec3 middle, double heading) {
        if (shape == null) {
            return null;
        }
        steered = level.getGameTime();
        if (pending) {
            layOut(autopilot, middle, heading);
        }
        if (!flyingLevel(autopilot) || !Orders.of(autopilot).equals(orders)) {
            stop(level, false);
            return null;
        }
        while (next < path.size() && horizontal(path.get(next), middle) < CAPTURE) {
            next++;
        }
        if (!smoking && next > 0) {
            smoking = true;
            changed();
        }
        if (next >= path.size()) {
            finish(level, autopilot, heading);
            return null;
        }
        if (horizontal(path.get(next), middle) > ASTRAY) {
            stop(level, false);
            return null;
        }
        Vec3 target = ahead(middle);
        return new Course.Steer(Mth.wrapDegrees(Math.toDegrees(Math.atan2(-(target.x - middle.x), target.z - middle.z))), WRITING_SPEED);
    }

    /** Once a tick, on the server: the shape given up once her pilot has stopped steering her along it. */
    void serverTick(ServerLevel level) {
        if (shape != null && level.getGameTime() - steered > ABANDONED) {
            stop(level, false);
        }
    }

    /** The shape's path laid out in the world, beginning a little ahead of her and turned with her heading. */
    private void layOut(AutopilotBlockEntity autopilot, Vec3 middle, double heading) {
        double yaw = Math.toRadians(heading);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 starboard = new Vec3(-forward.z, 0, forward.x);
        Vec3 start = new Vec3(middle.x, 0, middle.z).add(forward.scale(LEAD));
        path.clear();
        for (Vec3 point : shape.path()) {
            path.add(start.add(starboard.scale(point.x)).add(forward.scale(point.z)));
        }
        next = 0;
        pending = false;
        orders = Orders.of(autopilot);
    }

    /** The first point of the path from the one to pass next that is far enough ahead of her to steer for. */
    private Vec3 ahead(Vec3 middle) {
        for (int k = next; k < path.size(); k++) {
            if (horizontal(path.get(k), middle) >= LOOKAHEAD) {
                return path.get(k);
            }
        }
        return path.get(path.size() - 1);
    }

    private static double horizontal(Vec3 point, Vec3 middle) {
        return Math.hypot(point.x - middle.x, point.z - middle.z);
    }

    /** Done: a bell, and she holds the heading she ends on rather than turning back to the one she was set to. */
    private void finish(ServerLevel level, AutopilotBlockEntity autopilot, double heading) {
        stop(level, true);
        AutopilotBlockEntity.Settings now = autopilot.offered();
        int bearing = Math.floorMod((int) Math.round(heading + 180), 360);
        autopilot.set(SetAutopilot.Action.FLY, new AutopilotBlockEntity.Settings(now.speed(), now.altitude(), bearing, AutopilotBlockEntity.Lateral.HEADING,
            now.circle(), now.destination(), false));
    }

    /** The shape ended, done or given up, for every client too; a bell for one done, a low note for one given up. */
    private void stop(ServerLevel level, boolean done) {
        shape = null;
        pending = false;
        path.clear();
        smoking = false;
        play(level, done ? SoundEvents.NOTE_BLOCK_BELL.value() : SoundEvents.NOTE_BLOCK_BASS.value(), done ? 1 : 0.6F);
        changed();
    }

    private void play(ServerLevel level, SoundEvent sound, float pitch) {
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 1, pitch);
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (smoke != null) {
            tag.put("Smoke", NbtUtils.writeBlockPos(smoke));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        smoke = NbtUtils.readBlockPos(tag, "Smoke").orElse(null);
        if (tag.contains("Shape")) {
            shape = named(tag.getString("Shape"));
            smoking = tag.getBoolean("Smoking");
        }
    }

    /** The shape of this name; null for none, or a name no shape has. */
    @Nullable
    private static SkyShape named(String name) {
        for (SkyShape each : SkyShape.values()) {
            if (each.name().equals(name)) {
                return each;
            }
        }
        return null;
    }

    /** What every client is sent: where the smoke streams from, and the shape she writes and whether it smokes. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        tag.putString("Shape", shape == null ? "" : shape.name());
        tag.putBoolean("Smoking", smoking);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
