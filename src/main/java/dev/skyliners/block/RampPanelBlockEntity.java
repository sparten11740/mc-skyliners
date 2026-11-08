package dev.skyliners.block;

import dev.skyliners.Aboard;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import dev.skyliners.compat.SableFlight;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The freighter's ramp panel's block entity, which keeps her boarding ramp (see {@link BoardingRamp}) and works it:
 * the ramp comes down of its own accord once she first stands built, and again once she is down on her legs after
 * flying with her autopilot off, where the ground lies under its foot; it folds away as soon as her autopilot is set
 * to fly or she moves, and her take-off waits until it is all away (see {@link #holdsTakeoff}), as it waits, with a
 * chime, until nobody stands on it; while someone holds it so, everyone aboard is told once and hears the chime too,
 * wherever they are aboard. The panel's one button works it by hand while she stands with her autopilot off, showing
 * what it does: down while the ramp is up, up while it is down. Pressed while she comes in to land, it promises the
 * ramp, which comes down by itself as soon as she has settled; flying otherwise, the lamp only turns red with a low
 * note. Its lamp follows how the ramp stands (see {@link RampPanelBlock.Status}).
 */
public class RampPanelBlockEntity extends BlockEntity implements BoardingRamp.Keeper {
    private static final Loaded<RampPanelBlockEntity> LOADED = new Loaded<>(RampPanelBlockEntity.class);
    /** How often the ramp looks whether she has set off or come to rest, in ticks. */
    private static final int WATCH_TICKS = 10;
    /** Faster than this, in blocks a second, she is moving: the ramp folds away, and will not come down. */
    private static final double MOVING = 1.0;
    /** How many looks in a row she has to be still on the ground, after flying, before the ramp comes down. */
    private static final int STILL_LOOKS = 4;
    /**
     * How often everyone aboard hears the waiting chime while her take-off waits for someone to step off, in ticks;
     * and how near her ramp they hear its own chime instead, in blocks, as far as a block's sound carries.
     */
    private static final int WAITING_CHIME = 40;
    private static final double RAMP_HEARD = 16;
    /** How long the lamp stays red after the ramp would not move, in ticks. */
    private static final int REFUSED_TICKS = 40;
    /** How loud the click is that says the ramp will come down once she has landed. */
    private static final float PROMISE_VOLUME = 0.5F;

    /** Her ramp; none in a panel placed by hand. */
    @Nullable
    private BoardingRamp ramp;
    /** Whether she has flown since the ramp was last worked, so it comes down by itself once she is down. */
    private boolean flown;
    private int stillLooks;
    /** Whether her take-off waits for someone on the ramp, and everyone aboard has been told. */
    private boolean holding;
    /** The game tick until which the lamp stays red, the ramp having refused to move; not saved. */
    private long refusedUntil;
    /** Whether the ramp has been asked down while she lands, its lamp blinking until it comes; not saved. */
    private boolean promised;

    public RampPanelBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.RAMP_PANEL_ENTITY.get(), pos, state);
    }

    /**
     * Hands the panel the ramp it keeps, as she is built with it up: where it lies from the panel, and its hatch, each
     * block of it from the panel. It comes down of its own accord once she is found standing on the ground.
     */
    public void rigRamp(BoardingRamp.Frame frame, List<AirstairControllerBlockEntity.Step> hatch) {
        ramp = new BoardingRamp(this, frame, new Hatch(hatch));
        flown = true;
        setChanged();
    }

    /** The ramp it keeps, for its renderer to draw; none in a panel placed by hand. */
    @Nullable
    public BoardingRamp ramp() {
        return ramp;
    }

    /** Whether any ramp's deck, laid, takes in a block (see {@link RampDeckBlock}). */
    static boolean anyHolds(ServerLevel level, BlockPos pos) {
        for (RampPanelBlockEntity panel : LOADED.in(level)) {
            if (panel.ramp != null && panel.ramp.holds(level, pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether her ramp holds her take-off before her thrusters spool up (see {@code FreighterFlight#holdTakeoffWhile}):
     * any ramp in her autopilot's frame not yet all away.
     */
    public static boolean holdsTakeoff(ServerLevel level, BlockPos autopilotAt) {
        BoundingBox frame = level.getBlockEntity(autopilotAt) instanceof AutopilotBlockEntity autopilot ? autopilot.frame() : null;
        if (frame == null) {
            return false;
        }
        for (RampPanelBlockEntity panel : LOADED.in(level)) {
            if (panel.ramp != null && frame.isInside(panel.getBlockPos()) && !panel.ramp.stowed()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Her ramp call button pressed, on the server: her ramp comes down if it is up, or folds away if it is down, as its
     * panel's buttons would work it; only a low note from the button where no freighter frames it or she has no ramp.
     */
    static void call(ServerLevel level, BlockPos button) {
        AutopilotBlockEntity freighter = AutopilotBlockEntity.framedBy(level, button, Craft.FREIGHTER);
        BoundingBox frame = freighter == null ? null : freighter.frame();
        RampPanelBlockEntity panel = frame == null ? null : LOADED.in(level).stream()
            .filter(keeper -> keeper.ramp != null && frame.isInside(keeper.getBlockPos())).findFirst().orElse(null);
        if (panel == null) {
            level.playSound(null, button, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, BoardingAirstairs.REFUSED_VOLUME,
                BoardingAirstairs.REFUSED_PITCH);
            return;
        }
        panel.pressButton(level);
    }

    /** Its button pressed, on the server: it does what it shows, letting the ramp down while up, bringing it up while down. */
    void pressButton(ServerLevel level) {
        press(level, RampPanelBlock.ACTION.action(ramp != null && ramp.down()));
    }

    /**
     * A button pressed, on the server: the ramp goes down or up while she stands, her autopilot off. Asked down while
     * she is coming in to land, it is promised instead: a click, the lamp blinking amber, and down by itself as soon as
     * she has settled on her legs.
     */
    void press(ServerLevel level, RampPanelBlock.Button button) {
        if (ramp != null && button == RampPanelBlock.Button.LOWER && landing(level)) {
            flown = true;
            promised = true;
            level.playSound(null, worldPosition, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, PROMISE_VOLUME, 1.0F);
            return;
        }
        if (ramp == null || flying(level)) {
            refuse(level);
            return;
        }
        flown = false;
        promised = false;
        if (!ramp.go(level, button == RampPanelBlock.Button.LOWER)) {
            refusedUntil = level.getGameTime() + REFUSED_TICKS;
        }
        sync();
    }

    /** The low note of a panel that does nothing now, its lamp red a moment. */
    private void refuse(ServerLevel level) {
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, BoardingAirstairs.REFUSED_VOLUME,
            BoardingAirstairs.REFUSED_PITCH);
        refusedUntil = level.getGameTime() + REFUSED_TICKS;
    }

    /** Whether she is coming down to land, her autopilot bringing her down onto her legs. */
    private boolean landing(ServerLevel level) {
        AutopilotBlockEntity freighter = AutopilotBlockEntity.framedBy(level, worldPosition, Craft.FREIGHTER);
        return freighter != null && (freighter.phase() == AutopilotBlockEntity.Phase.LAND || freighter.phase() == AutopilotBlockEntity.Phase.DESCENT);
    }

    /** Whether nothing is being said in her cockpit, nor waits to be (see {@link AutopilotBlockEntity#quiet}). */
    private boolean cockpitQuiet(ServerLevel level) {
        AutopilotBlockEntity freighter = AutopilotBlockEntity.framedBy(level, worldPosition, Craft.FREIGHTER);
        return freighter == null || freighter.quiet();
    }

    /**
     * Whether she flies: moving, or her autopilot set to fly, from the moment it is engaged until it has brought her
     * down and switched itself off.
     */
    private boolean flying(ServerLevel level) {
        if (SableFlight.velocity(level, Vec3.atCenterOf(worldPosition)).length() > MOVING) {
            return true;
        }
        AutopilotBlockEntity freighter = AutopilotBlockEntity.framedBy(level, worldPosition, Craft.FREIGHTER);
        return freighter != null && freighter.phase() != AutopilotBlockEntity.Phase.OFF;
    }

    /** Once a tick, on the server: the ramp a tick further, now and then a look at how she is doing, and its lamp. */
    void serverTick(ServerLevel level) {
        if (ramp == null) {
            return;
        }
        if (level.getGameTime() % WATCH_TICKS == 0) {
            watch(level);
        }
        ramp.tick(level);
        tellHolding(level);
        showStatus(level);
    }

    /**
     * Folds the ramp away as she sets off, by her autopilot or otherwise; brings it down again once she is still on the
     * ground after flying, her autopilot off, with the ground under its foot, and her computer done saying she is down,
     * so its own line as it unlatches never talks over her. Her take-off called off while someone held it from folding,
     * it stays down.
     */
    private void watch(ServerLevel level) {
        if (promised && (!ramp.stowed() || flying(level) && !landing(level))) {
            promised = false;
        }
        if (flying(level)) {
            stillLooks = 0;
            flown = true;
            if (ramp.down()) {
                ramp.go(level, false);
                sync();
            }
            return;
        }
        if (flown && ramp.held() && !ramp.down()) {
            flown = false;
            ramp.go(level, true);
            sync();
            return;
        }
        stillLooks = flown && ramp.stowed() ? stillLooks + 1 : 0;
        if (stillLooks < STILL_LOOKS || !cockpitQuiet(level)) {
            return;
        }
        promised = false;
        if (ramp.groundUnderFoot(level)) {
            flown = false;
            ramp.go(level, true);
            sync();
        }
    }

    /**
     * Everyone aboard told once that her take-off waits for someone on the ramp to step off, as it begins to; and while
     * it waits, everyone aboard out of earshot of the ramp's own chime hears it where they are, in her cockpit or at
     * her gunner's seat, her console meanwhile showing her ramp (see {@code FreighterFlight#heldBy}).
     */
    private void tellHolding(ServerLevel level) {
        AutopilotBlockEntity freighter = ramp.held() ? AutopilotBlockEntity.framedBy(level, worldPosition, Craft.FREIGHTER) : null;
        boolean now = freighter != null && freighter.phase() == AutopilotBlockEntity.Phase.TAKEOFF;
        if (now && !holding) {
            for (ServerPlayer player : Aboard.players(this)) {
                player.displayClientMessage(Component.translatable("message.skyliners.freighter.takeoff_ramp"), true);
            }
        }
        if (now && level.getGameTime() % WAITING_CHIME == 0) {
            chimeAboard(level);
        }
        holding = now;
    }

    /** The ramp's waiting chime, heard by everyone aboard too far off to hear the ramp's own, each where they are. */
    private void chimeAboard(ServerLevel level) {
        Vec3 here = SableFlight.toWorld(level, Vec3.atCenterOf(worldPosition));
        for (ServerPlayer player : Aboard.players(this)) {
            if (player.position().distanceTo(here) > RAMP_HEARD) {
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, BoardingAirstairs.WAITING_VOLUME,
                    BoardingAirstairs.WAITING_PITCH);
            }
        }
    }

    /** Sets the panel's lamp to how the ramp stands, when that changes. */
    private void showStatus(ServerLevel level) {
        RampPanelBlock.Status status = status(level);
        BlockState state = getBlockState();
        if (state.hasProperty(RampPanelBlock.STATUS) && state.getValue(RampPanelBlock.STATUS) != status) {
            level.setBlock(worldPosition, state.setValue(RampPanelBlock.STATUS, status), Block.UPDATE_CLIENTS);
        }
    }

    private RampPanelBlock.Status status(ServerLevel level) {
        if (level.getGameTime() < refusedUntil) {
            return RampPanelBlock.Status.REFUSED;
        }
        if (ramp.stowed()) {
            return promised ? RampPanelBlock.Status.BUSY : RampPanelBlock.Status.READY;
        }
        return ramp.landed() && ramp.down() && !ramp.held() ? RampPanelBlock.Status.DOWN : RampPanelBlock.Status.BUSY;
    }

    /** Once a tick, on a client: the ramp's clock, so it is drawn on its way between the server's word. */
    void clientTick() {
        if (ramp != null) {
            ramp.clientTick();
        }
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
        if (ramp != null) {
            CompoundTag saved = new CompoundTag();
            ramp.save(saved);
            tag.put("Ramp", saved);
        }
        Turning.save(tag, getBlockState());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        flown = tag.getBoolean("Flown");
        Rotation turn = Turning.since(tag, getBlockState());
        ramp = tag.contains("Ramp") ? BoardingRamp.load(this, ramp, tag.getCompound("Ramp"), turn, registries) : null;
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
