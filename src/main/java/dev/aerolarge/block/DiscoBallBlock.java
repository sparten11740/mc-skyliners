package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A disco ball for the kids' den: a ball faceted all over with little mirror tiles that turns slowly while it is on
 * (LIT), its tiles glinting, and casts spots of coloured light that sweep round the room as it turns. Where it is put
 * up (MOUNT) it hangs on a short rod from the ceiling (or from a light cable down from it), as the blueprints hang it;
 * turns on a small motor stand on the floor; or hangs from the motor at the end of an arm out from a wall plate, facing
 * out from the wall (FACING, which only a ball on a wall shows). The ball, its turning and the spots are drawn on the
 * client (see {@link DiscoBallBlockEntity}), round its middle for the way it is put up (see {@link #centre}); the
 * block itself is only what holds it, and while on it lights the room round it as a lamp does, for wherever the spots
 * cannot be drawn.
 *
 * <p>Used, with or without anything in hand, it switches off, or on, with a chime falling or rising: a child who
 * cannot read sees it slow to a stop and go dark, or spin up and light the room. Nothing bumps into it, however it is
 * put up: hung overhead it is out of the way of a child leaping out of the ball pit under it.
 */
public class DiscoBallBlock extends Block implements EntityBlock {
    private static final MapCodec<DiscoBallBlock> CODEC = simpleCodec(DiscoBallBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** How it is put up; a disco ball saved before it had a mount was hung, and loads hanging. */
    public static final EnumProperty<Mount> MOUNT = Mount.property(Mount.CEILING, Mount.WALL, Mount.FLOOR);
    /** The way a disco ball on a wall faces, out from its wall. */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The ball's radius, in blocks. */
    public static final double RADIUS = 6 / 16.0;
    /**
     * How high the ball's middle is in its block, in pixels up from its bottom: hung, from the ceiling or a wall's arm,
     * a pixel clear of the bottom; on the floor, over its motor stand.
     */
    private static final double HUNG_MIDDLE = 7;
    private static final double STANDING_MIDDLE = 9.5;
    /** How far out from its wall the middle of a ball on a wall is, in pixels: its mirrors three pixels clear of it. */
    private static final double OUT_FROM_WALL = 9;
    /** Hung: the ball, and the rod it hangs from up to the top of the block. */
    private static final VoxelShape HANGING = Shapes.or(Block.box(2, 1, 2, 14, 13, 14), Block.box(7.5, 13, 7.5, 8.5, 16, 8.5));
    /** On the floor: the ball, and its motor stand under it. */
    private static final VoxelShape STANDING = Shapes.or(Block.box(2, 3.5, 2, 14, 15.5, 14), Block.box(6, 1, 6, 10, 4, 10),
        Block.box(4, 0, 6, 12, 1, 10), Block.box(5, 0, 5, 11, 1, 11), Block.box(6, 0, 4, 10, 1, 12));
    /** On a wall, by the way it faces: the ball, the motor over it, the arm out to it and the wall plate. */
    private static final Map<Direction, VoxelShape> ON_WALL = TurnedShapes.of(Shapes.or(Block.box(2, 1, 1, 14, 13, 13),
        Block.box(6, 13, 5, 10, 15.5, 9), Block.box(7, 13.5, 9, 9, 15, 15), Block.box(5.5, 10.5, 15, 10.5, 15.5, 16)));
    /** The chime as it switches: how loud, and its pitch as it goes on and as it goes off. */
    private static final float CHIME_VOLUME = 1.0F;
    private static final float CHIME_ON = 1.5F;
    private static final float CHIME_OFF = 0.6F;

    public DiscoBallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, true).setValue(MOUNT, Mount.CEILING).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, MOUNT, FACING);
    }

    /**
     * Put up against what holds it (see {@link Mounting}): hung under a ceiling or a light cable, stood on the floor or
     * fixed to a wall, facing out from it; nowhere that nothing holds it.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction toward = Mounting.support(context, MOUNT.getPossibleValues());
        if (toward == null) {
            return null;
        }
        BlockState state = defaultBlockState().setValue(MOUNT, Mount.against(toward));
        return toward.getAxis().isHorizontal() ? state.setValue(FACING, toward.getOpposite()) : state;
    }

    /** Where the ball's middle is in its block, in blocks from its lower corner, for the way it is put up. */
    public static Vec3 centre(BlockState state) {
        return switch (state.getValue(MOUNT)) {
            case CEILING -> new Vec3(0.5, HUNG_MIDDLE / 16, 0.5);
            case FLOOR -> new Vec3(0.5, STANDING_MIDDLE / 16, 0.5);
            case WALL -> TurnedShapes.point(state.getValue(FACING), 8, 16 - OUT_FROM_WALL).add(0, HUNG_MIDDLE / 16, 0);
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(MOUNT)) {
            case CEILING -> HANGING;
            case FLOOR -> STANDING;
            case WALL -> ON_WALL.get(state.getValue(FACING));
        };
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockState switched = state.cycle(LIT);
            level.setBlock(pos, switched, Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, CHIME_VOLUME, switched.getValue(LIT) ? CHIME_ON : CHIME_OFF);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DiscoBallBlockEntity(pos, state);
    }

    /** It only turns on the client, where it is drawn. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide && type == AerolargeRegistry.DISCO_BALL_ENTITY.get()
            ? (tickLevel, pos, tickState, ball) -> ((DiscoBallBlockEntity) ball).clientTick()
            : null;
    }
}
