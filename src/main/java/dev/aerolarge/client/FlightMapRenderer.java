package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.aerolarge.block.FlightMapBlock;
import dev.aerolarge.block.FlightMapBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the flight map over the whole of its face, from the block that holds it: the passenger screens' route map page
 * made square and as big as the wall panel, with three of their figures under the map (see
 * {@link PassengerScreenRenderer#drawFlightMap}), inside a thin bezel of the casing. It follows the airliner's flight
 * with its passenger screens, so both show one route. It is only drawn with the camera in front of it.
 */
public class FlightMapRenderer implements BlockEntityRenderer<FlightMapBlockEntity> {
    /** Where the face is, from the middle of the block toward the room: the panel's front, the drawing just off it. */
    private static final float FACE = FlightMapBlock.DEPTH / 16F - 0.5F + 0.002F;
    /** The casing left showing round the page, in blocks. */
    private static final double BEZEL = 0.06;
    /** How much bigger the page is drawn than on a passenger screen, so it fills the face inside the bezel. */
    private static final float SCALE = (float) ((FlightMapBlock.COLUMNS - 2 * BEZEL) / PassengerScreenRenderer.FLIGHT_MAP_WIDTH);

    /**
     * Only with the camera in front of the map, as its face is drawn from the front only. Sable asks this of every
     * block entity of an assembled airliner every frame, so the flight is followed here, as the screens do.
     */
    @Override
    public boolean shouldRender(FlightMapBlockEntity map, Vec3 camera) {
        PassengerScreenRenderer.follow(map);
        return BlockEntityRenderer.super.shouldRender(map, camera)
            && Sight.facing(map.getBlockPos(), FlightMapBlock.face(map.getBlockState()), FACE, camera, getViewDistance());
    }

    @Override
    public void render(FlightMapBlockEntity map, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = map.getLevel();
        if (level == null) {
            return;
        }
        Direction facing = map.getBlockState().getValue(FlightMapBlock.FACING);
        pose.pushPose();
        // Onto the face, x to the viewer's right and y up, from the middle of the whole map.
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.getOpposite().toYRot()));
        pose.translate(-0.5 + FlightMapBlock.COLUMNS / 2.0, 0.5 - FlightMapBlock.ROWS / 2.0, FACE);
        pose.scale(SCALE, SCALE, SCALE);
        PassengerScreenRenderer.drawFlightMap(PassengerScreenRenderer.follow(map), level, pose, buffers);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(FlightMapBlockEntity map) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(FlightMapBlockEntity map) {
        return new AABB(map.getBlockPos()).inflate(Math.max(FlightMapBlock.COLUMNS, FlightMapBlock.ROWS));
    }
}
