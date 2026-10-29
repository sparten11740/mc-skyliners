package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The robot bartender's moves, in its lower block (see {@link RobotBartenderBlock}). On the server: when it last began
 * serving, so that one serve plays out before the next begins. On the client, for its renderer: when it began serving
 * and the time before, told by block events, which way its head is turned and whom it is looking at. Each client tick
 * it is handed to {@link #clientTicker}, which the client sets (to turn its head toward whoever is nearest, see the
 * client's RobotBartenderRenderer) so this class stays safe to load on a dedicated server. Nothing is saved: a
 * bartender that comes into sight is polishing a glass.
 */
public class RobotBartenderBlockEntity extends BlockEntity {
    /** Set by the client: each tick, the bartender in sight. */
    public static Consumer<RobotBartenderBlockEntity> clientTicker = bartender -> { };
    /** The block event that sets it serving on every client in sight. */
    static final int SERVE = 1;
    /**
     * How long a serve takes, in ticks: shaking the shaker, pouring and setting the drink down on the bar (see the
     * client's RobotBartenderRenderer), as long as the sound of it (see tools/generate_sounds.mjs).
     */
    public static final int SERVE_TICKS = 64;
    /** Long enough ago that a serve begun then is over, without overflowing when it is taken from the time now. */
    private static final long NEVER = -1_000_000;
    /** How far its head turns toward where it looks each tick, as a share of the way left, and at most, in degrees. */
    private static final float HEAD_EASE = 0.3F;
    private static final float HEAD_STEP = 12;

    /** When it last began serving, in game ticks: on the server and, as told, on the client. */
    private long servedAt = NEVER;
    /**
     * When it began the serve before that, as told on the client: that drink may still stand on the bar while it
     * serves the next, a sale coming before the drink is taken.
     */
    private long servedBefore = NEVER;
    /**
     * How far its head is turned, now and a tick ago, in degrees: to its left of straight ahead, and up; and the
     * player it is looking at, if any. Only ever set on the client.
     */
    private float headYaw;
    private float headYawBefore;
    private float headPitch;
    private float headPitchBefore;
    @Nullable
    private UUID lookingAt;

    public RobotBartenderBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.ROBOT_BARTENDER_ENTITY.get(), pos, state);
    }

    /**
     * On the server, after a sale: unless a serve is still under way, it starts one, the shaker rattling, and every
     * client in sight sees it; whether it did.
     */
    boolean serve() {
        if (!(level instanceof ServerLevel server) || server.getGameTime() - servedAt < SERVE_TICKS) {
            return false;
        }
        servedAt = server.getGameTime();
        BlockPos pos = getBlockPos();
        server.blockEvent(pos, getBlockState().getBlock(), SERVE, 0);
        server.playSound(null, pos, AerolargeRegistry.BARTENDER_SHAKE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id != SERVE) {
            return super.triggerEvent(id, param);
        }
        if (level != null) {
            servedBefore = servedAt;
            servedAt = level.getGameTime();
        }
        return true;
    }

    /** When it last began serving, in game ticks; long ago if it has not since it came into sight. */
    public long servedAt() {
        return servedAt;
    }

    /** When it began the serve before the last, in game ticks; long ago if it has not. */
    public long servedBefore() {
        return servedBefore;
    }

    /** Ticks it on the client: its head's turn a tick ago is kept for drawing between ticks, then it looks about. */
    void clientTick() {
        headYawBefore = headYaw;
        headPitchBefore = headPitch;
        clientTicker.accept(this);
    }

    /**
     * Turns its head a tick's way toward where it is to look, in degrees to its left and up: a good part of the way,
     * slowing as it comes round, and never faster than a servo turns.
     */
    public void turnHead(float yaw, float pitch) {
        headYaw += Mth.clamp((yaw - headYaw) * HEAD_EASE, -HEAD_STEP, HEAD_STEP);
        headPitch += Mth.clamp((pitch - headPitch) * HEAD_EASE, -HEAD_STEP, HEAD_STEP);
    }

    /** How far its head is turned to its left at this partial tick, in degrees. */
    public float headYaw(float partialTick) {
        return Mth.lerp(partialTick, headYawBefore, headYaw);
    }

    /** How far its head is tilted up at this partial tick, in degrees. */
    public float headPitch(float partialTick) {
        return Mth.lerp(partialTick, headPitchBefore, headPitch);
    }

    /** The player it is looking at, as the client last found; null for nobody. */
    @Nullable
    public UUID lookingAt() {
        return lookingAt;
    }

    public void lookAt(@Nullable UUID player) {
        lookingAt = player;
    }
}
