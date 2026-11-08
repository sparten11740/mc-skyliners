package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A tiki torch, for the freighter's beach in her lounge: a bamboo pole with a woven head and a flame flickering on top
 * of it, warm and bright, sparks and a wisp of smoke rising from it, carried along with her as she flies. Put
 * up by hand it stands on a floor, its pole tall, or is fixed to a wall (MOUNT), a short pole held out from it on a
 * bamboo bracket, its flame lower; it goes against the block the player clicks, as a button does (see
 * {@link Mounting}), and nowhere that nothing holds it. FACING is the way it faces: on a wall out from it.
 */
public class TikiTorchBlock extends Block {
    private static final MapCodec<TikiTorchBlock> CODEC = simpleCodec(TikiTorchBlock::new);
    /** How it is put up: standing on a floor, as the freighter's beach has it, or fixed to a wall. */
    public static final EnumProperty<Mount> MOUNT = Mount.property(Mount.WALL, Mount.FLOOR);
    /** The way it faces: out from its wall; a standing torch looks the same any way. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Its pole and head, standing. */
    private static final VoxelShape STANDING = Block.box(6, 0, 6, 10, 16, 10);
    /** Its short pole and head, and the bracket holding it out from its wall, facing north, its wall to the south. */
    private static final Map<Direction, VoxelShape> ON_A_WALL = TurnedShapes.of(
        Shapes.or(Block.box(6, 2, 6, 10, 16, 10), Block.box(6.5, 3, 9, 9.5, 9, 16)));
    /**
     * Where its flame burns over its block's foot, standing and on a wall, in blocks, over the middle of its block
     * either way; and how far its flames stray from the middle.
     */
    private static final double FLAME_UP = 19.5 / 16;
    private static final double WALL_FLAME_UP = 16.5 / 16;
    private static final double FLAME_SPREAD = 0.08;
    /** How likely a wisp of smoke is with each flicker. */
    private static final float SMOKE_CHANCE = 0.3F;

    public TikiTorchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MOUNT, Mount.FLOOR).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MOUNT, FACING);
    }

    /**
     * Put up against what holds it where the player puts it (see {@link Mounting}): on a wall facing out from it, on a
     * floor facing whoever puts it there; nowhere that nothing holds it.
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
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(MOUNT) == Mount.WALL ? ON_A_WALL.get(state.getValue(FACING)) : STANDING;
    }

    /** On the client: its flame flickering, now and then a wisp of smoke over it. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double up = state.getValue(MOUNT) == Mount.WALL ? WALL_FLAME_UP : FLAME_UP;
        // Where its block is, in Sable's plot aboard a ship: Sable carries a particle given off there along with her.
        Vec3 at = new Vec3(pos.getX() + 0.5 + random.nextGaussian() * FLAME_SPREAD, pos.getY() + up,
            pos.getZ() + 0.5 + random.nextGaussian() * FLAME_SPREAD);
        level.addParticle(ParticleTypes.FLAME, at.x, at.y, at.z, 0, 0.01, 0);
        if (random.nextFloat() < SMOKE_CHANCE) {
            level.addParticle(ParticleTypes.SMOKE, at.x, at.y + 0.15, at.z, 0, 0.02, 0);
        }
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
