package dev.aerolarge.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The airframe's plain skin, a whole panel: white plating outside, and lined on each face the blueprints find looking
 * into the airliner (see {@link Lining}). Put down by hand it is plated all round, as it always was.
 */
public class HullPanelBlock extends Block {
    public HullPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(Lining.plated(stateDefinition.any()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
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
}
