package dev.skyliners.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A lift's call button, on the shaft's wall beside a landing's doors: a lit push-button (see {@link LitButtonBlock}),
 * its cap a pale steel with an arrow up and an arrow down on it, as a lift's call button has. Pressing it calls the
 * car to its landing: it comes if it is elsewhere and its doors open, for whoever called it to step in and pick their
 * floor on the board inside (see {@link LiftMachineBlockEntity#call}); while it is still on its way, or busy with
 * another ride first, the button stays in and its cap glows amber, and it lets go once the car is there. It gives off
 * no redstone signal: it calls its lift itself.
 */
public class LiftCallButtonBlock extends LitButtonBlock implements EntityBlock {
    /** While the car is called here, how often the button looks again whether it has come, in ticks. */
    private static final int CALLED_CHECK_TICKS = 10;
    /** The low note of a call the lift does not take: as loud as the lift board's, and its pitch. */
    private static final float BUSY_VOLUME = 0.5F;
    private static final float BUSY_PITCH = 0.6F;

    public LiftCallButtonBlock(BlockSetType type, Properties properties) {
        super(type, properties);
    }

    /** Pressed, it calls its car; a low note where the lift takes no calls, parked while the ship flies. */
    @Override
    public void press(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        super.press(state, level, pos, player);
        if (level instanceof ServerLevel server && server.getBlockEntity(pos) instanceof LiftCallButtonBlockEntity button) {
            LiftMachineBlockEntity lift = button.lift();
            if (lift != null && !lift.call(server, button.landing())) {
                server.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, BUSY_VOLUME, BUSY_PITCH);
            }
        }
    }

    /**
     * Used while it is in, it does nothing more but look again whether its car has come, in case the tick it waits on
     * was lost, as Sable's assembly can lose one (see {@link LostTicks}).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(POWERED) && level instanceof ServerLevel server) {
            LostTicks.rearm(server, pos, this, 1);
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    /** It stays in, glowing, until the car it called has come; then it lets go as a button does. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED) && called(level, pos)) {
            level.scheduleTick(pos, this, CALLED_CHECK_TICKS);
            return;
        }
        super.tick(state, level, pos, random);
    }

    /** Whether the car is still called to the button's landing, on its way there or waiting to come. */
    private static boolean called(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof LiftCallButtonBlockEntity button)) {
            return false;
        }
        LiftMachineBlockEntity lift = button.lift();
        return lift != null && lift.coming(button.landing());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LiftCallButtonBlockEntity(pos, state);
    }

    /** It powers nothing, not even what it is on: it calls its lift itself. */
    @Override
    protected boolean isSignalSource(BlockState state) {
        return false;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }
}
