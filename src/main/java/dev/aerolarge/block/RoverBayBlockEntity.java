package dev.aerolarge.block;

import dev.aerolarge.Aboard;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.Craft;
import dev.aerolarge.compat.SableFlight;
import dev.aerolarge.entity.Rover;
import dev.aerolarge.item.RoverRemoteItem;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The rocket's rover bay, kept by the middle of its door's sill (see {@link RoverHatchBlock}): it holds its rover (see
 * {@link Rover}) every tick, telling it where the bay is among her blocks and how far the door is open, and works the
 * door and its winch for it. The door opens as the rover leaves its cradle and shuts once it is back in it, its motor
 * running and its latch clunking; the winch's ratchet is heard while the rover is on the hook. Its panel (see
 * {@link RoverPanelBlock}) sends the rover down, calls it home and hands out remotes paired with it, and its lamp shows
 * how the bay stands.
 *
 * <p>The door only opens while she stands still on her fins with her autopilot off. Once her autopilot is on, the rover
 * is called home, its driver unable to call it off, and her countdown waits for it to be back in its cradle with the
 * door shut (see {@link #holdsCountdown}), everyone aboard told so once and her cockpit hearing the rover's beep while
 * it waits. A rover standing still where the land round it is not ticking, so it cannot drive home, is put straight
 * back in its cradle after a while. A bay makes its rover on its first tick, and makes a new one only when its own has
 * not been heard of for a long while and her countdown or its panel wants it.
 */
public class RoverBayBlockEntity extends BlockEntity {
    /** The bays loaded, so a panel and her autopilot find theirs. */
    public static final Loaded<RoverBayBlockEntity> LOADED = new Loaded<>(RoverBayBlockEntity.class);
    /** How long the door takes to open or shut, in ticks. */
    public static final int DOOR_TICKS = 40;
    /**
     * How long its rover goes unheard of, in ticks, before a new one may be made, or, found but standing still where
     * the land round it is not ticking, before it is put straight back in its cradle: half a minute.
     */
    private static final int ROVER_LOST = 30 * 20;
    /** How often the cockpit hears the rover's beep while her countdown waits for it, in ticks. */
    private static final int WAITING_BEEP = 40;
    /** How long the panel's lamp shows red after the door would not open, in ticks. */
    private static final int REFUSED_TICKS = 40;
    /** How often the winch's ratchet is heard while it works, in ticks, and how loud; and the door's motor, latch. */
    private static final int RATCHET_TICKS = 5;
    private static final float RATCHET_VOLUME = 0.6F;
    private static final float MOTOR_VOLUME = 0.8F;
    private static final float LATCH_VOLUME = 0.9F;
    /**
     * The pitch of the low note the panel gives when the door will not open, or nothing answers; how loud and how high
     * its buttons click; and how loud and how high a remote handed out is heard.
     */
    private static final float REFUSED_PITCH = 0.5F;
    private static final float CLICK_VOLUME = 0.5F;
    private static final float CLICK_PITCH = 1.6F;
    private static final float HANDED_VOLUME = 0.5F;
    private static final float HANDED_PITCH = 1.2F;
    /**
     * How fast she may move, in blocks a second, and how nearly upright she must stand, as the cosine of her lean, to
     * count as standing still on her fins.
     */
    private static final double STILL = 0.3;
    private static final double UPRIGHT = 0.99;
    /** How loud a spoken answer's note is, and its pitch: setting off, coming home, nothing to do and wait. */
    private static final float ANSWER_VOLUME = 0.6F;
    private static final float GOING_PITCH = 1.5F;
    private static final float HOME_PITCH = 0.9F;
    private static final float NOTHING_PITCH = 0.7F;
    private static final float WAIT_PITCH = 1.0F;

    /** Where the boom's trolley waits while the rover is not on its hook, so the client draws it there. */
    public enum Trolley {
        OVER_PLATFORM, AT_TIP, WITH_ROVER;

        static Trolley named(String name) {
            for (Trolley trolley : values()) {
                if (trolley.name().equals(name)) {
                    return trolley;
                }
            }
            return OVER_PLATFORM;
        }
    }

    @Nullable
    private UUID rover;
    /** Where the door is going, open or shut, and how far through its swing it is, in ticks, and was a tick ago. */
    private boolean open;
    private int ticks;
    private int lastTicks;
    /** Where the bay's panel is, as an offset from it; null without one. */
    @Nullable
    private BlockPos panel;
    /** How long its rover has gone unheard of, or found but not ticking, in ticks. */
    private int missing;
    private Trolley trolley = Trolley.OVER_PLATFORM;
    /** The game time the panel's lamp shows red until. */
    private long refusedUntil;
    /** Whether her countdown waits for the rover, and everyone aboard has been told. */
    private boolean holding;

    public RoverBayBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.ROVER_BAY_ENTITY.get(), pos, state);
    }

    /** Wired when the rocket is built: where its panel is, as an offset from the bay turned as she was placed. */
    public void wirePanel(BlockPos offset) {
        panel = offset;
        setChanged();
    }

    /** Its rover's id, if it has made one. */
    @Nullable
    public UUID roverId() {
        return rover;
    }

    /** The way the door faces out of her. */
    public Direction facing() {
        return getBlockState().getValue(RoverHatchBlock.FACING);
    }

    /** How far through its swing the door is, 0 shut to 1 open, between the last tick and this one. */
    public float progress(float partialTick) {
        return Mth.lerp(partialTick, lastTicks, ticks) / DOOR_TICKS;
    }

    /** Where the boom's trolley waits, for the client to draw it. */
    public Trolley trolley() {
        return trolley;
    }

    /** The bay a panel at a place is wired to, on either side; null for none. */
    @Nullable
    public static RoverBayBlockEntity wiredTo(Level level, BlockPos panel) {
        for (RoverBayBlockEntity bay : LOADED.in(level)) {
            if (bay.panel != null && bay.worldPosition.offset(bay.panel).equals(panel)) {
                return bay;
            }
        }
        return null;
    }

    /**
     * Whether a rover bay in the frame of the rocket whose autopilot stands at a place holds her countdown: its rover
     * not back in its cradle with the door shut. For her pilot, which waits before its countdown's first second.
     */
    public static boolean holdsCountdown(ServerLevel level, BlockPos autopilotAt) {
        BoundingBox frame = level.getBlockEntity(autopilotAt) instanceof AutopilotBlockEntity autopilot ? autopilot.frame() : null;
        if (frame == null) {
            return false;
        }
        for (RoverBayBlockEntity bay : LOADED.in(level)) {
            if (frame.isInside(bay.worldPosition) && !bay.ready(Rover.find(level, bay.rover))) {
                return true;
            }
        }
        return false;
    }

    /** Whether its rover is back in its cradle with the door shut. */
    private boolean ready(@Nullable Rover found) {
        return found != null && found.state() == Rover.State.STOWED && ticks == 0 && !open;
    }

    void serverTick(ServerLevel level) {
        Direction out = facing();
        AutopilotBlockEntity autopilot = AutopilotBlockEntity.framedBy(level, worldPosition, Craft.ROCKET);
        boolean counting = autopilot != null && autopilot.phase() != AutopilotBlockEntity.Phase.OFF;
        Rover found = Rover.find(level, rover);
        boolean ticking = found != null && found.ticking(level);
        missing = ticking ? 0 : missing + 1;
        if (found == null && (rover == null || missing > ROVER_LOST && counting)) {
            found = make(level, out);
        }
        if (found != null) {
            found.hold(level, worldPosition, out, (double) ticks / DOOR_TICKS);
            recall(found, counting, ticking);
        }
        tellHolding(level, autopilot, counting && !ready(found));
        moveDoor(level, found == null ? open : found.state() != Rover.State.STOWED);
        windWinch(level, found);
        showTrolley(found);
        showStatus(level, found);
    }

    /**
     * Her autopilot on, the rover is called home, its driver unable to call it off; standing still where the land round
     * it is not ticking, so it cannot drive home, it is put straight back in its cradle once it has stood so long. Her
     * autopilot off, its driver may take it over again.
     */
    private void recall(Rover found, boolean counting, boolean ticking) {
        if (!counting) {
            found.release();
        } else if (ticking) {
            found.comeHome(true);
        } else if (missing > ROVER_LOST) {
            found.stowNow();
        }
    }

    void clientTick() {
        lastTicks = ticks;
        ticks = Mth.clamp(ticks + (open ? 1 : -1), 0, DOOR_TICKS);
    }

    /** Whether her autopilot is on: counting down, or flying. */
    private boolean countingDown(ServerLevel level) {
        AutopilotBlockEntity autopilot = AutopilotBlockEntity.framedBy(level, worldPosition, Craft.ROCKET);
        return autopilot != null && autopilot.phase() != AutopilotBlockEntity.Phase.OFF;
    }

    /** A new rover, in its cradle. */
    private Rover make(ServerLevel level, Direction out) {
        Rover made = Rover.stowedIn(level, worldPosition, out);
        level.addFreshEntity(made);
        rover = made.getUUID();
        missing = 0;
        setChanged();
        return made;
    }

    /**
     * Everyone aboard told once that her countdown waits for the rover to come home, as it begins to; and while it
     * waits, the rover's beep heard in her cockpit every so often, for anyone who cannot read it.
     */
    private void tellHolding(ServerLevel level, @Nullable AutopilotBlockEntity autopilot, boolean now) {
        if (now && !holding) {
            for (ServerPlayer player : Aboard.players(this)) {
                player.displayClientMessage(Component.translatable("message.aerolarge.rover.countdown_holding"), true);
            }
        }
        if (now && autopilot != null && level.getGameTime() % WAITING_BEEP == 0) {
            level.playSound(null, autopilot.getBlockPos(), AerolargeRegistry.ROVER_START.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        holding = now;
    }

    /**
     * The door one tick on toward open or shut: its motor heard as it starts, its latch as it gets there; its doorway
     * clear from the moment it starts to open until it is shut again.
     */
    private void moveDoor(ServerLevel level, boolean wanted) {
        lastTicks = ticks;
        if (wanted != open) {
            open = wanted;
            if (open) {
                clearDoorway(level, true);
            }
            level.playSound(null, worldPosition, AerolargeRegistry.AIRSTAIR_MOTOR.get(), SoundSource.BLOCKS, MOTOR_VOLUME, 1.0F);
            sync();
        }
        int next = Mth.clamp(ticks + (open ? 1 : -1), 0, DOOR_TICKS);
        if (next == ticks) {
            return;
        }
        ticks = next;
        setChanged();
        if (ticks == 0 || ticks == DOOR_TICKS) {
            level.playSound(null, worldPosition, AerolargeRegistry.AIRSTAIR_LATCH.get(), SoundSource.BLOCKS, LATCH_VOLUME, 1.0F);
            if (ticks == 0) {
                clearDoorway(level, false);
            }
            sync();
        }
    }

    /** Every part of the door marked open, its doorway clear but for the light curtain, or shut. */
    private void clearDoorway(ServerLevel level, boolean clear) {
        BlockState own = getBlockState();
        for (int part = 0; part < RoverHatchBlock.COLUMNS * RoverHatchBlock.ROWS; part++) {
            BlockPos at = RoverHatchBlock.part(own, worldPosition, part % RoverHatchBlock.COLUMNS, part / RoverHatchBlock.COLUMNS);
            BlockState state = level.getBlockState(at);
            if (state.getBlock() instanceof RoverHatchBlock && state.getValue(RoverHatchBlock.OPEN) != clear) {
                level.setBlock(at, state.setValue(RoverHatchBlock.OPEN, clear), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** The winch's ratchet, heard every so often while the rover is on its hook. */
    private void windWinch(ServerLevel level, @Nullable Rover found) {
        if (found != null && found.onTheHook() && level.getGameTime() % RATCHET_TICKS == 0) {
            level.playSound(null, worldPosition, AerolargeRegistry.CRANE_RATCHET.get(), SoundSource.BLOCKS, RATCHET_VOLUME, 1.0F);
        }
    }

    /**
     * Where the trolley waits, told to the clients as it changes: with the rover while it is on the hook, at the tip
     * while it is out, over the platform otherwise.
     */
    private void showTrolley(@Nullable Rover found) {
        if (found == null) {
            return;
        }
        Trolley now = found.onTheHook() ? Trolley.WITH_ROVER : found.state() == Rover.State.OUT ? Trolley.AT_TIP : Trolley.OVER_PLATFORM;
        if (now != trolley) {
            trolley = now;
            sync();
        }
    }

    /** The panel's lamp set to how the bay stands. */
    private void showStatus(ServerLevel level, @Nullable Rover found) {
        if (panel == null) {
            return;
        }
        BlockPos at = worldPosition.offset(panel);
        BlockState state = level.getBlockState(at);
        if (!state.is(AerolargeRegistry.ROVER_PANEL.get())) {
            return;
        }
        RoverPanelBlock.Status status;
        if (level.getGameTime() < refusedUntil) {
            status = RoverPanelBlock.Status.REFUSED;
        } else if (ready(found)) {
            status = RoverPanelBlock.Status.READY;
        } else if (found != null && found.state() == Rover.State.OUT) {
            status = RoverPanelBlock.Status.OUT;
        } else {
            status = RoverPanelBlock.Status.BUSY;
        }
        if (state.getValue(RoverPanelBlock.STATUS) != status) {
            level.setBlock(at, state.setValue(RoverPanelBlock.STATUS, status), Block.UPDATE_CLIENTS);
        }
    }

    /** A button of its panel pressed, on the server. */
    void press(ServerPlayer player, RoverPanelBlock.Button button) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        server.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, CLICK_VOLUME,
            CLICK_PITCH);
        switch (button) {
            case DEPLOY -> deploy(server, player);
            case HOME -> callHome(server, player);
            case REMOTE -> handRemote(server, player);
        }
    }

    /**
     * Sends the rover down, if it is in its cradle and she stands still on her fins with her autopilot off; says what
     * happens, or why not.
     */
    public void deploy(ServerLevel level, ServerPlayer player) {
        Rover found = answering(level, player);
        if (found == null) {
            return;
        }
        if (found.state() != Rover.State.STOWED) {
            tell(player, found.state() == Rover.State.OUT ? "already_out" : "busy");
            return;
        }
        if (!standing(level)) {
            refuse(level, player, "not_still");
            return;
        }
        found.deploy();
        tell(player, "deploying");
    }

    /** Calls the rover home, if it is out; says what happens. */
    private void callHome(ServerLevel level, ServerPlayer player) {
        Rover found = answering(level, player);
        if (found == null) {
            return;
        }
        switch (found.state()) {
            case OUT -> {
                found.comeHome(false);
                tell(player, "coming_home");
            }
            case STOWED -> tell(player, "already_home");
            default -> tell(player, "busy");
        }
    }

    /** Hands whoever pressed it a remote paired with the rover, unless they carry one already. */
    private void handRemote(ServerLevel level, ServerPlayer player) {
        Rover found = answering(level, player);
        if (found == null) {
            return;
        }
        if (RoverRemoteItem.carries(player, found.getUUID())) {
            tell(player, "has_remote");
            return;
        }
        player.getInventory().placeItemBackInInventory(RoverRemoteItem.pairedWith(found));
        tell(player, "remote_given");
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, HANDED_VOLUME, HANDED_PITCH);
    }

    /** A remote used on its panel, paired with its rover. */
    void pair(ItemStack remote, ServerPlayer player) {
        if (level instanceof ServerLevel server) {
            Rover found = answering(server, player);
            if (found != null) {
                RoverRemoteItem.pair(remote, found, player);
            }
        }
    }

    /**
     * Its rover, as it answers its panel: made anew if its own has gone unheard of long; null, with a low note and a
     * word to whoever pressed, if it does not answer.
     */
    @Nullable
    private Rover answering(ServerLevel level, ServerPlayer player) {
        Rover found = Rover.find(level, rover);
        if (found == null && (rover == null || missing > ROVER_LOST)) {
            found = make(level, facing());
        }
        if (found == null) {
            refuse(level, player, "no_answer");
        }
        return found;
    }

    /** Whether she stands still on her fins with her autopilot off: standing as built, or assembled and at rest. */
    private boolean standing(ServerLevel level) {
        if (countingDown(level)) {
            return false;
        }
        SableFlight.State state = SableFlight.read(this, facing());
        return state == null || state.velocity().length() < STILL && state.up().y > UPRIGHT;
    }

    private void refuse(ServerLevel level, ServerPlayer player, String why) {
        refusedUntil = level.getGameTime() + REFUSED_TICKS;
        tell(player, why);
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0F, REFUSED_PITCH);
    }

    private static void tell(ServerPlayer player, String what) {
        answer(player, what);
    }

    /**
     * An answer from a rover's bay or its remote, in words over the hotbar and in a note of its own, so that a player
     * who cannot read hears it too: a bright chime as it sets off down or a remote is handed over, a lower one as it
     * comes home, a soft low pling for nothing to do, and a deep note for wait; a refusal has its own bass.
     */
    public static void answer(ServerPlayer player, String what) {
        player.displayClientMessage(Component.translatable("message.aerolarge.rover." + what), true);
        switch (what) {
            case "deploying", "remote_given" -> note(player, SoundEvents.NOTE_BLOCK_CHIME, GOING_PITCH);
            case "coming_home" -> note(player, SoundEvents.NOTE_BLOCK_CHIME, HOME_PITCH);
            case "already_out", "already_home", "has_remote", "unpaired" -> note(player, SoundEvents.NOTE_BLOCK_PLING, NOTHING_PITCH);
            case "busy" -> note(player, SoundEvents.NOTE_BLOCK_BASS, WAIT_PITCH);
            default -> {
            }
        }
    }

    private static void note(ServerPlayer player, Holder<SoundEvent> sound, float pitch) {
        player.playNotifySound(sound.value(), SoundSource.BLOCKS, ANSWER_VOLUME, pitch);
    }

    private void sync() {
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
        if (rover != null) {
            tag.putUUID("Rover", rover);
        }
        tag.putBoolean("Open", open);
        tag.putInt("Ticks", ticks);
        tag.putString("Trolley", trolley.name());
        if (panel != null) {
            Turning.save(tag, getBlockState());
            tag.putLong("Panel", panel.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rover = tag.hasUUID("Rover") ? tag.getUUID("Rover") : null;
        open = tag.getBoolean("Open");
        int count = ToldTicks.count(level, ticks, Mth.clamp(tag.getInt("Ticks"), 0, DOOR_TICKS), open);
        lastTicks = count == ticks ? lastTicks : count;
        ticks = count;
        trolley = Trolley.named(tag.getString("Trolley"));
        Rotation turn = Turning.since(tag, getBlockState());
        panel = tag.contains("Panel") ? Turning.turn(BlockPos.of(tag.getLong("Panel")), turn) : null;
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
