package dev.aerolarge.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.aerolarge.Aerolarge;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Filled maps as the flank screens show them: each map's picture as a texture, painted from its colours as the game
 * paints a map in a frame, kept by the map's number and painted again every few seconds, as the map fills in while
 * its land is explored.
 */
final class ScreenMaps {
    /** A map's side, in pixels, and how often its texture is painted again, in ticks. */
    private static final int SIDE = 128;
    private static final int REPAINT_TICKS = 100;

    /** A map's texture, and the game time it was last painted at. */
    private record Painted(DynamicTexture texture, ResourceLocation location, long when) {
    }

    private static final Map<MapId, Painted> PAINTED = new HashMap<>();

    private ScreenMaps() {
    }

    /** A filled map's picture, as a texture; null for a map this client knows nothing of yet. */
    @Nullable
    static ResourceLocation picture(ClientLevel level, ItemStack map) {
        MapId id = map.get(DataComponents.MAP_ID);
        MapItemSavedData data = id == null ? null : MapItem.getSavedData(id, level);
        if (data == null) {
            return null;
        }
        Painted painted = PAINTED.get(id);
        if (painted == null) {
            DynamicTexture texture = new DynamicTexture(SIDE, SIDE, true);
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "screen_map/" + id.id());
            Minecraft.getInstance().getTextureManager().register(location, texture);
            painted = new Painted(texture, location, level.getGameTime() - REPAINT_TICKS);
        }
        if (level.getGameTime() - painted.when() >= REPAINT_TICKS) {
            paint(painted.texture(), data);
            painted = new Painted(painted.texture(), painted.location(), level.getGameTime());
        }
        PAINTED.put(id, painted);
        return painted.location();
    }

    private static void paint(DynamicTexture texture, MapItemSavedData data) {
        NativeImage pixels = texture.getPixels();
        if (pixels == null) {
            return;
        }
        for (int pixel = 0; pixel < SIDE * SIDE; pixel++) {
            pixels.setPixelRGBA(pixel % SIDE, pixel / SIDE, MapColor.getColorFromPackedId(data.colors[pixel]));
        }
        texture.upload();
    }
}
