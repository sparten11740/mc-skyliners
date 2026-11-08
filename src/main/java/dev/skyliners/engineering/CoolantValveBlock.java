package dev.skyliners.engineering;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The freighter's coolant valve in engineering (see {@link CoolantValveBlockEntity}): a big red valve wheel on a pipe
 * out of the wall, a pressure gauge over it and a vent pipe beside. Turn the wheel, using it, and it squeaks round a
 * quarter turn and the gauge's needle climbs a step toward red; at full pressure the vent blasts a great cloud of steam
 * with a whoosh and a hiss, and the needle sinks back to green as it lets the pressure off, the wheel turning back with
 * it. Used while it vents, it only clicks. The client's {@code CoolantValveRenderer} draws its wheel and gauge. FACING
 * is the way it faces out from the wall.
 */
public class CoolantValveBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<CoolantValveBlock> CODEC = simpleCodec(CoolantValveBlock::new);
    /** How many quarter turns of the wheel bring it to full pressure. */
    public static final int FULL = 4;
    /** How far its pressure stands, from none to full, and whether it is letting the pressure off. */
    public static final IntegerProperty PRESSURE = IntegerProperty.create("pressure", 0, FULL);
    public static final BooleanProperty VENTING = BooleanProperty.create("venting");
    /** How long each step of the pressure takes to let off, venting, in ticks. */
    private static final int VENT_STEP_TICKS = 15;
    /** How loud its squeak, its blast, its hiss and its busy click are. */
    private static final float SQUEAK_VOLUME = 0.8F;
    private static final float BLAST_VOLUME = 1.0F;
    private static final float HISS_VOLUME = 0.6F;
    private static final float BUSY_VOLUME = 0.4F;
    /**
     * Where its steam comes out: the vent's mouth, so far out from the wall, along the way it faces, so far toward the
     * viewer's right and so high, as shares of its block; and how fast the steam blows up and out, in blocks a tick.
     */
    private static final double VENT_OUT = 0.25;
    private static final double VENT_RIGHT = 0.25;
    private static final double VENT_UP = 1.0;
    private static final double STEAM_UP = 0.25;
    private static final double STEAM_OUT = 0.06;
    /** Its plate, pipe and wheel against the wall, to bump into, by the way it faces. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.NORTH, Block.box(2, 0, 7, 14, 16, 16));
        SHAPES.put(Direction.SOUTH, Block.box(2, 0, 0, 14, 16, 9));
        SHAPES.put(Direction.WEST, Block.box(7, 0, 2, 16, 16, 14));
        SHAPES.put(Direction.EAST, Block.box(0, 0, 2, 9, 16, 14));
    }

    public CoolantValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PRESSURE, 0).setValue(VENTING, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PRESSURE, VENTING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Turned a quarter, a step more pressure; at full, it vents. Venting, only a click. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || player.isSpectator()) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (state.getValue(VENTING)) {
            busy(server, pos);
            return InteractionResult.SUCCESS;
        }
        int pressure = state.getValue(PRESSURE) + 1;
        // The rocket's greenhouse irrigation valve's squeak, a little higher each turn as the pressure climbs.
        server.playSound(null, pos, SkylinersRegistry.IRRIGATION_VALVE_SOUND.get(), SoundSource.BLOCKS, SQUEAK_VOLUME,
            0.8F + 0.1F * pressure);
        boolean full = pressure >= FULL;
        server.setBlock(pos, state.setValue(PRESSURE, pressure).setValue(VENTING, full), Block.UPDATE_ALL);
        if (full) {
            server.playSound(null, pos, SkylinersRegistry.RAMP_STEAM.get(), SoundSource.BLOCKS, BLAST_VOLUME, 1.2F);
            server.playSound(null, pos, SkylinersRegistry.FOG_HISS.get(), SoundSource.BLOCKS, HISS_VOLUME, 1.3F);
            server.scheduleTick(pos, this, VENT_STEP_TICKS);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Used while it vents: a click, and its next step at once where that was lost, as when she was assembled meanwhile.
     */
    private void busy(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, BUSY_VOLUME, 1.0F);
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    /** Venting, a step of pressure let off; all let off, it stops venting. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(VENTING)) {
            return;
        }
        int pressure = state.getValue(PRESSURE) - 1;
        level.setBlock(pos, state.setValue(PRESSURE, Math.max(0, pressure)).setValue(VENTING, pressure > 0), Block.UPDATE_ALL);
        if (pressure > 0) {
            level.scheduleTick(pos, this, VENT_STEP_TICKS);
        }
    }

    /**
     * On the client, while it vents: steam billowing up out of its vent, carried along with her as she flies.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(VENTING)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        Direction right = facing.getCounterClockWise();
        Vec3 mouth = Vec3.atBottomCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(VENT_OUT - 0.5))
            .add(Vec3.atLowerCornerOf(right.getNormal()).scale(VENT_RIGHT)).add(0, VENT_UP, 0);
        // Given off where its block is, in Sable's plot aboard a ship, Sable carries the steam along with her.
        for (int puff = 0; puff < state.getValue(PRESSURE) + 1; puff++) {
            level.addParticle(ParticleTypes.CLOUD, mouth.x, mouth.y, mouth.z, facing.getStepX() * STEAM_OUT + random.nextGaussian() * 0.02,
                STEAM_UP * random.nextDouble(), facing.getStepZ() * STEAM_OUT + random.nextGaussian() * 0.02);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoolantValveBlockEntity(pos, state);
    }
}
