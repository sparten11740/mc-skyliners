package dev.skyliners.client;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.SpaceRockBlock;
import dev.skyliners.spaceweather.RockShape;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The colour of a space rock's stone (see {@link SpaceRockBlock}): its kind of stone, patched lighter and darker across
 * the rock and a little from block to block, so no rock is one even colour and no two blocks side by side quite match.
 */
public final class SpaceRockTint {
    /** How much brighter than its stone's colour its pale skin is tinted, to come out that colour. */
    private static final double SKIN_BRIGHTER = 1.3;
    /**
     * How much lighter or darker its patches make a block, as a share, and how many patches to the block; and how much
     * lighter or darker a block may be by itself.
     */
    private static final double PATCHES = 0.24;
    private static final double PATCHES_PER_BLOCK = 0.16;
    private static final double BLOCK_BY_BLOCK = 0.07;
    /** What is mixed into the seed of its patches, and of a block's own shade. */
    private static final long PATCH_SEED = 0x7A3C91L;
    private static final long BLOCK_SEED = 0x2F18D5L;
    private static final int WHITE = 0xFFFFFF;

    private SpaceRockTint() {
    }

    /** The colour of a block of space rock's skin, its stone's patched where it lies. */
    public static int tint(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        boolean magma = state.is(SkylinersRegistry.SPACE_ROCK_MAGMA.get());
        int colour = magma ? WHITE : RockShape.STONES[state.getValue(SpaceRockBlock.STONE)];
        double scale = (magma ? 1 : SKIN_BRIGHTER) * (pos == null ? 1 : shadeAt(pos));
        return channel(colour >> 16, scale) << 16 | channel(colour >> 8, scale) << 8 | channel(colour, scale);
    }

    /** How much lighter or darker a block is where it lies among its rock. */
    private static double shadeAt(BlockPos pos) {
        double patch = RockShape.noise(pos.getX() * PATCHES_PER_BLOCK, pos.getY() * PATCHES_PER_BLOCK, pos.getZ() * PATCHES_PER_BLOCK, PATCH_SEED) * 2 - 1;
        double block = RockShape.noise(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, BLOCK_SEED) * 2 - 1;
        return 1 + PATCHES * patch + BLOCK_BY_BLOCK * block;
    }

    private static int channel(int value, double scale) {
        return Math.min(255, (int) Math.round((value & 0xFF) * scale));
    }
}
