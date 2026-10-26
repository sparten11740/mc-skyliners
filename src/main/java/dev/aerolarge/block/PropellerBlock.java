package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * The hub of a zeppelin's propeller, at the tail of its engine car: a relay like the others there, passing its
 * engine's signal on to the thrusters round it that push the zeppelin along, and the propeller its renderer draws
 * behind it, four long wooden blades that spin up and down with that signal (see {@link PropellerBlockEntity}).
 * FACING is the way it pulls the zeppelin, the blades turning behind it.
 */
public class PropellerBlock extends ThrustRelayBlock implements EntityBlock {
    private static final MapCodec<PropellerBlock> CODEC = simpleCodec(PropellerBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public PropellerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    /** Placed by hand, it pulls the way the player looks, the blades turning toward them. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    /**
     * Placed by hand, it takes the strongest redstone signal round it, from a lever or a relay, and spins with that;
     * not another propeller's, so two side by side never hold each other spinning. On a zeppelin its relays beside it
     * carry its engine's signal, as it does itself.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos from, boolean moving) {
        if (level.isClientSide) {
            return;
        }
        int signal = 0;
        for (Direction side : Direction.values()) {
            BlockPos next = pos.relative(side);
            if (!(level.getBlockState(next).getBlock() instanceof PropellerBlock)) {
                signal = Math.max(signal, level.getSignal(next, side));
            }
        }
        if (signal != state.getValue(POWER)) {
            level.setBlock(pos, state.setValue(POWER, signal), Block.UPDATE_ALL);
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PropellerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide && type == AerolargeRegistry.PROPELLER_ENTITY.get()
            ? (tickLevel, pos, tickState, propeller) -> ((PropellerBlockEntity) propeller).clientTick()
            : null;
    }
}
