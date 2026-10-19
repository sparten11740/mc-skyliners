package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The parking brake beside the first officer's seat: set, it holds the main gear's wheels (their wheel mounts brake
 * on a signal from above); released, the airliner rolls. It is set when the airliner is built, so it does not roll
 * away when assembled. FACING is the way the nose points.
 */
public class ParkingBrakeBlock extends HorizontalDirectionalBlock implements EntityBlock, LinkedControlBlockEntity.Driver {
    private static final MapCodec<ParkingBrakeBlock> CODEC = simpleCodec(ParkingBrakeBlock::new);
    public static final BooleanProperty SET = BlockStateProperties.POWERED;
    public static final String BRAKES = "brakes";
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 8, 12);

    public ParkingBrakeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SET, true));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SET);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public int signal(BlockState state, String group) {
        return state.getValue(SET) ? 15 : 0;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            boolean set = !state.getValue(SET);
            level.setBlock(pos, state.setValue(SET, set), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.4F, set ? 1.4F : 1.7F);
            if (level.getBlockEntity(pos) instanceof Control control) {
                control.resync();
            }
            player.displayClientMessage(Component.translatable(set ? "message.aerolarge.brake.set" : "message.aerolarge.brake.released"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LinkedControlBlockEntity(pos, state);
    }
}
