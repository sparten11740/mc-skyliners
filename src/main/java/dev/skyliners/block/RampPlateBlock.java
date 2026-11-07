package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
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
 * What the freighter's boarding ramp is walked on while it swings (see {@link BoardingRamp}): blocks the ramp sets
 * under the plate its renderer draws, a tick at a time as it swings, so whoever walks onto it on its way is held up by
 * it rather than falling through it. Each is solid from its foot up to the plate's top over it, in four little steps
 * along the way up the ramp, as its laid deck is (see {@link RampDeckBlock}); where the plate's top dips into the block
 * under, that block is set too, full under the steps still over it. TOP is how high the plate's top stands over the
 * block's foot at its edge toward the hinge, DROP how far it falls across the block, both in sixteenths, and FACING the
 * way up the ramp, into her. Their shape comes from their state alone, never from the level, as Sable keeps each
 * state's shape for every ship. They are drawn by nothing and cannot be aimed at, mined or blown up: only the ramp sets
 * them and takes them away. One no ramp holds any more (left standing in the world as she was assembled with her ramp
 * swinging) takes itself away. With Sable, the ground passes through them (see {@code SableFlight#rampPlate}), so a
 * swinging plate never props her up.
 */
public class RampPlateBlock extends Block {
    private static final MapCodec<RampPlateBlock> CODEC = simpleCodec(RampPlateBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /**
     * The most the plate's top stands over a block's foot that it is set in, in sixteenths: a block and a half, for
     * the block under one its top dips out of; and the steepest it ever falls across a block, one block in two.
     */
    public static final int HIGHEST = 24;
    public static final int STEEPEST = 8;
    public static final IntegerProperty TOP = IntegerProperty.create("top", 1, HIGHEST);
    public static final IntegerProperty DROP = IntegerProperty.create("drop", 0, STEEPEST);
    private static final Map<Integer, Map<Direction, VoxelShape>> SHAPES = new HashMap<>();

    static {
        for (int top = 1; top <= HIGHEST; top++) {
            for (int drop = 0; drop <= STEEPEST; drop++) {
                SHAPES.put(key(top, drop), TurnedShapes.of(RampDeckBlock.slope(top, drop)));
            }
        }
    }

    public RampPlateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TOP, RampDeckBlock.FULL).setValue(DROP, 0));
    }

    /**
     * A block of the plate climbing toward {@code up}, the plate's top so many sixteenths over its foot at its edge
     * toward the hinge and falling so many across it; none where that leaves nothing of it solid.
     */
    @Nullable
    public static BlockState plate(BlockState base, Direction up, int top, int drop) {
        if (top < 1 || top > HIGHEST || drop < 0 || drop > STEEPEST || SHAPES.get(key(top, drop)).get(Direction.NORTH).isEmpty()) {
            return null;
        }
        return base.setValue(FACING, up).setValue(TOP, top).setValue(DROP, drop);
    }

    private static int key(int top, int drop) {
        return top * (STEEPEST + 1) + drop;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TOP, DROP);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Nothing to aim at: clicks go through to whatever is behind, the drawn plate or the ground. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(key(state.getValue(TOP), state.getValue(DROP))).get(state.getValue(FACING));
    }

    /** Solid ground to anything finding its way, as its laid deck is. */
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

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    /** Now and then one in the world makes sure some ramp still holds it, going if none does, as its laid deck does. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        RampDeckBlock.takeAwayIfLeftover(level, pos);
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
