package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The halo light put down on the ground (see {@code LightFittingItem}): a low dark drum set on the floor, a ring of
 * light round its top and a glowing lens in the middle, shining up, warm white, a cool night-light blue or pink (see
 * {@link LightTone}). Used, it goes on to its next tone; broken, it gives back a Halo Light.
 */
public class FloorLightBlock extends Block {
    private static final MapCodec<FloorLightBlock> CODEC = simpleCodec(FloorLightBlock::new);
    /** How it is lit. */
    public static final EnumProperty<LightTone> TONE = LightTone.PROPERTY;
    /** How brightly each tone lights the room round it, as a block's light level. */
    private static final int WARM_LIGHT = 12;
    private static final int COOL_LIGHT = 8;
    private static final int PINK_LIGHT = 10;
    /** The drum and its ring, low on the floor. */
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 2.5, 13);

    public FloorLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TONE, LightTone.WARM));
    }

    /** How brightly a floor light in this state lights the room round it. */
    public static int light(BlockState state) {
        return switch (state.getValue(TONE)) {
            case WARM -> WARM_LIGHT;
            case COOL -> COOL_LIGHT;
            case PINK -> PINK_LIGHT;
        };
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TONE);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return LightTone.turn(state, level, pos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
