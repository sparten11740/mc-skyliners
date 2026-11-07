package dev.skyliners.exterior;

import dev.skyliners.block.Mount;
import dev.skyliners.block.Mounting;
import dev.skyliners.block.TurnedShapes;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A piece of a ship's outside, such as an antenna mast, a sensor dome or a bank of heat-sink fins (see
 * {@link HullFittings}), that a player can put up anywhere: standing on a floor, fixed to a wall or hung under a
 * ceiling (MOUNT), each a model of its own that draws in what holds it there, never the same model tipped over: a mast
 * stands on a flange, juts from a wall as a whip on a hinge, and hangs as a blade. The freighter is built with them on
 * her hull (see {@code FreighterExterior}). Put up by hand, it goes against the block the player clicks, as a button
 * does (see {@link Mounting}), and nowhere that nothing holds it. FACING is the way it faces: on a wall out from it,
 * and on a floor or under a ceiling toward whoever put it there.
 */
public class HullFittingBlock extends Block {
    /** How it is put up. */
    public static final EnumProperty<Mount> MOUNT = Mount.property(Mount.CEILING, Mount.WALL, Mount.FLOOR);
    /** The way it faces: out from its wall, or toward whoever stood it on a floor or hung it under a ceiling. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Its shape for each mount and facing, turned from its outline. */
    private final Map<Mount, Map<Direction, VoxelShape>> shapes = new EnumMap<>(Mount.class);

    /**
     * What can be picked out of a fitting, and bumped into, as it stands on a floor, is fixed to a wall and hangs under
     * a ceiling, each drawn facing north, a wall's against its block's south side.
     */
    public record Outline(VoxelShape floor, VoxelShape wall, VoxelShape ceiling) {
    }

    public HullFittingBlock(Properties properties, Outline outline) {
        super(properties);
        shapes.put(Mount.FLOOR, TurnedShapes.of(outline.floor()));
        shapes.put(Mount.WALL, TurnedShapes.of(outline.wall()));
        shapes.put(Mount.CEILING, TurnedShapes.of(outline.ceiling()));
        registerDefaultState(stateDefinition.any().setValue(MOUNT, Mount.FLOOR).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MOUNT, FACING);
    }

    /** This fitting put up a way, facing a way, as the freighter's blueprint puts it up. */
    public BlockState mounted(Mount mount, Direction facing) {
        return defaultBlockState().setValue(MOUNT, mount).setValue(FACING, facing);
    }

    /**
     * Put up against what holds it where the player puts it (see {@link Mounting}): on a wall facing out from it, on a
     * floor or under a ceiling facing whoever puts it there; nowhere that nothing holds it.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction toward = Mounting.support(context, MOUNT.getPossibleValues());
        if (toward == null) {
            return null;
        }
        Mount mount = Mount.against(toward);
        return mounted(mount, mount == Mount.WALL ? toward.getOpposite() : context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(MOUNT)).get(state.getValue(FACING));
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
