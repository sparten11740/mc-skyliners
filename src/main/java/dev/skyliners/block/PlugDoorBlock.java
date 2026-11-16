package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
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
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A door in the hull's side, one panel over the whole doorway as an airliner's is: a plug door, which shuts from
 * inside against the frame like a plug in a hole, so it has to lift and move out of the frame before it can swing
 * away. Each block of the doorway is a part of it, by its COLUMN (counted left to right as seen from outside, so the
 * door is the same way round however the airliner is turned) and ROW (from the sill up); the part in the bottom left
 * corner keeps the door's block entity, which moves it (see {@link PlugDoorBlockEntity}) and is drawn by the client
 * with the whole panel. FACING is the way out of the hull, HINGE the edge it swings from. OPEN is whether the doorway
 * is clear: from the moment the door starts to open until it is shut again, so a closing door stops nobody until it
 * is back in its frame.
 *
 * <p>Using any part of it, from inside or out, or a redstone signal at any part, opens or shuts the whole door.
 * Villagers walk up to it as they would to a wooden door, and it opens for them while the airliner stands still (see
 * {@link PlugDoorBlockEntity}); none of them can open it in flight. Only the blueprints place it, so it has no item
 * and drops nothing.
 */
public class PlugDoorBlock extends BaseEntityBlock {
    private static final MapCodec<PlugDoorBlock> CODEC = simpleCodec(PlugDoorBlock::new);
    /** How many blocks wide the door is: two, so a jet bridge's tunnel, three wide inside, frames it. */
    public static final int COLUMNS = 2;
    /** How many blocks tall it may be: no taller than the jet bridge's tunnel, so the bridge frames it. */
    public static final int MOST_ROWS = 3;
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Hinge> HINGE = EnumProperty.create("hinge", Hinge.class);
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, COLUMNS - 1);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, MOST_ROWS - 1);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** How thick the panel is, in sixteenths of a block: it fills the outer quarter of the doorway, flush with the hull. */
    public static final int THICKNESS = 4;
    /**
     * How far the padded lining on its inside bulges out of the panel into the doorway at its crown, in sixteenths: as
     * far as anything on the door reaches, its handle too.
     */
    public static final int LINING = 7;
    /**
     * How thick the door is shut, panel and lining, in sixteenths: what stops anyone and keeps the daylight out, and how
     * far it reaches into the doorway, which leaves the doorway's inner quarter free.
     */
    public static final int DEPTH = THICKNESS + LINING;
    /** How thick the open doorway is to aim at and use, in sixteenths: a sliver across its outer face. */
    private static final int OPEN_FACE = 1;
    private static final Map<Direction, VoxelShape> SHUT = shapes(DEPTH);
    private static final Map<Direction, VoxelShape> CLEAR = shapes(OPEN_FACE);

    /** The edge of the door it swings from. */
    public enum Hinge implements StringRepresentable {
        /** Its left edge, seen from outside: it moves out of the frame and is carried round to the left on its arm. */
        LEFT("left"),
        /** Its right edge, seen from outside. */
        RIGHT("right"),
        /** Its top edge: it swings up and out, as a cargo door does. */
        TOP("top");

        private final String name;

        Hinge(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** The same edge seen in a mirror: left and right change places. */
        Hinge mirrored() {
            return this == LEFT ? RIGHT : this == RIGHT ? LEFT : TOP;
        }
    }

    public PlugDoorBlock(Properties properties) {
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

    /** Drawn whole, panel and all, by the door's block entity, wherever it is in its swing. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /** The panel while it is in the doorway; open, a sliver across the doorway's face, to aim at and shut it again. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(OPEN) ? CLEAR : SHUT).get(state.getValue(FACING));
    }

    /**
     * Shut, the panel keeps the daylight out of the cabin as the skin round it does: no light passes the doorway's
     * outer face, while the cabin's own light still reaches into the doorway and lights its sides. Open, the doorway
     * lets the light through and hides nothing behind it.
     */
    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return !state.getValue(OPEN);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(OPEN) ? Shapes.empty() : SHUT.get(state.getValue(FACING));
    }

    /**
     * Open, the doorway is clear: the panel stands outside beside it, where blocks of its own stop whoever walks into it
     * (see {@link ParkedPanelBlock}).
     */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(OPEN) ? Shapes.empty() : SHUT.get(state.getValue(FACING));
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return type != PathComputationType.WATER && state.getValue(OPEN);
    }

    /**
     * Shut, it is a door the way a wooden one is to anything finding its way: villagers walk up to it, and it opens
     * for them (see {@link PlugDoorBlockEntity}); anything that cannot open doors goes round.
     */
    @Override
    @Nullable
    public PathType getBlockPathType(BlockState state, BlockGetter level, BlockPos pos, @Nullable Mob mob) {
        return state.getValue(OPEN) ? null : PathType.DOOR_WOOD_CLOSED;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        PlugDoorBlockEntity door = PlugDoorBlockEntity.of(level, pos);
        if (door == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            door.use();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A redstone signal at any part of the door opens it, and it shuts once no part has one. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos neighborPos, boolean movedByPiston) {
        PlugDoorBlockEntity door = level.isClientSide ? null : PlugDoorBlockEntity.of(level, pos);
        if (door != null) {
            door.signal();
        }
    }

    /**
     * Taken apart on purpose (see {@link dev.skyliners.FrameProtection}), the whole door goes, not just the part, and
     * the blocks its open panel stands in with it.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        PlugDoorBlockEntity door = PlugDoorBlockEntity.of(level, pos);
        if (door != null) {
            door.unpark();
        }
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
        return isController(state) ? new PlugDoorBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, SkylinersRegistry.PLUG_DOOR_ENTITY.get(),
            level.isClientSide ? PlugDoorBlockEntity::clientTick : PlugDoorBlockEntity::serverTick);
    }

    public static boolean isDoor(BlockState state) {
        return state.getBlock() instanceof PlugDoorBlock;
    }

    /** Whether the state is a door's bottom left part, which keeps its block entity: one for each door. */
    public static boolean isController(BlockState state) {
        return isDoor(state) && state.getValue(COLUMN) == 0 && state.getValue(ROW) == 0;
    }

    /** Whether the state is a part of a door standing on the sill: the parts a jet bridge lines up with. */
    public static boolean isSill(BlockState state) {
        return isDoor(state) && state.getValue(ROW) == 0;
    }

    /** The way across the door from its left edge to its right, as seen from outside. */
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

    /** Picked, the airliner door that can be put up by hand: this one only goes up in a hull's doorway. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(SkylinersRegistry.AIRLINER_DOOR_ITEM.get());
    }
}
