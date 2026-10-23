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
 * An illuminated push-button in a dark steel bezel, its square cap glowing whatever the light around, with a picture
 * on it of what it works, so it needs no reading; pressed, it sinks in and glows brighter, and stays down a second, as
 * a stone button does. It is shaped as it is drawn: the bezel's square on what it is on, the cap standing a little off
 * it, or pressed in. The airstairs' button (see {@link AirstairButtonBlock}) and the doors' (see
 * {@link DoorButtonBlock}) are alike but for their cap's colour and picture, and what pressing them does.
 */
public abstract class LitButtonBlock extends ButtonBlock {
    /** How long it stays down once pressed, in ticks: a stone button's. */
    private static final int PRESS_TICKS = 20;
    /** Half the bezel's width, in pixels, and how far the cap stands off what it is on, up and pressed in. */
    private static final double HALF = 4;
    private static final double UP = 2.5;
    private static final double DOWN = 1.5;
    private static final Map<Direction, VoxelShape> UP_SHAPES = shapes(UP);
    private static final Map<Direction, VoxelShape> DOWN_SHAPES = shapes(DOWN);

    protected LitButtonBlock(BlockSetType type, Properties properties) {
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
