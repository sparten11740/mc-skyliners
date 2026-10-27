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
 * A zeppelin's speed boost button: a lit push-button (see {@link LitButtonBlock}) on the control car's front wall by
 * the rudder wheel, its red cap showing a fast-forward arrow, so it needs no reading. Pressed while her autopilot flies
 * her along, it fires the boosters round her tail a while, their plumes streaming behind her, holding her height and
 * the way she was going (see {@link BoostButtonBlockEntity}); at any other time, it sounds only a low note.
 */
public class BoostButtonBlock extends LitButtonBlock implements EntityBlock {
    public BoostButtonBlock(BlockSetType type, Properties properties) {
        super(type, properties);
    }

    @Override
    public void press(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        super.press(state, level, pos, player);
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof BoostButtonBlockEntity button) {
            button.press(server);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BoostButtonBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return !level.isClientSide && type == AerolargeRegistry.BOOST_BUTTON_ENTITY.get()
            ? (tickLevel, pos, tickState, button) -> ((BoostButtonBlockEntity) button).serverTick((ServerLevel) tickLevel)
            : null;
    }
}
