package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.airframe.FlankScreen;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.block.FlagHalyardBlockEntity;
import dev.aerolarge.block.PictureBoardBlock;
import dev.aerolarge.block.PictureBoardBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Draws a zeppelin's flank screens from her picture board (see {@link PictureBoardBlock}): on each flank, a glowing
 * panel in a dark bezel following the hull's curve just off the cover (see {@link FlankScreen}), reading the right way
 * round from either side, and the same picture small on the board itself, over its buttons. It shows a travel poster
 * or an advert under its headline, her colours flying as a flag, a map handed to the board, the faces of everyone
 * aboard, or her height and speed in huge figures; dark glass when off. A new picture flickers on. It glows at full
 * brightness, so it is seen at night.
 */
public class FlankScreenRenderer implements BlockEntityRenderer<PictureBoardBlockEntity> {
    /** How far off the screens are drawn, in blocks: a zeppelin's length. */
    private static final int VIEW_DISTANCE = 512;
    /** The bezel round each screen, in blocks, and how many strips each screen is drawn in round the hull's curve. */
    private static final float BEZEL = 1;
    private static final int ROWS = 10;
    /** How far out each layer of a picture stands from the one under it, in blocks, on a flank and on the board. */
    private static final float LAYER_STEP = 0.03F;
    private static final float FLAT_STEP = 0.002F;
    /** How long a new picture flickers on, in ticks, how often, and how dim it is between. */
    private static final int FLICKER_TICKS = 12;
    private static final int FLICKER_EVERY = 2;
    private static final float FLICKER_DIM = 0.3F;
    /** The little screen on the board: its left, right and bottom on the face, from the block's middle, in blocks. */
    private static final float MINI_LEFT = -7 / 16F;
    private static final float MINI_RIGHT = 7 / 16F;
    private static final float MINI_BOTTOM = 2.2F / 16;
    /** How far off the board's face the little screen is drawn, in blocks. */
    private static final float MINI_LIFT = 0.004F;
    /** How much of a poster the band under its headline takes, and how much of the band its letters do. */
    private static final float HEADLINE_BAND = 0.28F;
    private static final float HEADLINE_FILL = 0.7F;
    /** How much of the screen's height a flag, a map, a big face and the figures take; how many faces a row holds. */
    private static final float PICTURE_FILL = 0.86F;
    private static final float FIGURE_FILL = 0.55F;
    /**
     * The gap between a figure's sign and the figure, for the figure's height, and how much of its stretch a figure may
     * fill.
     */
    private static final float FIGURE_GAP = 0.25F;
    private static final float FIGURE_ROOM = 0.9F;
    private static final int BIG_FACES = 4;
    private static final int SMALL_FACES = 8;
    /** How long each page of faces shows when there are more than fit, in ticks. */
    private static final int PAGE_TICKS = 100;
    /** A zeppelin climbing or sinking slower than this, in blocks a second, holds her height. */
    private static final double LEVEL = 0.3;
    private static final double KMH_PER_BLOCK_A_SECOND = 3.6;
    private static final int OFF_GLASS = 0xFF14181E;
    private static final int BEZEL_COLOUR = 0xFF2A2F36;
    private static final int BAND = 0xFF101820;
    private static final int BACKDROP = 0xFF1B2838;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int CLIMBING = 0xFF5BE07A;
    private static final int SINKING = 0xFFF0B040;

    @Override
    public void render(PictureBoardBlockEntity board, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!(board.getLevel() instanceof ClientLevel level)) {
            return;
        }
        List<Layer> picture = picture(board, level);
        float glow = glow(board, level.getGameTime());
        int lit = picture.isEmpty() ? light : LightTexture.FULL_BRIGHT;
        pose.pushPose();
        Direction facing = board.getBlockState().getValue(PictureBoardBlock.FACING);
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(0, 0, PictureBoardBlock.THICKNESS / 16F - 0.5F + MINI_LIFT);
        FlankScreen place = FlankScreen.of(board.size());
        float width = MINI_RIGHT - MINI_LEFT;
        float height = width * (float) (place.height() / place.length());
        draw(new Flat(MINI_LEFT, MINI_BOTTOM + height, width, height), pose.last(), buffers, picture, lit, glow, FLAT_STEP);
        pose.popPose();
        BlockPos screens = board.screens();
        if (screens == null) {
            return;
        }
        for (boolean starboard : new boolean[]{false, true}) {
            draw(Cylinder.of(screens, board.aft(), starboard, place), pose.last(), buffers, picture, lit, glow, LAYER_STEP);
        }
    }

    /** One layer of a picture: what it is drawn with and where, on the screen from 0 to 1 across and down. */
    private record Layer(Kind kind, @Nullable ResourceLocation texture, @Nullable Banner banner, int colour,
                         float u0, float v0, float u1, float v1, float tu0, float tv0, float tu1, float tv1) {
        static Layer fill(int colour, float u0, float v0, float u1, float v1) {
            return new Layer(Kind.FILL, null, null, colour, u0, v0, u1, v1, 0, 0, 1, 1);
        }

        static Layer texture(ResourceLocation texture, int colour, float u0, float v0, float u1, float v1, float tu0, float tv0, float tu1, float tv1) {
            return new Layer(Kind.TEXTURE, texture, null, colour, u0, v0, u1, v1, tu0, tv0, tu1, tv1);
        }
    }

    private enum Kind {
        FILL, TEXTURE, FLAG
    }

    /** A banner, flown as a flag on the screen. */
    private record Banner(DyeColor base, BannerPatternLayers patterns) {
    }

    /** What the board's picture is, as layers, bottom first, inside the bezel; none when the screens are off. */
    private static List<Layer> picture(PictureBoardBlockEntity board, ClientLevel level) {
        List<Layer> layers = new ArrayList<>();
        FlankScreen place = FlankScreen.of(board.size());
        float bu = BEZEL / place.length();
        float bv = (float) (BEZEL / place.height());
        Frame frame = new Frame(bu, bv, 1 - bu, 1 - bv, (float) (place.height() / place.length()));
        switch (board.picture()) {
            case POSTERS -> poster(layers, frame, board.poster());
            case COLOURS -> colours(layers, frame, board, level);
            case MAP -> map(layers, frame, board, level);
            case FACES -> faces(layers, frame, board, level);
            case FLIGHT -> flight(layers, frame, board);
            case OFF -> {
                return layers;
            }
        }
        layers.add(0, Layer.fill(BEZEL_COLOUR, 0, 0, 1, 1));
        return layers;
    }

    /**
     * The part of the screen inside its bezel, from 0 to 1 across and down, and how tall the screen is for how long, in
     * blocks.
     */
    private record Frame(float u0, float v0, float u1, float v1, float shape) {
        float width() {
            return u1 - u0;
        }

        float height() {
            return v1 - v0;
        }

        /** How wide something is across the screen for how tall it is down it, in the screen's own measures. */
        float across(float tall, float aspect) {
            return tall * aspect * shape;
        }
    }

    /** A poster: its picture across the whole screen, its headline in a dark band along the bottom. */
    private static void poster(List<Layer> layers, Frame frame, PictureBoardBlockEntity.Poster poster) {
        ResourceLocation picture = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/misc/poster_" + poster.key() + ".png");
        layers.add(Layer.texture(picture, WHITE, frame.u0(), frame.v0(), frame.u1(), frame.v1(), 0, 0, 1, 1));
        float bandTop = frame.v1() - frame.height() * HEADLINE_BAND;
        layers.add(Layer.fill(BAND, frame.u0(), bandTop, frame.u1(), frame.v1()));
        ScreenType.Line headline = ScreenType.line(Component.translatable("poster.aerolarge." + poster.key()).getString());
        centred(layers, frame, headline, WHITE, (bandTop + frame.v1()) / 2, frame.height() * HEADLINE_BAND * HEADLINE_FILL);
    }

    /** The ship's colours flying as a flag across the middle of the screen, or a dark screen while none fly. */
    private static void colours(List<Layer> layers, Frame frame, PictureBoardBlockEntity board, ClientLevel level) {
        layers.add(Layer.fill(BACKDROP, frame.u0(), frame.v0(), frame.u1(), frame.v1()));
        FlagHalyardBlockEntity halyard = halyard(board, level);
        if (halyard == null) {
            return;
        }
        float tall = frame.height() * PICTURE_FILL;
        float wide = frame.across(tall, 2);
        float u0 = (frame.u0() + frame.u1() - wide) / 2;
        float v0 = (frame.v0() + frame.v1() - tall) / 2;
        layers.add(new Layer(Kind.FLAG, null, new Banner(halyard.base(), halyard.patterns()), WHITE, u0, v0, u0 + wide, v0 + tall, 0, 0, 1, 1));
    }

    /** The map handed to the board, square in the middle of the screen. */
    private static void map(List<Layer> layers, Frame frame, PictureBoardBlockEntity board, ClientLevel level) {
        layers.add(Layer.fill(BACKDROP, frame.u0(), frame.v0(), frame.u1(), frame.v1()));
        ResourceLocation picture = board.map().isEmpty() ? null : ScreenMaps.picture(level, board.map());
        if (picture == null) {
            return;
        }
        float tall = frame.height() * PICTURE_FILL;
        float wide = frame.across(tall, 1);
        float u0 = (frame.u0() + frame.u1() - wide) / 2;
        float v0 = (frame.v0() + frame.v1() - tall) / 2;
        layers.add(Layer.texture(picture, WHITE, u0, v0, u0 + wide, v0 + tall, 0, 0, 1, 1));
    }

    /**
     * The faces of everyone aboard, from their skins: a few big in a row, more smaller in two, and more than those a
     * page at a time.
     */
    private static void faces(List<Layer> layers, Frame frame, PictureBoardBlockEntity board, ClientLevel level) {
        layers.add(Layer.fill(BACKDROP, frame.u0(), frame.v0(), frame.u1(), frame.v1()));
        List<Player> aboard = aboard(board, level);
        boolean big = aboard.size() <= BIG_FACES;
        int perRow = big ? BIG_FACES : SMALL_FACES;
        int rows = big ? 1 : 2;
        int page = aboard.isEmpty() ? 0 : (int) (level.getGameTime() / PAGE_TICKS % ((aboard.size() + perRow * rows - 1) / (perRow * rows)));
        float tall = frame.height() * PICTURE_FILL / rows;
        float wide = frame.across(tall, 1);
        for (int k = 0; k < Math.min(perRow * rows, aboard.size() - page * perRow * rows); k++) {
            int shown = Math.min(perRow, aboard.size() - page * perRow * rows - k / perRow * perRow);
            float gap = (frame.width() - shown * wide) / (shown + 1);
            float u0 = frame.u0() + gap + k % perRow * (wide + gap);
            float v0 = frame.v0() + (frame.height() - tall * rows) / 2 + k / perRow * tall;
            face(layers, aboard.get(page * perRow * rows + k), u0, v0, wide, tall);
        }
    }

    /** One face, its hat over it, from a player's skin. */
    private static void face(List<Layer> layers, Player player, float u0, float v0, float wide, float tall) {
        ResourceLocation skin = skin(player);
        float inset = wide * 0.06F;
        layers.add(Layer.texture(skin, WHITE, u0 + inset, v0 + tall * 0.06F, u0 + wide - inset, v0 + tall * 0.94F, 8 / 64F, 8 / 64F, 16 / 64F, 16 / 64F));
        layers.add(Layer.texture(skin, WHITE, u0 + inset, v0 + tall * 0.06F, u0 + wide - inset, v0 + tall * 0.94F, 40 / 64F, 8 / 64F, 48 / 64F, 16 / 64F));
    }

    /**
     * Her height above the ground and her speed, in huge figures: an arrow showing whether she climbs, a chevron her
     * speed.
     */
    private static void flight(List<Layer> layers, Frame frame, PictureBoardBlockEntity board) {
        layers.add(Layer.fill(BACKDROP, frame.u0(), frame.v0(), frame.u1(), frame.v1()));
        SableClient.Ride ride = SableClient.ride(board);
        double height = ride == null ? 0 : ride.heightAboveGround();
        double climb = ride == null ? 0 : ride.velocity().y;
        double speed = ride == null ? 0 : Math.hypot(ride.velocity().x, ride.velocity().z) * KMH_PER_BLOCK_A_SECOND;
        String arrow = climb > LEVEL ? "▲" : climb < -LEVEL ? "▼" : "■";
        int arrowColour = climb > LEVEL ? CLIMBING : climb < -LEVEL ? SINKING : WHITE;
        float half = frame.width() / 2;
        figure(layers, frame, ScreenType.changing("flight_arrow", arrow), arrowColour,
            ScreenType.changing("flight_height", String.format(Locale.ROOT, "%d m", Math.round(height))), frame.u0(), half);
        figure(layers, frame, ScreenType.line("»"), WHITE,
            ScreenType.changing("flight_speed", String.format(Locale.ROOT, "%d km/h", Math.round(speed))), frame.u0() + half, half);
    }

    /**
     * A figure with its sign before it, in the middle of a stretch of the screen across, as big as fits: the sign in
     * its colour, the figure white.
     */
    private static void figure(List<Layer> layers, Frame frame, ScreenType.Line sign, int colour, ScreenType.Line figure, float from, float room) {
        float tall = frame.height() * FIGURE_FILL;
        float gap = frame.across(tall, FIGURE_GAP);
        float wide = frame.across(tall, sign.aspect()) + gap + frame.across(tall, figure.aspect());
        float fits = Math.min(1, room * FIGURE_ROOM / wide);
        float middle = (frame.v0() + frame.v1()) / 2;
        float u0 = from + (room - wide * fits) / 2;
        layers.add(Layer.texture(sign.texture(), colour, u0, middle - tall * fits / 2, u0 + frame.across(tall * fits, sign.aspect()), middle + tall * fits / 2,
            0, 0, 1, 1));
        float figureFrom = u0 + frame.across(tall * fits, sign.aspect()) + gap * fits;
        layers.add(Layer.texture(figure.texture(), WHITE, figureFrom, middle - tall * fits / 2, figureFrom + frame.across(tall * fits, figure.aspect()),
            middle + tall * fits / 2, 0, 0, 1, 1));
    }

    /**
     * A line of type centred across the screen, its middle at a height down it, so tall, narrower if it would not fit.
     */
    private static void centred(List<Layer> layers, Frame frame, ScreenType.Line line, int colour, float middle, float tall) {
        float wide = frame.across(tall, line.aspect());
        float fits = Math.min(1, frame.width() * 0.96F / wide);
        float u0 = (frame.u0() + frame.u1() - wide * fits) / 2;
        layers.add(Layer.texture(line.texture(), colour, u0, middle - tall * fits / 2, u0 + wide * fits, middle + tall * fits / 2, 0, 0, 1, 1));
    }

    /** The halyard flying the colours of the zeppelin a board is in; null for none. */
    @Nullable
    private static FlagHalyardBlockEntity halyard(PictureBoardBlockEntity board, ClientLevel level) {
        BoundingBox frame = frame(board, level);
        for (FlagHalyardBlockEntity halyard : FlagHalyardBlockEntity.loaded(level)) {
            if (frame != null && frame.isInside(halyard.getBlockPos()) && halyard.flying()) {
                return halyard;
            }
        }
        return null;
    }

    /** Everyone aboard the zeppelin a board is in: riding her in flight, or inside her frame on the ground. */
    private static List<Player> aboard(PictureBoardBlockEntity board, ClientLevel level) {
        BoundingBox frame = frame(board, level);
        List<Player> aboard = new ArrayList<>();
        for (Player player : level.players()) {
            if (SableClient.isAboard(player, board) || frame != null && frame.isInside(player.blockPosition())) {
                aboard.add(player);
            }
        }
        return aboard;
    }

    @Nullable
    private static BoundingBox frame(PictureBoardBlockEntity board, ClientLevel level) {
        AutopilotBlockEntity zeppelin = AutopilotBlockEntity.zeppelinFraming(level, board.getBlockPos());
        return zeppelin == null ? null : zeppelin.frame();
    }

    private static ResourceLocation skin(Player player) {
        PlayerInfo info = Minecraft.getInstance().getConnection() == null ? null : Minecraft.getInstance().getConnection().getPlayerInfo(player.getUUID());
        return info != null ? info.getSkin().texture() : DefaultPlayerSkin.get(player.getUUID()).texture();
    }

    /** How bright the picture is now: flickering for a moment after it changes. */
    private static float glow(PictureBoardBlockEntity board, long time) {
        long since = time - board.changed();
        return since >= 0 && since < FLICKER_TICKS && since / FLICKER_EVERY % 2 == 0 ? FLICKER_DIM : 1;
    }

    /**
     * Draws a picture's layers onto a surface, each a little further out than the one under it; dark glass when off.
     */
    private static void draw(Surface surface, PoseStack.Pose pose, MultiBufferSource buffers, List<Layer> picture, int light, float glow, float step) {
        if (picture.isEmpty()) {
            quad(surface, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE)), Layer.fill(OFF_GLASS, 0, 0, 1, 1), 0, light, 1);
            return;
        }
        for (int k = 0; k < picture.size(); k++) {
            Layer layer = picture.get(k);
            float lift = k * step;
            switch (layer.kind()) {
                case FILL -> quad(surface, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE)), layer, lift, light, glow);
                case TEXTURE -> quad(surface, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(layer.texture())), layer, lift, light, glow);
                case FLAG -> flag(surface, pose, buffers, layer, lift, light, glow);
            }
        }
    }

    /**
     * A banner on the screen as a flag: its top at the hoist, its left edge along the flag's top, its layers in turn.
     */
    private static void flag(Surface surface, PoseStack.Pose pose, MultiBufferSource buffers, Layer layer, float lift, int light, float glow) {
        Banner banner = layer.banner();
        BannerQuads.layers(buffers, banner.base(), banner.patterns(), (consumer, colour) -> quad(surface, pose, consumer,
            new Layer(Kind.FLAG, null, banner, colour, layer.u0(), layer.v0(), layer.u1(), layer.v1(), 0, 0, 1, 1), lift, light, glow));
    }

    /** A layer as quads on a surface, strip by strip down it, tinted its colour, dimmed by the flicker. */
    private static void quad(Surface surface, PoseStack.Pose pose, VertexConsumer consumer, Layer layer, float lift, int light, float glow) {
        int rows = surface.rows();
        int colour = tint(layer.colour(), glow);
        for (int row = 0; row < rows; row++) {
            float a = row / (float) rows;
            float b = (row + 1) / (float) rows;
            corner(surface, pose, consumer, layer, colour, light, lift, 0, a);
            corner(surface, pose, consumer, layer, colour, light, lift, 0, b);
            corner(surface, pose, consumer, layer, colour, light, lift, 1, b);
            corner(surface, pose, consumer, layer, colour, light, lift, 1, a);
        }
    }

    /** A corner of a layer's strip, at shares across and down the layer. */
    private static void corner(Surface surface, PoseStack.Pose pose, VertexConsumer consumer, Layer layer, int colour, int light, float lift, float across,
                               float down) {
        float u = layer.u0() + (layer.u1() - layer.u0()) * across;
        float v = layer.v0() + (layer.v1() - layer.v0()) * down;
        Vector3f at = surface.at(u, v, lift);
        boolean flag = layer.kind() == Kind.FLAG;
        float tu = flag ? BannerQuads.FACE_LEFT + down * BannerQuads.FACE_WIDTH : layer.tu0() + (layer.tu1() - layer.tu0()) * across;
        float tv = flag ? BannerQuads.FACE_TOP + across * BannerQuads.FACE_HEIGHT : layer.tv0() + (layer.tv1() - layer.tv0()) * down;
        consumer.addVertex(pose, at.x, at.y, at.z)
            .setColor(colour)
            .setUv(tu, tv)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, 0, 1, 0);
    }

    private static int tint(int colour, float glow) {
        int r = Math.round((colour >> 16 & 0xFF) * glow);
        int g = Math.round((colour >> 8 & 0xFF) * glow);
        int b = Math.round((colour & 0xFF) * glow);
        return colour & 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Where a picture is drawn: from 0 to 1 across it and down it, so far out from it. */
    private interface Surface {
        Vector3f at(float across, float down, float lift);

        /** How many strips it is drawn in down it, to follow its curve. */
        int rows();
    }

    /** The little screen on the board's face, flat, from its top left corner, in the face's own measures. */
    private record Flat(float left, float top, float width, float height) implements Surface {
        @Override
        public Vector3f at(float across, float down, float lift) {
            return new Vector3f(left + across * width, top - down * height, lift);
        }

        @Override
        public int rows() {
            return 1;
        }
    }

    /**
     * A flank screen, curved round the hull just off the cover: where the hull's axis is level with its front edge,
     * from the board, the way aft, the way out to its flank, and up; on the starboard flank it runs from aft forward,
     * so it reads the right way round from that side too.
     */
    private record Cylinder(Vector3f axis, Vector3f aft, Vector3f out, boolean starboard, FlankScreen place) implements Surface {
        static Cylinder of(BlockPos screens, Direction aft, boolean starboard, FlankScreen place) {
            Direction out = starboard ? aft.getCounterClockWise() : aft.getClockWise();
            return new Cylinder(new Vector3f(screens.getX() + 0.5F, screens.getY(), screens.getZ() + 0.5F), aft.step(), out.step(), starboard, place);
        }

        @Override
        public Vector3f at(float across, float down, float lift) {
            float along = (starboard ? 1 - across : across) * place.length();
            float radius = (float) place.radius() + lift;
            float round = (float) (-(place.top() + down * place.height()) / place.radius());
            return new Vector3f(axis).add(new Vector3f(aft).mul(along)).add(new Vector3f(out).mul(radius * (float) Math.cos(round)))
                .add(0, radius * (float) Math.sin(round), 0);
        }

        @Override
        public int rows() {
            return ROWS;
        }
    }

    @Override
    public boolean shouldRenderOffScreen(PictureBoardBlockEntity board) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /** Round the board and both its screens. */
    @Override
    public AABB getRenderBoundingBox(PictureBoardBlockEntity board) {
        AABB around = new AABB(board.getBlockPos());
        BlockPos screens = board.screens();
        if (screens == null) {
            return around;
        }
        FlankScreen place = FlankScreen.of(board.size());
        Vec3 axis = Vec3.atLowerCornerOf(board.getBlockPos().offset(screens));
        Vec3 aft = Vec3.atLowerCornerOf(board.aft().getNormal()).scale(place.length());
        double reach = place.radius() + 1;
        return around.minmax(new AABB(axis, axis.add(aft)).inflate(reach, place.bottom() + 1, reach));
    }
}
