package dev.aerolarge.client;

import dev.aerolarge.Aerolarge;
import dev.aerolarge.client.PanelArt.Icon;
import dev.aerolarge.radar.Contact;
import dev.aerolarge.radar.MarkerColour;
import dev.aerolarge.radar.MarkerIcon;
import dev.aerolarge.radar.PlayerContact;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * A transponder's marker on the maps, as a beacon shows in Subnautica: its picture in its colour on a dark disc ringed
 * in that colour, and for one aboard an airliner going somewhere, a short line of dots off the ring the way it goes.
 * Pointed at, it says what it is, where, how far off, and for an airliner how high and fast it flies. A player shows
 * as their face, framed, and says who they are, where and how far off.
 */
final class MarkerArt {
    /** The disc's radius, in pixels at scale 1, and the dark inside its ring. */
    static final int RADIUS = 8;
    /** A player's face: half its width, in pixels, with the frame round it, and the frame's colour. */
    static final int FACE_RADIUS = 5;
    private static final int FACE_FRAME = 0xFFFFFFFF;
    private static final int INSIDE = 0xF0081226;
    /** The heading's dots: from how far out from the middle, how many and how far apart. */
    private static final int HEADING_FROM = RADIUS + 2;
    private static final int HEADING_DOTS = 3;
    private static final int HEADING_GAP = 2;
    private static final Map<MarkerIcon, Icon> ICONS = new EnumMap<>(MarkerIcon.class);
    private static final Map<MarkerIcon, ResourceLocation> TEXTURES = new EnumMap<>(MarkerIcon.class);

    static {
        for (MarkerIcon icon : MarkerIcon.values()) {
            ICONS.put(icon, Icon.of(icon.texture()));
            TEXTURES.put(icon, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/gui/" + icon.texture() + ".png"));
        }
    }

    private MarkerArt() {
    }

    static Icon icon(MarkerIcon icon) {
        return ICONS.get(icon);
    }

    /** A picture's texture, white so it can be drawn in any colour, for drawing it in the world (on a screen, say). */
    static ResourceLocation texture(MarkerIcon icon) {
        return TEXTURES.get(icon);
    }

    /** A marker of this picture and colour, its middle at (x, y), at a whole multiple of its size. */
    static void draw(GuiGraphics graphics, MarkerIcon icon, MarkerColour colour, int x, int y, int scale) {
        PanelArt.drawDisc(graphics, x, y, RADIUS * scale, colour.argb());
        PanelArt.drawDisc(graphics, x, y, RADIUS * scale - scale, INSIDE);
        int half = Icon.SIZE * scale / 2;
        icon(icon).draw(graphics, x - half, y - half, scale, colour.argb());
    }

    /** A transponder's marker, its middle at (x, y), with the dots of its heading if it flies somewhere. */
    static void draw(GuiGraphics graphics, Contact contact, int x, int y) {
        draw(graphics, contact.icon(), contact.colour(), x, y, 1);
        if (!contact.airborne() || Float.isNaN(contact.heading())) {
            return;
        }
        double angle = Math.toRadians(contact.heading());
        for (int dot = 0; dot < HEADING_DOTS; dot++) {
            int out = HEADING_FROM + dot * HEADING_GAP;
            int dx = (int) Math.round(Math.sin(angle) * out);
            int dy = (int) Math.round(-Math.cos(angle) * out);
            graphics.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, contact.colour().argb());
        }
    }

    /** A player's face, its middle at (x, y), framed so it stands out from the land under it. */
    static void draw(GuiGraphics graphics, PlayerContact player, int x, int y) {
        int half = FACE_RADIUS - 1;
        graphics.fill(x - FACE_RADIUS, y - FACE_RADIUS, x + FACE_RADIUS, y + FACE_RADIUS, FACE_FRAME);
        PlayerFaceRenderer.draw(graphics, skin(player), x - half, y - half, half * 2);
    }

    /** A player's skin, as the player list has it; a default one while it has not been fetched. */
    private static PlayerSkin skin(PlayerContact player) {
        return skin(player.id());
    }

    /**
     * The skin of a player by who they are, as the player list has it; a default one while it has not been fetched,
     * or once they have gone.
     */
    static PlayerSkin skin(UUID id) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection != null ? connection.getPlayerInfo(id) : null;
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(id);
    }

    /**
     * What a marker says, pointed at: its picture's name, where it is, how far it is from a place (the airliner, or the
     * tower), and for an airliner how high and how fast it flies.
     */
    static List<Component> describe(Contact contact, double fromX, double fromZ) {
        List<Component> lines = new ArrayList<>();
        lines.add(contact.icon().title().copy().withColor(contact.colour().rgb()));
        where(lines, contact.x(), contact.z(), fromX, fromZ);
        if (contact.airborne()) {
            lines.add(Component.translatable("screen.aerolarge.map.contact.flight",
                Mth.floor(contact.y()), String.format(Locale.ROOT, "%.0f", contact.speed())));
        }
        return lines;
    }

    /** What a player's face says, pointed at: who they are, where, and how far they are from a place. */
    static List<Component> describe(PlayerContact player, double fromX, double fromZ) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(player.name()));
        where(lines, player.x(), player.z(), fromX, fromZ);
        return lines;
    }

    /** Where something on the map is, and how far it is from a place. */
    static void where(List<Component> lines, double x, double z, double fromX, double fromZ) {
        lines.add(Component.translatable("screen.aerolarge.map.contact.where", Mth.floor(x), Mth.floor(z)));
        long distance = Math.round(Math.hypot(x - fromX, z - fromZ));
        lines.add(Component.translatable("screen.aerolarge.map.contact.distance", distance));
    }
}
