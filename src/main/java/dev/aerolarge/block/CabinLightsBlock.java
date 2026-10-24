package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import java.util.function.UnaryOperator;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The cabin lights' dimmer on the flight deck: each use steps the whole cabin from bright to dimmed (as for take-off
 * and landing) to the low glow of a night flight and back, every light that has a {@link Brightness}. While a door
 * stands open in flight, the airliner turns every one of its lights red (see {@link #alarm}), and back as they were once
 * the doors are shut: the dimmer keeps its own setting through it.
 */
public class CabinLightsBlock extends CabinControlBlock {
    private static final MapCodec<CabinLightsBlock> CODEC = simpleCodec(CabinLightsBlock::new);

    public CabinLightsBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(Brightness.PROPERTY, Brightness.BRIGHT)
            .setValue(Brightness.ALARM, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, Brightness.PROPERTY, Brightness.ALARM);
    }

    @Override
    protected BlockState next(BlockState control) {
        return control.setValue(Brightness.PROPERTY, control.getValue(Brightness.PROPERTY).next());
    }

    @Override
    protected Component describe(BlockState control) {
        return Component.translatable("message.aerolarge.cabin_lights." + control.getValue(Brightness.PROPERTY).getSerializedName());
    }

    @Override
    public boolean works(BlockState target) {
        return target.hasProperty(Brightness.PROPERTY);
    }

    /**
     * Turns the lights of the airliner whose autopilot this is red for a door open in flight, or back as they were:
     * its dimmer, on the flight deck by the autopilot, keeps the alarm with its own setting and sweeps it through the
     * cabin as it does its brightness. Nothing where the dimmer already has it so.
     */
    public static void alarm(ServerLevel level, BlockEntity autopilot, boolean on) {
        setDimmer(level, autopilot, state -> state.setValue(Brightness.ALARM, on));
    }

    /**
     * Sets the cabin lights of the airliner whose autopilot this is to a brightness, as its dimmer would step them
     * there: a cabin announcement dims them for a sleep. Nothing where the dimmer already has them so.
     */
    public static void set(ServerLevel level, BlockEntity autopilot, Brightness brightness) {
        setDimmer(level, autopilot, state -> state.setValue(Brightness.PROPERTY, brightness));
    }

    /**
     * Changes the dimmer of the airliner whose autopilot this is, on the flight deck by the autopilot, and sweeps the
     * change through the cabin; nothing where the change leaves it as it was.
     */
    private static void setDimmer(ServerLevel level, BlockEntity autopilot, UnaryOperator<BlockState> change) {
        for (CabinControlBlockEntity control : CabinControlBlockEntity.all(level)) {
            BlockState state = control.getBlockState();
            if (!(state.getBlock() instanceof CabinLightsBlock) || !control.getBlockPos().closerThan(autopilot.getBlockPos(), AutopilotBlockEntity.FLIGHT_DECK)) {
                continue;
            }
            BlockState changed = change.apply(state);
            if (changed != state) {
                level.setBlock(control.getBlockPos(), changed, Block.UPDATE_ALL);
                control.sweep();
            }
        }
    }

    /** Sets the light to the dimmer's brightness, and red for the alarm if the dimmer has it so. */
    @Override
    public BlockState applyTo(BlockState control, BlockState target) {
        if (!works(target)) {
            return target;
        }
        return target.setValue(Brightness.PROPERTY, control.getValue(Brightness.PROPERTY)).trySetValue(Brightness.ALARM, control.getValue(Brightness.ALARM));
    }
}
