package dev.skyliners.beam;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A beam pad: a deck plate laid into a floor, the rings of a target round a round plate on its top, where whatever a
 * tractor beam lifts arrives (see {@link TractorBeamBlockEntity}), as onto the pad in the freighter's cargo hold. Press
 * it to switch the nearest tractor beam within reach on or off, its rings glowing green and pulsing in toward its
 * middle while the beam is lit; step onto it then and the beam lets you down to the ground, sinking gently. With no
 * beam in reach, pressed, it only buzzes. LIT is whether its beam is lit.
 */
public class BeamPadBlock extends BaseEntityBlock {
    private static final MapCodec<BeamPadBlock> CODEC = simpleCodec(BeamPadBlock::new);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** How brightly its rings glow while its beam is lit, in light levels. */
    public static final int GLOW = 10;
    /** How loud its buzz is, pressed with no beam in reach. */
    private static final float BUZZ_VOLUME = 0.8F;

    public BeamPadBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Switches the nearest tractor beam in reach on or off, things arriving on this pad; a buzz for none. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || player.isSpectator()) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        TractorBeamBlockEntity beam = TractorBeamBlockEntity.nearest(server, pos);
        if (beam == null) {
            server.playSound(null, pos, SkylinersRegistry.TRACTOR_BEAM_REFUSED.get(), SoundSource.BLOCKS, BUZZ_VOLUME, 1.0F);
            show(server, pos, false);
        } else {
            beam.light(server, !beam.lit(), pos);
        }
        return InteractionResult.SUCCESS;
    }

    /** Lights a pad, or puts it out, if it is still a pad. */
    static void show(ServerLevel level, BlockPos pos, boolean lit) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof BeamPadBlock && state.getValue(LIT) != lit) {
            level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_CLIENTS);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BeamPadBlockEntity(pos, state);
    }
}
