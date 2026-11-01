package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The WC sign over a lavatory's door, set into the wall as the bulkhead signs are: a block of the bulkhead's cream
 * panelling with a lightbox in its face, behind the same brushed steel bezel, glowing whatever the light around (it
 * lights nothing). Its picture is a toilet, so it needs no reading: lit green while the WC is free, and lit red with
 * someone sitting on it while anyone is inside, a player or a villager alike, so the colour is not all it says (see
 * {@link WcSignBlockEntity} for how it knows). FACING is the way its face looks, out of the WC; put down, it faces
 * whoever puts it there.
 */
public class WcSignBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<WcSignBlock> CODEC = simpleCodec(WcSignBlock::new);
    public static final BooleanProperty OCCUPIED = BooleanProperty.create("occupied");

    public WcSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OCCUPIED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OCCUPIED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WcSignBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof WcSignBlockEntity sign) {
                sign.tick();
            }
        };
    }
}
