package dev.aerolarge.block;

import dev.aerolarge.Aboard;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.compat.SableFlight;
import dev.aerolarge.selfdestruct.SelfDestruct;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The self-destruct button's workings. It belongs to the airliner whose autopilot it is linked to (as an offset turned
 * with the button, see {@link Turning}; a button put up by hand links itself to the nearest autopilot within the
 * flight deck's reach), and works only while that airliner is assembled with the button among its own blocks, so it
 * never blows up another mod's flying body or a builder's airliner standing in the world. Pressed:
 * <ol>
 * <li>with its cover shut, the cover flips up with a clack;</li>
 * <li>with the cover up, the button arms: it lights up and flashes red, faster and faster, and everyone aboard hears
 * the alarm's siren and a beep every second, rising in pitch, and sees the seconds left counted down on their screen
 * (see {@link SelfDestruct#alarm}), for {@link #WINDOW} ticks, and when the time is up the airliner blows up (see
 * {@link SelfDestruct#detonate});</li>
 * <li>pressed again while armed, the airliner blows up at once.</li>
 * </ol>
 * Sneaking and using it snaps the cover shut and disarms it, so the airliner does not blow up. Not on an assembled
 * airliner, or with the self-destruct switched off in the server config, arming it only thuds.
 */
public class SelfDestructButtonBlockEntity extends BlockEntity {
    /** How long the countdown runs before the airliner blows up, in ticks: ten seconds, as long as the siren wails. */
    public static final int WINDOW = 10 * SharedConstants.TICKS_PER_SECOND;
    /** How often the countdown beeps, in ticks: every second, and twice a second over the last {@link #HURRY}. */
    private static final int BEEP = SharedConstants.TICKS_PER_SECOND;
    private static final int HURRY = 3 * SharedConstants.TICKS_PER_SECOND;
    /** How loud the cover's clack and the button's hums are, as a share of full volume. */
    private static final float CLICK_VOLUME = 0.8F;
    /** The pitch of the cover's clack: a small lid's, higher than the iron trapdoor it borrows. */
    private static final float COVER_PITCH = 1.5F;
    /** The pitch of the thud of a button that will not arm: low and dull. */
    private static final float THUD_PITCH = 0.5F;
    /**
     * The least time between two presses that each count, in ticks: longer than the four ticks the game repeats a held
     * use key at, so holding it down never gets past the first step, whoever holds it.
     */
    private static final int PRESS_GAP = 6;
    /** How long it stays armed before a press sets it off, in ticks: a quick double press never arms and fires it. */
    private static final int SETTLE = SharedConstants.TICKS_PER_SECOND;
    /** How long it waits for a parked airliner to assemble, once pressed to arm on one, in ticks: ten seconds. */
    private static final int ASSEMBLY_WAIT = 10 * SharedConstants.TICKS_PER_SECOND;
    /**
     * The longest it waits, once a parked airliner it was pressed to arm on has assembled, for whoever pressed it to be
     * aboard before arming, in ticks: three seconds. Sable counts someone standing in it as riding it only once they
     * have moved on its blocks, a few ticks after it assembles, and the siren and the warning that everyone will get a
     * parachute go only to those riding it; someone who has stepped off by then does not hold it up.
     */
    private static final int BOARDING_WAIT = 3 * SharedConstants.TICKS_PER_SECOND;

    /** Where the autopilot is, from this block; null until linked. */
    @Nullable
    private BlockPos autopilot;
    /**
     * The game time the arming runs out at, while armed: kept on the server only while it runs, so a world loaded again
     * finds the button disarmed, and sent to the clients for the button's flashing.
     */
    private long armedUntil;
    /** The game time it was last pressed at, whether the press counted or not; not saved. */
    private long lastPress = Long.MIN_VALUE / 2;
    /**
     * Who pressed it to arm on a parked airliner, which it then assembles, and when: it arms for them once among the
     * assembled airliner's blocks. Saved, so it goes along into those blocks as the airliner assembles.
     */
    @Nullable
    private UUID armFor;
    private long armAskedAt;
    /** The game time it first found the airliner it was asked to arm on assembled, or -1 before then; not saved. */
    private long assembledAt = -1;

    public SelfDestructButtonBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.SELF_DESTRUCT_BUTTON_ENTITY.get(), pos, state);
    }

    /** Links the button to its airliner's autopilot, as an offset from the button. */
    public void configure(BlockPos offset) {
        autopilot = offset;
        setChanged();
    }

    /** The game time the arming runs out at, as last sent; 0 while not armed. */
    public long armedUntil() {
        return armedUntil;
    }

    /**
     * The autopilot of the airliner the button belongs to, if it is there; on the server, an unlinked button links
     * itself to the nearest one first, if there is one.
     */
    @Nullable
    private AutopilotBlockEntity autopilot() {
        if (level == null) {
            return null;
        }
        if (autopilot == null) {
            AutopilotBlockEntity nearest = level.isClientSide ? null : AutopilotBlockEntity.nearest(level, worldPosition);
            if (nearest != null) {
                configure(nearest.getBlockPos().subtract(worldPosition));
            }
            return nearest;
        }
        return level.getBlockEntity(worldPosition.offset(autopilot)) instanceof AutopilotBlockEntity found ? found : null;
    }

    /**
     * Whether the button may blow its airliner up: the self-destruct is switched on, and the button and its autopilot
     * are both among the blocks of one assembled airliner built from its blueprint, so it is one of this mod's
     * airliners and nothing else, not another mod's flying body with an autopilot put aboard by hand.
     */
    public boolean mayBlowUp() {
        AutopilotBlockEntity linked = AerolargeConfig.SELF_DESTRUCT.get() ? autopilot() : null;
        return linked != null && linked.built() && SableFlight.sameAirliner(this, linked);
    }

    /**
     * Pressed by a player, on the server: sneaking, it shuts; else a step on, as the cover and the button stand. A
     * press too soon after the last ({@link #PRESS_GAP}), or one setting it off too soon after it armed
     * ({@link #SETTLE}), does nothing, so it takes three separate presses to blow the airliner up.
     */
    void press(ServerLevel level, Player player) {
        BlockState state = getBlockState();
        long now = level.getGameTime();
        boolean held = now - lastPress < PRESS_GAP;
        lastPress = now;
        if (player.isShiftKeyDown()) {
            shut(level, player);
        } else if (held) {
            return;
        } else if (!state.getValue(SelfDestructButtonBlock.OPEN)) {
            level.setBlock(worldPosition, state.setValue(SelfDestructButtonBlock.OPEN, true), Block.UPDATE_ALL);
            level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, CLICK_VOLUME, COVER_PITCH);
            player.displayClientMessage(Component.translatable("message.aerolarge.self_destruct.open"), true);
        } else if (state.getValue(SelfDestructButtonBlock.ARMED)) {
            if (now >= armedUntil - WINDOW + SETTLE) {
                fire(level, player);
            }
        } else {
            arm(level, player);
        }
    }

    /**
     * The cover up, the button pressed: armed, with the alarm through the airliner; on an airliner standing parked as
     * blocks, assembled first and armed once it is (see {@link #assembleFirst}); else a thud and why not. While the
     * airliner assembles for it, pressed again, it only waits.
     */
    private void arm(ServerLevel level, Player player) {
        if (armFor != null) {
            return;
        }
        if (mayBlowUp()) {
            armNow(level, player);
        } else if (!assembleFirst(level, player)) {
            thud(level, player);
        }
    }

    /**
     * On an airliner of this mod's standing parked as blocks, with the self-destruct switched on: its Physics Assembler
     * assembles it, as Take Off does, with a hum while it does, and the button arms for whoever pressed it once it is
     * among the assembled airliner's blocks (see {@link #armOnceAssembled}); whether that started.
     */
    private boolean assembleFirst(ServerLevel level, Player player) {
        AutopilotBlockEntity linked = AerolargeConfig.SELF_DESTRUCT.get() ? autopilot() : null;
        if (linked == null || !linked.built() || !(player instanceof ServerPlayer asker)) {
            return false;
        }
        // Noted before it assembles, so the button takes it along into the new airliner's blocks.
        armFor = player.getUUID();
        armAskedAt = level.getGameTime();
        assembledAt = -1;
        setChanged();
        if (!linked.startAssembly(asker)) {
            armFor = null;
            setChanged();
            return false;
        }
        level.playSound(null, worldPosition, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, CLICK_VOLUME, 1.0F);
        player.displayClientMessage(Component.translatable("message.aerolarge.self_destruct.assembling"), true);
        return true;
    }

    /**
     * Asked to arm on a parked airliner: arms for whoever asked once it has assembled and they are aboard it (or have
     * not come aboard within {@link #BOARDING_WAIT}), or gives up after a while.
     */
    private void armOnceAssembled(ServerLevel level) {
        ServerPlayer asker = level.getServer().getPlayerList().getPlayer(armFor);
        long now = level.getGameTime();
        if (mayBlowUp() && asker != null) {
            assembledAt = assembledAt < 0 ? now : assembledAt;
            if (Aboard.isAboard(asker, this) || now - assembledAt > BOARDING_WAIT) {
                armFor = null;
                setChanged();
                armNow(level, asker);
            }
        } else if (now - armAskedAt > ASSEMBLY_WAIT) {
            armFor = null;
            setChanged();
        }
    }

    /** Armed: the button lit and flashing, the alarm through the airliner, for whoever pressed it to hear why. */
    private void armNow(ServerLevel level, Player player) {
        armedUntil = level.getGameTime() + WINDOW;
        level.setBlock(worldPosition, getBlockState().setValue(SelfDestructButtonBlock.ARMED, true), Block.UPDATE_ALL);
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, CLICK_VOLUME, 1.0F);
        SelfDestruct.alarm(this, WINDOW);
        player.displayClientMessage(Component.translatable("message.aerolarge.self_destruct.armed", WINDOW / SharedConstants.TICKS_PER_SECOND), true);
    }

    /** Pressed again while armed: the airliner blows up once this tick is over; or, no longer one, a thud. */
    private void fire(ServerLevel level, Player player) {
        if (!mayBlowUp()) {
            disarm(level, false);
            thud(level, player);
            return;
        }
        SelfDestruct.detonate(level, this);
    }

    /** Sneaked at: the cover snaps shut, disarming the button if it was armed. */
    private void shut(ServerLevel level, Player player) {
        BlockState state = getBlockState();
        boolean armed = state.getValue(SelfDestructButtonBlock.ARMED);
        if (!state.getValue(SelfDestructButtonBlock.OPEN) && !armed) {
            return;
        }
        if (armed) {
            disarm(level, true);
        } else {
            level.setBlock(worldPosition, state.setValue(SelfDestructButtonBlock.OPEN, false), Block.UPDATE_ALL);
        }
        level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, CLICK_VOLUME, COVER_PITCH);
        player.displayClientMessage(Component.translatable(armed ? "message.aerolarge.self_destruct.cancelled" : "message.aerolarge.self_destruct.shut"), true);
    }

    /** Disarmed: the light goes out with a falling hum, the cover snapping shut too if asked, and the alarm stops. */
    private void disarm(ServerLevel level, boolean shut) {
        armedUntil = 0;
        BlockState state = getBlockState().setValue(SelfDestructButtonBlock.ARMED, false);
        level.setBlock(worldPosition, shut ? state.setValue(SelfDestructButtonBlock.OPEN, false) : state, Block.UPDATE_ALL);
        level.playSound(null, worldPosition, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, CLICK_VOLUME, 1.0F);
        SelfDestruct.silence(this);
    }

    /** A button that will not arm: a dull thud, and why, for whoever can read it. */
    private void thud(ServerLevel level, Player player) {
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.BLOCKS, 1.0F, THUD_PITCH);
        String why = AerolargeConfig.SELF_DESTRUCT.get() ? "message.aerolarge.self_destruct.not_assembled" : "message.aerolarge.self_destruct.off";
        player.displayClientMessage(Component.translatable(why), true);
    }

    /**
     * Each tick, on the server: asked to arm on a parked airliner, whether it has assembled yet; while armed, the
     * countdown's beeps, until the time is up and the airliner blows up.
     */
    void tick() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (armFor != null) {
            armOnceAssembled(server);
        }
        if (!getBlockState().getValue(SelfDestructButtonBlock.ARMED)) {
            return;
        }
        long left = armedUntil - server.getGameTime();
        if (left <= 0) {
            timeUp(server);
            return;
        }
        if (left < WINDOW && left % (left <= HURRY ? BEEP / 2 : BEEP) == 0) {
            SelfDestruct.countdown(this, (int) left, 1 - (float) left / WINDOW);
        }
    }

    /**
     * The countdown run out: the airliner blows up once this tick is over, as when the button is pressed again; or,
     * no longer one it may blow up, the button disarms.
     */
    private void timeUp(ServerLevel level) {
        if (mayBlowUp()) {
            SelfDestruct.detonate(level, this);
        } else {
            disarm(level, false);
        }
    }

    /** The button taken away: if it was armed, the alarm stops for everyone aboard. */
    void taken() {
        if (getBlockState().getValue(SelfDestructButtonBlock.ARMED) && level instanceof ServerLevel) {
            SelfDestruct.silence(this);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (autopilot != null) {
            tag.put("Autopilot", NbtUtils.writeBlockPos(autopilot));
        }
        if (armFor != null) {
            tag.putUUID("ArmFor", armFor);
            tag.putLong("ArmAskedAt", armAskedAt);
        }
        Turning.save(tag, getBlockState());
    }

    /**
     * The link to the autopilot, and an arming asked on a parked airliner, from the save; the arming's end only ever
     * from the server's update.
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Autopilot")) {
            autopilot = Turning.turn(NbtUtils.readBlockPos(tag, "Autopilot").orElse(null), Turning.since(tag, getBlockState()));
        }
        armedUntil = tag.getLong("ArmedUntil");
        armFor = tag.hasUUID("ArmFor") ? tag.getUUID("ArmFor") : null;
        armAskedAt = tag.getLong("ArmAskedAt");
    }

    /** The clients only need when the arming runs out, for the button's flashing. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("ArmedUntil", armedUntil);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
