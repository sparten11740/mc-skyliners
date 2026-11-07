package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.Skyliners;
import dev.skyliners.console.ConsoleShape;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * The casing of a freighter console module (see {@link ConsoleShape}), drawn in its block's own frame with block
 * textures, lit as a block is by the light where it stands and shaded by the way each face looks: on a floor a desk,
 * its fascia with a glowing strip along its top and a dark kick along its foot, its sloping top under the controls, the
 * leaning face round its screen, its back, and its sides where it ends; on a wall a plate; under a ceiling a wedge
 * leaning down. Slices of a ring are drawn as one moulded desk: each face's shading follows the ring round, from one end
 * of the slice to the other, so the light rolls round the curve instead of creasing where one slice meets the next.
 * Hands back the two faces the pictures and the controls go on.
 */
final class ConsoleCasing {
    private static final ResourceLocation CASING = block("console_casing");
    private static final ResourceLocation DECK = block("console_deck");
    /** The glowing strip along the top of a desk's fascia: how far under its top and how tall, in blocks. */
    private static final double STRIP_UNDER = 0.07;
    private static final double STRIP_TALL = 0.025;
    /** The dark kick along the foot of a desk's fascia, how tall, in blocks. */
    private static final double KICK_TALL = 0.09;
    /** How far a strip or a kick stands proud of the face it lies on, in blocks. */
    private static final double PROUD = 0.003;
    /**
     * How far in from a face's edges the pictures and controls on it keep, in blocks: the casing's bezel round the
     * screen, and the border round the controls.
     */
    private static final double BEZEL = 0.045;
    /** How far a plate on a wall keeps its faces in from its block's edges, in blocks, so neighbouring plates show apart. */
    private static final double PLATE_MARGIN = 0.03;
    /**
     * How nearly a face must look toward a ring's middle or away from it, as the cosine across the floor plan, for its
     * shading to roll round the ring: a desk's sides, looking along it, keep their own.
     */
    private static final double ROLLED_FROM = 0.7;
    private static final int STRIP = 0xFF46C8F0;
    private static final int KICK = 0xFF15171A;

    private ConsoleCasing() {
    }

    /**
     * A face of a module that carries pictures or controls: its four corners as whoever works it sees it, in the block's
     * own frame, left to right and bottom to top.
     */
    record Surface(Vec3 bottomLeft, Vec3 bottomRight, Vec3 topLeft, Vec3 topRight) {
        Vec3 bottomMiddle() {
            return bottomLeft.add(bottomRight).scale(0.5);
        }

        Vec3 topMiddle() {
            return topLeft.add(topRight).scale(0.5);
        }

        /** How wide it is, at its narrower edge, in blocks: what is drawn on it keeps within that. */
        double width() {
            return Math.min(bottomLeft.distanceTo(bottomRight), topLeft.distanceTo(topRight));
        }

        double height() {
            return bottomMiddle().distanceTo(topMiddle());
        }

        Vec3 right() {
            return bottomRight.subtract(bottomLeft).normalize();
        }

        Vec3 up() {
            return topMiddle().subtract(bottomMiddle()).normalize();
        }

        Vec3 out() {
            return right().cross(up()).normalize();
        }

        /** The same face kept so far in from each of its edges, in blocks. */
        Surface inset(double by) {
            Vec3 across = right().scale(by);
            Vec3 along = up().scale(by);
            return new Surface(bottomLeft.add(across).add(along), bottomRight.subtract(across).add(along), topLeft.add(across).subtract(along),
                topRight.subtract(across).subtract(along));
        }

        /**
         * Its own frame pushed onto the pose: the middle of its bottom edge the origin, x to the right, y up it and z out
         * of it toward whoever works it.
         */
        void push(PoseStack pose) {
            Vec3 origin = bottomMiddle();
            Vec3 x = right();
            Vec3 y = up();
            Vec3 z = out();
            pose.pushPose();
            pose.translate(origin.x, origin.y, origin.z);
            Matrix4f turn = new Matrix4f((float) x.x, (float) x.y, (float) x.z, 0, (float) y.x, (float) y.y, (float) y.z, 0,
                (float) z.x, (float) z.y, (float) z.z, 0, 0, 0, 0, 1);
            pose.last().pose().mul(turn);
            pose.last().normal().mul(new Matrix3f(turn));
        }
    }

    /** The two faces of a module: its controls' and its screen's, each kept in from the casing's edges. */
    record Faces(Surface controls, Surface screen) {
    }

    /** Draws a module's casing, lit with the light where it stands; the faces its controls and its screen go on. */
    static Faces draw(MultiBufferSource buffers, PoseStack.Pose pose, ConsoleShape shape, int light) {
        VertexConsumer solid = buffers.getBuffer(RenderType.entitySolid(TextureAtlas.LOCATION_BLOCKS));
        Painter painter = new Painter(solid, pose, light, shape.middle());
        return switch (shape.mount()) {
            case FLOOR -> desk(painter, shape);
            case WALL -> plate(painter, shape.facing());
            case CEILING -> wedge(painter, shape.facing());
        };
    }

    /** A desk: its fascia, top, screen's face, back and the sides it ends at. */
    private static Faces desk(Painter painter, ConsoleShape shape) {
        double front = ConsoleShape.FRONT_TOP;
        double deskBack = ConsoleShape.DESK_BACK;
        double screenTop = ConsoleShape.SCREEN_TOP;
        double share = ConsoleShape.DESK_SHARE;
        Surface fascia = new Surface(at(shape, 0, 0, 0), at(shape, 1, 0, 0), at(shape, 0, 0, front), at(shape, 1, 0, front));
        Surface top = new Surface(at(shape, 0, 0, front), at(shape, 1, 0, front), at(shape, 0, share, deskBack), at(shape, 1, share, deskBack));
        Surface screen = new Surface(at(shape, 0, share, deskBack), at(shape, 1, share, deskBack), at(shape, 0, 1, screenTop), at(shape, 1, 1, screenTop));
        Surface back = new Surface(at(shape, 1, 1, 0), at(shape, 0, 1, 0), at(shape, 1, 1, screenTop), at(shape, 0, 1, screenTop));
        painter.face(CASING, fascia);
        painter.face(DECK, top);
        painter.face(CASING, screen);
        painter.face(CASING, back);
        painter.strip(fascia, front - STRIP_UNDER - STRIP_TALL, front - STRIP_UNDER, STRIP);
        painter.strip(fascia, 0, KICK_TALL, KICK);
        if (shape.leftEnd()) {
            side(painter, shape, 0, true);
        }
        if (shape.rightEnd()) {
            side(painter, shape, 1, false);
        }
        return new Faces(top.inset(BEZEL), screen.inset(BEZEL));
    }

    /** A desk's side at one end, its outline the desk's profile, in two pieces: under its top and under its screen. */
    private static void side(Painter painter, ConsoleShape shape, double across, boolean left) {
        double share = ConsoleShape.DESK_SHARE;
        Vec3 frontFoot = at(shape, across, 0, 0);
        Vec3 frontTop = at(shape, across, 0, ConsoleShape.FRONT_TOP);
        Vec3 middleFoot = at(shape, across, share, 0);
        Vec3 middleTop = at(shape, across, share, ConsoleShape.DESK_BACK);
        Vec3 backFoot = at(shape, across, 1, 0);
        Vec3 backTop = at(shape, across, 1, ConsoleShape.SCREEN_TOP);
        if (left) {
            painter.face(CASING, new Surface(middleFoot, frontFoot, middleTop, frontTop));
            painter.face(CASING, new Surface(backFoot, middleFoot, backTop, middleTop));
        } else {
            painter.face(CASING, new Surface(frontFoot, middleFoot, frontTop, middleTop));
            painter.face(CASING, new Surface(middleFoot, backFoot, middleTop, backTop));
        }
    }

    /** A point of a desk: so far across from its left end, so far back from its near edge, and so high, in blocks. */
    private static Vec3 at(ConsoleShape shape, double across, double back, double height) {
        return shape.plan(across, back).add(0, height, 0);
    }

    /**
     * A plate on a wall: its face standing out from the wall, its edges round it, the screen over the controls on its
     * face, as whoever stands before it sees it.
     */
    private static Faces plate(Painter painter, Direction facing) {
        Vec3 out = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 right = Vec3.atLowerCornerOf(facing.getCounterClockWise().getNormal());
        Vec3 middle = new Vec3(0.5, 0.5, 0.5);
        double half = 0.5 - PLATE_MARGIN;
        Vec3 face = middle.add(out.scale(-0.5 + ConsoleShape.PLATE_DEPTH));
        Vec3 wall = middle.add(out.scale(-0.5));
        Surface front = box(face, right, half);
        painter.face(CASING, front);
        Surface back = box(wall, right, half);
        painter.face(CASING, new Surface(front.topLeft(), front.topRight(), back.topLeft(), back.topRight()));
        painter.face(CASING, new Surface(back.bottomLeft(), back.bottomRight(), front.bottomLeft(), front.bottomRight()));
        painter.face(CASING, new Surface(back.bottomLeft(), front.bottomLeft(), back.topLeft(), front.topLeft()));
        painter.face(CASING, new Surface(front.bottomRight(), back.bottomRight(), front.topRight(), back.topRight()));
        return split(front);
    }

    /** A square face round a middle, so far out each way, facing out with its right along the given way. */
    private static Surface box(Vec3 middle, Vec3 right, double half) {
        Vec3 across = right.scale(half);
        Vec3 up = new Vec3(0, half, 0);
        return new Surface(middle.subtract(across).subtract(up), middle.add(across).subtract(up), middle.subtract(across).add(up),
            middle.add(across).add(up));
    }

    /**
     * A wedge hung under a ceiling: its face leaning down toward whoever stands before it, from its back's foot, half a
     * block under the ceiling, up to the ceiling over its front edge; its back and its sides.
     */
    private static Faces wedge(Painter painter, Direction facing) {
        Vec3 out = Vec3.atLowerCornerOf(facing.getNormal());
        Vec3 right = Vec3.atLowerCornerOf(facing.getCounterClockWise().getNormal());
        Vec3 middle = new Vec3(0.5, 1, 0.5);
        double low = -ConsoleShape.HUNG_DROP;
        Vec3 backLeft = middle.subtract(out.scale(0.5)).subtract(right.scale(0.5));
        Vec3 backRight = middle.subtract(out.scale(0.5)).add(right.scale(0.5));
        Vec3 frontLeft = middle.add(out.scale(0.5)).subtract(right.scale(0.5));
        Vec3 frontRight = middle.add(out.scale(0.5)).add(right.scale(0.5));
        Surface face = new Surface(backLeft.add(0, low, 0), backRight.add(0, low, 0), frontLeft, frontRight);
        painter.face(CASING, face);
        painter.face(CASING, new Surface(backRight.add(0, low, 0), backLeft.add(0, low, 0), backRight, backLeft));
        painter.face(CASING, new Surface(backLeft.add(0, low, 0), frontLeft, backLeft, frontLeft));
        painter.face(CASING, new Surface(frontRight, backRight.add(0, low, 0), frontRight, backRight));
        return split(face);
    }

    /** A plate's or a wedge's face split into its controls, under {@link ConsoleShape#SCREEN_FROM}, and its screen over. */
    private static Faces split(Surface face) {
        Vec3 leftSplit = face.bottomLeft().lerp(face.topLeft(), ConsoleShape.SCREEN_FROM);
        Vec3 rightSplit = face.bottomRight().lerp(face.topRight(), ConsoleShape.SCREEN_FROM);
        Surface controls = new Surface(face.bottomLeft(), face.bottomRight(), leftSplit, rightSplit);
        Surface screen = new Surface(leftSplit, rightSplit, face.topLeft(), face.topRight());
        return new Faces(controls.inset(BEZEL), screen.inset(BEZEL));
    }

    private static TextureAtlasSprite sprite(ResourceLocation texture) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
    }

    private static ResourceLocation block(String name) {
        return ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "block/" + name);
    }

    /**
     * Lays the casing's faces into a buffer: each tiled with a block texture, a block to a tile, its shading the way it
     * looks, or for a slice of a ring the way the ring looks at each of its corners, round from its middle.
     */
    private record Painter(VertexConsumer consumer, PoseStack.Pose pose, int light, @Nullable Vec3 ring) {
        void face(ResourceLocation texture, Surface surface) {
            TextureAtlasSprite sprite = sprite(texture);
            float u = (float) Math.min(1, surface.bottomLeft().distanceTo(surface.bottomRight()));
            float v = (float) Math.min(1, surface.height());
            Vec3 normal = surface.out();
            vertex(sprite, surface.bottomLeft(), 0, v, normal, light);
            vertex(sprite, surface.bottomRight(), u, v, normal, light);
            vertex(sprite, surface.topRight(), u, 0, normal, light);
            vertex(sprite, surface.topLeft(), 0, 0, normal, light);
        }

        /** A strip of one colour across a face, between two heights over its foot, standing just proud of it, glowing. */
        void strip(Surface surface, double from, double to, int colour) {
            TextureAtlasSprite sprite = sprite(CASING);
            Vec3 up = surface.up();
            Vec3 proud = surface.out().scale(PROUD);
            Vec3 normal = surface.out();
            int glow = colour == KICK ? light : LightTexture.FULL_BRIGHT;
            coloured(sprite, surface.bottomLeft().add(up.scale(from)).add(proud), normal, colour, glow);
            coloured(sprite, surface.bottomRight().add(up.scale(from)).add(proud), normal, colour, glow);
            coloured(sprite, surface.bottomRight().add(up.scale(to)).add(proud), normal, colour, glow);
            coloured(sprite, surface.bottomLeft().add(up.scale(to)).add(proud), normal, colour, glow);
        }

        private void vertex(TextureAtlasSprite sprite, Vec3 at, float u, float v, Vec3 normal, int lit) {
            Vec3 shaded = rolled(at, normal);
            consumer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(-1).setUv(sprite.getU(u), sprite.getV(v))
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(lit).setNormal(pose, (float) shaded.x, (float) shaded.y, (float) shaded.z);
        }

        private void coloured(TextureAtlasSprite sprite, Vec3 at, Vec3 normal, int colour, int lit) {
            Vec3 shaded = rolled(at, normal);
            consumer.addVertex(pose, (float) at.x, (float) at.y, (float) at.z).setColor(colour).setUv(sprite.getU(0.5F), sprite.getV(0.5F))
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(lit).setNormal(pose, (float) shaded.x, (float) shaded.y, (float) shaded.z);
        }

        /**
         * The way a face looks at a corner, rolled round a ring: its lean kept, the way it faces across the floor plan
         * turned to the ring's own there, toward the ring's middle or away from it as the face is; as it is off a ring.
         */
        private Vec3 rolled(Vec3 at, Vec3 normal) {
            double across = Math.hypot(normal.x, normal.z);
            if (ring == null || across < 1e-3) {
                return normal;
            }
            Vec3 inward = new Vec3(ring.x - at.x, 0, ring.z - at.z);
            if (inward.lengthSqr() < 1e-6) {
                return normal;
            }
            Vec3 toward = inward.normalize();
            double facing = (toward.x * normal.x + toward.z * normal.z) / across;
            if (Math.abs(facing) < ROLLED_FROM) {
                return normal;
            }
            Vec3 way = toward.scale(Math.signum(facing) * across);
            return new Vec3(way.x, normal.y, way.z);
        }
    }
}
