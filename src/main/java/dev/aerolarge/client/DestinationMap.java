package dev.aerolarge.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.AutopilotBlock;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The autopilot's map, on its settings panel while it is set to fly to a pin (see {@link AutopilotSettingsScreen}),
 * painted by {@link MapPainter}: the land as the player's route map knows it, the villages, the airliner where it is,
 * pointing the way it goes, and the pin with the circle the airliner will fly round it. Drag it to look about, scroll
 * to zoom out and in, and click to drop the pin: on a village when the click is close to one, so a village is easy to
 * pick, else where it was clicked. It opens on the airliner, or on the pin if there is one. A cross marks its middle,
 * whose coordinates it shows, and those of the pin once dropped, so it can be found about even where the land has not
 * been seen yet.
 */
final class DestinationMap extends AbstractWidget implements AutoCloseable {
    /** How often the map is drawn again while nothing moves it, in ticks: the airliner flies on. */
    private static final int REDRAW_TICKS = 10;
    /** How many blocks a pixel takes in: at first, and at the closest and the furthest it zooms. */
    private static final double FIRST_SCALE = 8;
    private static final double CLOSEST = 1;
    private static final double FURTHEST = 64;
    /** A click this many pixels or nearer a village drops the pin on it. */
    private static final double SNAP = 6;
    /** Dragged this many pixels or fewer between pressing and letting go, it was a click. */
    private static final double CLICK_SLOP = 3;
    private static final Component NAME = Component.translatable("screen.aerolarge.autopilot_settings.map");
    /** The cross at the middle: how far each arm reaches, in pixels, and its colour. */
    private static final int CROSS = 3;
    private static final int CROSS_COLOUR = 0xC0FFFFFF;
    /** The coordinates along the bottom: how far in from the edges, and the band behind them. */
    private static final int READOUT_INSET = 3;
    private static final int READOUT_BACKGROUND = 0xB0081226;

    private final Supplier<BlockEntity> autopilot;
    private final IntSupplier radius;
    private final DynamicTexture texture;
    private final ResourceLocation location;
    /** Where the map looks, in world x and z, and how many blocks a pixel takes in. */
    private double centreX;
    private double centreZ;
    private double scale = FIRST_SCALE;
    private boolean placed;
    @Nullable
    private BlockPos pin;
    /** How far the pointer has been dragged since it was pressed on the map, in pixels. */
    private double dragged;
    private long drawnAt = Long.MIN_VALUE;
    private boolean moved = true;

    DestinationMap(int width, int height, BlockPos at, Supplier<BlockEntity> autopilot, @Nullable BlockPos pin, IntSupplier radius) {
        super(0, 0, width, height, NAME);
        this.autopilot = autopilot;
        this.pin = pin;
        this.radius = radius;
        texture = new DynamicTexture(width, height, true);
        location = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "autopilot_map/" + at.asLong());
        Minecraft.getInstance().getTextureManager().register(location, texture);
        if (pin != null) {
            centreX = pin.getX();
            centreZ = pin.getZ();
            placed = true;
        }
    }

    /** The pin dropped, if one has been. */
    @Nullable
    BlockPos pin() {
        return pin;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        BlockEntity blockEntity = autopilot.get();
        Level level = blockEntity != null ? blockEntity.getLevel() : null;
        if (level != null) {
            draw(level, blockEntity, partialTick);
        }
        graphics.blit(location, getX(), getY(), 0, 0, width, height, width, height);
        crosshair(graphics);
        readouts(graphics);
    }

    /** A small cross at the map's middle, the place its coordinates are read at. */
    private void crosshair(GuiGraphics graphics) {
        int x = getX() + width / 2;
        int y = getY() + height / 2;
        graphics.fill(x - CROSS, y, x + CROSS + 1, y + 1, CROSS_COLOUR);
        graphics.fill(x, y - CROSS, x + 1, y + CROSS + 1, CROSS_COLOUR);
    }

    /**
     * The coordinates of the map's middle, in its bottom left corner, so it can be found even where the land has not
     * been seen yet; and once the pin is dropped, the pin's, in its bottom right corner.
     */
    private void readouts(GuiGraphics graphics) {
        Font font = Minecraft.getInstance().font;
        Component middle = Component.translatable("screen.aerolarge.autopilot_settings.map.middle", Mth.floor(centreX), Mth.floor(centreZ));
        label(graphics, font, middle, getX() + READOUT_INSET, PanelArt.WHITE);
        if (pin != null) {
            Component pinned = Component.translatable("screen.aerolarge.autopilot_settings.map.pin", pin.getX(), pin.getZ());
            label(graphics, font, pinned, getRight() - READOUT_INSET - font.width(pinned), PanelArt.RED);
        }
    }

    /** A line of text along the map's bottom edge, on a dark band so it reads over any land. */
    private void label(GuiGraphics graphics, Font font, Component text, int x, int colour) {
        int y = getBottom() - READOUT_INSET - font.lineHeight;
        graphics.fill(x - 2, y - 2, x + font.width(text) + 1, y + font.lineHeight, READOUT_BACKGROUND);
        graphics.drawString(font, text, x, y, colour, false);
    }

    /** Draws the map again if it has been moved, or a moment has gone by since. */
    private void draw(Level level, BlockEntity blockEntity, float partialTick) {
        long now = level.getGameTime();
        if (!moved && drawnAt != Long.MIN_VALUE && now >= drawnAt && now - drawnAt < REDRAW_TICKS) {
            return;
        }
        NativeImage image = texture.getPixels();
        if (image == null) {
            return;
        }
        drawnAt = now;
        moved = false;
        Vec3 airliner = SableClient.toWorld(level, Vec3.atCenterOf(blockEntity.getBlockPos()));
        if (!placed) {
            centreX = airliner.x;
            centreZ = airliner.z;
            placed = true;
        }
        MapPainter.View view = view();
        TerrainMap map = TerrainMap.of(level);
        MapPainter.land(image, map, view);
        MapPainter.villages(image, map, view);
        if (pin != null) {
            int pinX = view.pixelX(pin.getX());
            int pinY = view.pixelY(pin.getZ());
            MapPainter.circle(image, pinX, pinY, radius.getAsInt() / scale, MapPainter.PIN);
            MapPainter.dot(image, pinX, pinY, MapPainter.PIN);
        }
        Direction nose = blockEntity.getBlockState().getValue(AutopilotBlock.FACING);
        SableClient.Attitude attitude = SableClient.attitude(blockEntity, nose, partialTick);
        MapPainter.airliner(image, view.pixelX(airliner.x), view.pixelY(airliner.z), attitude != null ? attitude.heading() : 0);
        texture.upload();
    }

    private MapPainter.View view() {
        return new MapPainter.View(centreX, centreZ, scale, width, height);
    }

    @Override
    public void onClick(double mouseX, double mouseY, int button) {
        dragged = 0;
    }

    /** Dragged, it looks about: the land moves with the pointer. */
    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        dragged += Math.abs(dragX) + Math.abs(dragY);
        centreX -= dragX * scale;
        centreZ -= dragY * scale;
        moved = true;
    }

    /** Let go without having been dragged, it drops the pin there, or on the village close by. */
    @Override
    public void onRelease(double mouseX, double mouseY) {
        if (dragged > CLICK_SLOP) {
            return;
        }
        MapPainter.View view = view();
        double x = view.worldX(mouseX - getX());
        double z = view.worldZ(mouseY - getY());
        BlockPos village = village(x, z);
        pin = village != null ? village : BlockPos.containing(x, 0, z);
        moved = true;
    }

    /** The village nearest a place on the map, if one is close enough to it to take the click. */
    @Nullable
    private BlockPos village(double x, double z) {
        Level level = Minecraft.getInstance().level;
        TerrainMap map = level != null ? TerrainMap.of(level) : null;
        if (map == null) {
            return null;
        }
        BlockPos nearest = null;
        double best = SNAP * scale;
        for (BlockPos village : map.villages()) {
            double distance = Math.hypot(village.getX() - x, village.getZ() - z);
            if (distance <= best) {
                best = distance;
                nearest = village;
            }
        }
        return nearest;
    }

    /** Scrolled, it zooms in (up) or out (down) a step, about the same middle. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0) {
            return false;
        }
        scale = Mth.clamp(scrollY > 0 ? scale / 2 : scale * 2, CLOSEST, FURTHEST);
        moved = true;
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    @Override
    public void close() {
        Minecraft.getInstance().getTextureManager().release(location);
    }
}
