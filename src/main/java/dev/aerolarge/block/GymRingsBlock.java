package dev.aerolarge.block;

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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Gymnastic rings: a pair of wooden rings on straps, hung on chains from the gym hall's roof truss. Nothing bumps into
 * them; using them is grabbing them, and swings you up off the floor with a creak of the straps. FACING is the way
 * the pair faces, the rings side by side across it.
 */
public class GymRingsBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<GymRingsBlock> CODEC = simpleCodec(GymRingsBlock::new);
    private static final Map<Direction.Axis, VoxelShape> SHAPES = Map.of(
        Direction.Axis.Z, Block.box(2, 0, 6, 14, 16, 10),
        Direction.Axis.X, Block.box(6, 0, 2, 10, 16, 14)
    );
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
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Put down only where they hang from something (see {@link Hanging}), facing whoever puts them up. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return Hanging.hangs(context) ? defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()) : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING).getAxis());
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
