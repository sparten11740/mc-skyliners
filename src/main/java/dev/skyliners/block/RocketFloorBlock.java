package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A block of one of the rocket's floors, finished on top as the room it is the floor of and underneath as the ceiling
 * of the room under it, each in that room's {@link RocketFinish}: so the mess walks on oak under the cargo bay's
 * corrugated ceiling. Only her blueprint places it, in place of the plain cabin floor; broken, it gives back that.
 */
public class RocketFloorBlock extends Block {
    /** The room it is the floor of, and the room it is the ceiling of. */
    public static final EnumProperty<RocketFinish> FLOOR = EnumProperty.create("floor", RocketFinish.class);
    public static final EnumProperty<RocketFinish> CEILING = EnumProperty.create("ceiling", RocketFinish.class);

    public RocketFloorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FLOOR, RocketFinish.ENTRY).setValue(CEILING, RocketFinish.ENTRY));
    }

    /** The floor of one room over the ceiling of another. */
    public BlockState between(RocketFinish floor, RocketFinish ceiling) {
        return defaultBlockState().setValue(FLOOR, floor).setValue(CEILING, ceiling);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FLOOR, CEILING);
    }

    /** Picked, the plain cabin floor it gives back. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(SkylinersRegistry.CABIN_FLOOR.get());
    }
}
