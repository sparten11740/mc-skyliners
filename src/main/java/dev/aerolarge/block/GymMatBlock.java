package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A gym mat: a thick padded mat a quarter of a block high, its blue vinyl cover piped in grey round its edges. Mats
 * laid side by side make one mat, piped only round its outside (NORTH, EAST, SOUTH and WEST say which ways it runs on
 * into another). It lies on whatever is under it as a carpet does, and as with a carpet a curtain or a wall put up
 * across it takes its place. A fall onto it hurts a fifth as much as onto the floor, as onto hay.
 */
public class GymMatBlock extends CarpetBlock implements Joining {
    private static final MapCodec<GymMatBlock> CODEC = simpleCodec(GymMatBlock::new);
    private static final Map<Direction, BooleanProperty> SIDES = new EnumMap<>(Map.of(
        Direction.NORTH, BlockStateProperties.NORTH, Direction.EAST, BlockStateProperties.EAST,
        Direction.SOUTH, BlockStateProperties.SOUTH, Direction.WEST, BlockStateProperties.WEST));
    /** How thick the mat is, in pixels. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 4, 16);
    /** How much of a fall's damage a fall onto the mat does: as much as onto a hay bale. */
    private static final float FALL_DAMAGE = 0.2F;

    public GymMatBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any();
        for (BooleanProperty side : SIDES.values()) {
            state = state.setValue(side, false);
        }
        registerDefaultState(state);
    }

    @Override
    public MapCodec<? extends CarpetBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIDES.values().toArray(BooleanProperty[]::new));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        return joined(defaultBlockState(), way -> context.getLevel().getBlockState(pos.relative(way)));
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        BlockState updated = super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
        return updated.is(this) ? joined(updated, way -> level.getBlockState(pos.relative(way))) : updated;
    }

    /** Joined on to a mat beside it, each way. */
    @Override
    public BlockState joined(BlockState state, Function<Direction, BlockState> beside) {
        for (Map.Entry<Direction, BooleanProperty> side : SIDES.entrySet()) {
            state = state.setValue(side.getValue(), beside.apply(side.getKey()).is(this));
        }
        return state;
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        entity.causeFallDamage(fallDistance, FALL_DAMAGE, level.damageSources().fall());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState turned = state;
        for (Map.Entry<Direction, BooleanProperty> side : SIDES.entrySet()) {
            turned = turned.setValue(SIDES.get(rotation.rotate(side.getKey())), state.getValue(side.getValue()));
        }
        return turned;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state;
        for (Map.Entry<Direction, BooleanProperty> side : SIDES.entrySet()) {
            mirrored = mirrored.setValue(SIDES.get(mirror.mirror(side.getKey())), state.getValue(side.getValue()));
        }
        return mirrored;
    }
}
