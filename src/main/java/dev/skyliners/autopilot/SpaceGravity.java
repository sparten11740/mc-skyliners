package dev.skyliners.autopilot;

import dev.skyliners.Aboard;
import dev.skyliners.Skyliners;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Low gravity aboard a rocket as she reaches space, until her artificial gravity comes on (the server config's
 * {@code rocket.lowGravity} leaves it out). For {@link #WEIGHTLESS} ticks after she reaches the edge of space (see
 * {@link EdgeOfSpace}), and on until that is announced, everyone aboard keeps only {@link #KEPT} of the world's
 * gravity: a jump rises three blocks and hangs in the air nearly two seconds, and a fall that long does not hurt. It
 * still pulls them down faster than she ever falls herself, so no one floats off her floor. A rising glide and a puff
 * of sparkles at their feet as it comes on; then her flight deck, over the cabin chime, says "Artificial gravity
 * enabled.", and with a falling glide each of them is their own weight again as they next land. Once a climb: she has
 * to come well back down under the edge before it happens again, and a rocket loaded again up there has had it.
 *
 * <p>Each player aboard holds a lease on it, renewed by her autopilot every few ticks while it lasts. It is lifted the
 * moment they are no longer aboard her (out of her door, a parachute's fall then as it always is), and, once their
 * lease has run out, only when they stand on something, so that it never ends mid-jump. Its modifiers are never saved,
 * so leaving the game takes it away too. One window for each rocket, worked by her autopilot's block entity.
 */
public final class SpaceGravity {
    /** How much of the world's gravity players aboard keep in space. */
    private static final double KEPT = 0.3;
    /** How long everyone aboard is light once she reaches space, in ticks: ten seconds, then her artificial gravity. */
    private static final int WEIGHTLESS = 10 * 20;
    /** How long the announcement waits for her cockpit to fall quiet, at most, in ticks, before it is let go unsaid. */
    private static final int WAIT_MOST = 10 * 20;
    /**
     * How long after the announcement starts its first line has been said, in ticks: the chime, then "Artificial
     * gravity enabled." (measured off announcement_gravity.ogg, whose words run from 2.1 to 4.2 seconds in). Everyone
     * stays light until then, and is their own weight again as they next land.
     */
    private static final int SAID = 84;
    /** How much further than usual they may fall unhurt there, in blocks: a jump's height and more. */
    private static final double SOFTER_LANDING = 4;
    /**
     * How often her autopilot renews everyone's lease and each light player is looked for aboard her, and how long a
     * lease lasts, in ticks.
     */
    private static final int EVERY = 10;
    private static final int LEASE = 40;
    /** How many sparkles puff out at a player's feet as it comes on. */
    private static final int SPARKLES = 12;
    private static final ResourceLocation LIGHTER = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "space_gravity");
    private static final ResourceLocation SOFTER = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "space_landing");
    /** Each light player's lease, on the server: their rocket's autopilot, among her blocks, and the tick it runs to. */
    private static final Map<UUID, Lease> LEASES = new HashMap<>();

    private record Lease(BlockPos autopilot, long until) {
    }

    /**
     * The game time she reached space this climb, from which everyone aboard is light for a while; unknown until she
     * is first seen, when a rocket already up there, loaded again, has had it. {@link Long#MIN_VALUE} while she has
     * not reached it since she was last well under its edge.
     */
    @Nullable
    private Long reachedAt;
    /** Whether her artificial gravity has been announced this climb, and how long the announcement has waited. */
    private boolean announced;
    private int waited;

    /**
     * Each tick of an assembled rocket's: once she reaches space, everyone aboard is light, renewed every few ticks,
     * until her artificial gravity is announced; none this climb if she gets there with it switched off.
     */
    void tick(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State flight) {
        double height = flight.position().y;
        long now = level.getGameTime();
        if (reachedAt == null) {
            reachedAt = EdgeOfSpace.reached(height) ? now - WEIGHTLESS : Long.MIN_VALUE;
            announced = EdgeOfSpace.reached(height);
        }
        if (EdgeOfSpace.wellUnder(height)) {
            reachedAt = Long.MIN_VALUE;
            announced = false;
            waited = 0;
            return;
        }
        if (!EdgeOfSpace.reached(height)) {
            return;
        }
        if (reachedAt == Long.MIN_VALUE) {
            reachedAt = now;
        }
        if (!SkylinersConfig.ROCKET_LOW_GRAVITY.get()) {
            announced = true;
        }
        if (announced) {
            return;
        }
        if (now >= reachedAt + WEIGHTLESS) {
            announce(level, autopilot, now);
        }
        if (!announced) {
            lightenAboard(autopilot, now);
        }
    }

    /**
     * Whether everyone aboard is light now: she has reached space this climb and her artificial gravity is not yet
     * announced (a sudden boost then would throw them about her).
     */
    boolean light() {
        return reachedAt != null && reachedAt != Long.MIN_VALUE && !announced;
    }

    /** Every few ticks, everyone aboard is light, their lease renewed for a while. */
    private static void lightenAboard(AutopilotBlockEntity autopilot, long now) {
        if (now % EVERY != 0) {
            return;
        }
        Lease lease = new Lease(autopilot.getBlockPos(), now + LEASE);
        for (ServerPlayer player : Aboard.players(autopilot)) {
            LEASES.put(player.getUUID(), lease);
            lighten(player);
        }
    }

    /**
     * Her flight deck announces her artificial gravity, over the cabin chime, to everyone aboard wherever they are in
     * her, once her cockpit is quiet, and everyone's lease runs out as its first line is said; let go unsaid if it
     * waits too long, everyone's lease running out there and then.
     */
    private void announce(ServerLevel level, AutopilotBlockEntity autopilot, long now) {
        if (autopilot.quiet()) {
            EdgeOfSpace.announce(level, autopilot, SkylinersRegistry.ANNOUNCEMENT_GRAVITY);
            leaseAboardUntil(autopilot, now + SAID);
            announced = true;
        } else if (++waited >= WAIT_MOST) {
            leaseAboardUntil(autopilot, now);
            announced = true;
        }
    }

    /** The leases of everyone light aboard her run out at a tick, from which each is their own weight as they land. */
    private static void leaseAboardUntil(AutopilotBlockEntity autopilot, long until) {
        Lease lease = new Lease(autopilot.getBlockPos(), until);
        for (ServerPlayer player : Aboard.players(autopilot)) {
            LEASES.replace(player.getUUID(), lease);
        }
    }

    /**
     * Each tick of a light player's, on the server: back to their weight once they are due it and stand on something,
     * looked at every tick so that a hop's one tick on the floor is not missed, or, looked at every few ticks, once
     * they have left her.
     */
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Lease lease = LEASES.get(player.getUUID());
        if (lease == null) {
            return;
        }
        boolean due = player.level().getGameTime() > lease.until() && player.onGround();
        if (due || player.tickCount % EVERY == 0 && !aboard(player, lease)) {
            LEASES.remove(player.getUUID());
            restore(player);
        }
    }

    /** Whether a light player is still aboard the rocket whose autopilot gave them their lease. */
    private static boolean aboard(ServerPlayer player, Lease lease) {
        return player.level().isLoaded(lease.autopilot())
            && player.level().getBlockEntity(lease.autopilot()) instanceof AutopilotBlockEntity autopilot && Aboard.isAboard(player, autopilot);
    }

    /** A player leaving the game leaves their lease too: its modifiers are never saved. */
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LEASES.remove(event.getEntity().getUUID());
    }

    private static void lighten(ServerPlayer player) {
        boolean came = modify(player, Attributes.GRAVITY, LIGHTER, KEPT - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modify(player, Attributes.SAFE_FALL_DISTANCE, SOFTER, SOFTER_LANDING, AttributeModifier.Operation.ADD_VALUE);
        if (came) {
            cue(player, SkylinersRegistry.SPACE_LIGHT);
            player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.1, player.getZ(), SPARKLES, 0.3, 0.05, 0.3, 0.02);
        }
    }

    private static void restore(ServerPlayer player) {
        boolean went = unmodify(player, Attributes.GRAVITY, LIGHTER);
        unmodify(player, Attributes.SAFE_FALL_DISTANCE, SOFTER);
        if (went) {
            cue(player, SkylinersRegistry.SPACE_HEAVY);
        }
    }

    /** Adds a modifier to a player's attribute unless it is there already; whether it was added. */
    private static boolean modify(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null || instance.hasModifier(id)) {
            return false;
        }
        instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        return true;
    }

    /** Takes a modifier off a player's attribute; whether it was on. */
    private static boolean unmodify(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id) {
        AttributeInstance instance = player.getAttribute(attribute);
        return instance != null && instance.removeModifier(id);
    }

    private static void cue(ServerPlayer player, Holder<SoundEvent> sound) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.6F, 1.0F);
    }
}
