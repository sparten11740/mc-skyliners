package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The club's fog machine: a black flight case on the floor by the DJ deck, a chrome nozzle at its front and a light on
 * its top that says how it stands (FOG). Ready, the light is green. Pressed, it hisses and pumps a stream of thick fog
 * out of its nozzle for five seconds, its nozzle glowing and its light white, the fog rolling out over the dance floor
 * and hanging in the disco balls' light (drawn on the client, see {@link FogMachineBlockEntity}); then it heats up
 * again, its light amber, and a beep says when it is ready. Pressed before then, it only clicks. FACING is the way its
 * nozzle points.
 */
public class FogMachineBlock extends BaseEntityBlock {
    private static final MapCodec<FogMachineBlock> CODEC = simpleCodec(FogMachineBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Fog> FOG = EnumProperty.create("fog", Fog.class);
    /** How long it pumps fog for and how long it takes to heat up again after, in ticks. */
    private static final int FOG_TICKS = 100;
    private static final int HEAT_TICKS = 160;
    /** How loud its click is when pressed while busy, and its beep as it is ready again, and the beep's pitch. */
    private static final float BUSY_VOLUME = 0.4F;
    private static final float READY_VOLUME = 0.5F;
    private static final float READY_PITCH = 1.6F;
    /** How bright it glows while it pumps fog, in light levels: its nozzle lit; otherwise only its little light. */
    private static final int FOGGING_GLOW = 8;
    private static final int IDLE_GLOW = 2;
    /** Facing north: the case on the floor, its nozzle at its front. */
    private static final Map<Direction, VoxelShape> SHAPES = TurnedShapes.of(Block.box(2, 0, 1, 14, 8.5, 15));

    /** How the fog machine stands. */
    public enum Fog implements StringRepresentable {
        READY,
        FOGGING,
        HEATING;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public FogMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FOG, Fog.READY));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FOG);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Turned with the rocket as she is built facing another way, its nozzle and its fog with it. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** How bright it glows: its nozzle lit while it pumps fog, only its little light otherwise. */
    public static int glow(BlockState state) {
        return state.getValue(FOG) == Fog.FOGGING ? FOGGING_GLOW : IDLE_GLOW;
    }

    /**
     * Pumps fog when ready, with its hiss; while it pumps or heats up, only a click, or its next step at once where
     * that was lost, as when the rocket was assembled in the middle of one.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || player.isSpectator()) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (state.getValue(FOG) != Fog.READY) {
            server.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, BUSY_VOLUME, 1.0F);
            if (!server.getBlockTicks().hasScheduledTick(pos, this)) {
                server.scheduleTick(pos, this, 1);
            }
            return InteractionResult.SUCCESS;
        }
        server.setBlock(pos, state.setValue(FOG, Fog.FOGGING), Block.UPDATE_ALL);
        server.playSound(null, pos, AerolargeRegistry.FOG_HISS.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        server.scheduleTick(pos, this, FOG_TICKS);
        return InteractionResult.SUCCESS;
    }

    /** Done pumping, it heats up again; heated, it is ready, with a beep. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        switch (state.getValue(FOG)) {
            case FOGGING -> {
                level.setBlock(pos, state.setValue(FOG, Fog.HEATING), Block.UPDATE_ALL);
                level.scheduleTick(pos, this, HEAT_TICKS);
            }
            case HEATING -> {
                level.setBlock(pos, state.setValue(FOG, Fog.READY), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, READY_VOLUME, READY_PITCH);
            }
            case READY -> {
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FogMachineBlockEntity(pos, state);
    }

    /** The fog it pumps out, on the client; on the server, its next step kept to where it was lost. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, AerolargeRegistry.FOG_MACHINE_ENTITY.get(),
            level.isClientSide ? FogMachineBlockEntity::clientTick : FogMachineBlockEntity::serverTick);
    }
}
