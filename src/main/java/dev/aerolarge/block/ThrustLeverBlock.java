package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One engine's thrust lever on the flight deck's throttle quadrant. The four move together, the way a pilot's hand
 * moves them, so the engines never pull the airliner off to one side: using any of them pushes them all a notch
 * forward (more thrust), using it while sneaking pulls them all a notch back. Each engine's thrusters follow its
 * lever through the relays in the nacelle. FACING is the way the nose points, and the lever pushes toward it.
 */
public class ThrustLeverBlock extends BaseEntityBlock {
    private static final MapCodec<ThrustLeverBlock> CODEC = simpleCodec(ThrustLeverBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty THRUST = IntegerProperty.create("thrust", 0, 4);
    /** The redstone signal each notch sends the thrusters: idle, then quarters up to full. */
    private static final int[] POWER = {0, 4, 8, 12, 15};
    /**
     * Where the lever's grip is, as its models draw it: this many pixels out from the lever's pivot in the middle of
     * the slab's top, swung back this many degrees at idle and forward a notch of so many degrees at a time.
     */
    private static final double GRIP_REACH = 7.25;
    private static final double IDLE_ANGLE = 45;
    private static final double NOTCH_ANGLE = 22.5;
    /**
     * A module of the throttle quadrant, by thrust: its slab and the lever's grip where the lever stands. The slim
     * handle between them is left out, so that the quadrant stands in the way of nothing beyond it on the console.
     */
    private static final List<Map<Direction, VoxelShape>> SHAPES = THRUST.getPossibleValues().stream().map(thrust -> TurnedShapes.of(gripShape(thrust))).toList();
    /** How far along the quadrant, either way, the other levers moved with this one are looked for. */
    private static final int QUADRANT = 4;

    public ThrustLeverBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(THRUST, 0));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, THRUST);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** The slab and the grip, drawn facing north, at this thrust. */
    private static VoxelShape gripShape(int thrust) {
        double angle = Math.toRadians(IDLE_ANGLE - NOTCH_ANGLE * thrust);
        double y = ConsoleModule.HEIGHT + GRIP_REACH * Math.cos(angle);
        double z = 8 + GRIP_REACH * Math.sin(angle);
        return Shapes.or(ConsoleModule.SLAB, Block.box(6, y - 1.5, z - 1.5, 10, y + 1.5, z + 1.5));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(THRUST)).get(state.getValue(FACING));
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
        int thrust = Math.clamp(state.getValue(THRUST) + (player.isShiftKeyDown() ? -1 : 1), 0, 4);
        if (thrust == state.getValue(THRUST)) {
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide) {
            Direction along = state.getValue(FACING).getClockWise();
            for (int k = -QUADRANT; k <= QUADRANT; k++) {
                BlockPos at = pos.relative(along, k);
                BlockState other = level.getBlockState(at);
                if (other.is(this) && other.getValue(FACING) == state.getValue(FACING)) {
                    level.setBlock(at, other.setValue(THRUST, thrust), Block.UPDATE_ALL);
                    if (level.getBlockEntity(at) instanceof ThrustLeverBlockEntity lever) {
                        lever.drive(POWER[thrust]);
                    }
                }
            }
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5F, 0.6F + thrust * 0.1F);
            player.displayClientMessage(Component.translatable("message.aerolarge.thrust", thrust * 25), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The signal a lever sends its engine's thrusters in this state. */
    static int power(BlockState state) {
        return POWER[state.getValue(THRUST)];
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ThrustLeverBlockEntity(pos, state);
    }
}
