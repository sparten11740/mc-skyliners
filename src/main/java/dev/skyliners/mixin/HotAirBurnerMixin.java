package dev.skyliners.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.skyliners.client.BurnerSmoke;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets each player's {@link BurnerSmoke} setting say how much smoke Create Aeronautics' hot air burner puffs. It puffs
 * it as smoke seen from any distance, which the game draws whatever its Particles setting says, so a zeppelin's many
 * burners kept some 1,600 puffs aloft at once, and Aeronautics has no setting of its own for it. Only the smoke is
 * touched, not the burner's embers, which follow the Particles setting already. Without Aeronautics, or with a
 * version whose burner puffs otherwise, nothing is changed.
 */
@Pseudo
@Mixin(targets = "dev.eriksonn.aeronautics.content.blocks.hot_air.hot_air_burner.HotAirBurnerBlockEntity")
abstract class HotAirBurnerMixin {
    @WrapWithCondition(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/Level;addAlwaysVisibleParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V"))
    private boolean skyliners$puffs(Level level, ParticleOptions particle, boolean ignoreRange, double x, double y, double z, double xSpeed,
        double ySpeed, double zSpeed) {
        return !level.isClientSide || particle != ParticleTypes.LARGE_SMOKE || BurnerSmoke.puffs(level.getRandom());
    }
}
