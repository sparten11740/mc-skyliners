package dev.skyliners.client;

import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.block.LookoutGlassBlockEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The lookouts' flags on the autopilot's map (see {@link LookoutGlassBlockEntity}): each a swallow-tailed pennant of
 * oxblood edged in cream on a brass pole, the lookout's face on it at the hoist over the spot they marked, so whoever
 * flies knows whose it is and where without reading. Pointed at, it says who spotted it, where and how far off;
 * clicked, it is the pin, as a player's face is. Only the flags dropped through the glasses of the autopilot's own
 * zeppelin show.
 */
final class LookoutFlags {
    /**
     * The pennant: how far its cloth reaches either side of the spot, how far it streams on past the face and how
     * deep the cut in its swallow tail is, in pixels.
     */
    private static final int CLOTH_HALF = 6;
    private static final int STREAM = 9;
    private static final int NOTCH = 4;
    /** The lookout's face on it: half its width, in pixels. */
    private static final int FACE_HALF = 4;
    private static final int CLOTH = 0xFF7A1F2B;
    private static final int EDGE = 0xFFF3E9D2;
    private static final int POLE = 0xFFD9B24C;
    private static final int POLE_SHADE = 0xFF3A2A10;

    private LookoutFlags() {
    }

    /** The flags standing on the land from the glasses of the zeppelin an autopilot flies, for its map. */
    static List<MapMarker> on(Level level, BlockEntity autopilot) {
        List<MapMarker> flags = new ArrayList<>();
        long now = level.getGameTime();
        for (LookoutGlassBlockEntity glass : LookoutGlassBlockEntity.loaded(level)) {
            if (AutopilotBlockEntity.zeppelinFraming(level, glass.getBlockPos()) == autopilot) {
                flags.addAll(glass.flags(now).stream().map(Pennant::new).toList());
            }
        }
        return flags;
    }

    /** One lookout's flag, on the map. */
    private record Pennant(LookoutGlassBlockEntity.Flag flag) implements MapMarker {
        @Override
        public double x() {
            return flag.x() + 0.5;
        }

        @Override
        public double z() {
            return flag.z() + 0.5;
        }

        /** The pole down the hoist, the cloth streaming from it, swallow-tailed, and the face over the spot itself. */
        @Override
        public void draw(GuiGraphics graphics, int x, int y) {
            int hoist = x - FACE_HALF - 1;
            graphics.fill(hoist - 2, y - CLOTH_HALF - 1, hoist, y + CLOTH_HALF + 3, POLE_SHADE);
            graphics.fill(hoist - 1, y - CLOTH_HALF - 1, hoist, y + CLOTH_HALF + 3, POLE);
            for (int row = 0; row < CLOTH_HALF * 2; row++) {
                float fromMiddle = Math.abs(row + 0.5F - CLOTH_HALF) / CLOTH_HALF;
                int fly = x + FACE_HALF + 1 + STREAM - Math.round(NOTCH * (1 - fromMiddle));
                graphics.fill(hoist, y - CLOTH_HALF + row, fly, y - CLOTH_HALF + row + 1, row == 0 || row == CLOTH_HALF * 2 - 1 ? EDGE : CLOTH);
                graphics.fill(fly - 1, y - CLOTH_HALF + row, fly, y - CLOTH_HALF + row + 1, EDGE);
            }
            graphics.fill(x - FACE_HALF - 1, y - FACE_HALF - 1, x + FACE_HALF + 1, y + FACE_HALF + 1, EDGE);
            PlayerFaceRenderer.draw(graphics, MarkerArt.skin(flag.lookout()), x - FACE_HALF, y - FACE_HALF, FACE_HALF * 2);
        }

        /** Who spotted it, where it is and how far off. */
        @Override
        public List<Component> describe(double fromX, double fromZ) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable("screen.skyliners.map.lookout_flag", flag.name()).withColor(EDGE & 0xFFFFFF));
            MarkerArt.where(lines, x(), z(), fromX, fromZ);
            return lines;
        }
    }
}
