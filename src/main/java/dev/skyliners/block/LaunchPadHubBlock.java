package dev.skyliners.block;

import dev.skyliners.compat.SableFlight;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The steel hub at the very middle of a launch pad's grate, a yellow cross on it, under a rocket's axis as she stands
 * on the pad (see {@link LaunchPadBlockEntity}). Its item puts one up by hand, which marks a spot for her on its own: a
 * pad no wider than the hub itself, its front toward whoever put it down.
 */
public class LaunchPadHubBlock extends Block implements EntityBlock {
    public LaunchPadHubBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaunchPadBlockEntity(pos, state);
    }

    /**
     * Put up by hand, on the server, its front is toward the player who put it down, so a rocket set down over it has
     * her door to them as the blueprint's pad has hers to whoever built it; it covers only itself, there being no deck
     * round it. The way they face is turned into the frame of a ship it is put up aboard; clients are sent the hub.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide || placer == null || !(level.getBlockEntity(pos) instanceof LaunchPadBlockEntity hub)) {
            return;
        }
        Vec3 facing = SableFlight.turnToPlot(level, pos, Vec3.atLowerCornerOf(placer.getDirection().getNormal()));
        hub.laid(0, Direction.getNearest(facing.x, 0, facing.z));
        hub.setChanged();
    }
}
