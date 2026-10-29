package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Modern cabin lighting: a flat round shade lit from below, as bright as the flight deck's dimmer has it. It takes its
 * shape from where it is put up (MOUNT): under a ceiling or a light cable it hangs at the end of a slim cable, as the
 * blueprints hang it; on the floor it is a floor lamp, the same shade at the top of its block on a slim pole over a
 * weighted round base; and on a wall a wall sconce, the shade hanging from a short arm out from a wall plate, facing
 * out from the wall (FACING, which only a sconce shows).
 */
public class PendantLightBlock extends Block {
    private static final MapCodec<PendantLightBlock> CODEC = simpleCodec(PendantLightBlock::new);
    /** How it is put up; a pendant light saved before it had a mount was hung, and loads hanging. */
    public static final EnumProperty<Mount> MOUNT = Mount.property(Mount.CEILING, Mount.WALL, Mount.FLOOR);
    /** The way a wall sconce faces, out from its wall. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Hung: its cable, and its shade. */
    private static final VoxelShape HANGING = Shapes.or(Block.box(7.5, 8, 7.5, 8.5, 16, 8.5), Block.box(3, 4, 3, 13, 8, 13));
    /** A floor lamp: its round base, its pole and its shade at the top of the block. */
    private static final VoxelShape STANDING = Shapes.or(Block.box(4, 0, 6, 12, 1, 10), Block.box(5, 0, 5, 11, 1, 11),
        Block.box(6, 0, 4, 10, 1, 12), Block.box(6.5, 1, 6.5, 9.5, 1.5, 9.5), Block.box(7.5, 1.5, 7.5, 8.5, 12, 8.5),
        Block.box(3, 12, 3, 13, 16, 13));
    /** A wall sconce, by the way it faces: its wall plate, the arm out from it, the cable down and its shade. */
    private static final Map<Direction, VoxelShape> SCONCE = TurnedShapes.of(Shapes.or(Block.box(6.5, 10.5, 15, 9.5, 15.5, 16),
        Block.box(7.5, 12.5, 8.5, 8.5, 13.5, 15), Block.box(7.5, 8, 7.5, 8.5, 13.5, 8.5), Block.box(3, 4, 3, 13, 8, 13)));

    public PendantLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(Brightness.PROPERTY, Brightness.BRIGHT).setValue(Brightness.ALARM, false)
            .setValue(MOUNT, Mount.CEILING).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Brightness.PROPERTY, Brightness.ALARM, MOUNT, FACING);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    /**
     * Put up against what holds it (see {@link Mounting}): hung under a ceiling or a light cable, stood on the floor or
     * fixed to a wall, facing out from it; nowhere that nothing holds it.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction toward = Mounting.support(context, MOUNT.getPossibleValues());
        if (toward == null) {
            return null;
        }
        BlockState state = defaultBlockState().setValue(MOUNT, Mount.against(toward));
        return toward.getAxis().isHorizontal() ? state.setValue(FACING, toward.getOpposite()) : state;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(MOUNT)) {
            case CEILING -> HANGING;
            case FLOOR -> STANDING;
            case WALL -> SCONCE.get(state.getValue(FACING));
        };
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
