package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A care package: a crate to pack with supplies and drop from the airliner. Use it to pack it; sneak and use it with
 * an empty hand to shove it the way you face, or give it a redstone pulse to send it off the way it faces, out of the
 * cargo door. Out in the air it falls free a moment, then floats down under a parachute and lands as the same crate
 * (see {@link dev.aerolarge.entity.FallingCrate}). Broken, it keeps what is packed in it, like a shulker box.
 */
public class CargoCrateBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<CargoCrateBlock> CODEC = simpleCodec(CargoCrateBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** How hard a shove sends it off, in blocks a tick. */
    private static final double SHOVE = 0.45;

    public CargoCrateBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CargoCrateBlockEntity crate) {
            if (player.isShiftKeyDown()) {
                Vec3 look = player.getLookAngle();
                crate.launch(new Vec3(look.x, 0, look.z).normalize().scale(SHOVE), player.getUUID());
            } else {
                player.openMenu(crate);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        if (level.isClientSide) {
            return;
        }
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) {
            return;
        }
        if (powered && level.getBlockEntity(pos) instanceof CargoCrateBlockEntity crate) {
            crate.launch(Vec3.atLowerCornerOf(state.getValue(FACING).getNormal()).scale(SHOVE), null);
        } else {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
    }

    /** Broken in creative mode, where nothing drops, a packed crate still drops itself with what is in it. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && level.getBlockEntity(pos) instanceof CargoCrateBlockEntity crate && !crate.isEmpty()) {
            ItemStack packed = new ItemStack(this);
            packed.applyComponents(crate.collectComponents());
            ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, packed);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CargoCrateBlockEntity(pos, state);
    }
}
