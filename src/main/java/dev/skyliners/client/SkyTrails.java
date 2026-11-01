package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.skyliners.Skyliners;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.block.SkywritingBoardBlockEntity;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The smoke of the zeppelins' skywriting, as this client sees it (see {@link SkywritingBoardBlockEntity}): while one
 * writes a shape, a puff left under her belly every couple of blocks she flies, in her gas cells' colours one after the
 * other, so the shape is drawn in coloured smoke across the sky. Each puff swells a little and fades away a quarter of
 * an hour later, the shape vanishing in the order it was drawn; all are drawn glowing, so the shape shows at night
 * too. Each client keeps the smoke it saw being made.
 */
public final class SkyTrails {
    private static final ResourceLocation PUFF = ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "textures/misc/sky_puff.png");
    private static final RenderType PUFF_TYPE = RenderType.entityTranslucent(PUFF);
    /** How far apart the puffs are left, in blocks, and how many puffs in a row are each colour. */
    private static final double PUFF_STEP = 2.5;
    private static final int PUFFS_A_COLOUR = 14;
    /** How many puffs a trail keeps, at most: a shape's worth, the oldest going first. */
    private static final int MOST_PUFFS = 1500;
    /**
     * How long a puff lasts and how long it takes to fade away at the end, in ticks: long enough that the longest
     * shape, some eight minutes in the writing, stands whole for minutes after.
     */
    private static final long LASTS = 20 * 60 * 15;
    private static final long FADES = 20 * 60;
    /** How big a puff is when it is left and how much it swells each tick after, in blocks across. */
    private static final float SIZE = 5;
    private static final float SWELL = 0.0005F;
    /** How near a puff is drawn, in blocks. */
    private static final double SEEN = 640;

    /** A puff of smoke: where it is in the world, the game time it was left, and its colour. */
    private record Puff(Vec3 at, long left, int colour) {
    }

    /**
     * Each board's smoke, by where the board is, so it stays as her zeppelin passes out of this client's sight and
     * back; and the level it was seen in, the smoke going with it.
     */
    private static final Map<BlockPos, Deque<Puff>> TRAILS = new HashMap<>();
    @Nullable
    private static ClientLevel seen;

    private SkyTrails() {
    }

    /** Once a tick: a puff under each zeppelin writing a shape, once she has flown on far enough. */
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != seen) {
            TRAILS.clear();
            seen = level;
        }
        if (level == null) {
            return;
        }
        long now = DrawingClock.wholeTicks();
        for (SkywritingBoardBlockEntity board : SkywritingBoardBlockEntity.loaded(level)) {
            if (board.writing() && board.smoke() != null) {
                smoke(level, board, now);
            }
        }
        TRAILS.values().forEach(trail -> trail.removeIf(puff -> now - puff.left() > LASTS));
        TRAILS.values().removeIf(Deque::isEmpty);
    }

    /** A puff under a writing zeppelin, if she has come far enough since the last, in the next of her colours. */
    private static void smoke(ClientLevel level, SkywritingBoardBlockEntity board, long now) {
        Vec3 source = SableClient.toWorld(level, Vec3.atCenterOf(board.getBlockPos().offset(board.smoke())));
        Deque<Puff> trail = TRAILS.computeIfAbsent(board.getBlockPos(), ignored -> new ArrayDeque<>());
        Puff last = trail.peekLast();
        if (last != null && last.at().distanceTo(source) < PUFF_STEP) {
            return;
        }
        String[] colours = colours(level, board);
        int colour = DyeColor.byName(colours[trail.size() / PUFFS_A_COLOUR % colours.length], DyeColor.WHITE).getTextureDiffuseColor();
        trail.addLast(new Puff(source, now, colour));
        while (trail.size() > MOST_PUFFS) {
            trail.removeFirst();
        }
    }

    /** The colours of a zeppelin's gas cells, bow to stern, as her livery has them. */
    private static String[] colours(ClientLevel level, SkywritingBoardBlockEntity board) {
        AutopilotBlockEntity autopilot = AutopilotBlockEntity.zeppelinFraming(level, board.getBlockPos());
        return autopilot != null ? autopilot.livery().cellColours() : new String[]{"white"};
    }

    /** After the particles are drawn: every puff near enough, turned to face the camera. */
    public static void stage(RenderLevelStageEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || level == null || TRAILS.isEmpty()) {
            return;
        }
        long now = DrawingClock.wholeTicks();
        Vec3 camera = event.getCamera().getPosition();
        Quaternionf facing = event.getCamera().rotation();
        Vector3f right = facing.transform(new Vector3f(1, 0, 0));
        Vector3f up = facing.transform(new Vector3f(0, 1, 0));
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(PUFF_TYPE);
        PoseStack.Pose pose = event.getPoseStack().last();
        for (Deque<Puff> trail : TRAILS.values()) {
            for (Puff puff : trail) {
                draw(consumer, pose, camera, right, up, puff, now);
            }
        }
        buffers.endBatch(PUFF_TYPE);
    }

    /** One puff, swollen and faded for its age, if near enough to see. */
    private static void draw(VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera, Vector3f right, Vector3f up, Puff puff, long now) {
        if (puff.at().distanceTo(camera) > SEEN) {
            return;
        }
        long age = now - puff.left();
        float half = (SIZE + age * SWELL) / 2;
        float alpha = age < LASTS - FADES ? 1 : Math.max(0, (LASTS - age) / (float) FADES);
        int colour = ((int) (alpha * 0xD0) << 24) | (puff.colour() & 0xFFFFFF);
        float x = (float) (puff.at().x - camera.x);
        float y = (float) (puff.at().y - camera.y);
        float z = (float) (puff.at().z - camera.z);
        corner(consumer, pose, x, y, z, right, up, -half, -half, colour, 0, 1);
        corner(consumer, pose, x, y, z, right, up, half, -half, colour, 1, 1);
        corner(consumer, pose, x, y, z, right, up, half, half, colour, 1, 0);
        corner(consumer, pose, x, y, z, right, up, -half, half, colour, 0, 0);
    }

    private static void corner(VertexConsumer consumer, PoseStack.Pose pose, float x, float y, float z, Vector3f right, Vector3f up, float across, float high,
                               int colour, float u, float v) {
        consumer.addVertex(pose, x + right.x * across + up.x * high, y + right.y * across + up.y * high, z + right.z * across + up.z * high)
            .setColor(colour).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
    }
}
