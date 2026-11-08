package dev.skyliners.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The freighter's engine grille: the slatted grille across her stern round her main engines, dark while she rests,
 * glowing deep blue while her lift thrusters hold her up and bright cyan while her mains push her ahead, as her
 * autopilot sets it (see the autopilot's EngineGlow). Glowing, it is drawn lit whatever the light round it, so she is
 * seen by night, and lights her own blocks round it; once she is assembled her blocks are far from the world, so it
 * lights nothing of the ground round her. Set by hand it has no ship to light it, so it takes its look from the
 * redstone signal it receives, which a grille of hers never does, whatever redstone is near her.
 */
public class EngineGrilleBlock extends Block {
    /** How it glows: 0 dark, 1 idling, 2 at full. */
    public static final IntegerProperty GLOW = IntegerProperty.create("glow", 0, 2);
    /**
     * Whether it was set by hand, and so goes by redstone: false for a grille a freighter is built with, and for every
     * grille saved before this was, which load as that, so a freighter built then stays her autopilot's alone.
     */
    public static final BooleanProperty BY_HAND = BooleanProperty.create("by_hand");
    /** How brightly it lights what is round it while it idles and at full, as a light level. */
    private static final int IDLE_LIGHT = 10;
    private static final int FULL_LIGHT = 15;
    /**
     * The weakest redstone signal, of 15, at which a grille set by hand glows at full: from half strength, so a lever
     * still brings it to full down eight blocks of wire. Any weaker signal has it idling, and none leaves it dark.
     */
    private static final int FULL_FROM = 8;

    public EngineGrilleBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(GLOW, 0).setValue(BY_HAND, false));
    }

    /** How brightly a grille lights what is round it, as it glows. */
    public static int light(BlockState state) {
        return switch (state.getValue(GLOW)) {
            case 1 -> IDLE_LIGHT;
            case 2 -> FULL_LIGHT;
            default -> 0;
        };
    }

    /** How a grille set by hand glows for a redstone signal. */
    private static int glowFor(int signal) {
        if (signal <= 0) {
            return 0;
        }
        return signal >= FULL_FROM ? 2 : 1;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BY_HAND, true)
            .setValue(GLOW, glowFor(context.getLevel().getBestNeighborSignal(context.getClickedPos())));
    }

    /**
     * A grille set by hand changes as the signal it receives does. It tells clients but no neighbour, as it sends no
     * signal on.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moved) {
        if (level.isClientSide || !state.getValue(BY_HAND)) {
            return;
        }
        int glow = glowFor(level.getBestNeighborSignal(pos));
        if (glow != state.getValue(GLOW)) {
            level.setBlock(pos, state.setValue(GLOW, glow), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(GLOW, BY_HAND);
    }
}
