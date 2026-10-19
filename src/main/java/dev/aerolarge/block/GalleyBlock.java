package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.entity.Trolley;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The galley, where the cabin crew's trolleys are stocked: using it fills every trolley standing by it, or, with none
 * there, rolls out a full one a couple of blocks in front of it. FACING is the way its front looks.
 */
public class GalleyBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<GalleyBlock> CODEC = simpleCodec(GalleyBlock::new);
    /** How near the galley a trolley has to stand to be restocked, in blocks. */
    private static final double REACH = 3;
    /** The highest a floor covering in front of the galley may be, in blocks, for a trolley to be rolled out onto it. */
    private static final double LOW = 0.25;
    /** How far out in front a new trolley is rolled, in blocks: far enough to step behind it and push. */
    private static final int ROLL_OUT = 2;

    public GalleyBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        List<Trolley> trolleys = level.getEntitiesOfClass(Trolley.class, new AABB(pos).inflate(REACH));
        if (trolleys.isEmpty()) {
            Direction front = state.getValue(FACING);
            BlockPos out = pos.relative(front, ROLL_OUT);
            // Out across the floor in front, standing on anything low there, like carpet, with room behind it to push.
            double floorTop = floorTop(level, out);
            if (floorTop <= LOW && clear(level, pos.relative(front)) && clear(level, out)) {
                level.addFreshEntity(Trolley.at(level, Vec3.atBottomCenterOf(out).add(0, floorTop, 0), front.toYRot()));
                player.displayClientMessage(Component.translatable("message.aerolarge.galley.rolled_out"), true);
            } else {
                player.displayClientMessage(Component.translatable("message.aerolarge.galley.blocked"), true);
            }
        } else {
            long restocked = trolleys.stream().filter(Trolley::restock).count();
            player.displayClientMessage(Component.translatable(restocked > 0 ? "message.aerolarge.galley.restocked" : "message.aerolarge.galley.full"), true);
        }
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8F, 1.4F);
        return InteractionResult.SUCCESS;
    }

    /** How high the floor covering at a spot stands, in blocks: none on a bare floor. */
    private static double floorTop(Level level, BlockPos at) {
        VoxelShape floor = level.getBlockState(at).getCollisionShape(level, at);
        return floor.isEmpty() ? 0 : floor.max(Direction.Axis.Y);
    }

    /** Whether a trolley can stand at a spot: nothing but a low covering on the floor, and nothing above it. */
    private static boolean clear(Level level, BlockPos at) {
        return floorTop(level, at) <= LOW && level.getBlockState(at.above()).getCollisionShape(level, at.above()).isEmpty();
    }
}
