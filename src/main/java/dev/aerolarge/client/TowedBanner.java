package dev.aerolarge.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.airframe.ZeppelinSilhouette;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.block.BannerCleatBlockEntity;
import dev.aerolarge.block.ShipsPlaqueBlockEntity;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The towed banner, as this client draws it, streaming from a zeppelin's tail while its cleat in her control car lets
 * it out (see {@link BannerCleatBlockEntity}): a manila tow rope from the tip of her tail and, on it, a long banner of
 * sail cloth with her christened name across it in big capitals, or the airship line's name before she has one,
 * reading the right way round from either side. Still, it hangs straight down from her tail; the faster she flies the
 * further back it streams, the wind's drag on it against its weight, near level at her cruising speeds, and the harder
 * it flutters, so its angle shows how fast she goes. It only streams while she is well clear of the ground, and not
 * at all with the {@code towedBanner} switch off.
 *
 * <p>Each streaming banner's zeppelin, her frame and her name are looked for once a second, not every frame, as
 * finding her frame searches her blocks for her autopilot.
 */
public final class TowedBanner {
    /**
     * How long the tow rope is, from her tail to the banner, and how tall the banner is and how long, in blocks: sized
     * to read against a full-size zeppelin's 490-block hull, and as big behind a half-size one.
     */
    private static final double ROPE = 24;
    private static final double HEIGHT = 14;
    private static final double LONGEST = 100;
    private static final double SHORTEST = 34;
    /**
     * How the banner streams back with her speed: the tangent of its angle from hanging straight down for each square
     * block a second, so it streams some 80 degrees back at 10 blocks a second; and slower than this, in blocks a
     * second, it hangs as she is turned rather than back along the way she moves.
     */
    private static final double DRAG = 0.0567;
    private static final double STILL = 0.5;
    /**
     * How much higher over the ground under it her tail must be than the rope and banner reach down as they stream,
     * all their length hanging straight down while she is still and less the faster she flies, for it to stream, in
     * blocks.
     */
    private static final double CLEARANCE = 8;
    /**
     * How far its free end flutters either way at most, in blocks, at {@link #FAST} or faster, in blocks a second; how
     * many waves run along it; and how fast they run, in radians a tick.
     */
    private static final double FLUTTER = 2.5;
    private static final double FAST = 12;
    private static final double WAVES = 2.5;
    private static final double WAVE_SPEED = 0.6;
    /** How many strips it is drawn in along it, to bend with its waves. */
    private static final int STRIPS = 24;
    /** The rope's half thickness, in blocks, and its manila colour. */
    private static final float ROPE_HALF = 0.06F;
    private static final int ROPE_COLOUR = 0xFFC9B27A;
    /** How far apart its two faces are drawn, in blocks, so each reads from its own side. */
    private static final double FACES_APART = 0.02;
    /**
     * Its picture: how tall in pixels, how wide its margin either side of the name, and its colours: the sail cloth,
     * its red edging and the dark letters; and how big the letters are, as a share of its height.
     */
    private static final int PICTURE_HEIGHT = 32;
    private static final int MARGIN = 12;
    private static final int EDGE = 3;
    private static final int CLOTH = 0xFFF2EEE4;
    private static final int EDGING = 0xFFC8282C;
    private static final float LETTERS = 0.62F;
    private static final int INK = 0xFF1C1C22;
    /** What it reads before she is christened. */
    private static final String UNNAMED = "AEROLARGE";

    /** How often a streaming banner's zeppelin, her frame and her name are looked for again, in ticks. */
    private static final int LOOK_TICKS = 20;
    /** How many names' pictures are kept; the one longest unused is let go. */
    private static final int KEPT = 8;

    /** Each name's picture, made once as a texture of its own, and how long a banner it makes. */
    private record Picture(ResourceLocation texture, double length) {
    }

    /**
     * A streaming banner's zeppelin as last looked for, at a tick of {@link #ticks}: her frame, none when it is not
     * aboard an assembled one, and her banner's picture.
     */
    private record Tow(@Nullable SableClient.Frame frame, Picture picture, long lookedAt) {
    }

    private static final Map<String, Picture> PICTURES = new LinkedHashMap<>(KEPT, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Picture> eldest) {
            boolean unused = size() > KEPT;
            if (unused) {
                Minecraft.getInstance().getTextureManager().release(eldest.getValue().texture());
            }
            return unused;
        }
    };
    private static final Map<BannerCleatBlockEntity, Tow> TOWS = new HashMap<>();
    /** This client's own count of ticks: the game's time is set back now and then. */
    private static long ticks;

    private TowedBanner() {
    }

    /** Once a tick: the streaming banners loaded, each one's zeppelin looked for as it starts and once a second. */
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !AerolargeConfig.TOWED_BANNER.get()) {
            TOWS.clear();
            return;
        }
        ticks++;
        List<BannerCleatBlockEntity> streaming = BannerCleatBlockEntity.loaded(level).stream()
            .filter(cleat -> !cleat.isRemoved() && cleat.streaming())
            .toList();
        TOWS.keySet().retainAll(streaming);
        for (BannerCleatBlockEntity cleat : streaming) {
            Tow tow = TOWS.get(cleat);
            if (tow == null || ticks - tow.lookedAt() >= LOOK_TICKS) {
                TOWS.put(cleat, look(level, cleat));
            }
        }
    }

    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        TOWS.clear();
    }

    /** A streaming banner's zeppelin as she is now: her frame and her banner's picture. */
    private static Tow look(ClientLevel level, BannerCleatBlockEntity cleat) {
        AutopilotBlockEntity zeppelin = AutopilotBlockEntity.zeppelinFraming(level, cleat.getBlockPos());
        SableClient.Frame frame = zeppelin == null ? null : SableClient.frame(zeppelin);
        return new Tow(frame, picture(zeppelin == null ? UNNAMED : name(level, zeppelin)), ticks);
    }

    /** After the entities are drawn: every streaming banner, its rope and its cloth. */
    public static void stage(RenderLevelStageEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || level == null) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        for (Map.Entry<BannerCleatBlockEntity, Tow> tow : TOWS.entrySet()) {
            if (tow.getValue().frame() != null) {
                stream(level, tow.getKey(), tow.getValue(), camera, partialTick, event.getPoseStack(), buffers);
            }
        }
    }

    /** One zeppelin's banner, where she is well clear of the ground. */
    private static void stream(ClientLevel level, BannerCleatBlockEntity cleat, Tow tow, Vec3 camera, float partialTick, PoseStack pose,
                               MultiBufferSource.BufferSource buffers) {
        SableClient.Frame frame = tow.frame();
        SableClient.Ride ride = SableClient.ride(cleat);
        if (frame == null || ride == null) {
            return;
        }
        Picture picture = tow.picture();
        ZeppelinSilhouette shape = ZeppelinSilhouette.of(frame.size());
        Vec3 plotTail = frame.origin().add(0, shape.axis(), 0).add(Vec3.atLowerCornerOf(frame.nose().getOpposite().getNormal()).scale(shape.length()));
        SableClient.View tail = SableClient.view(plotTail, Vec3.atLowerCornerOf(frame.nose().getOpposite().getNormal()), new Vec3(0, 1, 0), partialTick);
        if (tail == null) {
            return;
        }
        Vec3 velocity = new Vec3(ride.velocity().x(), ride.velocity().y(), ride.velocity().z());
        Vec3 back = new Vec3(-velocity.x, 0, -velocity.z);
        double speed = back.length();
        back = speed > STILL ? back.normalize() : new Vec3(tail.forward().x, 0, tail.forward().z).normalize();
        double slant = Math.atan(DRAG * speed * speed);
        double reach = (ROPE + picture.length()) * Math.cos(slant) + HEIGHT * Math.sin(slant);
        if (tail.eye().y - ground(level, tail.eye()) < reach + CLEARANCE) {
            return;
        }
        Vec3 along = back.scale(Math.sin(slant)).add(0, -Math.cos(slant), 0);
        Vec3 side = new Vec3(-back.z, 0, back.x);
        Vec3 up = side.cross(along).normalize();
        up = up.y < 0 ? up.scale(-1) : up;
        Vec3 head = tail.eye().add(along.scale(ROPE));
        rope(buffers, pose, camera, tail.eye(), head, level);
        double flutter = FLUTTER * Math.min(1, speed / FAST);
        float time = ticks + partialTick;
        cloth(buffers, pose, camera, level, picture, head, along, up, side, flutter, time);
    }

    /** The top of the ground, or of what stands on it, under a point in the world. */
    private static double ground(ClientLevel level, Vec3 over) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(over.x), Mth.floor(over.z));
    }

    /** The tow rope from her tail to the banner, a thin strip turned to face the camera. */
    private static void rope(MultiBufferSource.BufferSource buffers, PoseStack pose, Vec3 camera, Vec3 from, Vec3 to, ClientLevel level) {
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        Vec3 way = to.subtract(from);
        Vec3 across = way.cross(from.subtract(camera)).normalize().scale(ROPE_HALF);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(from.add(to).scale(0.5)));
        PoseStack.Pose at = pose.last();
        point(consumer, at, camera, from.add(across), 0, 0, ROPE_COLOUR, light);
        point(consumer, at, camera, from.subtract(across), 1, 0, ROPE_COLOUR, light);
        point(consumer, at, camera, to.subtract(across), 1, 1, ROPE_COLOUR, light);
        point(consumer, at, camera, to.add(across), 0, 1, ROPE_COLOUR, light);
        buffers.endBatch(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
    }

    /**
     * The banner's cloth from its head, where the rope holds it, back along the way it streams and down from its top,
     * waving across more the further from its head; its two faces each read the right way round from their own side.
     */
    private static void cloth(MultiBufferSource.BufferSource buffers, PoseStack pose, Vec3 camera, ClientLevel level, Picture picture, Vec3 head,
                              Vec3 along, Vec3 up, Vec3 side, double flutter, float time) {
        RenderType type = RenderType.entityCutout(picture.texture());
        VertexConsumer consumer = buffers.getBuffer(type);
        int light = LevelRenderer.getLightColor(level, BlockPos.containing(head.add(along.scale(picture.length() / 2))));
        PoseStack.Pose at = pose.last();
        for (int strip = 0; strip < STRIPS; strip++) {
            for (int face = -1; face <= 1; face += 2) {
                quad(consumer, at, camera, picture, head, along, up, side, flutter, time, strip, face, light);
            }
        }
        buffers.endBatch(type);
    }

    /** One strip of one face of the cloth, wound so it is seen only from that face's side. */
    private static void quad(VertexConsumer consumer, PoseStack.Pose at, Vec3 camera, Picture picture, Vec3 head, Vec3 along, Vec3 up, Vec3 side,
                             double flutter, float time, int strip, int face, int light) {
        float from = strip / (float) STRIPS;
        float to = (strip + 1) / (float) STRIPS;
        Vec3 topFrom = onCloth(head, along, up, side, flutter, time, from, 0, face, picture);
        Vec3 topTo = onCloth(head, along, up, side, flutter, time, to, 0, face, picture);
        Vec3 bottomTo = onCloth(head, along, up, side, flutter, time, to, 1, face, picture);
        Vec3 bottomFrom = onCloth(head, along, up, side, flutter, time, from, 1, face, picture);
        // Each face reads from its own side: the far one's picture mirrored, and wound the other way round.
        float uFrom = face > 0 ? from : 1 - from;
        float uTo = face > 0 ? to : 1 - to;
        if (face > 0) {
            point(consumer, at, camera, topFrom, uFrom, 0, -1, light);
            point(consumer, at, camera, bottomFrom, uFrom, 1, -1, light);
            point(consumer, at, camera, bottomTo, uTo, 1, -1, light);
            point(consumer, at, camera, topTo, uTo, 0, -1, light);
        } else {
            point(consumer, at, camera, topTo, uTo, 0, -1, light);
            point(consumer, at, camera, bottomTo, uTo, 1, -1, light);
            point(consumer, at, camera, bottomFrom, uFrom, 1, -1, light);
            point(consumer, at, camera, topFrom, uFrom, 0, -1, light);
        }
    }

    /** A point on the cloth so far along it and down it, both shares, as it waves now, on one face of it. */
    private static Vec3 onCloth(Vec3 head, Vec3 along, Vec3 up, Vec3 side, double flutter, float time, float share, float down, int face,
                                Picture picture) {
        double wave = flutter * share * Mth.sin((float) (share * WAVES * 2 * Math.PI - time * WAVE_SPEED));
        return head.add(along.scale(share * picture.length())).add(up.scale(-down * HEIGHT)).add(side.scale(wave + face * FACES_APART / 2));
    }

    private static void point(VertexConsumer consumer, PoseStack.Pose at, Vec3 camera, Vec3 point, float u, float v, int colour, int light) {
        consumer.addVertex(at, (float) (point.x - camera.x), (float) (point.y - camera.y), (float) (point.z - camera.z)).setColor(colour).setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(at, 0, 1, 0);
    }

    /** Her christened name, from her plaque; the airship line's before she has one. */
    private static String name(ClientLevel level, AutopilotBlockEntity zeppelin) {
        BoundingBox frame = zeppelin.frame();
        for (ShipsPlaqueBlockEntity plaque : ShipsPlaqueBlockEntity.loaded(level)) {
            if (frame != null && frame.isInside(plaque.getBlockPos()) && plaque.christened() && !plaque.name().isBlank()) {
                return plaque.name().toUpperCase(Locale.ROOT);
            }
        }
        return UNNAMED;
    }

    /** A name's picture: the name in bold serif capitals on the sail cloth between its red edging, made once. */
    private static Picture picture(String name) {
        return PICTURES.computeIfAbsent(name, TowedBanner::paint);
    }

    private static Picture paint(String name) {
        Font font = new Font(Font.SERIF, Font.BOLD, 1).deriveFont(PICTURE_HEIGHT * LETTERS);
        BufferedImage measure = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D measuring = measure.createGraphics();
        measuring.setFont(font);
        FontMetrics metrics = measuring.getFontMetrics();
        measuring.dispose();
        int width = metrics.stringWidth(name) + 2 * MARGIN;
        BufferedImage drawn = new BufferedImage(width, PICTURE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = drawn.createGraphics();
        graphics.setColor(new Color(CLOTH, true));
        graphics.fillRect(0, 0, width, PICTURE_HEIGHT);
        graphics.setColor(new Color(EDGING, true));
        graphics.fillRect(0, 0, width, EDGE);
        graphics.fillRect(0, PICTURE_HEIGHT - EDGE, width, EDGE);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setFont(font);
        graphics.setColor(new Color(INK, true));
        graphics.drawString(name, MARGIN, (PICTURE_HEIGHT + metrics.getAscent() - metrics.getDescent()) / 2);
        graphics.dispose();
        NativeImage image = new NativeImage(width, PICTURE_HEIGHT, false);
        for (int y = 0; y < PICTURE_HEIGHT; y++) {
            for (int x = 0; x < width; x++) {
                int argb = drawn.getRGB(x, y);
                // NativeImage keeps its pixels as ABGR.
                image.setPixelRGBA(x, y, argb & 0xFF00FF00 | (argb & 0xFF) << 16 | argb >> 16 & 0xFF);
            }
        }
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "towed_banner/" + Integer.toHexString(name.hashCode()));
        Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(image));
        double length = Mth.clamp(HEIGHT * width / PICTURE_HEIGHT, SHORTEST, LONGEST);
        return new Picture(texture, length);
    }
}
