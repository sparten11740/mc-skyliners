package dev.skyliners.client;

import dev.skyliners.turret.LaserCannonBlockEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * The gunner's sight over the view from the freighter's laser cannon (see {@link GunnerView}), unless the GUI is
 * hidden: thin amber lines on a dark translucent frame, every reading a picture or a colour, never words. At the middle
 * a reticle, a broken ring round a cross, where the bolts land; it turns red, brackets closing in round it with a beep,
 * while it rests on a hostile mob in reach, and grey, its cross turned to a red X, where the cannon holds its fire:
 * into her own hull, or lower than its barrels go. To its right a thermometer, filling with each bolt and going from
 * amber to red as the cannon nears overheating, heat waves over it and the whole of it flashing while it is
 * overheated. To its left a ring seen from above, her hull in the middle and her bow at the top, with a beam out of it
 * the way the gunner looks, so they know which way they face from her bow. Under it, if the cannon has rocket pods, a
 * row of four little rockets on a dark plate, one for each in its pods: amber while loaded, red while the sight is
 * locked on to a hostile mob they would home in on, grey where the cannon holds its fire, and an outline once
 * launched, filling up from its tail as its rocket loads again, so the row empties from the left as they go and fills
 * back from the right.
 */
public final class GunnerSight {
    /** The frame: how far in from the screen's edges its dark margin reaches, and how long its corners' arms are. */
    private static final float FRAME_INSET = 14;
    private static final float CORNER = 18;
    /** The short marks at the middle of each of the frame's sides, pointing in at the reticle, how long. */
    private static final float SIDE_MARK = 6;
    /**
     * The reticle: its ring's radius and the gaps in it at each side, in degrees; how far out its cross's arms start
     * and end, inside the ring and outside it.
     */
    private static final float RING = 13;
    private static final float RING_GAP = 30;
    private static final float CROSS_FROM = 4;
    private static final float CROSS_TO = 8;
    private static final float STUB_FROM = 16;
    private static final float STUB_TO = 21;
    /**
     * The brackets round a hostile mob locked on to: how far out from the middle they start and close in to, how long
     * each arm is, and how many ticks they take to close.
     */
    private static final float BRACKET_OPEN = 28;
    private static final float BRACKET_SHUT = 19;
    private static final float BRACKET_ARM = 5;
    private static final float BRACKET_TICKS = 4;
    /**
     * The thermometer: how far right of the middle it stands; how far above and below the middle its tube reaches and
     * how wide it is; its bulb's radius, under the tube; the marks along its side, how many and how long; and the heat
     * waves over it, how far above the tube, how tall, how far apart and how far each sways either way.
     */
    private static final float HEAT_OUT = 54;
    private static final float TUBE_TOP = 28;
    private static final float TUBE_BOTTOM = 18;
    private static final float TUBE_HALF = 2.5F;
    private static final float BULB = 5;
    private static final int HEAT_MARKS = 4;
    private static final float HEAT_MARK = 3;
    private static final float WAVES_UP = 6;
    private static final float WAVE_TALL = 9;
    private static final float WAVE_APART = 4;
    private static final float WAVE_SWAY = 1.2F;
    /** The share of the heat that overheats the cannon from which the thermometer goes from amber toward red. */
    private static final float WARM_FROM = 0.5F;
    /**
     * The ring seen from above: how far left of the middle it stands and its radius; her hull's radius in the middle,
     * and her prongs' reach ahead of it and how far out from her middle line each is, inside and out; the mark at her
     * bow, how tall and wide.
     */
    private static final float BEARING_OUT = 58;
    private static final float BEARING_RING = 18;
    private static final float HULL = 7;
    private static final float PRONG_AHEAD = 5;
    private static final float PRONG_IN = 1.5F;
    private static final float PRONG_OUT = 4.5F;
    private static final float BOW_TALL = 4;
    private static final float BOW_WIDE = 3;
    /**
     * The row of rockets under the reticle: how far below the middle their middles are, and how far apart; each one's
     * half width, how far above its middle its nose's tip and its shoulder are, how far below its tail is, how far out
     * its fins reach past it and how far up its tail they start; and how far round the row its dark plate reaches.
     */
    private static final float ROCKETS_DOWN = 40;
    private static final float ROCKET_APART = 11;
    private static final float ROCKET_HALF = 2;
    private static final float ROCKET_TIP = 9;
    private static final float ROCKET_SHOULDER = 4;
    private static final float ROCKET_TAIL = 6;
    private static final float FIN_OUT = 2.5F;
    private static final float FIN_UP = 3;
    private static final float PLATE = 4;
    /** How many pieces a ring or a disc is drawn as. */
    private static final int ROUND = 32;
    /** How often what flashes flashes, in ticks a flash: slow enough to be gentle on the eyes. */
    private static final int FLASH_TICKS = 4;
    private static final float LINE = 1;

    private static final int AMBER = 0xFFFFB13B;
    private static final int AMBER_SOFT = 0xB0FFB13B;
    private static final int AMBER_FAINT = 0x40FFB13B;
    private static final int BEAM = 0x55FFB13B;
    private static final int RED = 0xFFFF4A3A;
    private static final int HOT_WHITE = 0xFFFFE8D0;
    private static final int GREY = 0xC0B4B8BE;
    private static final int SHADE = 0x7004070A;
    private static final int GLASS = 0x60040A10;
    /** Under the reticle's lines, so they show against anything bright. */
    private static final int SHADOW = 0x90000000;

    private GunnerSight() {
    }

    /** Over the view from a cannon's sight, unless the GUI is hidden. */
    public static void overlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LaserCannonBlockEntity cannon = GunnerView.cannon();
        LocalPlayer player = minecraft.player;
        if (cannon == null || player == null || minecraft.options.hideGui) {
            return;
        }
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(true);
        Matrix4f matrix = graphics.pose().last().pose();
        float width = graphics.guiWidth();
        float height = graphics.guiHeight();
        float x = width / 2;
        float y = height / 2;
        boolean flash = DrawingClock.wholeTicks() / FLASH_TICKS % 2 == 0;
        frame(graphics, matrix, width, height);
        reticle(graphics, matrix, x, y, partialTick, GunnerView.held());
        heat(graphics, matrix, x + HEAT_OUT, y, cannon.heat(partialTick), cannon.overheated(partialTick), flash);
        float facing = Mth.wrapDegrees(player.getViewYRot(partialTick) - cannon.bowYaw());
        bearing(graphics, matrix, x - BEARING_OUT, y, facing, halfWidth(minecraft, width, height));
        if (cannon.pods()) {
            int colour = GunnerView.held() ? GREY : GunnerView.locked() ? RED : AMBER;
            rockets(graphics, matrix, x, y + ROCKETS_DOWN, cannon.rockets(), cannon.loading(partialTick), colour);
        }
    }

    /**
     * The row of rockets with its middle at a point, on its dark plate: so many loaded, drawn in a colour, the next to
     * load filled so far, as a share, the rest outlines.
     */
    private static void rockets(GuiGraphics graphics, Matrix4f matrix, float x, float y, int loaded, float loading, int colour) {
        int count = LaserCannonBlockEntity.ROCKETS;
        float first = x - (count - 1) * ROCKET_APART / 2;
        float halfRow = (count - 1) * ROCKET_APART / 2 + ROCKET_HALF + FIN_OUT + PLATE;
        rect(graphics, matrix, x - halfRow, y - ROCKET_TIP - PLATE, x + halfRow, y + ROCKET_TAIL + PLATE, GLASS);
        for (int rocket = 0; rocket < count; rocket++) {
            float rocketX = first + rocket * ROCKET_APART;
            int fromEnd = count - rocket;
            if (fromEnd <= loaded) {
                rocketShape(graphics, matrix, rocketX, y, colour);
            } else if (fromEnd == loaded + 1) {
                rocketFilling(graphics, matrix, rocketX, y, loading);
            }
            rocketOutline(graphics, matrix, rocketX, y, fromEnd <= loaded ? colour : AMBER_SOFT);
        }
    }

    /** A little rocket standing with its middle at a point, nose up, filled in a colour. */
    private static void rocketShape(GuiGraphics graphics, Matrix4f matrix, float x, float y, int colour) {
        GuiShapes.triangle(graphics, matrix, x, y - ROCKET_TIP, x - ROCKET_HALF, y - ROCKET_SHOULDER, x + ROCKET_HALF, y - ROCKET_SHOULDER, colour);
        rect(graphics, matrix, x - ROCKET_HALF, y - ROCKET_SHOULDER, x + ROCKET_HALF, y + ROCKET_TAIL, colour);
        for (int side = -1; side <= 1; side += 2) {
            float root = x + side * ROCKET_HALF;
            GuiShapes.triangle(graphics, matrix, root, y + ROCKET_TAIL - FIN_UP, root + side * FIN_OUT, y + ROCKET_TAIL + 1, root, y + ROCKET_TAIL,
                colour);
        }
    }

    /** A little rocket loading again, its body filled from its tail up so far, as a share. */
    private static void rocketFilling(GuiGraphics graphics, Matrix4f matrix, float x, float y, float share) {
        float top = y + ROCKET_TAIL - (ROCKET_TAIL + ROCKET_SHOULDER) * Mth.clamp(share, 0, 1);
        rect(graphics, matrix, x - ROCKET_HALF, top, x + ROCKET_HALF, y + ROCKET_TAIL, AMBER_SOFT);
    }

    /** A little rocket's outline, nose up, its middle at a point. */
    private static void rocketOutline(GuiGraphics graphics, Matrix4f matrix, float x, float y, int colour) {
        float left = x - ROCKET_HALF;
        float right = x + ROCKET_HALF;
        float shoulder = y - ROCKET_SHOULDER;
        float tail = y + ROCKET_TAIL;
        GuiShapes.line(graphics, matrix, x, y - ROCKET_TIP, left, shoulder, LINE, colour);
        GuiShapes.line(graphics, matrix, x, y - ROCKET_TIP, right, shoulder, LINE, colour);
        GuiShapes.line(graphics, matrix, left, shoulder, left, tail, LINE, colour);
        GuiShapes.line(graphics, matrix, right, shoulder, right, tail, LINE, colour);
        GuiShapes.line(graphics, matrix, left - FIN_OUT, tail + 1, right + FIN_OUT, tail + 1, LINE, colour);
    }

    /** A dark translucent margin round the screen, its inner corners marked in amber, short marks at each side's middle. */
    private static void frame(GuiGraphics graphics, Matrix4f matrix, float width, float height) {
        float left = FRAME_INSET;
        float top = FRAME_INSET;
        float right = width - FRAME_INSET;
        float bottom = height - FRAME_INSET;
        rect(graphics, matrix, 0, 0, width, top, SHADE);
        rect(graphics, matrix, 0, bottom, width, height, SHADE);
        rect(graphics, matrix, 0, top, left, bottom, SHADE);
        rect(graphics, matrix, right, top, width, bottom, SHADE);
        corner(graphics, matrix, left, top, 1, 1);
        corner(graphics, matrix, right, top, -1, 1);
        corner(graphics, matrix, left, bottom, 1, -1);
        corner(graphics, matrix, right, bottom, -1, -1);
        float x = width / 2;
        float y = height / 2;
        GuiShapes.line(graphics, matrix, x, top, x, top + SIDE_MARK, LINE, AMBER_SOFT);
        GuiShapes.line(graphics, matrix, x, bottom, x, bottom - SIDE_MARK, LINE, AMBER_SOFT);
        GuiShapes.line(graphics, matrix, left, y, left + SIDE_MARK, y, LINE, AMBER_SOFT);
        GuiShapes.line(graphics, matrix, right, y, right - SIDE_MARK, y, LINE, AMBER_SOFT);
    }

    /** An L at a corner of the frame, its arms reaching in along both sides. */
    private static void corner(GuiGraphics graphics, Matrix4f matrix, float x, float y, int across, int down) {
        GuiShapes.line(graphics, matrix, x, y, x + across * CORNER, y, LINE, AMBER_SOFT);
        GuiShapes.line(graphics, matrix, x, y, x, y + down * CORNER, LINE, AMBER_SOFT);
    }

    /**
     * The reticle at the middle, shadowed so it shows on anything: amber; red, brackets closing in round it, on a
     * hostile mob in reach; grey with a red X for its cross where the cannon cannot fire.
     */
    private static void reticle(GuiGraphics graphics, Matrix4f matrix, float x, float y, float partialTick, boolean held) {
        boolean locked = !held && GunnerView.locked();
        int colour = held ? GREY : locked ? RED : AMBER;
        for (int pass = 0; pass < 2; pass++) {
            boolean shadow = pass == 0;
            float width = shadow ? LINE + 2 : LINE;
            ring(graphics, matrix, x, y, width, shadow ? SHADOW : colour);
            for (int side = 0; side < 4; side++) {
                double arm = side * Math.PI / 2 + (held ? Math.PI / 4 : 0);
                spoke(graphics, matrix, x, y, arm, CROSS_FROM, CROSS_TO, width, shadow ? SHADOW : held ? RED : colour);
                spoke(graphics, matrix, x, y, side * Math.PI / 2, STUB_FROM, STUB_TO, width, shadow ? SHADOW : colour);
            }
        }
        GuiShapes.disc(graphics, matrix, x, y, LINE, ROUND / 4, held ? RED : colour);
        if (locked) {
            float closing = Mth.clamp((GunnerView.lockedTicks() + partialTick) / BRACKET_TICKS, 0, 1);
            brackets(graphics, matrix, x, y, Mth.lerp(closing, BRACKET_OPEN, BRACKET_SHUT));
        }
    }

    /** The reticle's ring, broken at each side, so wide. */
    private static void ring(GuiGraphics graphics, Matrix4f matrix, float x, float y, float width, int colour) {
        double gap = Math.toRadians(RING_GAP / 2);
        for (int side = 0; side < 4; side++) {
            double from = side * Math.PI / 2 + gap;
            GuiShapes.arc(graphics, matrix, x, y, RING, from, from + Math.PI / 2 - 2 * gap, ROUND / 4, width, colour);
        }
    }

    /**
     * A short stroke out from the middle the way an angle points, clockwise from up, from one radius to another, so
     * wide.
     */
    private static void spoke(GuiGraphics graphics, Matrix4f matrix, float x, float y, double angle, float from, float to, float width, int colour) {
        float sin = (float) Math.sin(angle);
        float cos = (float) Math.cos(angle);
        GuiShapes.line(graphics, matrix, x + sin * from, y - cos * from, x + sin * to, y - cos * to, width, colour);
    }

    /** Red brackets at the four corners of a square round the middle, so far out, shadowed so they show on anything. */
    private static void brackets(GuiGraphics graphics, Matrix4f matrix, float x, float y, float out) {
        for (int pass = 0; pass < 2; pass++) {
            float width = pass == 0 ? LINE + 2 : LINE;
            int colour = pass == 0 ? SHADOW : RED;
            for (int corner = 0; corner < 4; corner++) {
                int across = corner % 2 == 0 ? -1 : 1;
                int down = corner < 2 ? -1 : 1;
                float cornerX = x + across * out;
                float cornerY = y + down * out;
                GuiShapes.line(graphics, matrix, cornerX + across * width / 2, cornerY, cornerX - across * BRACKET_ARM, cornerY, width, colour);
                GuiShapes.line(graphics, matrix, cornerX, cornerY, cornerX, cornerY - down * BRACKET_ARM, width, colour);
            }
        }
    }

    /**
     * The thermometer standing at a point, its tube filled from its bulb up as hot as the cannon is, a share of the
     * heat that overheats it: amber, going red past the halfway mark; overheated, flashing, heat waves rising over it.
     */
    private static void heat(GuiGraphics graphics, Matrix4f matrix, float x, float y, float share, boolean overheated, boolean flash) {
        int colour = overheated ? flash ? RED : HOT_WHITE : FastColor.ARGB32.lerp(Mth.clamp((share - WARM_FROM) / (1 - WARM_FROM), 0, 1), AMBER, RED);
        float top = y - TUBE_TOP;
        float bottom = y + TUBE_BOTTOM;
        float bulb = bottom + BULB;
        rect(graphics, matrix, x - TUBE_HALF - 2, top - 2, x + TUBE_HALF + 2, bottom, GLASS);
        GuiShapes.disc(graphics, matrix, x, bulb, BULB + 2, ROUND, GLASS);
        float level = bottom - (bottom - top) * Mth.clamp(share, 0, 1);
        rect(graphics, matrix, x - TUBE_HALF, level, x + TUBE_HALF, bottom + 1, colour);
        GuiShapes.disc(graphics, matrix, x, bulb, BULB - 1, ROUND, colour);
        int outline = overheated ? colour : AMBER_SOFT;
        GuiShapes.line(graphics, matrix, x - TUBE_HALF - 1, top, x - TUBE_HALF - 1, bottom + 1, LINE, outline);
        GuiShapes.line(graphics, matrix, x + TUBE_HALF + 1, top, x + TUBE_HALF + 1, bottom + 1, LINE, outline);
        GuiShapes.line(graphics, matrix, x - TUBE_HALF - 1.5F, top, x + TUBE_HALF + 1.5F, top, LINE, outline);
        GuiShapes.arc(graphics, matrix, x, bulb, BULB + 0.5F, Math.PI * 0.15, Math.PI * 1.85, ROUND, LINE, outline);
        for (int mark = 1; mark < HEAT_MARKS; mark++) {
            float markY = bottom - (bottom - top) * mark / HEAT_MARKS;
            GuiShapes.line(graphics, matrix, x - TUBE_HALF - 2, markY, x - TUBE_HALF - 2 - HEAT_MARK, markY, LINE, AMBER_SOFT);
        }
        waves(graphics, matrix, x, top - WAVES_UP, overheated ? colour : share >= WARM_FROM ? AMBER_SOFT : AMBER_FAINT);
    }

    /** Three wavy strokes rising from a point, as heat shimmers off something hot. */
    private static void waves(GuiGraphics graphics, Matrix4f matrix, float x, float y, int colour) {
        float step = WAVE_TALL / 3;
        for (int wave = -1; wave <= 1; wave++) {
            float waveX = x + wave * WAVE_APART;
            for (int piece = 0; piece < 3; piece++) {
                float sway = piece % 2 == 0 ? WAVE_SWAY : -WAVE_SWAY;
                GuiShapes.line(graphics, matrix, waveX - sway, y - piece * step, waveX + sway, y - (piece + 1) * step, LINE, colour);
            }
        }
    }

    /**
     * The ring seen from above at a point, her bow at its top: her hull in its middle, her two prongs ahead, a mark at
     * her bow, and a beam out of it the way the gunner looks, round from her bow, as wide as their view.
     */
    private static void bearing(GuiGraphics graphics, Matrix4f matrix, float x, float y, float facing, float halfWidth) {
        GuiShapes.disc(graphics, matrix, x, y, BEARING_RING + 2, ROUND, GLASS);
        beam(graphics, matrix, x, y, Math.toRadians(facing), Math.toRadians(halfWidth));
        GuiShapes.arc(graphics, matrix, x, y, BEARING_RING, 0, 2 * Math.PI, ROUND, LINE, AMBER_SOFT);
        GuiShapes.disc(graphics, matrix, x, y, HULL, ROUND, AMBER_FAINT);
        GuiShapes.arc(graphics, matrix, x, y, HULL, 0, 2 * Math.PI, ROUND, LINE, AMBER);
        float tip = y - HULL - PRONG_AHEAD;
        for (int side = -1; side <= 1; side += 2) {
            float inner = x + side * PRONG_IN;
            float outer = x + side * PRONG_OUT;
            float innerRoot = y - (float) Math.sqrt(HULL * HULL - PRONG_IN * PRONG_IN);
            float outerRoot = y - (float) Math.sqrt(HULL * HULL - PRONG_OUT * PRONG_OUT);
            GuiShapes.quad(graphics, matrix, inner, innerRoot, inner, tip, outer, tip, outer, outerRoot, AMBER_FAINT);
            GuiShapes.line(graphics, matrix, inner, innerRoot, inner, tip, LINE, AMBER);
            GuiShapes.line(graphics, matrix, outer, outerRoot, outer, tip, LINE, AMBER);
            GuiShapes.line(graphics, matrix, inner - side * LINE / 2, tip, outer + side * LINE / 2, tip, LINE, AMBER);
        }
        float bowY = y - BEARING_RING - 1;
        GuiShapes.triangle(graphics, matrix, x, bowY - BOW_TALL, x - BOW_WIDE, bowY, x + BOW_WIDE, bowY, AMBER);
    }

    /**
     * The beam out from her hull's edge to the ring, round from her bow by an angle and so wide either side, both in
     * radians, clockwise as seen from above, a soft fill between two bright edges.
     */
    private static void beam(GuiGraphics graphics, Matrix4f matrix, float x, float y, double look, double half) {
        float nearLeftX = x + HULL * (float) Math.sin(look - half);
        float nearLeftY = y - HULL * (float) Math.cos(look - half);
        float nearRightX = x + HULL * (float) Math.sin(look + half);
        float nearRightY = y - HULL * (float) Math.cos(look + half);
        float farLeftX = x + BEARING_RING * (float) Math.sin(look - half);
        float farLeftY = y - BEARING_RING * (float) Math.cos(look - half);
        float farRightX = x + BEARING_RING * (float) Math.sin(look + half);
        float farRightY = y - BEARING_RING * (float) Math.cos(look + half);
        GuiShapes.quad(graphics, matrix, nearLeftX, nearLeftY, farLeftX, farLeftY, farRightX, farRightY, nearRightX, nearRightY, BEAM);
        GuiShapes.arc(graphics, matrix, x, y, BEARING_RING - 1, look - half, look + half, ROUND / 4, 2, AMBER);
        GuiShapes.line(graphics, matrix, nearLeftX, nearLeftY, farLeftX, farLeftY, LINE, AMBER);
        GuiShapes.line(graphics, matrix, nearRightX, nearRightY, farRightX, farRightY, LINE, AMBER);
    }

    /**
     * How far either side of the middle the view reaches, in degrees, from the field of view as set and the screen's
     * shape: near enough for the beam, which leaves out the widening a sprint gives it.
     */
    private static float halfWidth(Minecraft minecraft, float width, float height) {
        double halfHeight = Math.toRadians(minecraft.options.fov().get() / 2.0);
        return (float) Math.toDegrees(Math.atan(Math.tan(halfHeight) * width / height));
    }

    private static void rect(GuiGraphics graphics, Matrix4f matrix, float x0, float y0, float x1, float y1, int colour) {
        GuiShapes.quad(graphics, matrix, x0, y0, x0, y1, x1, y1, x1, y0, colour);
    }
}
