package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The jump light beside a door the parachutists leave by: dark, red to stand by, green to go, with a bell when it
 * turns green. It shows whatever redstone reaches it, a weak signal red and a strong one green, so the jump switch
 * on the flight deck works it through a relay, and so can anything else. Put up by hand with no relay beside it, a
 * jump switch put up by hand near it works it too (see {@link JumpSwitchBlock}), keeping what that switch turned it to
 * as SWITCHED, so that a door opening beside it or a block put down there leaves it as the switch set it.
 */
public class JumpLightBlock extends Block {
    private static final MapCodec<JumpLightBlock> CODEC = simpleCodec(JumpLightBlock::new);
    public static final EnumProperty<Signal> SIGNAL = EnumProperty.create("signal", Signal.class);
    /**
     * What a jump switch put up by hand near it last turned it to: the light shows the brighter of this and the
     * redstone reaching it. Off for every light no such switch works, as for those a ship wires through relays, and
     * for one saved before there was any.
     */
    public static final EnumProperty<Signal> SWITCHED = EnumProperty.create("switched", Signal.class);

    /** What the light shows, the redstone signal that makes it, and how much light it gives. */
    public enum Signal implements StringRepresentable {
        OFF(0, 0), RED(7, 9), GREEN(15, 13);

        private final int power;
        private final int light;

        Signal(int power, int light) {
            this.power = power;
            this.light = light;
        }

        /** The signal that turns a jump light this colour. */
        public int power() {
            return power;
        }

        public int light() {
            return light;
        }

        /** What a jump light shows for a redstone signal: none dark, up to half red, more green. */
        static Signal of(int power) {
            return power <= 0 ? OFF : power <= 7 ? RED : GREEN;
        }

        public Signal next() {
            return values()[(ordinal() + 1) % values().length];
        }

        /** The brighter of this and another, what a light shows for a switch and redstone both. */
        Signal brighter(Signal other) {
            return other.power > power ? other : this;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public JumpLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SIGNAL, Signal.OFF).setValue(SWITCHED, Signal.OFF));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIGNAL, SWITCHED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(SIGNAL, Signal.of(context.getLevel().getBestNeighborSignal(context.getClickedPos())));
    }

    /** Follows the redstone reaching it; a relay put beside it takes it over from any switch put up by hand. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        if (level instanceof ServerLevel serverLevel) {
            Signal switched = state.getValue(SWITCHED);
            show(serverLevel, pos, state, switched != Signal.OFF && wired(level, pos) ? Signal.OFF : switched);
        }
    }

    /**
     * Shows the brighter of the redstone reaching it and the signal given, what a jump switch put up by hand has turned
     * it to, ringing its bell as it turns green.
     */
    static void show(ServerLevel level, BlockPos pos, BlockState state, Signal switched) {
        Signal signal = Signal.of(level.getBestNeighborSignal(pos)).brighter(switched);
        BlockState shown = state.setValue(SIGNAL, signal).setValue(SWITCHED, switched);
        if (shown == state) {
            return;
        }
        level.setBlock(pos, shown, Block.UPDATE_CLIENTS);
        if (signal == Signal.GREEN && state.getValue(SIGNAL) != Signal.GREEN) {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 2.0F, 1.2F);
        }
    }

    /**
     * Whether a relay beside it wires it to a ship's own jump switch, as a ship's lights are wired as it is built: a
     * switch put up by hand leaves such a light to it.
     */
    static boolean wired(BlockGetter level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            if (Relays.isRelay(level.getBlockState(pos.relative(side)))) {
                return true;
            }
        }
        return false;
    }

    /** Set into the hull, it keeps the daylight out of the cabin like the skin around it; it is glass only to look at. */
    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return level.getMaxLightLevel();
    }
}
