package dev.skyliners.entity;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Particles a creature gives off, on the client, carried on with the ship it rides (see the client's {@code
 * CarriedParticles}): a creature aboard a flying ship moves with her, and so should the bubbles, crumbs and sleepy "z"s
 * it gives off, which the game itself leaves standing in the air where they were born, trailing away behind her. Common
 * code gives them off through {@link #give}; the client sets {@link #spawner} so they are carried, and without it (or
 * with no ship) they are given off as the game gives off any particle.
 */
public final class ShipParticles {
    /** Gives off one particle from a creature, at a point in the world, at a speed in blocks a tick. */
    @FunctionalInterface
    public interface Spawner {
        void spawn(Entity source, ParticleOptions options, Vec3 at, Vec3 speed);
    }

    /** How particles are given off: as the game gives them off, until the client sets a way that carries them. */
    public static Spawner spawner = ShipParticles::plain;

    private ShipParticles() {
    }

    /** Gives off a particle from a creature, carried on with any ship it rides. */
    public static void give(Entity source, ParticleOptions options, Vec3 at, Vec3 speed) {
        spawner.spawn(source, options, at, speed);
    }

    /** A particle given off as the game gives off any, standing in the air where it was born. */
    public static void plain(Entity source, ParticleOptions options, Vec3 at, Vec3 speed) {
        source.level().addParticle(options, at.x, at.y, at.z, speed.x, speed.y, speed.z);
    }
}
