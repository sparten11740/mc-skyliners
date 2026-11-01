package dev.skyliners.block;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.ContraptionHandler;
import com.simibubi.create.content.contraptions.elevator.ElevatorColumn;
import com.simibubi.create.content.contraptions.elevator.ElevatorContactBlock;
import com.simibubi.create.content.contraptions.elevator.ElevatorContraption;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.createmod.catnip.data.Couple;
import net.createmod.catnip.data.IntAttached;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A lift as its panels see it: one of Create's elevators, its cabin an elevator contraption riding up and down a
 * column of elevator contacts, one at each floor. A panel standing beside a landing belongs to the elevator whose
 * column of contacts is nearest it; one aboard a cabin belongs to that cabin. The floors are the contacts' names, as
 * Create's own controls list them, the top floor first; sending the cabin to one is what Create's controls do, a call
 * to that floor's contact.
 */
public final class Lift {
    /** How far a panel beside a landing may stand from the lift's column of contacts, across, in blocks. */
    private static final double REACH = 3;
    /** How far the cabin may be off a floor, in blocks, and still be standing there rather than on its way. */
    private static final double ARRIVED = 0.25;

    private Lift() {
    }

    /** A floor, by the height of its contact and the short and long names it was given there. */
    public record Floor(int y, String shortName, String longName) {
    }

    /**
     * What a panel shows of a lift: its floors, the top one first; which of them the cabin is at or nearest; which it
     * has been sent to, or -1 for none; and whether it is on its way up (1), down (-1) or standing (0).
     */
    public record View(List<Floor> floors, int at, int target, int heading) {
    }

    /** The cabin of the elevator whose column a panel standing at this spot in the level is beside, or null. */
    @Nullable
    public static AbstractContraptionEntity beside(Level level, BlockPos pos) {
        AbstractContraptionEntity nearest = null;
        double best = REACH * REACH;
        for (AbstractContraptionEntity cabin : cabins(level)) {
            ElevatorColumn.ColumnCoords column = elevator(cabin).getGlobalColumn();
            double distance = Mth.square(column.x() - pos.getX()) + Mth.square(column.z() - pos.getZ());
            if (distance <= best) {
                best = distance;
                nearest = cabin;
            }
        }
        return nearest;
    }

    /**
     * The cabin a panel drawn on a moving contraption rides in, or null: on the client, the cabin whose blocks the panel
     * is drawn among.
     */
    @Nullable
    public static AbstractContraptionEntity aboard(Level level, BlockEntity panel) {
        for (AbstractContraptionEntity cabin : cabins(level)) {
            if (cabin.getContraption().getBlockEntityClientSide(panel.getBlockPos()) == panel) {
                return cabin;
            }
        }
        return null;
    }

    /** Every elevator cabin loaded in the level, standing or moving. */
    private static List<AbstractContraptionEntity> cabins(Level level) {
        Map<Integer, WeakReference<AbstractContraptionEntity>> loaded = ContraptionHandler.loadedContraptions.get(level);
        List<AbstractContraptionEntity> cabins = new ArrayList<>();
        for (WeakReference<AbstractContraptionEntity> reference : loaded.values()) {
            AbstractContraptionEntity entity = reference.get();
            if (entity != null && entity.isAlive() && entity.getContraption() instanceof ElevatorContraption) {
                cabins.add(entity);
            }
        }
        return cabins;
    }

    private static ElevatorContraption elevator(AbstractContraptionEntity cabin) {
        return (ElevatorContraption) cabin.getContraption();
    }

    /** The lift's floors as Create names them, the top one first. */
    public static List<Floor> floors(AbstractContraptionEntity cabin) {
        List<Floor> floors = new ArrayList<>();
        for (IntAttached<Couple<String>> named : elevator(cabin).namesList) {
            floors.addFirst(new Floor(named.getFirst(), named.getSecond().getFirst(), named.getSecond().getSecond()));
        }
        return floors;
    }

    /**
     * The lift as a panel on the client shows it this frame: the cabin's contact as high as the cabin is drawn, the
     * floor it was last sent to as the client was told.
     */
    public static View view(AbstractContraptionEntity cabin, float partialTick) {
        ElevatorContraption elevator = elevator(cabin);
        List<Floor> floors = floors(cabin);
        double y = Mth.lerp(partialTick, cabin.yo, cabin.getY()) + elevator.getContactYOffset();
        int target = elevator.clientYTarget;
        int heading = Math.abs(target - y) < ARRIVED ? 0 : target > y ? 1 : -1;
        int at = -1;
        int targetIndex = -1;
        for (int i = 0; i < floors.size(); i++) {
            Floor floor = floors.get(i);
            if (at < 0 || Math.abs(floor.y() - y) < Math.abs(floors.get(at).y() - y)) {
                at = i;
            }
            targetIndex = floor.y() == target ? i : targetIndex;
        }
        return new View(floors, at, targetIndex, heading);
    }

    /**
     * Sends the cabin to a floor, as Create's own controls do, by calling it to that floor's contact: on the server,
     * and only to a floor of the lift's that its rope reaches. Whether it was sent.
     */
    public static boolean send(Level level, AbstractContraptionEntity cabin, Floor floor) {
        if (level.isClientSide) {
            return false;
        }
        ElevatorContraption elevator = elevator(cabin);
        ElevatorColumn column = ElevatorColumn.get(level, elevator.getGlobalColumn());
        if (column == null || elevator.isTargetUnreachable(floor.y())) {
            return false;
        }
        BlockPos contact = column.contactAt(floor.y());
        BlockState state = level.getBlockState(contact);
        if (!(state.getBlock() instanceof ElevatorContactBlock block)) {
            return false;
        }
        block.callToContactAndUpdate(column, state, level, contact, false);
        return true;
    }
}
