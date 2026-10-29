package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The greenhouse's irrigation valve: a copper standpipe with a red hand wheel on top, a sight glass in it and a tap
 * at its front. Turn the wheel and it squeaks open, the sight glass runs blue, and every sprinkler within reach pops
 * up and sprays for a while, hissing and ticking round over the beds, bringing on what grows in them (see
 * {@link SprinklerBlockEntity}); then the wheel turns back and the heads drop. While the water is on, turning it again
 * only knocks the pipe. OPEN is whether the water is on; FACING the way its tap looks, toward whoever put it down.
 */
public class IrrigationValveBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<IrrigationValveBlock> CODEC = simpleCodec(IrrigationValveBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** How long the sprinklers spray once it is turned, in ticks: as long as their hiss. */
    public static final int SPRAY = 8 * SharedConstants.TICKS_PER_SECOND;
    /**
     * How far it reaches its sprinklers, in blocks each way and rows over or under it: all through a greenhouse as big
     * as the biggest rocket's, from anywhere in it, and no further than its own story. And how many it turns on at
     * most, the nearest.
     */
    private static final int REACH = 24;
    private static final int ROWS = 2;
    private static final int MOST = 64;
    /** How many of the sprinklers it turns on are heard hissing at most, spread among them, so the hiss never roars. */
    private static final int HEARD = 4;
    /** How loud the valve squeaking and the sprinklers hissing are, and how far a hiss's pitch strays either way. */
    private static final float VALVE_VOLUME = 0.9F;
    private static final float SPRAY_VOLUME = 0.7F;
    private static final float PITCH_STRAY = 0.08F;
    /** The wheel squeaking shut, lower than opening. */
    private static final float SHUT_PITCH = 0.85F;
    /** The pipe knocked while the water is on: soft and low. */
    private static final float KNOCK_VOLUME = 0.5F;
    private static final float KNOCK_PITCH = 0.7F;
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 14, 14);

    public IrrigationValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    /** Its tap faces whoever puts it down. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(OPEN) && server.getBlockTicks().hasScheduledTick(pos, this)) {
            server.playSound(null, pos, SoundEvents.COPPER_BULB_TURN_OFF, SoundSource.BLOCKS, KNOCK_VOLUME, KNOCK_PITCH);
        } else {
            open(server, pos, state);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * An open valve is looked at now and then, in case its spray's end was lost, as when the rocket was assembled while
     * it sprayed; turned while it stands open that way, it opens again and its sprinklers spray once more.
     */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(OPEN);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        LostTicks.rearm(level, pos, this, 1);
    }

    /** The water on: the wheel turned, and every sprinkler in reach spraying until it turns back. */
    private void open(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(OPEN, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, SPRAY);
        level.playSound(null, pos, AerolargeRegistry.IRRIGATION_VALVE_SOUND.get(), SoundSource.BLOCKS, VALVE_VOLUME, 1);
        List<SprinklerBlockEntity> heads = SprinklerBlockEntity.near(level, pos, REACH, ROWS);
        int count = Math.min(heads.size(), MOST);
        int heardEvery = Math.max(1, (count + HEARD - 1) / HEARD);
        long until = level.getGameTime() + SPRAY;
        for (int i = 0; i < count; i++) {
            SprinklerBlockEntity head = heads.get(i);
            head.spray(level, until);
            if (i % heardEvery == 0) {
                level.playSound(null, head.getBlockPos(), AerolargeRegistry.SPRINKLER_SPRAY.get(), SoundSource.BLOCKS, SPRAY_VOLUME, stray(level.getRandom()));
            }
        }
    }

    private static float stray(RandomSource random) {
        return 1 + PITCH_STRAY * (2 * random.nextFloat() - 1);
    }

    /** The spray over: the wheel turns back, squeaking shut. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(OPEN)) {
            level.setBlock(pos, state.setValue(OPEN, false), Block.UPDATE_ALL);
            level.playSound(null, pos, AerolargeRegistry.IRRIGATION_VALVE_SOUND.get(), SoundSource.BLOCKS, VALVE_VOLUME, SHUT_PITCH);
        }
    }
}
