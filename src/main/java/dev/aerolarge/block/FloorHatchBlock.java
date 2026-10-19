package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TrapDoorBlock;

/**
 * The hatch in the cabin floor over the ladder down to the hold, so the drop bay can be reloaded in flight: a
 * trapdoor of the airliner's own make, its lid striped like the drop bay's doors with a ladder on it, opened by hand
 * from above or below with the doors' clunk and a puff of dust. Set into the floor's row and hinged at its top, it lies
 * flush with the floor when shut; open, it stands up at the ladder's side, and is climbed like the ladder under it.
 */
public class FloorHatchBlock extends TrapDoorBlock {
    private static final MapCodec<FloorHatchBlock> CODEC = simpleCodec(FloorHatchBlock::new);
    /** How many puffs of dust rise out of the opening as the hatch moves, and how far they spread, in blocks. */
    private static final int PUFFS = 6;
    private static final double SPREAD = 0.3;

    public FloorHatchBlock(Properties properties) {
        super(AerolargeRegistry.AIRLINER, properties);
    }

    @Override
    public MapCodec<? extends TrapDoorBlock> codec() {
        return CODEC;
    }

    /** Every time it opens or shuts, by hand or by redstone, the clunk comes with a puff of dust for everyone to see. */
    @Override
    protected void playSound(@Nullable Player player, Level level, BlockPos pos, boolean isOpened) {
        super.playSound(player, level, pos, isOpened);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, PUFFS, SPREAD, 0.1, SPREAD, 0.02);
        }
    }
}
