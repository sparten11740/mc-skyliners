package dev.skyliners.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The freighter's engine grille: the slatted grille across her stern round her main engines, dark while she rests,
 * glowing deep blue while her lift thrusters hold her up and bright cyan while her mains push her ahead, as her
 * autopilot sets it (see the autopilot's EngineGlow). Glowing, it is drawn lit whatever the light round it, so she is
 * seen by night, and lights her own blocks round it; once she is assembled her blocks are far from the world, so it
 * lights nothing of the ground round her.
 */
public class EngineGrilleBlock extends Block {
    /** How it glows: 0 dark, 1 idling, 2 at full. */
    public static final IntegerProperty GLOW = IntegerProperty.create("glow", 0, 2);
    /** How brightly it lights what is round it while it idles and at full, as a light level. */
    private static final int IDLE_LIGHT = 10;
    private static final int FULL_LIGHT = 15;

    public EngineGrilleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(GLOW, 0));
    }

    /** How brightly a grille lights what is round it, as it glows. */
    public static int light(BlockState state) {
        return switch (state.getValue(GLOW)) {
            case 1 -> IDLE_LIGHT;
            case 2 -> FULL_LIGHT;
            default -> 0;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GLOW);
    }
}
