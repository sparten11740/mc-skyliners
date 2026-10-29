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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A cultured-meat bioreactor in the rocket's lab: a tall glass vessel of pink broth glowing softly on a steel plinth,
 * bubbles rising through it, a steel cap with a pipe up into the ceiling, and on a white scaffold rod up its middle a
 * steak growing, pink and marbled with fat. GROWTH is how far it has grown, from the bare rod (0) through a tuft at its
 * foot and a half-grown mass to a whole steak filling the vessel (GROWN), so it shows how long there is to wait. Used
 * once grown, the cap's seal hisses open with a puff of steam and the steak comes out, a Lab-Grown Steak, to the hand,
 * and the rod starts over, growing a stage a minute (see {@link #STAGE_TICKS}), with a pop of bubbles at each stage and
 * a chime once the steak is whole again. Used before that, it only gurgles. The server keeps its growth: a stage is a
 * scheduled tick of the lower half, set again if the tick was lost, as when the rocket was assembled, when it is next
 * used or on its own within a minute or two, as the game looks at its blocks now and then.
 */
public class BioreactorBlock extends LabMachineBlock {
    private static final MapCodec<BioreactorBlock> CODEC = simpleCodec(BioreactorBlock::new);
    public static final int GROWN = 3;
    public static final IntegerProperty GROWTH = IntegerProperty.create("growth", 0, GROWN);
    /** How long the steak takes to grow a stage, in ticks: a minute, so a harvested vessel is whole in three. */
    private static final int STAGE_TICKS = 60 * 20;
    /** How brightly its broth glows, in light levels: a soft pink lamp in the corner of the room. */
    public static final int GLOW = 7;
    /** How high up its upper half the cap's seal is, where the steam puffs out, in blocks. */
    private static final double SEAL_UP = 0.75;
    /** How many puffs of steam the seal lets out, and how far they spread, in blocks. */
    private static final int STEAM_PUFFS = 8;
    private static final double STEAM_SPREAD = 0.3;
    /** The gurgle of a vessel still growing, quiet and a little high, and the pops and chime of its stages. */
    private static final float GURGLE_VOLUME = 0.4F;
    private static final float GURGLE_PITCH = 1.3F;
    private static final float POP_VOLUME = 0.5F;
    private static final float CHIME_VOLUME = 0.8F;
    /** The lower half, its plinth and the vessel on it, and the upper, the vessel, its cap and the hub over it. */
    private static final VoxelShape LOWER = Shapes.or(Block.box(1, 0, 1, 15, 3, 15), Block.box(2, 3, 2, 14, 16, 14));
    private static final VoxelShape UPPER = Shapes.or(Block.box(2, 0, 2, 14, 11, 14), Block.box(1, 11, 1, 15, 14, 15), Block.box(5, 14, 5, 11, 16, 11));

    public BioreactorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER)
            .setValue(GROWTH, GROWN));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(GROWTH);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER : UPPER;
    }

    /** Grown, the steak comes out to whoever uses it; still growing, it gurgles. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos lower = lower(pos, state);
        BlockState vessel = server.getBlockState(lower);
        if (!vessel.is(this)) {
            return InteractionResult.PASS;
        }
        if (vessel.getValue(GROWTH) >= GROWN) {
            harvest(server, lower, player);
        } else {
            gurgle(server, lower);
        }
        return InteractionResult.CONSUME;
    }

    /** The cap hisses open in a puff of steam, the steak goes to the hand (or the feet), and the rod starts over. */
    private void harvest(ServerLevel level, BlockPos lower, Player player) {
        grow(level, lower, 0);
        ItemStack steak = new ItemStack(AerolargeRegistry.LAB_GROWN_STEAK.get());
        if (!player.addItem(steak)) {
            player.drop(steak, false);
        }
        Vec3 seal = Vec3.atBottomCenterOf(lower.above()).add(0, SEAL_UP, 0);
        level.playSound(null, seal.x, seal.y, seal.z, AerolargeRegistry.BIOREACTOR_HARVEST.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.WHITE_SMOKE, seal.x, seal.y, seal.z, STEAM_PUFFS, STEAM_SPREAD, 0.05, STEAM_SPREAD, 0.02);
        level.scheduleTick(lower, this, STAGE_TICKS);
    }

    /** A vessel still growing gurgles, and its growth is set going again if its tick was lost. */
    private void gurgle(ServerLevel level, BlockPos lower) {
        level.playSound(null, lower, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, GURGLE_VOLUME, GURGLE_PITCH);
        keepGrowing(level, lower);
    }

    /** A vessel's growth set going again, its next stage a stage's time away, if its tick was lost. */
    private void keepGrowing(ServerLevel level, BlockPos lower) {
        LostTicks.rearm(level, lower, this, STAGE_TICKS);
    }

    /** A vessel still growing is looked at now and then, in case its tick was lost. */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER && state.getValue(GROWTH) < GROWN;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        keepGrowing(level, pos);
    }

    /** A stage of growth, on the lower half's tick: a pop of bubbles, the next stage set going, or a chime when whole. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(HALF) != DoubleBlockHalf.LOWER || state.getValue(GROWTH) >= GROWN) {
            return;
        }
        int growth = state.getValue(GROWTH) + 1;
        grow(level, pos, growth);
        if (growth < GROWN) {
            level.playSound(null, pos, SoundEvents.BUBBLE_COLUMN_BUBBLE_POP, SoundSource.BLOCKS, POP_VOLUME, 1.0F);
            level.scheduleTick(pos, this, STAGE_TICKS);
        } else {
            level.playSound(null, pos.above(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, CHIME_VOLUME, 1.0F);
        }
    }

    /** Sets both halves to a stage of growth. */
    private void grow(ServerLevel level, BlockPos lower, int growth) {
        for (BlockPos half : new BlockPos[]{lower, lower.above()}) {
            BlockState state = level.getBlockState(half);
            if (state.is(this)) {
                level.setBlock(half, state.setValue(GROWTH, growth), Block.UPDATE_ALL);
            }
        }
    }
}
