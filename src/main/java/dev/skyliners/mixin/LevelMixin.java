package dev.skyliners.mixin;

import dev.skyliners.client.ShipWeather;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fair weather over the clouds, on the client (see {@link ShipWeather#seen}). The game rains and snows on a biome at
 * every height, so a ship flying high over a storm, a rocket on her way to space among them, was rained on all the
 * way up under a grey sky. No hook reaches the weather the client draws short of drawing the sky, the rain and the
 * light over again: here the client's rain level, which its rain and snow, their patter and the sky's greying in rain
 * and in a storm all go by, fades away as the camera climbs over the clouds' top, and comes back as it comes down into
 * them; the server's weather is left as it is. Listed among the client's mixins, it touches only the client's level: in
 * a game played alone the server's level is of the same class, and is left alone.
 */
@Mixin(Level.class)
abstract class LevelMixin {
    @Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
    private void skyliners$fairOverClouds(float delta, CallbackInfoReturnable<Float> info) {
        if ((Object) this instanceof ClientLevel client) {
            info.setReturnValue(info.getReturnValue() * ShipWeather.seen(client));
        }
    }
}
