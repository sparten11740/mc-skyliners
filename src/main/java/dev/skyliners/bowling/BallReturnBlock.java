package dev.skyliners.bowling;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.Mount;
import dev.skyliners.block.Mounting;
import dev.skyliners.block.TurnedShapes;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A ball return, for the freighter's bowling lane (see {@code airframe.FreighterBowling}): never empty, it hands over a
 * bowling ball whenever it is used, with a rumble as one rolls up its track, and a ball that has finished rolling near
 * it goes back down its track by itself (see {@link BowlingBall}). Stood on a floor (MOUNT) it is the squat machine of
 * an alley, a dark casing under a domed hood, three balls waiting on the chrome rails at its front; fixed to a wall,
 * a rack of three balls on a chrome shelf. A ball return under a ceiling would make no sense, so it is never hung
 * there. FACING is the way its front looks: on a wall out from it.
 */
public class BallReturnBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<BallReturnBlock> CODEC = simpleCodec(BallReturnBlock::new);
    /** How it is put up: standing on a floor, as on the freighter's lane, or fixed to a wall. */
    public static final EnumProperty<Mount> MOUNT = Mount.property(Mount.WALL, Mount.FLOOR);
    /**
     * Its casing and hood standing, and its rack and the balls on it on a wall, facing north, out from its wall, which
     * is to the south.
     */
    private static final Map<Direction, VoxelShape> STANDING = TurnedShapes.of(Shapes.or(Block.box(1, 0, 2, 15, 9, 14), Block.box(2, 9, 7, 14, 13, 14)));
    private static final Map<Direction, VoxelShape> ON_A_WALL = TurnedShapes.of(Block.box(1, 3, 10, 15, 10, 16));
    /** How loud the rumble of a ball rolling up its track is, and its pitch. */
    private static final float RUMBLE_VOLUME = 0.7F;
    private static final float RUMBLE_PITCH = 1.4F;

    public BallReturnBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MOUNT, Mount.FLOOR));
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
     * Put up against what holds it where the player puts it (see {@link Mounting}): on a wall facing out from it, on a
     * floor facing whoever puts it there; nowhere that nothing holds it.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction toward = Mounting.support(context, MOUNT.getPossibleValues());
        if (toward == null) {
            return null;
        }
        Mount mount = Mount.against(toward);
        Direction facing = mount == Mount.WALL ? toward.getOpposite() : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(MOUNT, mount).setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(MOUNT) == Mount.WALL ? ON_A_WALL : STANDING).get(state.getValue(FACING));
    }

    /** Used, it hands over a bowling ball, rumbling as the ball rolls up its track. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            player.getInventory().placeItemBackInInventory(new ItemStack(SkylinersRegistry.BOWLING_BALL_ITEM.get()));
            level.playSound(null, pos, SkylinersRegistry.BOWLING_ROLL.get(), SoundSource.BLOCKS, RUMBLE_VOLUME, RUMBLE_PITCH);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
