package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * The lab's plumbing: a brushed steel pipe a quarter of a block thick, banded green where it joins, run up from the
 * caps of the food machines into the ceiling and along it, feeding them their broth, water and air. It runs from its
 * middle each way it has set (NORTH, EAST, SOUTH, WEST, UP and DOWN), as the blueprint lays it, so its runs turn
 * corners and branch. Only the blueprints place it; broken, it gives back nothing.
 */
public class LabPipeBlock extends PipeBlock {
    private static final MapCodec<LabPipeBlock> CODEC = simpleCodec(LabPipeBlock::new);
    /** How far out from the middle of its block the pipe's sides are, in blocks: a quarter block thick. */
    private static final float APOTHEM = 0.125F;

    public LabPipeBlock(Properties properties) {
        super(APOTHEM, properties);
        BlockState none = stateDefinition.any();
        for (BooleanProperty way : PROPERTY_BY_DIRECTION.values()) {
            none = none.setValue(way, false);
        }
        registerDefaultState(none);
    }

    @Override
    protected MapCodec<? extends PipeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    /** A pipe running from its middle each of the ways given. */
    public BlockState running(Iterable<Direction> ways) {
        BlockState state = defaultBlockState();
        for (Direction way : ways) {
            state = state.setValue(PROPERTY_BY_DIRECTION.get(way), true);
        }
        return state;
    }

    /** Its runs turn with it, as when the rocket is built facing another way. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        BlockState turned = state;
        for (Map.Entry<Direction, BooleanProperty> way : PROPERTY_BY_DIRECTION.entrySet()) {
            turned = turned.setValue(PROPERTY_BY_DIRECTION.get(rotation.rotate(way.getKey())), state.getValue(way.getValue()));
        }
        return turned;
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState turned = state;
        for (Map.Entry<Direction, BooleanProperty> way : PROPERTY_BY_DIRECTION.entrySet()) {
            turned = turned.setValue(PROPERTY_BY_DIRECTION.get(mirror.mirror(way.getKey())), state.getValue(way.getValue()));
        }
        return turned;
    }
}
