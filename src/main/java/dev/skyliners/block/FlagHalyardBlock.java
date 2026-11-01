package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The foot of a flagstaff, where its halyard is made fast to a brass cleat: the ship's colours are hoisted here. Use it
 * with a banner and the banner is bent on and run up the staff, a pulley squeaking, and once it is closed up at the top
 * it flies as a great flag streaming aft, with a flap of cloth, and the zeppelin it stands on has its pattern painted
 * on its top and bottom fins (see the client's {@code ShipsColoursRenderer} and {@code ShipsColours}). Use it with an
 * empty hand and the colours are struck, the banner handed back. FACING is the way the flag streams out; the staff is
 * the {@link FlagstaffBlock}s stacked on it.
 */
public class FlagHalyardBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<FlagHalyardBlock> CODEC = simpleCodec(FlagHalyardBlock::new);
    /** The most flagstaff looked for over a halyard, in blocks. */
    private static final int TALLEST_STAFF = 32;
    /** How loud the flag's flap is as it breaks out at the top of the staff (heard 16 blocks off for each). */
    private static final float FLAP_VOLUME = 3;
    private static final float FLAP_PITCH = 0.6F;
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 2, 13), Block.box(6.5, 2, 6.5, 9.5, 16, 9.5));

    public FlagHalyardBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Put down by hand, its flag streams out the way whoever puts it down looks. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** How many blocks of flagstaff stand on a halyard, the colours flown from the top of them. */
    public static int staffHeight(BlockGetter level, BlockPos foot) {
        int height = 0;
        while (height < TALLEST_STAFF && level.getBlockState(foot.above(height + 1)).getBlock() instanceof FlagstaffBlock) {
            height++;
        }
        return height;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof BannerItem) || !(level.getBlockEntity(pos) instanceof FlagHalyardBlockEntity halyard) || halyard.flying()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            halyard.hoist(stack);
            stack.consume(1, player);
            level.playSound(null, pos, SkylinersRegistry.FLAG_HALYARD_SOUND.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            level.scheduleTick(pos, this, FlagHalyardBlockEntity.HOIST_TICKS);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof FlagHalyardBlockEntity halyard) || !halyard.flying()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack banner = halyard.strike();
            if (!player.getInventory().add(banner)) {
                player.drop(banner, false);
            }
            level.playSound(null, pos, SkylinersRegistry.FLAG_HALYARD_SOUND.get(), SoundSource.BLOCKS, 1.0F, 0.8F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The colours closed up at the top, the flag breaks out with a flap; still rising, it looks again later. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!(level.getBlockEntity(pos) instanceof FlagHalyardBlockEntity halyard) || !halyard.flying()) {
            return;
        }
        long left = halyard.hoistLeft();
        if (left > 0) {
            level.scheduleTick(pos, this, (int) left);
            return;
        }
        level.playSound(null, pos.above(staffHeight(level, pos)), SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.BLOCKS, FLAP_VOLUME, FLAP_PITCH);
    }

    /** Broken, it drops the colours it flies. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof FlagHalyardBlockEntity halyard && halyard.flying()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, halyard.strike());
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlagHalyardBlockEntity(pos, state);
    }
}
