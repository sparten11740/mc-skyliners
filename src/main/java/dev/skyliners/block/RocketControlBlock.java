package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.RocketControls;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One of the hands-on controls on a rocket's centre console, round her control head (see {@link RocketControls}): a
 * module of the console (see {@link ConsoleModule}), laid out as every other: a picture plate across the half nearest
 * the pilots saying what it does, glowing in the colour of what it shows, and its control behind the plate. What they
 * show is read from her autopilot every few ticks (see {@link RocketControlBlockEntity}). Used, it asks her autopilot to
 * work it, which answers with a sound and a word in the hotbar. FACING is the way its face looks, toward the pilots, as
 * the laser switch's does.
 */
public abstract class RocketControlBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private final Map<Direction, VoxelShape> shapes;

    /** Its shape drawn facing north, the pilots at its north side (see {@link ConsoleModule#behindPlate}). */
    protected RocketControlBlock(Properties properties, VoxelShape north) {
        super(properties);
        this.shapes = TurnedShapes.of(north);
    }

    /** The control on her console it works. */
    abstract RocketControls.Control control();

    /**
     * Its state showing what her autopilot's panel shows (see {@link RocketControls.Panel}), and whether it blinks now:
     * a refusal's red blink, or the abort handle pulled out.
     */
    abstract BlockState shown(BlockState state, RocketControls.Panel panel, boolean blinking);

    /** Whether it blinks after a press, taken or refused (see {@link #shown}): a refused one blinks, as a rule. */
    boolean blinksAfter(boolean taken) {
        return !taken;
    }

    /** Used by a player, on the server: asks her autopilot to work it (see {@link RocketControlBlockEntity#ask}). */
    void use(ServerLevel level, BlockPos pos, BlockState state, RocketControlBlockEntity control, ServerPlayer player) {
        control.ask(level, player);
    }

    /** Shown afresh from what her autopilot shows: a sound for the change, where it has one. */
    void changed(ServerLevel level, BlockPos pos, BlockState was, BlockState now) {
    }

    /** Put down by hand, its face looks at whoever puts it down, as it looks at the pilots. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
            && level.getBlockEntity(pos) instanceof RocketControlBlockEntity control) {
            control.press(serverLevel, serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RocketControlBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != SkylinersRegistry.ROCKET_CONTROL_ENTITY.get() ? null : RocketControlBlock::tick;
    }

    private static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T blockEntity) {
        if (level instanceof ServerLevel serverLevel && blockEntity instanceof RocketControlBlockEntity control) {
            control.tick(serverLevel);
        }
    }
}
