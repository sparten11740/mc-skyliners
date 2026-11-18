package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What an evacuation slide stands on, as it comes out and once out: invisible steps its control lays just under the
 * lane its renderer draws (see {@link SlideControlBlockEntity}), one in each of the lane's columns for each block out,
 * so whoever steps out of the doorway has something underfoot until the slide takes them down, and villagers find their
 * way out down it. Each is solid from its foot to the lane's height over it, in eighths of a block (HEIGHT), and knows
 * the way back up the slide to its door (FACING). Drawn by nothing and not to be aimed at, mined or blown up: only the
 * control lays them and takes them up. A fall onto one never hurts. A step no control holds any more (left standing in
 * the world when an airliner was assembled and flew off with its slide out) takes itself away.
 */
public class SlideStepBlock extends Block {
    private static final MapCodec<SlideStepBlock> CODEC = simpleCodec(SlideStepBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty HEIGHT = IntegerProperty.create("height", 1, 8);
    /** Each height's solid part, by its eighths. */
    private static final VoxelShape[] SHAPES = new VoxelShape[9];
    /** How far a lone step looks up the slide for its top, in blocks: further than any slide reaches. */
    private static final int LONGEST_SLIDE = 96;
    /** How far from the doorway at the top of the slide it looks for the control, in blocks. */
    private static final int CONTROL_REACH = 3;

    static {
        for (int eighths = 1; eighths <= 8; eighths++) {
            SHAPES[eighths] = box(0, 0, 0, 16, 2 * eighths, 16);
        }
    }

    public SlideStepBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HEIGHT, 8));
    }

    /** A step of a slide whose door is toward {@code up}, solid this many eighths of a block up from its foot. */
    public static BlockState step(BlockState base, Direction up, int eighths) {
        return base.setValue(FACING, up).setValue(HEIGHT, eighths);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HEIGHT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Nothing to aim at: clicks go through to the slide's control or whatever is behind. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(HEIGHT)];
    }

    /** Solid ground to anything finding its way, as a slab is, so villagers walk down the slide out of the door. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    /** The slide is soft: landing on it never hurts. */
    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        entity.resetFallDistance();
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    /**
     * Now and then a step makes sure it is still some control's: it follows its column up the slide to its top and
     * looks round the doorway there for a slide control that has laid it; with none, it is a leftover, and goes. Where
     * that part of the world is not loaded it waits.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction up = state.getValue(FACING);
        BlockPos top = pos;
        for (int i = 0; i < LONGEST_SLIDE; i++) {
            BlockPos next = top.relative(up);
            if (isStep(level.getBlockState(next))) {
                top = next;
            } else if (isStep(level.getBlockState(next.above()))) {
                top = next.above();
            } else {
                break;
            }
        }
        BlockPos doorway = top.relative(up).above();
        for (int corner = 0; corner < 4; corner++) {
            if (!level.isLoaded(doorway.offset((corner & 1) == 0 ? -CONTROL_REACH : CONTROL_REACH, 0,
                (corner & 2) == 0 ? -CONTROL_REACH : CONTROL_REACH))) {
                return;
            }
        }
        BlockPos from = doorway.offset(-CONTROL_REACH, -CONTROL_REACH, -CONTROL_REACH);
        BlockPos to = doorway.offset(CONTROL_REACH, CONTROL_REACH, CONTROL_REACH);
        for (BlockPos at : BlockPos.betweenClosed(from, to)) {
            if (level.getBlockEntity(at) instanceof SlideControlBlockEntity slide && slide.holds(level, pos)) {
                return;
            }
        }
        level.removeBlock(pos, false);
    }

    public static boolean isStep(BlockState state) {
        return state.getBlock() instanceof SlideStepBlock;
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
