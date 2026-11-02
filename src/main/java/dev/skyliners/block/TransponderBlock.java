package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.radar.Transponders;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A transponder: wherever it is, it shows on every autopilot's map and on the tower's radar by the picture and the
 * colour picked on its panel, as a beacon does in Subnautica. Put one at home, at a mine or at an airfield to fly back
 * to it; the control tower has one, and every airliner one on its flight deck, shown on the radar where it flies. It
 * is a module of the centre console, so it stands among the flight deck's controls, and on a desk anywhere else; its
 * little screen shows its picture in its colour. Put up by hand it can also go on a wall or under a ceiling, as a
 * button can (MOUNT): the module tipped over onto the wall, its face looking out from it, or hung face down. FACING is
 * the way toward whoever put it there: on a wall the way its face looks out; on the floor the way the front of the
 * module with its knobs is, and under a ceiling the way the top of its picture is, so that it reads the right way up
 * to them looking down or up at it.
 */
public class TransponderBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<TransponderBlock> CODEC = simpleCodec(TransponderBlock::new);
    /** How the transponder is put up; one saved before it had a mount stood on a floor or a desk, as all did then. */
    public static final EnumProperty<Mount> MOUNT = Mount.property(Mount.CEILING, Mount.WALL, Mount.FLOOR);

    public TransponderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(MOUNT, Mount.FLOOR));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MOUNT);
    }

    /**
     * Against the block clicked, as a button goes (see {@link Mounting}): on top of it, on its side or under it, facing
     * whoever puts it there. Where nothing would hold it, it stands where it is put as it always could, on a console
     * module or a desk whose top is not whole.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction placer = context.getHorizontalDirection().getOpposite();
        Direction toward = Mounting.support(context, MOUNT.getPossibleValues());
        if (toward == null) {
            return defaultBlockState().setValue(FACING, placer);
        }
        Mount mount = Mount.against(toward);
        return defaultBlockState().setValue(MOUNT, mount).setValue(FACING, mount == Mount.WALL ? toward.getOpposite() : placer);
    }

    /** The console module's slab, wherever it is put up: its knobs are too small to catch on. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(MOUNT)) {
            case WALL -> ConsoleModule.ON_WALL.get(state.getValue(FACING));
            case CEILING -> ConsoleModule.UNDER_CEILING;
            case FLOOR -> ConsoleModule.SLAB;
        };
    }

    /** Used, it opens its panel, to pick its picture and colour. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer opener && level.getBlockEntity(pos) instanceof TransponderBlockEntity transponder) {
            PacketDistributor.sendToPlayer(opener, new OpenTransponder(pos, transponder.icon(), transponder.colour()));
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5F, 1.6F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Gone from where it stood, it goes off the maps there; moved (into an airliner being assembled), it shows where the
     * airliner takes it once loaded there (see {@link Transponders#keep}).
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            Transponders.remove(server, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TransponderBlockEntity(pos, state);
    }
}
