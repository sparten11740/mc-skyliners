package dev.skyliners.radar;

import io.netty.buffer.ByteBuf;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * One chunk of land as the maps know it, column by column, row by row: the map colour of its top block that has one
 * (as vanilla's maps colour it), how deep the water over it is, and its height. Taken in by a client from the chunks
 * round an airliner (see {@code TerrainMap}), and by the server for the towers' radar (see {@link RadarSurvey}), which
 * sends what it takes in to the clients. Colours are kept as vanilla's map colour ids, a byte each.
 */
public record LandTile(byte[] colours, byte[] depths, short[] heights) {
    /** The columns in a chunk. */
    public static final int COLUMNS = 256;
    /** Water deeper than this, in blocks, is taken as this deep. */
    public static final int DEEPEST = 10;
    public static final StreamCodec<ByteBuf, LandTile> STREAM_CODEC = StreamCodec.of(LandTile::write, LandTile::read);

    /** Takes in a chunk, from the top block of each column down. */
    public static LandTile sample(Level level, LevelChunk chunk) {
        LandTile tile = new LandTile(new byte[COLUMNS], new byte[COLUMNS], new short[COLUMNS]);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < COLUMNS; i++) {
            int x = i & 15;
            int z = i >> 4;
            pos.set(baseX + x, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), baseZ + z);
            tile.column(level, chunk, pos, i);
        }
        return tile;
    }

    /** Takes in one column, from its top block down: its colour, the water's depth over it and its height. */
    private void column(Level level, LevelChunk chunk, BlockPos.MutableBlockPos pos, int index) {
        int bottom = level.getMinBuildHeight();
        BlockState state = chunk.getBlockState(pos);
        MapColor colour = state.getMapColor(level, pos);
        // Down through whatever has no colour on a map (glass, say) to what has.
        while (colour == MapColor.NONE && pos.getY() > bottom) {
            state = chunk.getBlockState(pos.move(Direction.DOWN));
            colour = state.getMapColor(level, pos);
        }
        colours[index] = (byte) colour.id;
        depths[index] = (byte) (state.getFluidState().isEmpty() ? 0 : depth(chunk, pos.mutable(), bottom));
        heights[index] = (short) pos.getY();
    }

    /** How deep the water is under its surface here, in blocks, up to {@link #DEEPEST}. */
    private static int depth(LevelChunk chunk, BlockPos.MutableBlockPos under, int bottom) {
        int depth = 0;
        while (depth < DEEPEST && under.getY() > bottom && !chunk.getBlockState(under.move(Direction.DOWN)).getFluidState().isEmpty()) {
            depth++;
        }
        return depth;
    }

    /** A column's colour as RGB, the water's depth over it in the top byte. */
    public int rgb(int index) {
        return MapColor.byId(colours[index] & 0xFF).col | (depths[index] & 0xFF) << 24;
    }

    private static void write(ByteBuf buffer, LandTile tile) {
        buffer.writeBytes(tile.colours);
        buffer.writeBytes(tile.depths);
        for (short height : tile.heights) {
            buffer.writeShort(height);
        }
    }

    private static LandTile read(ByteBuf buffer) {
        LandTile tile = new LandTile(new byte[COLUMNS], new byte[COLUMNS], new short[COLUMNS]);
        buffer.readBytes(tile.colours);
        buffer.readBytes(tile.depths);
        for (int i = 0; i < COLUMNS; i++) {
            tile.heights[i] = buffer.readShort();
        }
        return tile;
    }

    public void write(DataOutputStream out) throws IOException {
        out.write(colours);
        out.write(depths);
        for (short height : heights) {
            out.writeShort(height);
        }
    }

    public static LandTile read(DataInputStream in) throws IOException {
        LandTile tile = new LandTile(new byte[COLUMNS], new byte[COLUMNS], new short[COLUMNS]);
        in.readFully(tile.colours);
        in.readFully(tile.depths);
        for (int i = 0; i < COLUMNS; i++) {
            tile.heights[i] = in.readShort();
        }
        return tile;
    }
}
