package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.radio.Broadcasts;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The captain's handset on the flight deck: a cradle with the receiver in it. Picked up, the next line is a cabin
 * announcement; picked up while sneaking, a radio call to the tower.
 */
public class HandsetBlock extends RadioBlock {
    private static final MapCodec<HandsetBlock> CODEC = simpleCodec(HandsetBlock::new);
    /** A module of the console with the cradle and its receiver on it. */
    private static final Map<Mount, Map<Direction, VoxelShape>> SHAPES = MountedModule.shapes(ConsoleModule.with(5, 4));

    public HandsetBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(MountedModule.MOUNT, Mount.FLOOR));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MountedModule.MOUNT);
    }

    /** Against the block clicked, as a button goes: on a floor, on a wall or under a ceiling (see {@link MountedModule}). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return MountedModule.place(context, defaultBlockState());
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected Broadcasts.Kind kind(Player player) {
        return player.isShiftKeyDown() ? Broadcasts.Kind.RADIO : Broadcasts.Kind.ANNOUNCEMENT;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return MountedModule.shape(SHAPES, state);
    }
}
