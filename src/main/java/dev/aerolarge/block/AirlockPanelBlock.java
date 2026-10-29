package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The airlock panel by the rocket's door: a steel post with a screen tilted toward whoever stands at it, a door on it
 * under a lamp bar, which shows how the airlock stands (CYCLE). Sealed, the door is shut under a red bar. Used, it
 * cycles: the latches clunk, air hisses out of the vents at its foot in a cloud and chevrons sweep amber along the bar,
 * and after a few seconds a chime rings and the door on the screen stands open under a green bar, clear to go, until it
 * seals itself again with a clunk; used again before then, it only clicks. It is only to play with: the door itself is
 * always open. FACING is the way its screen faces.
 */
public class AirlockPanelBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<AirlockPanelBlock> CODEC = simpleCodec(AirlockPanelBlock::new);
    public static final EnumProperty<Cycle> CYCLE = EnumProperty.create("cycle", Cycle.class);
    /** How long the airlock takes to cycle and how long it stays clear before it seals again, in ticks. */
    private static final int CYCLE_TICKS = 60;
    private static final int CLEAR_TICKS = 200;
    /** How many puffs of air leave its vents as it cycles, how far they spread and how fast, in blocks and blocks a tick. */
    private static final int PUFFS = 14;
    private static final double PUFF_SPREAD = 0.3;
    private static final double PUFF_SPEED = 0.03;
    /** How high over its foot its vents are, and how far up from them its puffs of air spread, in blocks. */
    private static final double VENTS_UP = 0.15;
    private static final double PUFF_RISE = 0.05;
    /** How loud its seal's clunk is as it seals again, and its click when it is used while busy. */
    private static final float SEAL_VOLUME = 0.6F;
    private static final float BUSY_VOLUME = 0.4F;
    /** How bright its screen glows, sealed, cycling and clear, in light levels. */
    private static final int SEALED_GLOW = 4;
    private static final int CYCLING_GLOW = 7;
    private static final int CLEAR_GLOW = 9;
    /** Facing north: a foot, a post and the screen's head, its outline round them. */
    private static final Map<Direction, VoxelShape> SHAPES = TurnedShapes.of(Block.box(3, 0, 3, 13, 15, 12));

    /** How the airlock stands. */
    public enum Cycle implements StringRepresentable {
        SEALED,
        CYCLING,
        CLEAR;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public AirlockPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CYCLE, Cycle.SEALED));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CYCLE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** How bright its screen glows: dim red sealed, brighter as it cycles, brightest clear. */
    public static int glow(BlockState state) {
        return switch (state.getValue(CYCLE)) {
            case SEALED -> SEALED_GLOW;
            case CYCLING -> CYCLING_GLOW;
            case CLEAR -> CLEAR_GLOW;
        };
    }

    /**
     * Starts a cycle when sealed: the clunk and the hiss, and a cloud of air from its vents; while it cycles or stands
     * clear, only a click, as its cycle runs on to its end, or moves on at once where its next step was lost, as when
     * the rocket was assembled in the middle of one.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || player.isSpectator()) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (state.getValue(CYCLE) != Cycle.SEALED) {
            server.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, BUSY_VOLUME, 1.0F);
            if (!server.getBlockTicks().hasScheduledTick(pos, this)) {
                server.scheduleTick(pos, this, 1);
            }
            return InteractionResult.SUCCESS;
        }
        server.setBlock(pos, state.setValue(CYCLE, Cycle.CYCLING), Block.UPDATE_ALL);
        server.playSound(null, pos, AerolargeRegistry.AIRLOCK_CYCLE.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        server.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + VENTS_UP, pos.getZ() + 0.5, PUFFS, PUFF_SPREAD, PUFF_RISE,
            PUFF_SPREAD, PUFF_SPEED);
        server.scheduleTick(pos, this, CYCLE_TICKS);
        return InteractionResult.SUCCESS;
    }

    /** Cycled, the airlock is clear, with a chime; clear a while, it seals again with a clunk. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        switch (state.getValue(CYCLE)) {
            case CYCLING -> {
                level.setBlock(pos, state.setValue(CYCLE, Cycle.CLEAR), Block.UPDATE_ALL);
                level.playSound(null, pos, AerolargeRegistry.AIRLOCK_CLEAR.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
                level.scheduleTick(pos, this, CLEAR_TICKS);
            }
            case CLEAR -> {
                level.setBlock(pos, state.setValue(CYCLE, Cycle.SEALED), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, SEAL_VOLUME, 1.0F);
            }
            case SEALED -> {
            }
        }
    }
}
