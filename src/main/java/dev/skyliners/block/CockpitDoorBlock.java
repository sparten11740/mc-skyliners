package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
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
 * one side, as a room door does. The doorway is three blocks wide, so it stands in the middle of the aisle: the panel
 * in its middle, half a block of frame either side of it. Each block of the doorway is a part of it, by its COLUMN
 * (counted left to right as seen from the aisle it swings into) and ROW (from the floor up); the part in the bottom
 * left corner keeps the door's block entity, which swings it (see {@link CockpitDoorBlockEntity}) and is drawn by the
 * client with the whole panel. FACING is the way it swings out, HINGE the side it swings about. OPEN is whether the
 * doorway is clear: from the moment the door starts to open until it is shut again. FRAMED is whether the doorway has
 * its frame: doors built before it had one are two blocks wide, the panel right across them, and stay so.
 *
 * <p>Using any part of it, from either side, opens or shuts the whole door. Sneaking and using it from the flight deck
 * locks or unlocks it; locked, it does not open from the aisle, though it still shuts from there, and opens from the
 * flight deck as ever. Villagers do not open it: the passengers have no business on the flight deck. Only the
 * blueprints place it, so it has no item and drops nothing.
 */
public class CockpitDoorBlock extends BaseEntityBlock {
    private static final MapCodec<CockpitDoorBlock> CODEC = simpleCodec(CockpitDoorBlock::new);
    /** How many blocks wide the doorway is: the panel's two and its frame's half a block either side. */
    public static final int COLUMNS = 3;
    /** How many blocks wide the panel is, and so a doorway without its frame. */
    public static final int PANEL_WIDTH = 2;
    /** How many blocks tall it may be: four, where the flight deck has the headroom. */
    public static final int MOST_ROWS = 4;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Hinge> HINGE = EnumProperty.create("hinge", Hinge.class);
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, COLUMNS - 1);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, MOST_ROWS - 1);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty FRAMED = BooleanProperty.create("framed");
    /** How thick the panel is, in sixteenths of a block: a reinforced door, against the doorway's outer face. */
    public static final int THICKNESS = 3;
    /** How thick the open doorway is to aim at and use, in sixteenths: a sliver across its outer face. */
    private static final int OPEN_FACE = 1;
    /** How wide the frame is either side of the panel, in sixteenths: half a block. */
    private static final int FRAME = 8;
    /** The parts' shapes for each way out: a part with no frame in it, one with the frame on its left, on its right. */
    private static final Map<Direction, PartShapes[]> SHAPES = shapes();

    /**
     * A part's shapes: shut, the panel and any frame; open, to aim at, the frame and a sliver across the rest of the
     * doorway's outer face, to shut it again by; and open, to bump into, only the frame.
     */
    private record PartShapes(VoxelShape shut, VoxelShape open, VoxelShape clear) {
    }

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
            .setValue(FACING, Direction.NORTH).setValue(HINGE, Hinge.LEFT).setValue(COLUMN, 0).setValue(ROW, 0).setValue(OPEN, false)
            .setValue(FRAMED, false));
    }

    private static Map<Direction, PartShapes[]> shapes() {
        Map<Direction, PartShapes[]> shapes = new EnumMap<>(Direction.class);
        for (Direction out : Direction.Plane.HORIZONTAL) {
            shapes.put(out, new PartShapes[]{
                partShapes(out, 0, 16, Shapes.empty()),
                partShapes(out, FRAME, 16, box(out, 0, 0, FRAME, 16)),
                partShapes(out, 0, 16 - FRAME, box(out, 16 - FRAME, 0, 16, 16))
            });
        }
        return shapes;
    }

    /** The shapes of a part with this frame in it and the panel across it from x0 to x1, in sixteenths from its left. */
    private static PartShapes partShapes(Direction out, int x0, int x1, VoxelShape frame) {
        return new PartShapes(
            Shapes.or(frame, box(out, x0, 16 - THICKNESS, x1, 16)),
            Shapes.or(frame, box(out, x0, 16 - OPEN_FACE, x1, 16)),
            frame);
    }

    /**
     * A box the height of the block from (x0, z0) to (x1, z1), in sixteenths turned with the door: x across to the
     * right as seen from the aisle it swings into, z out toward the aisle, the doorway's outer face at z 16.
     */
    private static VoxelShape box(Direction out, int x0, int z0, int x1, int z1) {
        Direction right = out.getCounterClockWise();
        int ax = 8 + (x0 - 8) * right.getStepX() + (z0 - 8) * out.getStepX();
        int az = 8 + (x0 - 8) * right.getStepZ() + (z0 - 8) * out.getStepZ();
        int bx = 8 + (x1 - 8) * right.getStepX() + (z1 - 8) * out.getStepX();
        int bz = 8 + (x1 - 8) * right.getStepZ() + (z1 - 8) * out.getStepZ();
        return Block.box(Math.min(ax, bx), 0, Math.min(az, bz), Math.max(ax, bx), 16, Math.max(az, bz));
    }

    /** The part's shapes: with the frame in it at either edge of a framed doorway. */
    private static PartShapes partShapes(BlockState state) {
        int column = state.getValue(COLUMN);
        int kind = 0;
        if (state.getValue(FRAMED) && column == 0) {
            kind = 1;
        } else if (state.getValue(FRAMED) && column == COLUMNS - 1) {
            kind = 2;
        }
        return SHAPES.get(state.getValue(FACING))[kind];
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HINGE, COLUMN, ROW, OPEN, FRAMED);
    }

    /** Drawn whole by the door's block entity, wherever it is in its swing. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** The panel while it is in the doorway; open, a sliver across the doorway's face, to aim at and shut it again. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        PartShapes shapes = partShapes(state);
        return state.getValue(OPEN) ? shapes.open() : shapes.shut();
    }

    /** Open, the doorway is clear but for its frame: the panel stands back against the aisle's side. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        PartShapes shapes = partShapes(state);
        return state.getValue(OPEN) ? shapes.clear() : shapes.shut();
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
            press(door, state, player, hit);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Used, on the server: sneaking from the flight deck, it locks or unlocks; locked and shut, from the aisle, it
     * rattles; else it opens or shuts.
     */
    private static void press(CockpitDoorBlockEntity door, BlockState state, Player player, BlockHitResult hit) {
        boolean inside = fromFlightDeck(state, hit);
        if (inside && player.isShiftKeyDown()) {
            door.toggleLock(player);
        } else if (door.locked() && !inside && !door.opening()) {
            door.rattle(player);
        } else {
            door.use();
        }
    }

    /** Whether the door was used from the flight deck: on a face looking back into it, away from the aisle. */
    private static boolean fromFlightDeck(BlockState state, BlockHitResult hit) {
        return hit.getDirection() == state.getValue(FACING).getOpposite();
    }

    /** Taken apart on purpose (see {@link dev.skyliners.FrameProtection}), the whole door goes, not just the part. */
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
            .setValue(COLUMN, columns(state) - 1 - state.getValue(COLUMN))
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
        return createTickerHelper(type, SkylinersRegistry.COCKPIT_DOOR_ENTITY.get(),
            level.isClientSide ? CockpitDoorBlockEntity::clientTick : CockpitDoorBlockEntity::serverTick);
    }

    public static boolean isDoor(BlockState state) {
        return state.getBlock() instanceof CockpitDoorBlock;
    }

    /** Whether the state is a door's bottom left part, which keeps its block entity: one for each door. */
    public static boolean isController(BlockState state) {
        return isDoor(state) && state.getValue(COLUMN) == 0 && state.getValue(ROW) == 0;
    }

    /** How many blocks wide the door this is a part of is: with its frame, or one built before it had one. */
    public static int columns(BlockState state) {
        return state.getValue(FRAMED) ? COLUMNS : PANEL_WIDTH;
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
            for (int column = 0; column < columns(bottom); column++) {
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
            && state.getValue(FRAMED) == bottom.getValue(FRAMED) && state.getValue(COLUMN) == column && state.getValue(ROW) == row;
    }

    /** Picked, the airliner door that can be put up by hand: this one only goes up in a flight deck's bulkhead. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(SkylinersRegistry.AIRLINER_DOOR_ITEM.get());
    }
}
