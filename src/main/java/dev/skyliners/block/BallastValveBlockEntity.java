package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.VoiceLength;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The water ballast valve's block entity: where the zeppelin's ground handlers would stand and the points on her hull
 * their lines are made fast to, the officer who gives the call among them, and the outlet in her belly her bow's water
 * pours from, all set as she is built (turned as she was placed); and the game time her last take-off from the ground
 * began, sent with the outlet to every client, which pours the water from it (see the client's {@code UpShipScene}).
 * Taking off, she is held on the lines: the ship's bell, a short blast of the officer's whistle to hold fast, the bow's
 * water ballast pouring out, the officer's call "Up ship!" with a long blast, and the lines let go together. The
 * handlers are not drawn; their whistles and the call are heard from where the officer stands.
 */
public class BallastValveBlockEntity extends BlockEntity {
    private static final Loaded<BallastValveBlockEntity> LOADED = new Loaded<>(BallastValveBlockEntity.class, true);
    /** No take-off begun. */
    private static final long NONE = Long.MIN_VALUE;
    /**
     * The take-off's beats, in ticks from its start: the whistle to hold fast, the water pouring from and to, the call
     * "Up ship!", the lines let go, and the take-off over.
     */
    public static final int HOLD_FAST = 10;
    public static final int POUR_FROM = 30;
    public static final int POUR_TO = 90;
    public static final int CALL = 110;
    public static final int RELEASE = 130;
    public static final int GONE = 220;
    /**
     * How long a tick takes, in real time, at least, once the server is ticking at its pace again, in milliseconds,
     * and for how many ticks in a row: straight after the zeppelin is assembled it catches up on the ticks it fell
     * behind by, back to back, which would rush the beats through in a moment. And how long the take-off is held
     * waiting for that, at most, in ticks.
     */
    private static final long STEADY_MILLIS = 45;
    private static final int STEADY_TICKS = 10;
    private static final long WAIT_MOST = 200;
    /**
     * How long the take-off is held, at most, for her control car to fall quiet before the beats begin, so that the
     * officer's call never comes over a cabin announcement: the longest one may run, and a little more. And how long
     * the call runs, in ticks, if its sound cannot be read.
     */
    private static final long QUIET_MOST = 320;
    private static final int CALL_LENGTH = 40;
    /**
     * How long the hold may go unasked before it is given up, in ticks, its beats and all: the autopilot switched off
     * as she waits on the lines, or the ship gone.
     */
    private static final long ABANDONED = 5;
    /** How loud the bell, the whistles, the water and the call are: heard some 16 blocks round for each. */
    private static final float BELL_VOLUME = 2;
    private static final float WHISTLE_VOLUME = 3;
    private static final float WATER_VOLUME = 3;
    private static final float CALL_VOLUME = 4;

    /**
     * Where a handler stands on the ground under the ship and what they face, and the point on her hull their line is
     * made fast to; none for the officer, who holds none. Offsets from the valve, among the ship's blocks.
     */
    public record Station(BlockPos stand, BlockPos faces, @Nullable BlockPos line) {
    }

    private final List<Station> stations = new ArrayList<>();
    @Nullable
    private BlockPos outlet;
    /**
     * The game time a take-off asked to be held, the one its beats began, once the server ticks steadily, and the one
     * it was last asked to hold her at. Not saved: a take-off on the lines does not outlast the world being closed.
     */
    private long asked = NONE;
    private long started = NONE;
    private long held = NONE;
    /**
     * The real time of the last tick, in milliseconds, and how many ticks in a row have come at the server's pace, to
     * tell it catching up from ticking steadily.
     */
    private long lastTickMillis;
    private int steadyTicks;

    public BallastValveBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.BALLAST_VALVE_ENTITY.get(), pos, state);
    }

    /** The valves loaded in a level, on the server or on this client. */
    public static List<BallastValveBlockEntity> loaded(Level level) {
        return LOADED.in(level);
    }

    /** Tells it where the handlers stand and where the water pours out, as the zeppelin is built. */
    public void rig(List<Station> stations, BlockPos outlet) {
        this.stations.clear();
        this.stations.addAll(stations);
        this.outlet = outlet;
        setChanged();
    }

    /** Where in her belly the water pours out, from the valve; null for a valve put down by hand. */
    @Nullable
    public BlockPos outlet() {
        return outlet;
    }

    /** Whether it has its handlers' stations, as one built into a zeppelin has. */
    public boolean rigged() {
        return outlet != null && !stations.isEmpty();
    }

    /**
     * A take-off from the ground asks to be held on the lines, on the server: its beats begin once the server ticks
     * steadily again (see {@link #serverTick}).
     */
    public void ask(long now) {
        asked = now;
        held = now;
        if (started != NONE) {
            started = NONE;
            changed();
        }
    }

    /** The beats begin, the floor in her control car held for them until the officer's call is over. */
    private void start(ServerLevel level, long now) {
        asked = NONE;
        started = now;
        AutopilotBlockEntity autopilot = AutopilotBlockEntity.zeppelinFraming(level, worldPosition);
        if (autopilot != null) {
            autopilot.holdFloor(CALL + VoiceLength.ticks(SkylinersRegistry.UP_SHIP_CALL, CALL_LENGTH));
        }
        changed();
    }

    /**
     * Whether the beats may begin, so long after the take-off asked to be held: the server ticking steadily and her
     * control car quiet, or each waited for as long as it may be.
     */
    private boolean ready(ServerLevel level, long waited) {
        boolean steady = steadyTicks >= STEADY_TICKS || waited >= WAIT_MOST - 1;
        return steady && (quiet(level) || waited >= WAIT_MOST + QUIET_MOST - 1);
    }

    /** Whether nothing is being said in her control car: no cabin announcement, which the beats wait for. */
    private boolean quiet(ServerLevel level) {
        AutopilotBlockEntity autopilot = AutopilotBlockEntity.zeppelinFraming(level, worldPosition);
        return autopilot == null || autopilot.quiet();
    }

    /** Gives the take-off on the lines up, its beats and all, for every client too. */
    private void cancel() {
        asked = NONE;
        started = NONE;
        changed();
    }

    /** Sends what it holds to every client, as it changes. */
    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Whether a take-off asked to be held is waiting for its beats to begin: for the server to tick steadily, and her
     * control car to fall quiet.
     */
    private boolean waiting(long now) {
        return asked != NONE && now >= asked && now - asked < WAIT_MOST + QUIET_MOST;
    }

    /**
     * Holds her on the lines a tick longer, asked by her pilot each tick while she waits: whether the handlers still
     * hold her, waiting for the beats to begin or on them until they let go.
     */
    public boolean hold(long now) {
        held = now;
        long since = since(now);
        return waiting(now) || since >= 0 && since < RELEASE;
    }

    /** How long ago her take-off on the lines began, in ticks; less than nothing for none, or one long over. */
    public long since(long now) {
        if (started == NONE || now < started || now - started >= GONE) {
            return -1;
        }
        return now - started;
    }

    /**
     * Once a tick on the server: the take-off's sounds on its beats, the bell, the whistles, the call and the lines let
     * go where the officer stands ahead of the control car, heard there and in the car, and the water at its outlet.
     */
    void serverTick(ServerLevel level) {
        long now = level.getGameTime();
        long millis = Util.getMillis();
        steadyTicks = lastTickMillis != 0 && millis - lastTickMillis >= STEADY_MILLIS ? steadyTicks + 1 : 0;
        lastTickMillis = millis;
        // Begun a tick before each wait runs out at the latest, so her pilot, ticking before or after it, never finds
        // her let go before the beats have begun.
        if (asked != NONE && ready(level, now - asked)) {
            start(level, now);
        }
        long since = since(now);
        boolean onTheLines = asked != NONE || since >= 0 && since < RELEASE;
        if (onTheLines && held != NONE && now - held > ABANDONED) {
            cancel();
            return;
        }
        if (since < 0 || outlet == null) {
            return;
        }
        BlockPos officer = officer();
        if (since == 0) {
            play(level, officer, SkylinersRegistry.LOOKOUT_BELL.get(), BELL_VOLUME);
        } else if (since == HOLD_FAST) {
            play(level, officer, SkylinersRegistry.HANDLER_WHISTLE_SHORT.get(), WHISTLE_VOLUME);
        } else if (since == POUR_FROM) {
            play(level, worldPosition.offset(outlet), SkylinersRegistry.WATER_BALLAST.get(), WATER_VOLUME);
        } else if (since == CALL) {
            level.playSound(null, officer, SkylinersRegistry.UP_SHIP_CALL.get(), SoundSource.VOICE, CALL_VOLUME, 1);
            play(level, officer, SkylinersRegistry.HANDLER_WHISTLE_LONG.get(), WHISTLE_VOLUME);
        } else if (since == RELEASE) {
            play(level, officer, SoundEvents.LEASH_KNOT_BREAK, WHISTLE_VOLUME);
        }
    }

    /** Where the officer stands, among the ship's blocks: the station with no line; the valve itself for none. */
    private BlockPos officer() {
        for (Station station : stations) {
            if (station.line() == null) {
                return worldPosition.offset(station.stand());
            }
        }
        return worldPosition;
    }

    private static void play(ServerLevel level, BlockPos at, SoundEvent sound, float volume) {
        level.playSound(null, at, sound, SoundSource.BLOCKS, volume, 1);
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
        ListTag saved = new ListTag();
        for (Station station : stations) {
            CompoundTag one = new CompoundTag();
            one.put("Stand", NbtUtils.writeBlockPos(station.stand()));
            one.put("Faces", NbtUtils.writeBlockPos(station.faces()));
            if (station.line() != null) {
                one.put("Line", NbtUtils.writeBlockPos(station.line()));
            }
            saved.add(one);
        }
        tag.put("Stations", saved);
        if (outlet != null) {
            tag.put("Outlet", NbtUtils.writeBlockPos(outlet));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stations.clear();
        for (Tag each : tag.getList("Stations", Tag.TAG_COMPOUND)) {
            CompoundTag one = (CompoundTag) each;
            BlockPos stand = NbtUtils.readBlockPos(one, "Stand").orElse(null);
            if (stand != null) {
                stations.add(new Station(stand, NbtUtils.readBlockPos(one, "Faces").orElse(stand), NbtUtils.readBlockPos(one, "Line").orElse(null)));
            }
        }
        outlet = NbtUtils.readBlockPos(tag, "Outlet").orElse(null);
        started = tag.contains("Started") ? tag.getLong("Started") : NONE;
    }

    /** What every client is sent: the outlet, and when the take-off on the lines began, for them to pour the water. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (outlet != null) {
            tag.put("Outlet", NbtUtils.writeBlockPos(outlet));
        }
        tag.putLong("Started", started);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
