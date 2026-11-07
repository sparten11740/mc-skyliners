package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The panel at the head of the freighter's boarding ramp, on the wall beside its well, which keeps the ramp and works
 * it (see {@link RampPanelBlockEntity}): a little touch screen of two picture buttons under a lamp, as the rocket's
 * rover bay panel is, drawn live by its renderer along with the ramp. The first, a ramp over an arrow pointing down,
 * lets the ramp down; the second, a ramp under an arrow pointing up, brings it back up. The lamp says how the ramp
 * stands, in colour: green with it up and the hatch shut, amber while it moves or waits for someone to step off, blue
 * with it down, red for a moment when it will not move, something standing in its way or she flying. FACING is the
 * way its face looks, its back to the wall.
 */
public class RampPanelBlock extends HorizontalDirectionalBlock implements EntityBlock, TouchPanelBlock {
    private static final MapCodec<RampPanelBlock> CODEC = simpleCodec(RampPanelBlock::new);

    /** How the ramp stands, as its lamp shows it. */
    public enum Status implements StringRepresentable {
        READY, BUSY, DOWN, REFUSED;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Its buttons, left to right as seen from the front. */
    public enum Button {
        LOWER, RAISE
    }

    public static final EnumProperty<Status> STATUS = EnumProperty.create("status", Status.class);
    /** How thick the panel is, in sixteenths of a block. */
    public static final int THICKNESS = 2;
    /** How far in from the face's edges the buttons are, and how much of its height the lamp takes at the top. */
    private static final double MARGIN = 1 / 16.0;
    private static final double LAMP = 0.25;
    /** Where the lamp's bottom edge is on the face, over the buttons, in blocks up from its foot. */
    private static final double LAMP_FOOT = 1 - MARGIN - LAMP * (1 - 2 * MARGIN);
    /** The lamp across the top of the face, and its one big button under it (see {@link TouchPanel}). */
    public static final TouchPanel.Tile LAMP_TILE = new TouchPanel.Tile(MARGIN, LAMP_FOOT, 1 - MARGIN, 1 - MARGIN);
    public static final TouchPanel.Layout BUTTONS = TouchPanel.Layout.row(1, new TouchPanel.Tile(MARGIN, MARGIN, 1 - MARGIN, LAMP_FOOT));
    /** What its button does, shown on it: lets the ramp down while it is up, and brings it up while it is down. */
    public static final TouchPanel.Toggle<Button> ACTION = new TouchPanel.Toggle<>(Button.LOWER, Button.RAISE);
    /** How bright its lamp glows, as block light, by how the ramp stands. */
    private static final Map<Status, Integer> GLOW = Map.of(Status.READY, 4, Status.BUSY, 6, Status.DOWN, 5, Status.REFUSED, 7);
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
        Direction.NORTH, Block.box(0, 0, 16 - THICKNESS, 16, 16, 16),
        Direction.SOUTH, Block.box(0, 0, 0, 16, 16, THICKNESS),
        Direction.WEST, Block.box(16 - THICKNESS, 0, 0, 16, 16, 16),
        Direction.EAST, Block.box(0, 0, 0, THICKNESS, 16, 16)
    );

    public RampPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATUS, Status.READY));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATUS);
    }

    /** How bright a panel glows, as block light. */
    public static int glow(BlockState state) {
        return GLOW.get(state.getValue(STATUS));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RampPanelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != SkylinersRegistry.RAMP_PANEL_ENTITY.get()) {
            return null;
        }
        return level.isClientSide ? (tickLevel, pos, tickState, panel) -> ((RampPanelBlockEntity) panel).clientTick()
            : (tickLevel, pos, tickState, panel) -> ((RampPanelBlockEntity) panel).serverTick((ServerLevel) tickLevel);
    }

    /** A button pressed, on the server: the ramp it keeps goes down or up, or the lamp turns red for a moment. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int tile = tileAt(level, state, pos, hit, player.getLookAngle());
        if (tile < 0) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && level.getBlockEntity(pos) instanceof RampPanelBlockEntity panel) {
            panel.pressButton(server);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Whether a hit on the panel's face lands on its button: 0 on it, -1 on its lamp, edge or back. */
    @Override
    public int tileAt(BlockGetter level, BlockState state, BlockPos pos, BlockHitResult hit, Vec3 look) {
        return BUTTONS.at(TouchPanel.touched(state.getValue(FACING), pos, hit));
    }

    /** Picked, nothing: the panel only goes up with her ramp. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }
}
