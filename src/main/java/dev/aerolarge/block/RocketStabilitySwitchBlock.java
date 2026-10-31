package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.autopilot.RocketControls;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A rocket's stability switch, at the commander's inboard hand on the centre console: a toggle under a yellow-and-black
 * striped flip cover, beside a picture of a rocket with her side jets puffing, so it reads as the dangerous one without
 * a word (see {@link RocketControls}). It takes two uses on purpose: the first flips the cover up (OPEN), the second
 * flips the toggle down, switching her stability off; used again, the toggle goes back up, and she is saved. Sneaking
 * and using it snaps the cover shut, switching her stability back on first if it was off. Its LAMP glows green while
 * her jets hold her upright, flashes red while they do not, and glows amber while it stays locked on after it snapped
 * back by itself, when the cover springs shut with a clack; one red blink when a press is refused.
 */
public class RocketStabilitySwitchBlock extends RocketControlBlock {
    private static final MapCodec<RocketStabilitySwitchBlock> CODEC = simpleCodec(RocketStabilitySwitchBlock::new);
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    /** What its lamp shows, and with it which way the toggle stands: down only while her stability is off. */
    public static final EnumProperty<Lamp> LAMP = EnumProperty.create("lamp", Lamp.class);
    /** How loud the cover's clack is, and its pitch: a small lid's, higher than the iron trapdoor it borrows. */
    private static final float CLICK_VOLUME = 0.8F;
    private static final float COVER_PITCH = 1.5F;
    /** How much light its lamp gives while her stability is off, of the fifteen. */
    private static final int OFF_LIGHT = 6;

    /** Holding her upright, off, locked on after it snapped back, or a press refused. */
    public enum Lamp implements StringRepresentable {
        HOLDING, OFF, LOCKED, REFUSED;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public RocketStabilitySwitchBlock(Properties properties) {
        super(properties, ConsoleModule.with(3.5, 4));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(OPEN, false).setValue(LAMP, Lamp.HOLDING));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, LAMP);
    }

    @Override
    RocketControls.Control control() {
        return RocketControls.Control.STABILITY;
    }

    /**
     * Used: sneaking, the cover snaps shut, stability back on first; with the cover shut, it flips up, unless stability
     * is locked on, which only says so; with it up, the toggle is worked.
     */
    @Override
    void use(ServerLevel level, BlockPos pos, BlockState state, RocketControlBlockEntity control, ServerPlayer player) {
        if (player.isShiftKeyDown()) {
            shut(level, pos, state, control, player);
        } else if (!state.getValue(OPEN) && state.getValue(LAMP) != Lamp.LOCKED) {
            level.setBlock(pos, state.setValue(OPEN, true), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, CLICK_VOLUME, COVER_PITCH);
            player.displayClientMessage(Component.translatable("message.aerolarge.rocket_controls.stability_cover"), true);
        } else {
            control.ask(level, player);
        }
    }

    /** The cover snapped shut by a player sneaking, stability switched back on first if it was off. */
    private static void shut(ServerLevel level, BlockPos pos, BlockState state, RocketControlBlockEntity control, ServerPlayer player) {
        if (state.getValue(LAMP) == Lamp.OFF) {
            control.ask(level, player);
        }
        BlockState now = level.getBlockState(pos);
        if (now.getBlock() instanceof RocketStabilitySwitchBlock && now.getValue(OPEN)) {
            level.setBlock(pos, now.setValue(OPEN, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, CLICK_VOLUME, COVER_PITCH);
        }
    }

    /** As her stability stands; snapped back on by itself and locked, the cover springs shut. */
    @Override
    BlockState shown(BlockState state, RocketControls.Panel panel, boolean blinking) {
        Lamp lamp = blinking ? Lamp.REFUSED : switch (panel.stability()) {
            case HOLDING -> Lamp.HOLDING;
            case OFF -> Lamp.OFF;
            case LOCKED -> Lamp.LOCKED;
        };
        BlockState shown = state.setValue(LAMP, lamp);
        return lamp == Lamp.LOCKED ? shown.setValue(OPEN, false) : shown;
    }

    /** The cover springing shut as stability snaps back: its clack. */
    @Override
    void changed(ServerLevel level, BlockPos pos, BlockState was, BlockState now) {
        if (was.getValue(OPEN) && !now.getValue(OPEN)) {
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, CLICK_VOLUME, COVER_PITCH);
        }
    }

    /** How brightly its lamp glows: a little while her stability is off, flashing red, none otherwise. */
    public static int light(BlockState state) {
        return state.getValue(LAMP) == Lamp.OFF ? OFF_LIGHT : 0;
    }
}
