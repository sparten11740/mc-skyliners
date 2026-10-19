package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A "fasten seatbelts" sign set into the cabin wall, lit by any redstone reaching it: the seatbelt switch on the flight
 * deck works the airliner's signs through a relay beside each. Lighting up, it dings through the cabin.
 */
public class SeatbeltSignBlock extends Block {
    private static final MapCodec<SeatbeltSignBlock> CODEC = simpleCodec(SeatbeltSignBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public SeatbeltSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(LIT, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        boolean lit = level.hasNeighborSignal(pos);
        if (lit != state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_CLIENTS);
            if (lit) {
                level.playSound(null, pos, AerolargeRegistry.SEATBELT_DING.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
    }
}
