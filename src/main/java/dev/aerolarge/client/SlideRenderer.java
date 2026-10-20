package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.aerolarge.Aerolarge;
import dev.aerolarge.block.EvacuationSlide;
import dev.aerolarge.block.PlugDoorBlock;
import dev.aerolarge.block.SlideControlBlockEntity;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Draws an evacuation slide from its control, wherever it is between packed and out (see {@link EvacuationSlide}): the
 * pack dropping out of the doorway, the slide unrolling and filling out down to the ground, and the slide out, as the
 * control's clock says between the last tick and this one. Drawn in the served door's own frame, so it comes out of the
 * right door whichever way the airliner was built, and aboard an assembled airliner wherever it stands. Each quad is lit
 * by the light where it is. Out and firm, the slide does not change: its quads are worked out once and kept.
 */
public class SlideRenderer implements BlockEntityRenderer<SlideControlBlockEntity> {
    /** How far off the slide is still drawn, in blocks: it is big enough to see from across the apron. */
    private static final int VIEW_DISTANCE = 256;
    /** How far round its reach the slide is looked for to draw, in blocks. */
    private static final int REACH_MARGIN = 4;
    private static final Map<EvacuationSlide.Part, ResourceLocation> TEXTURES = new EnumMap<>(Map.of(
        EvacuationSlide.Part.LANE, texture("slide_lane"),
        EvacuationSlide.Part.UNDERSIDE, texture("slide_underside"),
        EvacuationSlide.Part.RAIL, texture("slide_rail"),
        EvacuationSlide.Part.BEAM, texture("slide_beam"),
        EvacuationSlide.Part.GIRT, texture("slide_girt"),
        EvacuationSlide.Part.PACK, texture("slide_pack")
    ));
    /** Each slide's quads out and firm, for the plan they were worked out for. */
    private final Map<SlideControlBlockEntity, Firm> firm = new WeakHashMap<>();

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "block/" + name);
    }

    /** A drawn quad: its part and its corners (see {@link EvacuationSlide.Sink}). */
    private record Quad(EvacuationSlide.Part part, float[] corners) {
    }

    private record Firm(EvacuationSlide.Plan plan, List<Quad> quads) {
    }

    @Override
    public void render(SlideControlBlockEntity slide, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int overlay) {
        Level level = slide.getLevel();
        EvacuationSlide.Plan plan = slide.plan();
        BlockPos door = slide.served();
        float progress = slide.progress(partialTick);
        if (level == null || plan == null || door == null || progress <= 0) {
            return;
        }
        List<Quad> quads;
        if (progress >= 1) {
            Firm kept = firm.get(slide);
            if (kept == null || !kept.plan().equals(plan)) {
                kept = new Firm(plan, quads(plan, 1));
                firm.put(slide, kept);
            }
            quads = kept.quads();
        } else {
            quads = quads(plan, progress);
        }
        BlockState part = level.getBlockState(door);
        Direction out = part.getValue(PlugDoorBlock.FACING);
        Direction right = PlugDoorBlock.right(part);
        BlockPos from = door.subtract(slide.getBlockPos());
        pose.pushPose();
        pose.translate(from.getX(), from.getY(), from.getZ());
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-out.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        PoseStack.Pose last = pose.last();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        Function<ResourceLocation, TextureAtlasSprite> atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        Map<EvacuationSlide.Part, TextureAtlasSprite> sprites = new EnumMap<>(EvacuationSlide.Part.class);
        Map<Long, Integer> lights = new HashMap<>();
        for (Quad quad : quads) {
            TextureAtlasSprite sprite = sprites.computeIfAbsent(quad.part(), kind -> atlas.apply(TEXTURES.get(kind)));
            float[] c = quad.corners();
            BlockPos at = door.relative(right, Mth.floor((c[0] + c[8] + c[16] + c[24]) / 4))
                .above(Mth.floor((c[1] + c[9] + c[17] + c[25]) / 4))
                .relative(out, Mth.floor((c[2] + c[10] + c[18] + c[26]) / 4));
            int light = lights.computeIfAbsent(at.asLong(), key -> SableClient.light(level, at));
            for (int k = 0; k < 4; k++) {
                int i = k * 8;
                consumer.addVertex(last, c[i], c[i + 1], c[i + 2])
                    .setColor(255, 255, 255, 255)
                    .setUv(sprite.getU(c[i + 6]), sprite.getV(c[i + 7]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(last, c[i + 3], c[i + 4], c[i + 5]);
            }
        }
        pose.popPose();
    }

    private static List<Quad> quads(EvacuationSlide.Plan plan, float progress) {
        List<Quad> quads = new ArrayList<>();
        EvacuationSlide.draw(plan, progress, (part, corners) -> quads.add(new Quad(part, corners)));
        return quads;
    }

    /**
     * Drawn whether or not the section its control stands in is: the control is inside the hull, and seen from close by
     * outside the hull hides that section, while the slide out on the apron is in plain view.
     */
    @Override
    public boolean shouldRenderOffScreen(SlideControlBlockEntity slide) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    /** Everything round the door, as far as the slide reaches out and down. */
    @Override
    public AABB getRenderBoundingBox(SlideControlBlockEntity slide) {
        EvacuationSlide.Plan plan = slide.plan();
        float reach = plan == null ? 0 : Math.max(plan.path().reach(), plan.path().depth());
        return new AABB(slide.getBlockPos()).inflate(reach + REACH_MARGIN);
    }
}
