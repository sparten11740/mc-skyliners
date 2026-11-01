package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A whole block of the rocket's cover where a room sees it from inside: painted by her livery on every face but those
 * a room sees, which are lined in that room's {@link RocketFinish} (see {@link Lining}). Only her blueprint places it;
 * broken, it gives back the fabric her cover is made from.
 */
public class RocketLiningPanelBlock extends Block {
    public RocketLiningPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(Lining.plated(stateDefinition.any()).setValue(RocketFinish.PROPERTY, RocketFinish.ENTRY));
    }

    /** The panel lined in a room's finish on one more side. */
    public static BlockState linedOn(BlockState panel, Direction side) {
        return panel.setValue(Lining.side(side), true);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        Lining.define(builder);
        builder.add(RocketFinish.PROPERTY);
    }

    /** Picked, the fabric her cover is made from, as her cover gives. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(SkylinersRegistry.ZEPPELIN_FABRIC.get());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return Lining.rotate(state, rotation);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return Lining.mirror(state, mirror);
    }
}
