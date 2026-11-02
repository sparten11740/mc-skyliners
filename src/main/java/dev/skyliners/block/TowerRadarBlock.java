package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The tower's radar screen: a monitor on a desk stand whose screen sweeps round like a radar's, the transponders in
 * range lighting up on it in their colours as the sweep passes (see {@code TowerRadarRenderer}). Used, it opens the
 * radar's map, every transponder on it, the airliners where they fly. Put up by hand against the side of a block it
 * hangs on it as a wall monitor, without its stand (MOUNT); anywhere else it stands on its stand. FACING is the way its
 * screen looks, toward whoever placed it, or out from the wall it hangs on.
 */
public class TowerRadarBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<TowerRadarBlock> CODEC = simpleCodec(TowerRadarBlock::new);
    /** How the monitor is put up: on its stand, or hung on a wall; one saved before it could be stood on its stand. */
    public static final EnumProperty<Mount> MOUNT = Mount.property(Mount.WALL, Mount.FLOOR);
    /**
     * Where the monitor's screen is: in pixels in from the face of a wall it hangs on, as deep as it is on its stand,
     * and as high on a wall as on the stand.
     */
    public static final int WALL_SCREEN_BACK = 12;
    /** The stand and the monitor on it, drawn facing north: the screen's face is the monitor's north side. */
    private static final Map<Direction, VoxelShape> ON_STAND = TurnedShapes.of(Shapes.or(Block.box(3, 0, 6, 13, 3, 11), Block.box(0.5, 3, 6, 15.5, 15, 10)));
    /** The monitor alone hung on a wall, drawn facing north: its back against the wall at its block's south side. */
    private static final Map<Direction, VoxelShape> ON_WALL = TurnedShapes.of(Block.box(0.5, 3, WALL_SCREEN_BACK, 15.5, 15, 16));

    public TowerRadarBlock(Properties properties) {
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

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return monitor(context, defaultBlockState());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return monitorShape(state);
    }

    /**
     * A monitor put up by a player, the radar's or the weather screen's: against the side of the block clicked, as a
     * button goes (see {@link Mounting}), hung on it looking out; anywhere else on its stand, looking at whoever puts
     * it there.
     */
    static BlockState monitor(BlockPlaceContext context, BlockState base) {
        Direction toward = Mounting.support(context, List.of(Mount.WALL));
        return toward == null ? base.setValue(MOUNT, Mount.FLOOR).setValue(FACING, context.getHorizontalDirection().getOpposite())
            : base.setValue(MOUNT, Mount.WALL).setValue(FACING, toward.getOpposite());
    }

    /** A monitor's shape, the radar's or the weather screen's, on its stand or on a wall. */
    static VoxelShape monitorShape(BlockState state) {
        return (state.getValue(MOUNT) == Mount.WALL ? ON_WALL : ON_STAND).get(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer viewer) {
            PacketDistributor.sendToPlayer(viewer, new OpenTowerRadar(pos));
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5F, 1.6F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TowerRadarBlockEntity(pos, state);
    }
}
