package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A bar stool for the lounge's bar: a round oxblood leather seat on a chrome column with a footrest ring round it,
 * over a heavy round foot. Use it to sit on it: it is one of Create's seats underneath, as the cabin seats are, so the
 * seatbelts and everything else that knows about seats treat whoever sits on it as seated. It is round, so it has no
 * front, and nothing sits down on it by landing on it.
 */
public class BarStoolBlock extends SeatBlock {
    private static final MapCodec<BarStoolBlock> CODEC = simpleCodec(BarStoolBlock::new);
    /** The foot, the column and the seat on it. */
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(4, 0, 4, 12, 1, 12),
        Block.box(7, 1, 7, 9, 8.5, 9),
        Block.box(3, 8.5, 3, 13, 10, 13)
    );

    public BarStoolBlock(Properties properties) {
        super(properties, DyeColor.RED);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /**
     * Using it sits whoever uses it down on it, as Create's seats do, with nothing in hand as far as Create is
     * concerned, so that no dye turns it into one of Create's.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return super.useItemOn(ItemStack.EMPTY, state, level, pos, player, hand, hit);
    }

    /** Nothing that lands on it sits down, and nothing bounces off it as off Create's cushions. */
    @Override
    public void updateEntityAfterFallOn(BlockGetter level, Entity entity) {
        entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0, 0.0, 1.0));
    }
}
