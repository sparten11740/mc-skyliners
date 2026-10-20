package dev.aerolarge.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The airstairs' push-button, set beside their controller just aft of the forward door, inside the cabin and outside
 * by the top of the stairs: an illuminated push-button in a dark steel bezel, its square cap glowing amber whatever the
 * light around, with the panel's own staircase on it, so it needs no reading; pressed, it sinks in and glows brighter.
 * It is a stone button in all it does: it stays down a second and powers what it is on, which, on the controller,
 * folds the stairs away or brings them out on the signal (see {@link AirstairControllerBlock}). Unlike the call button
 * on the nose gear it works only what it is on.
 */
public class AirstairButtonBlock extends ButtonBlock {
    /** How long it stays down once pressed, in ticks: a stone button's. */
    private static final int PRESS_TICKS = 20;
    /** Half the bezel's width, in pixels, and how far the cap stands off what it is on, up and pressed in. */
    private static final double HALF = 4;
    private static final double UP = 2.5;
    private static final double DOWN = 1.5;
    private static final Map<Direction, VoxelShape> UP_SHAPES = shapes(UP);
    private static final Map<Direction, VoxelShape> DOWN_SHAPES = shapes(DOWN);

    public AirstairButtonBlock(BlockSetType type, Properties properties) {
        super(type, PRESS_TICKS, properties);
    }

    /** The button standing so far off what it is on, for each way it can look out from it. */
    private static Map<Direction, VoxelShape> shapes(double depth) {
        double from = 8 - HALF;
        double to = 8 + HALF;
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        shapes.put(Direction.UP, Block.box(from, 0, from, to, depth, to));
        shapes.put(Direction.DOWN, Block.box(from, 16 - depth, from, to, 16, to));
        shapes.put(Direction.NORTH, Block.box(from, from, 16 - depth, to, to, 16));
        shapes.put(Direction.SOUTH, Block.box(from, from, 0, to, to, depth));
        shapes.put(Direction.WEST, Block.box(16 - depth, from, from, 16, to, to));
        shapes.put(Direction.EAST, Block.box(0, from, from, depth, to, to));
        return shapes;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction out = getConnectedDirection(state).getOpposite();
        return (state.getValue(POWERED) ? DOWN_SHAPES : UP_SHAPES).get(out);
    }
}
