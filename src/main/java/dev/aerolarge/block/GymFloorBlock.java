package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A sprung sports floor, as a school gym's is: narrow strips of lacquered maple laid along AXIS, their butt joints
 * staggered, catching the light here and there, over white panelling underneath, so the hold under the gym hall sees a
 * ceiling. With LINE it is a court line painted on it in white, the maple's seams faint under the paint; use white
 * dye on a plain board to paint one. Put down, its strips run the way you face.
 */
public class GymFloorBlock extends Block {
    private static final MapCodec<GymFloorBlock> CODEC = simpleCodec(GymFloorBlock::new);
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final BooleanProperty LINE = BooleanProperty.create("line");

    public GymFloorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(LINE, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, LINE);
    }

    /** Whether the state is a court line painted on the gym floor. */
    public static boolean isLine(BlockState state) {
        return state.getBlock() instanceof GymFloorBlock && state.getValue(LINE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(AXIS, context.getHorizontalDirection().getAxis());
    }

    /** White dye paints a court line on a plain board, using the dye up (unless in creative mode). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof DyeItem dye) || dye.getDyeColor() != DyeColor.WHITE || state.getValue(LINE)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(LINE, true), Block.UPDATE_CLIENTS);
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A quarter turn lays the strips the other way; a half turn leaves them as they were. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        boolean quarter = rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
        if (!quarter) {
            return state;
        }
        return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
    }
}
