package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.FirstFlight;
import dev.aerolarge.compat.SableFlight;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A basketball hoop: an orange ring with its net, on a bracket to the backboard. Only the bracket is solid, so a ball
 * drops through the ring, and a ball whose middle drops through near enough its centre scores for the hoop's side:
 * two points, or three thrown from beyond the three-point line, on every scoreboard nearby. FACING is the way to the
 * backboard; sneak and use it with an empty hand to change the side it counts for.
 */
public class HoopBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<HoopBlock> CODEC = simpleCodec(HoopBlock::new);
    public static final EnumProperty<Team> TEAM = EnumProperty.create("team", Team.class);
    /**
     * The ring's height in the block, and how near its middle a ball's middle has to drop through it, in blocks: as
     * near as a ball that grazes the rim on its way in, its middle a ball's half width inside the ring's outer edge.
     */
    private static final double RING_Y = 10.5 / 16;
    private static final double INNER_RADIUS = 0.45;
    /** Thrown from farther than this, across the floor, a basket is worth three: the real line, in metres, scaled like the airliner. */
    private static final double THREE_POINT_METRES = 6.75;
    /** Scoreboards this close to the hoop count its baskets, and players this close hear of them. */
    private static final double REACH = 48;
    /** Moving faster than this, in blocks a second, a hoop is aboard an airliner in flight. */
    private static final double IN_FLIGHT = 10;
    private static final VoxelShape RING = Shapes.or(
        Block.box(2, 10, 2, 14, 11, 3.5), Block.box(2, 10, 12.5, 14, 11, 14),
        Block.box(2, 10, 3.5, 3.5, 11, 12.5), Block.box(12.5, 10, 3.5, 14, 11, 12.5)
    );
    private static final Map<Direction, VoxelShape> BRACKETS = new EnumMap<>(Map.of(
        Direction.NORTH, Block.box(7, 9, 0, 9, 11, 2),
        Direction.SOUTH, Block.box(7, 9, 14, 9, 11, 16),
        Direction.WEST, Block.box(0, 9, 7, 2, 11, 9),
        Direction.EAST, Block.box(14, 9, 7, 16, 11, 9)
    ));

    /** The two sides of a game, as the scoreboard shows them. */
    public enum Team implements StringRepresentable {
        HOME, GUEST;

        public Team other() {
            return this == HOME ? GUEST : HOME;
        }

        public Component label() {
            return Component.translatable("team.aerolarge." + getSerializedName());
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public HoopBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TEAM, Team.HOME));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TEAM);
    }

    /** Put up against a wall, its ring out from it; not on a floor or a ceiling, where it would hang on nothing. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        return clicked.getAxis().isHorizontal() ? defaultBlockState().setValue(FACING, clicked.getOpposite()) : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.or(RING, BRACKETS.get(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return BRACKETS.get(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            Team team = state.getValue(TEAM).other();
            level.setBlock(pos, state.setValue(TEAM, team), Block.UPDATE_ALL);
            player.displayClientMessage(Component.translatable("message.aerolarge.hoop.team", team.label()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The middle of a hoop's ring, in its own frame. */
    public static Vec3 ringMiddle(BlockPos hoop) {
        return new Vec3(hoop.getX() + 0.5, hoop.getY() + RING_Y, hoop.getZ() + 0.5);
    }

    /** Whether a ball's middle moving from one point to the next, in the hoop's own frame, dropped down through its ring. */
    public static boolean dropsThrough(BlockPos hoop, Vec3 from, Vec3 to) {
        Vec3 ring = ringMiddle(hoop);
        if (!(from.y > ring.y && to.y <= ring.y)) {
            return false;
        }
        Vec3 crossing = from.lerp(to, (from.y - ring.y) / (from.y - to.y));
        return Math.hypot(crossing.x - ring.x, crossing.z - ring.z) < INNER_RADIUS;
    }

    /** A basket: the points go up on every scoreboard near the hoop, with a swish, a burst of sparks and a word to all around. */
    public static void score(ServerLevel level, BlockPos hoop, @Nullable Player thrower) {
        BlockState state = level.getBlockState(hoop);
        if (!(state.getBlock() instanceof HoopBlock)) {
            return;
        }
        Team team = state.getValue(TEAM);
        Vec3 at = SableFlight.toWorld(level, Vec3.atCenterOf(hoop));
        double threePoint = THREE_POINT_METRES * AerolargeConfig.SCALE.get();
        int points = thrower != null && Math.hypot(thrower.getX() - at.x, thrower.getZ() - at.z) > threePoint ? 3 : 2;
        List<ScoreboardBlockEntity> boards = ScoreboardBlockEntity.all(level).stream()
            .filter(board -> board.whereInWorld().distanceToSqr(at) < REACH * REACH)
            .toList();
        boards.forEach(board -> board.add(team, points));
        level.playSound(null, at.x, at.y, at.z, AerolargeRegistry.BASKET_SWISH.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        if (thrower instanceof ServerPlayer scorer && SableFlight.velocity(level, Vec3.atCenterOf(hoop)).length() > IN_FLIGHT) {
            FirstFlight.SWISH.award(scorer);
        }
        level.sendParticles(ParticleTypes.FIREWORK, at.x, at.y, at.z, 20, 0.3, 0.2, 0.3, 0.08);
        Component message = boards.isEmpty()
            ? Component.translatable("message.aerolarge.hoop.basket", points, team.label())
            : Component.translatable("message.aerolarge.hoop.basket_score", points, team.label(), boards.getFirst().describe());
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceToSqr(at) < REACH * REACH) {
                player.displayClientMessage(message.copy().withStyle(ChatFormatting.GOLD), true);
            }
        }
    }
}
