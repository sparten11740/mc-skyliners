package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The laser crossbow's rack on the freighter's entry hall wall, by her ramp: a dark board with two brass pegs, the
 * crossbow lying across them while it is racked (LOADED), which the client's {@code CrossbowRackRenderer} draws there.
 * Use it with an empty hand to take the crossbow down, with a clack; use it holding a laser crossbow to rack it again.
 * An empty rack shows its bare pegs, so anyone sees at once whether the crossbow is there. A crossbow racked cools at
 * once: the one taken down is fresh. With the server config's {@code freighter.laserCrossbow} off it only clicks, as
 * the crossbow would not fire. FACING is the way it faces out from the wall. Its block entity keeps nothing; it is only
 * there for its renderer.
 */
public class CrossbowRackBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<CrossbowRackBlock> CODEC = simpleCodec(CrossbowRackBlock::new);
    /** Whether the crossbow lies across its pegs. */
    public static final BooleanProperty LOADED = BooleanProperty.create("loaded");
    /** How loud its clack is, taking the crossbow down or racking it. */
    private static final float CLACK_VOLUME = 0.8F;
    /** Its board and pegs against the wall, to bump into, by the way it faces. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.NORTH, Block.box(1, 3, 12, 15, 13, 16));
        SHAPES.put(Direction.SOUTH, Block.box(1, 3, 0, 15, 13, 4));
        SHAPES.put(Direction.WEST, Block.box(12, 3, 1, 16, 13, 15));
        SHAPES.put(Direction.EAST, Block.box(0, 3, 1, 4, 13, 15));
    }

    public CrossbowRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LOADED, true));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOADED);
    }

    /** Put up by hand, it faces whoever puts it there, empty. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
            .setValue(LOADED, false);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Holding a laser crossbow at an empty rack, it is racked; anything else held is used as it would be anywhere. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
        InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!stack.is(SkylinersRegistry.LASER_CROSSBOW.get()) || state.getValue(LOADED)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide) {
            stack.consume(1, player);
            rack(level, pos, state, true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * With an empty hand at a racked crossbow, it is taken down into that hand, the main one, as only an empty main hand
     * comes here; with the crossbow switched off, only a click.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!state.getValue(LOADED)) {
            return InteractionResult.PASS;
        }
        if (!SkylinersConfig.FREIGHTER_LASER_CROSSBOW.get()) {
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, CLACK_VOLUME, 1.0F);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(SkylinersRegistry.LASER_CROSSBOW.get()));
            rack(level, pos, state, false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The crossbow racked or taken down, with a clack. */
    private static void rack(Level level, BlockPos pos, BlockState state, boolean loaded) {
        level.setBlock(pos, state.setValue(LOADED, loaded), Block.UPDATE_ALL);
        SoundEvent clack = loaded ? SoundEvents.CROSSBOW_LOADING_END.value() : SoundEvents.ARMOR_EQUIP_IRON.value();
        level.playSound(null, pos, clack, SoundSource.BLOCKS, CLACK_VOLUME, 1.0F);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrossbowRackBlockEntity(pos, state);
    }
}
