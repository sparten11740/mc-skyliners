package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The glass of the tardigrade's tank in the rocket's lab: a thin pane at the very edge of a block on each side of it
 * that looks out of the tank (NORTH, EAST, SOUTH and WEST), so the tank's whole floor is the creature's to walk on. Its
 * lowest row stands on a steel kerb with moss over the floor of its block, as the rest of the tank's floor; its highest
 * is lit along its top, the soft light the creature lives under. Only the blueprints place it; broken, it gives back
 * nothing.
 */
public class HabitatGlassBlock extends Block {
    private static final MapCodec<HabitatGlassBlock> CODEC = simpleCodec(HabitatGlassBlock::new);
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    /** Which row of the tank's glass it is: on the floor, between, or under the ceiling. */
    public static final EnumProperty<Row> ROW = EnumProperty.create("row", Row.class);
    /** A pane's side, the way out of the tank, by the way it looks out. */
    private static final Map<Direction, BooleanProperty> SIDES = Map.of(Direction.NORTH, NORTH, Direction.EAST, EAST, Direction.SOUTH, SOUTH,
        Direction.WEST, WEST);
    /** How thick a pane is, in pixels. */
    private static final double PANE = 1;
    /** How brightly the top row's light glows, in light levels: soft. */
    public static final int GLOW = 9;
    private static final VoxelShape[] SHAPES = new VoxelShape[16];

    static {
        for (int sides = 0; sides < SHAPES.length; sides++) {
            SHAPES[sides] = shape(sides);
        }
    }

    /** A row of the tank's glass. */
    public enum Row implements StringRepresentable {
        FLOOR, MIDDLE, TOP;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public HabitatGlassBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false).setValue(WEST, false)
            .setValue(ROW, Row.MIDDLE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, ROW);
    }

    /** The glass of a row with panes on the sides given, by the ways they look out of the tank. */
    public BlockState panes(Iterable<Direction> sides, Row row) {
        BlockState state = defaultBlockState().setValue(ROW, row);
        for (Direction side : sides) {
            state = state.setValue(SIDES.get(side), true);
        }
        return state;
    }

    /** The panes of a set of sides, numbered as {@link #index} numbers them. */
    private static VoxelShape shape(int sides) {
        VoxelShape shape = Shapes.empty();
        if ((sides & 1) != 0) {
            shape = Shapes.or(shape, Block.box(0, 0, 0, 16, 16, PANE));
        }
        if ((sides & 2) != 0) {
            shape = Shapes.or(shape, Block.box(16 - PANE, 0, 0, 16, 16, 16));
        }
        if ((sides & 4) != 0) {
            shape = Shapes.or(shape, Block.box(0, 0, 16 - PANE, 16, 16, 16));
        }
        if ((sides & 8) != 0) {
            shape = Shapes.or(shape, Block.box(0, 0, 0, PANE, 16, 16));
        }
        return shape;
    }

    /** A state's sides as a number, a bit for each, north first. */
    private static int index(BlockState state) {
        return (state.getValue(NORTH) ? 1 : 0) | (state.getValue(EAST) ? 2 : 0) | (state.getValue(SOUTH) ? 4 : 0) | (state.getValue(WEST) ? 8 : 0);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[index(state)];
    }

    /** Its panes turn with it, as when the rocket is built facing another way. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState turned = state;
        for (Map.Entry<Direction, BooleanProperty> side : SIDES.entrySet()) {
            turned = turned.setValue(SIDES.get(rotation.rotate(side.getKey())), state.getValue(side.getValue()));
        }
        return turned;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }
}
