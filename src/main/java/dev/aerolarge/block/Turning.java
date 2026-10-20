package dev.aerolarge.block;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
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
 *
 * <p>Links saved before this, by an airliner already set down turned, lead nowhere. Each block entity with links mends
 * them where it first uses them after loading ({@link #mend}), once; links it was just handed need no mending.
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

    /**
     * Mends links saved before they were turned with their block, by an airliner already set down turned; on the server.
     * Looks where the offsets from {@code origin} lead as they are and at each other quarter turn of them, counting those
     * that lead to a block the test accepts; if one turn leads more of them there than every other, none included, it is
     * handed to {@code turn}, to turn all of the block entity's links by and save them. Nothing is guessed: where the
     * links lead right as they are, or two turns lead as many right, they stay as they are. A place in a chunk that is
     * not loaded leads nowhere, and is never loaded to be looked at.
     */
    public static void mend(Level level, BlockPos origin, Collection<BlockPos> offsets, Predicate<BlockState> right, Consumer<Rotation> turn) {
        if (level.isClientSide) {
            return;
        }
        int[] leading = new int[TURNS.length];
        for (int i = 0; i < TURNS.length; i++) {
            for (BlockPos offset : offsets) {
                BlockPos at = origin.offset(offset.rotate(TURNS[i]));
                if (level.isLoaded(at) && right.test(level.getBlockState(at))) {
                    leading[i]++;
                }
            }
            if (i == 0 && leading[0] == offsets.size()) {
                // All lead right as they are, as they almost always do: no need to look further.
                return;
            }
        }
        int best = 0;
        for (int i = 1; i < TURNS.length; i++) {
            if (leading[i] > leading[best]) {
                best = i;
            }
        }
        if (best == 0) {
            return;
        }
        for (int i = 1; i < TURNS.length; i++) {
            if (i != best && leading[i] == leading[best]) {
                return;
            }
        }
        turn.accept(TURNS[best]);
    }
}
