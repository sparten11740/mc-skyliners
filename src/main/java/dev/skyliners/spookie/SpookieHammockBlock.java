package dev.skyliners.spookie;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.TurnedShapes;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Spookie's hammock in the freighter's crew quarters (see {@link SpookieHammockBlockEntity}, which keeps the
 * Spookie): a big striped canvas sling slung between two dark posts, three blocks long, its sling's block the middle of
 * it and a post standing in the block either side, each holding the sling's end on its spreader bar. FACING is the way
 * its long side faces, the side the Spookie climbs in from; its length runs across that. PART is which of its blocks
 * this is: a post faces the way the sling does on the sling's clockwise side and the other way on the other, so either
 * post's model is the same, the sling on its left as seen from the way it faces. Every block of it is solid as drawn,
 * the posts all the way up. A post knocked out other than by a player, as by a blast, leaves the sling hanging from the
 * other until it is loaded again, when its sling puts it back up. Set down by hand it keeps a Spookie of its own, which
 * stands beside it on the side it faces, having no rounds to walk.
 */
public class SpookieHammockBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<SpookieHammockBlock> CODEC = simpleCodec(SpookieHammockBlock::new);
    /** Which of its three blocks this is: the sling in the middle, or a post either side of it. */
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** The sling at its lowest, in its own block, by the way it faces: its length across that way. */
    private static final Map<Direction, VoxelShape> SLING = new EnumMap<>(Direction.class);
    /**
     * A post by the way it faces, drawn facing north: the post itself up the block's east side, near two blocks tall,
     * and the end of the sling on its spreader bar along its west side.
     */
    private static final Map<Direction, VoxelShape> POST =
        TurnedShapes.of(Shapes.or(Block.box(12, 0, 6, 15, 30, 10), Block.box(0, 12, 1, 6, 19, 15)));

    /** Which of its blocks a state is. */
    public enum Part implements StringRepresentable {
        SLING,
        POST;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    static {
        VoxelShape across = Block.box(0, 6, 2, 16, 10, 14);
        VoxelShape along = Block.box(2, 6, 0, 14, 10, 16);
        SLING.put(Direction.NORTH, across);
        SLING.put(Direction.SOUTH, across);
        SLING.put(Direction.EAST, along);
        SLING.put(Direction.WEST, along);
    }

    public SpookieHammockBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.SLING));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** The post standing beside a sling facing as given, on its clockwise side or the other, facing as it stands there. */
    public static BlockState post(Block hammock, Direction facing, boolean clockwise) {
        return hammock.defaultBlockState().setValue(PART, Part.POST).setValue(FACING, clockwise ? facing : facing.getOpposite());
    }

    /** Where that post stands, beside the sling at the block given. */
    public static BlockPos postAt(BlockPos sling, Direction facing, boolean clockwise) {
        return sling.relative(clockwise ? facing.getClockWise() : facing.getCounterClockWise());
    }

    /**
     * Put down by hand, it faces whoever puts it there, its sling where it is put and its posts either side, so it
     * needs room for them.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        boolean room = level.getBlockState(postAt(pos, facing, true)).canBeReplaced(context)
            && level.getBlockState(postAt(pos, facing, false)).canBeReplaced(context);
        return room ? defaultBlockState().setValue(FACING, facing) : null;
    }

    /**
     * Set down by hand, its posts go up either side and it keeps a Spookie standing beside it (see
     * {@link SpookieHammockBlockEntity#keepBeside}).
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof SpookieHammockBlockEntity keeper) {
            hangPosts(level, pos, state);
            keeper.keepBeside(state.getValue(FACING));
        }
    }

    /**
     * Puts up a sling's posts either side of it where they are missing and their blocks are free: as it is set down,
     * and for a hammock saved before its posts were blocks of their own, or set by a command, which puts up its sling
     * alone.
     */
    static void hangPosts(Level level, BlockPos sling, BlockState state) {
        Direction facing = state.getValue(FACING);
        for (boolean clockwise : new boolean[]{true, false}) {
            BlockPos at = postAt(sling, facing, clockwise);
            BlockState there = level.getBlockState(at);
            if (!there.is(state.getBlock()) && there.canBeReplaced()) {
                level.setBlock(at, post(state.getBlock(), facing, clockwise), Block.UPDATE_ALL);
            }
        }
    }

    /** Where the sling a post holds is: on its left as seen from the way it faces. */
    private static BlockPos slingOf(BlockPos post, BlockState state) {
        return post.relative(state.getValue(FACING).getCounterClockWise());
    }

    /**
     * Broken by a post, it takes the whole hammock down, which gives itself back from its sling, unless in creative
     * mode; its other post goes as its sling does (see {@link #updateShape}).
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos sling = slingOf(pos, state);
        BlockState held = level.getBlockState(sling);
        boolean holding = state.getValue(PART) == Part.POST && held.is(this) && held.getValue(PART) == Part.SLING;
        if (!level.isClientSide && holding) {
            level.destroyBlock(sling, !player.isCreative(), player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** A post with no sling beside it to hold any more goes too. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos,
        BlockPos neighbourPos) {
        boolean slingGone = !neighbour.is(this) || neighbour.getValue(PART) != Part.SLING;
        if (state.getValue(PART) == Part.POST && neighbourPos.equals(slingOf(pos, state)) && slingGone) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(PART) == Part.SLING ? SLING : POST).get(state.getValue(FACING));
    }

    /** Its sling keeps its Spookie; its posts keep nothing. */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.SLING ? new SpookieHammockBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != SkylinersRegistry.SPOOKIE_HAMMOCK_ENTITY.get()) {
            return null;
        }
        return (world, pos, blockState, keeper) -> SpookieHammockBlockEntity.tick((ServerLevel) world, (SpookieHammockBlockEntity) keeper);
    }
}
