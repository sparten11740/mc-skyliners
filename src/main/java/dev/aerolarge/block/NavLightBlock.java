package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An exterior light: a small lens on the airframe, FACING the way it shines. What it is, and so how it glows, is its
 * KIND: the steady red and green navigation lights on the port and starboard wingtips and the white one on the tail,
 * the white strobes that double-flash at the wingtips, and the red anti-collision beacons above and below the
 * fuselage. The glow is drawn by its renderer, so flashing never changes the block.
 */
public class NavLightBlock extends DirectionalBlock implements EntityBlock {
    private static final MapCodec<NavLightBlock> CODEC = simpleCodec(NavLightBlock::new);
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.UP, Block.box(5, 0, 5, 11, 5, 11));
        SHAPES.put(Direction.DOWN, Block.box(5, 11, 5, 11, 16, 11));
        SHAPES.put(Direction.NORTH, Block.box(5, 5, 11, 11, 11, 16));
        SHAPES.put(Direction.SOUTH, Block.box(5, 5, 0, 11, 11, 5));
        SHAPES.put(Direction.WEST, Block.box(11, 5, 5, 16, 11, 11));
        SHAPES.put(Direction.EAST, Block.box(0, 5, 5, 5, 11, 11));
    }

    public enum Kind implements StringRepresentable {
        RED(0xFF2A1A, false), GREEN(0x1AFF4A, false), WHITE(0xFFFFFF, false), STROBE(0xFFFFFF, true), BEACON(0xFF2010, true);

        private final int colour;
        private final boolean flashing;

        Kind(int colour, boolean flashing) {
            this.colour = colour;
            this.flashing = flashing;
        }

        public int colour() {
            return colour;
        }

        /**
         * How bright the light is at a moment, 0 to 1: the strobe double-flashes once a second and a bit, the beacon
         * blinks once a second, the navigation lights stay on.
         */
        public float brightness(float ticks) {
            if (!flashing) {
                return 1;
            }
            if (this == STROBE) {
                float phase = ticks % 24;
                return phase < 1.5F || (phase >= 4 && phase < 5.5F) ? 1 : 0;
            }
            float phase = ticks % 20;
            return phase < 4 ? 1 - phase / 4 : 0;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public NavLightBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(KIND, Kind.WHITE));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, KIND);
    }

    /** Put up by hand, it shines out from the face of the block it is put against. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    /**
     * Turned with the airliner it is built on, or a structure, so a wingtip's light still shines out from the tip:
     * {@link DirectionalBlock} leaves that to each block.
     */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NavLightBlockEntity(pos, state);
    }
}
