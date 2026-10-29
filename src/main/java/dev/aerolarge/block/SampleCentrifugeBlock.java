package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A benchtop sample centrifuge on the lab's counters: a squat white drum with a round window in its lid over the rotor,
 * its sample tubes in six colours, and on its front a dial and a lamp. Used while it stands still, it spins up with a
 * rising whir, the tubes blurring into a spinning disc behind the window and its lamp turning amber (SPINNING), and a
 * few seconds later spins down and stops with a ding, its lamp green again. Used while it spins, it beeps. The server
 * times the spin with a scheduled tick, set again if it was lost, as when the rocket was moved. FACING is the way its
 * front looks.
 */
public class SampleCentrifugeBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<SampleCentrifugeBlock> CODEC = simpleCodec(SampleCentrifugeBlock::new);
    public static final BooleanProperty SPINNING = BooleanProperty.create("spinning");
    /** How long a spin lasts, in ticks, as long as its whir (see tools/generate_sounds.mjs). */
    private static final int SPIN_TICKS = 80;
    /** How brightly its lamp glows while it spins, in light levels: a small amber light. */
    public static final int SPIN_GLOW = 3;
    /** The beep of its button while it is busy, quiet and high as a touch panel's. */
    private static final float BEEP_VOLUME = 0.5F;
    private static final float BEEP_PITCH = 1.6F;
    /** The drum and its lid, and the dial on its front, drawn facing north. */
    private static final Map<Direction, VoxelShape> SHAPES = TurnedShapes.of(Block.box(2, 0, 2, 14, 7.5, 14));

    public SampleCentrifugeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SPINNING, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SPINNING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Standing still, it spins up; spinning, it beeps, its spin over at once if its tick was lost. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(SPINNING)) {
            server.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, BEEP_VOLUME, BEEP_PITCH);
            if (!server.getBlockTicks().hasScheduledTick(pos, this)) {
                server.scheduleTick(pos, this, 1);
            }
            return InteractionResult.CONSUME;
        }
        server.setBlock(pos, state.setValue(SPINNING, true), Block.UPDATE_ALL);
        server.playSound(null, pos, AerolargeRegistry.CENTRIFUGE_SPIN.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        server.scheduleTick(pos, this, SPIN_TICKS);
        return InteractionResult.CONSUME;
    }

    /** The spin is over: it stops, with a ding. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(SPINNING)) {
            level.setBlock(pos, state.setValue(SPINNING, false), Block.UPDATE_ALL);
            level.playSound(null, pos, AerolargeRegistry.CENTRIFUGE_DING.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
