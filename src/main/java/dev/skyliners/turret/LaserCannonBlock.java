package dev.skyliners.turret;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The freighter's laser cannon (see {@link LaserCannonBlockEntity}): a gunmetal pedestal on the crown of her top dome,
 * its block's own model, standing its swivel clear of the glass so its gunner's sight over it sees past her rim, with
 * twin barrels on the swivel that the client's {@code LaserCannonRenderer} draws turning to its gunner's aim. FACING is
 * the way her bow is, which its barrels rest pointing and its gunner's sight shows them by: she builds it facing her
 * bow, and one set down by hand faces the way whoever set it down looked. Set down by hand by any seat, it works there
 * too.
 */
public class LaserCannonBlock extends BaseEntityBlock {
    private static final MapCodec<LaserCannonBlock> CODEC = simpleCodec(LaserCannonBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /**
     * Its pedestal, to stand on and aim at, a column up the middle of its block, and over it the housing on its swivel,
     * as far round as it reaches whichever way it is turned, short of its barrels, which swing out past its block.
     */
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 16, 13), Block.box(1, 16, 1, 15, 22, 15));

    public LaserCannonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Facing the way whoever sets it down looks, its bow ahead of them. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserCannonBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != SkylinersRegistry.LASER_CANNON_ENTITY.get()) {
            return null;
        }
        return level.isClientSide ? (tickLevel, pos, tickState, cannon) -> ((LaserCannonBlockEntity) cannon).clientTick()
            : (tickLevel, pos, tickState, cannon) -> ((LaserCannonBlockEntity) cannon).serverTick((ServerLevel) tickLevel);
    }
}
