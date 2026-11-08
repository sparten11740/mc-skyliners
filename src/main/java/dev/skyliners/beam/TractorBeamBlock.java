package dev.skyliners.beam;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.Mount;
import dev.skyliners.block.Mounting;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A tractor beam (see {@link TractorBeamBlockEntity}): a gunmetal housing ringed in hazard stripes round a wide round
 * lens, hung under a ceiling as the freighter's is under her belly, its lens looking down, or standing on a floor, its
 * lens looking up. Use it, or give it a redstone pulse, to switch it on or off: lit, its lens glows green and a beam of
 * green light shines out of it, rings of light rising up it, humming, and it carries whatever it carries up along it.
 * Hung, its beam reaches down to the ground; standing, a dozen blocks up. MOUNT is how it is put up, LIT whether it
 * shines and POWERED whether it has a redstone signal.
 */
public class TractorBeamBlock extends BaseEntityBlock {
    private static final MapCodec<TractorBeamBlock> CODEC = simpleCodec(TractorBeamBlock::new);
    public static final EnumProperty<Mount> MOUNT = Mount.property(Mount.CEILING, Mount.FLOOR);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** How far from its block's middle its lens is, toward the way it looks, in blocks: its rim's face, as drawn. */
    static final double LENS_OUT = 1.5 / 16;
    /** How brightly its lens glows while lit, in light levels. */
    public static final int GLOW = 12;
    /** Its housing hung under a ceiling: its mounting plate, its body and the rim round its lens. */
    private static final VoxelShape HUNG = Shapes.or(Block.box(1, 14, 1, 15, 16, 15), Block.box(3, 9, 3, 13, 14, 13),
        Block.box(2, 6.5, 2, 14, 9, 14));
    /** The same standing on a floor, upside down. */
    private static final VoxelShape STANDING = Shapes.or(Block.box(1, 0, 1, 15, 2, 15), Block.box(3, 2, 3, 13, 7, 13),
        Block.box(2, 7, 2, 14, 9.5, 14));

    public TractorBeamBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MOUNT, Mount.CEILING).setValue(LIT, false).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MOUNT, LIT, POWERED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(MOUNT) == Mount.CEILING ? HUNG : STANDING;
    }

    /** Hung under the ceiling or stood on the floor the player puts it against (see {@link Mounting}), dark. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction toward = Mounting.support(context, MOUNT.getPossibleValues());
        if (toward == null) {
            return null;
        }
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
        return defaultBlockState().setValue(MOUNT, Mount.against(toward)).setValue(POWERED, powered);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        boolean canUse = !player.isSpectator();
        if (canUse && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof TractorBeamBlockEntity beam) {
            beam.light(server, !beam.lit(), null);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A redstone pulse switches it as a hand does, once as its signal comes on. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos from, boolean moved) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        boolean powered = server.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) {
            return;
        }
        server.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        if (powered && server.getBlockEntity(pos) instanceof TractorBeamBlockEntity beam) {
            beam.light(server, !beam.lit(), null);
        }
    }

    /** Taken away while lit, it lets go of whatever it holds, gently, and puts its pad out. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState now, boolean moved) {
        boolean goneLit = !now.is(this) && state.getValue(LIT);
        if (goneLit && level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof TractorBeamBlockEntity beam) {
            beam.goneWhileLit(server);
        }
        super.onRemove(state, level, pos, now, moved);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TractorBeamBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!state.getValue(LIT)) {
            return null;
        }
        return createTickerHelper(type, SkylinersRegistry.TRACTOR_BEAM_ENTITY.get(),
            level.isClientSide ? TractorBeamBlockEntity::clientTick : TractorBeamBlockEntity::serverTick);
    }
}
