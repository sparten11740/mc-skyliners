package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
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

/**
 * A "fasten seatbelts" sign set into the cabin wall, lit by any redstone reaching it: the seatbelt switch on the flight
 * deck works the airliner's signs through a relay beside each. Lighting up, it dings through the cabin.
 *
 * <p>PLATED, it shows the sign only on its FACING side, the one that looks into the cabin, and is plated like the
 * skin round it everywhere else, so it cannot be seen from outside the airliner. The blueprints set it so, and a sign
 * placed by hand faces whoever placed it; one built before the plating has the sign all round, as it always had.
 */
public class SeatbeltSignBlock extends Block {
    private static final MapCodec<SeatbeltSignBlock> CODEC = simpleCodec(SeatbeltSignBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty PLATED = BooleanProperty.create("plated");

    public SeatbeltSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(FACING, Direction.NORTH).setValue(PLATED, false));
    }

    /** A sign set into a wall, plated outside, its face looking this way into the cabin. */
    public static BlockState facingInto(BlockState sign, Direction cabin) {
        return sign.setValue(PLATED, true).setValue(FACING, cabin);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, FACING, PLATED);
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
        boolean lit = level.hasNeighborSignal(pos);
        if (lit != state.getValue(LIT)) {
            level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_CLIENTS);
            if (lit) {
                level.playSound(null, pos, SkylinersRegistry.SEATBELT_DING.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
            }
        }
    }

    /** Set into the hull, it keeps the daylight out of the cabin like the skin around it; it is glass only to look at. */
    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return level.getMaxLightLevel();
    }
}
