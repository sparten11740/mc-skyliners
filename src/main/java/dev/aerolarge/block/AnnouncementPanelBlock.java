package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The flight deck's announcement panel: an overhead panel, {@link #COLUMNS} by {@link #ROWS} blocks, hung from the
 * ceiling over the centre console with its face looking down at the pilots' seats, a tile in each block for one cabin
 * announcement (see {@link Announcement}), in the order of a flight. Each tile is a picture first, so that the
 * youngest pilots, who cannot read yet, know what it says; pressing it makes the announcement (see
 * {@link AnnouncementPanelBlockEntity}). Every block of it is this block; PART says which, counting along the row
 * nearest the viewer from their left and then on, row by row, away from them, as the tiles read from the top; part 0
 * holds the block entity and draws the whole of it. FACING is the way whoever looks up at it faces, toward the nose.
 * REVERSED runs the rows the other way, the flight's first row furthest from the viewer: on a flight deck where the
 * panel reaches back behind the pilots' seats, that row hangs over the seats, within the seated pilots' reach.
 * Put up by hand against the underside of a block, it hangs there with the middle tile where it was put, facing the
 * way whoever puts it there faces, if there is room for all of it; every part goes up at once. Taking any part away
 * takes the whole panel, which drops one panel from part 0.
 */
public class AnnouncementPanelBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<AnnouncementPanelBlock> CODEC = simpleCodec(AnnouncementPanelBlock::new);
    public static final int COLUMNS = 3;
    public static final int ROWS = 3;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, COLUMNS * ROWS - 1);
    public static final BooleanProperty REVERSED = BooleanProperty.create("reversed");
    /**
     * How deep the panel hangs from the top of its blocks, in pixels: a quarter block, a slim panel close under the
     * ceiling, its face, the tiles, its underside.
     */
    public static final int DEPTH = 4;
    private static final VoxelShape SHAPE = Block.box(0, 16 - DEPTH, 0, 16, 16, 16);
    /** The middle part, which goes where the panel is put up. */
    private static final int MIDDLE = COLUMNS * ROWS / 2;

    public AnnouncementPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 0).setValue(REVERSED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, REVERSED);
    }

    /** The viewer's right, looking up at the panel facing this way. */
    public static Direction right(Direction facing) {
        return facing.getClockWise();
    }

    /** The announcement a part of the panel shows. */
    public static Announcement announcement(BlockState state) {
        int part = state.getValue(PART);
        return Announcement.values()[state.getValue(REVERSED) ? flipped(part) : part];
    }

    /** The part of the panel that shows an announcement. */
    public static int part(Announcement announcement, BlockState state) {
        return state.getValue(REVERSED) ? flipped(announcement.ordinal()) : announcement.ordinal();
    }

    /** The place on the panel with the rows the other way round. */
    private static int flipped(int part) {
        return (ROWS - 1 - part / COLUMNS) * COLUMNS + part % COLUMNS;
    }

    /** Where a part of the panel is, from the part holding it. */
    private static BlockPos part(BlockPos origin, Direction facing, int part) {
        return origin.relative(right(facing), part % COLUMNS).relative(facing, part / COLUMNS);
    }

    /** Where the part holding the panel is, from any part. */
    public static BlockPos origin(BlockState state, BlockPos pos) {
        int part = state.getValue(PART);
        Direction facing = state.getValue(FACING);
        return pos.relative(right(facing), -(part % COLUMNS)).relative(facing, -(part / COLUMNS));
    }

    /** Only against the underside of a block, with room under it for the whole panel round the spot. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (context.getClickedFace() != Direction.DOWN) {
            return null;
        }
        BlockState middle = defaultBlockState().setValue(FACING, context.getHorizontalDirection()).setValue(PART, MIDDLE);
        BlockPos origin = origin(middle, context.getClickedPos());
        Level level = context.getLevel();
        for (int part = 0; part < COLUMNS * ROWS; part++) {
            BlockPos at = part(origin, middle.getValue(FACING), part);
            if (level.isOutsideBuildHeight(at) || !level.getWorldBorder().isWithinBounds(at) || !level.getBlockState(at).canBeReplaced(context)) {
                return null;
            }
        }
        return middle;
    }

    /** The rest of the panel goes up round the middle part. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) {
            return;
        }
        BlockPos origin = origin(state, pos);
        for (int part = 0; part < COLUMNS * ROWS; part++) {
            if (part != MIDDLE) {
                level.setBlock(part(origin, state.getValue(FACING), part), state.setValue(PART, part), Block.UPDATE_ALL);
            }
        }
    }

    /** A part whose neighbour in the panel is gone goes too, so the panel is only ever whole. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        Direction facing = state.getValue(FACING);
        BlockPos origin = origin(state, pos);
        for (int part = 0; part < COLUMNS * ROWS; part++) {
            if (part(origin, facing, part).equals(neighbourPos)
                && !(neighbour.is(this) && neighbour.getValue(FACING) == facing && neighbour.getValue(PART) == part)) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /** Broken in creative mode, the parts it takes with it drop nothing either. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            BlockPos origin = origin(state, pos);
            for (int part = 0; part < COLUMNS * ROWS; part++) {
                BlockPos at = part(origin, state.getValue(FACING), part);
                if (!at.equals(pos) && level.getBlockState(at).is(this)) {
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Pressing a tile, on its face, asks for its announcement, on the server. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (hit.getDirection() != Direction.DOWN) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(origin(state, pos)) instanceof AnnouncementPanelBlockEntity panel) {
            panel.press(announcement(state), serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new AnnouncementPanelBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof AnnouncementPanelBlockEntity panel) {
                panel.tick();
            }
        };
    }
}
