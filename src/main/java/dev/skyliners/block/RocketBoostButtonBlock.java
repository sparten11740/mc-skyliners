package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.RocketControls;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A rocket's boost button, before her control head on the centre console: a big flat round mushroom button on a short
 * pedestal behind its plate's flame, so it reads as the button that kicks her engines up without a word (see {@link
 * RocketControls}). Its cap and its flame glow as its LOOK has it: unlit orange while it cannot boost, green ready,
 * pulsing orange while boosting, and red while it rests after a boost, a ring of four segments round its foot lighting
 * one by one as it rests, a rising beep as it is ready again; one red blink when a press is refused.
 */
public class RocketBoostButtonBlock extends RocketControlBlock {
    private static final MapCodec<RocketBoostButtonBlock> CODEC = simpleCodec(RocketBoostButtonBlock::new);
    /** What its cap and the ring round its foot show. */
    public static final EnumProperty<Look> LOOK = EnumProperty.create("look", Look.class);
    /** The module, and the pedestal with the cap on top and the ring round its foot, behind its plate. */
    private static final VoxelShape SHAPE = ConsoleModule.behindPlate(4.5, 5.5);
    /** How many segments the ring round its foot has, lighting one by one as it rests after a boost. */
    private static final int SEGMENTS = 4;
    /** How loud the beep as it is ready again is, as a share of full. */
    private static final float READY_VOLUME = 0.7F;
    /** How much light its cap gives, of the fifteen, boosting and lit otherwise. */
    private static final int BOOSTING_LIGHT = 9;
    private static final int LAMP_LIGHT = 4;

    /** What the cap shows: dark, ready, boosting, resting with so many of the ring's segments lit, or refused. */
    public enum Look implements StringRepresentable {
        DARK, READY, BOOSTING, COOLING_0, COOLING_1, COOLING_2, COOLING_3, REFUSED;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Resting after a boost, so much of the rest done: a segment of the ring lit for each quarter. */
        static Look cooling(double rested) {
            return values()[COOLING_0.ordinal() + Mth.clamp((int) (rested * SEGMENTS), 0, SEGMENTS - 1)];
        }

        boolean resting() {
            return ordinal() >= COOLING_0.ordinal() && ordinal() <= COOLING_3.ordinal();
        }
    }

    public RocketBoostButtonBlock(Properties properties) {
        super(properties, SHAPE);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(LOOK, Look.DARK));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOOK);
    }

    @Override
    RocketControls.Control control() {
        return RocketControls.Control.BOOST;
    }

    @Override
    BlockState shown(BlockState state, RocketControls.Panel panel, boolean blinking) {
        Look look = blinking ? Look.REFUSED : switch (panel.boost()) {
            case DARK -> Look.DARK;
            case READY -> Look.READY;
            case BOOSTING -> Look.BOOSTING;
            case COOLING -> Look.cooling(panel.rested());
        };
        return state.setValue(LOOK, look);
    }

    /** Rested and ready to boost again: a rising beep. */
    @Override
    void changed(ServerLevel level, BlockPos pos, BlockState was, BlockState now) {
        if (was.getValue(LOOK).resting() && now.getValue(LOOK) == Look.READY) {
            level.playSound(null, pos, SkylinersRegistry.ROCKET_BOOST_READY.get(), SoundSource.BLOCKS, READY_VOLUME, 1.0F);
        }
    }

    /** How brightly its cap glows: brightest boosting, softly ready, resting or refused, not at all dark. */
    public static int light(BlockState state) {
        return switch (state.getValue(LOOK)) {
            case DARK -> 0;
            case BOOSTING -> BOOSTING_LIGHT;
            default -> LAMP_LIGHT;
        };
    }
}
