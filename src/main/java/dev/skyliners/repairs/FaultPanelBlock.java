package dev.skyliners.repairs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * One of the freighter's fault spots, for her running repairs: the Fault Panel, a steel junction box on the wall, or
 * the Leaky Pipe, a pipe along it, each with a small warning lamp. While she flies, now and then one of them goes
 * FAULTY (see {@link RepairBoardBlockEntity}): its lamp flashes red, the panel spits sparks and crackles and the pipe
 * jets steam and hisses, loud enough to follow by ear from across her deck, and her console's scanner shows a red
 * warning sign at its bearing. Nothing is damaged and nobody is hurt; left alone it only goes on sparking. Whack it
 * with the Spanner, using it, and it is fixed with a clank, a burst of sparks and a chime, and the nearest repair board
 * lights a star; an empty hand, or anything but a block to place, fixes it too, with a thump, so no child is ever stuck
 * without a spanner. Put up by hand anywhere, a redstone signal coming on makes it go faulty, to fix the same way.
 * FACING is the way it faces out from the wall.
 */
public class FaultPanelBlock extends HorizontalDirectionalBlock implements EntityBlock {
    /** What sort of fault spot it is, which says how it shows a fault: sparks and a crackle, or steam and a hiss. */
    public enum Kind implements StringRepresentable {
        PANEL, PIPE;

        static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final MapCodec<FaultPanelBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
        .group(Kind.CODEC.fieldOf("kind").forGetter(FaultPanelBlock::kind), propertiesCodec()).apply(instance, FaultPanelBlock::new));
    /** Whether it is at fault now, flashing, sparking or steaming until fixed. */
    public static final BooleanProperty FAULTY = BooleanProperty.create("faulty");
    /** Whether a redstone signal reaches it, so only a signal coming on sets it off. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** How bright its flashing lamp lights the wall round it at fault, out of 15. */
    public static final int FAULT_GLOW = 6;
    /** How loud its clank and thump, the chime of a fix, and the tap on one with nothing wrong are. */
    private static final float CLANK_VOLUME = 0.9F;
    private static final float CHIME_VOLUME = 1.0F;
    private static final float TAP_VOLUME = 0.5F;
    /** How many sparks fly from a fix, and how fast, in blocks a tick. */
    private static final int FIX_SPARKS = 14;
    private static final double FIX_SPARK_SPEED = 0.15;
    /**
     * Where its sparks or steam come from, so far out from the wall, in shares of its block, and so high; how fast the
     * steam jets out, in blocks a tick; and how many sparks or puffs a frame shows at most.
     */
    private static final double MOUTH_OUT = 0.3;
    private static final double PANEL_MOUTH_UP = 0.55;
    private static final double PIPE_MOUTH_UP = 0.5;
    private static final double STEAM_OUT = 0.12;
    private static final int MOST_PUFFS = 3;
    /** How often a spot at fault sprays its sparks or steam on a client, in ticks. */
    private static final int SPRAY_EVERY = 3;
    /** Its box or pipe against the wall, to bump into, by the way it faces. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.NORTH, Block.box(0, 3, 11, 16, 16, 16));
        SHAPES.put(Direction.SOUTH, Block.box(0, 3, 0, 16, 16, 5));
        SHAPES.put(Direction.WEST, Block.box(11, 3, 0, 16, 16, 16));
        SHAPES.put(Direction.EAST, Block.box(0, 3, 0, 5, 16, 16));
    }

    private final Kind kind;

    public FaultPanelBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FAULTY, false).setValue(POWERED, false));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FAULTY, POWERED);
    }

    /** Against the wall it is put on, facing out from it; put on a floor or a ceiling, facing whoever puts it there. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction side = context.getClickedFace();
        Direction facing = side.getAxis().isHorizontal() ? side : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing).setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /**
     * With the spanner in hand: fixed with a clank. A block in hand is placed against it as against any block; anything
     * else in hand thumps it as an empty hand does.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (stack.getItem() instanceof BlockItem) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!stack.is(SkylinersRegistry.SPANNER.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel server && !player.isSpectator()) {
            whacked(server, pos, state, player, true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** An empty hand thumps it, which fixes it as well, if not as grandly. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && !player.isSpectator()) {
            whacked(server, pos, state, player, false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Struck, on the server: at fault, it is fixed, with the spanner's clank or the hand's thump, a burst of sparks and
     * a chime, and the nearest repair board told; with nothing wrong, only a light tap.
     */
    private void whacked(ServerLevel level, BlockPos pos, BlockState state, Player player, boolean spanner) {
        player.swing(InteractionHand.MAIN_HAND, true);
        if (!state.getValue(FAULTY)) {
            level.playSound(null, pos, spanner ? SoundEvents.CHAIN_HIT : SoundEvents.WOOD_HIT, SoundSource.BLOCKS, TAP_VOLUME, 1.4F);
            return;
        }
        level.setBlock(pos, state.setValue(FAULTY, false), Block.UPDATE_ALL);
        if (spanner) {
            level.playSound(null, pos, SoundEvents.ANVIL_PLACE, SoundSource.BLOCKS, CLANK_VOLUME * 0.6F, 1.7F);
            level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, CLANK_VOLUME, 0.8F);
        } else {
            level.playSound(null, pos, SkylinersRegistry.JUMP_DRIVE_THUMP.get(), SoundSource.BLOCKS, CLANK_VOLUME, 1.3F);
        }
        level.playSound(null, pos, SkylinersRegistry.CENTRIFUGE_DING.get(), SoundSource.BLOCKS, CHIME_VOLUME, 1.2F);
        Vec3 at = SableFlight.toWorld(level, mouth(pos, state.getValue(FACING), PANEL_MOUTH_UP));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, FIX_SPARKS, 0.15, 0.15, 0.15, FIX_SPARK_SPEED);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y, at.z, FIX_SPARKS / 2, 0.3, 0.3, 0.3, 0);
        RepairBoardBlockEntity.repaired(level, pos);
    }

    /**
     * On the server: a redstone signal coming on sets it off, as her fault does in flight; switched off, the server
     * config's {@code freighter.repairs} keeps it as it is.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos from, boolean moving) {
        boolean powered = level.hasNeighborSignal(pos);
        if (level.isClientSide || powered == state.getValue(POWERED)) {
            return;
        }
        boolean breaks = powered && !state.getValue(FAULTY) && SkylinersConfig.FREIGHTER_REPAIRS.get();
        level.setBlock(pos, state.setValue(POWERED, powered).setValue(FAULTY, state.getValue(FAULTY) || breaks), Block.UPDATE_ALL);
        if (breaks) {
            FaultPanelBlockEntity.brokeDown((ServerLevel) level, pos, kind);
        }
    }

    /**
     * On the client, at fault, every {@link #SPRAY_EVERY} ticks: sparks spitting from the panel, smoke curling now and
     * then, or steam jetting from the pipe's joint, where its block is in the world as she flies. The game's own
     * {@code animateTick} comes to any one block too seldom for a steady stream.
     */
    private void spray(BlockState state, Level level, BlockPos pos) {
        if ((level.getGameTime() + pos.asLong()) % SPRAY_EVERY != 0) {
            return;
        }
        RandomSource random = level.getRandom();
        Direction facing = state.getValue(FACING);
        Vec3 mouth = mouth(pos, facing, kind == Kind.PANEL ? PANEL_MOUTH_UP : PIPE_MOUTH_UP);
        Vec3 at = SableFlight.toWorld(level, mouth);
        Vec3 out = SableFlight.toWorld(level, mouth.add(Vec3.atLowerCornerOf(facing.getNormal()))).subtract(at);
        for (int puff = 0; puff < 1 + random.nextInt(MOST_PUFFS); puff++) {
            if (kind == Kind.PANEL) {
                spark(level, random, at, random.nextInt(4) == 0 ? ParticleTypes.SMOKE : ParticleTypes.ELECTRIC_SPARK);
            } else {
                level.addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z, out.x * STEAM_OUT + random.nextGaussian() * 0.02,
                    out.y * STEAM_OUT + 0.02 + random.nextDouble() * 0.04, out.z * STEAM_OUT + random.nextGaussian() * 0.02);
            }
        }
        if (kind == Kind.PANEL && random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.LAVA, at.x, at.y, at.z, 0, 0, 0);
        }
    }

    /** A spark or a wisp of smoke flying off in some direction out from the wall. */
    private static void spark(Level level, RandomSource random, Vec3 at, ParticleOptions particle) {
        level.addParticle(particle, at.x, at.y, at.z, random.nextGaussian() * 0.1, random.nextDouble() * 0.12, random.nextGaussian() * 0.1);
    }

    /** Where its sparks or steam come from: so far out from the wall before it and so high, in plot coordinates. */
    private static Vec3 mouth(BlockPos pos, Direction facing, double up) {
        return Vec3.atBottomCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getNormal()).scale(MOUTH_OUT - 0.5)).add(0, up, 0);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FaultPanelBlockEntity(pos, state);
    }

    /**
     * Only at fault: on the server its crackle or hiss at intervals (see {@link FaultPanelBlockEntity#tick}), and on a
     * client its sparks or steam (see {@link #spray}).
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!state.getValue(FAULTY)) {
            return null;
        }
        if (level.isClientSide) {
            return (tickLevel, pos, tickState, blockEntity) -> spray(tickState, tickLevel, pos);
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof FaultPanelBlockEntity panel) {
                panel.tick((ServerLevel) tickLevel, kind);
            }
        };
    }
}
