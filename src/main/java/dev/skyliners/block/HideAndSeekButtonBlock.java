package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.compat.SableFlight;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The hide and seek button: a big round button on a low stand by a hoop, an open eye on it and a hand on its front,
 * so it needs no reading. Pressing it starts the countdown on every scoreboard near it (see
 * {@link ScoreboardBlockEntity}), and it stays down, its eye shut and glowing, until the score comes back; pressed
 * again, the countdown starts over. FACING is the way its front looks.
 */
public class HideAndSeekButtonBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<HideAndSeekButtonBlock> CODEC = simpleCodec(HideAndSeekButtonBlock::new);
    public static final BooleanProperty PRESSED = BooleanProperty.create("pressed");
    private static final VoxelShape STAND = Block.box(2, 0, 2, 14, 8, 14);
    private static final VoxelShape UP = Shapes.or(STAND, Block.box(4, 8, 4, 12, 10, 12));
    private static final VoxelShape DOWN = Shapes.or(STAND, Block.box(4, 8, 4, 12, 9, 12));
    /** Scoreboards this close to the button count down: the gym hall's two, one at either end. */
    private static final double REACH = 48;
    /** The click's pitch: a fat button's, lower than a stone one's. */
    private static final float CLICK_PITCH = 0.7F;
    /** The burst of happy sparks over the button: how many, how far they spread. */
    private static final int SPARKS = 10;
    private static final double SPARK_SPREAD = 0.3;

    public HideAndSeekButtonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PRESSED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PRESSED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PRESSED) ? DOWN : UP;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            Vec3 at = SableFlight.toWorld(server, Vec3.atCenterOf(pos));
            List<ScoreboardBlockEntity> boards = boardsNear(server, at);
            server.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1.0F, CLICK_PITCH);
            if (!boards.isEmpty()) {
                boards.forEach(ScoreboardBlockEntity::startCountdown);
                server.setBlock(pos, state.setValue(PRESSED, true), Block.UPDATE_ALL);
                server.scheduleTick(pos, this, ScoreboardBlockEntity.COUNTDOWN_TICKS);
                server.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 0.3, at.z, SPARKS, SPARK_SPREAD, SPARK_SPREAD, SPARK_SPREAD, 0);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Back up once no scoreboard near is counting down any more: one started over keeps it down a while longer. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(PRESSED)) {
            return;
        }
        Vec3 at = SableFlight.toWorld(level, Vec3.atCenterOf(pos));
        if (boardsNear(level, at).stream().anyMatch(board -> board.countdown().isPresent())) {
            level.scheduleTick(pos, this, SharedConstants.TICKS_PER_SECOND);
            return;
        }
        level.setBlock(pos, state.setValue(PRESSED, false), Block.UPDATE_ALL);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 1.0F, CLICK_PITCH);
    }

    /** The scoreboards within reach of a point in the world. */
    private static List<ScoreboardBlockEntity> boardsNear(ServerLevel level, Vec3 at) {
        return ScoreboardBlockEntity.all(level).stream().filter(board -> board.whereInWorld().distanceToSqr(at) < REACH * REACH).toList();
    }
}
