package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A disco ball's turning, kept only on the client, where it is drawn: how far it has turned and how fast it turns,
 * spinning up when switched on and slowing to a stop when switched off, and so how bright its spots shine. Each tick it
 * is also handed to {@link #clientTicker}, which the client sets (for the spots, see the client's DiscoLights) so this
 * class stays safe to load on a dedicated server. Nothing is saved: a ball that comes into sight is already turning.
 */
public class DiscoBallBlockEntity extends BlockEntity {
    /** Set by the client: each tick, the ball near the player. */
    public static Consumer<DiscoBallBlockEntity> clientTicker = ball -> { };
    /** How far the ball turns a tick while on, in radians: once round in twelve seconds. */
    private static final float TURN = Mth.TWO_PI / 240;
    /** How much of the way to its new speed the ball gets each tick once switched: up to speed in about two seconds. */
    private static final float SPIN_UP = 0.08F;
    /** Slower than this share of its full speed, a ball switched off has stopped. */
    private static final float STOPPED = 0.002F;

    /** How far the ball has turned, now and a tick ago, in radians, kept within a turn or so. */
    private float turned;
    private float turnedBefore;
    /** How fast it turns, now and a tick ago, as a share of its full speed. */
    private float speed;
    private float speedBefore;
    private boolean ticked;

    public DiscoBallBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.DISCO_BALL_ENTITY.get(), pos, state);
    }

    /** Turns the ball on by a tick, on the client. */
    void clientTick() {
        float target = getBlockState().getValue(DiscoBallBlock.LIT) ? 1 : 0;
        if (!ticked) {
            // Already turning as it comes into sight, or still.
            ticked = true;
            speed = target;
        }
        speedBefore = speed;
        speed += (target - speed) * SPIN_UP;
        if (target == 0 && speed < STOPPED) {
            speed = 0;
        }
        if (turned > Mth.TWO_PI) {
            turned -= Mth.TWO_PI;
        }
        turnedBefore = turned;
        turned += speed * TURN;
        clientTicker.accept(this);
    }

    /** How far the ball has turned about its rod at this partial tick, in radians. */
    public float turned(float partialTick) {
        return Mth.lerp(partialTick, turnedBefore, turned);
    }

    /** How fast the ball turns at this partial tick, as a share of its full speed: as bright as its spots shine. */
    public float power(float partialTick) {
        return Mth.lerp(partialTick, speedBefore, speed);
    }
}
