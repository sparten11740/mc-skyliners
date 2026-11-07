package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.compat.SableFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A secret panel, two blocks tall like a door: shut, a length of riveted hull plate like the wall round it, but a shade
 * darker, scuffed along its edges where it has swung open many times, and while a player is near, wisps of draught puff
 * out of its seams now and then, the hints. Knocked on from its front it sounds hollow and dust puffs out of its seams,
 * but it stays shut: a redstone pulse opens it, as the loose rivet set in the wall beside it gives (see
 * {@link LooseRivetBlock}), and it swings back with a clunk and a hiss of air. From behind, it is pushed open by hand,
 * so nobody is ever shut in. It swings shut by itself after a while once nobody stands in its doorway, at the next
 * pulse, or pushed by hand. Shut, it fills its blocks as solidly as the wall round it. As with any door, FACING is the
 * way it swings open, away from its front, and its front faces whoever set it.
 */
public class SecretPanelBlock extends DoorBlock {
    private static final MapCodec<SecretPanelBlock> CODEC = simpleCodec(SecretPanelBlock::new);
    /** How long it stays open once opened, in ticks: time enough for both players to get through. */
    private static final int STAYS_OPEN = 200;
    /** How much longer it waits, in ticks, while someone still stands in the doorway. */
    private static final int WAIT_FOR_DOORWAY = 20;
    /** How near a player has to be for the draught to puff out of its seams, in blocks. */
    private static final double HINT_RANGE = 6;
    /** Wisps of draught puffed out of its seams on each of the client's random visits, and how fast they drift out. */
    private static final int DRAUGHT_WISPS = 1;
    private static final double DRAUGHT_SPEED = 0.03;
    /** The puffs of dust out of its seams when knocked on, and of air as it swings open or shut. */
    private static final int KNOCK_PUFFS = 6;
    private static final int SWING_PUFFS = 18;
    /** The hollow knock: a deep drum's thud, played low. */
    private static final float KNOCK_VOLUME = 0.9F;
    private static final float KNOCK_PITCH = 0.55F;
    /** The hiss of air as it swings. */
    private static final float HISS_VOLUME = 0.5F;
    private static final float HISS_PITCH = 1.3F;
    /** How far out of its front its seams' puffs start, in blocks. */
    private static final double SEAM_OUT = 0.55;

    public SecretPanelBlock(Properties properties) {
        super(BlockSetType.IRON, properties);
    }

    @Override
    public MapCodec<? extends DoorBlock> codec() {
        return CODEC;
    }

    /** Shut, a whole block like the wall round it; open, the thin leaf of a door swung back against its hinge. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return isOpen(state) ? super.getShape(state, level, pos, context) : Shapes.block();
    }

    /**
     * Its lower half stands on anything there, not only a full block's top: aboard a ship its floor can be her skin,
     * cut to her curve, which would otherwise knock it down the first time a block beside it changed.
     */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? !level.getBlockState(pos.below()).isAir() : level.getBlockState(pos.below()).is(this);
    }

    /** Open, pushed shut by hand; shut, pushed open from behind, or knocked on from its front, only sounding hollow. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (level instanceof ServerLevel server) {
            boolean fromBehind = hitResult.getDirection() == state.getValue(FACING);
            if (isOpen(state) || fromBehind) {
                swing(server, state, pos, !isOpen(state));
            } else {
                knock(server, lowerHalf(state, pos), state.getValue(FACING).getOpposite());
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * A redstone pulse swings it the other way, open or shut, as it starts; as it ends, nothing, so a button's press
     * opens it and the next shuts it again.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        BlockPos other = pos.relative(state.getValue(HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
        boolean powered = level.hasNeighborSignal(pos) || level.hasNeighborSignal(other);
        if (defaultBlockState().is(block) || powered == state.getValue(POWERED)) {
            return;
        }
        BlockState now = state.setValue(POWERED, powered);
        level.setBlock(pos, now, Block.UPDATE_CLIENTS);
        if (powered && level instanceof ServerLevel server) {
            swing(server, now, pos, !isOpen(now));
        }
    }

    /** Swings shut, unless redstone holds it open; while anyone still stands in the doorway, it waits for them. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!isOpen(state) || state.getValue(POWERED) || state.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return;
        }
        if (SecretBookshelfBlock.inDoorway(level, pos)) {
            level.scheduleTick(pos, this, WAIT_FOR_DOORWAY);
            return;
        }
        swing(level, state, pos, false);
    }

    /**
     * The hint: while a player is near and it is shut, wisps of draught puff out of the seams round its front. Aboard a
     * ship it stands in her plot, far from the players round her, so how near they are is measured where it is in the
     * world.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Vec3 there = SableFlight.toWorld(level, Vec3.atCenterOf(pos));
        if (isOpen(state) || level.getNearestPlayer(there.x, there.y, there.z, HINT_RANGE, false) == null) {
            return;
        }
        Direction front = state.getValue(FACING).getOpposite();
        for (int i = 0; i < DRAUGHT_WISPS; i++) {
            Vec3 seam = onSeam(pos, front, state.getValue(HALF) == DoubleBlockHalf.UPPER, random);
            level.addParticle(ParticleTypes.CLOUD, seam.x, seam.y, seam.z,
                front.getStepX() * DRAUGHT_SPEED, DRAUGHT_SPEED * random.nextDouble(), front.getStepZ() * DRAUGHT_SPEED);
        }
    }

    /**
     * A point on the seams round its front in one of its blocks: down either side edge, along the top of its upper
     * half or the foot of its lower, just out of its face.
     */
    private static Vec3 onSeam(BlockPos pos, Direction front, boolean upper, RandomSource random) {
        boolean upright = random.nextBoolean();
        double along = random.nextDouble();
        double across = upright ? (random.nextBoolean() ? 0.02 : 0.98) : along;
        double up = upright ? along : upper ? 0.95 : 0.05;
        double x = front.getAxis() == Direction.Axis.X ? 0.5 + front.getStepX() * SEAM_OUT : across;
        double z = front.getAxis() == Direction.Axis.Z ? 0.5 + front.getStepZ() * SEAM_OUT : across;
        return new Vec3(pos.getX() + x, pos.getY() + up, pos.getZ() + z);
    }

    /** Opens or shuts it with a clunk and a hiss and a puff of air; opened, it shuts again by itself in a while. */
    private void swing(ServerLevel level, BlockState state, BlockPos pos, boolean open) {
        setOpen(null, level, state, pos, open);
        BlockPos lower = lowerHalf(state, pos);
        level.playSound(null, lower, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, HISS_VOLUME, HISS_PITCH);
        level.sendParticles(ParticleTypes.CLOUD, lower.getX() + 0.5, lower.getY() + 1, lower.getZ() + 0.5, SWING_PUFFS, 0.4, 0.8, 0.4, 0.05);
        if (open) {
            level.scheduleTick(lower, this, STAYS_OPEN);
        }
    }

    /** A hollow knock on its front, and a little dust puffing out of its seams. */
    private static void knock(ServerLevel level, BlockPos lower, Direction front) {
        level.playSound(null, lower.above(), SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.BLOCKS, KNOCK_VOLUME, KNOCK_PITCH);
        double x = lower.getX() + 0.5 + front.getStepX() * SEAM_OUT;
        double z = lower.getZ() + 0.5 + front.getStepZ() * SEAM_OUT;
        level.sendParticles(ParticleTypes.WHITE_ASH, x, lower.getY() + 1, z, KNOCK_PUFFS, 0.3, 0.8, 0.3, 0.02);
        level.sendParticles(ParticleTypes.POOF, x, lower.getY() + 0.1, z, 2, 0.3, 0.05, 0.3, 0.01);
    }

    private static BlockPos lowerHalf(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }
}
