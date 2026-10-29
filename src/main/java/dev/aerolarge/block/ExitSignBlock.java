package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An emergency exit sign over a door: a green running man heading out through a doorway, the sign every airliner has
 * over its exits and the one passengers who cannot read yet know. It glows whatever the light around, the way a
 * backlit sign does, so it shows with the cabin dark, and casts a little light of its own round the door. It takes the
 * shape that fits where it is put (MOUNT): on a wall a thin plate with its back to it; under a ceiling a sign lit on
 * both faces, hung on two short rods; on the floor the same sign on a short steel post over a base. FACING is the way
 * the sign faces: out from the wall, or toward whoever put it up.
 */
public class ExitSignBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<ExitSignBlock> CODEC = simpleCodec(ExitSignBlock::new);
    /** How the sign is put up; one saved before it had a mount was on a wall, as every exit sign then was. */
    public static final EnumProperty<Mount> MOUNT = Mount.property(Mount.CEILING, Mount.WALL, Mount.FLOOR);
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
        Direction.NORTH, Block.box(1, 4, 14.5, 15, 12, 16),
        Direction.SOUTH, Block.box(1, 4, 0, 15, 12, 1.5),
        Direction.WEST, Block.box(14.5, 4, 1, 16, 12, 15),
        Direction.EAST, Block.box(0, 4, 1, 1.5, 12, 15)
    );
    /** Hung under a ceiling, drawn facing north as its model is: the sign across the block's middle and its rods. */
    private static final Map<Direction, VoxelShape> HUNG = TurnedShapes.of(Shapes.or(
        Block.box(1, 6, 7.25, 15, 14, 8.75), Block.box(3, 14, 7.5, 4, 16, 8.5), Block.box(12, 14, 7.5, 13, 16, 8.5)
    ));
    /** Standing on the floor, drawn facing north: the sign, its post and the base the post stands in. */
    private static final Map<Direction, VoxelShape> ON_POST = TurnedShapes.of(Shapes.or(
        Block.box(1, 7, 7.25, 15, 15, 8.75), Block.box(7, 1, 7, 9, 7, 9), Block.box(4, 0, 5, 12, 1, 11)
    ));

    public ExitSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MOUNT, Mount.WALL));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MOUNT);
    }

    /**
     * Put up against what holds it (see {@link Mounting}): against the side of a block it faces out from it; under a
     * ceiling or on the floor it faces whoever puts it up. Nowhere where nothing holds it.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction toward = Mounting.support(context, MOUNT.getPossibleValues());
        if (toward == null) {
            return null;
        }
        Mount mount = Mount.against(toward);
        Direction facing = mount == Mount.WALL ? toward.getOpposite() : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(MOUNT, mount).setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return switch (state.getValue(MOUNT)) {
            case WALL -> SHAPES.get(facing);
            case CEILING -> HUNG.get(facing);
            case FLOOR -> ON_POST.get(facing);
        };
    }
}
