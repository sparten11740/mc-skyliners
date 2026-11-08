package dev.skyliners.client;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.HyperspaceSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * A freighter's hyperspace jump as those outside her hear it, on the ground or aboard another ship, far beyond where it
 * is seen (see {@link HyperspaceSound}): her charge whining up and throbbing faster as she lets go, until the air is
 * sucked in toward her, and a rush swelling where she is about to come out; then a deep whump as she goes, with a crack
 * and the air ripping back in to fill where she was, and where she comes out a thunderclap, close lightning's tearing
 * crack and a boom, thunder rolling on after it. Each is heard as late as sound takes to come from where it is made,
 * so far off the flash is seen first and the bang heard a second later; the bangs later again by as long as a client
 * shows her later than the server has her (see {@link HyperspaceWakes#SHOWN_LATER}), so near by each lands on its
 * flash. Further off than {@link #FAR_FROM}, each bang is heard as the air carries it there: duller, with less crack
 * and more rumble. How far each carries is set with its sound (see tools/generate_resources.mjs).
 */
public final class HyperspaceSounds {
    /** How fast sound travels, in blocks a tick: three hundred and forty-three metres a second. */
    private static final double SOUND_SPEED = 17.15;
    /** How far off a bang is heard as from far off, duller, in blocks: a little short of where the jump is seen. */
    private static final double FAR_FROM = 160;
    /**
     * How loud each sound is played: in full, as the game plays nothing louder; its loudness is in the sound itself, and
     * a volume over full would only stretch how far it carries, which is set with the sound.
     */
    private static final float VOLUME = 1.0F;

    private HyperspaceSounds() {
    }

    /** A jump's sound reaching this client: played where it is made, once sound has come that far. */
    public static void heard(HyperspaceSound sound) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Vec3 at = sound.at();
        double off = minecraft.gameRenderer.getMainCamera().getPosition().distanceTo(at);
        int late = (int) Math.round(off / SOUND_SPEED) + (sound.part().bang() ? HyperspaceWakes.SHOWN_LATER : 0);
        SimpleSoundInstance heard = new SimpleSoundInstance(of(sound.part(), off > FAR_FROM), SoundSource.BLOCKS, VOLUME, 1.0F, level.getRandom(),
            at.x, at.y, at.z);
        minecraft.getSoundManager().playDelayed(heard, late);
    }

    /** The sound of a part of the jump, its bangs as heard near by or far off. */
    private static SoundEvent of(HyperspaceSound.Part part, boolean far) {
        return switch (part) {
            case CHARGE -> SkylinersRegistry.FREIGHTER_HYPERSPACE_CHARGE.get();
            case WHUMP -> far ? SkylinersRegistry.FREIGHTER_HYPERSPACE_OUT_FAR.get() : SkylinersRegistry.FREIGHTER_HYPERSPACE_OUT.get();
            case RUSH -> SkylinersRegistry.FREIGHTER_HYPERSPACE_RUSH.get();
            case THUNDER -> far ? SkylinersRegistry.FREIGHTER_HYPERSPACE_IN_FAR.get() : SkylinersRegistry.FREIGHTER_HYPERSPACE_IN.get();
        };
    }
}
