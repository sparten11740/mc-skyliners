package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A deck robot's block entity (see {@link DeckRobotBlock}): on the server, its beeps, a chirp of hello as someone
 * comes up to it, a trill back when touched, and now and then, with someone near, a beep to itself; on the client,
 * the turn of its head, which {@link #clientTicker} sets each tick (the client's {@code DeckRobotRenderer} turns it
 * toward whoever is nearest), kept here for drawing between ticks. Nothing of it is saved.
 */
public class DeckRobotBlockEntity extends BlockEntity {
    /** How near someone must come for it to chirp hello, and to beep to itself now and then, in blocks. */
    private static final double GREET_RANGE = 5;
    private static final double IDLE_RANGE = 12;
    /** How often it looks for who is near, in ticks, and how long after a hello before it says hello again. */
    private static final int LOOK_EVERY = 20;
    private static final int GREET_AGAIN = 200;
    /** How long it waits between beeps to itself at least, and how much longer at random, in ticks. */
    private static final int IDLE_LEAST = 600;
    private static final int IDLE_SPREAD = 900;
    /** How loud its hello and its trill are, and its beeps to itself, quieter, as shares of full. */
    private static final float VOLUME = 0.8F;
    private static final float IDLE_VOLUME = 0.5F;
    /** How fast its head comes round, a share of the way a tick, and at most so many degrees a tick. */
    private static final float HEAD_EASE = 0.25F;
    private static final float HEAD_STEP = 12;

    /** Turns its head on the client each tick; the client sets it, so the server never loads what it reaches for. */
    public static Consumer<DeckRobotBlockEntity> clientTicker = robot -> { };

    /** Whether someone was near it when it last looked, and the game tick before which it says no hello again. */
    private boolean someoneNear;
    private long quietUntil;
    /** The game tick from which it may beep to itself again; none before it is first ticked. */
    private long nextIdle = -1;
    /** How far its head is turned to its left, in degrees, on the client: all the way round if need be. */
    private final HeadTurn headYaw = new HeadTurn(HEAD_EASE, HEAD_STEP, true);

    public DeckRobotBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.DECK_ROBOT_ENTITY.get(), pos, state);
    }

    /** Each tick on the server, every so often: a hello for someone come up to it, or a beep to itself. */
    void serverTick(ServerLevel level) {
        long now = level.getGameTime();
        if (Math.floorMod(now + worldPosition.asLong(), LOOK_EVERY) != 0) {
            return;
        }
        if (nextIdle < 0) {
            nextIdle = now + IDLE_LEAST + level.getRandom().nextInt(IDLE_SPREAD);
        }
        Vec3 at = SableFlight.toWorld(level, Vec3.atCenterOf(worldPosition));
        boolean near = anyoneWithin(level, at, GREET_RANGE);
        if (near && !someoneNear && now >= quietUntil) {
            quietUntil = now + GREET_AGAIN;
            beep(level, SkylinersRegistry.ROBOT_CHIRP.get(), VOLUME);
        } else if (now >= nextIdle && anyoneWithin(level, at, IDLE_RANGE)) {
            nextIdle = now + IDLE_LEAST + level.getRandom().nextInt(IDLE_SPREAD);
            beep(level, level.getRandom().nextBoolean() ? SkylinersRegistry.ROBOT_HAPPY.get() : SkylinersRegistry.ROBOT_TRILL.get(), IDLE_VOLUME);
        }
        someoneNear = near;
    }

    /** Touched: it trills back. */
    void answer(ServerLevel level) {
        beep(level, SkylinersRegistry.ROBOT_TRILL.get(), VOLUME);
    }

    private void beep(ServerLevel level, SoundEvent sound, float volume) {
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, 1.0F);
    }

    /** Whether any player not watching stands within so far of a point in the world. */
    private static boolean anyoneWithin(ServerLevel level, Vec3 at, double range) {
        for (Player player : level.players()) {
            if (!player.isSpectator() && player.position().distanceToSqr(at) < range * range) {
                return true;
            }
        }
        return false;
    }

    /** Ticks it on the client: its head's turn a tick ago kept for drawing between ticks, then it looks about. */
    void clientTick() {
        headYaw.tick();
        clientTicker.accept(this);
    }

    /** Turns its head a tick's way toward where it is to look, in degrees to its left, the short way round. */
    public void turnHead(float yaw) {
        headYaw.toward(yaw);
    }

    /** How far its head is turned to its left at this partial tick, in degrees. */
    public float headYaw(float partialTick) {
        return headYaw.at(partialTick);
    }
}
