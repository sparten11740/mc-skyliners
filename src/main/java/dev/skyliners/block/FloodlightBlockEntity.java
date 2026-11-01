package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A floodlight's block entity (see {@link FloodlightBlock}): on the server it switches the light on, and keeps it on by
 * day as by night, looking now and then; on the clients it is kept track of for the light each throws (see the
 * client's {@code Floodlights}). Every light of a zeppelin looks at the same ticks, so they come on together.
 */
public class FloodlightBlockEntity extends BlockEntity {
    private static final Loaded<FloodlightBlockEntity> LOADED = new Loaded<>(FloodlightBlockEntity.class, true);
    /** How often the light looks whether it is on, in ticks. */
    private static final int LOOK_TICKS = 40;
    /** How loud it comes on, heard aboard over the hull's boom. */
    private static final float COMING_ON = 0.8F;
    /** How far ahead of straight down it shines, toward FACING, in radians. */
    private static final float TILT = (float) Math.toRadians(25);

    public FloodlightBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.FLOODLIGHT_ENTITY.get(), pos, state);
    }

    /** The floodlights loaded in a level, on the server or on this client. */
    public static List<FloodlightBlockEntity> loaded(Level level) {
        return LOADED.in(level);
    }

    public boolean lit() {
        return getBlockState().getValue(FloodlightBlock.LIT);
    }

    /** The way it shines, among the ship's blocks: down, and tilted a little toward FACING. */
    public Vec3 aim() {
        Vec3 ahead = Vec3.atLowerCornerOf(getBlockState().getValue(FloodlightBlock.FACING).getNormal());
        return ahead.scale(Mth.sin(TILT)).add(0, -Mth.cos(TILT), 0);
    }

    /**
     * Once a tick, on the server: now and then, lit if it is not yet, coming on with the clunk of its contactor and the
     * crackle of its lamp striking up. It stays on, day and night: by night it lights the land under her, and by day
     * the shade her hull casts on it (see the client's {@code GreatShadow}).
     */
    void serverTick(ServerLevel level) {
        if (level.getGameTime() % LOOK_TICKS != 0 || lit()) {
            return;
        }
        level.setBlock(worldPosition, getBlockState().setValue(FloodlightBlock.LIT, true), Block.UPDATE_ALL);
        level.playSound(null, worldPosition, SkylinersRegistry.SEARCHLIGHT_ON.get(), SoundSource.BLOCKS, COMING_ON, 1.0F);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
    }
}
