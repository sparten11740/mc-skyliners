package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.Locale;
import net.minecraft.core.BlockPos;
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
 * on the flight deck works it through a relay, and so can anything else.
 */
public class JumpLightBlock extends Block {
    private static final MapCodec<JumpLightBlock> CODEC = simpleCodec(JumpLightBlock::new);
    public static final EnumProperty<Signal> SIGNAL = EnumProperty.create("signal", Signal.class);

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

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public JumpLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(SIGNAL, Signal.OFF));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIGNAL);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(SIGNAL, Signal.of(context.getLevel().getBestNeighborSignal(context.getClickedPos())));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Signal signal = Signal.of(level.getBestNeighborSignal(pos));
        if (signal != state.getValue(SIGNAL)) {
            level.setBlock(pos, state.setValue(SIGNAL, signal), Block.UPDATE_CLIENTS);
            if (signal == Signal.GREEN) {
                serverLevel.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 2.0F, 1.2F);
            }
        }
    }

    /** Set into the hull, it keeps the daylight out of the cabin like the skin around it; it is glass only to look at. */
    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return level.getMaxLightLevel();
    }
}
