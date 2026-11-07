package dev.skyliners.client;

import dev.skyliners.entity.ShipParticles;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3d;

/**
 * The client's side of {@link ShipParticles}: a particle given off by a creature aboard an assembled ship is moved on
 * each tick as far as the ship moved under the creature, so it drifts about it as it would on the ground instead of
 * trailing away behind her as she flies. Only a bounded number are carried at once; past that, off any ship, and with
 * the player's particle setting turned down (which the game's own way of giving them off then thins out), they are given
 * off as the game gives off any. A carried particle that meets a block of the world on the way stops there, as any
 * particle does.
 */
public final class CarriedParticles {
    /** How many particles are carried at once at most; more are given off uncarried. */
    private static final int MOST = 512;
    /** How many ticks a second the ship's speed, in blocks a second, is shared over. */
    private static final double TICKS_A_SECOND = 20;
    private static final List<Carried> CARRIED = new ArrayList<>();

    /** A particle carried, and the creature whose ship carries it. */
    private record Carried(Particle particle, Entity source) {
    }

    private CarriedParticles() {
    }

    /** Gives off a particle from a creature, carried on with the ship it rides, if any. */
    public static void spawn(Entity source, ParticleOptions options, Vec3 at, Vec3 speed) {
        boolean allShown = Minecraft.getInstance().options.particles().get() == ParticleStatus.ALL;
        if (!allShown || CARRIED.size() >= MOST || SableClient.ride(source) == null) {
            ShipParticles.plain(source, options, at, speed);
            return;
        }
        Particle particle = Minecraft.getInstance().particleEngine.createParticle(options, at.x, at.y, at.z, speed.x, speed.y, speed.z);
        if (particle != null) {
            CARRIED.add(new Carried(particle, source));
        }
    }

    /**
     * Each client tick, after the particles' own: every carried particle moved on as far as its ship moved, each
     * creature's ship looked up once however many particles it gave off.
     */
    public static void tick(ClientTickEvent.Post event) {
        CARRIED.removeIf(carried -> !carried.particle().isAlive() || carried.source().isRemoved());
        Map<Entity, Vec3> steps = new IdentityHashMap<>();
        for (Carried carried : CARRIED) {
            Vec3 step = steps.computeIfAbsent(carried.source(), CarriedParticles::step);
            carried.particle().move(step.x, step.y, step.z);
        }
    }

    /** How far the ship a creature rides moves under it in a tick, in blocks; nowhere off any ship. */
    private static Vec3 step(Entity source) {
        SableClient.Ride ride = SableClient.ride(source);
        if (ride == null) {
            return Vec3.ZERO;
        }
        Vector3d velocity = ride.velocity();
        return new Vec3(velocity.x, velocity.y, velocity.z).scale(1 / TICKS_A_SECOND);
    }
}
