package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.spaceweather.RockShape;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * The stone of space weather's rocks: cut to a rock's lumps as the hull's skin is to her curve (see {@link
 * ShellBlock}), by the same cutter, and of one of the kinds of stone a rock may be of, or of dark stone cracked with
 * magma. Only space weather places it, one shot of a laser cannon breaks it, and it drops nothing.
 */
public class SpaceRockBlock extends ShellBlock {
    /** Which of the kinds of stone it is of (see {@link RockShape#STONES}); on a magma rock, nothing in particular. */
    public static final IntegerProperty STONE = IntegerProperty.create("stone", 0, RockShape.STONES.length - 1);

    public SpaceRockBlock(Properties properties) {
        super(properties, () -> Items.COBBLESTONE);
    }

    /** The stone of a rock that looks so, whole. */
    public static BlockState of(RockShape.Look look) {
        return look.magma() ? SkylinersRegistry.SPACE_ROCK_MAGMA.get().defaultBlockState()
            : SkylinersRegistry.SPACE_ROCK.get().defaultBlockState().setValue(STONE, look.stone());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STONE);
    }
}
