package dev.skyliners.block;

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.MapCodec;
import dev.skyliners.cabin.CurtainParting;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A curtain to hang across the cabin between its rooms: cloth in pleats, drawn shut, or gathered open into a bunch at
 * each end with a band round its foot. Pieces side by side and one above another join into one curtain, hung from a
 * steel rail along its top; using any piece draws the whole curtain open or shut, a column at a time away from the one
 * used, and using a dye on it dyes all of it. It is only cloth, open or shut: everyone and everything walks, rolls and
 * flies straight through it, and it keeps no light out. Shut, it parts round whoever walks through it and falls shut
 * behind them (see {@link CurtainParting}).
 *
 * <p>FACING is the way its front faces, toward whoever hung it, and the curtain hangs across that way: in the plane
 * through the middle of its block, square to FACING. LEFT and RIGHT say it carries on into the neighbour to the left
 * or right in that plane as seen from behind, looking the way it faces (for a curtain facing north, west and east);
 * UP and DOWN into the one above or below. Only pieces facing the same way join.
 */
public class CurtainBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<CurtainBlock> CODEC = simpleCodec(CurtainBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final BooleanProperty LEFT = BooleanProperty.create("left");
    public static final BooleanProperty RIGHT = BooleanProperty.create("right");
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    /** One cloth, drawn in grey, tinted the dye's colour on the client. */
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    /**
     * The most pieces one use draws or dyes: more than a curtain right across the cabin of the largest airliner, while
     * a player's sprawling build of them is worked a part at a time.
     */
    private static final int MOST_PIECES = 1024;
    /** How many ticks apart neighbouring columns follow one another as the curtain is drawn. */
    private static final int TICKS_PER_COLUMN = 2;
    /**
     * The outlines, in pixels, drawn for a curtain facing north, round only what its model draws: shut, the cloth
     * through the middle of the block, its hem a pixel off the floor; open, the rail and its rings along the top of the
     * curtain's top pieces and a bunch of gathered cloth at each end. An open piece between the ends and under the top
     * draws nothing and has no outline, so the crosshair passes through an open curtain as the eye does; it is drawn
     * shut by its gathered ends or its rail.
     */
    private static final double CLOTH_FRONT = 6.5;
    private static final double CLOTH_BACK = 9.5;
    private static final double HEM = 1;
    private static final double RAIL_BOTTOM = 14;
    private static final double RAIL_FRONT = 7;
    private static final double RAIL_BACK = 9;
    private static final double BUNCH_WIDTH = 4.5;
    private static final double BUNCH_FRONT = 5.5;
    private static final double BUNCH_BACK = 10.5;

    /** Each state's outline. */
    private final ImmutableMap<BlockState, VoxelShape> shapes;

    public CurtainBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(LEFT, false)
            .setValue(RIGHT, false).setValue(UP, false).setValue(DOWN, false).setValue(COLOR, DyeColor.WHITE));
        shapes = getShapeForEachState(CurtainBlock::outline);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, LEFT, RIGHT, UP, DOWN, COLOR);
    }

    /** Whether the state is a piece of curtain. */
    public static boolean isCurtain(BlockState state) {
        return state.getBlock() instanceof CurtainBlock;
    }

    /** The colour the client tints the cloth with: its dye's. The rail, rings and band are not tinted. */
    public static int tint(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        return state.getValue(COLOR).getTextureDiffuseColor();
    }

    /** The colour the client tints a curtain in hand or in an inventory with: the colour it was taken down in. */
    public static int itemTint(ItemStack stack, int tintIndex) {
        DyeColor color = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(COLOR);
        return (color != null ? color : DyeColor.WHITE).getTextureDiffuseColor();
    }

    /**
     * A curtain in the piece's colour, the way one broken down comes back (the loot table copies the colour the same
     * way): the colour rides along as the block state to hang it in, and white, the cloth's own, is left off, so white
     * curtains stack with new ones.
     */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (state.getValue(COLOR) != DyeColor.WHITE) {
            stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(COLOR, state.getValue(COLOR)));
        }
        return stack;
    }

    /**
     * Someone in a piece of curtain, on the server: it parts for them. Anything living does: a ball or a trolley only
     * goes through. Called for a ship's blocks too, by Sable, with the piece's position among them, and in a position
     * the game goes on to change, so what is kept of it is a copy.
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level instanceof ServerLevel server && entity instanceof LivingEntity && !entity.isSpectator()) {
            CurtainParting.walkedInto(server, pos.immutable(), state);
        }
    }

    /** The way to a curtain's left, in its plane, seen looking the way it faces. */
    public static Direction leftOf(Direction facing) {
        return facing.getCounterClockWise();
    }

    /**
     * The piece joined to whichever of its neighbours carry on the same curtain, from their states: for a layout,
     * whose blocks are set without updating one another.
     */
    public static BlockState joinedTo(BlockState piece, BlockState left, BlockState right, BlockState above, BlockState below) {
        return piece.setValue(LEFT, sameCurtain(piece, left)).setValue(RIGHT, sameCurtain(piece, right))
            .setValue(UP, sameCurtain(piece, above)).setValue(DOWN, sameCurtain(piece, below));
    }

    /** Whether the neighbour is a piece of curtain hanging the same way, so the two make one. */
    private static boolean sameCurtain(BlockState piece, BlockState neighbor) {
        return isCurtain(neighbor) && neighbor.getValue(FACING) == piece.getValue(FACING);
    }

    /**
     * Hung beside, under or over a curtain in its plane (the block clicked first), it carries that curtain on, the
     * same way round, colour and drawn the same; anywhere else it hangs across the way its hanger looks, shut. A
     * curtain that came down in a colour goes back up in it, whatever it carries on (see {@link #getCloneItemStack}).
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockState piece = defaultBlockState().setValue(FACING, facing);
        Direction clicked = context.getClickedFace().getOpposite();
        for (Direction way : new Direction[]{clicked, Direction.UP, Direction.DOWN, leftOf(facing), leftOf(facing).getOpposite()}) {
            BlockState next = level.getBlockState(pos.relative(way));
            if (isCurtain(next) && inPlane(next.getValue(FACING), way)) {
                piece = piece.setValue(FACING, next.getValue(FACING)).setValue(OPEN, next.getValue(OPEN)).setValue(COLOR, next.getValue(COLOR));
                break;
            }
        }
        return joinedTo(piece, level.getBlockState(pos.relative(leftOf(piece.getValue(FACING)))),
            level.getBlockState(pos.relative(leftOf(piece.getValue(FACING)).getOpposite())), level.getBlockState(pos.above()),
            level.getBlockState(pos.below()));
    }

    /** Whether a neighbour that way lies in the plane of a curtain facing so: beside it, above or below it. */
    private static boolean inPlane(Direction facing, Direction way) {
        return way.getAxis() != facing.getAxis();
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        BooleanProperty join = joinToward(state, direction);
        return join != null ? state.setValue(join, sameCurtain(state, neighbor)) : state;
    }

    /** The property saying whether the piece carries on into its neighbour that way; null across its plane. */
    private static BooleanProperty joinToward(BlockState piece, Direction way) {
        Direction left = leftOf(piece.getValue(FACING));
        if (way == left) {
            return LEFT;
        }
        if (way == left.getOpposite()) {
            return RIGHT;
        }
        return way == Direction.UP ? UP : way == Direction.DOWN ? DOWN : null;
    }

    /** Whether the piece carries on into its neighbour that way: another piece of the same curtain, joined to it. */
    private static boolean carriesOn(BlockState piece, Direction way, BlockState neighbor) {
        BooleanProperty join = joinToward(piece, way);
        return join != null && piece.getValue(join) && sameCurtain(piece, neighbor);
    }

    /** The curtain the piece belongs to, piece by piece, nearest first. */
    private static List<BlockPos> pieces(BlockGetter level, BlockPos pos) {
        return JoinedBlocks.from(level, pos, MOST_PIECES, CurtainBlock::carriesOn);
    }

    /**
     * A dye dyes the whole curtain, using one up (unless in creative mode); another curtain in hand hangs beside or
     * under it; anything else draws it.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is(asItem())) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(stack.getItem() instanceof DyeItem dye)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        DyeColor color = dye.getDyeColor();
        List<BlockPos> undyed = pieces(level, pos).stream().filter(at -> level.getBlockState(at).getValue(COLOR) != color).toList();
        if (undyed.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            for (BlockPos at : undyed) {
                level.setBlock(at, level.getBlockState(at).setValue(COLOR, color), Block.UPDATE_CLIENTS);
            }
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Draws the curtain open if it was shut, or shut if it was open: the column used at once, the rest following it a
     * column at a time out to the curtain's ends, each column on one scheduled tick (see {@link #tick}). A curtain
     * still being drawn is left to finish first.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            List<BlockPos> curtain = pieces(level, pos);
            forgetParted(level, curtain);
            if (curtain.stream().noneMatch(at -> level.getBlockTicks().hasScheduledTick(at, this))) {
                boolean open = !state.getValue(OPEN);
                Direction.Axis along = leftOf(state.getValue(FACING)).getAxis();
                Set<BlockPos> drawn = new HashSet<>();
                for (BlockPos at : curtain) {
                    if (drawn.contains(at)) {
                        continue;
                    }
                    List<BlockPos> column = column(level, at);
                    drawn.addAll(column);
                    int columns = Math.abs(at.get(along) - pos.get(along));
                    if (columns == 0) {
                        draw(level, column, open);
                    } else {
                        Direction back = Direction.fromAxisAndDirection(along,
                            pos.get(along) > at.get(along) ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
                        level.scheduleTick(followingFrom(level, column, back), this, columns * TICKS_PER_COLUMN);
                    }
                }
                swish(level, pos);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A curtain drawn by hand is its drawer's to shut: {@link CurtainParting} no longer shuts what it parted of it. */
    private static void forgetParted(Level level, List<BlockPos> curtain) {
        if (level instanceof ServerLevel server) {
            CurtainParting.forget(server, curtain);
        }
    }

    /**
     * A column's turn as the curtain is drawn. The column on its side toward the one used has just been drawn, and the
     * one beyond it, further out, is still as this one is, so the whole column follows whichever neighbour is drawn
     * differently. Each column is drawn on the one tick of one of its pieces: were every piece to tick, those after the
     * first would see the column further out still drawn the old way and draw theirs back.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction left = leftOf(state.getValue(FACING));
        for (Direction way : new Direction[]{left, left.getOpposite()}) {
            BlockState next = level.getBlockState(pos.relative(way));
            if (carriesOn(state, way, next) && next.getValue(OPEN) != state.getValue(OPEN)) {
                draw(level, column(level, pos), next.getValue(OPEN));
                return;
            }
        }
    }

    /** The column of pieces through the position: it and every piece above and below it it is joined to. */
    private static List<BlockPos> column(Level level, BlockPos pos) {
        JoinedBlocks.Join upAndDown = (piece, way, next) -> way.getAxis().isVertical() && carriesOn(piece, way, next);
        return JoinedBlocks.from(level, pos, MOST_PIECES, upAndDown);
    }

    /**
     * The piece of a column that follows the column back toward the one used: one joined to its neighbour that way,
     * as a column taller or shorter than the next has only some, or the first if none is.
     */
    private static BlockPos followingFrom(Level level, List<BlockPos> column, Direction back) {
        for (BlockPos at : column) {
            if (carriesOn(level.getBlockState(at), back, level.getBlockState(at.relative(back)))) {
                return at;
            }
        }
        return column.get(0);
    }

    /** The pieces of the columns through a position and so many either side of it, as far as the curtain goes. */
    public static List<BlockPos> columnsAround(Level level, BlockPos pos, int reach) {
        List<BlockPos> pieces = new ArrayList<>(column(level, pos));
        Direction left = leftOf(level.getBlockState(pos).getValue(FACING));
        pieces.addAll(columnsAlong(level, pos, left, reach));
        pieces.addAll(columnsAlong(level, pos, left.getOpposite(), reach));
        return pieces;
    }

    /** The pieces of so many columns one way along a curtain from a position, stopping where it ends. */
    private static List<BlockPos> columnsAlong(Level level, BlockPos pos, Direction way, int reach) {
        List<BlockPos> pieces = new ArrayList<>();
        BlockPos at = pos;
        for (int step = 0; step < reach && carriesOn(level.getBlockState(at), way, level.getBlockState(at.relative(way))); step++) {
            at = at.relative(way);
            pieces.addAll(column(level, at));
        }
        return pieces;
    }

    /** Draws every piece given open or shut. */
    public static void draw(Level level, List<BlockPos> pieces, boolean open) {
        for (BlockPos at : pieces) {
            BlockState piece = level.getBlockState(at);
            if (piece.getValue(OPEN) != open) {
                level.setBlock(at, piece.setValue(OPEN, open), Block.UPDATE_CLIENTS);
            }
        }
    }

    /** The swish of a curtain drawn along its rail, the rings clinking: once for the whole curtain. */
    public static void swish(Level level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.8F, 1.3F);
        level.playSound(null, pos, SoundEvents.CHAIN_STEP, SoundSource.BLOCKS, 0.3F, 1.6F);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return true;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    /** Mirrored, the curtain's left is its right. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }
        return super.mirror(state, mirror).setValue(LEFT, state.getValue(RIGHT)).setValue(RIGHT, state.getValue(LEFT));
    }

    /** What can be clicked of a piece: what it draws (see the outline constants), and nothing where it draws nothing. */
    private static VoxelShape outline(BlockState state) {
        double bottom = state.getValue(DOWN) ? 0 : HEM;
        Direction facing = state.getValue(FACING);
        if (!state.getValue(OPEN)) {
            return box(facing, 0, bottom, CLOTH_FRONT, 16, 16, CLOTH_BACK);
        }
        boolean top = !state.getValue(UP);
        double bunchTop = top ? RAIL_BOTTOM : 16;
        VoxelShape shape = top ? box(facing, 0, RAIL_BOTTOM, RAIL_FRONT, 16, 16, RAIL_BACK) : Shapes.empty();
        if (!state.getValue(LEFT)) {
            shape = Shapes.or(shape, box(facing, 0, bottom, BUNCH_FRONT, BUNCH_WIDTH, bunchTop, BUNCH_BACK));
        }
        if (!state.getValue(RIGHT)) {
            shape = Shapes.or(shape, box(facing, 16 - BUNCH_WIDTH, bottom, BUNCH_FRONT, 16, bunchTop, BUNCH_BACK));
        }
        return shape;
    }

    /** A box given in pixels as drawn facing north, turned to face the given way about the block's middle. */
    private static VoxelShape box(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (facing) {
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }
}
