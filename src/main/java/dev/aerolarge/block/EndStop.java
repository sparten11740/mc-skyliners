package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

/**
 * A control pushed past its last notch, a ship's wheel, a telegraph, a burner lever or a winch's lever at its stop: a
 * dull thud, so whoever works it, reading or not, hears it will go no further.
 */
final class EndStop {
    /** How loud the thud is. */
    private static final float VOLUME = 0.6F;

    private EndStop() {
    }

    /** The thud of a control at its stop, on the server, heard by everyone near. */
    static void thud(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            level.playSound(null, pos, AerolargeRegistry.CONTROL_STOP.get(), SoundSource.BLOCKS, VOLUME, 1.0F);
        }
    }
}
