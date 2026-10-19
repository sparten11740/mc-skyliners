package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A cabin light set into the crown, the walls or the hold, or a starlight panel in the lounge's ceiling: as bright
 * as the flight deck's dimmer has it. KEPT_ON marks a light kept on at night, as an airliner's lavatories are: the
 * blueprints set it on the lights of the WC, the crew room and the lavatories, which with the cabin lights set for
 * the night stay as they are dimmed. A light placed by hand is not kept on.
 */
public class CabinLightBlock extends Block {
    private static final MapCodec<CabinLightBlock> CODEC = simpleCodec(CabinLightBlock::new);
    public static final BooleanProperty KEPT_ON = BooleanProperty.create("kept_on");

    public CabinLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(Brightness.PROPERTY, Brightness.BRIGHT).setValue(KEPT_ON, false));
    }

    /** The light it gives at its setting, from what it gives full on; kept on, at night what it gives dimmed. */
    public static int light(BlockState state, int full) {
        Brightness brightness = state.getValue(Brightness.PROPERTY);
        return (state.getValue(KEPT_ON) && brightness == Brightness.NIGHT ? Brightness.DIM : brightness).light(full);
    }

    /** The light, kept on at night. */
    public static BlockState keptOn(BlockState light) {
        return light.trySetValue(KEPT_ON, true);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Brightness.PROPERTY, KEPT_ON);
    }
}
