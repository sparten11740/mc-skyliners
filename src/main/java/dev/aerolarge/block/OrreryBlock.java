package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The brass orrery hanging from the tip of the rocket's nose, over her cockpit (see {@code airframe.RocketDome}): this
 * block is its mount, a brass rose against her skin, and its block entity draws the rest on the clients, a brass rod
 * down to a glowing sun and the planets circling it on their arms. Placed only by the blueprint, so it has no item and
 * gives nothing back; taken down, it takes the soft light round its sun with it.
 */
public class OrreryBlock extends Block implements EntityBlock {
    private static final MapCodec<OrreryBlock> CODEC = simpleCodec(OrreryBlock::new);
    /** The mount, a brass rose at the top of its block. */
    private static final VoxelShape MOUNT = Block.box(5, 14, 5, 11, 16, 11);

    public OrreryBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return MOUNT;
    }

    /** Taken down, but not carried off with her blocks as she is assembled: the light round its sun goes too. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !moved && level.getBlockEntity(pos) instanceof OrreryBlockEntity orrery) {
            BlockPos sun = pos.below(Mth.floor(orrery.drop()));
            if (level.getBlockState(sun).is(Blocks.LIGHT)) {
                level.removeBlock(sun, false);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OrreryBlockEntity(pos, state);
    }
}
