package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.BallastValveBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Vector3f;

/**
 * "Up ship!" as this client sees it (see {@link BallastValveBlockEntity}): as a zeppelin takes off from the ground,
 * her ground handlers stand round her in navy with white caps, each on a line made fast to her hull, and her ground
 * officer in his white-covered cap stands ahead of the control car. Her bow's water ballast pours from her belly in a
 * stream; on the call the officer's arm goes up, and as all let go the lines fall, the handlers step back from her and
 * stand watching her rise, and fade away. Each handler stands on the ground at their station, found once near the
 * ground her wheels stand on; a station with no dry ground there, over water or land not loaded, has nobody. They are
 * drawn, no creatures at all: nothing to bump into or hit, nothing to save.
 */
public final class UpShipScene {
    /**
     * How near a handler is drawn and the water poured, in blocks, about as far as the land round is drawn; and how
     * far from the valve at her bow any of her handlers stands, at most, in blocks: past her length.
     */
    private static final double SEEN = 160;
    private static final double SHIP_REACH = 500;
    /** How far up or down from a station's ground row a handler finds ground to stand on, in blocks. */
    private static final int GROUND_REACH = 8;
    private static final ResourceLocation HANDLER = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/entity/ground_handler.png");
    private static final ResourceLocation OFFICER = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "textures/entity/ground_officer.png");
    private static final RenderType HANDLER_TYPE = RenderType.entityTranslucent(HANDLER);
    private static final RenderType OFFICER_TYPE = RenderType.entityTranslucent(OFFICER);
    private static final RenderType LINE_TYPE = RenderType.entityCutoutNoCull(FlatQuads.WHITE);
    /**
     * How big a handler is drawn, as a player is, and how high a model stands over its feet before it is scaled; and
     * the box a handler takes up, how far either side of their feet and how tall, in blocks, to tell whether in view.
     */
    private static final float FIGURE_SCALE = 0.9375F;
    private static final float MODEL_FEET = 1.501F;
    private static final double FIGURE_HALF = 0.5;
    private static final double FIGURE_TALL = 2;
    /** How far a handler steps back from the ship as they let go, in blocks, over how many ticks. */
    private static final double STEP_BACK = 2;
    private static final int STEP_TICKS = 20;
    /** Where a handler's hands are on their line: how high over their feet and how far ahead, in blocks. */
    private static final double HANDS_HIGH = 1.35;
    private static final double HANDS_AHEAD = 0.5;
    /** The lines: how thick they are drawn, in blocks, and their manila colour. */
    private static final float LINE_THICK = 0.04F;
    private static final int LINE = 0xFFC9B27A;
    /** The water: how many drops a tick, how widely about the outlet, in blocks, and how many spray puffs a tick. */
    private static final int DROPS = 10;
    private static final double DROP_SPREAD = 0.5;
    private static final int SPRAY = 2;
    /**
     * The arms and the head, in radians: raised forward and up to the line, a little in toward each other; the
     * officer's straight up for the call; and the head tipped back looking up at the ship.
     */
    private static final float ARMS_ON_LINE = -2.2F;
    private static final float ARMS_IN = 0.25F;
    private static final float ARM_UP = -3.0F;
    /** How far out from his side the officer's raised arm leans, in radians, to read as a signal from afar. */
    private static final float ARM_OUT = 0.45F;
    private static final float LOOK_UP = -0.5F;
    /**
     * The officer's arm goes up so many ticks before his call, and stays up so many after the lines are let go: long
     * enough to be seen.
     */
    private static final int ARM_UP_BEFORE = 10;
    private static final int ARM_UP_AFTER = 30;
    /** How far the legs swing stepping back, in radians, and how fast, in radians a tick. */
    private static final float STRIDE = 0.6F;
    private static final float STRIDE_SPEED = 0.7F;

    private static final Map<BallastValveBlockEntity, Cast> CASTS = new WeakHashMap<>();
    @Nullable
    private static HumanoidModel<LivingEntity> body;

    private UpShipScene() {
    }

    /** A handler, or the officer, where they stand on the ground for one take-off. */
    private record Figure(Vec3 feet, BallastValveBlockEntity.Station station) {
        boolean officer() {
            return station.line() == null;
        }
    }

    /** Everyone on the ground for one take-off, from the game time it began, and the game time they were found at. */
    private record Cast(long started, List<Figure> figures, long foundAt) {
    }

    /** Once a tick: the bow's water pouring out of her belly while it pours. */
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            CASTS.clear();
            return;
        }
        long now = level.getGameTime();
        for (BallastValveBlockEntity valve : BallastValveBlockEntity.loaded(level)) {
            long since = valve.since(now);
            if (since >= BallastValveBlockEntity.POUR_FROM && since < BallastValveBlockEntity.POUR_TO && valve.outlet() != null) {
                pour(level, player, SableClient.toWorld(level, Vec3.atCenterOf(valve.getBlockPos().offset(valve.outlet()))));
            }
        }
    }

    /** A tick of the water pouring from an outlet in the world: drops falling in a stream, and spray. */
    private static void pour(ClientLevel level, LocalPlayer player, Vec3 outlet) {
        if (outlet.distanceTo(player.position()) > SEEN) {
            return;
        }
        for (int drop = 0; drop < DROPS; drop++) {
            level.addAlwaysVisibleParticle(ParticleTypes.FALLING_WATER, true, outlet.x + level.random.nextGaussian() * DROP_SPREAD, outlet.y,
                outlet.z + level.random.nextGaussian() * DROP_SPREAD, 0, 0, 0);
        }
        for (int puff = 0; puff < SPRAY; puff++) {
            level.addAlwaysVisibleParticle(ParticleTypes.SPLASH, true, outlet.x + level.random.nextGaussian() * DROP_SPREAD, outlet.y,
                outlet.z + level.random.nextGaussian() * DROP_SPREAD, 0, 0, 0);
        }
    }

    /** After the particles are drawn: everyone on the ground round each take-off on the lines near enough to see. */
    public static void stage(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || level == null) {
            return;
        }
        long now = level.getGameTime();
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        for (BallastValveBlockEntity valve : BallastValveBlockEntity.loaded(level)) {
            long since = valve.since(now);
            if (since < 0 || !valve.rigged()) {
                CASTS.remove(valve);
                continue;
            }
            Vec3 at = SableClient.toWorld(level, Vec3.atCenterOf(valve.getBlockPos()));
            if (at.distanceTo(camera) <= SEEN + SHIP_REACH) {
                draw(event.getPoseStack(), event.getFrustum(), camera, level, valve, cast(level, valve, now - since, now), since + partialTick);
            }
        }
    }

    /**
     * Who stands round a take-off, found once as it begins, while the zeppelin is still held where she stood; found
     * again once a tick while nobody has been and she is still held, as her place may not have reached this client yet.
     */
    private static Cast cast(ClientLevel level, BallastValveBlockEntity valve, long started, long now) {
        Cast cast = CASTS.get(valve);
        boolean retry = cast != null && cast.figures().isEmpty() && cast.foundAt() != now && now - started < BallastValveBlockEntity.RELEASE;
        if (cast == null || cast.started() != started || retry) {
            cast = new Cast(started, figures(level, valve), now);
            CASTS.put(valve, cast);
        }
        return cast;
    }

    /** Everyone who stands round a take-off where their stations are now, but where there is no ground for them. */
    private static List<Figure> figures(ClientLevel level, BallastValveBlockEntity valve) {
        List<Figure> figures = new ArrayList<>();
        for (BallastValveBlockEntity.Station station : valve.stations()) {
            Vec3 feet = ground(level, SableClient.toWorld(level, Vec3.atCenterOf(valve.getBlockPos().offset(station.stand()))));
            if (feet != null) {
                figures.add(new Figure(feet, station));
            }
        }
        return figures;
    }

    /**
     * Ground to stand on near a station's place in the world, the highest within reach up or down of it: a block with
     * a firm top and room over it, not water. Null for none, or land not loaded.
     */
    @Nullable
    private static Vec3 ground(ClientLevel level, Vec3 point) {
        BlockPos at = BlockPos.containing(point);
        if (!level.hasChunk(at.getX() >> 4, at.getZ() >> 4)) {
            return null;
        }
        for (int up = GROUND_REACH; up >= -GROUND_REACH; up--) {
            BlockPos feet = at.above(up);
            if (standable(level, feet)) {
                return Vec3.atBottomCenterOf(feet);
            }
        }
        return null;
    }

    /** Whether a handler can stand with their feet in a block: a firm top under it, dry, and room for them. */
    private static boolean standable(ClientLevel level, BlockPos feet) {
        BlockPos under = feet.below();
        return level.getBlockState(under).isFaceSturdy(level, under, Direction.UP) && level.getFluidState(under).isEmpty()
            && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty() && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
            && level.getFluidState(feet).isEmpty();
    }

    /**
     * Everyone round one take-off near enough to see, at so many ticks into it, each drawn only if in view, and their
     * lines while they hold them.
     */
    private static void draw(PoseStack pose, Frustum frustum, Vec3 camera, ClientLevel level, BallastValveBlockEntity valve, Cast cast, float at) {
        HumanoidModel<LivingEntity> model = body();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        float alpha = at < BallastValveBlockEntity.FADE_FROM ? 1
            : Mth.clamp(1 - (at - BallastValveBlockEntity.FADE_FROM) / (BallastValveBlockEntity.GONE - BallastValveBlockEntity.FADE_FROM), 0, 1);
        for (Figure figure : cast.figures()) {
            Vec3 faces = SableClient.toWorld(level, Vec3.atCenterOf(valve.getBlockPos().offset(figure.station().faces())));
            Vec3 feet = steppedBack(figure, faces, at);
            if (feet.distanceTo(camera) > SEEN) {
                continue;
            }
            int light = LevelRenderer.getLightColor(level, BlockPos.containing(feet.add(0, 1, 0)));
            if (frustum.isVisible(new AABB(feet.x - FIGURE_HALF, feet.y, feet.z - FIGURE_HALF, feet.x + FIGURE_HALF, feet.y + FIGURE_TALL, feet.z + FIGURE_HALF))) {
                stand(pose, camera, buffers, model, figure, feet, faces, at, alpha, light);
            }
            if (!figure.officer() && at < BallastValveBlockEntity.RELEASE) {
                Vec3 line = SableClient.toWorld(level, Vec3.atCenterOf(valve.getBlockPos().offset(figure.station().line())));
                line(pose, camera, buffers, hands(feet, faces), line, light);
            }
        }
        buffers.endBatch(HANDLER_TYPE);
        buffers.endBatch(OFFICER_TYPE);
        buffers.endBatch(LINE_TYPE);
    }

    /** Where a handler's feet are: at their station, then stepping back from the ship once they let go. */
    private static Vec3 steppedBack(Figure figure, Vec3 faces, float at) {
        if (figure.officer() || at <= BallastValveBlockEntity.RELEASE) {
            return figure.feet();
        }
        double share = Math.min(1, (at - BallastValveBlockEntity.RELEASE) / STEP_TICKS);
        Vec3 away = new Vec3(figure.feet().x - faces.x, 0, figure.feet().z - faces.z).normalize();
        return figure.feet().add(away.scale(STEP_BACK * share));
    }

    /** Where a handler's hands are, holding their line: in front of them, about shoulder high. */
    private static Vec3 hands(Vec3 feet, Vec3 faces) {
        Vec3 toward = new Vec3(faces.x - feet.x, 0, faces.z - feet.z).normalize();
        return feet.add(toward.scale(HANDS_AHEAD)).add(0, HANDS_HIGH, 0);
    }

    /** One figure, facing what they face, posed for the moment, as a player is drawn. */
    private static void stand(PoseStack pose, Vec3 camera, MultiBufferSource buffers, HumanoidModel<LivingEntity> model, Figure figure, Vec3 feet, Vec3 faces,
                              float at, float alpha, int light) {
        float yaw = (float) Math.toDegrees(Math.atan2(-(faces.x - feet.x), faces.z - feet.z));
        pose.pushPose();
        pose.translate(feet.x - camera.x, feet.y - camera.y, feet.z - camera.z);
        pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
        pose.scale(-1, -1, 1);
        pose.scale(FIGURE_SCALE, FIGURE_SCALE, FIGURE_SCALE);
        pose.translate(0, -MODEL_FEET, 0);
        posed(model, figure.officer(), at);
        VertexConsumer consumer = buffers.getBuffer(figure.officer() ? OFFICER_TYPE : HANDLER_TYPE);
        model.renderToBuffer(pose, consumer, light, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.colorFromFloat(alpha, 1, 1, 1));
        pose.popPose();
    }

    /**
     * The figure's arms, legs and head for the moment: a handler's arms up on the line until they let go, then at
     * their sides, stepping back and looking up at her; the officer's at his sides but for his arm up for the call.
     */
    private static void posed(HumanoidModel<LivingEntity> model, boolean officer, float at) {
        boolean holding = !officer && at < BallastValveBlockEntity.RELEASE;
        boolean calling = officer && at >= BallastValveBlockEntity.CALL - ARM_UP_BEFORE && at < BallastValveBlockEntity.RELEASE + ARM_UP_AFTER;
        boolean stepping = !officer && at > BallastValveBlockEntity.RELEASE && at < BallastValveBlockEntity.RELEASE + STEP_TICKS;
        float stride = stepping ? Mth.sin(at * STRIDE_SPEED) * STRIDE : 0;
        model.head.setRotation(holding || at >= BallastValveBlockEntity.RELEASE ? LOOK_UP : 0, 0, 0);
        model.hat.copyFrom(model.head);
        model.body.setRotation(0, 0, 0);
        model.rightArm.setRotation(holding ? ARMS_ON_LINE : calling ? ARM_UP : 0, holding ? -ARMS_IN : 0, calling ? -ARM_OUT : 0);
        model.leftArm.setRotation(holding ? ARMS_ON_LINE : 0, holding ? ARMS_IN : 0, 0);
        model.rightLeg.setRotation(stride, 0, 0);
        model.leftLeg.setRotation(-stride, 0, 0);
    }

    /** A line from a handler's hands up to the point on the hull it is made fast to: two thin crossed strips. */
    private static void line(PoseStack pose, Vec3 camera, MultiBufferSource buffers, Vec3 from, Vec3 to, int light) {
        VertexConsumer consumer = buffers.getBuffer(LINE_TYPE);
        Vector3f start = from.subtract(camera).toVector3f();
        Vector3f end = to.subtract(camera).toVector3f();
        Vector3f along = new Vector3f(end).sub(start);
        Vector3f side = new Vector3f(along).cross(0, 1, 0).normalize().mul(LINE_THICK);
        Vector3f up = new Vector3f(along).cross(side).normalize().mul(LINE_THICK);
        for (Vector3f width : new Vector3f[]{side, up}) {
            FlatQuads.quad(consumer, pose.last(), light, LINE, 0, 1, 0, start.x - width.x, start.y - width.y, start.z - width.z,
                start.x + width.x, start.y + width.y, start.z + width.z, end.x + width.x, end.y + width.y, end.z + width.z, end.x - width.x, end.y - width.y,
                end.z - width.z);
        }
    }

    /** The figures' body, a player's, baked once. */
    private static HumanoidModel<LivingEntity> body() {
        if (body == null) {
            body = new HumanoidModel<>(Minecraft.getInstance().getEntityModels().bakeLayer(ModelLayers.PLAYER));
        }
        return body;
    }
}
