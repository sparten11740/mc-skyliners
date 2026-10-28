package dev.aerolarge.block;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A length of LED strip round one of the rocket's stories, along the inside of her skin, lying on the floor where it
 * meets the wall or, with {@link #CEILING}, fixed under the ceiling: a thin glowing bar along each side of its block
 * the skin stands on (NORTH, EAST, SOUTH and WEST), so round her stepped curve the bars of the blocks along it run on
 * as one line, turning its corners. It glows in its story's colour, as its story's panel has it (see
 * {@link LedPanelBlockEntity}), tinted by the client as her cover is; {@link #BRIGHTNESS} says how bright, and is a
 * block state so that it gives light, more the brighter it is, set only when it changes. It is placed only by the
 * blueprint and gives nothing back.
 */
public class LedStripBlock extends Block {
    /** The brightest a strip is set: full on. Off is 0, and the steps between are about a third and two thirds. */
    public static final int BRIGHTEST = 3;
    public static final IntegerProperty BRIGHTNESS = IntegerProperty.create("brightness", 0, BRIGHTEST);
    /** Whether it is fixed under the ceiling, rather than lying on the floor. */
    public static final BooleanProperty CEILING = BooleanProperty.create("ceiling");
    /** The sides of its block it runs along, where the skin stands. */
    public static final Map<Direction, BooleanProperty> WALLS = Map.of(
        Direction.NORTH, BlockStateProperties.NORTH,
        Direction.EAST, BlockStateProperties.EAST,
        Direction.SOUTH, BlockStateProperties.SOUTH,
        Direction.WEST, BlockStateProperties.WEST);
    /** How tall and how deep its bar is, in pixels of its block. */
    public static final int BAR = 2;
    /** The light it gives at each brightness, in light levels: some light, more as it brightens, none off. */
    private static final int[] LIGHT = {0, 5, 9, 12};

    private final ImmutableMap<BlockState, VoxelShape> shapes;

    public LedStripBlock(Properties properties) {
        super(properties);
        BlockState plain = stateDefinition.any().setValue(CEILING, false).setValue(BRIGHTNESS, BRIGHTEST);
        for (BooleanProperty wall : WALLS.values()) {
            plain = plain.setValue(wall, false);
        }
        registerDefaultState(plain);
        shapes = getShapeForEachState(LedStripBlock::bars);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.NORTH, BlockStateProperties.EAST, BlockStateProperties.SOUTH, BlockStateProperties.WEST, CEILING, BRIGHTNESS);
    }

    /** The light a strip in this state gives. */
    public static int light(BlockState state) {
        return LIGHT[state.getValue(BRIGHTNESS)];
    }

    /** Whether a strip in this state runs along a side of its block. */
    public static boolean runsAlong(BlockState state, Direction side) {
        BooleanProperty wall = WALLS.get(side);
        return wall != null && state.getValue(wall);
    }

    /** Its bars, one along each side it runs along, on the floor or under the ceiling. */
    private static VoxelShape bars(BlockState state) {
        double y0 = state.getValue(CEILING) ? 16 - BAR : 0;
        VoxelShape shape = Shapes.empty();
        for (Direction side : WALLS.keySet()) {
            if (runsAlong(state, side)) {
                shape = Shapes.or(shape, bar(side, y0));
            }
        }
        return shape;
    }

    /** A bar along one side of the block, from a height up, in pixels. */
    private static VoxelShape bar(Direction side, double y0) {
        return switch (side) {
            case NORTH -> Block.box(0, y0, 0, 16, y0 + BAR, BAR);
            case SOUTH -> Block.box(0, y0, 16 - BAR, 16, y0 + BAR, 16);
            case WEST -> Block.box(0, y0, 0, BAR, y0 + BAR, 16);
            default -> Block.box(16 - BAR, y0, 0, 16, y0 + BAR, 16);
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state);
    }

    /** The sides it runs along turn with it, as the rocket is placed turned. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState turned = state;
        for (Map.Entry<Direction, BooleanProperty> wall : WALLS.entrySet()) {
            turned = turned.setValue(WALLS.get(rotation.rotate(wall.getKey())), state.getValue(wall.getValue()));
        }
        return turned;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState mirrored = state;
        for (Map.Entry<Direction, BooleanProperty> wall : WALLS.entrySet()) {
            mirrored = mirrored.setValue(WALLS.get(mirror.mirror(wall.getKey())), state.getValue(wall.getValue()));
        }
        return mirrored;
    }
}
