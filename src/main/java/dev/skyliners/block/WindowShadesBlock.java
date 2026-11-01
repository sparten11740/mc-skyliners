package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The window shades' control on the flight deck: each use runs every cabin window's shade down, or up again, front
 * to back, with the whirr of their motors.
 */
public class WindowShadesBlock extends CabinControlBlock {
    private static final MapCodec<WindowShadesBlock> CODEC = simpleCodec(WindowShadesBlock::new);
    public static final BooleanProperty SHADED = CabinWindowBlock.SHADED;

    public WindowShadesBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(SHADED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHADED);
    }

    @Override
    protected BlockState next(BlockState control) {
        return control.setValue(SHADED, !control.getValue(SHADED));
    }

    @Override
    protected Component describe(BlockState control) {
        return Component.translatable(control.getValue(SHADED) ? "message.skyliners.window_shades.closed" : "message.skyliners.window_shades.open");
    }

    @Override
    public boolean works(BlockState target) {
        return CabinWindowBlock.isPane(target);
    }

    @Override
    public BlockState applyTo(BlockState control, BlockState target) {
        return works(target) ? target.setValue(SHADED, control.getValue(SHADED)) : target;
    }

    @Override
    public SoundEvent sweepSound() {
        return SkylinersRegistry.SHADE_MOTOR.get();
    }
}
