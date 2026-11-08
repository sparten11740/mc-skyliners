package dev.skyliners.zerog;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.LedPanelBlockEntity;
import dev.skyliners.block.Loaded;
import dev.skyliners.block.Mount;
import dev.skyliners.block.Turning;
import dev.skyliners.compat.SableFlight;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A gravity switch's block entity (see {@link GravitySwitchBlock}): the room it makes weightless, and, while its
 * gravity is off, everything floating there (see {@link ZeroGravity}). The freighter's switch in her club is told its
 * room as she is built, the club's wedge between her room walls out from her ring corridor, turned as she was placed;
 * a switch set by hand measures the room round it each time its gravity goes off, as far as the walls, floor and
 * ceiling reach from where it stands (see {@link #measure}), so it works wherever it is put. Only while its gravity is
 * off does it tick, and then it looks only in the box round its room: aboard a ship that box is among her blocks, and
 * whoever stands in her is in the world, so each is seen in her frame to be found in it.
 *
 * <p>Switched off, it whirrs down and her computer says "Gravity off. Try not to hit your head."; switched back on, it
 * thunks and her computer says "Gravity back on. Mind your landing.", everyone floating falling slowly for a few seconds.
 * Gravity comes back on by itself too as her ship lands hard or hits something, when the server config's
 * {@code freighter.zeroGravity} is switched off, and for whatever leaves the room. Aboard a ship moving along, only
 * players float: Sable carries them along with her in the air (see the client's {@code CarriedAboard}), but would leave
 * a floating mob or item behind to be thrown against her walls, so those keep their weight until she slows.
 *
 * <p>Kept track of on the clients too, which hum, sparkle and float rubber ducks in a weightless room (see the client's
 * {@code Weightless}). What floats is never saved: a switch loaded again starts afresh.
 */
public class GravitySwitchBlockEntity extends BlockEntity {
    private static final Loaded<GravitySwitchBlockEntity> LOADED = new Loaded<>(GravitySwitchBlockEntity.class, true);
    /**
     * How far a switch set by hand measures its room out round it, in blocks: across, along the floor each way, and up
     * from the floor to the ceiling and down from it to the floor.
     */
    private static final int REACH_ACROSS = 10;
    private static final int REACH_UP = 8;
    private static final int REACH_DOWN = 4;
    /**
     * How fast her ship may move at the switch, in blocks a second, for mobs and items to float: slower, nothing leaves
     * them behind.
     */
    private static final double STILL_ENOUGH = 1;
    /**
     * The most her ship's speed may change at the switch in a tick, in blocks a second, beyond what gravity does, before
     * gravity comes back on by itself: a hard landing, or running into something.
     */
    private static final double JOLT = 5;
    /** How long after the whirr down her computer speaks, in ticks, so its words are heard over it. */
    private static final int SAY_AFTER = 25;
    /** How loud the switch's whirr and thunk are, and her computer's voice. */
    private static final float SWITCH_VOLUME = 1.0F;
    private static final float VOICE_VOLUME = 1.0F;
    /** How loud the click is as it is pressed, and its buzz where gravity may not go off. */
    private static final float CLICK_VOLUME = 0.6F;
    private static final float BUZZ_VOLUME = 0.8F;
    private static final float BUZZ_PITCH = 0.5F;
    /** When nothing is waiting to be said. */
    private static final long NEVER = Long.MAX_VALUE;
    /** Set by the client: each tick of a weightless room's switch there, for its hum, its sparkles and its ducks. */
    public static Consumer<GravitySwitchBlockEntity> clientTicker = gravitySwitch -> { };

    /** The box round its room, its two corners as offsets from the switch; null before it has one. */
    @Nullable
    private BlockPos from;
    @Nullable
    private BlockPos to;
    /**
     * For a room shaped as a wedge round a ship's axis, as the freighter's club is: the axis as an offset from the
     * switch, the bearings the room lies between, in degrees clockwise from north, and how far out from the axis it
     * starts, in blocks; a null axis for a room that is its whole box.
     */
    @Nullable
    private BlockPos wedgeAxis;
    private float wedgeFrom;
    private float wedgeTo;
    private float inner;
    /** Whether it was told its room as its ship was built, rather than measuring the room round it. */
    private boolean fitted;
    /** What floats in its room now, on the server, each with the velocity it was left with last tick. */
    private Map<UUID, Vec3> floating = new HashMap<>();
    /** How fast her ship moved at the switch last tick, in blocks a second; null before it has looked. */
    @Nullable
    private Vec3 lastVelocity;
    /** The game time her computer says gravity is off at. */
    private long sayAt = NEVER;

    public GravitySwitchBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.GRAVITY_SWITCH_ENTITY.get(), pos, state);
    }

    /** The switches loaded in a level, on the server or on this client. */
    public static List<GravitySwitchBlockEntity> loaded(Level level) {
        return LOADED.in(level);
    }

    /** Whether a block lies in a room whose gravity is off, on the server or on this client. */
    public static boolean weightlessAt(Level level, BlockPos pos) {
        for (GravitySwitchBlockEntity gravitySwitch : loaded(level)) {
            WeightlessRoom room = gravitySwitch.weightless() ? gravitySwitch.room() : null;
            if (room != null && room.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    /** Whether its room's gravity is off. */
    public boolean weightless() {
        return getBlockState().getValue(GravitySwitchBlock.WEIGHTLESS);
    }

    /** The room it makes weightless, in the level as it stands; null before it has one. */
    @Nullable
    public WeightlessRoom room() {
        if (from == null || to == null) {
            return null;
        }
        LedPanelBlockEntity.Wedge wedge = wedgeAxis == null ? null : new LedPanelBlockEntity.Wedge(worldPosition.offset(wedgeAxis), wedgeFrom, wedgeTo);
        return new WeightlessRoom(BoundingBox.fromCorners(worldPosition.offset(from), worldPosition.offset(to)), wedge, inner);
    }

    /**
     * Tells it its room, as the freighter is built: the corners of the box round it, from the switch, the axis it lies
     * round, from the switch, the bearings it lies between and how far out from the axis it starts, in her frame, all
     * turned as she was placed.
     */
    public void room(BlockPos boxFrom, BlockPos boxTo, BlockPos axis, float bearingFrom, float bearingTo, float out) {
        from = boxFrom;
        to = boxTo;
        wedgeAxis = axis;
        wedgeFrom = bearingFrom;
        wedgeTo = bearingTo;
        inner = out;
        fitted = true;
        setChanged();
    }

    /** Pressed, by hand or by a redstone pulse: its gravity the other way, with a click. */
    void press(ServerLevel level) {
        level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, CLICK_VOLUME, 1.0F);
        switchGravity(level, weightless());
    }

    /**
     * Switches its room's gravity on or off, on the server, with its sound. Off, a switch set by hand measures its room
     * first, and her computer speaks a moment after; with the server config's {@code freighter.zeroGravity} switched off,
     * it only buzzes. On, everything floating is let go of, and her computer says so.
     */
    void switchGravity(ServerLevel level, boolean on) {
        if (on != weightless()) {
            return;
        }
        if (!on && !SkylinersConfig.FREIGHTER_ZERO_GRAVITY.get()) {
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, BUZZ_VOLUME, BUZZ_PITCH);
            return;
        }
        if (!on && !fitted) {
            measure(level);
        }
        level.setBlock(worldPosition, getBlockState().setValue(GravitySwitchBlock.WEIGHTLESS, !on), Block.UPDATE_ALL);
        SoundEvent sound = on ? SkylinersRegistry.GRAVITY_SWITCH_ON.get() : SkylinersRegistry.GRAVITY_SWITCH_OFF.get();
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, SWITCH_VOLUME, 1.0F);
        lastVelocity = null;
        if (on) {
            letGoOfAll(level);
            sayAt = NEVER;
            say(level, SkylinersRegistry.FREIGHTER_COMPUTER_GRAVITY.get());
        } else {
            sayAt = level.getGameTime() + SAY_AFTER;
        }
    }

    /** Her computer's voice, in the middle of the room, heard all through it. */
    private void say(ServerLevel level, SoundEvent line) {
        WeightlessRoom room = room();
        Vec3 at = room != null ? room.middle() : Vec3.atCenterOf(worldPosition);
        level.playSound(null, at.x, at.y, at.z, line, SoundSource.BLOCKS, VOICE_VOLUME, 1.0F);
    }

    /**
     * Measures the room round a switch set by hand, as far as REACH_ACROSS, REACH_UP and REACH_DOWN: from the open block
     * before it on a wall, or its own on a floor, down to the floor, then out along it each way as far as the first
     * thing in the way at either of the two rows over the floor, whichever reaches further, and up to the ceiling.
     */
    private void measure(Level level) {
        BlockState state = getBlockState();
        boolean onWall = state.getValue(GravitySwitchBlock.MOUNT) == Mount.WALL;
        BlockPos start = onWall ? worldPosition.relative(state.getValue(GravitySwitchBlock.FACING)) : worldPosition;
        int down = run(level, start, Direction.DOWN, REACH_DOWN);
        BlockPos floor = start.below(down);
        int up = run(level, floor, Direction.UP, REACH_UP);
        int[] reach = new int[4];
        for (Direction way : Direction.Plane.HORIZONTAL) {
            int low = run(level, floor, way, REACH_ACROSS);
            int high = up > 0 ? run(level, floor.above(), way, REACH_ACROSS) : 0;
            reach[way.get2DDataValue()] = Math.max(low, high);
        }
        BlockPos low = floor.offset(-reach[Direction.WEST.get2DDataValue()], 0, -reach[Direction.NORTH.get2DDataValue()]);
        BlockPos high = floor.offset(reach[Direction.EAST.get2DDataValue()], up, reach[Direction.SOUTH.get2DDataValue()]);
        from = low.subtract(worldPosition);
        to = high.subtract(worldPosition);
        wedgeAxis = null;
        setChanged();
    }

    /** How many open blocks run from a block on one way, up to so many: those a player or a floating thing passes. */
    private static int run(Level level, BlockPos start, Direction way, int most) {
        BlockPos.MutableBlockPos at = start.mutable();
        for (int open = 0; open < most; open++) {
            at.move(way);
            if (!level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                return open;
            }
        }
        return most;
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, GravitySwitchBlockEntity gravitySwitch) {
        if (level instanceof ServerLevel server) {
            gravitySwitch.keepWeightless(server);
        }
    }

    static void clientTick(Level level, BlockPos pos, BlockState state, GravitySwitchBlockEntity gravitySwitch) {
        clientTicker.accept(gravitySwitch);
    }

    /**
     * One tick of its room with gravity off: back on by itself if its ship was jolted or the server config says so;
     * her computer's word once it is due; everything floating in the room kept floating, and whatever has gone from it
     * let go of.
     */
    private void keepWeightless(ServerLevel level) {
        long now = level.getGameTime();
        Vec3 velocity = SableFlight.velocity(level, Vec3.atCenterOf(worldPosition));
        if (!SkylinersConfig.FREIGHTER_ZERO_GRAVITY.get() || jolted(level, velocity)) {
            switchGravity(level, true);
            return;
        }
        if (now >= sayAt) {
            sayAt = NEVER;
            say(level, SkylinersRegistry.FREIGHTER_COMPUTER_ZERO_GRAVITY.get());
        }
        WeightlessRoom room = room();
        if (room == null) {
            return;
        }
        boolean still = velocity.lengthSqr() < STILL_ENOUGH * STILL_ENOUGH;
        Map<UUID, Vec3> seen = new HashMap<>();
        for (Entity entity : inRoom(level, room)) {
            if (still || entity instanceof Player) {
                seen.put(entity.getUUID(), ZeroGravity.keepFloating(level, entity, floating.get(entity.getUUID()), now));
            }
        }
        floating.keySet().stream().filter(id -> !seen.containsKey(id)).forEach(id -> ZeroGravity.letGo(level.getEntity(id)));
        floating = seen;
    }

    /**
     * Whether her ship's speed at the switch changed more this tick than gravity changes it, as she lands hard or hits
     * something; never for a switch on the ground, which does not move.
     */
    private boolean jolted(ServerLevel level, Vec3 velocity) {
        Vec3 was = lastVelocity;
        lastVelocity = velocity;
        if (was == null) {
            return false;
        }
        double fall = SableFlight.gravity(level, Vec3.atCenterOf(worldPosition)) / level.tickRateManager().tickrate();
        return velocity.subtract(was).add(0, fall, 0).length() > JOLT;
    }

    /**
     * Whatever floats in its room: aboard a ship, both what lives among her blocks, as her loose things do, and what is
     * in the world inside her, each seen in her frame; elsewhere, what is in the room's box.
     */
    private List<Entity> inRoom(ServerLevel level, WeightlessRoom room) {
        AABB box = room.bounds();
        Set<Entity> near = new LinkedHashSet<>(level.getEntities((Entity) null, box, ZeroGravity::floats));
        if (SableFlight.aboard(level, box.getCenter())) {
            near.addAll(level.getEntities((Entity) null, inWorld(level, box), ZeroGravity::floats));
        }
        return near.stream()
            .filter(entity -> room.contains(SableFlight.toPlot(level, worldPosition, entity.getBoundingBox().getCenter(), false)))
            .toList();
    }

    /** The box in the world round a box among her blocks, as her ship holds it now: round each of its corners. */
    private static AABB inWorld(Level level, AABB box) {
        AABB round = null;
        for (int corner = 0; corner < 8; corner++) {
            Vec3 at = SableFlight.toWorld(level, new Vec3((corner & 1) == 0 ? box.minX : box.maxX, (corner & 2) == 0 ? box.minY : box.maxY,
                (corner & 4) == 0 ? box.minZ : box.maxZ));
            round = round == null ? new AABB(at, at) : round.minmax(new AABB(at, at));
        }
        return round;
    }

    /** Lets go of everything floating in its room, gently. */
    private void letGoOfAll(ServerLevel level) {
        floating.keySet().forEach(id -> ZeroGravity.letGo(level.getEntity(id)));
        floating = new HashMap<>();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LOADED.add(this);
    }

    /** Gone, or its chunk unloaded, with gravity off: everything floating is let go of first. */
    @Override
    public void setRemoved() {
        if (level instanceof ServerLevel server) {
            letGoOfAll(server);
        }
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
        if (from != null && to != null) {
            Turning.save(tag, getBlockState());
            tag.put("From", NbtUtils.writeBlockPos(from));
            tag.put("To", NbtUtils.writeBlockPos(to));
        }
        if (wedgeAxis != null) {
            tag.put("WedgeAxis", NbtUtils.writeBlockPos(wedgeAxis));
            tag.putFloat("WedgeFrom", wedgeFrom);
            tag.putFloat("WedgeTo", wedgeTo);
            tag.putFloat("Inner", inner);
        }
        tag.putBoolean("Fitted", fitted);
    }

    /** Its room turned with its ship, if it faces another way than it was saved facing (see {@link Turning}). */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Rotation turn = Turning.since(tag, getBlockState());
        from = Turning.turn(NbtUtils.readBlockPos(tag, "From").orElse(null), turn);
        to = Turning.turn(NbtUtils.readBlockPos(tag, "To").orElse(null), turn);
        wedgeAxis = Turning.turn(NbtUtils.readBlockPos(tag, "WedgeAxis").orElse(null), turn);
        wedgeFrom = wedgeAxis == null ? 0 : LedPanelBlockEntity.Wedge.turned(tag.getFloat("WedgeFrom"), turn);
        wedgeTo = wedgeAxis == null ? 0 : LedPanelBlockEntity.Wedge.turned(tag.getFloat("WedgeTo"), turn);
        inner = wedgeAxis == null ? 0 : tag.getFloat("Inner");
        fitted = tag.getBoolean("Fitted");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
