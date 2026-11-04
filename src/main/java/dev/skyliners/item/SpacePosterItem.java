package dev.skyliners.item;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.SpacePoster;
import dev.skyliners.block.SpacePosterBlock;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

/**
 * A space poster rolled up (see {@link SpacePosterBlock}), its picture on it, to put up by hand: used on the side of a
 * block, the whole poster goes up against it, its middle in front of the block used, if every block of it has open air
 * to go in and a sturdy face behind it, with a painting's sound. Taken down, a poster gives this back.
 */
public class SpacePosterItem extends Item {
    private final SpacePoster poster;

    public SpacePosterItem(SpacePoster poster, Properties properties) {
        super(properties);
        this.poster = poster;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Direction facing = context.getClickedFace();
        BlockPos middle = context.getClickedPos().relative(facing);
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (facing.getAxis().isVertical()) {
            return InteractionResult.FAIL;
        }
        Level level = context.getLevel();
        Map<BlockPos, BlockState> pieces = SkylinersRegistry.SPACE_POSTER.get().pieces(poster, middle, facing);
        boolean allowed = player == null || pieces.keySet().stream().allMatch(at -> player.mayUseItemAt(at, facing, stack));
        if (!allowed || !pieces.keySet().stream().allMatch(at -> fits(level, at, facing))) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            pieces.forEach((at, piece) -> level.setBlock(at, piece, Block.UPDATE_ALL));
            level.playSound(null, middle, SoundEvents.PAINTING_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            level.gameEvent(player, GameEvent.BLOCK_PLACE, middle);
        }
        stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Whether a block of a poster facing one way can go up at a position: open air there and a sturdy face behind. */
    private static boolean fits(Level level, BlockPos at, Direction facing) {
        BlockPos behind = at.relative(facing.getOpposite());
        return !level.isOutsideBuildHeight(at) && level.getWorldBorder().isWithinBounds(at) && level.getBlockState(at).isAir()
            && level.getBlockState(behind).isFaceSturdy(level, behind, facing);
    }
}
