package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
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
 * the white strobes that double-flash at the wingtips, the red anti-collision beacons above and below the fuselage,
 * the warm bulbs strung along a zeppelin dressed for the night, and a festive zeppelin's twinkling fairy lights and its
 * gold star. The glow is drawn by its renderer, so flashing never changes the block.
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

    /** How a light glows: steadily, double-flashing as a strobe, blinking as a beacon, or twinkling. */
    public enum Glow {
        STEADY, STROBE, BEACON, TWINKLE
    }

    public enum Kind implements StringRepresentable {
        RED(0xFF2A1A, Glow.STEADY, Kind.SMALL, 0), GREEN(0x1AFF4A, Glow.STEADY, Kind.SMALL, 0), WHITE(0xFFFFFF, Glow.STEADY, Kind.SMALL, 0),
        STROBE(0xFFFFFF, Glow.STROBE, Kind.BIG, 0), BEACON(0xFF2010, Glow.BEACON, Kind.BIG, 0), BULB(0xFFD58A, Glow.STEADY, Kind.SMALL, 0),
        FAIRY_RED(0xFF3A30, Glow.TWINKLE, Kind.FAIRY, 0), FAIRY_GREEN(0x30FF5A, Glow.TWINKLE, Kind.FAIRY, 0.25F),
        FAIRY_GOLD(0xFFC830, Glow.TWINKLE, Kind.FAIRY, 0.5F), FAIRY_BLUE(0x3A78FF, Glow.TWINKLE, Kind.FAIRY, 0.75F),
        STAR(0xFFD24A, Glow.TWINKLE, Kind.HUGE, 0);

        /**
         * How big its glow is drawn, in blocks: the steady lights small, the flashing ones big to catch the eye, the
         * fairy lights smaller still, and a festive zeppelin's star bigger than any.
         */
        private static final float SMALL = 1.6F;
        private static final float BIG = 3.2F;
        private static final float FAIRY = 1.3F;
        private static final float HUGE = 6F;
        /** How long a twinkle takes, in ticks, and how far a twinkling light dims at its dimmest. */
        private static final float TWINKLE_TICKS = 30;
        private static final float TWINKLE_DIM = 0.45F;

        private final int colour;
        private final Glow glow;
        private final float size;
        /** Where in its twinkle a twinkling light is at the game's start, a share of it, so the colours chase round. */
        private final float phase;

        Kind(int colour, Glow glow, float size, float phase) {
            this.colour = colour;
            this.glow = glow;
            this.size = size;
            this.phase = phase;
        }

        public int colour() {
            return colour;
        }

        /** How big its glow is drawn, in blocks. */
        public float size() {
            return size;
        }

        /**
         * How bright the light is at a moment, 0 to 1: the strobe double-flashes once a second and a bit, the beacon
         * blinks once a second, a fairy light twinkles a little out of step with the other colours, and the navigation
         * lights stay on.
         */
        public float brightness(float ticks) {
            return switch (glow) {
                case STEADY -> 1;
                case STROBE -> {
                    float at = ticks % 24;
                    yield at < 1.5F || (at >= 4 && at < 5.5F) ? 1 : 0;
                }
                case BEACON -> {
                    float at = ticks % 20;
                    yield at < 4 ? 1 - at / 4 : 0;
                }
                case TWINKLE -> 1 - TWINKLE_DIM * (0.5F + 0.5F * Mth.sin((float) (2 * Math.PI * (ticks / TWINKLE_TICKS + phase))));
            };
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
