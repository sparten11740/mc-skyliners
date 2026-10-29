package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A disco ball for the kids' den: a ball faceted all over with little mirror tiles, hanging on a short rod from the
 * ceiling (or from a light cable down from it), that turns slowly while it is on (LIT), its tiles glinting, and casts
 * spots of coloured light that sweep round the room as it turns. The ball, its turning and the spots are drawn on the
 * client (see {@link DiscoBallBlockEntity}); the block itself is only the rod, and while on it lights the room round
 * it as a lamp does, for wherever the spots cannot be drawn.
 *
 * <p>Used, with or without anything in hand, it switches off, or on, with a chime falling or rising: a child who
 * cannot read sees it slow to a stop and go dark, or spin up and light the room. Nothing bumps into it: it hangs
 * overhead, out of the way of a child leaping out of the ball pit under it.
 */
public class DiscoBallBlock extends Block implements EntityBlock {
    private static final MapCodec<DiscoBallBlock> CODEC = simpleCodec(DiscoBallBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** Where the ball's middle is in its block, in blocks up from its bottom (in the middle across), and its radius. */
    public static final double MIDDLE = 7 / 16.0;
    public static final double RADIUS = 6 / 16.0;
    /** The ball, and the rod it hangs from up to the top of the block. */
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 1, 2, 14, 13, 14), Block.box(7.5, 13, 7.5, 8.5, 16, 8.5));
    /** The chime as it switches: how loud, and its pitch as it goes on and as it goes off. */
    private static final float CHIME_VOLUME = 1.0F;
    private static final float CHIME_ON = 1.5F;
    private static final float CHIME_OFF = 0.6F;

    public DiscoBallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, true));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    /** Put down only where it hangs from something (see {@link Hanging}). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return Hanging.hangs(context) ? defaultBlockState() : null;
    }

    /** The ball's middle in the level, for a ball at this position. */
    public static Vec3 middle(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + MIDDLE, pos.getZ() + 0.5);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockState switched = state.cycle(LIT);
            level.setBlock(pos, switched, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, CHIME_VOLUME, switched.getValue(LIT) ? CHIME_ON : CHIME_OFF);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DiscoBallBlockEntity(pos, state);
    }

    /** It only turns on the client, where it is drawn. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide && type == AerolargeRegistry.DISCO_BALL_ENTITY.get()
            ? (tickLevel, pos, tickState, ball) -> ((DiscoBallBlockEntity) ball).clientTick()
            : null;
    }
}
