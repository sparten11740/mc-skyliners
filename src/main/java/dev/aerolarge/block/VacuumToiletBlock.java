package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.Aboard;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.FirstFlight;
import dev.aerolarge.compat.SableFlight;
import dev.aerolarge.entity.BlueIceChunk;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An airliner's vacuum toilet: a stainless steel bowl in a white shroud, its seat and a lid that lifts, standing
 * against a wall panel with the square flush button on it. Use it to flush; sneak with an empty hand to lift or lower
 * the lid. A flush is what an airliner's is: a sharp, loud hiss as the vacuum snatches the bowl empty, swirling it,
 * and the thunk of the valve shutting; then the button stays lit for a few seconds while the vacuum builds up again,
 * and it will not flush till then. FACING is the way its front looks, its panel against the wall behind it.
 *
 * <p>A real airliner keeps what it flushes in a tank, but one with a leak is famous for letting it go as "blue ice",
 * the blue toilet water frozen in a chunk under the belly. This one lets it go every time: a chunk of blue ice drops
 * out of the airliner's belly under the toilet (see {@link BlueIceChunk}), at the airliner's own speed, so in flight it
 * falls away behind and far below. A toilet with no airframe under it, in a house, only flushes.
 */
public class VacuumToiletBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<VacuumToiletBlock> CODEC = simpleCodec(VacuumToiletBlock::new);
    /** Whether the lid is up, showing the seat and the bowl. */
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    /** Whether it is flushing, or building up its vacuum again after a flush: its button is lit, and it will not flush. */
    public static final BooleanProperty FLUSHING = BooleanProperty.create("flushing");
    /** How long a flush and the vacuum building up again take, in ticks, before it flushes again. */
    private static final int RECHARGE = 3 * SharedConstants.TICKS_PER_SECOND;
    /** The hiss: loud, as a real one is, heard all through the cabin around the lavatory. */
    private static final float FLUSH_VOLUME = 1.5F;
    /** The bowl's middle in the model drawn facing north, in pixels: where the swirl goes round. */
    private static final double BOWL_X = 8;
    private static final double BOWL_Y = 7;
    private static final double BOWL_Z = 9;
    /**
     * The swirl: how many drops go round the bowl, how far out from its middle in blocks, how fast they fly, and how
     * far in toward the drain each flies for every block it flies round.
     */
    private static final int SWIRL = 16;
    private static final double SWIRL_RADIUS = 0.17;
    private static final double SWIRL_SPEED = 0.12;
    private static final double SWIRL_INWARD = 0.3;
    /**
     * How far below the toilet the airliner's belly is looked for, in blocks per unit of scale: the fuselage is 8.4 m
     * tall. And how far further down a chunk of ice may have to go to leave it, past what hangs under the belly there
     * (a wing root is 1.9 m thick).
     */
    private static final double BELLY_REACH_PER_SCALE = 9;
    private static final double HUNG_BELOW_PER_SCALE = 2;
    /**
     * How high above the ground a chunk has to be let go for Blue Ice, in metres scaled like the airliner: well clear of
     * a runway. Standing as blocks, the airliner is itself the top of the ground under its belly, so only one in flight
     * lets a chunk go that high.
     */
    private static final double AIRBORNE_METRES = 15;
    /**
     * The lid's clack going up or down, light as plastic is, and the button's click pressed while the vacuum builds up:
     * quiet, and higher than a stone button's.
     */
    private static final float LID_VOLUME = 0.6F;
    private static final float LID_PITCH = 1.2F;
    private static final float BUTTON_VOLUME = 0.4F;
    private static final float BUTTON_PITCH = 1.6F;

    /** What can be clicked and bumped into: the wall panel, the shroud and the seat on it, and the lid when up. */
    private static final VoxelShape[] CLOSED = new VoxelShape[4];
    private static final VoxelShape[] OPENED = new VoxelShape[4];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape panel = box(facing, 0, 0, 14, 16, 16, 16);
            CLOSED[facing.get2DDataValue()] = Shapes.or(panel, box(facing, 3, 0, 4, 13, 8.5, 14));
            OPENED[facing.get2DDataValue()] = Shapes.or(panel, box(facing, 3, 0, 4, 13, 7.75, 14), box(facing, 4, 7, 13, 12, 15, 14));
        }
    }

    public VacuumToiletBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(FLUSHING, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, FLUSHING);
    }

    /** It faces whoever puts it down, its panel to the wall behind it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(OPEN) ? OPENED : CLOSED)[state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            if (player.isShiftKeyDown()) {
                boolean open = !state.getValue(OPEN);
                server.setBlock(pos, state.setValue(OPEN, open), Block.UPDATE_ALL);
                server.playSound(null, pos, open ? SoundEvents.WOODEN_TRAPDOOR_OPEN : SoundEvents.WOODEN_TRAPDOOR_CLOSE, SoundSource.BLOCKS, LID_VOLUME, LID_PITCH);
            } else if (state.getValue(FLUSHING)) {
                server.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, BUTTON_VOLUME, BUTTON_PITCH);
            } else {
                flush(server, pos, state, player);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The vacuum is back: the button goes out, and it flushes again. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(FLUSHING)) {
            level.setBlock(pos, state.setValue(FLUSHING, false), Block.UPDATE_ALL);
        }
    }

    /**
     * The flush: the hiss and the swirl in the bowl, and the chunk of blue ice dropped from the belly. The hiss and the
     * swirl are set at the toilet itself, which Sable carries along with an assembled airliner.
     */
    private void flush(ServerLevel level, BlockPos pos, BlockState state, Player player) {
        level.setBlock(pos, state.setValue(FLUSHING, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, this, RECHARGE);
        Direction facing = state.getValue(FACING);
        Vec3 bowl = Vec3.atLowerCornerOf(pos).add(turned(facing, BOWL_X, BOWL_Z).add(0, BOWL_Y / 16, 0));
        level.playSound(null, bowl.x, bowl.y, bowl.z, AerolargeRegistry.TOILET_FLUSH.get(), SoundSource.BLOCKS, FLUSH_VOLUME, 1.0F);
        for (int i = 0; i < SWIRL; i++) {
            double angle = 2 * Math.PI * i / SWIRL;
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            // Round the bowl's rim, each drop flying on round it and in toward the drain; a splash flying level hops.
            level.sendParticles(ParticleTypes.SPLASH, bowl.x + cos * SWIRL_RADIUS, bowl.y, bowl.z + sin * SWIRL_RADIUS, 0,
                -sin - cos * SWIRL_INWARD, 0, cos - sin * SWIRL_INWARD, SWIRL_SPEED);
        }
        level.sendParticles(ParticleTypes.BUBBLE_POP, bowl.x, bowl.y - 0.05, bowl.z, SWIRL / 2, SWIRL_RADIUS / 2, 0.02, SWIRL_RADIUS / 2, 0.02);
        BlockPos below = belly(level, pos);
        if (below == null) {
            return;
        }
        Vec3 at = SableFlight.toWorld(level, Vec3.atCenterOf(below));
        Vec3 velocity = SableFlight.velocity(level, Vec3.atCenterOf(pos)).scale(1.0 / SharedConstants.TICKS_PER_SECOND);
        level.addFreshEntity(BlueIceChunk.of(level, at, velocity));
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
        if (player instanceof ServerPlayer flusher && at.y - ground > AIRBORNE_METRES * AerolargeConfig.SCALE.get()) {
            FirstFlight.BLUE_ICE.award(flusher);
        }
    }

    /**
     * Where a chunk of blue ice leaves the airliner under a toilet: the first block open to the air under the lowest
     * piece of airframe beneath it, past anything hung under that (a wing root, a fairing). Null where no airframe
     * stands beneath the toilet (one put in a house), or nothing opens under it (an airliner sitting on its belly).
     * Assembled, the airliner's plot holds nothing but the airliner, so under its belly is open sky.
     */
    @Nullable
    private static BlockPos belly(Level level, BlockPos toilet) {
        double scale = AerolargeConfig.SCALE.get();
        BlockPos skin = null;
        for (int down = 1; down <= BELLY_REACH_PER_SCALE * scale; down++) {
            if (level.getBlockState(toilet.below(down)).is(Aboard.AIRFRAME)) {
                skin = toilet.below(down);
            }
        }
        if (skin == null) {
            return null;
        }
        for (int down = 1; down <= 1 + HUNG_BELOW_PER_SCALE * scale; down++) {
            BlockPos below = skin.below(down);
            if (level.getBlockState(below).getCollisionShape(level, below).isEmpty() && level.getFluidState(below).isEmpty()) {
                return below;
            }
        }
        return null;
    }

    /** A point given in pixels across and along the model drawn facing north, turned to face the given way: in blocks. */
    private static Vec3 turned(Direction facing, double x, double z) {
        return switch (facing) {
            case SOUTH -> new Vec3(16 - x, 0, 16 - z).scale(1.0 / 16);
            case EAST -> new Vec3(16 - z, 0, x).scale(1.0 / 16);
            case WEST -> new Vec3(z, 0, 16 - x).scale(1.0 / 16);
            default -> new Vec3(x, 0, z).scale(1.0 / 16);
        };
    }

    /** A box given in pixels as drawn facing north, turned to face the given way about the block's middle. */
    private static VoxelShape box(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (facing) {
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }
}
