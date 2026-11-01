package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The lab's growers beside its bioreactors, in two kinds, each two blocks tall: a hydroponic rack, four shelves in a
 * steel frame, each a white trough of lettuces or herbs under a bar of violet grow light, so the rack glows violet and
 * green; and an algae column, a tall glass tube of bright green algae glowing in its own light, bubbles of air rising
 * through it from the steel foot to the cap. FACING is the way the rack's shelves look out; the column is round.
 */
public class LabGrowerBlock extends LabMachineBlock {
    private static final MapCodec<LabGrowerBlock> RACK_CODEC = simpleCodec(properties -> new LabGrowerBlock(properties, Kind.HYDROPONIC_RACK));
    private static final MapCodec<LabGrowerBlock> COLUMN_CODEC = simpleCodec(properties -> new LabGrowerBlock(properties, Kind.ALGAE_COLUMN));
    /** How brightly each glows, in light levels: the rack's grow lights, and the column's algae, a little softer. */
    public static final int RACK_GLOW = 10;
    public static final int COLUMN_GLOW = 9;
    /** The rack's frame drawn facing north, its shelves open to the front, and the column's tube and its foot and cap. */
    private static final Map<Direction, VoxelShape> RACK = TurnedShapes.of(Block.box(1, 0, 3, 15, 16, 13));
    private static final VoxelShape COLUMN = Block.box(3, 0, 3, 13, 16, 13);

    /** Which grower it is. */
    public enum Kind {
        HYDROPONIC_RACK,
        ALGAE_COLUMN
    }

    private final Kind kind;

    public LabGrowerBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return kind == Kind.HYDROPONIC_RACK ? RACK_CODEC : COLUMN_CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return kind == Kind.HYDROPONIC_RACK ? RACK.get(state.getValue(FACING)) : COLUMN;
    }
}
