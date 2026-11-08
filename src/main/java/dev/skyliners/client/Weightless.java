package dev.skyliners.client;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.zerog.GravitySwitchBlockEntity;
import dev.skyliners.zerog.WeightlessRoom;
import dev.skyliners.zerog.ZeroGravity;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Weightlessness on this client (see {@link ZeroGravity}). The player's own game moves them, so here a player floating
 * in a room whose gravity switch is off is given a gentle push up while they hold jump and down while they sneak,
 * drifting to a stop up or down once they let go, lifted off the floor as they start to float, and bounced slowly back
 * off whatever wall, floor or ceiling they drift into,
 * with a soft boing; the game itself drifts them on, as weightless as the server made them, and steers them gently the
 * way they walk. Nothing here moves them into a block: they move by the game's own collisions. And each weightless
 * room hums softly while its gravity is off, a hum played in the middle of the room so that aboard a ship in flight
 * Sable carries it along, and little sparkles drift in its air round whoever is near.
 */
public final class Weightless {
    /** How hard holding jump or sneak pushes a floating player up or down, in blocks a tick each tick. */
    private static final double PUSH = 0.012;
    /** The fastest a push carries a floating player up or down, in blocks a tick. */
    private static final double MOST_UP_DOWN = 0.15;
    /**
     * The share of their speed up or down a floating player keeps each tick, on top of the air's own, while they hold
     * neither jump nor sneak: they drift to a stop a block or two after letting go, rather than on to the ceiling.
     */
    private static final double DRIFT_KEPT = 0.95;
    /** How fast a player rises off the floor as they start to float, in blocks a tick: about a block, then still. */
    private static final double LIFT = 0.08;
    /**
     * A bounce off a wall, floor or ceiling: the share of their speed a floating player keeps, the way back, and the
     * slowest they must have been going to bounce, in blocks a tick; slower, they stop against it.
     */
    private static final double BOUNCE = 0.5;
    private static final double BOUNCE_FROM = 0.04;
    /** How near still a way they moved in must be to have been stopped there by something, in blocks a tick. */
    private static final double STOPPED = 1.0E-3;
    /** How fast a bounce must be to be heard, in blocks a tick, and how loud it is then. */
    private static final double BUMP_HEARD_FROM = 0.06;
    private static final float BUMP_VOLUME = 0.5F;
    /** How loud a weightless room's hum is. */
    private static final float HUM_VOLUME = 0.5F;
    /** How near a weightless room's middle the player must be to see its sparkles, in blocks, and how many a tick. */
    private static final double SPARKLES_SEEN = 32;
    private static final int SPARKLES_A_TICK = 3;
    /** How fast a sparkle drifts at most, each way, in blocks a tick. */
    private static final double SPARKLE_DRIFT = 0.01;
    /** The hum each weightless room plays on this client, by its switch, forgotten once it stops. */
    private static final Map<GravitySwitchBlockEntity, Hum> HUMS = new HashMap<>();
    private static final RandomSource RANDOM = RandomSource.create();
    /** How the player was moving as their tick began, pushed; null while they are not floating. */
    @Nullable
    private static Vec3 before;

    private Weightless() {
    }

    /**
     * Before each of the player's own ticks: floating, pushed up while they hold jump and down while they sneak, coming
     * to a gentle stop up or down when they hold neither, and lifted off the floor as they start to float. (Standing,
     * whether they touch the floor flickers tick by tick while they fall slowly, so they are lifted whether or not.)
     */
    public static void onTickPre(PlayerTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LocalPlayer player)) {
            return;
        }
        if (!ZeroGravity.weightless(player) || player.isPassenger() || player.getAbilities().flying) {
            before = null;
            return;
        }
        Vec3 moving = player.getDeltaMovement();
        double up = (player.input.jumping ? PUSH : 0) - (player.input.shiftKeyDown ? PUSH : 0);
        double rising = up == 0 ? moving.y * DRIFT_KEPT : Mth.clamp(moving.y + up, -MOST_UP_DOWN, MOST_UP_DOWN);
        if (before == null) {
            rising = Math.max(rising, LIFT);
        }
        player.setDeltaMovement(moving.x, rising, moving.z);
        before = player.getDeltaMovement();
    }

    /** After each of the player's own ticks: floating, bounced off whatever stopped them, with a boing if it was hard. */
    public static void onTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || before == null) {
            return;
        }
        Vec3 after = player.getDeltaMovement();
        double x = player.horizontalCollision ? bounce(before.x, after.x) : after.x;
        double y = player.verticalCollision ? bounce(before.y, after.y) : after.y;
        double z = player.horizontalCollision ? bounce(before.z, after.z) : after.z;
        Vec3 bounced = new Vec3(x, y, z);
        if (bounced.equals(after)) {
            return;
        }
        player.setDeltaMovement(bounced);
        double hard = bounced.subtract(after).length();
        if (hard >= BUMP_HEARD_FROM) {
            float pitch = (float) Mth.clamp(1.3 - hard * 2, 0.8, 1.3);
            player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), SkylinersRegistry.ZERO_GRAVITY_BUMP.get(),
                SoundSource.PLAYERS, BUMP_VOLUME, pitch, false);
        }
    }

    private static double bounce(double moving, double now) {
        return Math.abs(moving) > BOUNCE_FROM && Math.abs(now) < STOPPED ? -moving * BOUNCE : now;
    }

    /**
     * Each tick of a weightless room's switch on this client: its hum started, if it has none, and sparkles in its air
     * if the player is near; every hum stopped, or whose switch is gone, forgotten. A hum is known to be playing by its
     * not having stopped: aboard a ship Sable plays it through a stand-in, so the sound manager never calls it active.
     */
    public static void tick(GravitySwitchBlockEntity gravitySwitch) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        WeightlessRoom room = gravitySwitch.room();
        if (level == null || minecraft.player == null || room == null) {
            return;
        }
        SoundManager sounds = minecraft.getSoundManager();
        HUMS.entrySet().removeIf(hum -> hum.getValue().isStopped() || hum.getKey().isRemoved());
        if (!HUMS.containsKey(gravitySwitch)) {
            Hum hum = new Hum(gravitySwitch, room.middle());
            HUMS.put(gravitySwitch, hum);
            sounds.play(hum);
        }
        if (SableClient.toWorld(level, room.middle()).distanceTo(minecraft.player.position()) < SPARKLES_SEEN) {
            sparkle(level, room);
        }
    }

    /** A few sparkles at open spots in a room's air, each drifting slowly as it twinkles out. */
    private static void sparkle(ClientLevel level, WeightlessRoom room) {
        AABB box = room.bounds();
        for (int k = 0; k < SPARKLES_A_TICK; k++) {
            Vec3 at = new Vec3(Mth.lerp(RANDOM.nextDouble(), box.minX, box.maxX), Mth.lerp(RANDOM.nextDouble(), box.minY, box.maxY),
                Mth.lerp(RANDOM.nextDouble(), box.minZ, box.maxZ));
            BlockPos block = BlockPos.containing(at);
            if (room.contains(block) && level.getBlockState(block).getCollisionShape(level, block).isEmpty()) {
                level.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, drift(), drift(), drift());
            }
        }
    }

    private static double drift() {
        return (RANDOM.nextDouble() * 2 - 1) * SPARKLE_DRIFT;
    }

    /** A weightless room's hum, in its middle, stopping once its gravity is back on or its switch is gone. */
    private static final class Hum extends AbstractTickableSoundInstance {
        private final GravitySwitchBlockEntity gravitySwitch;

        Hum(GravitySwitchBlockEntity gravitySwitch, Vec3 at) {
            super(SkylinersRegistry.ZERO_GRAVITY_HUM.get(), SoundSource.BLOCKS, RandomSource.create());
            this.gravitySwitch = gravitySwitch;
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.looping = true;
            this.delay = 0;
            this.volume = HUM_VOLUME;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
        }

        @Override
        public void tick() {
            if (gravitySwitch.isRemoved() || !gravitySwitch.weightless()) {
                stop();
            }
        }
    }
}
