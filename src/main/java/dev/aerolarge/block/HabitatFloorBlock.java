package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A patch of the tardigrade's tank floor lying on the rocket's floor as a carpet does, low enough for the creature to
 * walk over: a shallow pool, clear water rippling over pebbles, its surface glinting (it is no water to spill, so
 * nothing runs out of it however she tilts); or a bed of smooth pebbles in the moss. Broken, it gives itself back,
 * kind and all.
 */
public class HabitatFloorBlock extends CarpetBlock {
    private static final MapCodec<HabitatFloorBlock> CODEC = simpleCodec(HabitatFloorBlock::new);
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);

    /** What the patch is. */
    public enum Kind implements StringRepresentable {
        POOL, PEBBLES;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public HabitatFloorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(KIND, Kind.POOL));
    }

    @Override
    public MapCodec<? extends CarpetBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND);
    }

    /** A patch of a kind. */
    public BlockState of(Kind kind) {
        return defaultBlockState().setValue(KIND, kind);
    }

    /** Itself, with its kind, the way one broken comes back; the pool is left plain, so they stack. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (state.getValue(KIND) != defaultBlockState().getValue(KIND)) {
            stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(KIND, state.getValue(KIND)));
        }
        return stack;
    }
}
