package dev.aerolarge.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.aerolarge.airframe.FinArt;
import dev.aerolarge.airframe.Livery;
import dev.aerolarge.block.FlagHalyardBlockEntity;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;

/**
 * The ship's colours as a zeppelin's fins are painted with them (see {@link FinArt}): the picture of the banner
 * closed up at the top of the flagstaff on its Sky Deck, worked out pixel by pixel from the banner's layers' own
 * textures, each tinted its dye's colour and laid over the ones under it, as a banner is drawn. A banner's picture is
 * worked out once and kept, a few at a time.
 */
public final class ShipsColours {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How many banners' pictures are kept. */
    private static final int KEPT = 16;
    /** The banner textures' size, in pixels, for which the flag's face starts one pixel in from the corner. */
    private static final int TEXTURE_SIZE = 64;
    private static final ResourceLocation BASE = ResourceLocation.withDefaultNamespace("textures/entity/banner/base.png");

    /** A banner, by what it looks like. */
    private record Banner(DyeColor base, BannerPatternLayers patterns) {
    }

    /** The colours painted on a zeppelin's fins: a banner's picture, one block to a pixel where {@link FinArt} says. */
    private record Fins(int[] picture) implements PaintOverlay {
        @Override
        public int colour(Livery.Spot spot, boolean fabric, Livery under) {
            int pixel = fabric ? FinArt.pixel(spot) : -1;
            return pixel < 0 ? -1 : picture[pixel];
        }

        @Override
        public double from() {
            return FinArt.FRONT;
        }

        @Override
        public double to() {
            return FinArt.BACK;
        }
    }

    private static final Map<Banner, int[]> PICTURES = new LinkedHashMap<>(KEPT, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Banner, int[]> eldest) {
            return size() > KEPT;
        }
    };

    private ShipsColours() {
    }

    /**
     * What a zeppelin's fins are painted with: the colours closed up at a halyard in its box; nothing while none are.
     */
    static List<PaintOverlay> fins(ClientLevel level, BoundingBox box) {
        for (FlagHalyardBlockEntity halyard : FlagHalyardBlockEntity.loaded(level)) {
            if (box.isInside(halyard.getBlockPos()) && halyard.closedUp()) {
                return List.of(new Fins(picture(halyard.base(), halyard.patterns())));
            }
        }
        return List.of();
    }

    /** A banner's picture, row by row from its top, each row from its left, as 0xRRGGBB, worked out once and kept. */
    static int[] picture(DyeColor base, BannerPatternLayers patterns) {
        return PICTURES.computeIfAbsent(new Banner(base, patterns), ShipsColours::paint);
    }

    /** A banner's picture: its base colour, and each of its patterns laid over it in turn. */
    private static int[] paint(Banner banner) {
        float[][] picture = new float[3][FinArt.BANNER_WIDTH * FinArt.BANNER_HEIGHT];
        layer(picture, BASE, banner.base());
        for (BannerPatternLayers.Layer layer : banner.patterns().layers()) {
            ResourceLocation asset = layer.pattern().value().assetId();
            layer(picture, asset.withPath(path -> "textures/entity/banner/" + path + ".png"), layer.color());
        }
        int[] colours = new int[picture[0].length];
        for (int pixel = 0; pixel < colours.length; pixel++) {
            colours[pixel] = channel(picture[0][pixel]) << 16 | channel(picture[1][pixel]) << 8 | channel(picture[2][pixel]);
        }
        return colours;
    }

    /** Lays one of a banner's layers over the picture so far: its texture's shape, tinted a dye's colour. */
    private static void layer(float[][] picture, ResourceLocation texture, DyeColor dye) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
        if (resource.isEmpty()) {
            return;
        }
        int tint = dye.getTextureDiffuseColor();
        try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
            int scale = Math.max(1, image.getWidth() / TEXTURE_SIZE);
            for (int pixel = 0; pixel < picture[0].length; pixel++) {
                int abgr = image.getPixelRGBA((1 + pixel % FinArt.BANNER_WIDTH) * scale, (1 + pixel / FinArt.BANNER_WIDTH) * scale);
                float cover = (abgr >>> 24) / 255F;
                float shade = (abgr & 0xFF) / 255F;
                picture[0][pixel] += ((tint >> 16 & 0xFF) / 255F * shade - picture[0][pixel]) * cover;
                picture[1][pixel] += ((tint >> 8 & 0xFF) / 255F * shade - picture[1][pixel]) * cover;
                picture[2][pixel] += ((tint & 0xFF) / 255F * shade - picture[2][pixel]) * cover;
            }
        } catch (IOException e) {
            LOGGER.warn("Could not read the banner texture {} for a zeppelin's fins", texture, e);
        }
    }

    private static int channel(float value) {
        return Math.round(Math.max(0, Math.min(1, value)) * 255);
    }
}
