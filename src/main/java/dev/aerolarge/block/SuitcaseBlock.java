package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.compat.SableFlight;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A hard-shell suitcase standing on its end, as stowed in the hold: a small container of a row of slots, opened with
 * a zip and a puff. FACING is the way its front faces; COLOR is its shell's, one picked at random when placed by
 * hand. The golden suitcase is the same, all gold: three are hidden about an airliner's hold, glinting with a faint
 * chime while a player is near, and opening one counts toward the Lost and Found advancement (see
 * {@link SuitcaseBlockEntity}); a golden suitcase's colour does not show.
 */
public class SuitcaseBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<SuitcaseBlock> CODEC = simpleCodec(properties -> new SuitcaseBlock(properties, false));
    private static final MapCodec<SuitcaseBlock> GOLDEN_CODEC = simpleCodec(properties -> new SuitcaseBlock(properties, true));
    /** The shells' colours. */
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class, DyeColor.RED, DyeColor.BLUE, DyeColor.GREEN, DyeColor.PURPLE);
    /** Standing on its end, its broad front and back facing the way it faces, its handle on top. */
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
        Direction.NORTH, Block.box(2, 0, 5, 14, 16, 11),
        Direction.SOUTH, Block.box(2, 0, 5, 14, 16, 11),
        Direction.EAST, Block.box(5, 0, 2, 11, 16, 14),
        Direction.WEST, Block.box(5, 0, 2, 11, 16, 14)
    );
    /** How near a player has to be for a golden suitcase to glint, in blocks. */
    private static final double GLINT_RANGE = 8;
    /** Of the client's random visits to a golden suitcase, the share that also chimes. */
    private static final float CHIME_CHANCE = 0.25F;
    private static final float CHIME_VOLUME = 0.4F;
    /** The puff as a suitcase is opened. */
    private static final int PUFFS = 4;

    private final boolean golden;

    public SuitcaseBlock(Properties properties, boolean golden) {
        super(properties);
        this.golden = golden;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLOR, DyeColor.RED));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return golden ? GOLDEN_CODEC : CODEC;
    }

    public boolean golden() {
        return golden;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLOR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        DyeColor[] colours = COLOR.getPossibleValues().toArray(DyeColor[]::new);
        return defaultBlockState()
            .setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(COLOR, colours[context.getLevel().getRandom().nextInt(colours.length)]);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof SuitcaseBlockEntity suitcase) {
            player.openMenu(suitcase);
            server.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 1.0F, 1.3F);
            server.sendParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, PUFFS, 0.2, 0.1, 0.2, 0.01);
            if (player instanceof ServerPlayer finder) {
                suitcase.found(finder);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A golden suitcase's hint: while a player is near, it glints, and now and then chimes faintly. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // Where it is in the world, aboard an airliner in flight far from its block in Sable's plot.
        Vec3 there = SableFlight.toWorld(level, Vec3.atCenterOf(pos));
        if (!golden || level.getNearestPlayer(there.x, there.y, there.z, GLINT_RANGE, false) == null) {
            return;
        }
        level.addParticle(ParticleTypes.WAX_ON, pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 0.2 + random.nextDouble() * 0.8,
            pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0, 0, 0);
        if (random.nextFloat() < CHIME_CHANCE) {
            level.playLocalSound(there.x, there.y, there.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, CHIME_VOLUME, 1.6F + random.nextFloat() * 0.4F, false);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        Containers.dropContentsOnDestroy(state, newState, level, pos);
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SuitcaseBlockEntity(pos, state);
    }
}
