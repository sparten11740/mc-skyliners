package dev.aerolarge.block;

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
 * of whole panels, so the fuselage and the nose read as one smooth shell. SHAPE says where the hull's surface cuts
 * through the block (see {@link ShellGeometry}); its model draws the part below the cut and its collision follows it
 * in quarter-block steps. It stops light at the sides its cut leaves whole, the inside of the skin, so the daylight
 * that lights its cut face goes no further into the cabin than through a panel. Only the blueprints place it; broken,
 * it gives back the part it is cut from.
 */
public class ShellBlock extends Block {
    /** Any of {@link ShellGeometry}'s shapes. */
    public static final IntegerProperty SHAPE = IntegerProperty.create("shape", 0, ShellGeometry.COUNT - 1);
    private final Supplier<? extends ItemLike> part;

    public ShellBlock(Properties properties, Supplier<? extends ItemLike> part) {
        super(properties);
        this.part = part;
        registerDefaultState(stateDefinition.any().setValue(shapeProperty(), ShellGeometry.WHOLE));
    }

    /**
     * The property the block keeps its shape in: every shape, unless the block can only take some of them. It is
     * asked for while the block is being made, so it must be a constant.
     */
    protected IntegerProperty shapeProperty() {
        return SHAPE;
    }

    /** A block's shape: its own if it is cut to the curve, the whole block otherwise. */
    public static int shape(BlockState state) {
        return state.getBlock() instanceof ShellBlock shell ? state.getValue(shell.shapeProperty()) : ShellGeometry.WHOLE;
    }

    /** Whether the block can take the shape. */
    public static boolean takes(BlockState state, int shape) {
        return state.getBlock() instanceof ShellBlock shell && shell.shapeProperty().getPossibleValues().contains(shape);
    }

    /** The block cut to the shape, which it must be able to take. */
    public static BlockState withShape(BlockState state, int shape) {
        return state.setValue(((ShellBlock) state.getBlock()).shapeProperty(), shape);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(shapeProperty());
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
