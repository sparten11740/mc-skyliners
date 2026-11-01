package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.cabin.HighDive;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The giant rubber duck that rises out of an airliner's moon pool once a player has found all of its hidden ducks:
 * the rubber duck {@link #SCALE} times over, three blocks wide and tall, afloat with its body half under the water.
 * It is made of blocks, so it is part of the airliner like everything else in it, flies with it, is saved with it and
 * stays until the airliner is built again; an entity floating aboard a flying airliner would be left behind by it.
 * <p>
 * Each block is one PART of it, numbered by where it lies from the ORIGIN part, in the middle at the bottom of the
 * body, as the duck faces north; FACING is the way its beak points. Each holds its slice of the duck's boxes as its
 * shape, so it is solid where the duck is drawn, and the origin's renderer draws the whole duck from the rubber duck's
 * own model. Landing or standing on it bounces whoever it is straight back up with a squeak, like the pool's
 * springboard (see {@link SpringboardBlock}); sneak to stand on it. Used or hit, it squeaks; like a hidden duck it only
 * comes apart for a player who sneaks, and then breaking any part takes the whole duck away, giving back one duck.
 */
public class GiantRubberDuckBlock extends HorizontalDirectionalBlock implements SimpleWaterloggedBlock, EntityBlock {
    private static final MapCodec<GiantRubberDuckBlock> CODEC = simpleCodec(GiantRubberDuckBlock::new);
    /** How many times bigger than a rubber duck it is: each of the duck's pixels half a block. */
    public static final double SCALE = 8;
    /** How far its blocks reach from the origin's, as it faces north: either side, up, and fore and aft. */
    private static final int ACROSS = 1;
    private static final int UP = 2;
    private static final int ALONG = 2;
    private static final int PARTS = (2 * ACROSS + 1) * (UP + 1) * (2 * ALONG + 1);
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, PARTS - 1);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** The part the duck is drawn from and the others are found from. */
    public static final int ORIGIN = part(0, 0, 0);
    /** A rubber duck's block in pixels, and the middle of it, what the giant duck is drawn about. */
    private static final double PIXELS = 16;
    private static final double MIDDLE = 8;
    /** Each part's shape, by the way the duck faces; empty for a part the duck does not reach into. */
    private static final Map<Direction, VoxelShape[]> SHAPES = shapes();
    private static final List<Integer> SOLID = findSolidParts();
    /** How fast a bounce throws you up, in blocks a tick: the springboard's, about two and a half blocks high. */
    private static final double BOUNCE = 0.6;
    /** A bounce's squeak: the duck's own, deep. */
    private static final float BOUNCE_VOLUME = 1.0F;
    private static final float BOUNCE_PITCH = 0.55F;
    /** The splash at the feet on each bounce: how many drops and how far they spread. */
    private static final int SPLASHES = 10;
    private static final double SPLASH_SPREAD = 0.4;
    /** The hearts as it is squeaked by hand, and how far they spread. */
    private static final int HEARTS = 4;
    private static final double HEART_SPREAD = 0.4;

    public GiantRubberDuckBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, ORIGIN).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, WATERLOGGED);
    }

    /** The part a block is, from where it lies from the origin's as the duck faces north. */
    private static int part(int across, int up, int along) {
        return (up * (2 * ACROSS + 1) + across + ACROSS) * (2 * ALONG + 1) + along + ALONG;
    }

    /** Where a part lies from the origin's block, as the duck faces north: across, up and along. */
    private static int[] place(int part) {
        int along = part % (2 * ALONG + 1) - ALONG;
        int rest = part / (2 * ALONG + 1);
        return new int[]{rest % (2 * ACROSS + 1) - ACROSS, rest / (2 * ACROSS + 1), along};
    }

    /** Where a part's block is from the origin's, the duck facing the given way. */
    public static BlockPos offset(int part, Direction facing) {
        int[] at = place(part);
        double[] turned = RubberDuckBlock.turn(new double[]{at[0], at[1], at[2], at[0] + 1, at[1] + 1, at[2] + 1}, facing, 1);
        return new BlockPos((int) Math.round(turned[0]), at[1], (int) Math.round(turned[2]));
    }

    /** The parts the duck reaches into: the blocks it needs room for, the origin first. */
    public static List<Integer> solidParts() {
        return SOLID;
    }

    private static List<Integer> findSolidParts() {
        List<Integer> parts = new ArrayList<>();
        parts.add(ORIGIN);
        for (int part = 0; part < PARTS; part++) {
            if (part != ORIGIN && !SHAPES.get(Direction.NORTH)[part].isEmpty()) {
                parts.add(part);
            }
        }
        return Collections.unmodifiableList(parts);
    }

    /**
     * Each part's slice of the duck, facing each way: the rubber duck's boxes, made {@link #SCALE} times bigger about
     * the middle of its base, which sits at the middle of the bottom of the origin's block, cut to the part's block.
     */
    private static Map<Direction, VoxelShape[]> shapes() {
        Map<Direction, VoxelShape[]> shapes = new EnumMap<>(Direction.class);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape[] parts = new VoxelShape[PARTS];
            for (int part = 0; part < PARTS; part++) {
                int[] at = place(part);
                VoxelShape shape = Shapes.empty();
                for (double[] box : RubberDuckBlock.BOXES) {
                    double[] big = new double[6];
                    for (int axis = 0; axis < 6; axis += 3) {
                        big[axis] = (box[axis] - MIDDLE) * SCALE / PIXELS + 0.5 - at[0];
                        big[axis + 1] = box[axis + 1] * SCALE / PIXELS - at[1];
                        big[axis + 2] = (box[axis + 2] - MIDDLE) * SCALE / PIXELS + 0.5 - at[2];
                    }
                    double[] cut = {Math.max(big[0], 0), Math.max(big[1], 0), Math.max(big[2], 0), Math.min(big[3], 1), Math.min(big[4], 1), Math.min(big[5], 1)};
                    if (cut[0] < cut[3] && cut[1] < cut[4] && cut[2] < cut[5]) {
                        double[] turned = RubberDuckBlock.turn(cut, facing, 1);
                        shape = Shapes.or(shape, Shapes.box(turned[0], turned[1], turned[2], turned[3], turned[4], turned[5]));
                    }
                }
                parts[part] = shape.optimize();
            }
            shapes.put(facing, parts);
        }
        return shapes;
    }

    /** The origin's block of the duck a part belongs to. */
    private static BlockPos origin(BlockPos pos, BlockState state) {
        return pos.subtract(offset(state.getValue(PART), state.getValue(FACING)));
    }

    /** Whether a part of a giant duck can go into a block of the world: open space, or still water to float in. */
    private static boolean roomFor(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) && state.getFluidState().isSource();
    }

    private BlockState partAt(Level level, BlockPos pos, int part, Direction facing) {
        return defaultBlockState().setValue(FACING, facing).setValue(PART, part).setValue(WATERLOGGED, level.getBlockState(pos).is(Blocks.WATER));
    }

    /**
     * Raises a giant duck with its origin at the spot, facing the given way, for the player who found the last duck:
     * its origin goes in now, out of which it rises, and the rest of it once it has risen (see
     * {@link GiantRubberDuckBlockEntity}). Where one is already afloat, it only squeaks again with its fanfare; where
     * the spot has been built over, there is the squeak and the fanfare with no duck.
     */
    public static void raise(ServerLevel level, BlockPos origin, Direction facing, ServerPlayer finder) {
        BlockState there = level.getBlockState(origin);
        if (there.getBlock() instanceof GiantRubberDuckBlock && there.getValue(PART) == ORIGIN || !roomFor(there)) {
            GiantRubberDuckBlockEntity.celebrate(level, Vec3.atBottomCenterOf(origin), finder);
            return;
        }
        GiantRubberDuckBlock block = SkylinersRegistry.GIANT_RUBBER_DUCK.get();
        level.setBlock(origin, block.partAt(level, origin, ORIGIN, facing), Block.UPDATE_ALL);
        if (level.getBlockEntity(origin) instanceof GiantRubberDuckBlockEntity giant) {
            giant.rise(finder);
        }
    }

    /** The rest of the duck around its origin, once it has risen, wherever there is room for it. */
    static void fill(Level level, BlockPos origin, Direction facing) {
        GiantRubberDuckBlock block = SkylinersRegistry.GIANT_RUBBER_DUCK.get();
        for (int part : SOLID) {
            BlockPos pos = origin.offset(offset(part, facing));
            if (part != ORIGIN && roomFor(level.getBlockState(pos))) {
                level.setBlock(pos, block.partAt(level, pos, part, facing), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * Put down by hand, it looks at whoever puts it there, if there is room for all of it. Only its origin goes in now:
     * its block entity finds it already risen and puts the rest in round it on its next tick, squeak, fanfare and all.
     */
    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos origin = context.getClickedPos();
        Level level = context.getLevel();
        for (int part : SOLID) {
            BlockPos at = origin.offset(offset(part, facing));
            if (level.isOutsideBuildHeight(at) || !roomFor(level.getBlockState(at))) {
                return null;
            }
        }
        return partAt(level, origin, ORIGIN, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING))[state.getValue(PART)];
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    /**
     * Gone in part, gone whole: the rest of its parts go too, each back to the water or the air it took the place of.
     * Not as the airliner is assembled, which moves every part on its own.
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        super.onRemove(state, level, pos, newState, isMoving);
        if (isMoving || newState.is(this)) {
            return;
        }
        BlockPos origin = origin(pos, state);
        Direction facing = state.getValue(FACING);
        for (int part : SOLID) {
            BlockPos other = origin.offset(offset(part, facing));
            BlockState found = level.getBlockState(other);
            if (found.is(this) && found.getValue(PART) == part && found.getValue(FACING) == facing) {
                level.setBlock(other, found.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        squeak(level, pos);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        squeak(level, pos);
    }

    /** It stays put for anyone not sneaking, as a hidden duck does, so a child's stray click cannot take it away. */
    @Override
    protected float getDestroyProgress(BlockState state, Player player, BlockGetter level, BlockPos pos) {
        return player.isShiftKeyDown() ? super.getDestroyProgress(state, player, level, pos) : 0;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        return player.isShiftKeyDown() ? super.playerWillDestroy(level, pos, state, player) : state;
    }

    /** Creative mode breaks a block outright: for anyone not sneaking, it squeaks instead. */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (!player.isShiftKeyDown()) {
            squeak(level, pos);
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    /** Broken by someone not in creative mode, the whole of it gives back one duck, whichever part they broke. */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        popResource(level, pos, new ItemStack(this));
    }

    /** Its deep squeak, used or hit, with a few hearts over the part of it touched. */
    private static void squeak(Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            server.playSound(null, pos, SkylinersRegistry.RUBBER_DUCK_SQUEAK.get(), SoundSource.BLOCKS, BOUNCE_VOLUME, BOUNCE_PITCH);
            server.sendParticles(ParticleTypes.HEART, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, HEARTS, HEART_SPREAD, HEART_SPREAD, HEART_SPREAD, 0);
        }
    }

    /** Landing on it never hurts; a player's bounce is heard and seen by everyone else once the server hears of it. */
    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        entity.causeFallDamage(fallDistance, 0.0F, level.damageSources().fall());
        if (entity instanceof ServerPlayer player && !player.isSuppressingBounce()) {
            bounceSqueak(player.serverLevel(), player);
            HighDive.bounced(player);
        }
    }

    /** Throws whatever lands or stands on it straight back up, as the springboard does; the bouncer's own client plays its squeak. */
    @Override
    public void updateEntityAfterFallOn(BlockGetter getter, Entity entity) {
        Vec3 motion = entity.getDeltaMovement();
        if (entity.isSuppressingBounce() || motion.y >= 0) {
            super.updateEntityAfterFallOn(getter, entity);
            return;
        }
        entity.setDeltaMovement(motion.x, BOUNCE, motion.z);
        Level level = entity.level();
        if (level.isClientSide && entity instanceof Player player) {
            level.playSound(player, entity.getX(), entity.getY(), entity.getZ(), SkylinersRegistry.RUBBER_DUCK_SQUEAK.get(), SoundSource.BLOCKS,
                BOUNCE_VOLUME, BOUNCE_PITCH);
            for (int i = 0; i < SPLASHES; i++) {
                level.addParticle(ParticleTypes.SPLASH, entity.getX() + spread(level), entity.getY(), entity.getZ() + spread(level), 0, 0, 0);
            }
        } else if (level instanceof ServerLevel server && !(entity instanceof Player)) {
            bounceSqueak(server, entity);
        }
    }

    /** A bounce's deep squeak and a splash of drops at the bouncer's feet, for everyone but a bouncing player, who has had them. */
    private static void bounceSqueak(ServerLevel level, Entity entity) {
        Player except = entity instanceof Player player ? player : null;
        level.playSound(except, entity.getX(), entity.getY(), entity.getZ(), SkylinersRegistry.RUBBER_DUCK_SQUEAK.get(), SoundSource.BLOCKS,
            BOUNCE_VOLUME, BOUNCE_PITCH);
        for (ServerPlayer watcher : level.players()) {
            if (watcher != except) {
                level.sendParticles(watcher, ParticleTypes.SPLASH, false, entity.getX(), entity.getY(), entity.getZ(),
                    SPLASHES, SPLASH_SPREAD, 0.1, SPLASH_SPREAD, 0);
            }
        }
    }

    private static double spread(Level level) {
        return (level.random.nextDouble() * 2 - 1) * SPLASH_SPREAD;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == ORIGIN ? new GiantRubberDuckBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != SkylinersRegistry.GIANT_RUBBER_DUCK_ENTITY.get()) {
            return null;
        }
        return level.isClientSide
            ? (tickLevel, pos, tickState, giant) -> ((GiantRubberDuckBlockEntity) giant).clientTick()
            : (tickLevel, pos, tickState, giant) -> ((GiantRubberDuckBlockEntity) giant).serverTick();
    }
}
