package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A box of folded paper planes on the zeppelin's Sky Deck, never empty, a paper plane painted on its front: using it
 * hands one over, with the rustle of paper, to throw from the top of the airship and watch glide down hundreds of
 * blocks (see {@link dev.aerolarge.entity.PaperPlane}). FACING is the way its front looks.
 */
public class PaperPlaneBoxBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<PaperPlaneBoxBlock> CODEC = simpleCodec(PaperPlaneBoxBlock::new);

    public PaperPlaneBoxBlock(Properties properties) {
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

    /** Put down by hand, its front looks at whoever puts it down. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.getInventory().placeItemBackInInventory(new ItemStack(AerolargeRegistry.PAPER_PLANE_ITEM.get()));
            level.playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1.0F, 1.2F);
            player.displayClientMessage(Component.translatable("message.aerolarge.paper_plane_box.handed"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
