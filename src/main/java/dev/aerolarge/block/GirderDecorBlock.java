package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What hangs under a steel girder across the rocket's ceilings, themed to the room (see {@link SteelGirderBlock}): the
 * entry's mission-patch banners, the cargo bay's chain hoist, the mess's rack of copper pots and pans, the bunk room's
 * warm fairy lights, the greenhouse's hanging baskets, the pool's bunting, the club's stage spotlights and the lab's
 * cable tray. It hangs in the block under the girder, clipped to the girder's lower flange, over the head of anyone
 * walking under it, and nothing collides with it. AXIS is the way it runs, the girder's; PART, which of a pair of
 * blocks along the girder it is (see {@link Part}). Placed by the rocket's blueprint, or by hand under any girder;
 * broken, it gives itself back as it hung.
 */
public class GirderDecorBlock extends Block {
    private static final MapCodec<GirderDecorBlock> CODEC = simpleCodec(GirderDecorBlock::new);
    /** The way it runs, along the girder over it. */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    /** Which room's decoration it is. */
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    /** Which of a pair of blocks along its girder it is. */
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** How brightly the bunk room's fairy lights light the room round them, as a block's light level: cosy. */
    private static final int FAIRY_GLOW = 7;
    /** How brightly a club spotlight lights the room round it, as a block's light level. */
    private static final int SPOTLIGHT_GLOW = 10;
    /** What can be picked out of it: its upper part, the whole way along it and through the middle of its block. */
    private static final VoxelShape ALONG_X = Block.box(0, 2, 3, 16, 16, 13);
    private static final VoxelShape ALONG_Z = Block.box(3, 2, 0, 13, 16, 16);

    /** Which room's decoration it is, kept in its state by name, and whether the two of a pair of it differ. */
    public enum Kind implements StringRepresentable {
        /** The entry's: banners hanging down, each a round embroidered mission patch on navy cloth. */
        MISSION_PATCHES(true),
        /** The cargo bay's: a chain hoist on a trolley riding the girder, its yellow hook on the chain. */
        CHAIN_HOIST(false),
        /** The mess's: a rack of copper pots and pans on hooks. */
        POT_RACK(true),
        /** The bunk room's: warm fairy lights looping along under the girder. */
        FAIRY_LIGHTS(true),
        /** The greenhouse's: a wicker basket on four cords, trailing green. */
        HANGING_BASKET(false),
        /** The pool's: bunting, a string of triangular pennants in the pool's colours. */
        BUNTING(true),
        /** The club's: a stage spotlight clamped under the girder, angled down, its lens glowing pink or cyan. */
        SPOTLIGHT(true),
        /** The lab's: a ladder of cable tray, bundled coloured cables along its rungs. */
        CABLE_TRAY(false);

        private final boolean twoLooks;

        Kind(boolean twoLooks) {
            this.twoLooks = twoLooks;
        }

        /**
         * Whether it has a look for the first of a pair and another for the second (see {@link Part}); where it has
         * one only, it is always the first, so those broken stack.
         */
        public boolean twoLooks() {
            return twoLooks;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Which of a pair of blocks along its girder it is, reading toward the east or the south, so a decoration two
     * blocks long is drawn as one: a loop of fairy lights or bunting falls from where it is tied across the first and
     * rises to its next tie across the second, and a pot rack starts in the first and ends in the second. What hangs
     * one to a block has two looks to alternate, one for each: the entry's two patches, and the club's spotlights,
     * pink angled toward the west or north and cyan toward the east or south.
     */
    public enum Part implements StringRepresentable {
        FIRST,
        SECOND;

        /** The other of the pair, as the pair is read from its other end. */
        public Part other() {
            return this == FIRST ? SECOND : FIRST;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public GirderDecorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(KIND, Kind.CHAIN_HOIST).setValue(PART, Part.FIRST));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, KIND, PART);
    }

    /** A decoration of a kind, running along an axis, as which of a pair. */
    public BlockState hung(Kind kind, Direction.Axis axis, Part part) {
        return defaultBlockState().setValue(KIND, kind).setValue(AXIS, axis).setValue(PART, part);
    }

    /** How brightly a decoration lights the room round it: the fairy lights and the spotlights do. */
    public static int light(BlockState state) {
        return switch (state.getValue(KIND)) {
            case FAIRY_LIGHTS -> FAIRY_GLOW;
            case SPOTLIGHT -> SPOTLIGHT_GLOW;
            default -> 0;
        };
    }

    /**
     * Hung along the girder over it, or else the way whoever hangs it looks; only where it hangs from something (see
     * {@link Hanging}), never standing on the floor.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        if (!Hanging.hangs(context)) {
            return null;
        }
        BlockState above = context.getLevel().getBlockState(context.getClickedPos().above());
        boolean underGirder = above.getBlock() instanceof SteelGirderBlock && above.getValue(RotatedPillarBlock.AXIS).isHorizontal();
        Direction.Axis axis = underGirder ? above.getValue(RotatedPillarBlock.AXIS) : context.getHorizontalDirection().getAxis();
        return defaultBlockState().setValue(AXIS, axis);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return runningToward(state, rotation.rotate(eastOrSouth(state)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return runningToward(state, mirror.mirror(eastOrSouth(state)));
    }

    /** The way a decoration's pair is read toward: east along x, south along z. */
    private static Direction eastOrSouth(BlockState state) {
        return Direction.fromAxisAndDirection(state.getValue(AXIS), Direction.AxisDirection.POSITIVE);
    }

    /**
     * A decoration turned so that the way its pair was read toward is now the way given: along that way's axis, and,
     * of a kind with two looks, the other of its pair where that way is west or north, so a loop still falls from its
     * tie once it is turned.
     */
    private static BlockState runningToward(BlockState state, Direction toward) {
        BlockState turned = state.setValue(AXIS, toward.getAxis());
        boolean reversed = toward.getAxisDirection() == Direction.AxisDirection.NEGATIVE && state.getValue(KIND).twoLooks();
        return reversed ? turned.setValue(PART, state.getValue(PART).other()) : turned;
    }

    /** A decoration of the kind it is and the part of a pair, the way one broken comes back. */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        ItemStack stack = new ItemStack(this);
        if (state.getValue(KIND) != Kind.CHAIN_HOIST || state.getValue(PART) != Part.FIRST) {
            stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(KIND, state.getValue(KIND)).with(PART, state.getValue(PART)));
        }
        return stack;
    }
}
