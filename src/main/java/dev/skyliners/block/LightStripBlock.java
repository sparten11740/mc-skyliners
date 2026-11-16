package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A strip of LED light along the top of a wall, where it meets the ceiling: the cabin's modern indirect lighting.
 * FACING is the wall it is fixed to; strips side by side run on as one. As bright as the flight deck's dimmer has it,
 * or red for the alarm. The cabin's strip has no item of its own: only the blueprints put it up, and broken or picked
 * it gives an LED strip, which a player lays on any face (see {@link LedStripLineBlock}). The fairy lights are this
 * block too, with an item of their own, put up by hand on a wall.
 */
public class LightStripBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<LightStripBlock> CODEC = simpleCodec(LightStripBlock::new);
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
        Direction.NORTH, Block.box(0, 12, 0, 16, 15, 3),
        Direction.SOUTH, Block.box(0, 12, 13, 16, 15, 16),
        Direction.WEST, Block.box(0, 12, 0, 3, 15, 16),
        Direction.EAST, Block.box(13, 12, 0, 16, 15, 16)
    );

    public LightStripBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(Brightness.PROPERTY, Brightness.BRIGHT).setValue(Brightness.ALARM, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, Brightness.PROPERTY, Brightness.ALARM);
    }

    /** Put up by hand only on a wall, fixed to it: set down on a floor, it stood in the air at the top of its block. */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction wall = Mounting.support(context, List.of(Mount.WALL));
        return wall == null ? null : defaultBlockState().setValue(FACING, wall);
    }

    /** The cabin's strip picked, an LED strip, which lays a strip as a player lays one; fairy lights, themselves. */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return state.is(SkylinersRegistry.LIGHT_STRIP.get()) ? new ItemStack(SkylinersRegistry.LED_STRIP_ITEM.get())
            : super.getCloneItemStack(state, target, level, pos, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }
}
