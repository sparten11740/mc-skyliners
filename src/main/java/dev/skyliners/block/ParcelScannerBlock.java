package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The cargo bay's parcel scanner: a graphite cabinet as high as a table, a conveyor belt for its top and a gantry over
 * the belt, its screen on the front showing a crate. Set a care-package crate on the belt, or use the scanner, and it
 * scans whatever is on it (SCAN): its lamps down the gantry's posts turn red and a red curtain of laser light fills the
 * gantry with a rising whirr while a line sweeps down the crate on its screen; then, if a crate is on it with something
 * packed in it, a bright double beep, a green tick and green lamps, ready to drop, or else a low buzz, a red cross and
 * red lamps blinking. A few seconds later it is ready again. FACING is the way its screen faces, the belt running
 * across in front of whoever reads it.
 */
public class ParcelScannerBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<ParcelScannerBlock> CODEC = simpleCodec(ParcelScannerBlock::new);
    public static final EnumProperty<Scan> SCAN = EnumProperty.create("scan", Scan.class);
    /** How long a scan takes and how long its verdict shows before the scanner is ready again, in ticks. */
    private static final int SCAN_TICKS = 30;
    private static final int VERDICT_TICKS = 60;
    /** How loud its click is when it is used while busy. */
    private static final float BUSY_VOLUME = 0.4F;
    /** How bright its lamp and screen glow, ready and otherwise, in light levels. */
    private static final int READY_GLOW = 3;
    private static final int BUSY_GLOW = 7;

    /** Where a scan stands. */
    public enum Scan implements StringRepresentable {
        READY,
        SCANNING,
        PACKED,
        EMPTY;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public ParcelScannerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SCAN, Scan.READY));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SCAN);
    }

    /** How bright its lamp and screen glow: dim when ready, brighter as it scans and shows its verdict. */
    public static int glow(BlockState state) {
        return state.getValue(SCAN) == Scan.READY ? READY_GLOW : BUSY_GLOW;
    }

    /**
     * Scans what is on its belt when ready; while busy, only a click, or its next step at once where that was lost, as
     * when the rocket was assembled in the middle of a scan.
     */
    /** Put down by hand, it faces whoever puts it there. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || player.isSpectator()) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (state.getValue(SCAN) == Scan.READY) {
            scan(server, pos, state);
            return InteractionResult.SUCCESS;
        }
        server.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, BUSY_VOLUME, 1.0F);
        if (!server.getBlockTicks().hasScheduledTick(pos, this)) {
            server.scheduleTick(pos, this, 1);
        }
        return InteractionResult.SUCCESS;
    }

    /** A care-package crate set down on its belt is scanned at once, if the scanner is ready. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbour, BlockPos from, boolean moving) {
        super.neighborChanged(state, level, pos, neighbour, from, moving);
        boolean crateOn = from.equals(pos.above()) && level.getBlockState(from).getBlock() instanceof CargoCrateBlock;
        if (level instanceof ServerLevel server && crateOn && state.getValue(SCAN) == Scan.READY) {
            scan(server, pos, state);
        }
    }

    /** Starts a scan: the laser curtain and the sweep on its screen, with a rising whirr. */
    private void scan(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(SCAN, Scan.SCANNING), Block.UPDATE_ALL);
        level.playSound(null, pos, SkylinersRegistry.SCANNER_SWEEP.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.scheduleTick(pos, this, SCAN_TICKS);
    }

    /** Scanned, its verdict on what is on its belt, with its beep or its buzz; shown a while, it is ready again. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(SCAN) != Scan.SCANNING) {
            level.setBlock(pos, state.setValue(SCAN, Scan.READY), Block.UPDATE_ALL);
            return;
        }
        boolean packed = level.getBlockEntity(pos.above()) instanceof CargoCrateBlockEntity crate && !crate.isEmpty();
        level.setBlock(pos, state.setValue(SCAN, packed ? Scan.PACKED : Scan.EMPTY), Block.UPDATE_ALL);
        level.playSound(null, pos, (packed ? SkylinersRegistry.SCANNER_PASS : SkylinersRegistry.SCANNER_FAIL).get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        level.scheduleTick(pos, this, VERDICT_TICKS);
    }
}
