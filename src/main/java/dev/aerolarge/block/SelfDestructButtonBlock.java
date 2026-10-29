package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The self-destruct button, on the flight deck's back wall: a big red button on a yellow-and-black striped plate under
 * a striped flip cover, so that it reads as the button not to press without a word. It takes three presses on purpose
 * to set off (see {@link SelfDestructButtonBlockEntity}): the first flips the cover up (OPEN), the second arms it, the
 * button lit and flashing red (ARMED) while the alarm wails through the airliner, and a third while it is armed blows
 * the airliner up. Sneak and use it with an empty hand to snap the cover shut and disarm it. Put up by hand, it takes
 * the shape that fits where it goes (MOUNT): against the side of a block, the plate on the wall; on the floor, the
 * plate tilted toward whoever put it there on top of a pedestal console, a column of the flight deck's console casing
 * on a hazard-striped plinth. FACING is the way whoever looks at it faces, toward the wall or across the pedestal.
 */
public class SelfDestructButtonBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<SelfDestructButtonBlock> CODEC = simpleCodec(SelfDestructButtonBlock::new);
    public static final BooleanProperty OPEN = BooleanProperty.create("open");
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");
    /** How the button is put up; one saved before it had a mount was on a wall, as every button then was. */
    public static final EnumProperty<Mount> MOUNT = Mount.property(Mount.WALL, Mount.FLOOR);
    /** How far the plate, the button and the cover over it stand out from the wall, in pixels. */
    private static final int DEPTH = 8;
    /** How far in from the plate's edges to the edges of the block, in pixels: the plate is twelve pixels square. */
    private static final int MARGIN = 2;
    /** The plate on the wall, by the way its viewer faces: against the side of the block they look at. */
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
        Direction.NORTH, Block.box(MARGIN, MARGIN, 0, 16 - MARGIN, 16 - MARGIN, DEPTH),
        Direction.SOUTH, Block.box(MARGIN, MARGIN, 16 - DEPTH, 16 - MARGIN, 16 - MARGIN, 16),
        Direction.WEST, Block.box(0, MARGIN, MARGIN, DEPTH, 16 - MARGIN, 16 - MARGIN),
        Direction.EAST, Block.box(16 - DEPTH, MARGIN, MARGIN, 16, 16 - MARGIN, 16 - MARGIN)
    );
    /**
     * The pedestal, drawn facing north as its viewer does: its plinth and column, and over them its tilted head in
     * steps, the plate sloping down toward the viewer with the lid over its button.
     */
    private static final Map<Direction, VoxelShape> PEDESTAL = TurnedShapes.of(Shapes.or(
        Block.box(2.5, 0, 3, 13.5, 1.5, 13), Block.box(4, 1.5, 5, 12, 7.75, 11),
        Block.box(1.5, 7.5, 1, 14.5, 13.25, 5), Block.box(1.5, 5.5, 5, 14.5, 12.5, 10),
        Block.box(1.5, 4.25, 10, 14.5, 10.5, 14), Block.box(4.5, 11, 5, 11.5, 16, 9.5), Block.box(4.5, 9.5, 9.5, 11.5, 15, 13.5)
    ));
    /**
     * Where the armed button's flare shows, drawn facing north, in pixels across, up and along: a pixel off the middle
     * of the button's face, on the wall or tilted on the pedestal, where tools/generate_resources.mjs tilts it.
     */
    private static final Vec3 WALL_FLARE = new Vec3(8, 8, 5);
    private static final Vec3 PEDESTAL_FLARE = new Vec3(8, 14.1, 9.9);

    public SelfDestructButtonBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false).setValue(ARMED, false)
            .setValue(MOUNT, Mount.WALL));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN, ARMED, MOUNT);
    }

    /**
     * Against the side of a block or on the floor, whichever holds it (see {@link Mounting}), facing whoever puts it
     * there, its cover shut; not under a ceiling, where nobody could reach it.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction toward = Mounting.support(context, MOUNT.getPossibleValues());
        if (toward == null) {
            return null;
        }
        Mount mount = Mount.against(toward);
        Direction facing = mount == Mount.WALL ? toward : context.getHorizontalDirection();
        return defaultBlockState().setValue(MOUNT, mount).setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(MOUNT) == Mount.FLOOR ? PEDESTAL : SHAPES).get(state.getValue(FACING));
    }

    /** Where in its block the armed button's flare shows, over the middle of the button, on a wall or the pedestal. */
    public static Vec3 flare(BlockState state) {
        Vec3 at = state.getValue(MOUNT) == Mount.FLOOR ? PEDESTAL_FLARE : WALL_FLARE;
        Vec3 across = TurnedShapes.point(state.getValue(FACING), at.x, at.z);
        return new Vec3(across.x, at.y / 16, across.z);
    }

    /** Pressed, or sneaked at, on the server (see {@link SelfDestructButtonBlockEntity#press}). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof SelfDestructButtonBlockEntity button) {
            button.press(serverLevel, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Taken away while armed, the alarm stops for everyone aboard. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SelfDestructButtonBlockEntity button) {
            button.taken();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SelfDestructButtonBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != AerolargeRegistry.SELF_DESTRUCT_BUTTON_ENTITY.get() ? null : SelfDestructButtonBlock::tick;
    }

    private static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T blockEntity) {
        if (blockEntity instanceof SelfDestructButtonBlockEntity button) {
            button.tick();
        }
    }
}
