package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The thrust relay set into the cabin wall beside a lamp it lights (a seatbelt sign, a jump light): passed off as the
 * skin around it, plated outside and lined inside like a whole panel (see {@link Lining}).
 */
public class HullRelayBlock extends ThrustRelayBlock {
    public HullRelayBlock(Properties properties) {
        super(properties);
        registerDefaultState(Lining.plated(defaultBlockState()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        Lining.define(builder);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return Lining.rotate(state, rotation);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return Lining.mirror(state, mirror);
    }

    /** Picked, the airframe panel it is passed off as. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(SkylinersRegistry.AIRFRAME_PANEL_ITEM.get());
    }
}
