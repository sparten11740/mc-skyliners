package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.autopilot.ControlSurfaces;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What the control surfaces' panels are walked on: blocks the airframe lays where each panel lies at rest, aft of the
 * wing's trailing edge (see {@link ControlSurfaces.Panel}), as thin as the wing's lifting blocks and level with them,
 * so the wing can be walked out to its very edge. The panels are only drawn, by the autopilot, and swing as they are
 * drawn; what they stand on stays flat. Drawn by nothing and cannot be aimed at, mined or blown up. FACING is the way
 * to the wing it hangs from; once nothing is left there, it goes too, so none stays behind when a wing is taken apart.
 */
public class SurfacePanelBlock extends Block {
    private static final MapCodec<SurfacePanelBlock> CODEC = simpleCodec(SurfacePanelBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** As thick as a lifting block laid flat, and as high in its block (see Create Propulsion's wing model). */
    private static final VoxelShape PLATE = box(0, 6, 0, 16, 10, 16);

    public SurfacePanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    /** One hanging from the wing toward {@code wing}. */
    public static BlockState toward(BlockState base, Direction wing) {
        return base.setValue(FACING, wing);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Nothing to aim at: clicks go through to whatever is behind, as they would past the drawn panel. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PLATE;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    /** Goes with the wing: once the block toward it is gone, whether the wing's or another panel's. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.getValue(FACING) && neighbor.isAir()) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
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
