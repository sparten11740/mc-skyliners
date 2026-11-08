package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.skyliners.Skyliners;
import dev.skyliners.turret.LaserCannonBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/**
 * Draws the freighter's laser cannon's swivel on its pedestal (see {@link LaserCannonBlockEntity}), in worn, riveted
 * gunmetal plate as weathered as her hull (see {@link SpriteQuads}): a turntable ringed in bolts turning round to its
 * aim, a yoke standing on it, and between the yoke's cheeks a housing tilting up and down with it on bolted hubs, an
 * access hatch in each side, a louvred vent and worn hazard stripes on its top and a sight box at its back. Its twin
 * barrels reach out ahead, eight-sided: a finned cooling jacket with a hose along it, a slimmer barrel blued by heat,
 * and a blackened crown round the emitter at the muzzle. The emitter's lens, three coils round each barrel behind the
 * crown and a channel along its top glow at full brightness whatever the light round them: a low red ember at rest,
 * slowly breathing; brighter and whiter the hotter the cannon, flickering as it nears overheating; and flashing as a
 * bolt leaves that barrel. Outside its cheeks, on stubs through them and tilting with the housing, its two rocket pods
 * in chipped olive paint, banded in worn hazard stripes, each with two tubes: the red nose of a rocket shows in each
 * loaded tube, and a spent one is a dark hole, so the pods show at a glance how many rockets are left. Its pedestal is
 * its block's own model.
 */
public class LaserCannonRenderer implements BlockEntityRenderer<LaserCannonBlockEntity> {
    private static final ResourceLocation PLATE = texture("laser_cannon_plate");
    private static final ResourceLocation HATCH = texture("laser_cannon_hatch");
    private static final ResourceLocation TOP = texture("laser_cannon_top");
    private static final ResourceLocation JACKET = texture("laser_cannon_jacket");
    private static final ResourceLocation BARREL = texture("laser_cannon_barrel");
    private static final ResourceLocation CROWN = texture("laser_cannon_crown");
    private static final ResourceLocation LENS = texture("laser_cannon_lens");
    private static final ResourceLocation COIL = texture("laser_cannon_coil");
    private static final ResourceLocation POD = texture("laser_cannon_pod");
    private static final ResourceLocation HAZARD = texture("laser_cannon_hazard");
    private static final ResourceLocation TUBE = texture("laser_cannon_tube");
    private static final ResourceLocation ROCKET = texture("laser_cannon_rocket");
    private static final ResourceLocation RING = texture("laser_cannon_ring");
    private static final ResourceLocation HOSE = texture("laser_cannon_hose");
    /**
     * The yoke: how far out its cheeks stand either side and how thick they are, and how high its base is, in blocks:
     * on the top of the pedestal. The base between the cheeks is narrower than they are deep, so no side of it lies in
     * the plane of theirs.
     */
    private static final float CHEEK_OUT = 0.42F;
    private static final float CHEEK = 0.07F;
    private static final float YOKE_FOOT = 1.0F;
    /**
     * The turntable the yoke stands on: how far from its middle to the middle of each of its eight flats, and how thick
     * it is, in blocks, a little wider than the pedestal's drum under it; and how far over the drum's top it is drawn,
     * so the two never flicker.
     */
    private static final float TURNTABLE = 0.34F;
    private static final float TURNTABLE_THICK = 0.05F;
    private static final float TURNTABLE_LIFT = 0.002F;
    /**
     * The bolted hub plates on the outside of the yoke's cheeks that the housing tilts on: how far from their middle to
     * their flats, in blocks.
     */
    private static final float HUB = 0.075F;
    /**
     * The housing between the cheeks: its half width, half height and half length, in blocks; a little wider than its
     * barrels reach out, so their outer sides never lie in the plane of its own and flicker against them.
     */
    private static final float HOUSING_HALF_WIDTH = 0.39F;
    private static final float HOUSING_HALF_HEIGHT = 0.17F;
    private static final float HOUSING_HALF_LENGTH = 0.42F;
    /**
     * The sight box on the housing's top at its back, left of the middle: from where to where across, how high and
     * from where to where along, in blocks; well under the gunner's eye, so it never stands in their view.
     */
    private static final float SIGHT_FROM = -0.3F;
    private static final float SIGHT_TO = -0.16F;
    private static final float SIGHT_HIGH = 0.1F;
    private static final float SIGHT_BACK = -0.34F;
    private static final float SIGHT_FRONT = -0.1F;
    /**
     * How far proud of what it lies on a lens, a tube's mouth, a hub plate or a glowing channel is drawn, in blocks, so
     * neither flickers.
     */
    private static final float PROUD = 0.002F;
    /**
     * The barrels, each eight-sided, from just inside the housing out to the muzzle: the cooling jacket's distance from
     * its middle to its flats and where it ends, the slimmer barrel's, and the crown round the emitter's and how long
     * it is, in blocks; and the emitter's lens.
     */
    private static final float JACKET_ROUND = 0.095F;
    private static final float JACKET_END = 1.02F;
    private static final float BORE_ROUND = 0.06F;
    private static final float CROWN_ROUND = 0.085F;
    private static final float CROWN_LONG = 0.17F;
    private static final float LENS_ROUND = 0.055F;
    /**
     * The coils round each barrel behind its crown: how many, how far from the middle to their flats, how long each is
     * and how far apart they start, in blocks; and the channel along the barrel's top to them, its half width.
     */
    private static final int COILS = 3;
    private static final float COIL_ROUND = 0.068F;
    private static final float COIL_LONG = 0.035F;
    private static final float COIL_EVERY = 0.075F;
    private static final float CHANNEL_HALF = 0.014F;
    /**
     * The hose along the outside of each jacket, from the housing to near the jacket's end: its half thickness, how
     * far out from the barrel's middle and how far down it runs, and where it stops short of the jacket's end, in
     * blocks.
     */
    private static final float HOSE_HALF = 0.022F;
    private static final float HOSE_OUT = 0.1F;
    private static final float HOSE_DOWN = 0.055F;
    private static final float HOSE_SHORT = 0.06F;
    /** Where its yoke's cheeks and its barrels stand, across its middle line, in blocks. */
    private static final float[] CHEEKS = {-CHEEK_OUT, CHEEK_OUT};
    private static final float[] BARRELS = {(float) -LaserCannonBlockEntity.BARRELS_APART, (float) LaserCannonBlockEntity.BARRELS_APART};
    /** How far round its middle the swivel reaches, for leaving it out unseen, in blocks. */
    private static final float REACH = 2.3F;
    /**
     * Its rocket pods: their half width and half height, and how far back of the pivot they reach, in blocks; the
     * hazard band round each, where along it and how far proud of it; the stub each hangs on, how thick, from inside
     * its cheek to inside its pod.
     */
    private static final float POD_HALF_WIDTH = 0.12F;
    private static final float POD_HALF_HEIGHT = 0.2F;
    private static final float POD_BACK = -0.35F;
    private static final float BAND_FROM = 0.36F;
    private static final float BAND_TO = 0.44F;
    private static final float BAND_PROUD = 0.006F;
    private static final float STUB_HALF = 0.05F;
    private static final float STUB_IN = CHEEK_OUT;
    /**
     * Its tubes: the half width of each mouth; a loaded tube's rocket nose, its half width at the mouth and how far it
     * stands out, in blocks.
     */
    private static final float TUBE_HALF = 0.075F;
    private static final float NOSE_HALF = 0.06F;
    private static final float NOSE_OUT = 0.11F;
    /**
     * How brightly the barrels glow, as shares of full: at rest, how much more or less as they breathe and how long a
     * breath takes, in ticks; how much more at full heat; how much more as a bolt leaves, and how long that flash takes
     * to die away, in ticks, as long as the cannon takes between bolts, so each barrel's flash dies as the other fires;
     * and from what share of full heat they flicker, and how much they dim at most as they do.
     */
    private static final float GLOW_REST = 0.2F;
    private static final float GLOW_BREATH = 0.05F;
    private static final double BREATH_TICKS = 50;
    private static final float GLOW_HEAT = 0.5F;
    private static final float GLOW_FLASH = 0.8F;
    private static final float FLASH_TICKS = 5;
    private static final float FLICKER_FROM = 0.5F;
    private static final float FLICKER = 0.3F;
    /**
     * The glow's colours, which tint its grey textures: a dull ember at none, red-hot at {@link #RED_HOT_AT} of full,
     * the red of its bolts, and near white at full.
     */
    private static final int EMBER = 0xFF6E140A;
    private static final int RED_HOT = 0xFFFF3418;
    private static final int WHITE_HOT = 0xFFFFD8B0;
    private static final float RED_HOT_AT = 0.6F;

    @Override
    public void render(LaserCannonBlockEntity cannon, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (!Sight.ahead(pose.last(), 0.5F, (float) LaserCannonBlockEntity.PIVOT_UP, 0.5F, REACH)) {
            return;
        }
        SpriteQuads quads = new SpriteQuads(buffers.getBuffer(Sheets.cutoutBlockSheet()), pose, light);
        pose.pushPose();
        pose.translate(0.5, LaserCannonBlockEntity.PIVOT_UP, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-cannon.shownYaw(partialTick)));
        yoke(quads, pose);
        pose.mulPose(Axis.XP.rotationDegrees(-cannon.shownPitch(partialTick)));
        hubs(quads, pose);
        housing(quads);
        for (float side : BARRELS) {
            barrel(quads, side);
        }
        if (cannon.pods()) {
            pods(quads);
            tubes(quads, cannon);
        }
        for (float side : BARRELS) {
            glowing(quads.lit(LightTexture.FULL_BRIGHT, glowColour(glow(cannon, partialTick, (int) Math.signum(side)))), side);
        }
        pose.popPose();
    }

    /** The turntable turning round to its aim, and the yoke standing on it: its base and its two cheeks. */
    private static void yoke(SpriteQuads quads, PoseStack pose) {
        float foot = YOKE_FOOT - (float) LaserCannonBlockEntity.PIVOT_UP;
        float table = foot + TURNTABLE_LIFT;
        float tableTop = foot + TURNTABLE_THICK;
        pose.pushPose();
        pose.mulPose(Axis.XP.rotationDegrees(-90));
        quads.prism(sprite(PLATE), 0, 0, TURNTABLE, table, tableTop);
        quads.end(sprite(RING), 0, 0, TURNTABLE, tableTop, true, true);
        pose.popPose();
        SpriteQuads.Faces plate = SpriteQuads.Faces.all(sprite(PLATE));
        for (float side : CHEEKS) {
            quads.box(plate, side - CHEEK, tableTop, -CHEEK * 2, side + CHEEK, HOUSING_HALF_HEIGHT, CHEEK * 2);
        }
        quads.box(plate, -CHEEK_OUT, tableTop, -CHEEK, CHEEK_OUT, tableTop + CHEEK, CHEEK);
    }

    /**
     * The hub plates the housing tilts on, on the outside of the yoke's cheeks, turning with the housing, a ring of
     * bolts on each.
     */
    private static void hubs(SpriteQuads quads, PoseStack pose) {
        float face = CHEEK_OUT + CHEEK + PROUD;
        for (int side = -1; side <= 1; side += 2) {
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(side * 90));
            quads.end(sprite(RING), 0, 0, HUB, face, true, true);
            pose.popPose();
        }
    }

    /**
     * The housing between the cheeks, an access hatch in each side, its vent and hazard stripes on top, and its sight
     * box.
     */
    private static void housing(SpriteQuads quads) {
        TextureAtlasSprite plate = sprite(PLATE);
        quads.box(new SpriteQuads.Faces(sprite(TOP), plate, sprite(HATCH), plate), -HOUSING_HALF_WIDTH, -HOUSING_HALF_HEIGHT, -HOUSING_HALF_LENGTH,
            HOUSING_HALF_WIDTH, HOUSING_HALF_HEIGHT, HOUSING_HALF_LENGTH);
        quads.box(SpriteQuads.Faces.all(plate), SIGHT_FROM, HOUSING_HALF_HEIGHT, SIGHT_BACK, SIGHT_TO, HOUSING_HALF_HEIGHT + SIGHT_HIGH, SIGHT_FRONT);
        float lensHalf = (SIGHT_TO - SIGHT_FROM) / 3;
        float lensMiddle = (SIGHT_FROM + SIGHT_TO) / 2;
        float lensUp = HOUSING_HALF_HEIGHT + SIGHT_HIGH / 2;
        quads.square(sprite(TUBE), lensMiddle - lensHalf, lensUp - lensHalf, lensMiddle + lensHalf, lensUp + lensHalf, SIGHT_FRONT + PROUD);
    }

    /**
     * A barrel standing so far across its middle line, unlit by its glow: its finned jacket with the hose along it, the
     * slimmer barrel, and the blackened crown at its muzzle.
     */
    private static void barrel(SpriteQuads quads, float side) {
        float muzzle = (float) LaserCannonBlockEntity.BARREL;
        float crown = muzzle - CROWN_LONG;
        TextureAtlasSprite plate = sprite(PLATE);
        quads.prism(sprite(JACKET), side, 0, JACKET_ROUND, HOUSING_HALF_LENGTH - PROUD, JACKET_END);
        quads.end(plate, side, 0, JACKET_ROUND, JACKET_END, true, false);
        quads.prism(sprite(BARREL), side, 0, BORE_ROUND, JACKET_END, crown);
        quads.prism(sprite(CROWN), side, 0, CROWN_ROUND, crown, muzzle);
        quads.end(sprite(CROWN), side, 0, CROWN_ROUND, crown, false, false);
        quads.end(sprite(CROWN), side, 0, CROWN_ROUND, muzzle, true, false);
        float out = side + Math.signum(side) * HOSE_OUT;
        quads.box(SpriteQuads.Faces.all(sprite(HOSE)), out - HOSE_HALF, -HOSE_DOWN - HOSE_HALF, HOUSING_HALF_LENGTH - PROUD, out + HOSE_HALF,
            -HOSE_DOWN + HOSE_HALF, JACKET_END - HOSE_SHORT);
    }

    /**
     * What glows of a barrel standing so far across its middle line: its emitter's lens, its coils and the channel to
     * them.
     */
    private static void glowing(SpriteQuads quads, float side) {
        float muzzle = (float) LaserCannonBlockEntity.BARREL;
        float crown = muzzle - CROWN_LONG;
        quads.end(sprite(LENS), side, 0, LENS_ROUND, muzzle + PROUD, true, true);
        TextureAtlasSprite coil = sprite(COIL);
        for (int k = 1; k <= COILS; k++) {
            float back = crown - k * COIL_EVERY;
            quads.prism(coil, side, 0, COIL_ROUND, back, back + COIL_LONG);
        }
        quads.strip(coil, side, BORE_ROUND + PROUD, CHANNEL_HALF, JACKET_END, crown - COILS * COIL_EVERY);
    }

    /**
     * How brightly a barrel on one side of its middle line (1 toward positive x, -1 toward negative) glows at a
     * partial tick, as a share of full: a low ember at rest, slowly breathing; brighter the hotter the cannon, and
     * flickering from one tick to the next as it nears overheating; and a flash as a bolt leaves this barrel, dying
     * away. Breathing and flickering go by this client's own clock (see {@link DrawingClock}), the flash by its own
     * count since the bolt.
     */
    private static float glow(LaserCannonBlockEntity cannon, float partialTick, int side) {
        float heat = cannon.heat(partialTick);
        float breathing = GLOW_BREATH * (float) Math.sin(DrawingClock.ticks(partialTick) * 2 * Math.PI / BREATH_TICKS);
        float flash = cannon.lastBarrel() == side ? Math.max(0, 1 - cannon.sinceBolt(partialTick) / FLASH_TICKS) : 0;
        float flickering = Math.max(0, heat - FLICKER_FROM) / (1 - FLICKER_FROM);
        float flicker = flickering * FLICKER * flickerAt(DrawingClock.wholeTicks(), side);
        return Mth.clamp(GLOW_REST + breathing + heat * GLOW_HEAT + flash * GLOW_FLASH - flicker, 0, 1);
    }

    /**
     * How much a barrel's glow dims in a whole tick as it flickers, as a share of the most: drawn at random for each
     * tick and barrel, the same all through the tick.
     */
    private static float flickerAt(long tick, int side) {
        return (Mth.murmurHash3Mixer((int) (2 * tick) + (side > 0 ? 1 : 0)) >>> 8) / (float) (1 << 24);
    }

    /** The glow's colour at a brightness: from a dull ember through red-hot to near white. */
    private static int glowColour(float glow) {
        return glow < RED_HOT_AT ? FastColor.ARGB32.lerp(glow / RED_HOT_AT, EMBER, RED_HOT)
            : FastColor.ARGB32.lerp((glow - RED_HOT_AT) / (1 - RED_HOT_AT), RED_HOT, WHITE_HOT);
    }

    /** Its two rocket pods, either side of its housing on their stubs, banded in hazard stripes near their mouths. */
    private static void pods(SpriteQuads quads) {
        float mouth = (float) LaserCannonBlockEntity.POD_MOUTH;
        SpriteQuads.Faces pod = SpriteQuads.Faces.all(sprite(POD));
        SpriteQuads.Faces band = SpriteQuads.Faces.all(sprite(HAZARD));
        SpriteQuads.Faces plate = SpriteQuads.Faces.all(sprite(PLATE));
        for (int side = -1; side <= 1; side += 2) {
            float middle = side * (float) LaserCannonBlockEntity.POD_OUT;
            float inner = middle - side * POD_HALF_WIDTH;
            quads.box(pod, middle - POD_HALF_WIDTH, -POD_HALF_HEIGHT, POD_BACK, middle + POD_HALF_WIDTH, POD_HALF_HEIGHT, mouth);
            quads.box(band, middle - POD_HALF_WIDTH - BAND_PROUD, -POD_HALF_HEIGHT - BAND_PROUD, BAND_FROM, middle + POD_HALF_WIDTH + BAND_PROUD,
                POD_HALF_HEIGHT + BAND_PROUD, BAND_TO);
            quads.box(plate, Math.min(side * STUB_IN, inner), -STUB_HALF, -STUB_HALF, Math.max(side * STUB_IN, inner), STUB_HALF, STUB_HALF);
        }
    }

    /** The mouths of its pods' tubes, dark in their rims, and a rocket's red nose standing out of each loaded one. */
    private static void tubes(SpriteQuads quads, LaserCannonBlockEntity cannon) {
        float face = (float) LaserCannonBlockEntity.POD_MOUTH + PROUD;
        TextureAtlasSprite tube = sprite(TUBE);
        TextureAtlasSprite rocket = sprite(ROCKET);
        for (int k = 0; k < LaserCannonBlockEntity.ROCKETS; k++) {
            float x = (float) LaserCannonBlockEntity.tubeAcross(k);
            float y = (float) LaserCannonBlockEntity.tubeUp(k);
            quads.square(tube, x - TUBE_HALF, y - TUBE_HALF, x + TUBE_HALF, y + TUBE_HALF, face);
            if (cannon.tubeLoaded(k)) {
                quads.pyramid(rocket, x, y, NOSE_HALF, face, NOSE_OUT);
            }
        }
    }

    private static TextureAtlasSprite sprite(ResourceLocation texture) {
        return SpriteQuads.sprite(texture);
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "block/" + name);
    }

    @Override
    public AABB getRenderBoundingBox(LaserCannonBlockEntity cannon) {
        return new AABB(cannon.getBlockPos()).inflate(LaserCannonBlockEntity.BARREL + 1);
    }
}
