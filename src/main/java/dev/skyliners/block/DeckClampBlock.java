package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A docking clamp set into a zeppelin's flight deck: a block of the deck with a pair of yellow jaws lying flat in it
 * and a lamp between them, red while it is open. A plane come to rest on the deck round it is locked down by it (see
 * {@link DeckClampBlockEntity}): the jaws close up with a clang and the lamp turns green, and its tanks are kept full.
 * CLOSED is whether its jaws are closed on a plane.
 */
public class DeckClampBlock extends Block implements EntityBlock {
    public static final BooleanProperty CLOSED = BooleanProperty.create("closed");

    public DeckClampBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(CLOSED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CLOSED);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DeckClampBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != SkylinersRegistry.DECK_CLAMP_ENTITY.get()) {
            return null;
        }
        return (world, pos, blockState, clamp) -> ((DeckClampBlockEntity) clamp).tick((ServerLevel) world);
    }
}
