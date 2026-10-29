package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.Craft;
import dev.aerolarge.compat.SableFlight;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * A rocket's boarding stairs (see {@link RocketStairsButtonBlock}): the stairs button inside by her front door keeps
 * her integral airstairs, as wide as the door, out of it down to the ground (see {@link BoardingAirstairs}),
 * folded away in a bay under her entry floor behind a hatch in her skin below the door, and the sliding airlock doors
 * across the doorway, which open as the stairs come out and shut as they fold away (see {@link AirlockDoors}). The
 * stairs come down of their own accord once she first stands built, and again once she is down on her fins after
 * flying with her autopilot off; they fold away as soon as her autopilot is set to fly or she moves, and her countdown
 * waits until they are all away and the doors shut (see {@link #holdsCountdown}), as they wait, with a chime, until
 * nobody is on them or in the doorway. Any of her stairs buttons works the stairs by hand while she stands with her
 * autopilot off, and her airlock panel the doors while the stairs are out; flying, either only sounds a low note. The
 * call button low on her front right fin, calling the stairs from the ground, keeps no flight of its own.
 */
public class RocketStairsBlockEntity extends BlockEntity implements Airstairs, BoardingAirstairs.Keeper {
    private static final Loaded<RocketStairsBlockEntity> LOADED = new Loaded<>(RocketStairsBlockEntity.class);
    /** How often the stairs look whether she has set off or come to rest, in ticks. */
    private static final int WATCH_TICKS = 10;
    /** Faster than this, in blocks a second, she is moving: the stairs fold away, and will not come down. */
    private static final double MOVING = 1.0;
    /** How many looks in a row she has to be still on the ground, after flying, before the stairs come down. */
    private static final int STILL_LOOKS = 4;

    /** Her airstairs, kept by the button by her door; none in the call button. */
    @Nullable
    private BoardingAirstairs stairs;
    /** The airlock doors across her doorway, kept with the stairs; none in a rocket built before them. */
    @Nullable
    private AirlockDoors doors;
    /** Whether she has flown since the stairs were last worked, so they come down by themselves once she is down. */
    private boolean flown;
    private int stillLooks;

    public RocketStairsBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.ROCKET_STAIRS_ENTITY.get(), pos, state);
    }

    /**
     * Hands the button the airstairs it keeps, as the rocket is built with them away: where their head is from it, the
     * ways out and to the right, how wide they are, how far they come down and in how many sections, their hatch, each
     * block of it from the button, and the bay they stow in. They come down of their own accord once she is found
     * standing on the ground.
     */
    public void rigAirstairs(BlockPos head, Direction out, Direction right, int width, int rise, int sections,
                             List<AirstairControllerBlockEntity.Step> hatch, float hatchInset, int mouth) {
        stairs = new BoardingAirstairs(this, head, out, right, rise, sections, width, hatch, new BoardingAirstairs.Bay(hatchInset, mouth));
        flown = true;
        setChanged();
    }

    /**
     * Hands the button the airlock doors across the doorway its stairs come out of, as she is built with them shut:
     * where the doorway's bottom left block seen from outside is from it, the ways out and to the right, and how many
     * blocks wide and rows tall it is.
     */
    public void rigDoors(BlockPos corner, Direction out, Direction right, int width, int rows) {
        doors = new AirlockDoors(this, corner, out, right, width, rows);
        setChanged();
    }

    /** The airlock doors it keeps, for its renderer to draw; none in a rocket built before them, or the call button. */
    @Nullable
    public AirlockDoors doors() {
        return doors;
    }

    /** Her stairs button whose airlock doors an airlock panel at a block stands by; none by an open doorway. */
    @Nullable
    static RocketStairsBlockEntity doorsBy(ServerLevel level, BlockPos panel) {
        for (RocketStairsBlockEntity keeper : LOADED.in(level)) {
            if (keeper.doors != null && keeper.doors.near(panel)) {
                return keeper;
            }
        }
        return null;
    }

    /**
     * Her airlock panel used, on the server: while she stands with her stairs out, its cycle opens the doors, or they
     * shut; flying, or with her stairs away and nothing to step out onto, only a low note.
     */
    void workDoors(ServerLevel level, BlockPos panel) {
        if (doors == null) {
            return;
        }
        if (flying(level, worldPosition) || stairs == null || !stairs.down()) {
            level.playSound(null, panel, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0F, 0.6F);
            return;
        }
        doors.cycle(level, panel);
    }

    /** Opens the doors as the stairs set out, or shuts them once the flight is back off the sill. */
    @Override
    public void doorTo(ServerLevel level, boolean open) {
        if (doors != null) {
            doors.set(level, open);
        }
    }

    /**
     * A stairs button pressed, on the server: her stairs fold away if they are down, or come down if they are away;
     * nothing but a low note while she flies.
     */
    static void press(ServerLevel level, BlockPos button) {
        List<RocketStairsBlockEntity> flights = flights(level, button);
        if (flights.isEmpty()) {
            return;
        }
        if (flying(level, button)) {
            level.playSound(null, button, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0F, 0.6F);
            return;
        }
        boolean anyDown = flights.stream().anyMatch(flight -> flight.stairs.down());
        for (RocketStairsBlockEntity flight : flights) {
            flight.flown = false;
            flight.go(level, !anyDown);
        }
    }

    /** Every flight of the rocket a block is part of, by the buttons keeping them; only its own off a rocket. */
    private static List<RocketStairsBlockEntity> flights(ServerLevel level, BlockPos button) {
        AutopilotBlockEntity rocket = AutopilotBlockEntity.framedBy(level, button, Craft.ROCKET);
        BoundingBox frame = rocket == null ? null : rocket.frame();
        if (frame == null) {
            return level.getBlockEntity(button) instanceof RocketStairsBlockEntity own && own.stairs != null ? List.of(own) : List.of();
        }
        return LOADED.in(level).stream().filter(flight -> flight.stairs != null && frame.isInside(flight.getBlockPos())).toList();
    }

    /**
     * Whether she flies: moving, or her autopilot set to fly, from the moment it is engaged and its countdown waits
     * until it has brought her down and switched itself off.
     */
    private static boolean flying(ServerLevel level, BlockPos pos) {
        if (SableFlight.velocity(level, Vec3.atCenterOf(pos)).length() > MOVING) {
            return true;
        }
        AutopilotBlockEntity rocket = AutopilotBlockEntity.framedBy(level, pos, Craft.ROCKET);
        return rocket != null && rocket.phase() != AutopilotBlockEntity.Phase.OFF;
    }

    /**
     * Whether her stairs hold her countdown before it begins (see {@code RocketPilot#holdCountdownWhile}): any of them
     * in her autopilot's frame not yet all away, or their doors not yet shut.
     */
    public static boolean holdsCountdown(ServerLevel level, BlockPos autopilotAt) {
        BoundingBox frame = level.getBlockEntity(autopilotAt) instanceof AutopilotBlockEntity autopilot ? autopilot.frame() : null;
        if (frame == null) {
            return false;
        }
        for (RocketStairsBlockEntity flight : LOADED.in(level)) {
            if (flight.stairs != null && frame.isInside(flight.getBlockPos()) && !flight.closedUp()) {
                return true;
            }
        }
        return false;
    }

    /** Whether the stairs are all away and the doors shut. */
    private boolean closedUp() {
        return stairs != null && stairs.stowed() && (doors == null || doors.shut());
    }

    /** Sets the stairs off down or up, if they are not already going that way. */
    private void go(ServerLevel level, boolean toDown) {
        if (stairs != null) {
            stairs.go(level, toDown);
            sync();
        }
    }

    /** Once a tick, on the server: the stairs a tick further, and now and then a look at how she is doing. */
    void serverTick(ServerLevel level) {
        if (stairs == null) {
            return;
        }
        if (level.getGameTime() % WATCH_TICKS == 0) {
            watch(level);
        }
        stairs.tick(level);
        if (doors != null) {
            doors.tick(level);
        }
    }

    /** Once a tick, on a client: the stairs' clock, so they are drawn on their way between the server's word. */
    void clientTick() {
        if (stairs != null) {
            stairs.clientTick();
        }
        if (doors != null) {
            doors.clientTick();
        }
    }

    /**
     * Folds the stairs away as she sets off, by her autopilot or otherwise, and makes sure the doors shut behind them;
     * brings them down again once she is still on the ground after flying, her autopilot off.
     */
    private void watch(ServerLevel level) {
        if (flying(level, worldPosition)) {
            stillLooks = 0;
            flown = true;
            if (stairs.down()) {
                go(level, false);
            } else if (stairs.stowed() && doors != null && !doors.shutting()) {
                doors.set(level, false);
            }
            return;
        }
        stillLooks = flown && stairs.stowed() ? stillLooks + 1 : 0;
        if (stillLooks >= STILL_LOOKS && stairs.onGround(level)) {
            flown = false;
            go(level, true);
        }
    }

    @Nullable
    @Override
    public Frame frame() {
        return stairs == null ? null : stairs.frame();
    }

    @Nullable
    @Override
    public AirstairFlight.Motion motion(float partialTick) {
        return stairs == null ? null : stairs.motion(partialTick);
    }

    @Override
    public boolean holds(Level level, BlockPos pos) {
        return stairs != null && stairs.holds(level, pos);
    }

    @Override
    public void sync() {
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
        tag.putBoolean("Flown", flown);
        if (stairs != null) {
            CompoundTag airstairs = new CompoundTag();
            stairs.save(airstairs);
            tag.put("Airstairs", airstairs);
        }
        if (doors != null) {
            CompoundTag saved = new CompoundTag();
            doors.save(saved);
            tag.put("Doors", saved);
        }
        Turning.save(tag, getBlockState());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Rotation turn = Turning.since(tag, getBlockState());
        flown = tag.getBoolean("Flown");
        stairs = tag.contains("Airstairs") ? BoardingAirstairs.load(this, tag.getCompound("Airstairs"), turn, registries) : null;
        doors = tag.contains("Doors") ? AirlockDoors.load(this, tag.getCompound("Doors"), turn) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
