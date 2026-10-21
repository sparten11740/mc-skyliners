package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.FirstFlight;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * A bean-to-cup coffee machine, as in an airliner's galley: a brushed steel body with a black head, a bean hopper on
 * top, a drip tray under its spout and a small lit display on its front. Use it while it is ready to open its panel
 * (see {@link OpenCoffeeMachine}) and pick a drink; the server brews it (see {@link BrewCoffee}): the grinder, the
 * pump pouring the coffee into a cup that stands under the spout, and for a cappuccino or a mocha the steam frothing
 * the milk; a babycino is only the milk frothed. Then it chimes, and the drink waits under the spout until someone uses the machine to take it. The display
 * shows each step with a picture of its own. FACING is the way its front looks.
 */
public class CoffeeMachineBlock extends BaseEntityBlock {
    private static final MapCodec<CoffeeMachineBlock> CODEC = simpleCodec(CoffeeMachineBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** What it is doing, shown on its display; from grinding on, a cup of the drink stands under the spout. */
    public static final EnumProperty<Brew> BREW = EnumProperty.create("brew", Brew.class);
    /** The drink being brewed or waiting under the spout; the last one brewed while it is ready. */
    public static final EnumProperty<CoffeeDrink> DRINK = EnumProperty.create("drink", CoffeeDrink.class);

    /** The body, the drip tray sticking out in front of it and the bean hopper on top, drawn facing north. */
    private static final Map<Direction, VoxelShape> SHAPES = TurnedShapes.of(Shapes.or(
        Block.box(2, 0, 3, 14, 14, 15),
        Block.box(4, 0, 1, 12, 1, 3),
        Block.box(5, 14, 9, 10, 16, 14)
    ));
    /** The beeps of its buttons, high and quiet as a touch panel's, and the clink of the cup taken out. */
    private static final float BEEP_VOLUME = 0.5F;
    private static final float BEEP_PITCH = 1.8F;
    private static final float BUSY_PITCH = 0.9F;
    private static final float CUP_VOLUME = 0.6F;
    private static final float CUP_PITCH = 1.6F;

    /**
     * The steps of a brew, each lasting a few seconds, as long as its sound (see tools/generate_sounds.mjs): ready for
     * an order, grinding the beans, pouring the coffee, steaming the milk, and done, the drink waiting to be taken.
     */
    public enum Brew implements StringRepresentable {
        READY(0),
        GRINDING(30),
        POURING(50),
        STEAMING(50),
        DONE(0);

        private final int ticks;

        Brew(int ticks) {
            this.ticks = ticks;
        }

        /** How long the step lasts, in ticks; ready and done last until someone uses the machine. */
        int ticks() {
            return ticks;
        }

        /** Whether it is brewing, so it takes no order and has nothing to hand out. */
        public boolean busy() {
            return ticks > 0;
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public CoffeeMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(BREW, Brew.READY).setValue(DRINK, CoffeeDrink.ESPRESSO));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, BREW, DRINK);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** It faces whoever puts it down, its back to the wall behind it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Ready, it opens its panel to whoever uses it; done, it hands them the drink; brewing, it only beeps. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer user)) {
            return InteractionResult.SUCCESS;
        }
        switch (state.getValue(BREW)) {
            case READY -> {
                server.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, BEEP_VOLUME, BEEP_PITCH);
                PacketDistributor.sendToPlayer(user, new OpenCoffeeMachine(pos));
            }
            case DONE -> handOut(server, pos, state, user);
            default -> server.playSound(null, pos, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, BEEP_VOLUME, BUSY_PITCH);
        }
        return InteractionResult.CONSUME;
    }

    /**
     * Starts brewing a drink, grinding the beans first, or for a babycino, with no coffee in it, frothing the milk
     * straight away: only on a machine that is ready (see {@link BrewCoffee}).
     */
    static void brew(ServerLevel level, BlockPos pos, BlockState state, CoffeeDrink drink) {
        Brew first = drink.coffee() ? Brew.GRINDING : Brew.STEAMING;
        level.setBlock(pos, state.setValue(BREW, first).setValue(DRINK, drink), Block.UPDATE_ALL);
        SoundEvent sound = drink.coffee() ? AerolargeRegistry.COFFEE_GRIND.get() : AerolargeRegistry.COFFEE_STEAM.get();
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** The next step of the brew, once the one it is on is over: steaming only for a drink with milk. */
    static void advance(ServerLevel level, BlockPos pos, BlockState state) {
        Brew next = switch (state.getValue(BREW)) {
            case GRINDING -> Brew.POURING;
            case POURING -> state.getValue(DRINK).milk() ? Brew.STEAMING : Brew.DONE;
            case STEAMING -> Brew.DONE;
            case READY, DONE -> null;
        };
        if (next == null) {
            return;
        }
        level.setBlock(pos, state.setValue(BREW, next), Block.UPDATE_ALL);
        SoundEvent sound = switch (next) {
            case POURING -> AerolargeRegistry.COFFEE_POUR.get();
            case STEAMING -> AerolargeRegistry.COFFEE_STEAM.get();
            default -> AerolargeRegistry.COFFEE_READY.get();
        };
        level.playSound(null, pos, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /** The drink taken out from under the spout, into the hand or at the feet with no room for it, for Barista. */
    private static void handOut(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player) {
        CoffeeDrink drink = state.getValue(DRINK);
        level.setBlock(pos, state.setValue(BREW, Brew.READY), Block.UPDATE_ALL);
        ItemStack cup = drink.cup();
        if (!player.addItem(cup)) {
            player.drop(cup, false);
        }
        level.playSound(null, pos, SoundEvents.DECORATED_POT_INSERT, SoundSource.BLOCKS, CUP_VOLUME, CUP_PITCH);
        if (drink.coffee()) {
            FirstFlight.BARISTA.award(player, drink.getSerializedName());
        }
    }

    /**
     * A drink left under the spout comes away with the machine broken; one still brewing is lost. A machine moved
     * whole (by a piston, or as part of a contraption being assembled) keeps its drink in its state.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!movedByPiston && !newState.is(this) && state.getValue(BREW) == Brew.DONE) {
            popResource(level, pos, state.getValue(DRINK).cup());
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * A point given in pixels in the model drawn facing north, turned to face the way the machine does, in the level.
     */
    static Vec3 at(BlockPos pos, Direction facing, double x, double y, double z) {
        double u = x / 16 - 0.5;
        double w = z / 16 - 0.5;
        // Facing north is drawn as is; each quarter turn clockwise from there, seen from above, turns the point with it.
        int turns = (facing.get2DDataValue() + 2) % 4;
        for (int k = 0; k < turns; k++) {
            double turned = -w;
            w = u;
            u = turned;
        }
        return new Vec3(pos.getX() + 0.5 + u, pos.getY() + y / 16, pos.getZ() + 0.5 + w);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CoffeeMachineBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, AerolargeRegistry.COFFEE_MACHINE_ENTITY.get(),
            level.isClientSide ? CoffeeMachineBlockEntity::clientTick : CoffeeMachineBlockEntity::serverTick);
    }
}
