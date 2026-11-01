package dev.skyliners.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.skyliners.block.TowerRadarBlockEntity;
import dev.skyliners.client.PanelArt.CloseButton;
import dev.skyliners.client.PanelArt.Icon;
import dev.skyliners.radar.MarkerIcon;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The tower's radar, opened by using its radar screen: a panel in the flight deck's colours (see {@link PanelArt})
 * filled by a map round the tower (see {@link MapWidget}), with range rings round the tower every so many blocks and
 * every transponder's marker on it: the airliners where they fly, the way they go, and those on the ground, the
 * tower's own among them; and the other players' faces. Pointed at, a marker says what it is and a face who it is,
 * and how far from the tower; a click on either centres the map on it. The game goes on behind it, and it closes with
 * its close button or Escape.
 */
public class RadarScreen extends Screen {
    /** The panel's largest size, how far it keeps in from the window's edges, and the space inside its bezel. */
    private static final int MAX_WIDTH = 440;
    private static final int MAX_HEIGHT = 300;
    private static final int MARGIN = 10;
    private static final int PADDING = 8;
    private static final int MAP_Y = PanelArt.BORDER + PanelArt.TITLE_HEIGHT + 1 + PADDING;
    /** The close button's distance in from the corner of the title bar. */
    private static final int CLOSE_INSET = 3;
    private static final Icon TOWER = MarkerArt.icon(MarkerIcon.TOWER);

    private final BlockPos pos;
    private int left;
    private int top;
    private int panelWidth;
    private int panelHeight;
    @Nullable
    private RadarMap map;

    private RadarScreen(BlockPos pos) {
        super(Component.translatable("screen.skyliners.radar.title"));
        this.pos = pos;
    }

    /** Opens the radar of the screen at a position. */
    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new RadarScreen(pos));
    }

    @Override
    protected void init() {
        panelWidth = Math.min(MAX_WIDTH, width - 2 * MARGIN);
        panelHeight = Math.min(MAX_HEIGHT, height - 2 * MARGIN);
        left = (width - panelWidth) / 2;
        top = (height - panelHeight) / 2;
        int inset = PanelArt.BORDER + PADDING;
        RadarMap old = map;
        map = new RadarMap(panelWidth - 2 * inset, panelHeight - MAP_Y - inset, pos);
        if (old != null) {
            // Laid out again (the window resized): the new map looks where the old one did.
            MapPainter.View view = old.view();
            map.lookAt(view.x(), view.z());
            old.close();
        }
        map.setPosition(left + inset, top + MAP_Y);
        addRenderableWidget(map);
        int closeX = left + panelWidth - PanelArt.BORDER - CLOSE_INSET - CloseButton.SIZE;
        addRenderableWidget(new CloseButton(closeX, top + PanelArt.BORDER + CLOSE_INSET, this::onClose));
    }

    /**
     * Closes once the radar screen is gone; while it is open, the land round the tower is taken into the player's map
     * of it (see {@link TerrainMap}), as it is round an airliner in flight, so the map fills in round the tower.
     */
    @Override
    public void tick() {
        Level level = minecraft != null ? minecraft.level : null;
        if (level == null || !(level.getBlockEntity(pos) instanceof TowerRadarBlockEntity)) {
            onClose();
            return;
        }
        TerrainMap terrain = TerrainMap.of(level);
        if (terrain != null) {
            Vec3 tower = SableClient.toWorld(level, Vec3.atCenterOf(pos));
            terrain.see(level, tower.x, tower.z);
        }
    }

    @Override
    public void removed() {
        if (map != null) {
            map.close();
        }
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderTransparentBackground(graphics);
        PanelArt.drawFrame(graphics, left, top, left + panelWidth, top + panelHeight);
        PanelArt.drawTitleBar(graphics, font, TOWER, title, left, top, left + panelWidth);
    }

    /**
     * The map round the tower: range rings every {@link #RING} blocks round it, the markers of every transponder and
     * the other players' faces. A click on a marker or a face centres the map on it.
     */
    private static final class RadarMap extends MapWidget {
        /** How many blocks a pixel takes in at first: the rings out to a few thousand blocks across the panel. */
        private static final double FIRST_SCALE = 8;
        /** How far apart the range rings are, in blocks, and how many there are. */
        private static final int RING = 250;
        private static final int RINGS = 8;
        private static final int RING_COLOUR = 0x2EB84A;

        private final BlockPos pos;

        RadarMap(int width, int height, BlockPos pos) {
            super(width, height, Component.translatable("screen.skyliners.radar.map"), "radar_map/" + pos.asLong(), FIRST_SCALE);
            this.pos = pos;
        }

        @Nullable
        @Override
        Level level() {
            return Minecraft.getInstance().level;
        }

        @Override
        Vec3 home(Level level, float partialTick) {
            return from(level);
        }

        /** The tower, where its radar screen is in the world (aboard an airliner, where the airliner is). */
        @Override
        Vec3 from(Level level) {
            return SableClient.toWorld(level, Vec3.atCenterOf(pos));
        }

        @Override
        void paint(NativeImage image, MapPainter.View view, Level level, float partialTick) {
            Vec3 tower = from(level);
            int x = view.pixelX(tower.x);
            int y = view.pixelY(tower.z);
            for (int ring = 1; ring <= RINGS; ring++) {
                MapPainter.circle(image, x, y, ring * RING / view.scale(), RING_COLOUR);
            }
        }

        @Override
        void clicked(double x, double z, boolean marked) {
            if (marked) {
                lookAt(x, z);
            }
        }
    }
}
