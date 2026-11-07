package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The freighter's deck robot: a little white and teal robot on three wheels with a round sensor head and a cyan visor,
 * our own design, that stands about her rooms. It chirps hello as someone comes up to it, its head turning to
 * follow them; touched, it trills back; and now and then, with someone near, it beeps to itself. Its body is the
 * block's own model; its head is a state of {@link #PART} the client's {@code DeckRobotRenderer} draws turning.
 */
public class DeckRobotBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<DeckRobotBlock> CODEC = simpleCodec(DeckRobotBlock::new);
    /** Which of its parts a state draws: its body, the block as it stands, or its head, for the renderer. */
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** Its body and head together, as it faces north, to bump into and pick: a little under a block high. */
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 9, 13), Block.box(4, 9, 4, 12, 14, 12));
    private static final Map<Direction, VoxelShape> SHAPES = TurnedShapes.of(SHAPE);

    /** A part of the deck robot, as a state of {@link #PART}. */
    public enum Part implements StringRepresentable {
        BODY, HEAD;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public DeckRobotBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.BODY));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Touched, it trills back at whoever touched it. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof DeckRobotBlockEntity robot) {
            robot.answer(server);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DeckRobotBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != SkylinersRegistry.DECK_ROBOT_ENTITY.get()) {
            return null;
        }
        return level.isClientSide ? (tickLevel, pos, tickState, robot) -> ((DeckRobotBlockEntity) robot).clientTick()
            : (tickLevel, pos, tickState, robot) -> ((DeckRobotBlockEntity) robot).serverTick((ServerLevel) tickLevel);
    }
}
