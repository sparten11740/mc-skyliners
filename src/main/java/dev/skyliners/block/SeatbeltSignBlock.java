package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A "fasten seatbelts" sign in the cabin, lit by any redstone reaching it or by the seatbelt switch on the flight deck,
 * which works the airliner's signs itself (see {@link Relays.Driven}): POWERED while its switch has it on. Lighting up,
 * it dings through the cabin.
 *
 * <p>A PLAQUE is a thin sign hung on the cabin wall, its face looking into the cabin (FACING), as the blueprints hang
 * them: the wall behind it stays skin cut to the hull's curve. One set into the hull itself, as the blueprints set them
 * before, stood out of the curve as a whole block, its relay beside it another, with a pit round them; such signs in
 * airliners built then are lit through their relays as they always were. PLATED, a sign shows itself only on its
 * FACING side and is plated like the skin everywhere else; a sign placed by hand faces whoever placed it, and one built
 * before the plating has the sign all round, as it always had.
 */
public class SeatbeltSignBlock extends Block implements Relays.Driven {
    private static final MapCodec<SeatbeltSignBlock> CODEC = simpleCodec(SeatbeltSignBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty PLATED = BooleanProperty.create("plated");
    public static final BooleanProperty PLAQUE = BooleanProperty.create("plaque");
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** A plaque's box for each way its face looks, against the wall behind it, two sixteenths deep. */
    private static final Map<Direction, VoxelShape> PLAQUE_SHAPES = TurnedShapes.of(box(1, 1, 14, 15, 15, 16));

    public SeatbeltSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(FACING, Direction.NORTH).setValue(PLATED, false)
            .setValue(PLAQUE, false).setValue(POWERED, false));
    }

    /** A sign set into a wall, plated outside, its face looking this way into the cabin. */
    public static BlockState facingInto(BlockState sign, Direction cabin) {
        return sign.setValue(PLATED, true).setValue(FACING, cabin);
    }

    /** A thin sign hung on the cabin wall, its face looking this way into the cabin. */
    public static BlockState plaque(BlockState sign, Direction cabin) {
        return sign.setValue(PLAQUE, true).setValue(FACING, cabin);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, FACING, PLATED, PLAQUE, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return facingInto(defaultBlockState(), context.getHorizontalDirection().getOpposite())
            .setValue(LIT, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, BlockPos from, boolean moving) {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        set(level, pos, state, state.setValue(LIT, state.getValue(POWERED) || level.hasNeighborSignal(pos)));
    }

    /** Its switch turns it on or off; redstone reaching it still lights it either way. */
    @Override
    public void drive(Level level, BlockPos pos, BlockState state, int signal) {
        if (level instanceof ServerLevel) {
            set(level, pos, state, state.setValue(POWERED, signal > 0).setValue(LIT, signal > 0 || level.hasNeighborSignal(pos)));
        }
    }

    /** Gives the sign its new state in one update, if it changed, dinging as it lights. */
    private static void set(Level level, BlockPos pos, BlockState before, BlockState after) {
        if (after == before) {
            return;
        }
        level.setBlock(pos, after, Block.UPDATE_CLIENTS);
        if (after.getValue(LIT) && !before.getValue(LIT)) {
            level.playSound(null, pos, SkylinersRegistry.SEATBELT_DING.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /** A plaque is a thin sign on the wall behind it; otherwise a whole block. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(PLAQUE) ? PLAQUE_SHAPES.get(state.getValue(FACING)) : Shapes.block();
    }

    /**
     * Set into the hull, it keeps the daylight out of the cabin like the skin around it; it is glass only to look at.
     * A plaque on the wall lets the light by.
     */
    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(PLAQUE) ? 0 : level.getMaxLightLevel();
    }
}
