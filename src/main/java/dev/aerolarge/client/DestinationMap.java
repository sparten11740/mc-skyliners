package dev.aerolarge.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.aerolarge.block.AutopilotBlock;
import dev.aerolarge.radar.Contact;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The autopilot's map, on its settings panel while it is set to fly to a pin (see {@link AutopilotSettingsScreen}): a
 * {@link MapWidget} with the airliner where it is, pointing the way it goes, the pin with the circle the airliner will
 * fly round it, and the transponders' markers but for the airliner's own. A click drops the pin: on a transponder's
 * marker or a village when the click is close to one, so either is easy to pick, else where it was clicked. It opens
 * on the airliner, or on the pin if there is one, whose coordinates it shows beside its middle's.
 */
final class DestinationMap extends MapWidget {
    /** How many blocks a pixel takes in at first. */
    private static final double FIRST_SCALE = 8;
    /**
     * How far apart two blocks of the same airliner can be, at most, in its plot: a transponder this near the
     * autopilot is the airliner's own, already on the map as its arrow.
     */
    private static final int SAME_AIRLINER = 512;
    private static final Component NAME = Component.translatable("screen.aerolarge.autopilot_settings.map");

    private final BlockPos at;
    private final Supplier<BlockEntity> autopilot;
    private final IntSupplier radius;
    @Nullable
    private BlockPos pin;

    DestinationMap(int width, int height, BlockPos at, Supplier<BlockEntity> autopilot, @Nullable BlockPos pin, IntSupplier radius) {
        super(width, height, NAME, "autopilot_map/" + at.asLong(), FIRST_SCALE);
        this.at = at;
        this.autopilot = autopilot;
        this.pin = pin;
        this.radius = radius;
        if (pin != null) {
            lookAt(pin.getX(), pin.getZ());
        }
    }

    /** The pin dropped, if one has been. */
    @Nullable
    BlockPos pin() {
        return pin;
    }

    @Nullable
    @Override
    Level level() {
        BlockEntity blockEntity = autopilot.get();
        return blockEntity != null ? blockEntity.getLevel() : null;
    }

    @Override
    Vec3 home(Level level, float partialTick) {
        return from(level);
    }

    /** The airliner, where it is in the world. */
    @Override
    Vec3 from(Level level) {
        return SableClient.toWorld(level, Vec3.atCenterOf(at));
    }

    /** All but the airliner's own transponder, which its arrow stands for. */
    @Override
    boolean shows(Contact contact) {
        return !contact.airborne() || !contact.block().closerThan(at, SAME_AIRLINER);
    }

    /** The pin with its circle, and the airliner over all, pointing the way it goes. */
    @Override
    void paint(NativeImage image, MapPainter.View view, Level level, float partialTick) {
        if (pin != null) {
            int pinX = view.pixelX(pin.getX());
            int pinY = view.pixelY(pin.getZ());
            MapPainter.circle(image, pinX, pinY, radius.getAsInt() / view.scale(), MapPainter.PIN);
            MapPainter.dot(image, pinX, pinY, MapPainter.PIN);
        }
        BlockEntity blockEntity = autopilot.get();
        if (blockEntity == null) {
            return;
        }
        Vec3 airliner = from(level);
        Direction nose = blockEntity.getBlockState().getValue(AutopilotBlock.FACING);
        SableClient.Attitude attitude = SableClient.attitude(blockEntity, nose, partialTick);
        MapPainter.airliner(image, view.pixelX(airliner.x), view.pixelY(airliner.z), attitude != null ? attitude.heading() : 0);
    }

    /** Beside the middle's coordinates, the pin's in the bottom right corner once it is dropped. */
    @Override
    void readouts(GuiGraphics graphics) {
        super.readouts(graphics);
        if (pin != null) {
            Font font = Minecraft.getInstance().font;
            Component pinned = Component.translatable("screen.aerolarge.autopilot_settings.map.pin", pin.getX(), pin.getZ());
            label(graphics, pinned, getRight() - READOUT_INSET - font.width(pinned), PanelArt.RED);
        }
    }

    /** Drops the pin there: on the transponder or the village close by, if there is one. */
    @Override
    void clicked(double x, double z, @Nullable Contact contact) {
        if (contact != null) {
            pin = BlockPos.containing(contact.x(), 0, contact.z());
            return;
        }
        BlockPos village = village(x, z);
        pin = village != null ? village : BlockPos.containing(x, 0, z);
    }

    /** The village nearest a place on the map, if one is close enough to it to take the click. */
    @Nullable
    private BlockPos village(double x, double z) {
        Level level = level();
        TerrainMap map = level != null ? TerrainMap.of(level) : null;
        if (map == null) {
            return null;
        }
        BlockPos nearest = null;
        double best = SNAP * scale();
        for (BlockPos village : map.villages()) {
            double distance = Math.hypot(village.getX() - x, village.getZ() - z);
            if (distance <= best) {
                best = distance;
                nearest = village;
            }
        }
        return nearest;
    }
}
