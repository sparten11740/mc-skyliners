package dev.skyliners.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Buffers of their own for render types that many block entities' renderers add to through a frame, drawn once a frame,
 * all together, as soon as the level's block entities (a ship's among them) are drawn. Through vanilla's shared buffer,
 * each renderer's quads are drawn as soon as anything takes the buffer for another render type, sorting translucent
 * ones every time, so a dozen panels or console modules each switching between a few render types cost a draw and a
 * sort for every switch. Only for what looks the same drawn after every block entity: light added onto what lies
 * behind, opaque quads, or translucent ones sorted as vanilla's are.
 */
final class BatchedBuffers {
    private BatchedBuffers() {
    }

    /** Gives each render type a buffer of its own, which every renderer drawing in it adds to through a frame. */
    static void register(RegisterRenderBuffersEvent event, RenderType... types) {
        for (RenderType type : types) {
            event.registerRenderBuffer(type);
        }
    }

    /** Draws what every renderer added to each render type's buffer this frame, once the block entities are drawn. */
    static void draw(RenderLevelStageEvent event, RenderType... types) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        for (RenderType type : types) {
            buffers.endBatch(type);
        }
    }
}
