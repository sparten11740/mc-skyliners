package dev.skyliners.block;

import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A block whose state says which of the blocks round it it joins on to (a mat's piping, a bench's legs, a ladder's
 * top), worked out from them alone: the same as it is put down, as they change round it, and as a blueprint lays it
 * out, where nothing updates its neighbours.
 */
public interface Joining {
    /** The state joined to what is round it, given the block beside it each way. */
    BlockState joined(BlockState state, Function<Direction, BlockState> beside);
}
