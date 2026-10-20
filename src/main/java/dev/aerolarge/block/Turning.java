package dev.aerolarge.block;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;

/**
 * Turning a block entity's links with its block. A link to another block of the airliner (the relays a control works,
 * the door a slide or the airstairs serve, the autopilot a screen works) is kept as an offset from the block entity, in
 * the world's directions, which holds while the airliner is assembled, flown and set down on the heading it was
 * assembled on. Create Aeronautics' Physics Assembler sets it down turned by the quarter turns it has flown round,
 * though, and Sable, moving each block there, turns its state but loads its block entity's data as it was saved: every
 * offset would still point the old way. So a block entity with links saves them with the way its block faced
 * ({@link #save}), and loaded into a block facing another way turns them by the difference ({@link #since}): in
 * Sable's move, as in any other that turns blocks and carries their data, such as a structure placed turned. Nothing
 * of Sable is needed for it, so it works with Sable or without.
 */
public final class Turning {
    /** The facing the links are saved with: the horizontal one every block with links has. */
    private static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The key the way the block faced is saved under, beside its links. */
    private static final String FRAME = "Frame";
    /** Every quarter turn, none first. */
    private static final Rotation[] TURNS = {Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90};

    private Turning() {
    }

    /** Saves the way the block faces beside its links, so they can be turned if it faces another way once loaded. */
    public static void save(CompoundTag tag, BlockState state) {
        if (state.hasProperty(FACING)) {
            tag.putString(FRAME, state.getValue(FACING).getSerializedName());
        }
    }

    /**
     * How far the block has turned since its links were saved: the quarter turn from the way it faced then to the way it
     * faces now. None when they were saved without the way it faced, as before links were turned.
     */
    public static Rotation since(CompoundTag tag, BlockState state) {
        Direction then = Direction.byName(tag.getString(FRAME));
        return then == null || !state.hasProperty(FACING) ? Rotation.NONE : between(then, state.getValue(FACING));
    }

    /** The quarter turn that turns one horizontal direction onto another; none for anything else. */
    static Rotation between(Direction from, Direction to) {
        for (Rotation turn : TURNS) {
            if (turn.rotate(from) == to) {
                return turn;
            }
        }
        return Rotation.NONE;
    }

    /** An offset turned; null stays null. */
    @Nullable
    public static BlockPos turn(@Nullable BlockPos offset, Rotation turn) {
        return offset == null ? null : offset.rotate(turn);
    }

    /** Offsets turned. */
    public static List<BlockPos> turn(List<BlockPos> offsets, Rotation turn) {
        return turn == Rotation.NONE ? offsets : offsets.stream().map(offset -> offset.rotate(turn)).toList();
    }
}
