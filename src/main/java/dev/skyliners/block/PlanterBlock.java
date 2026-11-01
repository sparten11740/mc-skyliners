package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A slim white planter for the cabin, its back to the wall, planted with PLANT: low clipped greenery, the same in
 * flower, or a stand of bamboo canes up to the top of the block with tufts of leaves. Use a fern or leaves on it to
 * plant greenery, a small flower for flowers and bamboo for bamboo, the plant used up (unless in creative mode); taken
 * up, it keeps what is planted in it. FACING is the way its front faces, away from the wall behind it.
 */
public class PlanterBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<PlanterBlock> CODEC = simpleCodec(PlanterBlock::new);
    public static final EnumProperty<Plant> PLANT = EnumProperty.create("plant", Plant.class);

    /** What grows in it, and how high, in pixels; its item's looks follow this order. */
    public enum Plant implements StringRepresentable {
        GREENERY(12.5),
        FLOWERS(12.5),
        BAMBOO(16);

        private final double height;

        Plant(double height) {
            this.height = height;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** The planter box facing north, its back against the south side of the block, and what grows over it. */
    private static final Map<Plant, Map<Direction, VoxelShape>> SHAPES = Map.of(
        Plant.GREENERY, shapes(Plant.GREENERY),
        Plant.FLOWERS, shapes(Plant.FLOWERS),
        Plant.BAMBOO, shapes(Plant.BAMBOO)
    );

    public PlanterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PLANT, Plant.GREENERY));
    }

    private static Map<Direction, VoxelShape> shapes(Plant plant) {
        return TurnedShapes.of(Shapes.or(Block.box(0.5, 0, 6.5, 15.5, 7.5, 15.5), Block.box(2, 7.5, 8, 14, plant.height, 14)));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PLANT);
    }

    /** A planter of this block with the plant in it, its front facing the way given, as the blueprints put one in. */
    public static BlockState planted(Block planter, Plant plant, Direction facing) {
        return planter.defaultBlockState().setValue(PLANT, plant).setValue(FACING, facing);
    }

    /** Its front toward whoever puts it down, its back to the wall they face. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(PLANT)).get(state.getValue(FACING));
    }

    /** A plant used on it is planted in it instead of what grew there, if it is not that already. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        Plant plant = plantOf(stack);
        if (plant == null || state.getValue(PLANT) == plant) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(PLANT, plant), Block.UPDATE_CLIENTS);
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The plant an item plants, or null if it is none. */
    @Nullable
    private static Plant plantOf(ItemStack stack) {
        if (stack.is(Items.BAMBOO)) {
            return Plant.BAMBOO;
        }
        if (stack.is(ItemTags.SMALL_FLOWERS)) {
            return Plant.FLOWERS;
        }
        return stack.is(Items.FERN) || stack.is(ItemTags.LEAVES) ? Plant.GREENERY : null;
    }

    /** A planter with what grows in it, the way one broken comes back; plain greenery is left off, so they stack. */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        ItemStack stack = new ItemStack(this);
        if (state.getValue(PLANT) != Plant.GREENERY) {
            stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(PLANT, state.getValue(PLANT)));
        }
        return stack;
    }
}
