package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A floodlight's block entity (see {@link FloodlightBlock}): on the server it switches the light on as the sky
 * darkens, at dusk or under a storm, and off again once it is light, looking now and then; on the clients it is kept
 * track of for the light each throws (see the client's {@code Floodlights}). Every light of a zeppelin looks at the same
 * ticks, so they come on and go off together.
 */
public class FloodlightBlockEntity extends BlockEntity {
    private static final Loaded<FloodlightBlockEntity> LOADED = new Loaded<>(FloodlightBlockEntity.class, true);
    /** How often the light looks at the sky, in ticks. */
    private static final int LOOK_TICKS = 40;
    /** How far ahead of straight down it shines, toward FACING, in radians. */
    private static final float TILT = (float) Math.toRadians(25);

    public FloodlightBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.FLOODLIGHT_ENTITY.get(), pos, state);
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

    /** Once a tick, on the server: now and then, lit while the sky is dark (at night, or under a storm by day). */
    void serverTick(ServerLevel level) {
        if (level.getGameTime() % LOOK_TICKS != 0) {
            return;
        }
        boolean dark = level.isNight();
        if (dark != lit()) {
            level.setBlock(worldPosition, getBlockState().setValue(FloodlightBlock.LIT, dark), Block.UPDATE_ALL);
        }
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
