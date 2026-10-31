package dev.aerolarge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.TechnoDeckBlockEntity;
import it.unimi.dsi.fastutil.HashCommon;
import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Draws the secret techno room's lights while this player is in it (see {@link TechnoRoom}): the dance floor's tiles
 * lit two by two in the beat, only those that are dance floor (the rocket's club has its tiles only in the middle,
 * round her shaft), a checkerboard of pink and blue changing every beat at first, quickening with the
 * build; then, from the drop, every tile its own colour each half beat, the beams of light from over the deck sweeping
 * the dancers, and a white flash on the ceiling on every beat of the first bars. All at full brightness, so the room
 * glows with them.
 */
public class TechnoRoomRenderer implements BlockEntityRenderer<TechnoDeckBlockEntity> {
    /** How big the dance floor's lit squares are, in blocks, and how far over the floor they are drawn. */
    private static final int SQUARE = 2;
    private static final float LIFT = 0.02F;
    /** How far in from a lit patch's edges its light stops, in blocks, so that the tiles' joints show. */
    private static final float INSET = 0.1F;
    /** A square whose every tile is dance floor, as a bit for each of its tiles. */
    private static final int FULL = (1 << SQUARE * SQUARE) - 1;
    /** How often which of a room's squares are dance floor is looked at again, in game ticks: tiles can be broken. */
    private static final int LOOK_EVERY = 40;
    /** Where in the track the build starts, in ticks: the tiles flash twice as fast from there. */
    private static final int BUILD_TICK = 300;
    /** How long the ceiling flashes on each beat after the drop, in ticks, and for how many beats. */
    private static final float FLASH_TICKS = 2;
    private static final int FLASH_BEATS = 16;
    /** How long the ceiling flashes after the record is scratched, in ticks, as long as the scratch lasts. */
    private static final float SCRATCH_FLASH_TICKS = 17;
    /** How often the ceiling flickers through the scratch, in ticks a flicker: with each stroke of the hand. */
    private static final float SCRATCH_FLICKER = 3;
    /** The beams: how many, how far up from the deck they start, how wide they are and how fast they sweep. */
    private static final int BEAMS = 3;
    private static final float BEAM_HEIGHT = 1.2F;
    private static final float BEAM_THICK = 0.05F;
    private static final float SWEEP = 0.07F;
    /** The colours the floor and the beams flash, the floor's dark between them, and the ceiling's flash. */
    private static final int[] PALETTE = {0xFFFF2BD6, 0xFF2BE6FF, 0xFFFFE82B, 0xFF5BFF2B, 0xFFFF6A2B, 0xFF8A2BFF};
    private static final int DARK = 0xFF100818;
    private static final int FLASH = 0xFFF8F8FF;

    /** Which tiles of each deck's room are dance floor, as last looked at. */
    private final Map<TechnoDeckBlockEntity, Floor> floors = new WeakHashMap<>();

    /**
     * Which tiles of a room are dance floor, a bit for each tile of a square, the squares by their place in its rows,
     * and when that was seen.
     */
    private record Floor(long seen, int[] tiles) {
    }

    @Override
    public void render(TechnoDeckBlockEntity deck, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        float at = TechnoRoom.playing(deck, partialTick);
        BlockPos from = deck.roomFrom();
        BlockPos to = deck.roomTo();
        if (at < 0 || from == null || to == null) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(FlatQuads.WHITE));
        PoseStack.Pose last = pose.last();
        int beat = (int) (at / TechnoRoom.BEAT_TICKS);
        boolean dropped = at >= TechnoRoom.DROP_TICK;
        floor(consumer, last, from, to, at, beat, dropped, danceFloor(deck, from, to));
        if (dropped) {
            beams(consumer, last, from, to, at);
            flash(consumer, last, from, to, at, beat);
        }
        scratchFlash(consumer, last, from, to, deck, partialTick);
    }

    /** The ceiling flickering in the floor's colours while the record is scratched, with each stroke of the hand. */
    private static void scratchFlash(VertexConsumer consumer, PoseStack.Pose pose, BlockPos from, BlockPos to, TechnoDeckBlockEntity deck,
                                     float partialTick) {
        float ago = deck.getLevel() == null ? Float.MAX_VALUE : deck.getLevel().getGameTime() - deck.scratchedAt() + partialTick;
        if (ago < 0 || ago > SCRATCH_FLASH_TICKS || (int) (ago / SCRATCH_FLICKER) % 2 == 1) {
            return;
        }
        int colour = PALETTE[(int) (ago / SCRATCH_FLICKER / 2) % PALETTE.length];
        ceiling(consumer, pose, from, to, colour);
    }

    /** Which of the room's tiles are dance floor: looked at again every so often, as tiles can be broken or laid. */
    private int[] danceFloor(TechnoDeckBlockEntity deck, BlockPos from, BlockPos to) {
        Level level = deck.getLevel();
        long now = level == null ? 0 : level.getGameTime();
        Floor floor = floors.get(deck);
        if (floor == null || now - floor.seen() >= LOOK_EVERY || now < floor.seen()) {
            floor = new Floor(now, tilesOn(level, deck.getBlockPos(), from, to));
            floors.put(deck, floor);
        }
        return floor.tiles();
    }

    /** Which of the room's tiles are dance floor, a bit for each tile of a square, by the square's place in its rows. */
    private static int[] tilesOn(@Nullable Level level, BlockPos deck, BlockPos from, BlockPos to) {
        int along = (to.getZ() + 1 - from.getZ()) / SQUARE;
        int[] tiles = new int[(to.getX() + 1 - from.getX()) / SQUARE * along];
        for (int square = 0; square < tiles.length && level != null; square++) {
            BlockPos corner = deck.offset(from.getX() + square / along * SQUARE, from.getY() - 1, from.getZ() + square % along * SQUARE);
            tiles[square] = tiled(level, corner);
        }
        return tiles;
    }

    /** Which tiles of the square of floor from a corner are dance floor's: a bit for each, across it then along. */
    private static int tiled(Level level, BlockPos corner) {
        int tiles = 0;
        for (int tile = 0; tile < SQUARE * SQUARE; tile++) {
            if (level.getBlockState(corner.offset(tile % SQUARE, 0, tile / SQUARE)).is(AerolargeRegistry.DANCE_FLOOR.get())) {
                tiles |= 1 << tile;
            }
        }
        return tiles;
    }

    /** The dance floor's squares, lit as the track has got to, each over those of its tiles that are dance floor. */
    private static void floor(VertexConsumer consumer, PoseStack.Pose pose, BlockPos from, BlockPos to, float at, int beat, boolean dropped, int[] tiled) {
        int step = dropped ? (int) (at / (TechnoRoom.BEAT_TICKS / 2)) : at >= BUILD_TICK ? (int) (at / (TechnoRoom.BEAT_TICKS / 2)) : beat;
        float y = from.getY() + LIFT;
        int square = 0;
        for (int x = from.getX(); x + SQUARE <= to.getX() + 1; x += SQUARE) {
            for (int z = from.getZ(); z + SQUARE <= to.getZ() + 1; z += SQUARE) {
                square(consumer, pose, x, y, z, tiled[square++], colour(x, z, step, dropped));
            }
        }
    }

    /** A square's colour as the track has got to: a checkerboard, a dark square now and then, or each its own. */
    private static int colour(int x, int z, int step, boolean dropped) {
        if (dropped) {
            return PALETTE[Math.floorMod(HashCommon.mix(x * 31 + z * 7 + step * 131), PALETTE.length)];
        }
        if ((x / SQUARE + z / SQUARE) % 3 == 0) {
            return DARK;
        }
        return (x / SQUARE + z / SQUARE + step) % 2 == 0 ? PALETTE[0] : PALETTE[1];
    }

    /** A square lit in a colour: whole where all its tiles are dance floor, or each of those of them that are. */
    private static void square(VertexConsumer consumer, PoseStack.Pose pose, int x, float y, int z, int tiles, int colour) {
        if (tiles == FULL) {
            patch(consumer, pose, colour, x, y, z, SQUARE);
            return;
        }
        for (int tile = 0; tile < SQUARE * SQUARE; tile++) {
            if ((tiles & 1 << tile) != 0) {
                patch(consumer, pose, colour, x + tile % SQUARE, y, z + tile / SQUARE, 1);
            }
        }
    }

    /** A lit patch of floor from a corner, so many blocks across, its light stopping a little short of its edges. */
    private static void patch(VertexConsumer consumer, PoseStack.Pose pose, int colour, int x, float y, int z, int size) {
        FlatQuads.quad(consumer, pose, LightTexture.FULL_BRIGHT, colour, 0, 1, 0, x + INSET, y, z + INSET, x + INSET, y, z + size - INSET,
            x + size - INSET, y, z + size - INSET, x + size - INSET, y, z + INSET);
    }

    /**
     * Beams of light from over the deck out across the room to its far wall, through its middle, sweeping from side
     * to side and up and down, each in its own colour.
     */
    private static void beams(VertexConsumer consumer, PoseStack.Pose pose, BlockPos from, BlockPos to, float at) {
        Vector3f start = new Vector3f(0.5F, BEAM_HEIGHT, 0.5F);
        Vector3f middle = new Vector3f((from.getX() + to.getX() + 1) / 2F, (from.getY() + to.getY() + 1) / 2F, (from.getZ() + to.getZ() + 1) / 2F);
        Vector3f toward = new Vector3f(middle.x - start.x, 0, middle.z - start.z);
        Vector3f across = new Vector3f(-toward.z, 0, toward.x).normalize().mul(Math.min(to.getX() - from.getX(), to.getZ() - from.getZ()) / 2F);
        float rise = (to.getY() - from.getY()) / 2F;
        for (int k = 0; k < BEAMS; k++) {
            float sweep = Mth.sin(at * SWEEP + k * 2.1F);
            Vector3f end = new Vector3f(middle).add(toward).add(new Vector3f(across).mul(sweep)).add(0, rise * Mth.cos(at * SWEEP * 0.7F + k), 0);
            beam(consumer, pose, PALETTE[(k * 2 + 1) % PALETTE.length], start, end);
        }
    }

    /** A thin beam of light from one point to another: two crossed strips along it. */
    private static void beam(VertexConsumer consumer, PoseStack.Pose pose, int colour, Vector3f from, Vector3f to) {
        Vector3f along = new Vector3f(to).sub(from);
        Vector3f side = new Vector3f(along).cross(0, 1, 0).normalize().mul(BEAM_THICK);
        Vector3f up = new Vector3f(along).cross(side).normalize().mul(BEAM_THICK);
        for (Vector3f width : new Vector3f[]{side, up}) {
            FlatQuads.quad(consumer, pose, LightTexture.FULL_BRIGHT, colour, 0, 1, 0, from.x - width.x, from.y - width.y, from.z - width.z,
                from.x + width.x, from.y + width.y, from.z + width.z, to.x + width.x, to.y + width.y, to.z + width.z, to.x - width.x, to.y - width.y, to.z - width.z);
        }
    }

    /** A white flash over the whole ceiling on each beat of the first bars after the drop. */
    private static void flash(VertexConsumer consumer, PoseStack.Pose pose, BlockPos from, BlockPos to, float at, int beat) {
        float into = at - beat * TechnoRoom.BEAT_TICKS;
        if (beat >= TechnoRoom.DROP_TICK / TechnoRoom.BEAT_TICKS + FLASH_BEATS || into > FLASH_TICKS) {
            return;
        }
        ceiling(consumer, pose, from, to, FLASH);
    }

    /** The whole ceiling lit in one colour, just under it. */
    private static void ceiling(VertexConsumer consumer, PoseStack.Pose pose, BlockPos from, BlockPos to, int colour) {
        float y = to.getY() + 1 - LIFT;
        FlatQuads.quad(consumer, pose, LightTexture.FULL_BRIGHT, colour, 0, 1, 0, from.getX(), y, from.getZ(), to.getX() + 1, y, from.getZ(),
            to.getX() + 1, y, to.getZ() + 1, from.getX(), y, to.getZ() + 1);
    }

    @Override
    public boolean shouldRenderOffScreen(TechnoDeckBlockEntity deck) {
        return true;
    }

    /** The whole room round the deck. */
    @Override
    public AABB getRenderBoundingBox(TechnoDeckBlockEntity deck) {
        BlockPos from = deck.roomFrom();
        BlockPos to = deck.roomTo();
        return from == null || to == null ? new AABB(deck.getBlockPos())
            : new AABB(Vec3.atLowerCornerOf(deck.getBlockPos().offset(from)), Vec3.atLowerCornerOf(deck.getBlockPos().offset(to).offset(1, 1, 1)));
    }
}
