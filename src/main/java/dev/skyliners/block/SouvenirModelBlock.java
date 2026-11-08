package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A souvenir model on a stand, for a desk or a shelf: of the airliner, the zeppelin, the rocket or the freighter.
 * FACING is the way its nose points: toward whoever put it down. Each is as solid as its own model, drawn nose north
 * and turned with it (see tools/generate_resources.mjs). A blueprint's preview shows its airframe's model (see
 * {@link dev.skyliners.item.AirframeBlueprintItem}).
 */
public class SouvenirModelBlock extends HorizontalDirectionalBlock {
    /** The stand most of them sit on: a foot plate and a slim pole up from its middle, so high, in pixels. */
    private static VoxelShape stand(double pole) {
        return Shapes.or(Block.box(5, 0, 5, 11, 1, 11), Block.box(7.5, 1, 7.5, 8.5, pole, 8.5));
    }

    /** The model airliner: its fuselage, its swept wings and tailplane, its fin, on its stand. */
    public static final VoxelShape AIRLINER = Shapes.or(stand(6), Block.box(7, 6, 0, 9, 8, 15), Block.box(1, 5.5, 6, 15, 7, 12),
        Block.box(4.5, 7, 12, 11.5, 8, 14.5), Block.box(7.5, 8, 12, 8.5, 11.5, 15));
    /** The model zeppelin: her hull, her fins in a cross at her tail and her control car, on her stand. */
    public static final VoxelShape ZEPPELIN = Shapes.or(stand(4.5), Block.box(4.5, 4.5, 1, 11.5, 11.5, 15.5), Block.box(3, 7.75, 12, 13, 8.25, 15),
        Block.box(7.75, 3, 12, 8.25, 13, 15), Block.box(7, 3.5, 3, 9, 4.5, 6));
    /** The model rocket: her fins at her foot, her body and her nose. */
    public static final VoxelShape ROCKET = Shapes.or(Block.box(4.5, 0, 4.5, 11.5, 5, 11.5), Block.box(6.5, 5, 6.5, 9.5, 11, 9.5),
        Block.box(7, 11, 7, 9, 13, 9), Block.box(7.5, 13, 7.5, 8.5, 15, 8.5));
    /** The model freighter: her saucer, her prongs and cockpit pod ahead, her dome and dish on top, on her stand. */
    public static final VoxelShape FREIGHTER = Shapes.or(stand(5.5), Block.box(3, 5.5, 3, 13, 8.5, 13), Block.box(4.5, 6.25, 0, 11.5, 7.75, 3),
        Block.box(12, 6.5, 0.75, 13.5, 8, 4.5), Block.box(7.25, 8.5, 7.25, 8.75, 9.25, 8.75), Block.box(10, 8.5, 5, 11, 9.5, 6));

    /** Its shape for each way it faces, turned from its model's. */
    private final Map<Direction, VoxelShape> shapes;
    private final VoxelShape north;

    /** A souvenir model as solid as its model, drawn nose north, is. */
    public SouvenirModelBlock(Properties properties, VoxelShape north) {
        super(properties);
        this.north = north;
        shapes = TurnedShapes.of(north);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new SouvenirModelBlock(properties, north));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }
}
