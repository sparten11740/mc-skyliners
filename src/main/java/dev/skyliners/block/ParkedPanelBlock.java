package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
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
 * Where an open plug door's panel stands, flat against the hull beside its doorway (see {@link PlugDoorBlockEntity}):
 * blocks its door lays once it has swung open and takes away as it starts to shut, so the panel stops whoever walks
 * into it where it is drawn, lining and all, though it stands outside the doorway's own blocks. FACING is the door's
 * way out of the hull, FROM how far across its block from the side toward the hull the panel starts, in sixteenths.
 * Drawn by nothing, as the door draws the panel, and nothing to aim at, so a click goes through to the hull behind;
 * only the door places it and takes it away. One no open door holds any more takes itself away.
 */
public class ParkedPanelBlock extends Block {
    private static final MapCodec<ParkedPanelBlock> CODEC = simpleCodec(ParkedPanelBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty FROM = IntegerProperty.create("from", 0, 15);
    /**
     * How far round a panel a door that holds it may be, in blocks: a door's panel stands at most three blocks across
     * from its bottom left part (its far column, {@code 2 * PlugDoorBlock.COLUMNS - 1}), two over it (its top row) and
     * three out from it (where the hull bulges out two blocks past the doorway), so four leaves one to spare.
     */
    private static final int DOOR_REACH = 4;
    /** The panel's box for each start across its block and each way out, as deep as the door, lining and all. */
    private static final List<Map<Direction, VoxelShape>> SHAPES = IntStream.range(0, 16)
        .mapToObj(from -> TurnedShapes.of(box(0, 0, 16 - Math.min(16, from + PlugDoorBlock.DEPTH), 16, 16, 16 - from)))
        .toList();

    public ParkedPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FROM, 0));
    }

    /** The panel of a door facing out the given way, starting this many sixteenths across its block from the hull. */
    public static BlockState of(BlockState base, Direction out, int from) {
        return base.setValue(FACING, out).setValue(FROM, Math.clamp(from, 0, 15));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FROM);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Nothing to aim at: clicks go through to whatever is behind, as the door's own open doorway lets them. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FROM)).get(state.getValue(FACING));
    }

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

    /**
     * Now and then a panel makes sure an open door still holds it; with none, it is a leftover (its door broken while
     * open, or the hull round it changed), and goes. Where that part of the world is not loaded it waits.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockPos from = pos.offset(-DOOR_REACH, -DOOR_REACH, -DOOR_REACH);
        BlockPos to = pos.offset(DOOR_REACH, DOOR_REACH, DOOR_REACH);
        if (!level.hasChunksAt(from, to)) {
            return;
        }
        for (BlockPos at : BlockPos.betweenClosed(from, to)) {
            if (level.getBlockEntity(at) instanceof PlugDoorBlockEntity door && door.holds(pos)) {
                return;
            }
        }
        level.removeBlock(pos, false);
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
