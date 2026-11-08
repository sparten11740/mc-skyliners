package dev.skyliners.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.skyliners.client.ShipWeather;
import javax.annotation.Nullable;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps rain and snow out of ships (see {@link ShipWeather}). Sable draws an assembled airframe's blocks where she
 * flies, but the game still lets rain and snow fall down each column to the world's own highest block, which knows
 * nothing of hers, so it rained and snowed in her cabins and rooms. No hook reaches the height a column's rain falls to
 * short of drawing all of the game's rain over again: here the height read for each column, as the rain is drawn and as
 * it splashes, is raised to just over a ship's roof over it, and a splash is left out where it would land on the
 * freighter's canopy.
 */
@Mixin(LevelRenderer.class)
abstract class LevelRendererMixin {
    @Shadow
    @Nullable
    private ClientLevel level;

    @Inject(method = "renderSnowAndRain", at = @At("HEAD"))
    private void skyliners$lookForShips(LightTexture lightTexture, float partialTick, double camX, double camY, double camZ, CallbackInfo info) {
        if (level != null) {
            ShipWeather.look(level, new Vec3(camX, camY, camZ), partialTick);
        }
    }

    @WrapOperation(method = "renderSnowAndRain",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getHeight(Lnet/minecraft/world/level/levelgen/Heightmap$Types;II)I"))
    private int skyliners$fallOnShips(Level drawn, Heightmap.Types type, int x, int z, Operation<Integer> original) {
        return ShipWeather.fallsTo(drawn, x, z, original.call(drawn, type, x, z));
    }

    @Inject(method = "tickRain", at = @At("HEAD"))
    private void skyliners$lookForShipsSplashing(Camera camera, CallbackInfo info) {
        if (level != null) {
            ShipWeather.look(level, camera.getPosition(), 1.0F);
        }
    }

    @WrapOperation(method = "tickRain", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/level/LevelReader;getHeightmapPos(Lnet/minecraft/world/level/levelgen/Heightmap$Types;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/core/BlockPos;"))
    private BlockPos skyliners$splashOnShips(LevelReader reader, Heightmap.Types type, BlockPos pos, Operation<BlockPos> original) {
        BlockPos ground = original.call(reader, type, pos);
        return level == null ? ground : ground.atY(ShipWeather.fallsTo(level, ground.getX(), ground.getZ(), ground.getY()));
    }

    @WrapOperation(method = "tickRain", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private void skyliners$splashOffCanopies(ClientLevel splashed, ParticleOptions splash, double x, double y, double z, double dx, double dy, double dz,
                                             Operation<Void> original) {
        if (ShipWeather.splashes(splashed, x, y, z)) {
            original.call(splashed, splash, x, y, z, dx, dy, dz);
        }
    }
}
