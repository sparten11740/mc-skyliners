package dev.skyliners.client;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.FogMachineBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A fog machine's hiss heard from this client: played once as it begins to pump, five seconds long as its pump is, at
 * its block, so that on a ship in flight Sable carries it along as it does her engines' sounds (see
 * {@link EngineSounds}). The client plays it, not the server, so that it stops the moment its machine is gone from this
 * client, however it went: a sound the server sends cannot be stopped for one machine alone, and a ship taken away
 * whole never removes her blocks one by one on the server.
 */
public final class FogMachineSounds {
    private FogMachineSounds() {
    }

    /** A machine begins to pump, as this client sees it: its hiss starts. */
    public static void hiss(FogMachineBlockEntity machine) {
        Minecraft.getInstance().getSoundManager().play(new Hiss(machine));
    }

    /** One pump's hiss, which ends by itself as the pump does, or at once if its machine goes. */
    private static final class Hiss extends AbstractTickableSoundInstance {
        private final FogMachineBlockEntity machine;

        Hiss(FogMachineBlockEntity machine) {
            super(SkylinersRegistry.FOG_HISS.get(), SoundSource.BLOCKS, RandomSource.create());
            this.machine = machine;
            Vec3 centre = Vec3.atCenterOf(machine.getBlockPos());
            this.x = centre.x;
            this.y = centre.y;
            this.z = centre.z;
        }

        @Override
        public void tick() {
            if (machine.isRemoved()) {
                stop();
            }
        }
    }
}
