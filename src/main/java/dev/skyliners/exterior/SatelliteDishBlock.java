package dev.skyliners.exterior;

import dev.skyliners.block.Mount;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * A satellite dish, a white bowl with its feed horn held out over it on two struts, a tiny amber light at its tip,
 * turning slowly as if tracking something far off (see the client's {@code SatelliteDishRenderer}); the big one's
 * dish three times as wide, nearly four blocks across. Its mount is its block's own model, a shape of its own for each
 * way it is put up (see {@link HullFittingBlock}): on a floor a post on a foot plate, the dish turning round on top of
 * it, tipped up at the sky; on a wall a plate and an arm out from it, the dish at its end looking out from the wall and
 * sweeping from side to side; and under a ceiling a hanger, the dish hung from it, tipped down at the ground, turning
 * round. PART is what the state looks like: only its mount is ever placed, its dish drawn over it.
 */
public class SatelliteDishBlock extends HullFittingBlock implements EntityBlock {
    /** Which of its parts the state looks like: its mount, placed, or its dish, drawn over the mount. */
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** Where its mounts hold its dish, and how big it is drawn. */
    private final Dish dish;

    /**
     * Where a dish's mounts hold it, on a floor, on a wall and under a ceiling, in pixels from the block's corner as
     * drawn facing north (see tools/generate_resources.mjs); how many times its model's size the dish is drawn; how far
     * it is tipped up at the sky on a floor, and down at the ground under a ceiling, in degrees; and how far either way
     * it sweeps on a wall, in degrees: a big dish less, so it never swings into its wall.
     */
    public record Dish(float[] floor, float[] wall, float[] ceiling, float scale, float tilt, float sweep) {
    }

    /** Which part of the dish a state draws. */
    public enum Part implements StringRepresentable {
        MOUNT,
        DISH;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** A dish on mounts of the outline given, held and drawn as given. */
    public SatelliteDishBlock(Properties properties, Outline outline, Dish dish) {
        super(properties, outline);
        this.dish = dish;
        registerDefaultState(defaultBlockState().setValue(PART, Part.MOUNT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    /** Where its mounts hold its dish and how it is drawn. */
    public Dish dish() {
        return dish;
    }

    /** Where its mount holds its dish, put up as given, in pixels from the block's corner as drawn facing north. */
    public float[] pivot(Mount mount) {
        return switch (mount) {
            case FLOOR -> dish.floor();
            case WALL -> dish.wall();
            case CEILING -> dish.ceiling();
        };
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SatelliteDishBlockEntity(pos, state);
    }
}
