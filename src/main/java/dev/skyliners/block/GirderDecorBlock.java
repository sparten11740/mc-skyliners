package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
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
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * What hangs under a steel girder across the rocket's ceilings, themed to the room (see {@link SteelGirderBlock}): the
 * entry's mission-patch banners, the cargo bay's chain hoist, the mess's rack of copper pots and pans, the bunk room's
 * warm fairy lights, the greenhouse's hanging baskets, the pool's bunting, the club's stage spotlights and the lab's
 * cable tray. Under a ceiling it hangs in the block under the girder, clipped to the girder's lower flange, over the
 * head of anyone walking under it; put up by hand it takes its shape from where it goes (see {@link #MOUNT}), so it can
 * also be fixed to a wall or stand on the floor, each shape drawing in what holds it there. Nothing collides with it.
 * AXIS is the way it runs: along the girder, along its wall or across the floor; FACING, on a wall or the floor, the
 * way it faces; PART, which of a pair of blocks along the way it runs it is (see {@link Part}). Placed by the rocket's
 * blueprint, or by hand; broken, it gives itself back as it was, whatever it was put up against.
 */
public class GirderDecorBlock extends Block {
    private static final MapCodec<GirderDecorBlock> CODEC = simpleCodec(GirderDecorBlock::new);
    /** The way it runs: along the girder over it, along its wall, or across the floor. */
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    /** Which room's decoration it is. */
    public static final EnumProperty<Kind> KIND = EnumProperty.create("kind", Kind.class);
    /** Which of a pair of blocks along the way it runs it is. */
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /**
     * How it is put up: hung under a ceiling, the shape it had before it had a mount, so one saved then loads as it
     * hung, as every one the rocket's blueprint lays still hangs; fixed to a wall; or standing on the floor.
     */
    public static final EnumProperty<Mount> MOUNT = Mount.property(Mount.CEILING, Mount.WALL, Mount.FLOOR);
    /**
     * The way it faces fixed to a wall, out from it, or standing on the floor, toward whoever stood it there; hung
     * under a ceiling, it goes by AXIS alone.
     */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** How brightly the bunk room's fairy lights light the room round them, as a block's light level: cosy. */
    private static final int FAIRY_GLOW = 7;
    /** How brightly a club spotlight lights the room round it, as a block's light level. */
    private static final int SPOTLIGHT_GLOW = 10;
    /**
     * What can be picked out of it hung under a ceiling: its upper part, the whole way along it and through the middle
     * of its block.
     */
    private static final VoxelShape ALONG_X = Block.box(0, 2, 3, 16, 16, 13);
    private static final VoxelShape ALONG_Z = Block.box(3, 2, 0, 13, 16, 16);
    /** What can be picked out of each kind fixed to a wall, by the way it faces (see {@link #onWall}). */
    private static final Map<Kind, Map<Direction, VoxelShape>> ON_WALL = byFacing(GirderDecorBlock::onWall);
    /** What can be picked out of each kind standing on the floor, by the way it faces (see {@link #onFloor}). */
    private static final Map<Kind, Map<Direction, VoxelShape>> ON_FLOOR = byFacing(GirderDecorBlock::onFloor);

    /**
     * Which room's decoration it is, kept in its state by name, and whether the two of a pair of it differ; its item's
     * looks follow this order.
     */
    public enum Kind implements StringRepresentable {
        /** The entry's: banners hanging down, each a round embroidered mission patch on navy cloth. */
        MISSION_PATCHES(true, SoundType.WOOL),
        /** The cargo bay's: a chain hoist on a trolley riding the girder, its yellow hook on the chain. */
        CHAIN_HOIST(false, SoundType.CHAIN),
        /** The mess's: a rack of copper pots and pans on hooks. */
        POT_RACK(true, SoundType.COPPER),
        /** The bunk room's: warm fairy lights looping along under the girder. */
        FAIRY_LIGHTS(true, SoundType.GLASS),
        /** The greenhouse's: a wicker basket on four cords, trailing green. */
        HANGING_BASKET(false, SoundType.AZALEA),
        /** The pool's: bunting, a string of triangular pennants in the pool's colours. */
        BUNTING(true, SoundType.WOOL),
        /** The club's: a stage spotlight clamped under the girder, angled down, its lens glowing pink or cyan. */
        SPOTLIGHT(true, SoundType.METAL),
        /** The lab's: a ladder of cable tray, bundled coloured cables along its rungs. */
        CABLE_TRAY(false, SoundType.METAL);

        private final boolean twoLooks;
        /** What it sounds of, broken or bumped: cloth, chain, copper, glass, leaves or steel. */
        private final SoundType sound;

        Kind(boolean twoLooks, SoundType sound) {
            this.twoLooks = twoLooks;
            this.sound = sound;
        }

        /**
         * Whether it has a look for the first of a pair and another for the second (see {@link Part}); where it has
         * one only, it is always the first, so those broken stack.
         */
        public boolean twoLooks() {
            return twoLooks;
        }

        /**
         * Whether its pair is one thing two blocks long, a loop of string or a pot rack, rather than two looks of
         * something a block long: mirrored, such a pair swaps its two, so the loop still falls from its ties.
         */
        public boolean twoBlocksLong() {
            return this == POT_RACK || this == FAIRY_LIGHTS || this == BUNTING;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Which of a pair of blocks along its girder it is, reading toward the east or the south, so a decoration two
     * blocks long is drawn as one: a loop of fairy lights or bunting falls from where it is tied across the first and
     * rises to its next tie across the second, and a pot rack starts in the first and ends in the second. On a wall or
     * the floor a pair reads from left to right as it is faced. What hangs one to a block has two looks to alternate,
     * one for each: the entry's two patches, and the club's spotlights, pink and cyan, under a ceiling the pink angled
     * toward the west or north and the cyan toward the east or south.
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
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(KIND, Kind.CHAIN_HOIST).setValue(PART, Part.FIRST)
            .setValue(MOUNT, Mount.CEILING).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, KIND, PART, MOUNT, FACING);
    }

    /** A decoration of a kind hung under a ceiling, running along an axis, as which of a pair. */
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

    /** Each kind sounds as what it is made of: banners and bunting cloth, a hoist chain, pans copper, lights glass. */
    @Override
    public SoundType getSoundType(BlockState state, LevelReader level, BlockPos pos, @Nullable Entity entity) {
        return state.getValue(KIND).sound;
    }

    /**
     * Put up against what holds it where the player puts it (see {@link Mounting}): under a ceiling it hangs along the
     * girder over it, or else the way whoever hangs it looks; on a wall it runs along the wall, facing out from it; on
     * the floor it stands across the way whoever stands it there looks, facing them. Nothing goes up where nothing
     * holds it.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction toward = Mounting.support(context, MOUNT.getPossibleValues());
        if (toward == null) {
            return null;
        }
        return switch (Mount.against(toward)) {
            case CEILING -> defaultBlockState().setValue(AXIS, hangingAlong(context));
            case WALL -> facingToward(defaultBlockState().setValue(MOUNT, Mount.WALL), toward.getOpposite());
            case FLOOR -> facingToward(defaultBlockState().setValue(MOUNT, Mount.FLOOR), context.getHorizontalDirection().getOpposite());
        };
    }

    /** The way a decoration hung by hand runs: along the girder over it, or else the way whoever hangs it looks. */
    private static Direction.Axis hangingAlong(BlockPlaceContext context) {
        BlockState above = context.getLevel().getBlockState(context.getClickedPos().above());
        boolean underGirder = above.getBlock() instanceof SteelGirderBlock && above.getValue(RotatedPillarBlock.AXIS).isHorizontal();
        return underGirder ? above.getValue(RotatedPillarBlock.AXIS) : context.getHorizontalDirection().getAxis();
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(MOUNT)) {
            case CEILING -> state.getValue(AXIS) == Direction.Axis.X ? ALONG_X : ALONG_Z;
            case WALL -> ON_WALL.get(state.getValue(KIND)).get(state.getValue(FACING));
            case FLOOR -> ON_FLOOR.get(state.getValue(KIND)).get(state.getValue(FACING));
        };
    }

    /** Each kind's shape, drawn facing north, turned to face each way, as its model is. */
    private static Map<Kind, Map<Direction, VoxelShape>> byFacing(Function<Kind, VoxelShape> facingNorth) {
        Map<Kind, Map<Direction, VoxelShape>> shapes = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) {
            shapes.put(kind, TurnedShapes.of(facingNorth.apply(kind)));
        }
        return shapes;
    }

    /**
     * What can be picked out of a kind fixed to a wall, facing north, its wall to the south: the banner on its rod, the
     * jib arm and its hoist, the rail and its pans, the loop of lights or bunting, the basket on its bracket, the
     * spotlight on its arm, or the tray on its bracket.
     */
    private static VoxelShape onWall(Kind kind) {
        return switch (kind) {
            case MISSION_PATCHES -> Block.box(1, 1, 13.75, 15, 16, 16);
            case CHAIN_HOIST -> Block.box(4, 0, 1.5, 12, 16, 16);
            case POT_RACK -> Block.box(0, 0, 9.5, 16, 13, 16);
            case FAIRY_LIGHTS -> Block.box(0, 8.5, 13.5, 16, 16, 16);
            case HANGING_BASKET -> Block.box(3.5, 0, 3, 12.5, 16, 16);
            case BUNTING -> Block.box(0, 5, 13.5, 16, 16, 16);
            case SPOTLIGHT -> Block.box(4.5, 3, 3, 11.5, 16, 16);
            case CABLE_TRAY -> Block.box(0, 2.5, 6.5, 16, 12, 16);
        };
    }

    /**
     * What can be picked out of a kind standing on the floor, facing north: the banner stand, the gantry, the standing
     * rack, the jar of lights, the plant stand, the posts and their bunting, the tripod or the cable cover.
     */
    private static VoxelShape onFloor(Kind kind) {
        return switch (kind) {
            case MISSION_PATCHES -> Block.box(1, 0, 6.25, 15, 16, 12.25);
            case CHAIN_HOIST -> Block.box(0.75, 0, 0.5, 15.25, 16, 15.5);
            case POT_RACK -> Block.box(0, 0, 3, 16, 16, 13);
            case FAIRY_LIGHTS -> Block.box(4.5, 0, 4.5, 11.5, 12.25, 11.5);
            case HANGING_BASKET -> Block.box(4, 0, 4, 12, 16, 12);
            case BUNTING -> Block.box(0, 0, 6, 16, 16, 10);
            case SPOTLIGHT -> Block.box(2.75, 0, 4.5, 13.25, 16, 13.25);
            case CABLE_TRAY -> Block.box(0, 0, 1, 16, 2.5, 15);
        };
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (state.getValue(MOUNT) == Mount.CEILING) {
            return runningToward(state, rotation.rotate(eastOrSouth(state)));
        }
        return facingToward(state, rotation.rotate(state.getValue(FACING)));
    }

    /**
     * Mirrored, a decoration under a ceiling runs as it would turned (see {@link #runningToward}); one on a wall or the
     * floor faces the mirrored way, and of a pair two blocks long it is the other, as the mirror reads the pair from
     * its other end.
     */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (state.getValue(MOUNT) == Mount.CEILING) {
            return runningToward(state, mirror.mirror(eastOrSouth(state)));
        }
        BlockState mirrored = facingToward(state, mirror.mirror(state.getValue(FACING)));
        boolean swapped = mirror != Mirror.NONE && state.getValue(KIND).twoBlocksLong();
        return swapped ? mirrored.setValue(PART, state.getValue(PART).other()) : mirrored;
    }

    /** The way a decoration's pair is read toward under a ceiling: east along x, south along z. */
    private static Direction eastOrSouth(BlockState state) {
        return Direction.fromAxisAndDirection(state.getValue(AXIS), Direction.AxisDirection.POSITIVE);
    }

    /**
     * A decoration under a ceiling turned so that the way its pair was read toward is now the way given: along that
     * way's axis, and, of a kind with two looks, the other of its pair where that way is west or north, so a loop still
     * falls from its tie once it is turned.
     */
    private static BlockState runningToward(BlockState state, Direction toward) {
        BlockState turned = state.setValue(AXIS, toward.getAxis());
        boolean reversed = toward.getAxisDirection() == Direction.AxisDirection.NEGATIVE && state.getValue(KIND).twoLooks();
        return reversed ? turned.setValue(PART, state.getValue(PART).other()) : turned;
    }

    /** A decoration on a wall or the floor facing the way given, and so running across that way. */
    private static BlockState facingToward(BlockState state, Direction facing) {
        return state.setValue(FACING, facing).setValue(AXIS, facing.getClockWise().getAxis());
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
