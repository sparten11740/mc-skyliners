package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The pool's wave machine: a white post by the water with a big blue button on top, a wave drawn on it. Pressed, the
 * pumps start with a rising whine and the pool surges for ten seconds (RUNNING): the button sinks and glows, its wave
 * rolling, the ring round it lights up, and the water all along the channel churns with splashes and bubbles (drawn on
 * the client, see {@link WaveMachineBlockEntity}) to the sound of waves washing. Pressed again while it runs, it only
 * clicks.
 */
public class WaveMachineBlock extends BaseEntityBlock {
    private static final MapCodec<WaveMachineBlock> CODEC = simpleCodec(WaveMachineBlock::new);
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    /** How long the waves run for, in ticks. */
    private static final int WAVE_TICKS = 200;
    /** How loud its click is when it is pressed while running. */
    private static final float BUSY_VOLUME = 0.4F;
    /** How bright its button and ring glow, idle and running, in light levels. */
    private static final int IDLE_GLOW = 4;
    private static final int RUNNING_GLOW = 10;
    /** A post with a head and a button on it. */
    private static final VoxelShape SHAPE = Block.box(3.5, 0, 3.5, 12.5, 14.5, 12.5);

    public WaveMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(RUNNING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(RUNNING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** How bright its button and ring glow: softly idle, bright while the waves run. */
    public static int glow(BlockState state) {
        return state.getValue(RUNNING) ? RUNNING_GLOW : IDLE_GLOW;
    }

    /**
     * Starts the waves when idle, with the pumps' whine and the waves' wash; while they run, only a click, or their end
     * at once where it was lost, as when the rocket was assembled while they ran.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || player.isSpectator()) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (state.getValue(RUNNING)) {
            server.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, BUSY_VOLUME, 1.0F);
            LostTicks.rearm(server, pos, this, 1);
            return InteractionResult.SUCCESS;
        }
        server.setBlock(pos, state.setValue(RUNNING, true), Block.UPDATE_ALL);
        server.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, BUSY_VOLUME, 1.0F);
        server.playSound(null, pos, SkylinersRegistry.WAVE_SURGE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        server.scheduleTick(pos, this, WAVE_TICKS);
        return InteractionResult.SUCCESS;
    }

    /** The waves die down, the button rising again. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(RUNNING)) {
            level.setBlock(pos, state.setValue(RUNNING, false), Block.UPDATE_ALL);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WaveMachineBlockEntity(pos, state);
    }

    /** The waves' splashes on the client while they run; on the server, their end kept to where it was lost. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, SkylinersRegistry.WAVE_MACHINE_ENTITY.get(),
            level.isClientSide ? WaveMachineBlockEntity::clientTick : WaveMachineBlockEntity::serverTick);
    }
}
