package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import java.util.List;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A story's LED panel's block entity: what the story's strips are set to (see {@link LedSetting}), and where they run,
 * the box round them as two corners from the panel, set as the rocket is built and turned with it. The colour and the
 * mode go to the clients, which tint the strips and animate them (see the client's {@code LedLights}); a new brightness
 * the server sets on every strip of the story itself, as their block state, for the light they give. A change may wait
 * a few ticks (see {@link #change}), so the cockpit's console can run one up the rocket a story at a time. The console
 * dims the story's windows through it too, which it finds in her skin round its box (see {@link #dimWindows}). Kept
 * track of on the clients, which look up each strip's story by its box.
 */
public class LedPanelBlockEntity extends BlockEntity {
    private static final Loaded<LedPanelBlockEntity> LOADED = new Loaded<>(LedPanelBlockEntity.class, true);
    /** What {@link #windows} says while the console has not set the story's windows, as each is left by hand. */
    public static final int WINDOWS_UNSET = -1;
    /** What {@link #windowsAt} says while nothing waits. */
    private static final long NOT_WAITING = -1;
    /** How far past the box round its strips the story's windows may stand in her skin, in blocks. */
    private static final int WINDOW_REACH = 3;

    /** The box round the story's strips, its two corners as offsets from the panel; null for a panel set by hand. */
    @Nullable
    private BlockPos from;
    @Nullable
    private BlockPos to;
    /**
     * For a room shaped as a wedge round a ship's axis, as the freighter's are, whose box overlaps its neighbours': the
     * axis as an offset from the panel, and the bearings the room lies between, in degrees clockwise from north; null
     * for a story that is its whole box, as the rocket's are.
     */
    @Nullable
    private BlockPos wedgeAxis;
    private float wedgeFrom;
    private float wedgeTo;
    private LedSetting setting = LedSetting.PLAIN;
    /** A change waiting to be made, and the game time it is made at; none while nothing waits. */
    @Nullable
    private LedSetting waiting;
    private long waitingUntil;
    /** How dark the console last set the story's windows, for it to show. */
    private int windows = WINDOWS_UNSET;
    /** The game time the story's windows are to take that at. */
    private long windowsAt = NOT_WAITING;

    public LedPanelBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.LED_PANEL_ENTITY.get(), pos, state);
    }

    /** The panels loaded in a level, on the server or on this client. */
    public static List<LedPanelBlockEntity> loaded(Level level) {
        return LOADED.in(level);
    }

    /**
     * Tells it the story it sets, as the rocket is built: the corners of the box round its strips, from the panel
     * (turned as she was placed), and what its strips were laid set to.
     */
    public void story(BlockPos from, BlockPos to, LedSetting setting) {
        this.from = from;
        this.to = to;
        this.setting = setting;
        setChanged();
    }

    /**
     * Tells it the room it sets, as the freighter is built: the corners of the box round its strips, from the panel,
     * the axis the room lies round, from the panel, and the bearings it lies between, in her frame (all turned as she
     * was placed), and what its strips were laid set to.
     */
    public void room(BlockPos from, BlockPos to, BlockPos axis, float bearingFrom, float bearingTo, LedSetting setting) {
        story(from, to, setting);
        wedgeAxis = axis;
        wedgeFrom = bearingFrom;
        wedgeTo = bearingTo;
    }

    /**
     * A room shaped as a wedge round a ship's axis: the strips of it are those whose bearing from the axis, clockwise
     * from north, lies from one bearing round clockwise up to the other; measured between the blocks' own corners, as
     * between their middles alike.
     */
    public record Wedge(BlockPos axis, float from, float to) {
        /** Whether a block lies in the wedge. */
        public boolean contains(BlockPos pos) {
            double bearing = Math.toDegrees(Math.atan2(pos.getX() - axis.getX(), axis.getZ() - pos.getZ()));
            return Mth.positiveModulo(bearing - from, 360.0) < Mth.positiveModulo((double) to - from, 360.0);
        }

        /** A bearing turned by quarter turns clockwise, as a block placed turned is. */
        public static float turned(float bearing, Rotation turn) {
            return bearing + 90 * turn.ordinal();
        }
    }

    /** The wedge the room's strips lie in, in the level as the panel stands; null for a story that is its whole box. */
    @Nullable
    public Wedge wedge() {
        return wedgeAxis == null ? null : new Wedge(worldPosition.offset(wedgeAxis), wedgeFrom, wedgeTo);
    }

    /** What the story's strips are set to now. */
    public LedSetting setting() {
        return setting;
    }

    /** What they will be set to once any change waiting is made: what the next change starts from. */
    public LedSetting next() {
        return waiting != null ? waiting : setting;
    }

    /** The box round the story's strips, in the level as the panel stands; null for a panel set by hand. */
    @Nullable
    public BoundingBox box() {
        return from == null || to == null ? null : BoundingBox.fromCorners(worldPosition.offset(from), worldPosition.offset(to));
    }

    /**
     * Changes the story's setting, on the server, from what it is going to be, in so many ticks: at once for none. A
     * change made while another waits takes that one's place, both made together.
     */
    public void change(UnaryOperator<LedSetting> change, int delay) {
        if (level == null || level.isClientSide) {
            return;
        }
        waiting = change.apply(next());
        waitingUntil = level.getGameTime() + delay;
        setChanged();
        if (delay <= 0) {
            tick();
        }
    }

    /** How dark the console last set the story's windows, or {@link #WINDOWS_UNSET}. */
    public int windows() {
        return windows;
    }

    /** Sets the story's windows to a darkness, on the server, in so many ticks; the console shows it at once. */
    public void dimWindows(int dim, int delay) {
        if (level == null || level.isClientSide) {
            return;
        }
        windows = dim;
        windowsAt = level.getGameTime() + delay;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Each tick on the server: makes the changes waiting once their times have come. */
    void tick() {
        if (level == null) {
            return;
        }
        if (windowsAt != NOT_WAITING && level.getGameTime() >= windowsAt) {
            windowsAt = NOT_WAITING;
            setChanged();
            darkenWindows();
        }
        if (waiting == null || level.getGameTime() < waitingUntil) {
            return;
        }
        LedSetting now = waiting;
        waiting = null;
        if (now.brightness() != setting.brightness()) {
            light(now.brightness());
        }
        setting = now;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /**
     * Sets every strip of the story to a brightness: those along the two rows they run along, the box's bottom one over
     * the floor and its top one under the ceiling. Nothing where the box reaches a place not loaded.
     */
    private void light(int brightness) {
        BoundingBox box = box();
        if (box == null || level == null) {
            return;
        }
        Wedge wedge = wedge();
        for (int y : new int[]{box.minY(), box.maxY()}) {
            for (BlockPos at : BlockPos.betweenClosed(box.minX(), y, box.minZ(), box.maxX(), y, box.maxZ())) {
                lightStrip(at.immutable(), brightness, wedge);
            }
        }
    }

    /**
     * Sets every piece of oval window in the story to the darkness last asked for, looking through the box round its
     * strips and a little past it, out to her skin (see {@link OvalWindowPieceBlock#dimWithin}). Only when asked, a
     * story at a time, and only where it is loaded.
     */
    private void darkenWindows() {
        BoundingBox box = box();
        if (box == null || level == null) {
            return;
        }
        OvalWindowPieceBlock.dimWithin(level, new BoundingBox(box.minX() - WINDOW_REACH, box.minY(), box.minZ() - WINDOW_REACH,
            box.maxX() + WINDOW_REACH, box.maxY(), box.maxZ() + WINDOW_REACH), windows);
    }

    private void lightStrip(BlockPos at, int brightness, @Nullable Wedge wedge) {
        if (!level.isLoaded(at) || wedge != null && !wedge.contains(at)) {
            return;
        }
        BlockState state = level.getBlockState(at);
        if (state.getBlock() instanceof LedStripBlock && state.getValue(LedStripBlock.BRIGHTNESS) != brightness) {
            level.setBlock(at, state.setValue(LedStripBlock.BRIGHTNESS, brightness), Block.UPDATE_CLIENTS);
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
        if (from != null && to != null) {
            tag.put("From", NbtUtils.writeBlockPos(from));
            tag.put("To", NbtUtils.writeBlockPos(to));
            Turning.save(tag, getBlockState());
        }
        if (wedgeAxis != null) {
            tag.put("WedgeAxis", NbtUtils.writeBlockPos(wedgeAxis));
            tag.putFloat("WedgeFrom", wedgeFrom);
            tag.putFloat("WedgeTo", wedgeTo);
        }
        tag.put("Setting", setting.save());
        if (waiting != null) {
            tag.put("Waiting", waiting.save());
            tag.putLong("WaitingUntil", waitingUntil);
        }
        tag.putInt("Windows", windows);
        tag.putLong("WindowsAt", windowsAt);
    }

    /** Its box turned with the rocket, if it faces another way than it was saved facing (see {@link Turning}). */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Rotation turn = Turning.since(tag, getBlockState());
        from = Turning.turn(NbtUtils.readBlockPos(tag, "From").orElse(null), turn);
        to = Turning.turn(NbtUtils.readBlockPos(tag, "To").orElse(null), turn);
        wedgeAxis = Turning.turn(NbtUtils.readBlockPos(tag, "WedgeAxis").orElse(null), turn);
        wedgeFrom = wedgeAxis == null ? 0 : Wedge.turned(tag.getFloat("WedgeFrom"), turn);
        wedgeTo = wedgeAxis == null ? 0 : Wedge.turned(tag.getFloat("WedgeTo"), turn);
        setting = LedSetting.load(tag.getCompound("Setting"), LedSetting.PLAIN);
        waiting = tag.contains("Waiting", Tag.TAG_COMPOUND) ? LedSetting.load(tag.getCompound("Waiting"), setting) : null;
        waitingUntil = tag.getLong("WaitingUntil");
        windows = tag.contains("Windows", Tag.TAG_INT) ? tag.getInt("Windows") : WINDOWS_UNSET;
        windowsAt = tag.contains("WindowsAt", Tag.TAG_LONG) ? tag.getLong("WindowsAt") : NOT_WAITING;
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
