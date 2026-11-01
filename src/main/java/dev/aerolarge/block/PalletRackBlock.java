package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A bay of a pallet rack, as a warehouse's or the rocket's cargo bay's: blue steel uprights at its corners, orange
 * beams along its front and back at its foot, a wooden pallet on them and LOAD on the pallet, cardboard boxes taped
 * shut, a pair of steel drums, wooden crates or a pallet left empty. Bays stood on one another make a rack as tall as
 * the room, their uprights running on; side by side, a wall of them. FACING is the way its front faces, the beams
 * across it; it is a whole block to stand against, and only to look at.
 */
public class PalletRackBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<PalletRackBlock> CODEC = simpleCodec(PalletRackBlock::new);
    public static final EnumProperty<Load> LOAD = EnumProperty.create("load", Load.class);

    /** What stands on a bay's pallet. */
    public enum Load implements StringRepresentable {
        BOXES,
        DRUMS,
        CRATES,
        EMPTY;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public PalletRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LOAD, Load.BOXES));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOAD);
    }

    /** A bay of this block with this load, facing the way given, as the blueprints put one in. */
    public static BlockState loaded(Block rack, Load load, Direction facing) {
        return rack.defaultBlockState().setValue(LOAD, load).setValue(FACING, facing);
    }

    /** Put down by hand, it faces whoever puts it there. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Itself, with its load, the way one broken comes back; the boxes is left plain, so they stack. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (state.getValue(LOAD) != defaultBlockState().getValue(LOAD)) {
            stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(LOAD, state.getValue(LOAD)));
        }
        return stack;
    }
}
