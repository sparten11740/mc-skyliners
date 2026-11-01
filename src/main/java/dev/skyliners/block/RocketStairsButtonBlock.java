package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A rocket's stairs button: a lit push-button (see {@link LitButtonBlock}), its amber cap showing the airliner's
 * airstairs' staircase, as a zeppelin's stairs button does, so it needs no reading. One stands inside by her front
 * door on her right, keeping her airstairs (see {@link RocketStairsBlockEntity}), and one low on the front of her
 * front right fin, to call them down from the ground. Pressed, it folds her stairs away into her or brings them down
 * again; while she flies, it only sounds a low note.
 */
public class RocketStairsButtonBlock extends LitButtonBlock implements EntityBlock {
    public RocketStairsButtonBlock(BlockSetType type, Properties properties) {
        super(type, properties);
    }

    @Override
    public void press(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        super.press(state, level, pos, player);
        if (level instanceof ServerLevel server) {
            RocketStairsBlockEntity.press(server, pos);
        }
    }

    /**
     * Used while it is in, it does nothing more but let go again in a moment, in case the tick it waits on was lost,
     * as Sable's assembly can lose one (see {@link LostTicks}).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(POWERED) && level instanceof ServerLevel server) {
            LostTicks.rearm(server, pos, this, 1);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RocketStairsBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != SkylinersRegistry.ROCKET_STAIRS_ENTITY.get()) {
            return null;
        }
        return level.isClientSide
            ? (tickLevel, pos, tickState, stairs) -> ((RocketStairsBlockEntity) stairs).clientTick()
            : (tickLevel, pos, tickState, stairs) -> ((RocketStairsBlockEntity) stairs).serverTick((ServerLevel) tickLevel);
    }
}
