package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Hidden in each nacelle just ahead of a thruster: gives off the redstone signal its engine's thrust lever is set
 * to, so the thrusters, which fire as hard as the strongest signal next to them, follow the lever.
 */
public class ThrustRelayBlock extends Block {
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    public ThrustRelayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWER, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }

    /**
     * Picked, the thrust lever whose signal it gives off: the relay itself only goes up with a ship's engines, wired as
     * she is built.
     */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(SkylinersRegistry.THRUST_LEVER_ITEM.get());
    }
}
