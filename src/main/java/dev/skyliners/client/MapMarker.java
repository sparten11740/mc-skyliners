package dev.skyliners.client;

import dev.skyliners.radar.OnTheMap;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Something a feature puts on a map besides the transponders and the players, such as a lookout's flag: drawn at its
 * place over the land, saying what it is when pointed at, and taken as the pin when clicked, as a player's face is.
 */
interface MapMarker extends OnTheMap {
    /** Draws it with the place it marks at (x, y) on the screen. */
    void draw(GuiGraphics graphics, int x, int y);

    /** What it says, pointed at, its distance told from a place. */
    List<Component> describe(double fromX, double fromZ);
}
