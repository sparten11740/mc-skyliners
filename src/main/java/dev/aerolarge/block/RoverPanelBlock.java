package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The panel that works the rocket's rover bay (see {@link RoverBayBlockEntity}), on the lift shaft's back wall facing
 * the bay's door: a little screen of three picture buttons under a lamp, drawn live by its block entity's renderer,
 * which lights up the button looked at in a bold white frame (one built before it had a block entity shows the same
 * pictures painted on, unlit). The first, the rover over an arrow pointing down, opens the
 * door and lowers it to the ground; the second, the rover under an arrow pointing up, calls it home and hoists it
 * aboard; the third, a remote, hands whoever presses it a remote paired with the rover. A remote used on it is paired
 * with the rover too. The lamp says how the bay stands, in colour: green with the rover in its cradle and the door
 * shut, amber while the door or the winch works, blue with the rover out, red for a moment when the door will not
 * open, she not standing still on her fins. FACING is the way its face looks, its back to the wall.
 */
public class RoverPanelBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<RoverPanelBlock> CODEC = simpleCodec(RoverPanelBlock::new);

    /** How the bay stands, as its lamp shows it. */
    public enum Status implements StringRepresentable {
        READY, BUSY, OUT, REFUSED;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Its buttons, left to right as seen from the front. */
    public enum Button {
        DEPLOY, HOME, REMOTE
    }

    public static final EnumProperty<Status> STATUS = EnumProperty.create("status", Status.class);
    /** How thick the panel is, in sixteenths of a block. */
    public static final int THICKNESS = 2;
    /** How far in from the face's edges the buttons are, and how much of its height the lamp takes at the top. */
    private static final double MARGIN = 1 / 16.0;
    private static final double LAMP = 0.25;
    /** How bright its lamp glows, as block light, by how the bay stands. */
    private static final Map<Status, Integer> GLOW = Map.of(Status.READY, 4, Status.BUSY, 6, Status.OUT, 5, Status.REFUSED, 7);
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
        Direction.NORTH, Block.box(0, 0, 16 - THICKNESS, 16, 16, 16),
        Direction.SOUTH, Block.box(0, 0, 0, 16, 16, THICKNESS),
        Direction.WEST, Block.box(16 - THICKNESS, 0, 0, 16, 16, 16),
        Direction.EAST, Block.box(0, 0, 0, THICKNESS, 16, 16)
    );

    public RoverPanelBlock(Properties properties) {
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
        return new RoverPanelBlockEntity(pos, state);
    }

    /** A button pressed, on the server: worked by the bay the panel is wired to, which says what it does. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Direction facing = state.getValue(FACING);
        Button button = hit.getDirection() == facing ? button(facing, hit.getLocation().subtract(Vec3.atCenterOf(pos))) : null;
        if (button == null) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            RoverBayBlockEntity bay = RoverBayBlockEntity.wiredTo(level, pos);
            if (bay != null) {
                bay.press(server, button);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** A remote used on it is paired with the bay's rover, whichever button it touches. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!stack.is(AerolargeRegistry.ROVER_REMOTE.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (player instanceof ServerPlayer server) {
            RoverBayBlockEntity bay = RoverBayBlockEntity.wiredTo(level, pos);
            if (bay != null) {
                bay.pair(stack, server);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Where a button is on the panel's face, as its renderer draws it and a touch finds it: across from its left edge
     * and up from its foot as seen from the front, in blocks.
     */
    public record Area(double left, double bottom, double right, double top) {
    }

    /** Where a button is on the face. */
    public static Area area(Button button) {
        double width = (1 - 2 * MARGIN) / Button.values().length;
        double left = MARGIN + width * button.ordinal();
        return new Area(left, MARGIN, left + width, 1 - MARGIN - LAMP * (1 - 2 * MARGIN));
    }

    /** Where the lamp is on the face, over the buttons. */
    public static Area lamp() {
        return new Area(MARGIN, 1 - MARGIN - LAMP * (1 - 2 * MARGIN), 1 - MARGIN, 1 - MARGIN);
    }

    /**
     * The button at a place on the panel's face, from the middle of its block; null on its lamp or at its edge. Seen
     * from the front, the viewer's right is the panel's counterclockwise side.
     */
    @Nullable
    public static Button button(Direction facing, Vec3 touch) {
        Direction right = facing.getCounterClockWise();
        double across = (touch.x * right.getStepX() + touch.z * right.getStepZ() + 0.5 - MARGIN) / (1 - 2 * MARGIN);
        double down = (0.5 - touch.y - MARGIN) / (1 - 2 * MARGIN);
        Button[] buttons = Button.values();
        int column = (int) Math.floor(across * buttons.length);
        return down >= LAMP && down <= 1 && column >= 0 && column < buttons.length ? buttons[column] : null;
    }
}
