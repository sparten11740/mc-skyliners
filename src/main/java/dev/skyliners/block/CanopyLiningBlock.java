package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The freighter's canopy as it is seen (see {@link CanopyLiningBlockEntity}): an unseen block in the air of her
 * cockpit, near the tip of her canopy, whose renderer draws the whole canopy, inside and out, as one smooth dome of
 * tinted glass framed by slim ribs, where her glass, which draws nothing, would otherwise show its blocks stepping
 * round her curve. It has no shape to bump into or aim at and nothing to pick or break; only her build lays it, since
 * it means nothing outside a canopy, and taking her apart takes it with her. FACING is the way her canopy points, her
 * bow.
 */
public class CanopyLiningBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<CanopyLiningBlock> CODEC = simpleCodec(CanopyLiningBlock::new);

    public CanopyLiningBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Drawn by its renderer alone, the whole canopy at once. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Nothing to aim at or bump into: the pilots look through where it stands. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CanopyLiningBlockEntity(pos, state);
    }
}
