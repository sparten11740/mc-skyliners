package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.common.FarmlandWaterManager;

/**
 * A raised bed of the rocket's greenhouse, a block of it at a time: soil in a frame of spruce boards, the frame running
 * round the outside of however many beds stand side by side, so a row of them is one bed, its outer corners posted. It
 * is farmland to what grows in it, moist where water is within four blocks as farmland is, and its soil darkens wet and
 * pales dry; but raised, it is never trampled, and dry, or with something solid on it, it stays a bed where farmland
 * would turn back to dirt. A sprinkler standing in it counts as more of the bed, the frame going round it too.
 * NORTH, EAST, SOUTH and WEST are whether more of the bed stands that way, where the frame leaves off.
 */
public class GardenBedBlock extends FarmBlock {
    @SuppressWarnings("unchecked")
    private static final MapCodec<FarmBlock> CODEC = (MapCodec<FarmBlock>) (MapCodec<? extends FarmBlock>) simpleCodec(GardenBedBlock::new);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    /** Each side's property, by the way it looks. */
    private static final Map<Direction, BooleanProperty> SIDES = Map.of(Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH,
        Direction.WEST, WEST);
    /** How far from water the soil stays moist, in blocks each way, and how far over it: farmland's own reach. */
    private static final int WATER_REACH = 4;
    private static final int WATER_ABOVE = 1;

    public GardenBedBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MOISTURE, 0).setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
            .setValue(WEST, false));
    }

    @Override
    public MapCodec<FarmBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(NORTH, EAST, SOUTH, WEST);
    }

    /** A bed joined, or not, to more of it on a side. */
    public static BlockState joined(BlockState state, Direction side, boolean joined) {
        return state.setValue(SIDES.get(side), joined);
    }

    /** Whether a bed takes what stands beside it as more of itself: another bed, or a sprinkler standing in it. */
    public static boolean joins(BlockState beside) {
        return beside.getBlock() instanceof GardenBedBlock || beside.getBlock() instanceof SprinklerBlock;
    }

    /** Put down, it joins the beds beside it, and stays a bed even under something solid. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        for (Direction side : Direction.Plane.HORIZONTAL) {
            state = joined(state, side, joins(context.getLevel().getBlockState(context.getClickedPos().relative(side))));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction side, BlockState beside, LevelAccessor level, BlockPos pos, BlockPos besidePos) {
        return side.getAxis().isHorizontal() ? joined(state, side, joins(beside)) : state;
    }

    /** Never turned back to dirt, whatever stands on it. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    }

    /** Its soil wets with water near or rain, and slowly dries without, as farmland's does, but stays a bed dry. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int moisture = state.getValue(MOISTURE);
        if (watered(level, pos)) {
            if (moisture < MAX_MOISTURE) {
                level.setBlock(pos, state.setValue(MOISTURE, MAX_MOISTURE), Block.UPDATE_CLIENTS);
            }
        } else if (moisture > 0) {
            level.setBlock(pos, state.setValue(MOISTURE, moisture - 1), Block.UPDATE_CLIENTS);
        }
    }

    /** Whether water is near enough to wet the soil, as farmland feels it, or rain falls on it. */
    private static boolean watered(ServerLevel level, BlockPos pos) {
        if (level.isRainingAt(pos.above())) {
            return true;
        }
        BlockState state = level.getBlockState(pos);
        for (BlockPos near : BlockPos.betweenClosed(pos.offset(-WATER_REACH, 0, -WATER_REACH), pos.offset(WATER_REACH, WATER_ABOVE, WATER_REACH))) {
            if (state.canBeHydrated(level, pos, level.getFluidState(near), near)) {
                return true;
            }
        }
        return FarmlandWaterManager.hasBlockWaterTicket(level, pos);
    }

    /** Raised, it is never trampled: whoever lands on it only falls as onto anything else. */
    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        entity.causeFallDamage(fallDistance, 1.0F, entity.damageSources().fall());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState turned = state;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            turned = turned.setValue(SIDES.get(rotation.rotate(side)), state.getValue(SIDES.get(side)));
        }
        return turned;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState turned = state;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            turned = turned.setValue(SIDES.get(mirror.mirror(side)), state.getValue(SIDES.get(side)));
        }
        return turned;
    }
}
