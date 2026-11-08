package dev.skyliners.floorwindow;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.Mount;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A block of a round floor window: a disc of glass set into a floor and walked over, under an iris that opens from its
 * middle out to show what lies below and closes again over it. Shut, the iris is the floor it took the place of, so
 * the window blends into the floor round it but for a thin steel ring round its edge; its edge blocks are only partly
 * glass, their corners outside the ring the floor still, so the window is round however square the floor's blocks.
 * Laid by hand it takes the largest size that fits the face it is set into, from nine blocks across down to two (see
 * {@link WindowDisc}), and goes into a wall as a round porthole or into a ceiling as a skylight as readily as into a
 * floor, its glass flush with the face it was set into. It is opened and shut by hand, from its panel (see {@link
 * FloorWindowPanelBlock}), or by a redstone pulse to the panel.
 *
 * <p>Each of its blocks keeps where the window's middle block is, how big it is and what floor it blends into (see
 * {@link FloorWindowBlockEntity}); the client's {@code FloorWindowRenderer} draws it all, as the block has no model of
 * its own. Laid as a whole from its item (see {@link FloorWindowItem}), and taken out as a whole: breaking any of its
 * blocks puts back the floor it took the place of everywhere and gives the window back. OPEN is whether its iris is
 * open; MOUNT whether it lies in a floor, a wall or a ceiling; FACING the way it was laid, so its offsets turn with it,
 * and in a wall the way it looks out.
 */
public class FloorWindowBlock extends BaseEntityBlock {
    private static final MapCodec<FloorWindowBlock> CODEC = simpleCodec(FloorWindowBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /**
     * What the window is set into: a floor, its glass looking up, as every window laid before walls; a wall, looking
     * out the way it faces; or a ceiling, looking down.
     */
    public static final EnumProperty<Mount> MOUNT = Mount.property(Mount.FLOOR, Mount.WALL, Mount.CEILING);
    /** How far out from the middle of a full-sized window its glass reaches, to the inside of its ring, in blocks. */
    public static final double RADIUS = WindowDisc.radius(WindowDisc.FULL);
    /** Every block of a full-sized window lying in a floor, as an offset from its middle block. */
    private static final List<BlockPos> FOOTPRINT = WindowDisc.blocks(BlockPos.ZERO, BlockPos.ZERO, WindowDisc.FULL, Direction.UP);
    /** How loud the iris is as it opens or shuts, and its pitches. */
    private static final float IRIS_VOLUME = 0.8F;
    private static final float IRIS_PITCH = 0.7F;
    private static final float HATCH_PITCH = 0.6F;

    public FloorWindowBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(OPEN, false).setValue(FACING, Direction.NORTH).setValue(MOUNT, Mount.FLOOR));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(OPEN, FACING, MOUNT);
    }

    /** The way a window's glass looks out of the face it was set into. */
    public static Direction out(BlockState state) {
        return switch (state.getValue(MOUNT)) {
            case FLOOR -> Direction.UP;
            case CEILING -> Direction.DOWN;
            case WALL -> state.getValue(FACING);
        };
    }

    /** Turned, as on a ship set down turned, it faces the turned way, and its block entity turns its offsets to match. */
    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    /** Drawn by its block entity's renderer alone. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    /**
     * Open, it lets the light through, as glass does; shut, it keeps it out as the floor it took the place of did, so a
     * room under it stays as dark as before.
     */
    @Override
    protected int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(OPEN) ? 0 : level.getMaxLightLevel();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(OPEN);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FloorWindowBlockEntity(pos, state);
    }

    /** Every block of a full-sized window lying in a floor, as an offset from its middle block, as her beach lays it. */
    public static List<BlockPos> footprint() {
        return FOOTPRINT;
    }

    /** The blocks of the window a block belongs to that stand now, each with its block entity, that block among them. */
    static List<FloorWindowBlockEntity> blocksOf(Level level, FloorWindowBlockEntity window) {
        BlockPos middle = window.middle();
        List<FloorWindowBlockEntity> found = new ArrayList<>();
        for (BlockPos at : window.disc()) {
            if (level.getBlockEntity(at) instanceof FloorWindowBlockEntity block && block.middle().equals(middle)) {
                found.add(block);
            }
        }
        return found;
    }

    /** Whether the window whose middle block is the one given is open: its middle block's iris. */
    static boolean isOpen(Level level, BlockPos middle) {
        BlockState state = level.getBlockState(middle);
        return state.getBlock() instanceof FloorWindowBlock && state.getValue(OPEN);
    }

    /**
     * Opens or shuts the window whose middle block is the one given, every block of it at once, with the iris's whirr;
     * nothing where its middle block is gone, as one blown out leaves no glass to open onto.
     */
    static void setOpen(ServerLevel level, BlockPos middle, boolean open) {
        if (!(level.getBlockEntity(middle) instanceof FloorWindowBlockEntity middleBlock)) {
            return;
        }
        for (FloorWindowBlockEntity window : blocksOf(level, middleBlock)) {
            BlockState state = window.getBlockState();
            if (state.getValue(OPEN) != open) {
                level.setBlock(window.getBlockPos(), state.setValue(OPEN, open), Block.UPDATE_CLIENTS);
            }
        }
        SoundEvent iris = open ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT;
        SoundEvent hatch = open ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE;
        level.playSound(null, middle, iris, SoundSource.BLOCKS, IRIS_VOLUME, IRIS_PITCH);
        level.playSound(null, middle, hatch, SoundSource.BLOCKS, IRIS_VOLUME, HATCH_PITCH);
    }

    /**
     * Used by hand, the window opens, or shuts if it is open, as its panel works it, every panel working it showing
     * which on its lamp.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && !player.isSpectator()
            && level.getBlockEntity(pos) instanceof FloorWindowBlockEntity window) {
            BlockPos middle = window.middle();
            boolean open = !isOpen(server, middle);
            setOpen(server, middle, open);
            FloorWindowPanelBlock.showFor(server, middle, open);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Broken by a player, the whole window comes out: every block of it gives back the floor it took the place of, and
     * the window drops as an item where its middle was, unless they play in creative.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof FloorWindowBlockEntity broken) {
            BlockPos middle = broken.middle();
            takeOut(server, broken, pos);
            if (!player.isCreative()) {
                popResource(server, middle.relative(out(state)), new ItemStack(SkylinersRegistry.FLOOR_WINDOW_ITEM.get()));
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Puts back the floor every block of a window took the place of, found from the block being broken, but for that
     * one, which its own breaking clears, and which is given its floor back just after.
     */
    private static void takeOut(ServerLevel level, FloorWindowBlockEntity broken, BlockPos breaking) {
        for (FloorWindowBlockEntity window : blocksOf(level, broken)) {
            BlockPos at = window.getBlockPos();
            BlockState floor = window.replaced();
            if (at.equals(breaking)) {
                putBackAfterBreak(level, at, floor);
            } else {
                level.setBlock(at, floor, Block.UPDATE_ALL);
            }
        }
    }

    /** Puts a floor back where a block is being broken, once the break has cleared it. */
    private static void putBackAfterBreak(ServerLevel level, BlockPos at, BlockState floor) {
        MinecraftServer server = level.getServer();
        server.tell(new TickTask(server.getTickCount(), () -> putBack(level, at, floor)));
    }

    /** Puts a floor back where a block was, as long as nothing has taken its place. */
    private static void putBack(ServerLevel level, BlockPos at, BlockState floor) {
        if (level.getBlockState(at).isAir()) {
            level.setBlock(at, floor, Block.UPDATE_ALL);
        }
    }
}
