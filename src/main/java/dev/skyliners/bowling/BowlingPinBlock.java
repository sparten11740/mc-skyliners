package dev.skyliners.bowling;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.Mount;
import dev.skyliners.block.Mounting;
import dev.skyliners.block.TurnedShapes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
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
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A bowling pin, for the freighter's bowling lane down her starboard prong (see {@code airframe.FreighterBowling}):
 * white, a red stripe round its neck, standing on a floor. A bowling ball rolling into it knocks it down with a wooden
 * clonk (see {@link BowlingBall}), and as it goes over it wobbles the pins in the row behind it, each of which most
 * likely topples a moment later and wobbles the row behind that, so a ball into the head pin scatters the rack as a
 * real one does. Once every pin round it is down, a strike: a fanfare and a shower of sparks over the pins. A few
 * seconds after it fell the pinsetter stands it up again where it stood; a pin whose moment was lost, as when her ship
 * was assembled with it down, goes over or stands up by itself a while later, or at once when it is used or struck.
 *
 * <p>FACING is the way its front looks, toward the bowler; it falls away from them. SHIFTED draws it half a block to
 * the bowler's right, between its block and the next, so a rack of ten stands four blocks wide as on a real lane, its
 * rows by turns on the blocks' middles and between them; using a standing pin nudges it across, so a rack set down by
 * hand can be lined up. POSE is whether it stands, wobbles on its way over or lies fallen, and ON_LANE whether it
 * stands on a square of a lane's boards, set down where they were, as a lane's rack does. It is set down only on a
 * floor, as a pin is: fixed to a wall or under a ceiling it would make no sense. It is solid as drawn to everyone, so
 * whoever walks down the lane bumps into the rack; only the ball rolls on into it, knocking it over.
 */
public class BowlingPinBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<BowlingPinBlock> CODEC = simpleCodec(BowlingPinBlock::new);
    /** Whether it stands on a square of a lane's boards, in place of the boards in its block, as a lane's rack does. */
    public static final BooleanProperty ON_LANE = BooleanProperty.create("on_lane");
    /** Whether it is drawn half a block to the bowler's right of its block's middle. */
    public static final BooleanProperty SHIFTED = BooleanProperty.create("shifted");
    /** Whether it stands, wobbles on its way over, or lies fallen. */
    public static final EnumProperty<Pose> POSE = EnumProperty.create("pose", Pose.class);
    /** The mounts a pin takes: a floor alone. */
    private static final List<Mount> MOUNTS = List.of(Mount.FLOOR);
    /**
     * Its shape standing, round its block's middle, and lying, from its foot at the middle out the way it fell, facing
     * north, in pixels; how far across it is drawn shifted, to the bowler's right, who stands to the north, so to the
     * west, in blocks; and the square of a lane's boards under it, a pixel thick, which raises it by as much.
     */
    private static final VoxelShape STANDING = Block.box(6, 0, 6, 10, 13, 10);
    private static final VoxelShape LYING = Block.box(6, 0, 3, 10, 4, 16);
    private static final double SHIFT = 0.5;
    private static final VoxelShape BOARD = Block.box(0, 0, 0, 16, 1, 16);
    /** How many shapes a pin takes facing one way: shifted or not, lying or standing, on a lane or not. */
    private static final int SHAPE_KINDS = 8;
    /** Its shapes facing each way, by {@link #shapeIndex}: shifted or not, lying or standing, on a lane or not. */
    private static final List<Map<Direction, VoxelShape>> SHAPES = shapes();
    /**
     * How long a pin wobbles before it goes over, and how long it lies before the pinsetter stands it up again, in
     * ticks; and how likely a pin going over is to topple each pin it touches in the row behind it.
     */
    private static final int WOBBLE_TICKS = 3;
    private static final int RESET_TICKS = 100;
    private static final float TOPPLE_CHANCE = 0.8F;
    /**
     * How far across the lane a pin in the row behind may stand from one going over and still be toppled by it, in
     * blocks: the two behind it in a rack, half a block either side, not the next but one.
     */
    private static final double TOPPLE_ACROSS = 0.75;
    /** How far round a fallen pin the rest of its rack is looked for, in blocks across and along the lane. */
    private static final int RACK_REACH = 4;
    /** How many pins a rack takes at least for all of them down to count as a strike, not a pin or two set out. */
    private static final int STRIKE_LEAST = 6;
    /** How loud a pin's clonk is, and how far its pitch strays either way. */
    private static final float CLONK_VOLUME = 0.9F;
    private static final float CLONK_SPREAD = 0.15F;
    /** How loud and high the knock of a pin stood up again is, and of one nudged across. */
    private static final float RESET_VOLUME = 0.6F;
    private static final float RESET_PITCH = 1.3F;
    private static final float NUDGE_VOLUME = 0.8F;
    private static final float NUDGE_PITCH = 1.4F;
    /**
     * How many sparks a strike throws up over the rack, how far they spread across and up, in blocks, and how fast,
     * and how loud the fanfare is.
     */
    private static final int STRIKE_SPARKS = 40;
    private static final double SPARKS_ACROSS = 1.0;
    private static final double SPARKS_UP = 0.5;
    private static final double SPARKS_SPEED = 0.1;
    private static final float FANFARE_VOLUME = 1.0F;
    /** Which blocks of the row behind a pin going over it may touch: the one straight behind and one either side. */
    private static final int BEHIND_REACH = 1;

    /** How a pin stands: upright, wobbling on its way over, or fallen. */
    public enum Pose implements StringRepresentable {
        STANDING,
        WOBBLING,
        FALLEN;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public BowlingPinBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SHIFTED, false).setValue(POSE, Pose.STANDING)
            .setValue(ON_LANE, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SHIFTED, POSE, ON_LANE);
    }

    /**
     * Set down standing on a floor, facing whoever sets it down; set down on a lane's boards, in their place, standing
     * on a square of them (see {@link BowlingLaneBlock#canBeReplaced}); nowhere that no floor holds it.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (Mounting.support(context, MOUNTS) == null) {
            return null;
        }
        boolean onLane = context.getLevel().getBlockState(context.getClickedPos()).getBlock() instanceof BowlingLaneBlock;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(ON_LANE, onLane);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shape(state);
    }

    /** Its shape as it stands or lies, where it is drawn in its block, on its square of boards if it has one. */
    private static VoxelShape shape(BlockState state) {
        boolean lying = state.getValue(POSE) == Pose.FALLEN;
        return SHAPES.get(shapeIndex(state.getValue(SHIFTED), lying, state.getValue(ON_LANE))).get(state.getValue(FACING));
    }

    /** Where among {@link #SHAPES} the shape for a pin shifted or not, lying or standing, on a lane or not is. */
    private static int shapeIndex(boolean shifted, boolean lying, boolean onLane) {
        return (shifted ? 1 : 0) + (lying ? 2 : 0) + (onLane ? 4 : 0);
    }

    /** Every shape a pin takes, facing north, turned each way, in the order of {@link #shapeIndex}. */
    private static List<Map<Direction, VoxelShape>> shapes() {
        List<Map<Direction, VoxelShape>> shapes = new ArrayList<>();
        for (int index = 0; index < SHAPE_KINDS; index++) {
            VoxelShape pin = ((index & 2) != 0 ? LYING : STANDING).move((index & 1) != 0 ? -SHIFT : 0, 0, 0);
            shapes.add(TurnedShapes.of((index & 4) != 0 ? Shapes.or(BOARD, pin.move(0, BOARD.max(Direction.Axis.Y), 0)) : pin));
        }
        return List.copyOf(shapes);
    }

    /** Solid as drawn, but to the ball, which rolls on into a pin to knock it over (see {@link BowlingBall}). */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean ball = context instanceof EntityCollisionContext rolling && rolling.getEntity() instanceof BowlingBall;
        return ball ? Shapes.empty() : shape(state);
    }

    /**
     * Used standing, it is nudged half a block across, or back, with a knock of wood: to line a rack up by hand. Used
     * wobbling, it goes over; used fallen, it is stood up again at once, as the pinsetter would.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && state.getValue(POSE) != Pose.STANDING) {
            settle(server, pos, state);
        } else if (!level.isClientSide) {
            level.setBlockAndUpdate(pos, state.cycle(SHIFTED));
            level.playSound(null, pos, SoundEvents.BAMBOO_WOOD_HIT, SoundSource.BLOCKS, NUDGE_VOLUME, NUDGE_PITCH);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Struck standing or wobbling, as a child kicking at it would, it goes over as a ball would knock it. */
    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (level instanceof ServerLevel server && state.getValue(POSE) != Pose.FALLEN) {
            knock(server, pos, state);
        }
    }

    /**
     * Where its middle stands across the floor, x and z, among the blocks it stands with (a ship's plot or the world):
     * its block's middle, or half a block to the bowler's right of it if shifted.
     */
    public static double[] middle(BlockState state, BlockPos pos) {
        Direction right = state.getValue(FACING).getCounterClockWise();
        double shift = state.getValue(SHIFTED) ? SHIFT : 0;
        return new double[]{pos.getX() + 0.5 + right.getStepX() * shift, pos.getZ() + 0.5 + right.getStepZ() * shift};
    }

    /** Whether a box, in the pin's level, touches a pin standing at a block: what a ball rolling into it does. */
    public static boolean touches(BlockState state, BlockPos pos, AABB box) {
        boolean onLane = state.getValue(ON_LANE);
        VoxelShape pin = SHAPES.get(shapeIndex(state.getValue(SHIFTED), false, false)).get(state.getValue(FACING));
        return state.getValue(POSE) == Pose.STANDING && pin.bounds().move(pos).move(0, onLane ? BOARD.max(Direction.Axis.Y) : 0, 0).intersects(box);
    }

    /**
     * Knocks a standing or wobbling pin over: it lies the way it faces away from, clonking, wobbles the pins it touches
     * in the row behind it, and the pinsetter is due to stand it up again; with every other pin of its rack down too,
     * a strike.
     */
    public static void knock(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state.setValue(POSE, Pose.FALLEN));
        level.scheduleTick(pos, state.getBlock(), RESET_TICKS);
        level.playSound(null, pos, SkylinersRegistry.BOWLING_PIN_CLONK.get(), SoundSource.BLOCKS, CLONK_VOLUME,
            1 + (level.random.nextFloat() * 2 - 1) * CLONK_SPREAD);
        wobbleBehind(level, pos, state);
        if (rackDown(level, pos, state)) {
            strike(level, pos);
        }
    }

    /**
     * Wobbles each standing pin in the row behind a pin going over, the way it falls, near enough across the lane to be
     * touched, most likely: each goes over a moment later in its own turn.
     */
    private static void wobbleBehind(ServerLevel level, BlockPos pos, BlockState state) {
        Direction away = state.getValue(FACING).getOpposite();
        Direction side = away.getClockWise();
        double[] from = middle(state, pos);
        for (int step = -BEHIND_REACH; step <= BEHIND_REACH; step++) {
            BlockPos behind = pos.relative(away).relative(side, step);
            BlockState pin = level.getBlockState(behind);
            if (!(pin.getBlock() instanceof BowlingPinBlock) || pin.getValue(POSE) != Pose.STANDING || level.random.nextFloat() >= TOPPLE_CHANCE) {
                continue;
            }
            double[] to = middle(pin, behind);
            double across = (to[0] - from[0]) * side.getStepX() + (to[1] - from[1]) * side.getStepZ();
            if (Math.abs(across) <= TOPPLE_ACROSS) {
                level.setBlockAndUpdate(behind, pin.setValue(POSE, Pose.WOBBLING));
                level.scheduleTick(behind, pin.getBlock(), WOBBLE_TICKS);
            }
        }
    }

    /** Whether every pin of the rack round a pin is down, a rack being enough pins to bowl at, not one set out. */
    private static boolean rackDown(ServerLevel level, BlockPos pos, BlockState state) {
        int pins = 0;
        for (BlockPos at : BlockPos.betweenClosed(pos.offset(-RACK_REACH, 0, -RACK_REACH), pos.offset(RACK_REACH, 0, RACK_REACH))) {
            BlockState pin = level.getBlockState(at);
            if (!(pin.getBlock() instanceof BowlingPinBlock)) {
                continue;
            }
            if (pin.getValue(POSE) != Pose.FALLEN) {
                return false;
            }
            pins++;
        }
        return pins >= STRIKE_LEAST;
    }

    /** A strike: a fanfare, and sparks thrown up over the rack. */
    private static void strike(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SkylinersRegistry.BOWLING_STRIKE.get(), SoundSource.BLOCKS, FANFARE_VOLUME, 1.0F);
        level.sendParticles(ParticleTypes.FIREWORK, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, STRIKE_SPARKS,
            SPARKS_ACROSS, SPARKS_UP, SPARKS_ACROSS, SPARKS_SPEED);
    }

    /** A wobbling pin goes over; a fallen one is stood up again by the pinsetter. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        settle(level, pos, state);
    }

    /** A pin not standing ticks now and then, so one whose scheduled moment was lost still settles in a while. */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(POSE) != Pose.STANDING;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        settle(level, pos, state);
    }

    /** Takes a pin on from where it is: a wobbling one goes over, a fallen one stands up again. */
    private static void settle(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.getValue(POSE) == Pose.WOBBLING) {
            knock(level, pos, state);
        } else if (state.getValue(POSE) == Pose.FALLEN) {
            standUp(level, pos, state);
        }
    }

    /** Stands a fallen pin up again where it stood, with a soft knock as it is set down. */
    private static void standUp(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlockAndUpdate(pos, state.setValue(POSE, Pose.STANDING));
        level.playSound(null, pos, SoundEvents.BAMBOO_WOOD_PLACE, SoundSource.BLOCKS, RESET_VOLUME, RESET_PITCH);
    }
}
