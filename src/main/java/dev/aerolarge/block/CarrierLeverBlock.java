package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The flight deck's release lever at its forward corner: a yellow-and-black lever on an iron stand. Up, the deck's
 * clamps lock down any plane come to rest on it (see {@link DeckClampBlockEntity}); used, it goes down with a hiss and
 * every clamp of the zeppelin opens and stays open, so a plane can take off; used again, it goes back up. RELEASED is
 * whether it is down; FACING is the way whoever works it looks.
 */
public class CarrierLeverBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<CarrierLeverBlock> CODEC = simpleCodec(CarrierLeverBlock::new);
    public static final BooleanProperty RELEASED = BooleanProperty.create("released");
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 12, 12);

    public CarrierLeverBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RELEASED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RELEASED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Down or back up, with a click and, going down, the hiss of the clamps' rams letting go. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            boolean down = !state.getValue(RELEASED);
            level.setBlock(pos, state.setValue(RELEASED, down), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.6F, down ? 0.6F : 0.8F);
            if (down) {
                level.playSound(null, pos, AerolargeRegistry.PLUG_DOOR_HISS.get(), SoundSource.BLOCKS, 0.6F, 0.8F);
            }
            release(server, pos, down);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Every clamp of the zeppelin this lever is part of, opened and kept open, or set to lock again. */
    private static void release(ServerLevel level, BlockPos pos, boolean down) {
        UUID zeppelin = SableFlight.bodyAt(level, pos);
        for (DeckClampBlockEntity clamp : DeckClampBlockEntity.loaded(level)) {
            if (zeppelin != null && zeppelin.equals(SableFlight.bodyAt(level, clamp.getBlockPos()))) {
                clamp.released(level, down);
            }
        }
    }
}
