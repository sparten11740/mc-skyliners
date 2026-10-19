package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A cabin window with its shade: open, it lets the daylight in; pulled down, it shuts it out. The captain works every
 * shade at once from the flight deck (see {@link WindowShadesBlock}); anyone can open or close a window's own by hand.
 */
public class CabinWindowBlock extends TransparentBlock {
    private static final MapCodec<CabinWindowBlock> CODEC = simpleCodec(CabinWindowBlock::new);
    public static final BooleanProperty SHADED = BooleanProperty.create("shaded");

    public CabinWindowBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SHADED, false));
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHADED);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean shaded = !state.getValue(SHADED);
            level.setBlock(pos, state.setValue(SHADED, shaded), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.6F, shaded ? 0.9F : 1.2F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return !state.getValue(SHADED);
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(SHADED) ? level.getMaxLightLevel() : super.getLightBlock(state, level, pos);
    }
}
