package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.FirstFlight;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A rubber duck's state. On the client: when it last started wiggling, for its renderer, which draws the duck so it
 * can move; and through {@link #clientTicker}, which the client sets so this class stays safe to load on a dedicated
 * server, a hidden duck's hint to the player who has not found it yet. On the server, a hidden duck is found by the
 * player the first time they squeak it, in its airliner's own hunt (see {@link DuckHunts}): a row of a dot for each of
 * the airliner's hidden ducks fills in yellow for those found so far, and the duck counts them out in squeaks, rising,
 * a heart with each, for those who cannot read the dots yet. For the last of them, the giant duck rises out of the
 * airliner's moon pool, from where each hidden duck was told it is when the airliner was built (see
 * {@link GiantRubberDuckBlock}). Each find also counts toward the finder's Duck Hunt advancement, which keeps every
 * duck they have found aboard any airliner. An airliner built before each had a hunt of its own has its ducks counted
 * by the advancement alone, as they were then: a player finds each of them once, whichever airliner it is aboard.
 */
public class RubberDuckBlockEntity extends BlockEntity {
    /** Set by the client: a hidden duck's hint, each tick. */
    public static Consumer<RubberDuckBlockEntity> clientTicker = duck -> { };
    /** The block event that sets a duck wiggling on every client in sight. */
    static final int WIGGLE = 1;
    /** Long enough ago that a wiggle from then is over, without overflowing when it is taken from the time now. */
    private static final long NEVER = -1_000_000;
    /** How long after the find the counting starts, and how far apart its squeaks are, in ticks. */
    private static final int COUNT_DELAY = 12;
    private static final int COUNT_GAP = 7;
    /** The counting squeaks: how loud, the first one's pitch and how much higher each next one is. */
    private static final float COUNT_VOLUME = 0.8F;
    private static final float COUNT_PITCH = 0.9F;
    private static final float COUNT_RISE = 0.12F;
    /** How high over the duck's block a counting squeak's heart starts. */
    private static final double COUNT_HEART_HEIGHT = 0.9;
    /** A dot on the progress row, for each hidden duck: yellow once found, grey until then (a black circle, U+25CF). */
    private static final String DOT = Character.toString(0x25CF);

    /**
     * Where the giant duck rises once the last of the hidden ducks is found, from this one, in its own frame: blocks to
     * its right, up and ahead of it as it faces, and how many quarter turns clockwise from its own facing the giant
     * faces. Kept that way it holds however the airliner was placed, or turned as it was assembled, as the duck's
     * facing turns with it.
     */
    public record GiantSpot(int right, int up, int ahead, int turns) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Right", right);
            tag.putInt("Up", up);
            tag.putInt("Ahead", ahead);
            tag.putInt("Turns", turns);
            return tag;
        }

        static GiantSpot load(CompoundTag tag) {
            return new GiantSpot(tag.getInt("Right"), tag.getInt("Up"), tag.getInt("Ahead"), tag.getInt("Turns"));
        }
    }

    /** When the duck last started wiggling, in game ticks; only ever set on the client. */
    private long wiggled = NEVER;
    /** Where the giant duck rises, for a hidden duck in an airliner with room for one; null for any other. */
    @Nullable
    private GiantSpot giant;
    /**
     * The hunt a hidden duck is part of, and how many ducks its airliner hides for it; none for one hidden before each
     * airliner had a hunt of its own.
     */
    @Nullable
    private UUID hunt;
    private int ducks;
    /** Who has found the duck in its hunt, as the client was last told. */
    private Set<UUID> foundBy = Set.of();
    /** After a find, how many squeaks are left to count and how many have been; when the next is due, in game ticks. */
    private int toCount;
    private int counted;
    private long nextCount;

    public RubberDuckBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.RUBBER_DUCK_ENTITY.get(), pos, state);
    }

    /** The criterion of the Duck Hunt advancement for a hidden duck, from 1. */
    public static String criterion(int duck) {
        return "duck_" + duck;
    }

    /** Which hidden duck this is, from 1; 0 for one put down by hand. */
    public int duck() {
        return getBlockState().getValue(RubberDuckBlock.DUCK);
    }

    public long wiggled() {
        return wiggled;
    }

    /** Sets the duck wiggling, on the client. */
    public void wiggle() {
        if (level != null && level.isClientSide) {
            wiggled = level.getGameTime();
        }
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id != WIGGLE) {
            return super.triggerEvent(id, param);
        }
        wiggle();
        return true;
    }

    /**
     * Tells a hidden duck, as the airliner is built, the hunt it is part of, how many ducks the airliner hides for it,
     * and where the giant duck rises, if it has room to.
     */
    public void hide(UUID hunt, int ducks, @Nullable GiantSpot giant) {
        this.hunt = hunt;
        this.ducks = ducks;
        this.giant = giant;
        setChanged();
    }

    /** The hunt a hidden duck is part of; null for one hidden before each airliner had a hunt of its own. */
    @Nullable
    public UUID hunt() {
        return hunt;
    }

    /** Whether the player has found the duck in its hunt, as the client was last told. */
    public boolean foundBy(UUID player) {
        return foundBy.contains(player);
    }

    /**
     * Counts this hidden duck as found by the player, the first time: their progress on screen, counted out in
     * squeaks; and for the last of them, the giant duck rises.
     */
    void found(ServerPlayer player) {
        if (duck() <= 0 || !(level instanceof ServerLevel server)) {
            return;
        }
        boolean firstEver = FirstFlight.DUCK_HUNT.award(player, criterion(duck()));
        int found = countFind(server, player, firstEver);
        if (found == 0) {
            return;
        }
        int of = hunt != null ? ducks : RubberDuckBlock.HIDDEN_DUCKS;
        player.displayClientMessage(Component.translatable("message.aerolarge.rubber_duck.found", dots(found, of)), true);
        toCount = found;
        counted = 0;
        nextCount = server.getGameTime() + COUNT_DELAY;
        if (found >= of) {
            raiseGiant(server, player);
        }
    }

    /**
     * How many of its hunt's ducks the player has found, this one with them, if it is new to them in the hunt; 0 if
     * they had found it already. One hidden before each airliner had a hunt counts their Duck Hunt progress instead,
     * if this is the first time they have found it anywhere.
     */
    private int countFind(ServerLevel level, ServerPlayer player, boolean firstEver) {
        if (hunt == null) {
            return firstEver ? FirstFlight.DUCK_HUNT.progress(player) : 0;
        }
        DuckHunts hunts = DuckHunts.of(level);
        if (!hunts.find(hunt, player.getUUID(), duck())) {
            return 0;
        }
        // The clients hear who has found it, so its hint falls quiet for the finder.
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        return hunts.found(hunt, player.getUUID());
    }

    /**
     * The giant duck, for the player who found the last duck: up out of this airliner's moon pool, or where it has no
     * room for one, its squeak, fanfare and confetti here.
     */
    private void raiseGiant(ServerLevel level, ServerPlayer finder) {
        if (giant == null) {
            GiantRubberDuckBlockEntity.celebrate(level, Vec3.atBottomCenterOf(getBlockPos()), finder);
            finder.displayClientMessage(Component.translatable("message.aerolarge.rubber_duck.all_found"), false);
            return;
        }
        Direction facing = getBlockState().getValue(RubberDuckBlock.FACING);
        BlockPos origin = getBlockPos().relative(facing.getClockWise(), giant.right()).above(giant.up()).relative(facing, giant.ahead());
        Direction giantFacing = facing;
        for (int turn = 0; turn < giant.turns(); turn++) {
            giantFacing = giantFacing.getClockWise();
        }
        GiantRubberDuckBlock.raise(level, origin, giantFacing, finder);
        finder.displayClientMessage(Component.translatable("message.aerolarge.rubber_duck.giant"), false);
    }

    /** A dot for each of so many hidden ducks, the first {@code found} of them yellow and the rest grey. */
    private static MutableComponent dots(int found, int of) {
        MutableComponent row = Component.empty();
        for (int duck = 0; duck < of; duck++) {
            row.append(Component.literal(duck == 0 ? DOT : " " + DOT).withStyle(duck < found ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY));
        }
        return row;
    }

    /** Counts the ducks found out in squeaks, one each few ticks, each a little higher, with a heart and a wiggle. */
    void serverTick() {
        if (toCount <= 0 || !(level instanceof ServerLevel server) || server.getGameTime() < nextCount) {
            return;
        }
        BlockPos pos = getBlockPos();
        server.playSound(null, pos, AerolargeRegistry.RUBBER_DUCK_SQUEAK.get(), SoundSource.BLOCKS, COUNT_VOLUME, COUNT_PITCH + COUNT_RISE * counted);
        server.sendParticles(ParticleTypes.HEART, pos.getX() + 0.5, pos.getY() + COUNT_HEART_HEIGHT + RubberDuckBlock.lift(getBlockState()),
            pos.getZ() + 0.5, 1, 0, 0, 0, 0);
        server.blockEvent(pos, getBlockState().getBlock(), WIGGLE, 0);
        counted++;
        toCount--;
        nextCount = server.getGameTime() + COUNT_GAP;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (giant != null) {
            tag.put("Giant", giant.save());
        }
        if (hunt != null) {
            tag.putUUID("Hunt", hunt);
            tag.putInt("Ducks", ducks);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        giant = tag.contains("Giant", Tag.TAG_COMPOUND) ? GiantSpot.load(tag.getCompound("Giant")) : null;
        hunt = tag.hasUUID("Hunt") ? tag.getUUID("Hunt") : null;
        ducks = tag.getInt("Ducks");
        foundBy = tag.getList("FoundBy", Tag.TAG_INT_ARRAY).stream().map(NbtUtils::loadUUID).collect(Collectors.toUnmodifiableSet());
    }

    /** What the clients are told of a hidden duck: its hunt, and who has found it in that hunt, for its hint. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (hunt == null || !(level instanceof ServerLevel server)) {
            return tag;
        }
        tag.putUUID("Hunt", hunt);
        ListTag finders = new ListTag();
        DuckHunts.of(server).finders(hunt, duck()).forEach(finder -> finders.add(NbtUtils.createUUID(finder)));
        tag.put("FoundBy", finders);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
