package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import dev.aerolarge.rover.RoverRoute;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What the rocket's rover bay's door is walked on once it lies folded down into a platform (see
 * {@link RoverBayBlockEntity}): blocks the bay lays out of her under the deck its renderer draws, as wide as the
 * doorway and reaching as far out as the door is tall, each a plate as thick as the door at the foot of its block,
 * level with the deck, so whoever follows the rover out walks to the platform's end. The block at its end, its TIP, is
 * a plate only over its half toward her, where the deck ends; FACING is the way out of her. The doorway's sill, under
 * the door's hinge, is the door's own (see {@link RoverHatchBlock}). The bay lays them once the door lies flat and
 * takes them up as it starts to fold back up, so nothing holds anyone up where no deck is drawn. They are drawn by
 * nothing and cannot be aimed at, mined or blown up; one no bay holds any more (left standing in the world as she was
 * assembled with her door down) takes itself away.
 */
public class RoverPlatformBlock extends Block {
    private static final MapCodec<RoverPlatformBlock> CODEC = simpleCodec(RoverPlatformBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty TIP = BooleanProperty.create("tip");
    /** How many blocks out of her the platform reaches, its hinge in the doorway's middle: the last only half. */
    private static final int ROWS = (int) Math.round(RoverRoute.PLATFORM);
    /** How many blocks the platform has: as many rows out of her as it reaches, each as wide as the doorway. */
    private static final int CELLS = ROWS * RoverHatchBlock.COLUMNS;
    /** Each block's plate, as thick as the door, level with the doorway's sill and the drawn deck. */
    private static final VoxelShape WHOLE = box(0, 0, 0, 16, RoverHatchBlock.DECK, 16);
    /** The tip's plate, over its half toward her, by the way out of her. */
    private static final Map<Direction, VoxelShape> TIP_PLATE = Map.of(
        Direction.NORTH, box(0, 0, 8, 16, RoverHatchBlock.DECK, 16),
        Direction.SOUTH, box(0, 0, 0, 16, RoverHatchBlock.DECK, 8),
        Direction.EAST, box(0, 0, 0, 8, RoverHatchBlock.DECK, 16),
        Direction.WEST, box(8, 0, 0, 16, RoverHatchBlock.DECK, 16)
    );

    public RoverPlatformBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TIP, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TIP);
    }

    /**
     * Where a block of the platform goes out of a bay's doorway, from the middle of its sill, by its number: row by row
     * out of her from the one next to the doorway, and along each from the left seen from outside.
     */
    private static BlockPos cell(BlockPos bay, Direction out, int cell) {
        int row = 1 + cell / RoverHatchBlock.COLUMNS;
        int column = cell % RoverHatchBlock.COLUMNS;
        return bay.relative(out, row).relative(out.getClockWise(), RoverHatchBlock.COLUMNS / 2 - column);
    }

    /** Lays the platform out of a bay's doorway where it is missing, wherever there is room for it. */
    static void lay(ServerLevel level, BlockPos bay, Direction out) {
        BlockState plate = AerolargeRegistry.ROVER_PLATFORM.get().defaultBlockState().setValue(FACING, out);
        for (int cell = 0; cell < CELLS; cell++) {
            BlockPos at = cell(bay, out, cell);
            BlockState there = level.getBlockState(at);
            if (!(there.getBlock() instanceof RoverPlatformBlock) && (there.isAir() || there.canBeReplaced())) {
                level.setBlock(at, plate.setValue(TIP, cell >= CELLS - RoverHatchBlock.COLUMNS), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * Takes up the platform out of a bay's doorway: its own blocks, and, for a bay aboard an assembled rocket, those
     * left standing in the world where they lay as she was assembled, as long as she still stands there.
     */
    static void takeUp(ServerLevel level, BlockPos bay, Direction out) {
        for (int cell = 0; cell < CELLS; cell++) {
            BlockPos at = cell(bay, out, cell);
            takeUp(level, at);
            BlockPos world = BlockPos.containing(SableFlight.toWorld(level, Vec3.atCenterOf(at)));
            if (!world.equals(at) && level.isLoaded(world)) {
                takeUp(level, world);
            }
        }
    }

    private static void takeUp(ServerLevel level, BlockPos at) {
        if (level.getBlockState(at).getBlock() instanceof RoverPlatformBlock) {
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Whether a block is part of the platform out of a bay's doorway. */
    static boolean holds(BlockPos bay, Direction out, BlockPos pos) {
        for (int cell = 0; cell < CELLS; cell++) {
            if (cell(bay, out, cell).equals(pos)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Nothing to aim at: clicks go through to whatever lies past it, the drawn deck or the ground below. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(TIP) ? TIP_PLATE.get(state.getValue(FACING)) : WHOLE;
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
     * Now and then a block of the platform makes sure some bay still holds it, its door down; with none, it is a
     * leftover, and goes. Where the land round it, as far as a bay could be, is not loaded, it waits.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int reach = ROWS + 1;
        for (int corner = 0; corner < 4; corner++) {
            if (!level.isLoaded(pos.offset((corner & 1) == 0 ? -reach : reach, 0, (corner & 2) == 0 ? -reach : reach))) {
                return;
            }
        }
        for (RoverBayBlockEntity bay : RoverBayBlockEntity.LOADED.in(level)) {
            if (bay.holdsPlatform(pos)) {
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
