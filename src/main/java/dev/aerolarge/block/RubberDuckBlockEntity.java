package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.FirstFlight;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A rubber duck's state. On the client: when it last started wiggling, for its renderer, which draws the duck so it
 * can move; and through {@link #clientTicker}, which the client sets so this class stays safe to load on a dedicated
 * server, a hidden duck's hint to the player who has not found it yet. On the server, a hidden duck counts toward the
 * finder's Duck Hunt advancement the first time they squeak it: a row of five dots on screen fills in yellow for the
 * ducks found so far, and the duck counts them out in squeaks, rising, a heart with each, for those who cannot read
 * the dots yet. For the last of them, the giant duck rises out of the airliner's moon pool, from where each hidden duck
 * was told it is when the airliner was built (see {@link GiantRubberDuckBlock}).
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

    /** Sets where the giant duck rises, as the airliner is built. */
    public void giant(GiantSpot giant) {
        this.giant = giant;
        setChanged();
    }

    /**
     * Counts this hidden duck as found by the player, the first time: their progress on screen, counted out in
     * squeaks; and for the last of them, the giant duck rises.
     */
    void found(ServerPlayer player) {
        if (duck() <= 0 || !FirstFlight.DUCK_HUNT.award(player, criterion(duck())) || !(level instanceof ServerLevel server)) {
            return;
        }
        int found = FirstFlight.DUCK_HUNT.progress(player);
        player.displayClientMessage(Component.translatable("message.aerolarge.rubber_duck.found", dots(found)), true);
        toCount = found;
        counted = 0;
        nextCount = server.getGameTime() + COUNT_DELAY;
        if (found >= RubberDuckBlock.HIDDEN_DUCKS) {
            raiseGiant(server, player);
        }
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

    /** A dot for each hidden duck, the first {@code found} of them yellow and the rest grey. */
    private static MutableComponent dots(int found) {
        MutableComponent row = Component.empty();
        for (int duck = 0; duck < RubberDuckBlock.HIDDEN_DUCKS; duck++) {
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        giant = tag.contains("Giant", Tag.TAG_COMPOUND) ? GiantSpot.load(tag.getCompound("Giant")) : null;
    }
}
