package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The cockpit door in the bulkhead behind the flight deck: one big panel two blocks wide and three or four tall, its
 * top corners rounded and a round porthole in it, which swings out of the flight deck into the aisle behind it about
 * one side, as a room door does. Each block of the doorway is a part of it, by its COLUMN (counted left to right as
 * seen from the aisle it swings into) and ROW (from the floor up); the part in the bottom left corner keeps the door's
 * block entity, which swings it (see {@link CockpitDoorBlockEntity}) and is drawn by the client with the whole panel.
 * FACING is the way it swings out, HINGE the side it swings about. OPEN is whether the doorway is clear: from the
 * moment the door starts to open until it is shut again.
 *
 * <p>Using any part of it, from either side, opens or shuts the whole door. Villagers do not open it: the passengers
 * have no business on the flight deck. Only the blueprints place it, so it has no item and drops nothing.
 */
public class CockpitDoorBlock extends BaseEntityBlock {
    private static final MapCodec<CockpitDoorBlock> CODEC = simpleCodec(CockpitDoorBlock::new);
    public static final int COLUMNS = 2;
    /** How many blocks tall it may be: four, where the flight deck has the headroom. */
    public static final int MOST_ROWS = 4;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Hinge> HINGE = EnumProperty.create("hinge", Hinge.class);
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, COLUMNS - 1);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, MOST_ROWS - 1);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** How thick the panel is, in sixteenths of a block: a reinforced door, against the doorway's outer face. */
    public static final int THICKNESS = 3;
    /** How thick the open doorway is to aim at and use, in sixteenths: a sliver across its outer face. */
    private static final int OPEN_FACE = 1;
    private static final Map<Direction, VoxelShape> SHUT = shapes(THICKNESS);
    private static final Map<Direction, VoxelShape> CLEAR = shapes(OPEN_FACE);

    /** The side of the door it swings about, seen from the aisle it swings into. */
    public enum Hinge implements StringRepresentable {
        LEFT("left"),
        RIGHT("right");

        private final String name;

        Hinge(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        Hinge mirrored() {
            return this == LEFT ? RIGHT : LEFT;
        }
    }

    public CockpitDoorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
            .setValue(FACING, Direction.NORTH).setValue(HINGE, Hinge.LEFT).setValue(COLUMN, 0).setValue(ROW, 0).setValue(OPEN, false));
    }

    /** The panel's box in a block for each way out, this many sixteenths thick against the block's outer face. */
    private static Map<Direction, VoxelShape> shapes(int thick) {
        return Map.of(
            Direction.NORTH, Block.box(0, 0, 0, 16, 16, thick),
            Direction.SOUTH, Block.box(0, 0, 16 - thick, 16, 16, 16),
            Direction.WEST, Block.box(0, 0, 0, thick, 16, 16),
            Direction.EAST, Block.box(16 - thick, 0, 0, 16, 16, 16)
        );
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HINGE, COLUMN, ROW, OPEN);
    }

    /** Drawn whole by the door's block entity, wherever it is in its swing. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** The panel while it is in the doorway; open, a sliver across the doorway's face, to aim at and shut it again. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(OPEN) ? CLEAR : SHUT).get(state.getValue(FACING));
    }

    /** Open, the doorway is clear: the panel stands back against the aisle's side. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : SHUT.get(state.getValue(FACING));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type != PathComputationType.WATER && state.getValue(OPEN);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        CockpitDoorBlockEntity door = CockpitDoorBlockEntity.of(level, pos);
        if (door == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            door.use();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Taken apart on purpose (see {@link dev.aerolarge.FrameProtection}), the whole door goes, not just the part. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            for (BlockPos part : parts(level, controller(state, pos))) {
                if (!part.equals(pos)) {
                    level.setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    /** Mirrored, the door faces the mirrored way and its left and right change places: its columns and its hinge. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)))
            .setValue(COLUMN, COLUMNS - 1 - state.getValue(COLUMN))
            .setValue(HINGE, state.getValue(HINGE).mirrored());
    }

    /** Only the bottom left part keeps the door's block entity. */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isController(state) ? new CockpitDoorBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, AerolargeRegistry.COCKPIT_DOOR_ENTITY.get(),
            level.isClientSide ? CockpitDoorBlockEntity::clientTick : CockpitDoorBlockEntity::serverTick);
    }

    public static boolean isDoor(BlockState state) {
        return state.getBlock() instanceof CockpitDoorBlock;
    }

    /** Whether the state is a door's bottom left part, which keeps its block entity: one for each door. */
    public static boolean isController(BlockState state) {
        return isDoor(state) && state.getValue(COLUMN) == 0 && state.getValue(ROW) == 0;
    }

    /** The way across the door from its left edge to its right, as seen from the aisle it swings into. */
    public static Direction right(BlockState state) {
        return state.getValue(FACING).getCounterClockWise();
    }

    /** Where the door's bottom left part is, from any part of it. */
    public static BlockPos controller(BlockState part, BlockPos pos) {
        return pos.relative(right(part), -part.getValue(COLUMN)).below(part.getValue(ROW));
    }

    /** How many rows tall the door with its bottom left part here is: as many as stand one on the other. */
    public static int rows(BlockGetter level, BlockPos controller) {
        BlockState bottom = level.getBlockState(controller);
        int rows = 0;
        while (rows < MOST_ROWS && isPart(level.getBlockState(controller.above(rows)), bottom, 0, rows)) {
            rows++;
        }
        return rows;
    }

    /** Where the parts of the door with its bottom left part here stand, row by row. */
    public static List<BlockPos> parts(BlockGetter level, BlockPos controller) {
        BlockState bottom = level.getBlockState(controller);
        List<BlockPos> parts = new ArrayList<>();
        if (!isController(bottom)) {
            return parts;
        }
        Direction right = right(bottom);
        for (int row = 0; row < MOST_ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                BlockPos at = controller.relative(right, column).above(row);
                if (isPart(level.getBlockState(at), bottom, column, row)) {
                    parts.add(at);
                }
            }
        }
        return parts;
    }

    /** Whether the state is the part of the same door as the bottom left part at this column and row. */
    private static boolean isPart(BlockState state, BlockState bottom, int column, int row) {
        return isDoor(state) && state.getValue(FACING) == bottom.getValue(FACING) && state.getValue(HINGE) == bottom.getValue(HINGE)
            && state.getValue(COLUMN) == column && state.getValue(ROW) == row;
    }
}
