package dev.skyliners.zerog;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.Mount;
import dev.skyliners.block.Mounting;
import dev.skyliners.block.TurnedShapes;
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
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The gravity switch (see {@link GravitySwitchBlockEntity}): a chunky plate in hazard stripes with a big round button
 * on it, painted with a little figure floating up off a floor among sparkles, and a lamp over it: green while the
 * room's gravity is on, glowing cyan while it is off. Pressed, or given a redstone pulse, it turns gravity off in the
 * room it stands in, everyone and everything there floating, and pressed again turns it back on. The freighter has one
 * by the doorway of her club; put up by hand it works wherever it stands, on the room round it. It goes on a wall,
 * facing out from it, or stands on its post on a floor, facing whoever put it there (MOUNT), against the block the
 * player clicks, as a button does (see {@link Mounting}). FACING is the way its button looks; WEIGHTLESS whether the
 * room's gravity is off; POWERED whether it has a redstone signal.
 */
public class GravitySwitchBlock extends BaseEntityBlock {
    private static final MapCodec<GravitySwitchBlock> CODEC = simpleCodec(GravitySwitchBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Mount> MOUNT = Mount.property(Mount.WALL, Mount.FLOOR);
    public static final BooleanProperty WEIGHTLESS = BooleanProperty.create("weightless");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** How brightly its lamp lights the room round it while gravity is on, and while it is off, in light levels. */
    public static final int GLOW = 4;
    public static final int GLOW_WEIGHTLESS = 9;
    /** A post with its head, its button and its lamp, standing on a floor, as drawn. */
    private static final VoxelShape STANDING = Shapes.or(Block.box(4, 0, 4, 12, 1, 12), Block.box(6.5, 1, 6.5, 9.5, 10, 9.5),
        Block.box(3, 10, 3, 13, 14.5, 13), Block.box(6.5, 14.5, 12, 9.5, 15, 13));
    /** Its plate, its button and its lamp on a wall, facing north, its wall to the south, as drawn. */
    private static final Map<Direction, VoxelShape> ON_A_WALL = TurnedShapes.of(Shapes.or(Block.box(2, 1, 15, 14, 15, 16),
        Block.box(4, 2.5, 13, 12, 10.5, 15), Block.box(6.5, 11.5, 14, 9.5, 13.5, 15)));

    public GravitySwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MOUNT, Mount.WALL).setValue(WEIGHTLESS, false)
            .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MOUNT, WEIGHTLESS, POWERED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Turned with its ship as she is placed or set down turned, its button still facing out from its wall. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(MOUNT) == Mount.WALL ? ON_A_WALL.get(state.getValue(FACING)) : STANDING;
    }

    /**
     * Put up against what holds it where the player puts it (see {@link Mounting}), on a wall facing out from it and on
     * a floor facing whoever puts it there, nowhere that nothing holds it; its room's gravity on.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction toward = Mounting.support(context, MOUNT.getPossibleValues());
        if (toward == null) {
            return null;
        }
        Mount mount = Mount.against(toward);
        Direction facing = mount == Mount.WALL ? toward.getOpposite() : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(MOUNT, mount).setValue(FACING, facing)
            .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isSpectator() && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof GravitySwitchBlockEntity gravitySwitch) {
            gravitySwitch.press(server);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A redstone pulse presses it as a hand does, once as its signal comes on. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos from, boolean moved) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        boolean powered = server.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) {
            return;
        }
        server.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        if (powered && server.getBlockEntity(pos) instanceof GravitySwitchBlockEntity gravitySwitch) {
            gravitySwitch.press(server);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GravitySwitchBlockEntity(pos, state);
    }

    /** It ticks only while its room's gravity is off. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!state.getValue(WEIGHTLESS)) {
            return null;
        }
        return createTickerHelper(type, SkylinersRegistry.GRAVITY_SWITCH_ENTITY.get(),
            level.isClientSide ? GravitySwitchBlockEntity::clientTick : GravitySwitchBlockEntity::serverTick);
    }
}
