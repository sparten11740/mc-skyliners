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
 * The rocket's halo light: a ring of light a hand's width across, held level on four fine spokes from a slim cable
 * hanging from the ceiling (see {@link LightCableBlock}), warm white, a cool blue as a night light in her bunk room, or
 * pink (see {@link LightTone}). Her rooms hang them where a ship of the age of sail would hang lanterns. The Halo Light
 * a player puts up hangs so under a ceiling, and is a wall washer on a wall or a floor light on the ground (see
 * {@code LightFittingItem}); used, it goes on to its next tone.
 */
public class HaloLightBlock extends Block {
    private static final MapCodec<HaloLightBlock> CODEC = simpleCodec(HaloLightBlock::new);
    /** How it is lit. */
    public static final EnumProperty<LightTone> TONE = LightTone.PROPERTY;
    /** How brightly each tone lights the room round it, as a block's light level. */
    private static final int WARM_LIGHT = 14;
    private static final int COOL_LIGHT = 9;
    private static final int PINK_LIGHT = 11;
    /** The ring and its spokes, and the cable down to them. */
    private static final VoxelShape SHAPE = Block.box(2, 6, 2, 14, 16, 14);

    public HaloLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TONE, LightTone.WARM));
    }

    /** How brightly a halo light in this state lights the room round it. */
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
