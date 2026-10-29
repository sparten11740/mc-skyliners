package dev.aerolarge.autopilot;

import dev.aerolarge.Aboard;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
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
 * Low gravity aboard a rocket in space, an experimental behaviour the server config's {@code rocket.lowGravity}
 * switches on. While she is at the edge of space or over it (see {@link EdgeOfSpace}), everyone aboard keeps only
 * {@link #KEPT} of the world's gravity: a jump rises three blocks and hangs in the air nearly two seconds, and a fall
 * that long does not hurt. It still pulls them down faster than she ever falls herself, so no one floats off her floor
 * as she starts back down. A rising glide and a puff of sparkles at their feet as it comes on, a falling glide as it
 * goes.
 *
 * <p>Each player aboard holds a lease on it, renewed by her autopilot every few ticks while she is up there. It is
 * lifted the moment they are no longer aboard her (out of her door, a parachute's fall then as it always is), and,
 * once their lease has run out, as she comes back under the edge, only when they stand on something, so that dropping
 * under the edge mid-jump never hurts. Its modifiers are never saved, so leaving the game takes it away too.
 */
public final class SpaceGravity {
    /** How much of the world's gravity players aboard keep in space. */
    private static final double KEPT = 0.3;
    /** How much further than usual they may fall unhurt there, in blocks: a jump's height and more. */
    private static final double SOFTER_LANDING = 4;
    /** How often her autopilot renews everyone's lease and each player's is looked at, and how long it lasts, in ticks. */
    private static final int EVERY = 10;
    private static final int LEASE = 40;
    /** How many sparkles puff out at a player's feet as it comes on. */
    private static final int SPARKLES = 12;
    private static final ResourceLocation LIGHTER = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "space_gravity");
    private static final ResourceLocation SOFTER = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "space_landing");
    /** Each light player's lease, on the server: their rocket's autopilot, among her blocks, and the tick it runs to. */
    private static final Map<UUID, Lease> LEASES = new HashMap<>();

    private record Lease(BlockPos autopilot, long until) {
    }

    private SpaceGravity() {
    }

    /** Each tick of an assembled rocket's: every few ticks, at the edge of space or over it, everyone aboard is light. */
    static void tick(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State flight) {
        boolean inSpace = flight.position().y >= EdgeOfSpace.HEIGHT - EdgeOfSpace.AT_EDGE;
        if (!AerolargeConfig.ROCKET_LOW_GRAVITY.get() || !inSpace || level.getGameTime() % EVERY != 0) {
            return;
        }
        Lease lease = new Lease(autopilot.getBlockPos(), level.getGameTime() + LEASE);
        for (ServerPlayer player : Aboard.players(autopilot)) {
            LEASES.put(player.getUUID(), lease);
            lighten(player);
        }
    }

    /** Every few ticks of a light player's, on the server: back to their weight once they have left her, or are due it. */
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % EVERY != 0) {
            return;
        }
        Lease lease = LEASES.get(player.getUUID());
        if (lease == null) {
            return;
        }
        boolean aboard = player.level().isLoaded(lease.autopilot())
            && player.level().getBlockEntity(lease.autopilot()) instanceof AutopilotBlockEntity autopilot && Aboard.isAboard(player, autopilot);
        boolean due = player.level().getGameTime() > lease.until() && player.onGround();
        if (!aboard || due) {
            LEASES.remove(player.getUUID());
            restore(player);
        }
    }

    /** A player leaving the game leaves their lease too: its modifiers are never saved. */
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LEASES.remove(event.getEntity().getUUID());
    }

    private static void lighten(ServerPlayer player) {
        boolean came = modify(player, Attributes.GRAVITY, LIGHTER, KEPT - 1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        modify(player, Attributes.SAFE_FALL_DISTANCE, SOFTER, SOFTER_LANDING, AttributeModifier.Operation.ADD_VALUE);
        if (came) {
            cue(player, AerolargeRegistry.SPACE_LIGHT);
            player.serverLevel().sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.1, player.getZ(), SPARKLES, 0.3, 0.05, 0.3, 0.02);
        }
    }

    private static void restore(ServerPlayer player) {
        boolean went = unmodify(player, Attributes.GRAVITY, LIGHTER);
        unmodify(player, Attributes.SAFE_FALL_DISTANCE, SOFTER);
        if (went) {
            cue(player, AerolargeRegistry.SPACE_HEAVY);
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
