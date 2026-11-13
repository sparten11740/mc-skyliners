package dev.skyliners.block;

import com.google.common.collect.ImmutableMap;
import dev.skyliners.SkylinersRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An LED strip laid by hand (those built round the rocket's and the freighter's rooms are {@link LedStripBlock}s): a
 * thin glowing bar lying on a face of its block, on a floor, under a ceiling or against a wall, running through the
 * middle of the face as a train track does, its way through its block its {@link #PATH} (see {@link StripPath}). Laid
 * next to strips already down, it joins them as track does: straight on, bending round, round the outside of a block
 * from one face of it to the next, up from a floor into a wall or over the top edge of one; and the strips it joins
 * that ran elsewhere turn to meet it, as a straight run of track bends round to a new piece laid beside its end. With
 * none to join it runs straight on the way whoever lays it looks, or along a wall unless they look steeply up or down.
 * It needs every face it lies on held by something solid, and goes when that goes. WRAP_FIRST and WRAP_SECOND say
 * whether it goes on round the outside of a block at its first or second end, where its bar reaches a little past its
 * block to fill the corner. It glows in the plain white an LED strip has with no story round it, or its story's colour
 * (see {@link LedStripBlock}), as bright as a strip beside it as it is laid. Broken, it gives an LED strip back.
 */
public class LedStripLineBlock extends Block {
    /** Its way through its block, numbered as {@link StripPath#ALL} lists them. */
    public static final IntegerProperty PATH = IntegerProperty.create("path", 0, StripPath.ALL.size() - 1);
    /** Whether it goes on round the outside of a block at its first end, and at its second. */
    public static final BooleanProperty WRAP_FIRST = BooleanProperty.create("wrap_first");
    public static final BooleanProperty WRAP_SECOND = BooleanProperty.create("wrap_second");
    /** How thick its bar is, out from the face it lies on, and how wide, in pixels of its block. */
    public static final int BAR = LedStripBlock.BAR;
    /**
     * How steeply whoever lays one on a wall with nothing to join must look up or down for it to run up the wall, in
     * degrees.
     */
    private static final float STEEP = 45;

    /** Its bars for each way through its block, by its number, worked out once for all its states. */
    private static final List<VoxelShape> BARS = StripPath.ALL.stream().map(LedStripLineBlock::bars).toList();

    private final ImmutableMap<BlockState, VoxelShape> shapes;

    public LedStripLineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PATH, StripPath.straightFrom(new StripPath.End(Direction.DOWN, Direction.NORTH),
            Direction.DOWN).index()).setValue(LedStripBlock.BRIGHTNESS, LedStripBlock.BRIGHTEST).setValue(WRAP_FIRST, false)
            .setValue(WRAP_SECOND, false));
        shapes = getShapeForEachState(state -> BARS.get(state.getValue(PATH)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PATH, LedStripBlock.BRIGHTNESS, WRAP_FIRST, WRAP_SECOND);
    }

    /** Its way through its block. */
    public static StripPath path(BlockState state) {
        return StripPath.of(state.getValue(PATH));
    }

    /**
     * Laid by hand against the face clicked: joining the strips beside it that already run toward it first, then those
     * with an end free to turn to it, two at most that make a way through its block lying on that face (and on a face
     * round the corner from it, where a strip there comes down to it); with one, straight on from it; with none,
     * straight on the way whoever lays it looks.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace().getOpposite();
        if (!held(level, pos, face)) {
            return null;
        }
        List<StripPath.Joint> waiting = new ArrayList<>();
        List<StripPath.Joint> turnable = new ArrayList<>();
        for (StripPath.End end : endsAt(level, pos, face)) {
            for (StripPath.Joint joint : end.joints(pos)) {
                sortJoint(level, joint, waiting, turnable);
            }
        }
        List<StripPath.Joint> joins = new ArrayList<>(waiting);
        joins.addAll(turnable);
        StripPath path = chosen(joins, face, context);
        return withWraps(defaultBlockState().setValue(PATH, path.index()), path, level, pos)
            .setValue(LedStripBlock.BRIGHTNESS, brightnessBeside(level, pos));
    }

    /** Files a joint by whether the strip there already runs toward it, or could turn to; leaves it out otherwise. */
    private void sortJoint(Level level, StripPath.Joint joint, List<StripPath.Joint> waiting, List<StripPath.Joint> turnable) {
        BlockState there = level.getBlockState(joint.at());
        if (!there.is(this)) {
            return;
        }
        if (path(there).has(joint.end())) {
            waiting.add(joint);
        } else if (turnedToward(level, joint.at(), there, joint.end()) != null) {
            turnable.add(joint);
        }
    }

    /**
     * The ends its block could have, lying on the face laid against or a face round the corner from it held by
     * something solid; not one at the edge where the two meet.
     */
    private static List<StripPath.End> endsAt(LevelReader level, BlockPos pos, Direction face) {
        List<StripPath.End> ends = new ArrayList<>();
        for (Direction on : Direction.values()) {
            boolean lies = on == face || on.getAxis() != face.getAxis() && held(level, pos, on);
            if (lies) {
                StripPath.endsOf(on).stream().filter(end -> on == face || end.edge() != face).forEach(ends::add);
            }
        }
        return ends;
    }

    /**
     * Its way through its block toward the joints wanted, first come first: the first two that make a way lying on the
     * face laid against, else straight on from the first, else straight on the way whoever lays it looks.
     */
    private static StripPath chosen(List<StripPath.Joint> joins, Direction face, BlockPlaceContext context) {
        for (int one = 0; one < joins.size(); one++) {
            StripPath pair = firstPairWith(joins, one, face);
            if (pair != null) {
                return pair;
            }
        }
        for (StripPath.Joint joint : joins) {
            StripPath straight = StripPath.straightFrom(meeting(joint), face);
            if (straight != null) {
                return straight;
            }
        }
        return StripPath.straightFrom(new StripPath.End(face, lookedAlong(face, context)), face);
    }

    /** The way between one joint wanted and any later one, lying on the face laid against, if there is one. */
    @Nullable
    private static StripPath firstPairWith(List<StripPath.Joint> joins, int one, Direction face) {
        for (int other = one + 1; other < joins.size(); other++) {
            StripPath path = StripPath.between(meeting(joins.get(one)), meeting(joins.get(other)));
            if (path != null && path.faces().contains(face)) {
                return path;
            }
        }
        return null;
    }

    /** The end of a strip here that meets the strip at a joint: the joint's end, seen back from there. */
    private static StripPath.End meeting(StripPath.Joint joint) {
        StripPath.End there = joint.end();
        return joint.round() ? new StripPath.End(there.edge().getOpposite(), there.face().getOpposite())
            : new StripPath.End(there.face(), there.edge().getOpposite());
    }

    /**
     * The way it runs laid with nothing to join: on a floor or under a ceiling the way whoever lays it looks; on a wall
     * up it if they look steeply up or down, else along it.
     */
    private static Direction lookedAlong(Direction face, BlockPlaceContext context) {
        if (face.getAxis() == Direction.Axis.Y) {
            return context.getHorizontalDirection();
        }
        Player player = context.getPlayer();
        boolean steep = player != null && Math.abs(player.getXRot()) > STEEP;
        return steep ? Direction.UP : face.getClockWise();
    }

    /**
     * Laid, at each of its ends the strip it joins there turns to meet it if it does not run toward it yet, one already
     * running toward it chosen first; and where it joins one round the outside of a block, both fill that corner.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (level.isClientSide) {
            return;
        }
        for (StripPath.End end : path(state).ends()) {
            meetAt(level, pos, end);
        }
        BlockState laid = level.getBlockState(pos);
        if (laid.is(this)) {
            level.setBlock(pos, withWraps(laid, path(laid), level, pos), Block.UPDATE_ALL);
        }
    }

    /**
     * Has the strip one of its ends goes on into meet it there: one already running toward it, else one that can turn.
     */
    private void meetAt(Level level, BlockPos pos, StripPath.End end) {
        StripPath.Joint met = firstToMeet(level, end.joints(pos));
        if (met != null) {
            BlockState there = level.getBlockState(met.at());
            StripPath now = path(there).has(met.end()) ? path(there) : turnedToward(level, met.at(), there, met.end());
            level.setBlock(met.at(), withWraps(there.setValue(PATH, now.index()), now, level, met.at()), Block.UPDATE_ALL);
        }
    }

    /** Of the joints an end goes on into, the first whose strip runs toward it, else one that can turn. */
    @Nullable
    private StripPath.Joint firstToMeet(LevelReader level, List<StripPath.Joint> joints) {
        for (StripPath.Joint joint : joints) {
            if (joinsAt(level, joint)) {
                return joint;
            }
        }
        return joints.stream().filter(joint -> turnable(level, joint)).findFirst().orElse(null);
    }

    /**
     * Broken by a player or blown up, the strips it went on into round the outside of a block stop filling that corner:
     * every strip round its block, as far as a corner's strip can be, looks again at the strips beside it.
     */
    @Override
    public void destroy(LevelAccessor level, BlockPos pos, BlockState state) {
        super.destroy(level, pos, state);
        if (level instanceof Level world && !world.isClientSide) {
            BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 1, 1)).forEach(at -> refreshCorners(world, at.immutable()));
        }
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        super.wasExploded(level, pos, explosion);
        destroy(level, pos, Blocks.AIR.defaultBlockState());
    }

    /** Fills or leaves the corners of a strip at a block, if one stands there, by the strips beside it now. */
    private void refreshCorners(Level level, BlockPos at) {
        BlockState there = level.getBlockState(at);
        if (there.is(this)) {
            BlockState now = withWraps(there, path(there), level, at);
            if (now != there) {
                level.setBlock(at, now, Block.UPDATE_ALL);
            }
        }
    }

    /** Whether a strip stands at a joint that could turn to run out toward it. */
    private boolean turnable(LevelReader level, StripPath.Joint joint) {
        BlockState there = level.getBlockState(joint.at());
        return there.is(this) && turnedToward(level, joint.at(), there, joint.end()) != null;
    }

    /**
     * The way a strip runs once turned to run out at an end too: on toward a strip it joins at another end, if it joins
     * one, else straight on from the new end; none where that makes no way, or it joins strips at both its ends.
     */
    @Nullable
    private StripPath turnedToward(LevelReader level, BlockPos at, BlockState strip, StripPath.End end) {
        StripPath path = path(strip);
        List<StripPath.End> joined = path.ends().stream().filter(own -> joins(level, at, own)).toList();
        if (joined.size() == 2 || !held(level, at, end.face())) {
            return null;
        }
        if (joined.isEmpty()) {
            return StripPath.straightFrom(end, end.face());
        }
        return StripPath.between(joined.get(0), end);
    }

    /** Whether a strip leaving its block at an end goes on into another strip there. */
    private boolean joins(LevelReader level, BlockPos at, StripPath.End end) {
        return end.joints(at).stream().anyMatch(joint -> joinsAt(level, joint));
    }

    /** Whether the strip at a joint, if one stands there, runs out toward it. */
    private boolean joinsAt(LevelReader level, StripPath.Joint joint) {
        BlockState there = level.getBlockState(joint.at());
        return there.is(this) && path(there).has(joint.end());
    }

    /**
     * A strip's state with its corners filled at each end it leaves round the outside of a block into another strip.
     */
    private BlockState withWraps(BlockState state, StripPath path, LevelReader level, BlockPos at) {
        return state.setValue(WRAP_FIRST, wraps(level, at, path.first())).setValue(WRAP_SECOND, wraps(level, at, path.second()));
    }

    /** Whether a strip leaving its block at an end goes on round the outside of what it lies on, into another strip. */
    private boolean wraps(LevelReader level, BlockPos at, StripPath.End end) {
        return end.joints(at).stream().anyMatch(joint -> joint.round() && joinsAt(level, joint));
    }

    /** Whether something solid holds a strip lying on a face of its block. */
    private static boolean held(LevelReader level, BlockPos pos, Direction face) {
        BlockPos behind = pos.relative(face);
        return level.getBlockState(behind).isFaceSturdy(level, behind, face.getOpposite(), SupportType.CENTER);
    }

    /** How bright a strip of either kind beside where one is laid is; else full. */
    private static int brightnessBeside(LevelReader level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            BlockState beside = level.getBlockState(pos.relative(side));
            if (beside.getBlock() instanceof LedStripLineBlock || beside.getBlock() instanceof LedStripBlock) {
                return beside.getValue(LedStripBlock.BRIGHTNESS);
            }
        }
        return LedStripBlock.BRIGHTEST;
    }

    /** It stays only while every face it lies on is held by something solid. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return path(state).faces().stream().allMatch(face -> held(level, pos, face));
    }

    /** What held it gone, it goes, as a lamp on a wall does. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos,
        BlockPos neighbourPos) {
        if (path(state).faces().contains(direction) && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /**
     * Its bars for a way through its block: out to each of its ends from the middle of each face it lies on. The pieces
     * past its block filling the corner round the outside of a block are left out of its shape, being too short to
     * stand in anyone's way, and outside its block.
     */
    private static VoxelShape bars(StripPath path) {
        VoxelShape shape = Shapes.empty();
        for (StripPath.Arm arm : path.arms()) {
            shape = Shapes.or(shape, arm(arm.face(), arm.edge()));
        }
        return shape;
    }

    /**
     * A bar lying on a face, from its middle out to the edge toward a side, in the block's sixteenths, as
     * tools/generate_resources.mjs draws it.
     */
    private static VoxelShape arm(Direction face, Direction edge) {
        double[] low = new double[3];
        double[] high = new double[3];
        for (Direction.Axis axis : Direction.Axis.values()) {
            int i = axis.ordinal();
            boolean out = (axis == face.getAxis() ? face : edge).getAxisDirection() == Direction.AxisDirection.POSITIVE;
            low[i] = axis == face.getAxis() ? (out ? 16 - BAR : 0) : axis == edge.getAxis() ? (out ? 8 - BAR / 2.0 : 0) : 8 - BAR / 2.0;
            high[i] = axis == face.getAxis() ? low[i] + BAR : axis == edge.getAxis() ? (out ? 16 : 8 + BAR / 2.0) : 8 + BAR / 2.0;
        }
        return Block.box(low[0], low[1], low[2], high[0], high[1], high[2]);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state);
    }

    /** Its way turns with it, as a ship is placed turned, each corner it fills kept at its end. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return turned(state, rotation::rotate);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return turned(state, mirror::mirror);
    }

    /** A strip's state turned or mirrored: its way, and its corners at the ends they were at, whichever comes first. */
    private static BlockState turned(BlockState state, UnaryOperator<Direction> turn) {
        StripPath path = path(state);
        StripPath now = path.turned(turn);
        boolean kept = now.first().equals(new StripPath.End(turn.apply(path.first().face()), turn.apply(path.first().edge())));
        boolean firstWraps = state.getValue(kept ? WRAP_FIRST : WRAP_SECOND);
        boolean secondWraps = state.getValue(kept ? WRAP_SECOND : WRAP_FIRST);
        return state.setValue(PATH, now.index()).setValue(WRAP_FIRST, firstWraps).setValue(WRAP_SECOND, secondWraps);
    }

    /** Picked, an LED strip, the one item both kinds come from. */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        return new ItemStack(SkylinersRegistry.LED_STRIP_ITEM.get());
    }
}
