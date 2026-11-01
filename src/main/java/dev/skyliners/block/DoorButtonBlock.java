package dev.skyliners.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * The door button, on the cabin wall beside every hull door: a lit push-button like the airstairs' (see
 * {@link LitButtonBlock}), its cap glowing sky blue with a door on it, so even someone who cannot read knows what it
 * does. Pressing it opens the nearest plug door if it is shut and shuts it if it is open, as using the door does (see
 * {@link PlugDoorBlockEntity#use}), in flight as on the ground: a steadier mark than the doorway itself while the cabin
 * decompresses through it and drags everyone near toward it (see {@code Decompression}). The door is found afresh
 * round the button at every press, so it is the right one however the airliner was turned, standing or assembled.
 *
 * <p>Unlike any other button it gives off no redstone signal: a plug door opens while any part of it has a signal and
 * shuts once none has, so a button's pulse would open it only for as long as the button stayed down. Put up by hand
 * within a few blocks of a plug door, it works that door.
 */
public class DoorButtonBlock extends LitButtonBlock {
    /**
     * How far round the button a door is looked for, in blocks across and up and down: from the cabin past the jamb it
     * is on to the door beside it, however thick the wall there.
     */
    private static final int REACH = 3;

    public DoorButtonBlock(BlockSetType type, Properties properties) {
        super(type, properties);
    }

    @Override
    public void press(BlockState state, Level level, BlockPos pos, @Nullable Player player) {
        super.press(state, level, pos, player);
        if (level.isClientSide) {
            return;
        }
        PlugDoorBlockEntity door = nearestDoor(level, pos);
        if (door != null) {
            door.use();
        }
    }

    /** The plug door with a part nearest the button, within reach; null where there is none. */
    @Nullable
    private static PlugDoorBlockEntity nearestDoor(Level level, BlockPos pos) {
        BlockPos nearest = null;
        for (BlockPos at : BlockPos.betweenClosed(pos.offset(-REACH, -REACH, -REACH), pos.offset(REACH, REACH, REACH))) {
            if (PlugDoorBlock.isDoor(level.getBlockState(at)) && (nearest == null || at.distSqr(pos) < nearest.distSqr(pos))) {
                nearest = at.immutable();
            }
        }
        return nearest == null ? null : PlugDoorBlockEntity.of(level, nearest);
    }

    /** It powers nothing, not even what it is on: it works its door itself. */
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
