package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.compat.SableFlight;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What the freighter's boarding ramp is walked on once it is down (see {@link BoardingRamp}): blocks laid under the
 * plate its renderer draws, falling one block in two toward the ground, so the ramp is walked like a slope. Each is
 * solid up to the plate's top over its block in four little steps, low enough to walk up without a jump: an UPPER block
 * holds the higher half of a block's fall, from its top to its middle, a LOWER one the lower half, from its middle to
 * its foot; FACING is the way up the ramp, into her. Their shape comes from their state alone, never from the level,
 * as Sable keeps each state's shape for every ship. They are drawn by nothing and cannot be aimed at, mined or blown
 * up: only the ramp lays them and takes them up. One no ramp holds any more (left standing in the world as she was
 * assembled with her ramp down) takes itself away. With Sable, the ground passes through them (see
 * {@code SableFlight#rampDeck}), so a ramp let down never props her up.
 */
public class RampDeckBlock extends Block {
    private static final MapCodec<RampDeckBlock> CODEC = simpleCodec(RampDeckBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Half> HALF = EnumProperty.create("half", Half.class);
    /**
     * How far round a block of the deck the world must be loaded before it may decide it is a leftover, in blocks:
     * further than its ramp's panel ever stands from it, so the panel is loaded too if it is there.
     */
    private static final int PANEL_REACH = 24;
    /** How many little steps each block's fall is cut into, and how many sixteenths each of a block's run is. */
    private static final int STEPS = 4;
    private static final int STEP_RUN = 16 / STEPS;
    private static final Map<Half, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(Half.class);

    static {
        for (Half half : Half.values()) {
            SHAPES.put(half, TurnedShapes.of(slope(half)));
        }
    }

    /** Which half of a block's fall a block of the ramp holds: the higher, from its top to its middle, or the lower. */
    public enum Half implements StringRepresentable {
        UPPER, LOWER;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public RampDeckBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, Half.UPPER));
    }

    /** A block of a ramp climbing toward {@code up}, holding one half of a block's fall. */
    public static BlockState deck(BlockState base, Direction up, Half half) {
        return base.setValue(FACING, up).setValue(HALF, half);
    }

    /**
     * A block's slope climbing north, as {@link TurnedShapes} wants it: each little step as high as the plate over the
     * middle of its run, so the plate's top is never more than half a step from it.
     */
    private static VoxelShape slope(Half half) {
        VoxelShape shape = Shapes.empty();
        double top = half == Half.UPPER ? 16 : 8;
        for (int step = 0; step < STEPS; step++) {
            double height = top - 8.0 * (step + 0.5) / STEPS;
            shape = Shapes.or(shape, box(0, 0, step * STEP_RUN, 16, height, (step + 1) * STEP_RUN));
        }
        return shape;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Nothing to aim at: clicks go through to whatever is behind, the drawn plate or the ground. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(HALF)).get(state.getValue(FACING));
    }

    /** Solid ground to anything finding its way, so villagers walk up the ramp. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    /**
     * Now and then one in the world makes sure some ramp still holds it; with none, it is a leftover, and goes. One
     * aboard is never a leftover, which are only ever left in the world, so nothing aboard goes because its panel's
     * part of her plot is not loaded yet; nor does one in the world while the world round it is not all loaded.
     */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!BlockPos.containing(SableFlight.toWorld(level, Vec3.atCenterOf(pos))).equals(pos) || !aroundLoaded(level, pos)) {
            return;
        }
        if (!RampPanelBlockEntity.anyHolds(level, pos)) {
            level.removeBlock(pos, false);
        }
    }

    /** Whether the world is loaded as far round a block as its ramp's panel may stand: its four corners say so. */
    private static boolean aroundLoaded(ServerLevel level, BlockPos pos) {
        for (int corner = 0; corner < 4; corner++) {
            int x = (corner & 1) == 0 ? -PANEL_REACH : PANEL_REACH;
            int z = (corner & 2) == 0 ? -PANEL_REACH : PANEL_REACH;
            if (!level.isLoaded(pos.offset(x, 0, z))) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
