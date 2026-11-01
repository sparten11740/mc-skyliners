package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The instruments of the rocket's lab, in five kinds: a microscope, white with a lit stage and its eyepieces toward
 * whoever uses it, who looks into it and sees a slide magnified (see {@link LookIntoMicroscope}); a rack of sample
 * vials, their liquids glowing faintly in five colours; a scan screen on its stand showing a body scan of the specimen,
 * a line sweeping down it; the vitals screen set into the tardigrade's tank over its feeding hatch, a pulse tracing
 * across it under the creature's own picture, which goes slow and blue (DORMANT) while the creature is curled up in
 * its tun, lies down under a crescent moon, its pulse slow (ASLEEP), while it sleeps, and gives way to stars streaming
 * past (AWAY) while it is away through its spore drive (see {@link HabitatHatchBlockEntity}); and specimen jars, three on a tray, a pickled tentacle, an eye and a
 * sprouting potato each in its own faintly glowing liquid. FACING is the way its front looks. Broken, they give
 * themselves back, kind and all. A vitals screen set up by hand has no creature to watch, and only ever beats.
 */
public class LabInstrumentBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<LabInstrumentBlock> CODEC = simpleCodec(LabInstrumentBlock::new);
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    public static final BooleanProperty DORMANT = BooleanProperty.create("dormant");
    public static final BooleanProperty ASLEEP = BooleanProperty.create("asleep");
    /** Whether the vitals screen shows the creature away through its spore drive, stars streaming past. */
    public static final BooleanProperty AWAY = BooleanProperty.create("away");
    /** How brightly a kind glows, in light levels: its screen, its vials or its jars. */
    private static final int SCREEN_GLOW = 5;
    private static final int VIALS_GLOW = 3;
    private static final int DORMANT_GLOW = 2;
    private static final int JARS_GLOW = 2;
    private static final Map<Kind, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(Kind.class);

    /** An instrument's kind, and its outline as drawn facing north, in pixels. */
    public enum Kind implements StringRepresentable {
        MICROSCOPE(4, 0, 4, 12, 13, 13),
        SAMPLES(2, 0, 5, 14, 9, 11),
        SCAN(1, 0, 6, 15, 13, 11),
        VITALS(0, 0, 0, 16, 16, 2),
        SPECIMENS(2, 0, 4, 14, 9, 12);

        private final double[] outline;

        Kind(double... outline) {
            this.outline = outline;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    static {
        for (Kind kind : Kind.values()) {
            Map<Direction, VoxelShape> turned = new EnumMap<>(Direction.class);
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                turned.put(facing, box(facing, kind.outline));
            }
            SHAPES.put(kind, turned);
        }
    }

    public LabInstrumentBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(KIND, Kind.MICROSCOPE).setValue(DORMANT, false)
            .setValue(ASLEEP, false).setValue(AWAY, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, KIND, DORMANT, ASLEEP, AWAY);
    }

    /** An instrument of a kind, its front looking the way given. */
    public BlockState of(Kind kind, Direction facing) {
        return defaultBlockState().setValue(KIND, kind).setValue(FACING, facing);
    }

    /** How brightly an instrument glows, in light levels. */
    public static int light(BlockState state) {
        return switch (state.getValue(KIND)) {
            case MICROSCOPE -> 0;
            case SAMPLES -> VIALS_GLOW;
            case SCAN -> SCREEN_GLOW;
            case VITALS -> state.getValue(DORMANT) || state.getValue(ASLEEP) ? DORMANT_GLOW : SCREEN_GLOW;
            case SPECIMENS -> JARS_GLOW;
        };
    }

    /** A box given in pixels as drawn facing north, turned to face the given way about the block's middle. */
    private static VoxelShape box(Direction facing, double[] box) {
        double x0 = box[0];
        double y0 = box[1];
        double z0 = box[2];
        double x1 = box[3];
        double y1 = box[4];
        double z1 = box[5];
        return switch (facing) {
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    /** Put down by hand, it faces whoever puts it there. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Itself, with its kind, the way one broken comes back; the microscope is left plain, so they stack. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        ItemStack stack = super.getCloneItemStack(level, pos, state);
        if (state.getValue(KIND) != defaultBlockState().getValue(KIND)) {
            stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(KIND, state.getValue(KIND)));
        }
        return stack;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(KIND)).getOrDefault(state.getValue(FACING), Shapes.block());
    }

    /**
     * A microscope looked into: the server picks one of its slides and has the player's screen show it magnified, with
     * the click of its focus.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (state.getValue(KIND) != Kind.MICROSCOPE) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer looker) {
            LookIntoMicroscope.Slide[] slides = LookIntoMicroscope.Slide.values();
            server.playSound(null, pos, SoundEvents.SPYGLASS_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            PacketDistributor.sendToPlayer(looker, new LookIntoMicroscope(slides[server.random.nextInt(slides.length)]));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
