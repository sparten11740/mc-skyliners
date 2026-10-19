package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;

/**
 * The jet bridge's reach, run by the airstairs' engine. Used while folded, it looks out ahead for an airliner door,
 * standing in the world or aboard an assembled airliner, and if one lines up with it plans a tunnel out to the hull,
 * a slice at a time, with a canopy at the end that stretches on to close against the hull where it curves away; then
 * it unfolds that. Docked, it opens the door (a plug door; older airliners' doors are opened by hand), keeps watch on
 * it and pulls back of its own accord when the airliner moves off, shutting the door it opened as it goes.
 */
public class JetBridgeBlockEntity extends AirstairControllerBlockEntity {
    /** How far ahead it reaches for a door, in blocks. */
    private static final int REACH = 32;
    /** How far to either side, and up and down, it looks for a door that does not line up, to say where it is. */
    private static final int SEARCH = 8;
    /** How far its canopy can stretch on past the tunnel's end to meet the hull, in blocks. */
    private static final int CANOPY = 3;
    /** The tunnel's walls stand this far to either side, and its roof this high above the floor: three by three inside. */
    private static final int WALL = 2;
    private static final int ROOF = 4;
    /** Every so many slices there is a lamp in the roof. */
    private static final int LAMP_EVERY = 4;
    /** Faster than this, in blocks a second, the airliner is moving: it cannot dock, and a docked bridge pulls back. */
    private static final double MOVING = 0.5;
    /** How often a docked bridge looks whether its airliner is still there, and still, in ticks. */
    private static final int WATCH_TICKS = 10;

    private static final BlockState FLOOR = Blocks.GRAY_CONCRETE.defaultBlockState();
    private static final BlockState PANEL = Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState();
    private static final BlockState WINDOW = Blocks.LIGHT_GRAY_STAINED_GLASS.defaultBlockState();
    private static final BlockState LAMP = Blocks.SEA_LANTERN.defaultBlockState();
    private static final BlockState BELLOWS = Blocks.BLACK_CONCRETE.defaultBlockState();

    private static final Predicate<BlockState> SOLID = state -> !state.isAir() && !state.canBeReplaced();
    /** A door's foot: a plug door's part on its sill, or the lower half of a door in an airliner built before them. */
    private static final Predicate<BlockState> DOOR = state -> PlugDoorBlock.isSill(state)
        || state.is(AerolargeRegistry.AIRLINER_DOOR.get()) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;

    /** Where in the world the foot of the door it docked at was; null before it first docks. */
    @Nullable
    private BlockPos dock;
    /** Whether the tunnel has reached the door since it last set out, and opened it if it is a plug door. */
    private boolean arrived;
    /**
     * The plug door it opened once docked, where it stands (in the world, or in its airliner's plot), to shut it again
     * as it pulls back; null while it holds none open.
     */
    @Nullable
    private BlockPos opened;

    public JetBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.JET_BRIDGE_ENTITY.get(), pos, state);
    }

    /**
     * Folds the bridge back if it is out, or reaches out to dock at a door, telling the player who works it (none for
     * a redstone signal) where it is docking, or why it cannot.
     */
    public void operate(@Nullable Player player) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (!folded()) {
            toggle();
            return;
        }
        Door door = nearestDoor(server);
        if (door == null) {
            refuse(player, Component.translatable("message.aerolarge.jet_bridge.no_door", REACH));
            return;
        }
        Component misaligned = door.misalignment();
        if (misaligned != null) {
            refuse(player, misaligned);
            return;
        }
        BlockPos at = worldPosition.offset(offset(door.d(), door.s(), door.h()));
        if (!still(server, at)) {
            refuse(player, Component.translatable("message.aerolarge.jet_bridge.moving"));
            return;
        }
        int end = end(server, door);
        if (door.d() - end > CANOPY) {
            refuse(player, Component.translatable("message.aerolarge.jet_bridge.blocked", end, door.d()));
            return;
        }
        replan(tunnel(server, door, end));
        dock = at;
        tell(player, Component.translatable("message.aerolarge.jet_bridge.docking", door.d()));
        toggle();
    }

    private void refuse(@Nullable Player player, Component why) {
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0F, 0.6F);
        }
        tell(player, why);
    }

    @Override
    protected void tick(ServerLevel level) {
        super.tick(level);
        if (dock != null && unfolding() && level.getGameTime() % WATCH_TICKS == 0 && !still(level, dock)) {
            // The airliner is pushing back, or gone: get out of its way.
            toggle();
        }
        if (dock != null && !arrived && extended()) {
            arrived = true;
            BlockPos foot = SableFlight.find(level, Vec3.atCenterOf(dock), PlugDoorBlock::isSill);
            PlugDoorBlockEntity door = foot == null ? null : PlugDoorBlockEntity.of(level, foot);
            if (door != null) {
                door.set(true);
                opened = door.getBlockPos();
            }
            setChanged();
        }
    }

    /** Folding back, it shuts the door it opened, wherever its airliner has got to by then. */
    @Override
    public void toggle() {
        super.toggle();
        arrived = false;
        if (!unfolding()) {
            shutDoor();
        }
    }

    /** Taken away at once, as when the bridge is broken, it shuts the door it opened too. */
    @Override
    protected void foldAway() {
        super.foldAway();
        shutDoor();
    }

    private void shutDoor() {
        PlugDoorBlockEntity door = opened == null || level == null ? null : PlugDoorBlockEntity.of(level, opened);
        if (door != null) {
            door.set(false);
        }
        opened = null;
        setChanged();
    }

    /** Whether there is an airliner door at the spot in the world, standing still. */
    private static boolean still(ServerLevel level, BlockPos at) {
        BlockPos door = SableFlight.find(level, Vec3.atCenterOf(at), DOOR);
        return door != null && SableFlight.velocity(level, Vec3.atCenterOf(door)).length() <= MOVING;
    }

    /** The foot of an airliner door: d blocks ahead of the controller, s to the left of it, h above it. */
    private record Door(int d, int s, int h) {
        /** How far it is from lining up: its foot one above the controller's, within the tunnel's width. */
        int miss() {
            return Math.max(Math.abs(s) - 1, 0) + Math.abs(h - 1);
        }

        /** Which way to move the bridge to line up with it; null when it does. */
        @Nullable
        Component misalignment() {
            if (miss() == 0) {
                return null;
            }
            MutableComponent moves = Component.empty();
            move(moves, Math.abs(s) > 1 ? s : 0, "left", "right");
            move(moves, h - 1, "up", "down");
            return Component.translatable("message.aerolarge.jet_bridge.misaligned", d, moves);
        }

        private static void move(MutableComponent moves, int by, String positive, String negative) {
            if (by == 0) {
                return;
            }
            if (!moves.getSiblings().isEmpty()) {
                moves.append(", ");
            }
            moves.append(Component.translatable("message.aerolarge.jet_bridge." + (by > 0 ? positive : negative), Math.abs(by)));
        }
    }

    /** The airliner door nearest to lining up in the first slice ahead that has one; null if there is none in reach. */
    @Nullable
    private Door nearestDoor(ServerLevel level) {
        for (int d = 1; d <= REACH; d++) {
            Door nearest = null;
            for (int s = -SEARCH; s <= SEARCH; s++) {
                for (int h = 1 - SEARCH; h <= 1 + SEARCH; h++) {
                    Door door = new Door(d, s, h);
                    if ((nearest == null || door.miss() < nearest.miss()) && SableFlight.find(level, centre(d, s, h), DOOR) != null) {
                        nearest = door;
                    }
                }
            }
            if (nearest != null) {
                return nearest;
            }
        }
        return null;
    }

    /** How far ahead the first slice is that anything solid stands in, up to the door's: where whole slices end. */
    private int end(ServerLevel level, Door door) {
        int end = 1;
        while (end < door.d() && !blocked(level, end)) {
            end++;
        }
        return end;
    }

    /**
     * The bridge out to the door: whole slices up to the end, then, as the last stage, the canopy, each block of the
     * tunnel's sides stretching on from there until it meets the hull.
     */
    private List<List<Step>> tunnel(ServerLevel level, Door door, int end) {
        List<List<Step>> stages = new ArrayList<>();
        for (int d = 1; d < end; d++) {
            List<Step> slice = new ArrayList<>();
            for (int[] cell : shell()) {
                slice.add(new Step(offset(d, cell[0], cell[1]), part(d, cell[0], cell[1])));
            }
            stages.add(List.copyOf(slice));
        }
        List<Step> canopy = new ArrayList<>();
        for (int[] cell : shell()) {
            for (int d = end; d < door.d() && SableFlight.find(level, centre(d, cell[0], cell[1]), SOLID) == null; d++) {
                canopy.add(new Step(offset(d, cell[0], cell[1]), cell[1] == 0 ? FLOOR : BELLOWS));
            }
        }
        if (!canopy.isEmpty()) {
            stages.add(List.copyOf(canopy));
        }
        return List.copyOf(stages);
    }

    /** Whether anything solid, in the world or aboard an airliner, stands anywhere in the slice d blocks ahead. */
    private boolean blocked(ServerLevel level, int d) {
        for (int s = -WALL; s <= WALL; s++) {
            for (int h = 0; h <= ROOF; h++) {
                if (SableFlight.find(level, centre(d, s, h), SOLID) != null) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The cells of a slice the tunnel is built of, as {s, h}: the floor, the walls either side and the roof. */
    private static List<int[]> shell() {
        List<int[]> cells = new ArrayList<>();
        for (int s = -WALL; s <= WALL; s++) {
            cells.add(new int[] {s, 0});
            cells.add(new int[] {s, ROOF});
        }
        for (int h = 1; h < ROOF; h++) {
            cells.add(new int[] {-WALL, h});
            cells.add(new int[] {WALL, h});
        }
        return cells;
    }

    /** What the tunnel is built of at a cell: a floor, panelled walls with a band of windows at eye height, a lit roof. */
    private static BlockState part(int d, int s, int h) {
        if (h == 0) {
            return FLOOR;
        }
        if (h == ROOF) {
            return s == 0 && d % LAMP_EVERY == 0 ? LAMP : PANEL;
        }
        return h == 2 ? WINDOW : PANEL;
    }

    /** The cell d blocks ahead of the controller, s to its left and h above it, as an offset from it. */
    private BlockPos offset(int d, int s, int h) {
        Direction ahead = getBlockState().getValue(JetBridgeBlock.FACING);
        return BlockPos.ZERO.relative(ahead, d).relative(ahead.getCounterClockWise(), s).above(h);
    }

    private Vec3 centre(int d, int s, int h) {
        return Vec3.atCenterOf(worldPosition.offset(offset(d, s, h)));
    }

    private static void tell(@Nullable Player player, Component message) {
        if (player != null) {
            player.displayClientMessage(message, true);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (dock != null) {
            tag.put("Dock", NbtUtils.writeBlockPos(dock));
        }
        tag.putBoolean("Arrived", arrived);
        if (opened != null) {
            tag.put("Opened", NbtUtils.writeBlockPos(opened));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        dock = NbtUtils.readBlockPos(tag, "Dock").orElse(null);
        arrived = tag.getBoolean("Arrived");
        opened = NbtUtils.readBlockPos(tag, "Opened").orElse(null);
    }
}
