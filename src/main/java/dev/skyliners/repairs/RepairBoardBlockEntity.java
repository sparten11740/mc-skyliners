package dev.skyliners.repairs;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import dev.skyliners.autopilot.VoiceLength;
import dev.skyliners.block.Loaded;
import dev.skyliners.compat.SableFlight;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The repair board's block entity (see {@link RepairBoardBlock}), on part 0, which runs her running repairs: how many
 * faults have been fixed near it, saved, and when the last star lit, for the client's {@code RepairBoardRenderer} to
 * pop it on; and how many fault spots near it are at fault now, sent to clients for its warning lamp.
 *
 * <p>While the ship it is aboard flies along under her autopilot, it counts her flight, and every few minutes of it
 * sets off one of the fault spots round it (see {@link FaultPanelBlock}) at random, never more than {@link #MOST_OPEN}
 * at a time and never on the ground. Each fault fixed near it lights its next star with a chime a step higher, five a
 * fanfare and ten a bigger one, and aboard the freighter her computer has a short dry line to say about it; a fault
 * left a good long while gets a rare grumble from her. With the server config's {@code freighter.repairs} off it sets
 * nothing off and lights nothing.
 */
public class RepairBoardBlockEntity extends BlockEntity {
    private static final Loaded<RepairBoardBlockEntity> LOADED = new Loaded<>(RepairBoardBlockEntity.class);
    /** How far from the board the fault spots it looks after may be, in blocks: across the whole of her deck. */
    public static final double REACH = 48;
    /** How many stars the board has: two rows of five. */
    public static final int STARS = 10;
    /** How many stars make a row, each row filled playing a fanfare. */
    public static final int ROW = 5;
    /** No star lit yet. */
    public static final long NEVER = Long.MIN_VALUE;
    /** How often the board looks over its fault spots and her flight, in ticks. */
    private static final int LOOK_EVERY = 20;
    /**
     * How long she flies between one fault and the next, in ticks, at least and at most, picked at random between:
     * from two to five minutes, so a fault is something to find, not a chore.
     */
    private static final int FAULT_EVERY_LEAST = 2 * 60 * 20;
    private static final int FAULT_EVERY_MOST = 5 * 60 * 20;
    /** How many faults may wait to be fixed at once: no more go off until one is fixed. */
    private static final int MOST_OPEN = 2;
    /** The autopilot's phases in which she is flying along, high off the ground, when faults may go off. */
    private static final Set<AutopilotBlockEntity.Phase> FLYING = EnumSet.of(AutopilotBlockEntity.Phase.CLIMB,
        AutopilotBlockEntity.Phase.CRUISE, AutopilotBlockEntity.Phase.DESCENT);
    /**
     * Her computer's grumble: after a fault has waited this long, in ticks, and no sooner than this long after her
     * last, so it comes rarely.
     */
    private static final int GRUMBLE_AFTER = 4 * 60 * 20;
    private static final int GRUMBLE_EVERY = 8 * 60 * 20;
    /** How long after a fix her computer speaks, in ticks, so its line comes after the clank and the chime. */
    private static final int VOICE_AFTER = 25;
    /** How long a line of hers waits for her cockpit to fall quiet at most, in ticks, then is let go unsaid. */
    private static final int VOICE_WAIT_MOST = 10 * 20;
    /** How long a line of hers is given, in ticks, if its sound cannot be read. */
    private static final int VOICE_LONGEST = 5 * 20;
    /** How loud her voice is, where the fault was: heard all through her (her lines carry far). */
    private static final float VOICE_VOLUME = 1.0F;
    /** How loud a star's chime and the fanfares are, heard round her rooms and on along her corridor. */
    private static final float CHIME_VOLUME = 1.5F;
    private static final float FANFARE_VOLUME = 2.0F;
    /** The chime of the first star, its pitch, and how much higher each star after it chimes. */
    private static final float CHIME_PITCH = 0.7F;
    private static final float CHIME_STEP = 0.12F;
    /** How loud the clink of a spanner taken off the board and the low note of a board switched off are. */
    private static final float CLINK_VOLUME = 0.8F;
    private static final float REFUSED_VOLUME = 0.6F;
    /**
     * For play-testing only: how long she flies between faults, in ticks, in place of the minutes above; none, at 0.
     * Set by Blockpilot's {@code skyliners:repairs} and never saved.
     */
    public static int testEvery;

    /** How many faults have been fixed near it, ever. */
    private int repairs;
    /** When its last star lit, in game time; {@link #NEVER} before the first. */
    private long litAt = NEVER;
    /** How many fault spots near it are at fault now, as last looked, sent to clients. */
    private int waiting;
    /** On the server: how long she has flown since the last fault went off, and how long until the next, in ticks. */
    private int flown;
    private int nextAfter = -1;
    /** On the server: since when a fault has waited unfixed, and when her computer last grumbled, in game time. */
    private long waitingSince = NEVER;
    private long grumbledAt = NEVER;
    /**
     * On the server: a line of her computer's waiting to be said, where, from when, and the autopilot whose cockpit it
     * waits on; none waiting. Not saved: a board loaded again has nothing left to say.
     */
    @Nullable
    private Holder<SoundEvent> line;
    private BlockPos lineAt = BlockPos.ZERO;
    private long lineFrom;
    @Nullable
    private AutopilotBlockEntity lineShip;

    public RepairBoardBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.REPAIR_BOARD_ENTITY.get(), pos, state);
    }

    /** How many faults have been fixed near it, ever, on either side. */
    public int repairs() {
        return repairs;
    }

    /** When its last star lit, in game time, on either side; {@link #NEVER} before the first. */
    public long litAt() {
        return litAt;
    }

    /** How many fault spots near it are at fault now, as the server last looked, on either side. */
    public int waiting() {
        return waiting;
    }

    /**
     * Where she stands with her repairs, for play-testing: faults waiting, fixes, flight counted and the next fault
     * due, in ticks, whether she flies along, her fault spots and where those at fault are.
     */
    public record Report(int waiting, int repairs, int flown, int nextAfter, boolean flying, int spots, List<BlockPos> faulty) {
    }

    /** How the board stands now, on the server. */
    public Report report(ServerLevel level) {
        List<FaultPanelBlockEntity> spots = spots(level);
        List<BlockPos> faulty = spots.stream().filter(FaultPanelBlockEntity::faulty).map(BlockEntity::getBlockPos).toList();
        return new Report(waiting, repairs, flown, nextAfter, flying(level), spots.size(), faulty);
    }

    /** Every board loaded in a level, on the server. */
    public static List<RepairBoardBlockEntity> loaded(ServerLevel level) {
        return LOADED.in(level);
    }

    /**
     * A fault spot just fixed, on the server: the nearest board aboard the same ship, or on the ground with it, lights
     * its next star; with none near, the fix's own clank and chime are all.
     */
    static void repaired(ServerLevel level, BlockPos spot) {
        if (!SkylinersConfig.FREIGHTER_REPAIRS.get() || !(level.getBlockEntity(spot) instanceof FaultPanelBlockEntity panel)) {
            return;
        }
        RepairBoardBlockEntity best = null;
        for (RepairBoardBlockEntity board : LOADED.in(level)) {
            boolean closer = best == null || board.getBlockPos().distSqr(spot) < best.getBlockPos().distSqr(spot);
            if (closer && board.looksAfter(panel)) {
                best = board;
            }
        }
        if (best != null) {
            best.lightStar(level, spot);
        }
    }

    /** Whether a fault spot is the board's: near it, and aboard the same ship as it or, like it, on the ground. */
    private boolean looksAfter(FaultPanelBlockEntity panel) {
        if (!panel.getBlockPos().closerThan(worldPosition, REACH) || level == null) {
            return false;
        }
        boolean aboard = SableFlight.aboard(level, Vec3.atCenterOf(worldPosition));
        boolean panelAboard = SableFlight.aboard(level, Vec3.atCenterOf(panel.getBlockPos()));
        return aboard ? panelAboard && SableFlight.sameAirliner(this, panel) : !panelAboard;
    }

    /** The fault spots the board looks after. */
    private List<FaultPanelBlockEntity> spots(ServerLevel level) {
        return FaultPanelBlockEntity.near(level, worldPosition, REACH).stream().filter(this::looksAfter).toList();
    }

    /** Whether the ship it is aboard is flying along under her autopilot, high off the ground. */
    private boolean flying(ServerLevel level) {
        AutopilotBlockEntity autopilot = AutopilotBlockEntity.shipOf(this);
        return autopilot != null && FLYING.contains(autopilot.phase()) && SableFlight.aboard(level, Vec3.atCenterOf(worldPosition));
    }

    /** Lights the next star, with its chime, a fanfare for each five, and her computer's line to follow. */
    private void lightStar(ServerLevel level, BlockPos spot) {
        repairs++;
        long now = level.getGameTime();
        litAt = now;
        waiting = Math.max(0, waiting - 1);
        int star = (repairs - 1) % STARS;
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, CHIME_VOLUME, CHIME_PITCH + CHIME_STEP * star);
        if (repairs % ROW == 0) {
            Holder<SoundEvent> fanfare = repairs % STARS == 0 ? SkylinersRegistry.STICKER_MAP_FULL : SkylinersRegistry.STICKER_MAP_ROW;
            level.playSound(null, worldPosition, fanfare.value(), SoundSource.BLOCKS, FANFARE_VOLUME, 1.0F);
        }
        AutopilotBlockEntity ship = AutopilotBlockEntity.shipOf(this);
        if (ship != null && ship.craft() == Craft.FREIGHTER) {
            sayLater(repairs % ROW == 0 ? SkylinersRegistry.FREIGHTER_COMPUTER_REPAIR_FIVE : fixedLine(level), spot, now + VOICE_AFTER, ship);
        }
        changed();
    }

    /** One of her lines for a fault fixed, at random. */
    private static Holder<SoundEvent> fixedLine(ServerLevel level) {
        List<Holder<SoundEvent>> lines = List.of(SkylinersRegistry.FREIGHTER_COMPUTER_REPAIR_FIXED, SkylinersRegistry.FREIGHTER_COMPUTER_REPAIR_HOLDS,
            SkylinersRegistry.FREIGHTER_COMPUTER_REPAIR_PATCHED);
        return lines.get(level.getRandom().nextInt(lines.size()));
    }

    /** Has her computer say a line where given, from the moment given, once her cockpit is quiet. */
    private void sayLater(Holder<SoundEvent> said, BlockPos at, long from, AutopilotBlockEntity ship) {
        line = said;
        lineAt = at;
        lineFrom = from;
        lineShip = ship;
    }

    /**
     * Used by a player, on the server: with no spanner about them it hands them one off its clip, with a clink;
     * otherwise its last star's chime again. Switched off, a low note and why.
     */
    void used(ServerLevel level, ServerPlayer player) {
        if (!SkylinersConfig.FREIGHTER_REPAIRS.get()) {
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, REFUSED_VOLUME, 0.6F);
            player.displayClientMessage(Component.translatable("message.skyliners.repairs.off"), true);
            return;
        }
        if (!hasSpanner(player)) {
            ItemStack spanner = new ItemStack(SkylinersRegistry.SPANNER.get());
            if (!player.getInventory().add(spanner)) {
                player.drop(spanner, false);
            }
            level.playSound(null, worldPosition, SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.BLOCKS, CLINK_VOLUME, 1.2F);
            return;
        }
        int star = Math.max(0, (repairs - 1) % STARS);
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, CLINK_VOLUME, CHIME_PITCH + CHIME_STEP * star);
    }

    /** Whether a player has a spanner anywhere about them. */
    private static boolean hasSpanner(Player player) {
        return player.getInventory().hasAnyMatching(stack -> stack.is(SkylinersRegistry.SPANNER.get()));
    }

    /**
     * Each tick, on the server: a line of her computer's waiting is said once its moment comes and her cockpit is
     * quiet; and every so often it looks over its fault spots and her flight (see {@link #look}).
     */
    void tick(ServerLevel level) {
        if (line != null) {
            sayWaiting(level);
        }
        if ((level.getGameTime() + worldPosition.asLong()) % LOOK_EVERY == 0 && SkylinersConfig.FREIGHTER_REPAIRS.get()) {
            look(level);
        }
    }

    /**
     * Counts the faults waiting, telling clients when that changes, and her computer grumbles at one waiting long; and,
     * while she flies along with fewer than the most waiting, counts her flight and sets one more off when it is due.
     */
    private void look(ServerLevel level) {
        List<FaultPanelBlockEntity> spots = spots(level);
        int open = (int) spots.stream().filter(FaultPanelBlockEntity::faulty).count();
        if (open != waiting) {
            waiting = open;
            changed();
        }
        grumble(level, open > 0);
        if (open >= MOST_OPEN || !flying(level)) {
            return;
        }
        if (nextAfter < 0) {
            nextAfter = interval(level);
        }
        flown += LOOK_EVERY;
        List<FaultPanelBlockEntity> sound = spots.stream().filter(spot -> !spot.faulty()).toList();
        if (flown < nextAfter || sound.isEmpty()) {
            return;
        }
        sound.get(level.getRandom().nextInt(sound.size())).breakDown(level);
        waiting = open + 1;
        changed();
        flown = 0;
        nextAfter = interval(level);
    }

    /** How long she flies until the next fault, in ticks: a few minutes at random, or the play-test's own. */
    private static int interval(ServerLevel level) {
        return testEvery > 0 ? testEvery : FAULT_EVERY_LEAST + level.getRandom().nextInt(FAULT_EVERY_MOST - FAULT_EVERY_LEAST + 1);
    }

    /** Her computer's rare grumble, aboard the freighter, at a fault left waiting a good long while. */
    private void grumble(ServerLevel level, boolean anyWaiting) {
        long now = level.getGameTime();
        if (!anyWaiting) {
            waitingSince = NEVER;
            return;
        }
        if (waitingSince == NEVER) {
            waitingSince = now;
        }
        boolean due = now - waitingSince > GRUMBLE_AFTER && (grumbledAt == NEVER || now - grumbledAt > GRUMBLE_EVERY);
        AutopilotBlockEntity ship = due && line == null ? AutopilotBlockEntity.shipOf(this) : null;
        if (ship != null && ship.craft() == Craft.FREIGHTER) {
            grumbledAt = now;
            sayLater(SkylinersRegistry.FREIGHTER_COMPUTER_REPAIR_GRUMBLE, worldPosition, now, ship);
        }
    }

    /**
     * Says the line of her computer's waiting, where it was given, heard all through her, once its moment has come
     * and nothing else is said in her cockpit, holding its floor while it lasts; let go unsaid after
     * {@link #VOICE_WAIT_MOST} or with her autopilot gone.
     */
    private void sayWaiting(ServerLevel level) {
        long now = level.getGameTime();
        if (now < lineFrom) {
            return;
        }
        if (lineShip == null || lineShip.isRemoved() || now - lineFrom > VOICE_WAIT_MOST) {
            line = null;
            return;
        }
        if (!lineShip.quiet()) {
            return;
        }
        lineShip.holdFloor(VoiceLength.ticks(line, VOICE_LONGEST));
        level.playSound(null, lineAt, line.value(), SoundSource.VOICE, VOICE_VOLUME, 1.0F);
        line = null;
    }

    /** Saves, and sends its stars and waiting faults to the clients. */
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
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
        tag.putInt("Repairs", repairs);
        if (litAt != NEVER) {
            tag.putLong("LitAt", litAt);
        }
    }

    /**
     * Read back as saved, or as sent: a board saved before its first star has none lit, and the faults waiting come
     * only in what is sent to clients, never in a save.
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        repairs = Math.max(0, tag.getInt("Repairs"));
        litAt = tag.contains("LitAt") ? tag.getLong("LitAt") : NEVER;
        waiting = tag.getInt("Waiting");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        tag.putInt("Waiting", waiting);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
