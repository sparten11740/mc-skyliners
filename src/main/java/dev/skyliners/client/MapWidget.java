package dev.skyliners.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.skyliners.Skyliners;
import dev.skyliners.radar.Contact;
import dev.skyliners.radar.OnTheMap;
import dev.skyliners.radar.PlayerContact;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A map on a panel, painted by {@link MapPainter}: the land as the player's route map knows it and the villages, and
 * over them the transponders' markers (see {@link MarkerArt}) and the other players' faces, each saying what it is or
 * who they are when pointed at. Drag it to look about and scroll to zoom out and in; a click (pressed and let go
 * without dragging) is the map's own to use, snapped to a face or a marker close to it. A cross marks its middle,
 * whose coordinates it shows, so it can be found about even where the land has not been seen yet.
 * The autopilot's map ({@link DestinationMap}) and the tower's radar ({@link RadarScreen}) are maps.
 */
abstract class MapWidget extends AbstractWidget implements AutoCloseable {
    /** How often the map is painted again while nothing moves it, in ticks: what is on it moves on. */
    private static final int REDRAW_TICKS = 10;
    /** How many blocks a pixel takes in, at the closest and the furthest it zooms. */
    private static final double CLOSEST = 1;
    private static final double FURTHEST = 64;
    /** A click this many pixels or nearer a marker or a village is taken as a click on it. */
    static final double SNAP = 6;
    /** Dragged this many pixels or fewer between pressing and letting go, it was a click. */
    private static final double CLICK_SLOP = 3;
    /** The cross at the middle: how far each arm reaches, in pixels, and its colour. */
    private static final int CROSS = 3;
    private static final int CROSS_COLOUR = 0xC0FFFFFF;
    /** The coordinates along the bottom: how far in from the edges, and the band behind them. */
    static final int READOUT_INSET = 3;
    private static final int READOUT_BACKGROUND = 0xB0081226;
    /** How far apart two readouts on lines of their own are kept, in GUI pixels, so their dark bands do not touch. */
    static final int READOUT_GAP = 3;

    private final DynamicTexture texture;
    private final ResourceLocation location;
    /** Where the map looks, in world x and z, and how many blocks a pixel takes in. */
    private double centreX;
    private double centreZ;
    private double scale;
    private boolean placed;
    /** How far the pointer has been dragged since it was pressed on the map, in pixels. */
    private double dragged;
    private long drawnAt = Long.MIN_VALUE;
    private boolean moved = true;

    MapWidget(int width, int height, Component name, String textureName, double scale) {
        super(0, 0, width, height, name);
        this.scale = scale;
        texture = new DynamicTexture(width, height, true);
        location = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, textureName);
        Minecraft.getInstance().getTextureManager().register(location, texture);
    }

    /** The level the map shows, if there still is one. */
    @Nullable
    abstract Level level();

    /** Where the map opens, in the world, the first time it is painted. */
    abstract Vec3 home(Level level, float partialTick);

    /** Where the markers' distances are told from: the airliner, or the tower. */
    abstract Vec3 from(Level level);

    /** Paints what the map shows over the land and the villages. */
    abstract void paint(NativeImage image, MapPainter.View view, Level level, float partialTick);

    /**
     * A click on the map, not a drag: at a place in the world, or on a face or a marker if one is close enough, at
     * where that is.
     */
    abstract void clicked(double x, double z, boolean marked);

    /** What features of the mod put on the map besides the transponders and the players: nothing, unless it says so. */
    List<MapMarker> extras(Level level) {
        return List.of();
    }

    /** Whether a transponder's marker shows: all do, unless the map says otherwise. */
    boolean shows(Contact contact) {
        return true;
    }

    /** Whether another player's face shows: all do, unless the map says otherwise. */
    boolean shows(PlayerContact player) {
        return true;
    }

    /** Opens the map on a place, rather than where {@link #home} would have it. */
    void lookAt(double x, double z) {
        centreX = x;
        centreZ = z;
        placed = true;
        moved = true;
    }

    /** Paints the map again on its next frame: what it shows has changed. */
    void repaint() {
        moved = true;
    }

    MapPainter.View view() {
        return new MapPainter.View(centreX, centreZ, scale, width, height);
    }

    double scale() {
        return scale;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Level level = level();
        if (level != null) {
            draw(level, partialTick);
        }
        graphics.blit(location, getX(), getY(), 0, 0, width, height, width, height);
        crosshair(graphics);
        if (level != null) {
            markers(graphics, level, mouseX, mouseY);
        }
        readouts(graphics);
    }

    /** A small cross at the map's middle, the place its coordinates are read at. */
    private void crosshair(GuiGraphics graphics) {
        int x = getX() + width / 2;
        int y = getY() + height / 2;
        graphics.fill(x - CROSS, y, x + CROSS + 1, y + 1, CROSS_COLOUR);
        graphics.fill(x, y - CROSS, x + 1, y + CROSS + 1, CROSS_COLOUR);
    }

    /** The coordinates of the map's middle, in its bottom left corner; the map may add its own (see {@link #label}). */
    void readouts(GuiGraphics graphics) {
        label(graphics, middleReadout(), getX() + READOUT_INSET, PanelArt.WHITE);
    }

    /** The coordinates of the map's middle, as its bottom left corner shows them. */
    Component middleReadout() {
        return Component.translatable("screen.skyliners.autopilot_settings.map.middle", Mth.floor(centreX), Mth.floor(centreZ));
    }

    /** A line of text along the map's bottom edge, on a dark band so it reads over any land. */
    void label(GuiGraphics graphics, Component text, int x, int colour) {
        label(graphics, text, x, colour, 0);
    }

    /** A line of text so many lines up from the map's bottom edge, on a dark band so it reads over any land. */
    void label(GuiGraphics graphics, Component text, int x, int colour, int line) {
        Font font = Minecraft.getInstance().font;
        int y = getBottom() - READOUT_INSET - font.lineHeight - line * (font.lineHeight + READOUT_GAP);
        graphics.fill(x - 2, y - 2, x + font.width(text) + 1, y + font.lineHeight, READOUT_BACKGROUND);
        graphics.drawString(font, text, x, y, colour, false);
    }

    /**
     * The transponders' markers, over them what the mod's features put on the map and over those the other players'
     * faces, kept inside the map, and what the one pointed at says.
     */
    private void markers(GuiGraphics graphics, Level level, int mouseX, int mouseY) {
        MapPainter.View view = view();
        graphics.enableScissor(getX(), getY(), getRight(), getBottom());
        for (Contact contact : shown(level)) {
            MarkerArt.draw(graphics, contact, getX() + view.pixelX(contact.x()), getY() + view.pixelY(contact.z()));
        }
        for (MapMarker extra : extras(level)) {
            extra.draw(graphics, getX() + view.pixelX(extra.x()), getY() + view.pixelY(extra.z()));
        }
        for (PlayerContact player : players(level)) {
            MarkerArt.draw(graphics, player, getX() + view.pixelX(player.x()), getY() + view.pixelY(player.z()));
        }
        graphics.disableScissor();
        Screen screen = Minecraft.getInstance().screen;
        List<Component> pointed = isHovered() ? pointedAt(level, mouseX, mouseY) : List.of();
        if (!pointed.isEmpty() && screen != null) {
            List<FormattedCharSequence> lines = pointed.stream().map(Component::getVisualOrderText).toList();
            screen.setTooltipForNextRenderPass(lines);
        }
    }

    /**
     * What the face or the marker under the pointer says, a face before a feature's marker before a transponder's, as
     * each is drawn over the next; or nothing.
     */
    private List<Component> pointedAt(Level level, int mouseX, int mouseY) {
        Vec3 from = from(level);
        PlayerContact player = nearest(players(level), mouseX, mouseY, MarkerArt.FACE_RADIUS);
        if (player != null) {
            return MarkerArt.describe(player, from.x, from.z);
        }
        MapMarker extra = nearest(extras(level), mouseX, mouseY, MarkerArt.FACE_RADIUS);
        if (extra != null) {
            return extra.describe(from.x, from.z);
        }
        Contact contact = nearest(shown(level), mouseX, mouseY, MarkerArt.RADIUS);
        return contact != null ? MarkerArt.describe(contact, from.x, from.z) : List.of();
    }

    private List<Contact> shown(Level level) {
        return RadarPicture.in(level).stream().filter(this::shows).toList();
    }

    private List<PlayerContact> players(Level level) {
        return RadarPicture.others(level).stream().filter(this::shows).toList();
    }

    /**
     * The face or the marker a click this near takes, a face before a feature's marker before a transponder's, as each
     * is drawn over the next.
     */
    @Nullable
    private OnTheMap marked(Level level, double mouseX, double mouseY) {
        PlayerContact player = nearest(players(level), mouseX, mouseY, SNAP);
        if (player != null) {
            return player;
        }
        MapMarker extra = nearest(extras(level), mouseX, mouseY, SNAP);
        return extra != null ? extra : nearest(shown(level), mouseX, mouseY, SNAP);
    }

    /** What of these is nearest a place on the screen, if one is within this many pixels of it. */
    @Nullable
    private <T extends OnTheMap> T nearest(List<T> shown, double mouseX, double mouseY, double reach) {
        MapPainter.View view = view();
        T nearest = null;
        double best = reach;
        for (T each : shown) {
            double distance = Math.hypot(getX() + view.pixelX(each.x()) - mouseX, getY() + view.pixelY(each.z()) - mouseY);
            if (distance <= best) {
                best = distance;
                nearest = each;
            }
        }
        return nearest;
    }

    /** Paints the map again if it has been moved, or a moment has gone by since. */
    private void draw(Level level, float partialTick) {
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
        if (!placed) {
            Vec3 home = home(level, partialTick);
            lookAt(home.x, home.z);
        }
        MapPainter.View view = view();
        TerrainMap map = TerrainMap.of(level);
        MapPainter.land(image, map, view);
        MapPainter.villages(image, map, view);
        paint(image, view, level, partialTick);
        texture.upload();
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

    /** Let go without having been dragged, it was a click: the map's own to use. */
    @Override
    public void onRelease(double mouseX, double mouseY) {
        Level level = level();
        if (dragged > CLICK_SLOP || level == null) {
            return;
        }
        OnTheMap marked = marked(level, mouseX, mouseY);
        MapPainter.View view = view();
        if (marked != null) {
            clicked(marked.x(), marked.z(), true);
        } else {
            clicked(view.worldX(mouseX - getX()), view.worldZ(mouseY - getY()), false);
        }
        moved = true;
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
