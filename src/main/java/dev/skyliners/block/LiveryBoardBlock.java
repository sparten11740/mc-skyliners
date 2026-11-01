package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.airframe.Livery;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The livery board in a zeppelin's control car: a wooden board painted with six swatches, one for each of the
 * zeppelin's liveries (see {@link Livery}), in two rows of three: silver, cream and navy grey over the airliner's
 * colours, polar red and midnight blue. Use a swatch and the zeppelin is repainted in it, the new paint sweeping from
 * its nose to its tail behind a painters' cradle, with the scrape of a brush. It is a touch panel (see
 * {@link TouchPanel}), its swatches its tiles, lit over their paint by the client's {@code LiveryBoardRenderer}: the
 * one the zeppelin wears marked as picked, the one looked at lit up. Its zeppelin is the one whose autopilot is
 * nearest, on its flight deck. FACING is the way its painted face looks.
 */
public class LiveryBoardBlock extends HorizontalDirectionalBlock implements EntityBlock, TouchPanelBlock {
    private static final MapCodec<LiveryBoardBlock> CODEC = simpleCodec(LiveryBoardBlock::new);
    /**
     * Where the swatches are painted on the board's face, in its texture's sixteen pixels to a block: their columns'
     * left and right edges, and their rows' bottom and top edges, the top row first.
     */
    private static final double[][] SWATCH_COLUMNS = {{1, 5}, {6, 10}, {11, 15}};
    private static final double[][] SWATCH_ROWS = {{9, 14}, {2, 7}};
    private static final double PIXELS = 16;
    /**
     * The swatches as the tiles of a touch panel (see {@link TouchPanel}), in the order of the zeppelin's liveries'
     * swatches (see {@link Livery#swatch}).
     */
    public static final TouchPanel.Layout SWATCH_TILES = TouchPanel.Layout.crossings(SWATCH_COLUMNS, SWATCH_ROWS, PIXELS);
    /** How thick the board is, in sixteenths of a block, standing at the back of its block. */
    public static final int THICKNESS = 3;
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
        Direction.NORTH, Block.box(0, 0, 16 - THICKNESS, 16, 16, 16),
        Direction.SOUTH, Block.box(0, 0, 0, 16, 16, THICKNESS),
        Direction.WEST, Block.box(16 - THICKNESS, 0, 0, 16, 16, 16),
        Direction.EAST, Block.box(0, 0, 0, THICKNESS, 16, 16)
    );

    public LiveryBoardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Put down by hand, its face looks at whoever puts it down. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (hit.getDirection() != state.getValue(FACING)) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            repaint(server, pos, player, Livery.swatch(Craft.ZEPPELIN, tileAt(level, state, pos, hit, player.getLookAngle())));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * A livery picked on a board or a selector at a place, on the server: its zeppelin is repainted in it, with the
     * scrape of a brush and the livery's name; nothing for none picked, and a word if no zeppelin is about to paint.
     */
    public static void repaint(ServerLevel server, BlockPos pos, Player player, @Nullable Livery livery) {
        AutopilotBlockEntity autopilot = painted(server, pos);
        if (livery != null && autopilot != null) {
            autopilot.repaint(livery);
            server.playSound(null, pos, SoundEvents.BRUSH_GENERIC, SoundSource.BLOCKS, 1.0F, 0.8F);
            String repainting = autopilot.craft() == Craft.ROCKET ? "repainting_rocket" : "repainting";
            player.displayClientMessage(Component.translatable("message.skyliners.livery_board." + repainting,
                Component.translatable("livery.skyliners." + livery.name().toLowerCase(Locale.ROOT))), true);
        } else if (autopilot == null) {
            player.displayClientMessage(Component.translatable("message.skyliners.livery_board.no_zeppelin"), true);
        }
    }

    /** The swatch a hit on the board's face lands on, by its place in {@link #SWATCH_TILES}; -1 off them. */
    @Override
    public int tileAt(BlockGetter level, BlockState state, BlockPos pos, BlockHitResult hit, Vec3 look) {
        return SWATCH_TILES.at(TouchPanel.touched(state.getValue(FACING), pos, hit));
    }

    /**
     * The zeppelin or rocket a board or a selector paints, by its autopilot: the one whose autopilot is nearest it, in
     * her control car or cockpit; null for none, or for an airliner's.
     */
    @Nullable
    public static AutopilotBlockEntity painted(Level level, BlockPos pos) {
        AutopilotBlockEntity autopilot = AutopilotBlockEntity.nearest(level, pos);
        return autopilot != null && autopilot.craft() != Craft.AIRLINER && autopilot.frame() != null ? autopilot : null;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LiveryBoardBlockEntity(pos, state);
    }
}
