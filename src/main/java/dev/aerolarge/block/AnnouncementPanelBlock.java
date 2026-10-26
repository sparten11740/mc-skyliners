package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The flight deck's announcement panel, {@link #COLUMNS} by {@link #ROWS} blocks (see {@link TiledPanelBlock}): on the
 * flight deck's back wall beside the door, facing the pilots, or where the wall leaves no room, hung from the ceiling
 * over the centre console with its face looking down at the pilots' seats; a tile in each block for one cabin
 * announcement (see {@link Announcement}), in the order of a flight. Each tile is a picture first, so that the
 * youngest pilots, who cannot read yet, know what it says; pressing it makes the announcement (see
 * {@link AnnouncementPanelBlockEntity}). Under a ceiling, REVERSED runs the rows the other way, the flight's first row
 * furthest from the viewer: on a flight deck where the panel reaches back behind the pilots' seats, that row hangs over
 * the seats, within the seated pilots' reach.
 */
public class AnnouncementPanelBlock extends TiledPanelBlock {
    private static final MapCodec<AnnouncementPanelBlock> CODEC = simpleCodec(AnnouncementPanelBlock::new);
    public static final BooleanProperty REVERSED = BooleanProperty.create("reversed");

    public AnnouncementPanelBlock(Properties properties) {
        super(properties, true);
        registerDefaultState(defaultBlockState().setValue(REVERSED, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(REVERSED);
    }

    /** The announcement a part of the panel shows. */
    public static Announcement announcement(BlockState state) {
        int part = state.getValue(PART);
        return Announcement.values()[state.getValue(REVERSED) ? flipped(part) : part];
    }

    /** The part of the panel that shows an announcement. */
    public static int part(Announcement announcement, BlockState state) {
        return state.getValue(REVERSED) ? flipped(announcement.ordinal()) : announcement.ordinal();
    }

    /** The place on the panel with the rows the other way round. */
    private static int flipped(int part) {
        return (ROWS - 1 - part / COLUMNS) * COLUMNS + part % COLUMNS;
    }

    /** Pressing a tile, on its face, asks for its announcement, on the server. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (hit.getDirection() != face(state)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(origin(state, pos)) instanceof AnnouncementPanelBlockEntity panel) {
            panel.press(announcement(state), serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new AnnouncementPanelBlockEntity(pos, state) : null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof AnnouncementPanelBlockEntity panel) {
                panel.tick();
            }
        };
    }
}
