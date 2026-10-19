package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A secret door that looks like a bookshelf, two blocks tall like a door: one book on the lower half glows faintly on
 * both faces, the hint, and while a player is near, sparks drift into it now and then with a faint shimmer. Use it
 * and it swings back on its hinge with a deep creak and a swirl of sparks, a doorway behind it; it swings shut again
 * by itself after a while, once nobody stands in the doorway, or when used again. Shut, it fills its blocks as solidly
 * as the bookshelves around it.
 */
public class SecretBookshelfBlock extends DoorBlock {
    private static final MapCodec<SecretBookshelfBlock> CODEC = simpleCodec(SecretBookshelfBlock::new);
    /** How long it stays open once used, in ticks: time enough for both players to get through. */
    private static final int STAYS_OPEN = 200;
    /** How much longer it waits, in ticks, while someone still stands in the doorway. */
    private static final int WAIT_FOR_DOORWAY = 20;
    /** How near a player has to be for the hint to sparkle, in blocks. */
    private static final double HINT_RANGE = 6;
    /** Sparks drawn into the book on each of the client's random visits (a fraction of a second apart at best). */
    private static final int HINT_SPARKS = 3;
    /** Of those visits, the share that also shimmers aloud. */
    private static final float SHIMMER_CHANCE = 0.3F;
    private static final float SHIMMER_VOLUME = 0.5F;
    /** How high up the lower half the glowing book's middle is, as a share of the block. */
    private static final double BOOK_HEIGHT = 0.3;
    /** The swirl of sparks as it swings open or shut. */
    private static final int SWING_SPARKS = 24;

    public SecretBookshelfBlock(Properties properties) {
        super(AerolargeRegistry.SECRET_BOOKSHELF_SET, properties);
    }

    @Override
    public MapCodec<? extends DoorBlock> codec() {
        return CODEC;
    }

    /** Shut, a whole block like a bookshelf; open, the thin leaf of a door swung back against its hinge. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return isOpen(state) ? super.getShape(state, level, pos, context) : Shapes.block();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        InteractionResult result = super.useWithoutItem(state, level, pos, player, hitResult);
        if (level instanceof ServerLevel server) {
            BlockPos lower = lowerHalf(state, pos);
            sparks(server, lower);
            if (isOpen(level.getBlockState(pos))) {
                server.scheduleTick(lower, this, STAYS_OPEN);
            }
        }
        return result;
    }

    /** Swings shut, unless redstone holds it open; while anyone still stands in the doorway, it waits for them. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!isOpen(state) || state.getValue(POWERED) || state.getValue(HALF) != DoubleBlockHalf.LOWER) {
            return;
        }
        if (!level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).expandTowards(0, 1, 0)).isEmpty()) {
            level.scheduleTick(pos, this, WAIT_FOR_DOORWAY);
            return;
        }
        setOpen(null, level, state, pos, false);
        sparks(level, pos);
    }

    /** The hint: while a player is near and it is shut, sparks drift into the glowing book on both faces. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (isOpen(state) || state.getValue(HALF) != DoubleBlockHalf.LOWER
            || level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, HINT_RANGE, false) == null) {
            return;
        }
        Direction front = state.getValue(FACING);
        for (Direction face : new Direction[]{front, front.getOpposite()}) {
            double x = pos.getX() + 0.5 + face.getStepX() * 0.5;
            double y = pos.getY() + BOOK_HEIGHT;
            double z = pos.getZ() + 0.5 + face.getStepZ() * 0.5;
            for (int i = 0; i < HINT_SPARKS; i++) {
                // An enchanting spark starts this far off its spot and drifts into it.
                level.addParticle(ParticleTypes.ENCHANT, x, y, z,
                    face.getStepX() * random.nextDouble() + random.nextDouble() - 0.5, random.nextDouble() * 0.6, face.getStepZ() * random.nextDouble() + random.nextDouble() - 0.5);
            }
        }
        if (random.nextFloat() < SHIMMER_CHANCE) {
            level.playLocalSound(pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, SHIMMER_VOLUME, 1.2F + random.nextFloat() * 0.4F, false);
        }
    }

    private static BlockPos lowerHalf(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    private static void sparks(ServerLevel level, BlockPos lower) {
        level.sendParticles(ParticleTypes.ENCHANT, lower.getX() + 0.5, lower.getY() + 1, lower.getZ() + 0.5, SWING_SPARKS, 0.4, 0.8, 0.4, 0.6);
    }
}
