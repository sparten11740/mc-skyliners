package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.radar.Transponders;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A transponder: wherever it is, it shows on every autopilot's map and on the tower's radar by the picture and the
 * colour picked on its panel, as a beacon does in Subnautica. Put one at home, at a mine or at an airfield to fly back
 * to it; the control tower has one, and every airliner one on its flight deck, shown on the radar where it flies. It
 * is a module of the centre console, so it stands among the flight deck's controls, and on a desk anywhere else; its
 * little screen shows its picture in its colour. FACING is the way its face looks, toward whoever placed it.
 */
public class TransponderBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<TransponderBlock> CODEC = simpleCodec(TransponderBlock::new);
    /** A module of the console: its knobs are too small to catch on. */
    private static final VoxelShape SHAPE = ConsoleModule.SLAB;

    public TransponderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Used, it opens its panel, to pick its picture and colour. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer opener && level.getBlockEntity(pos) instanceof TransponderBlockEntity transponder) {
            PacketDistributor.sendToPlayer(opener, new OpenTransponder(pos, transponder.icon(), transponder.colour()));
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5F, 1.6F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Gone from where it stood, it goes off the maps there; moved (into an airliner being assembled), it shows where the
     * airliner takes it once loaded there (see {@link Transponders#keep}).
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            Transponders.remove(server, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransponderBlockEntity(pos, state);
    }
}
