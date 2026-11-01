package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A bean bag for the kids' den: a big squashy bag of fabric panels slumped into a seat, its sides bunched up round it
 * and its back heaped up behind, a carrying strap on top. It is one of Create's seats underneath, so using it sits you
 * down in it and the seatbelts and villager passengers treat it as a seat (see {@link dev.skyliners.cabin.Passengers});
 * nothing sits down in it by landing on it, nothing bounces off it, and a fall into it never hurts, only flumps. It is
 * in COLOR, tinted on the client, and dyed by using a dye on it, as a cabin seat is. Put down, it faces whoever puts it
 * there, its back away from them; FACING is the way the sitter faces.
 */
public class BeanBagBlock extends SeatBlock {
    private static final MapCodec<BeanBagBlock> CODEC = simpleCodec(BeanBagBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    /** The colour a bean bag is made in until dyed. */
    private static final DyeColor OWN = DyeColor.ORANGE;
    /** The fall into it that flumps, in blocks, and the flump: a soft, deep thud of fabric. */
    private static final float FLUMP_FALL = 1.0F;
    private static final float FLUMP_PITCH = 0.6F;
    /**
     * The bag facing north, its back to the south: the bag on the floor up to its seat, its back heaped up behind it
     * and its sides bunched up either side of the seat.
     */
    private static final VoxelShape OUTLINE = Shapes.or(
        Block.box(0.5, 0, 0.5, 15.5, 6.5, 15.5),
        Block.box(1, 6.5, 10, 15, 12.5, 15.5),
        Block.box(0.5, 6.5, 2, 3, 8, 10),
        Block.box(13, 6.5, 2, 15.5, 8, 10)
    );
    /** What is solid of it: the seat a little lower, as the bag gives under whoever sits, and the back. */
    private static final VoxelShape SOLID = Shapes.or(
        Block.box(0.5, 0, 0.5, 15.5, 6, 15.5),
        Block.box(1, 6, 11, 15, 10, 15.5)
    );
    private static final Map<Direction, VoxelShape> OUTLINES = TurnedShapes.of(OUTLINE);
    private static final Map<Direction, VoxelShape> SOLIDS = TurnedShapes.of(SOLID);

    public BeanBagBlock(Properties properties) {
        super(properties, OWN);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(COLOR, OWN));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(FACING, COLOR));
    }

    /** The colour the client tints the bag with: its dye's. */
    public static int tint(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        return state.getValue(COLOR).getTextureDiffuseColor();
    }

    /** The colour the client tints a bag in hand or in an inventory with: the one it was taken up in, or its own. */
    public static int itemTint(ItemStack stack, int tintIndex) {
        DyeColor color = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY).get(COLOR);
        return (color != null ? color : OWN).getTextureDiffuseColor();
    }

    /** Facing whoever puts it there, its back away from them, so it goes against a wall with its back to it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return withWater(defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()), context);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINES.get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SOLIDS.get(state.getValue(FACING));
    }

    /**
     * A dye recolours the bag, using one up (unless in creative mode); anything else sits whoever uses it down in it, as
     * Create's seats do, with nothing in hand as far as Create is concerned, so that no dye turns it into one of Create's.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof DyeItem dye && !player.isShiftKeyDown()) {
            if (state.getValue(COLOR) == dye.getDyeColor()) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide) {
                level.setBlock(pos, state.setValue(COLOR, dye.getDyeColor()), Block.UPDATE_CLIENTS);
                stack.consume(1, player);
                level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return super.useItemOn(ItemStack.EMPTY, state, level, pos, player, hand, hit);
    }

    /** A fall into it never hurts: it flumps instead. */
    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if (!level.isClientSide && fallDistance >= FLUMP_FALL) {
            level.playSound(null, pos, SoundEvents.WOOL_FALL, SoundSource.BLOCKS, 1.0F, FLUMP_PITCH);
        }
        entity.causeFallDamage(fallDistance, 0.0F, level.damageSources().fall());
    }

    /** Nothing that lands in it sits down, and nothing bounces off it as off Create's cushions: it sinks in. */
    @Override
    public void updateEntityAfterFallOn(BlockGetter level, Entity entity) {
        entity.setDeltaMovement(entity.getDeltaMovement().multiply(1.0, 0.0, 1.0));
    }

    /**
     * A bag in its colour, the way one broken comes back (the loot table copies the colour the same way): the colour
     * rides along as the block state to put it down in, and its own is left off, so new bags stack with it.
     */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        ItemStack stack = new ItemStack(this);
        if (state.getValue(COLOR) != OWN) {
            stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(COLOR, state.getValue(COLOR)));
        }
        return stack;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }
}
