package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The robot bartender, an android in chrome and white that stands behind a bar, two blocks tall: a chrome pedestal
 * glowing round its foot, a white torso with a light on its chest, two jointed arms and a rounded head whose dark visor
 * glows for its eyes. Its lower block holds the pedestal and the torso, its block's own model, and its block entity;
 * the upper block is room for the rest, which its renderer draws moving (see the client's RobotBartenderRenderer): it
 * polishes a glass, turns its head with a servo's whirr toward whoever comes near, and blinks now and then. Either
 * half, used, opens its shop of mocktails (see {@link Shop}) with a greeting in its robot voice, and each sale it
 * answers, shakes a cocktail shaker, pours and sets the drink down on the bar in front of it. Two blocks tall so a
 * customer across a counter, who sees only what stands over it, can reach it there.
 *
 * <p>FACING is the way it faces, toward its customers across the bar; HALF which of its blocks this is; PART what a
 * state of its lower block looks like (see {@link Part}): only its body is ever placed.
 */
public class RobotBartenderBlock extends BaseEntityBlock {
    private static final MapCodec<RobotBartenderBlock> CODEC = simpleCodec(RobotBartenderBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** The lower block facing north: the pedestal's foot, its column and the torso from the hips up. */
    private static final VoxelShape LOWER = Shapes.or(
        Block.box(2.5, 0, 2.5, 13.5, 2, 11.5),
        Block.box(6, 2, 5, 10, 11.5, 9),
        Block.box(4, 11.5, 3.5, 12, 16, 10.5)
    );
    /** The upper block facing north: the chest, its shoulders and its arms reaching forward, and the head over them. */
    private static final VoxelShape UPPER = Shapes.or(
        Block.box(2, 0, 0, 14, 7, 10.5),
        Block.box(4.5, 7, 3.5, 11.5, 16, 10.5)
    );
    private static final Map<Direction, VoxelShape> LOWER_SHAPES = TurnedShapes.of(LOWER);
    private static final Map<Direction, VoxelShape> UPPER_SHAPES = TurnedShapes.of(UPPER);
    /** What it sells, cheapest first: an emerald for two of the gentler drinks, or for one of the stronger. */
    private static final List<Shop.Trade> STOCK = List.of(
        new Shop.Trade(1, SkylinersRegistry.NEBULA_FIZZ, 2),
        new Shop.Trade(1, SkylinersRegistry.ZERO_G_FLOAT, 2),
        new Shop.Trade(1, SkylinersRegistry.MOON_MILK, 2),
        new Shop.Trade(1, SkylinersRegistry.ROCKET_FUEL, 1),
        new Shop.Trade(1, SkylinersRegistry.SUPERNOVA_SUNRISE, 1)
    );
    /**
     * Its shop: a greeting as it opens and an answer to each sale, both in its robot voice, the answer only with a
     * serve, so a sale while one is under way, from this shopper or another, is not answered twice.
     */
    private static final Shop.Shopfront SHOPFRONT = new Shop.Shopfront(Component.translatable("container.skyliners.mocktail_bar"), STOCK,
        SkylinersRegistry.BARTENDER_GREET, SkylinersRegistry.BARTENDER_SERVE, RobotBartenderBlockEntity.SERVE_TICKS, RobotBartenderBlock::sold);

    /**
     * What a state of the bartender's lower block looks like: its body, the pedestal and the torso, the block as
     * placed; or one of the parts its renderer draws over it, each turned about its own joint: its head, its visor's
     * glow (left out as it blinks), the upper arms and forearms of its left and right arms, and what it holds, the
     * glass it polishes, the drink it pours into it, the cocktail shaker and the polishing cloth.
     */
    public enum Part implements StringRepresentable {
        BODY,
        HEAD,
        VISOR,
        LEFT_UPPER_ARM,
        LEFT_FOREARM,
        RIGHT_UPPER_ARM,
        RIGHT_FOREARM,
        GLASS,
        DRINK,
        SHAKER,
        CLOTH;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public RobotBartenderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, DoubleBlockHalf.LOWER).setValue(PART, Part.BODY));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HALF, PART);
    }

    /** The bartender's lower block, facing the way given; its upper block is {@link #upper} of it. */
    public static BlockState lower(Block bartender, Direction facing) {
        return bartender.defaultBlockState().setValue(FACING, facing);
    }

    /** The upper block over a bartender's lower block. */
    public static BlockState upper(BlockState lower) {
        return lower.setValue(HALF, DoubleBlockHalf.UPPER);
    }

    /** Where the bartender's lower block is, under the half given or the half itself. */
    private static BlockPos base(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
    }

    /** It faces whoever puts it down, as across the bar; it needs the room above for its head. */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return lower(this, context.getHorizontalDirection().getOpposite());
    }

    /** The upper block goes in over the lower one put down. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), upper(state), Block.UPDATE_ALL);
    }

    /** A half whose other half is gone goes too, so the bartender is only ever whole. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        boolean towardOtherHalf = direction == (half == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN);
        if (towardOtherHalf && !(neighbour.is(this) && neighbour.getValue(HALF) != half)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /** Broken by its upper block in creative mode, the lower one it takes with it drops nothing either. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(HALF) == DoubleBlockHalf.UPPER && level.getBlockState(pos.below()).is(this)) {
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(HALF) == DoubleBlockHalf.LOWER ? LOWER_SHAPES : UPPER_SHAPES).get(state.getValue(FACING));
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    /** Either half opens the bartender's shop, at its lower block. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos base = base(pos, state);
        if (level instanceof ServerLevel server && player instanceof ServerPlayer shopper && server.getBlockState(base).is(this)) {
            Shop.open(shopper, server, base, SHOPFRONT);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A sale: it serves the drink, unless it is still serving the last one. */
    private static boolean sold(ServerLevel level, BlockPos bartender, @Nullable ServerPlayer shopper) {
        return level.getBlockEntity(bartender) instanceof RobotBartenderBlockEntity robot && robot.serve();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    /** Only the lower block holds the bartender's moves. */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER ? new RobotBartenderBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide && type == SkylinersRegistry.ROBOT_BARTENDER_ENTITY.get()
            ? (tickLevel, pos, tickState, bartender) -> ((RobotBartenderBlockEntity) bartender).clientTick()
            : null;
    }
}
