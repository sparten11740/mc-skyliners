package dev.aerolarge.client;

import dev.aerolarge.AerolargeClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;

/**
 * How much smoke Create Aeronautics' hot air burners puff as they burn, as each player sets it in the client config.
 * Aeronautics puffs it as smoke seen from any distance, which the game draws whatever its Particles setting says, so
 * a full-size zeppelin's burners kept some 1,600 puffs aloft at once and cost every player near her a share of each
 * frame however few particles they had asked for (see {@link dev.aerolarge.mixin.HotAirBurnerMixin}).
 */
public enum BurnerSmoke {
    /** As much as the game's Particles setting allows, as other particles: all, fewer at Decreased, none at Minimal. */
    PARTICLES,
    /** Every puff, whatever the Particles setting, as Aeronautics puffs them. */
    ALL,
    /** None. */
    NONE;

    /** At the Particles setting Decreased, one puff in so many is left out, as the game leaves out its own. */
    private static final int DECREASED_LEAVES_OUT = 3;

    /** Whether a burner on this client puffs the smoke it is about to. */
    public static boolean puffs(RandomSource random) {
        return switch (AerolargeClientConfig.BURNER_SMOKE.get()) {
            case PARTICLES -> byParticles(random);
            case ALL -> true;
            case NONE -> false;
        };
    }

    private static boolean byParticles(RandomSource random) {
        return switch (Minecraft.getInstance().options.particles().get()) {
            case ALL -> true;
            case DECREASED -> random.nextInt(DECREASED_LEAVES_OUT) != 0;
            case MINIMAL -> false;
        };
    }
}
