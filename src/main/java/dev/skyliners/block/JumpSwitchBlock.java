package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.compat.SableFlight;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The jump light switch on the flight deck's centre console: each use turns every jump light by the doors from dark
 * to red (stand by), to green (go), and dark again, through the relays behind them. One put up by hand, which nothing
 * wired, works the jump lights put up by hand round it instead (see {@link #switchLights}), so that a ship or a drop
 * zone of a player's own can have its jump lights too. FACING is the way its face looks, toward the pilots.
 */
public class JumpSwitchBlock extends HorizontalDirectionalBlock implements EntityBlock, LinkedControlBlockEntity.Driver {
    private static final MapCodec<JumpSwitchBlock> CODEC = simpleCodec(JumpSwitchBlock::new);
    public static final EnumProperty<JumpLightBlock.Signal> SIGNAL = JumpLightBlock.SIGNAL;
    public static final String LIGHTS = "lights";
    /** A module of the console with the selector knob on it. */
    private static final VoxelShape SHAPE = ConsoleModule.with(2, 2);
    /**
     * How far a jump switch put up by hand reaches the jump lights put up by hand round it, in blocks: across a hold to
     * its door, or from a cockpit to the door behind it, but not to another ship's lights across a hangar.
     */
    private static final int REACH = 16;

    public JumpSwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(SIGNAL, JumpLightBlock.Signal.OFF));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SIGNAL);
    }

    /** Put down by hand, its face looks at whoever puts it down, as it looks at the pilots. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public int signal(BlockState state, String group) {
        return state.getValue(SIGNAL).power();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            JumpLightBlock.Signal signal = state.getValue(SIGNAL).next();
            level.setBlock(pos, state.setValue(SIGNAL, signal), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6F, 1.2F);
            if (level.getBlockEntity(pos) instanceof LinkedControlBlockEntity control && control.wired()) {
                control.resync();
            } else if (level instanceof ServerLevel server) {
                switchLights(server, pos, signal);
            }
            player.displayClientMessage(Component.translatable("message.skyliners.jump_switch." + signal.getSerializedName()), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Turns the jump lights put up by hand round a switch nothing wired to the signal: those within its reach aboard
     * the same ship as it, or in the world as it is, that no relay beside them wires to a ship's own switch. They are
     * looked for only as the switch is used. A switch and its lights aboard one ship are all in its plot, so the reach
     * is measured among them as they fly.
     */
    private static void switchLights(ServerLevel level, BlockPos pos, JumpLightBlock.Signal signal) {
        UUID ship = SableFlight.bodyAt(level, pos);
        for (BlockPos light : Nearby.blocks(level, pos, REACH, REACH, state -> state.getBlock() instanceof JumpLightBlock)) {
            if (light.distSqr(pos) <= REACH * REACH && Objects.equals(SableFlight.bodyAt(level, light), ship) && !JumpLightBlock.wired(level, light)) {
                JumpLightBlock.show(level, light, level.getBlockState(light), signal);
            }
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LinkedControlBlockEntity(pos, state);
    }
}
