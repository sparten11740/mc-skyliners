package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An open shelf unit for the rocket's rooms: two shelves between birch end panels on a steel plinth, open at the back
 * as well as the front, so a row of them stands across a room as a low divider and is seen through from either side.
 * Stood on one another they make a taller one. What is kept on it is KIND, the things of the room it stands in: the
 * entry's boots and helmets, the bunk room's books, the mess's jars, the pool's rolled towels, the cargo bay's tool
 * cases and the club's records. FACING is the way the things on it face; it is only to look at.
 */
public class ShelfUnitBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<ShelfUnitBlock> CODEC = simpleCodec(ShelfUnitBlock::new);
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    /** Facing north: the whole width of its block, ten pixels deep through its middle, and as tall as its block. */
    private static final Map<Direction, VoxelShape> SHAPES = TurnedShapes.of(Block.box(0, 0, 3, 16, 16, 13));

    /** What is kept on the shelves. */
    public enum Kind implements StringRepresentable {
        BOOTS,
        BOOKS,
        JARS,
        TOWELS,
        TOOLS,
        RECORDS;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public ShelfUnitBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(KIND, Kind.BOOKS));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, KIND);
    }

    /** A shelf unit of this block with these things on it, facing the way given, as the blueprints put one in. */
    public static BlockState stocked(Block shelf, Kind kind, Direction facing) {
        return shelf.defaultBlockState().setValue(KIND, kind).setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }
}
