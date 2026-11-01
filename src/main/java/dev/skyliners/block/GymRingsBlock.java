package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Gymnastic rings: a pair of wooden rings on straps. Where they are put up (MOUNT) they hang under a ceiling, as the
 * gym hall's hang on chains from its roof truss; from the crossbar of a free-standing steel frame on the floor, two
 * uprights on long feet; or from a steel bracket's bar held out from a wall on two arms, facing out from it. Nothing
 * bumps into them, nor into their frame or bracket; using them is grabbing them, and swings you up off the floor with a
 * creak of the straps. FACING is the way the pair faces, the rings side by side across it.
 */
public class GymRingsBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<GymRingsBlock> CODEC = simpleCodec(GymRingsBlock::new);
    /** How they are put up; rings saved before they had a mount were hung, and load hanging. */
    public static final EnumProperty<Mount> MOUNT = Mount.property(Mount.CEILING, Mount.WALL, Mount.FLOOR);
    /** Hung, by the way the pair runs across: the rings and their straps. */
    private static final Map<Direction.Axis, VoxelShape> HANGING = Map.of(
        Direction.Axis.Z, Block.box(2, 0, 6, 14, 16, 10),
        Direction.Axis.X, Block.box(6, 0, 2, 10, 16, 14)
    );
    /** On their frame, by the way they face: the rings and straps, the crossbar, the uprights and their feet. */
    private static final Map<Direction, VoxelShape> STANDING = TurnedShapes.of(Shapes.or(Block.box(2, 4, 6, 14, 14.5, 10),
        Block.box(0, 14.5, 7, 16, 16, 9), Block.box(0.5, 1, 7.25, 2, 14.5, 8.75), Block.box(14, 1, 7.25, 15.5, 14.5, 8.75),
        Block.box(0, 0, 3, 2.5, 1, 13), Block.box(13.5, 0, 3, 16, 1, 13)));
    /** On their bracket, by the way they face: the rings and straps, the bar, its arms and their wall plates. */
    private static final Map<Direction, VoxelShape> ON_WALL = TurnedShapes.of(Shapes.or(Block.box(2, 0, 6, 14, 14.5, 10),
        Block.box(0, 14.5, 7, 16, 16, 9), Block.box(1, 14.5, 9, 2.5, 16, 15), Block.box(13.5, 14.5, 9, 15, 16, 15),
        Block.box(0.5, 12, 15, 3, 16, 16), Block.box(13, 12, 15, 15.5, 16, 16)));
    /** How hard grabbing the rings swings you up, in blocks a tick: about a block off the floor. */
    private static final double SWING = 0.45;
    /** The straps creak as you grab them, a little deeper than leather armour going on. */
    private static final float CREAK_PITCH = 0.8F;
    /** The puff of chalk off your hands as you grab them: how many, how far they spread and how fast they fly. */
    private static final int CHALK = 6;
    private static final double CHALK_SPREAD = 0.25;
    private static final double CHALK_SPEED = 0.02;

    public GymRingsBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MOUNT, Mount.CEILING));
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
     * Put up against what holds them (see {@link Mounting}): hung under a ceiling or a light cable, or stood on the
     * floor on their frame, facing whoever puts them up; or on their bracket on a wall, facing out from it; nowhere
     * that nothing holds them.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction toward = Mounting.support(context, MOUNT.getPossibleValues());
        if (toward == null) {
            return null;
        }
        Direction facing = toward.getAxis().isHorizontal() ? toward.getOpposite() : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(MOUNT, Mount.against(toward)).setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Direction facing = state.getValue(FACING);
        return switch (state.getValue(MOUNT)) {
            case CEILING -> HANGING.get(facing.getAxis());
            case FLOOR -> STANDING.get(facing);
            case WALL -> ON_WALL.get(facing);
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, Math.max(motion.y, SWING), motion.z);
            // The server's say-so for the player's own client, which moves them.
            player.hurtMarked = true;
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 1.0F, CREAK_PITCH);
            Vec3 hands = player.getEyePosition();
            server.sendParticles(ParticleTypes.POOF, hands.x, hands.y, hands.z, CHALK, CHALK_SPREAD, CHALK_SPREAD, CHALK_SPREAD, CHALK_SPEED);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
