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
 * Furniture to go beside the cabin's seats, in two kinds. The seat console is a business seat's side console, as high
 * as its armrests: a moulded white unit with a stowage cubby in its front, a walnut cocktail tray on top and a reading
 * lamp on a slim stalk at its back, by the sitter's shoulder, its shade glowing warm whatever the light around. The
 * lounge table is a low round table on a chrome pedestal, its top walnut, to stand in front of the lounge's armchairs.
 * FACING is the way the seats beside a console face; a table is round and looks the same whichever way it is put.
 */
public class SeatConsoleBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<SeatConsoleBlock> CONSOLE_CODEC = simpleCodec(properties -> new SeatConsoleBlock(properties, Kind.CONSOLE));
    private static final MapCodec<SeatConsoleBlock> TABLE_CODEC = simpleCodec(properties -> new SeatConsoleBlock(properties, Kind.TABLE));

    /** Which piece of furniture it is. */
    public enum Kind {
        CONSOLE,
        TABLE
    }

    /** The console facing north: the unit at armrest height, and the lamp's stalk and shade at its back. */
    private static final Map<Direction, VoxelShape> CONSOLE_SHAPES = TurnedShapes.of(Shapes.or(
        Block.box(0.5, 0, 1, 15.5, 11.5, 15),
        Block.box(10.5, 11.5, 10.5, 13.5, 15.5, 13.5)
    ));
    /** The table: its round top over the pedestal and the foot under it. */
    private static final VoxelShape TABLE_SHAPE = Shapes.or(
        Block.box(1, 9, 1, 15, 10, 15),
        Block.box(7, 1, 7, 9, 9, 9),
        Block.box(3, 0, 3, 13, 1, 13)
    );

    private final Kind kind;

    public SeatConsoleBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return kind == Kind.CONSOLE ? CONSOLE_CODEC : TABLE_CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Facing the way whoever puts it down faces, as the seats they put beside it do. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return kind == Kind.CONSOLE ? CONSOLE_SHAPES.get(state.getValue(FACING)) : TABLE_SHAPE;
    }
}
