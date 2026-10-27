package dev.aerolarge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

/**
 * Leaves out a sound that starts too far from the player to be heard. Its volume has fallen to nothing there, but it
 * would still take one of the game's few hundred sound channels for as long as it plays. A zeppelin's eighty burners
 * each crackle now and then, however far away they are, and would take them all, so that the autopilot's bell, the
 * flight deck's voice and everything else nearby went unheard. Only a sound that plays once is left out, over in a
 * moment: one that loops may come within earshot while it plays.
 */
public final class Earshot {
    private Earshot() {
    }

    /** A sound is about to play: left out if it plays once and starts out of earshot. */
    public static void heard(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        Minecraft minecraft = Minecraft.getInstance();
        if (sound == null || minecraft.level == null || !playsOnce(sound) || sound.resolve(minecraft.getSoundManager()) == null) {
            return;
        }
        double reach = Math.max(sound.getVolume(), 1F) * sound.getSound().getAttenuationDistance();
        Vec3 at = SableClient.toWorld(minecraft.level, new Vec3(sound.getX(), sound.getY(), sound.getZ()));
        if (at.distanceToSqr(minecraft.gameRenderer.getMainCamera().getPosition()) > reach * reach) {
            event.setSound(null);
        }
    }

    /** Whether a sound plays once and fades out with distance, as a block's crackle does. */
    private static boolean playsOnce(SoundInstance sound) {
        return !sound.isRelative() && !sound.isLooping() && sound.getAttenuation() == SoundInstance.Attenuation.LINEAR;
    }
}
