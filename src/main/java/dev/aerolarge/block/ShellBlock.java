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
 * in quarter-block steps. Only the blueprints place it; broken, it gives back the part it is cut from.
 */
public class ShellBlock extends Block {
    public static final IntegerProperty SHAPE = IntegerProperty.create("shape", 0, ShellGeometry.COUNT - 1);
    private final Supplier<? extends ItemLike> part;

    public ShellBlock(Properties properties, Supplier<? extends ItemLike> part) {
        super(properties);
        this.part = part;
        registerDefaultState(stateDefinition.any().setValue(SHAPE, ShellGeometry.WHOLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SHAPE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ShellGeometry.shape(state.getValue(SHAPE));
    }

    @Override
    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(SHAPE, ShellGeometry.rotate(state.getValue(SHAPE), rotation));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(SHAPE, ShellGeometry.mirror(state.getValue(SHAPE), mirror));
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(part.get());
    }
}
