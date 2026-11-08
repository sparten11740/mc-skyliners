package dev.skyliners.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Airframe skin cut to the curve of the hull: the airliner's outer skin, where it curves, is made of these instead
 * of whole panels, so the fuselage and the nose read as one smooth shell. Its shape says where the hull's surface cuts
 * through the block (see {@link ShellGeometry}); its model draws the part below the cut and its collision follows it
 * in quarter-block steps. It stops light at the sides its cut leaves whole, the inside of the skin, so the daylight
 * that lights its cut face goes no further into the cabin than through a panel. Only the blueprints place it; broken,
 * it gives back the part it is cut from.
 * <p>
 * Its shape is kept in two properties, a high and a low part of about the square root of the shapes each, rather than
 * one of all of them: the game keeps a table of every state's neighbours as large as all of its property values
 * together, which for every shape in one property is some 75 MB for each block cut to the curve, and several times
 * that for a block with states of its own besides; split, it is a few.
 */
public class ShellBlock extends Block {
    /** Any of {@link ShellGeometry}'s shapes. */
    public static final Cuts CUTS = Cuts.of(ShellGeometry.COUNT);
    private final Supplier<? extends ItemLike> part;

    /**
     * The shapes a block can take, the first {@code count} of {@link ShellGeometry}'s, kept split in two properties:
     * shape {@code high * split + low}. The split leaves a few values past the last shape, which no block is cut to.
     */
    public record Cuts(int count, int split, IntegerProperty high, IntegerProperty low) {
        public static Cuts of(int count) {
            int split = (int) Math.ceil(Math.sqrt(count));
            return new Cuts(count, split, IntegerProperty.create("shape_high", 0, (count - 1) / split),
                IntegerProperty.create("shape_low", 0, split - 1));
        }
    }

    /**
     * Skin counts as solid however thin its cut. The game lets rain fall past a block whose collision is small, as it
     * falls through a carpet onto the floor under it, so a sliver cut from the crown of the freighter's canopy, with
     * only her cockpit's air under it, let the rain fall straight through onto her floor, standing or flying. Solid,
     * the skin keeps the rain and snow off as a roof does.
     */
    public ShellBlock(Properties properties, Supplier<? extends ItemLike> part) {
        super(properties.forceSolidOn());
        this.part = part;
        registerDefaultState(cut(stateDefinition.any(), ShellGeometry.WHOLE));
    }

    /**
     * The shapes the block can take: every one, unless it can only take some of them. It is asked for while the block
     * is being made, so it must be a constant.
     */
    protected Cuts cuts() {
        return CUTS;
    }

    /** The shape this block's state is cut to. */
    protected int shapeOf(BlockState state) {
        Cuts cuts = cuts();
        int shape = state.getValue(cuts.high()) * cuts.split() + state.getValue(cuts.low());
        return shape < cuts.count() ? shape : ShellGeometry.WHOLE;
    }

    /** This block's state cut to a shape it can take. */
    protected BlockState cut(BlockState state, int shape) {
        Cuts cuts = cuts();
        return state.setValue(cuts.high(), shape / cuts.split()).setValue(cuts.low(), shape % cuts.split());
    }

    /** Whether this block can be cut to the shape. */
    protected boolean canTake(int shape) {
        return shape >= 0 && shape < cuts().count();
    }

    /** A block's shape: its own if it is cut to the curve, the whole block otherwise. */
    public static int shape(BlockState state) {
        return state.getBlock() instanceof ShellBlock shell ? shell.shapeOf(state) : ShellGeometry.WHOLE;
    }

    /** Whether the block can take the shape. */
    public static boolean takes(BlockState state, int shape) {
        return state.getBlock() instanceof ShellBlock shell && shell.canTake(shape);
    }

    /** The block cut to the shape, which it must be able to take. */
    public static BlockState withShape(BlockState state, int shape) {
        return ((ShellBlock) state.getBlock()).cut(state, shape);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(cuts().high(), cuts().low());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ShellGeometry.shape(shape(state));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    /**
     * The skin, which occludes, shuts its neighbours off with plates on its whole sides (see
     * {@link ShellGeometry#occlusion}). Glass cut to the curve (the windscreen, the curved cabin windows, the moon
     * pool's belly) occludes nothing, so its shape only decides which of its own faces a neighbour that does occlude
     * hides. Its model culls a pane's partly covered sides by side, which is what joins the panes without a seam, so
     * those are culled by the stepped shape as ever: the plates would leave them only edges to be culled by, and hide
     * faces that show.
     */
    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return state.canOcclude() ? ShellGeometry.occlusion(shape(state)) : ShellGeometry.shape(shape(state));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return withShape(state, ShellGeometry.rotate(shape(state), rotation));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return withShape(state, ShellGeometry.mirror(shape(state), mirror));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(part.get());
    }
}
