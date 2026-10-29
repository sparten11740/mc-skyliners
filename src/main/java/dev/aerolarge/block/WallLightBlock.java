package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The rocket's wall washer: a small dark lamp fixed to her wall over head height, throwing a fan of light up the wall
 * that fades toward the ceiling, warm white, a cool night-light blue in her bunk room or pink in her club. Its light
 * on the wall is drawn, as the game's light cannot wash a wall; it lights the room round it as any lamp does. Faces
 * the way it shines out from the wall. The Halo Light a player puts against a wall is one (see
 * {@code LightFittingItem}); used, it goes on to its next tone (see {@link LightTone}); broken, it gives back a Halo
 * Light.
 */
public class WallLightBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<WallLightBlock> CODEC = simpleCodec(WallLightBlock::new);
    /** The colour of its light. */
    public static final EnumProperty<LightTone> TONE = LightTone.PROPERTY;
    /** How brightly each tone lights the room round it, as a block's light level. */
    private static final int WARM_LIGHT = 12;
    private static final int COOL_LIGHT = 8;
    private static final int PINK_LIGHT = 10;
    /** The lamp itself against the wall, by the way it faces. */
    private static final VoxelShape FACING_NORTH = Block.box(5, 3, 13, 11, 6.25, 16);
    private static final VoxelShape FACING_SOUTH = Block.box(5, 3, 0, 11, 6.25, 3);
    private static final VoxelShape FACING_WEST = Block.box(13, 3, 5, 16, 6.25, 11);
    private static final VoxelShape FACING_EAST = Block.box(0, 3, 5, 3, 6.25, 11);

    public WallLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TONE, LightTone.WARM));
    }

    /** How brightly a wall washer in this state lights the room round it. */
    public static int light(BlockState state) {
        return switch (state.getValue(TONE)) {
            case WARM -> WARM_LIGHT;
            case COOL -> COOL_LIGHT;
            case PINK -> PINK_LIGHT;
        };
    }

    /** A wall washer shining out one way, in a tone. */
    public BlockState shining(Direction facing, LightTone tone) {
        return defaultBlockState().setValue(FACING, facing).setValue(TONE, tone);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return LightTone.turn(state, level, pos);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TONE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> FACING_SOUTH;
            case WEST -> FACING_WEST;
            case EAST -> FACING_EAST;
            default -> FACING_NORTH;
        };
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
