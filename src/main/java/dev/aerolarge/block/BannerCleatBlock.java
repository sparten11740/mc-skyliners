package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The towed banner's cleat on the back wall of a zeppelin's control car: a brass cleat with the tow rope made fast to
 * it, coiled round it while the banner is hauled in and run out to the tail while it streams. Used, it streams the
 * banner out behind her tail or hauls it in again (see the client's {@code TowedBanner}, which draws it). STREAMING is
 * whether the banner is out; its block entity only lets the clients find it.
 */
public class BannerCleatBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<BannerCleatBlock> CODEC = simpleCodec(BannerCleatBlock::new);
    public static final BooleanProperty STREAMING = BooleanProperty.create("streaming");
    private static final VoxelShape SHAPE = Block.box(3, 3, 3, 13, 13, 13);
    /** The pitch of the rope running through its block as the banner goes out and as it comes in, and of its cloth. */
    private static final float RUNNING_OUT = 0.8F;
    private static final float HAULING_IN = 1.0F;
    private static final float FILLING = 0.7F;

    public BannerCleatBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STREAMING, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STREAMING);
    }

    /** Put up by hand, it faces whoever puts it up. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * The banner streamed out or hauled in, the rope heard running through its block either way, lower going out, and
     * the cloth heard to fill as it goes; with the {@code towedBanner} switch off, nothing.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!AerolargeConfig.TOWED_BANNER.get()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            boolean out = !state.getValue(STREAMING);
            level.setBlock(pos, state.setValue(STREAMING, out), Block.UPDATE_ALL);
            level.playSound(null, pos, AerolargeRegistry.TOW_ROPE.get(), SoundSource.BLOCKS, 1.0F, out ? RUNNING_OUT : HAULING_IN);
            if (out) {
                level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.BLOCKS, 1.0F, FILLING);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BannerCleatBlockEntity(pos, state);
    }
}
