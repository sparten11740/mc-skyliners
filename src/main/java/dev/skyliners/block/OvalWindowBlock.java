package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A big oval window in the rocket's skin, a few blocks wide and tall, each block a piece of it (see
 * {@link OvalWindowPieceBlock}), its glass bulging a little out of the skin. Each piece is the inner half of its block,
 * where the skin's cut runs through the block's middle out to the four ways round a round body, so it sits flush with
 * the skin beside it. It turns with the ship as she is built facing any way. Inside, the wall round its oval is her
 * cover's weave in her livery, or, in a room finished its own way, that room's wall (see {@link RocketFinish}), so the
 * oval sits in the wall as the room has it. Broken, a piece of hers gives back glass.
 *
 * <p>It is also set into any wall by hand, from its item or picked from one in creative mode, and then looks out toward
 * whoever sets it, as a painting does ({@link Wall#MATCHED}); the same item lays it in a floor or a roof (see
 * {@link FlatOvalWindowBlock}). Set by hand, each piece fills its whole block, flush with the wall on both sides, its
 * glass standing out of the wall's face in its collar as hers stands out of her skin.
 */
public class OvalWindowBlock extends OvalWindowPieceBlock {
    private static final MapCodec<OvalWindowBlock> CODEC = simpleCodec(OvalWindowBlock::new);
    /** The way the window looks out. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** What the wall round its oval is inside: her cover, or a room's finish. */
    public static final EnumProperty<Wall> WALL = EnumProperty.create("wall", Wall.class);

    /**
     * The wall round a window's oval inside: her cover's weave, painted by her livery, or one of the rooms' finishes.
     */
    public enum Wall implements StringRepresentable {
        COVER, ENTRY, CARGO, MESS, BUNKS, GREENHOUSE, POOL, CLUB, LAB, LOUNGE, COCKPIT,
        /** Set into a wall by hand: round its oval, outside and in, it looks like the wall beside it. */
        MATCHED;

        /** The wall of a room finished its own way; the cover's for a finish no window is set in, as the dome's. */
        public static Wall of(RocketFinish finish) {
            for (Wall wall : values()) {
                if (wall.name().equals(finish.name())) {
                    return wall;
                }
            }
            return COVER;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
    /** Her pieces' shapes, the inner half of the block, by the way they look out. */
    private static final VoxelShape LOOKING_NORTH = Block.box(0, 0, 8, 16, 16, 16);
    private static final VoxelShape LOOKING_SOUTH = Block.box(0, 0, 0, 16, 16, 8);
    private static final VoxelShape LOOKING_EAST = Block.box(0, 0, 0, 8, 16, 16);
    private static final VoxelShape LOOKING_WEST = Block.box(8, 0, 0, 16, 16, 16);

    public OvalWindowBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLUMN, 0).setValue(ROW, 0)
            .setValue(DIM, 0).setValue(WALL, Wall.COVER));
    }

    @Override
    protected MapCodec<? extends TransparentBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLUMN, ROW, DIM, WALL);
    }

    /** A piece of a window looking out one way, by its column from the left as seen from outside and its row. */
    public BlockState piece(Direction facing, int column, int row) {
        return defaultBlockState().setValue(FACING, facing).setValue(COLUMN, column).setValue(ROW, row);
    }

    @Override
    public Direction out(BlockState piece) {
        return piece.getValue(FACING);
    }

    @Override
    public Direction up(BlockState piece) {
        return Direction.UP;
    }

    @Override
    public boolean setByHand(BlockState piece) {
        return piece.getValue(WALL) == Wall.MATCHED;
    }

    /**
     * Set by hand: the piece for the spot that fits the window best into an opening round it, standing in a wall
     * looking out toward whoever sets it, or lying flat in a floor or a roof (see {@link FlatOvalWindowBlock#lying}),
     * standing first unless they look up or down; null where the window has no room either way.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState standing = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(WALL, Wall.MATCHED);
        BlockState flat = SkylinersRegistry.FLAT_OVAL_WINDOW.get().lying(context);
        boolean steep = context.getNearestLookingDirection().getAxis().isVertical();
        return bestFit(context, steep ? List.of(flat, standing) : List.of(standing, flat));
    }

    /** A window turns with the ship it is set in, as she is built facing another way. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** Mirrored, a window looks out the mirrored way, and its columns from the left run the other way round. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING))).setValue(COLUMN, COLUMNS - 1 - state.getValue(COLUMN));
    }

    /** Hers the inner half of the block; one set by hand the whole block. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (setByHand(state)) {
            return Shapes.block();
        }
        return switch (state.getValue(FACING)) {
            case SOUTH -> LOOKING_SOUTH;
            case EAST -> LOOKING_EAST;
            case WEST -> LOOKING_WEST;
            default -> LOOKING_NORTH;
        };
    }

    /** One set by hand is solid over the glass standing out of it too. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return setByHand(state) ? solidWithDome(this, state) : getShape(state, level, pos, context);
    }
}
