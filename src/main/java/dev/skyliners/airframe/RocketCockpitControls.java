package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.RocketControlBlock;
import dev.skyliners.block.RocketControlBlockEntity;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;

/**
 * The hands-on controls on the rocket's centre console, round her control head (see {@link
 * dev.skyliners.autopilot.RocketControls}): five modules of the console, their faces toward the pilots, grouped by what
 * they are for. Starting and stopping at the commander's side: the launch key at the commander's inboard hand and the
 * abort handle before the commander. How she flies at the pilot's side: the flight mode knob at the pilot's inboard
 * hand and the stability switch before the pilot. Between them, before the head, the boost button. The key and the
 * knob, the lowest, stand on the head's row, so that from behind the console none hides the picture plate of another
 * before it. Each is linked to the head as she is built, the offset turning with her (see {@link Fitting}). They are
 * laid the most wanted first, each only where its cell is open and inside her, and once one does not fit none after it
 * is laid, so on a small rocket the launch key is left out first, then the abort handle, then the stability switch. The
 * server config's {@code rocket.cockpitControls} leaves them out.
 */
final class RocketCockpitControls implements RocketPiece {
    /** A control and where it stands from the control head: so many blocks to her right and toward her front. */
    private record Spot(Supplier<? extends Block> control, int right, int ahead) {
    }

    /** The controls, the most wanted first, so that the last are those left out on a small rocket. */
    private static final List<Spot> SPOTS = List.of(
        new Spot(SkylinersRegistry.ROCKET_BOOST_BUTTON, 0, 1),
        new Spot(SkylinersRegistry.ROCKET_MODE_KNOB, 1, 0),
        new Spot(SkylinersRegistry.ROCKET_STABILITY_SWITCH, 1, 1),
        new Spot(SkylinersRegistry.ROCKET_ABORT_HANDLE, -1, 1),
        new Spot(SkylinersRegistry.ROCKET_LAUNCH_KEY, -1, 0));

    private final RocketLayout layout;

    RocketCockpitControls(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return SkylinersConfig.ROCKET_COCKPIT_CONTROLS.get();
    }

    @Override
    public void build() {
        BlockPos head = layout.autopilot();
        if (head == null) {
            return;
        }
        for (Spot spot : SPOTS) {
            if (!place(head, spot)) {
                return;
            }
        }
    }

    /**
     * One control on the console, facing the pilots behind it, if its cell is open and inside her, linked to the head;
     * whether it was laid.
     */
    private boolean place(BlockPos head, Spot spot) {
        // Nose north: her right is east (+x), her front north (-z).
        BlockPos at = head.offset(spot.right(), 0, -spot.ahead());
        if (!layout.isOpen(at.getX(), at.getY(), at.getZ()) || !layout.inside(at.getX(), at.getY(), at.getZ())) {
            return false;
        }
        layout.set(at.getX(), at.getY(), at.getZ(), spot.control().get().defaultBlockState().setValue(RocketControlBlock.FACING, Direction.SOUTH));
        BlockPos toHead = head.subtract(at);
        layout.fit(at, (blockEntity, rotation) -> {
            if (blockEntity instanceof RocketControlBlockEntity control) {
                control.link(toHead.rotate(rotation));
            }
        });
        return true;
    }
}
