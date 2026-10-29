package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The rocket's halo light: a ring of light a hand's width across, held level on four fine spokes from a slim cable
 * hanging from the ceiling (see {@link LightCableBlock}), warm white, or a cool blue as a night light in her bunk
 * room. Her rooms hang them where a ship of the age of sail would hang lanterns. Placed by her blueprint; broken, it
 * gives itself back.
 */
public class HaloLightBlock extends Block {
    private static final MapCodec<HaloLightBlock> CODEC = simpleCodec(HaloLightBlock::new);
    /** How it is lit: warm white, or a cool blue night light. */
    public static final EnumProperty<Tone> TONE = EnumProperty.create("tone", Tone.class);
    /** How brightly each tone lights the room round it, as a block's light level. */
    private static final int WARM_LIGHT = 14;
    private static final int COOL_LIGHT = 9;
    /** The ring and its spokes, and the cable down to them. */
    private static final VoxelShape SHAPE = Block.box(2, 6, 2, 14, 16, 14);

    /** The colour of its light. */
    public enum Tone implements StringRepresentable {
        WARM, COOL;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public HaloLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(TONE, Tone.WARM));
    }

    /** How brightly a halo light in this state lights the room round it. */
    public static int light(BlockState state) {
        return state.getValue(TONE) == Tone.WARM ? WARM_LIGHT : COOL_LIGHT;
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
