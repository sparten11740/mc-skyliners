package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * A zeppelin's boarding stairs' push-button: a lit push-button (see {@link LitButtonBlock}), its amber cap showing the
 * airliner's airstairs' staircase, so it needs no reading. One stands on the lower deck's floor at the head of each
 * flight of boarding stairs, keeping that flight (see {@link BoardingStairsBlockEntity}), and one low on the outside of
 * the control car, to call them down from the ground. Pressed, it folds every flight of its zeppelin's boarding stairs
 * away into her belly, or brings them down again; with her moving, it only sounds a low note.
 */
public class BoardingStairsButtonBlock extends LitButtonBlock implements EntityBlock {
    public BoardingStairsButtonBlock(BlockSetType type, Properties properties) {
        super(type, properties);
    }

    @Override
    public void press(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        super.press(state, level, pos, player);
        if (level instanceof ServerLevel server) {
            BoardingStairsBlockEntity.press(server, pos);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoardingStairsBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != AerolargeRegistry.BOARDING_STAIRS_ENTITY.get()) {
            return null;
        }
        return level.isClientSide
            ? (tickLevel, pos, tickState, stairs) -> ((BoardingStairsBlockEntity) stairs).clientTick()
            : (tickLevel, pos, tickState, stairs) -> ((BoardingStairsBlockEntity) stairs).serverTick((ServerLevel) tickLevel);
    }
}
