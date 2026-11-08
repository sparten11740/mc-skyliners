package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import dev.skyliners.autopilot.FreighterClimbBoost;
import dev.skyliners.autopilot.FreighterFlight;
import dev.skyliners.autopilot.FreighterSlam;
import dev.skyliners.block.AutopilotBlock;
import dev.skyliners.console.ConsoleBlock;
import dev.skyliners.console.ConsoleBlockEntity;
import dev.skyliners.console.ConsoleKind;
import dev.skyliners.console.ConsoleShape;
import dev.skyliners.repairs.FaultPanelBlockEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws a freighter console module (see {@link ConsoleBlock}), its block having no model of its own: its casing as it
 * stands, a slice of her half-round console or a module on its own (see {@link ConsoleCasing}), and on it its controls
 * and its screen (see {@link ConsoleArt}), live from the autopilot it reads, as her instruments read her from the pose
 * the client draws her in, so it moves as smoothly as she does; a standby picture with no ship to read. Its scope's
 * blips, whoever is near and her pin, are found once a tick, not every frame. Left out while out of sight.
 */
public class ConsoleRenderer implements BlockEntityRenderer<ConsoleBlockEntity> {
    /** How far from the middle of its block a module draws at most, in blocks: a slice of a ring reaches past it. */
    private static final float REACH = 2;
    /**
     * How many ticks the drawing clock is counted over for the console's blinking and sweeping, an hour's: reduced to it
     * before it is a float, which would blur a long-running count, its lamps skipping a beat once an hour.
     */
    private static final double TICKS_ROUND = 72000;
    /** How many of those near a scope shows at most. */
    private static final int MOST_BLIPS = 16;
    /** How many scopes' blips are kept from one tick to the next at most, before they are all found afresh. */
    private static final int KEPT_SCOPES = 32;
    /**
     * How far from a scope her fault spots at fault are shown on it, in blocks: past the scope's own reach, so one in
     * engineering at her stern still shows from her cockpit out at her front, at the scope's rim at its bearing.
     */
    private static final double FAULT_REACH = 100;
    /** Each scope's blips as last found, and the tick they were found in. */
    private final Map<BlockPos, Found> found = new HashMap<>();

    private record Found(long tick, List<ConsoleArt.Blip> blips) {
    }

    @Override
    public void render(ConsoleBlockEntity console, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = console.getLevel();
        if (level == null || !(console.getBlockState().getBlock() instanceof ConsoleBlock block) || !Sight.ahead(pose.last(), REACH)) {
            return;
        }
        ConsoleKind kind = block.kind();
        ConsoleShape shape = console.shape();
        ConsoleCasing.Faces faces = ConsoleCasing.draw(buffers, pose.last(), shape, light);
        ConsoleArt.Readings readings = readings(console, level, kind, shape, partialTick);
        ConsoleCasing.Surface controls = faces.controls();
        controls.push(pose);
        ConsoleArt.controls(kind, pose, buffers, controls.width(), controls.height(), light, readings, console);
        pose.popPose();
        ConsoleCasing.Surface screen = faces.screen();
        screen.push(pose);
        ConsoleArt.screen(kind, pose, buffers, screen.width(), screen.height(), readings);
        pose.popPose();
    }

    /** What a module draws from this frame, from the autopilot it reads, if any (see {@link ConsoleArt.Readings}). */
    private ConsoleArt.Readings readings(ConsoleBlockEntity console, Level level, ConsoleKind kind, ConsoleShape shape, float partialTick) {
        AutopilotBlockEntity autopilot = console.autopilot();
        SableClient.Attitude attitude = autopilot == null ? null
            : SableClient.attitude(console, autopilot.getBlockState().getValue(AutopilotBlock.FACING), partialTick);
        double heading = attitude != null ? attitude.heading() : (shape.facing().getOpposite().toYRot() + 180) % 360;
        boolean freighter = autopilot != null && autopilot.craft() == Craft.FREIGHTER;
        double now = DrawingClock.gameTime(partialTick);
        FreighterFlight.BoostShown boost = autopilot == null ? FreighterFlight.BoostShown.READY : FreighterFlight.boostOf(autopilot, now);
        FreighterFlight.JumpShown jump = autopilot == null ? FreighterFlight.JumpShown.UNAVAILABLE : FreighterFlight.jumpOf(autopilot, now);
        FreighterClimbBoost.Shown climb = autopilot == null ? FreighterClimbBoost.Shown.UNAVAILABLE : FreighterFlight.climbOf(autopilot, now);
        FreighterSlam.Stage slam = autopilot == null ? FreighterSlam.Stage.UNAVAILABLE : FreighterFlight.slamOf(autopilot);
        float since = console.touched() == Long.MIN_VALUE ? -1 : (float) (now - console.touched());
        List<ConsoleArt.Blip> blips = kind == ConsoleKind.SCANNER ? blips(console, level, heading, autopilot) : List.of();
        return new ConsoleArt.Readings(attitude != null, attitude == null ? 0 : attitude.airspeed(), attitude == null ? 0 : attitude.altitude(),
            attitude == null ? 0 : attitude.verticalSpeed(), heading, autopilot == null ? 0 : FreighterFlight.liftOf(autopilot),
            autopilot == null ? 0 : FreighterFlight.mainsOf(autopilot), boost, jump, climb, slam, freighter, (float) (DrawingClock.ticks(partialTick) % TICKS_ROUND), since,
            console.toggles(), blips, autopilot == null ? null : FreighterFlight.heldBy(autopilot));
    }

    /**
     * A scope's blips, found afresh once a tick: whoever is near but the player looking, her pin, if she has one, and
     * each of her fault spots near it at fault (see {@link FaultPanelBlockEntity}).
     */
    private List<ConsoleArt.Blip> blips(ConsoleBlockEntity console, Level level, double heading, @Nullable AutopilotBlockEntity autopilot) {
        long tick = DrawingClock.wholeTicks();
        Found last = found.get(console.getBlockPos());
        if (last != null && last.tick() == tick) {
            return last.blips();
        }
        if (found.size() > KEPT_SCOPES) {
            found.clear();
        }
        Vec3 at = SableClient.toWorld(level, Vec3.atCenterOf(console.getBlockPos()));
        List<ConsoleArt.Blip> blips = new ArrayList<>();
        AABB near = new AABB(at, at).inflate(ConsoleArt.SCOPE_NEAR);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, near, entity -> entity != Minecraft.getInstance().player)) {
            if (blips.size() < MOST_BLIPS) {
                blips.add(blip(at, entity.getX(), entity.getZ(), heading, ConsoleArt.Mark.SOMEONE));
            }
        }
        if (autopilot != null && autopilot.lateral() == AutopilotBlockEntity.Lateral.DESTINATION) {
            blips.add(blip(at, autopilot.orbitX(), autopilot.orbitZ(), heading, ConsoleArt.Mark.PIN));
        }
        for (FaultPanelBlockEntity panel : FaultPanelBlockEntity.near(level, console.getBlockPos(), FAULT_REACH)) {
            if (panel.faulty()) {
                Vec3 spot = SableClient.toWorld(level, Vec3.atCenterOf(panel.getBlockPos()));
                blips.add(blip(at, spot.x, spot.z, heading, ConsoleArt.Mark.FAULT));
            }
        }
        found.put(console.getBlockPos(), new Found(tick, List.copyOf(blips)));
        return blips;
    }

    /** A blip for a spot in the world, from where the scope is and the way she heads. */
    private static ConsoleArt.Blip blip(Vec3 from, double x, double z, double heading, ConsoleArt.Mark mark) {
        double dx = x - from.x;
        double dz = z - from.z;
        double bearing = Math.toDegrees(Math.atan2(dx, -dz));
        return new ConsoleArt.Blip(bearing - heading, Math.hypot(dx, dz), mark);
    }
}
