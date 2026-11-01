package dev.skyliners.entity;

import dev.skyliners.SkylinersRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A chunk of blue ice on its way down from an airliner's belly: what a vacuum toilet flushed lets go of (see
 * {@link dev.skyliners.block.VacuumToiletBlock}). It leaves at the airliner's own speed and falls like a stone, the
 * air slowing it, so from an airliner in flight it falls away behind and far below, glinting. It harms nothing:
 * whoever it comes down on only gets a bonk, and it never breaks or replaces a block. Landing on top of solid ground
 * with nothing there, it lies as a lump of blue lav ice that melts away in a few minutes (see
 * {@link dev.skyliners.block.LavIceBlock}); anything else it hits (a wall, a fence, water, the airliner itself) it
 * shatters on. It moves the same on both sides, so it falls smoothly between updates.
 */
public class BlueIceChunk extends Entity {
    /** Gravity, and what the air leaves of its speed each tick, in blocks a tick: a falling block's. */
    private static final double GRAVITY = 0.04;
    private static final double AIR_DRAG = 0.98;
    /** The longest it falls, in ticks (a minute), before it shatters wherever it is. */
    private static final int LONGEST_FALL = 60 * 20;
    /** How far off it is still drawn, in blocks: falling out of the sky, it is worth seeing from a long way down. */
    private static final double SEEN_FROM = 128;
    /** One in so many ticks of its fall it sheds a glint of ice. */
    private static final int GLINT = 2;
    /** Shattering: how many shards and flakes fly, how far they spread and how fast; and how loud it cracks. */
    private static final int SHARDS = 24;
    private static final int FLAKES = 12;
    private static final double SHARD_SPREAD = 0.25;
    private static final double SHARD_SPEED = 0.15;
    private static final float CRACK_VOLUME = 1.5F;

    public BlueIceChunk(EntityType<? extends BlueIceChunk> type, Level level) {
        super(type, level);
    }

    /** A chunk falling from the point given in the world, moving as given (blocks a tick). */
    public static BlueIceChunk of(Level level, Vec3 at, Vec3 velocity) {
        BlueIceChunk chunk = new BlueIceChunk(SkylinersRegistry.BLUE_ICE_CHUNK.get(), level);
        chunk.setPos(at.subtract(0, chunk.getBbHeight() / 2, 0));
        chunk.setDeltaMovement(velocity);
        return chunk;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        super.tick();
        Vec3 velocity = getDeltaMovement().add(0, -GRAVITY, 0).scale(AIR_DRAG);
        if (level() instanceof ServerLevel level && bonk(level, velocity)) {
            return;
        }
        move(MoverType.SELF, velocity);
        setDeltaMovement(velocity);
        if (level().isClientSide) {
            if (random.nextInt(GLINT) == 0) {
                level().addParticle(ParticleTypes.SNOWFLAKE, getX(), getY() + getBbHeight() / 2, getZ(), 0, 0, 0);
            }
        } else if (onGround() || horizontalCollision || isInWater() || isInLava() || tickCount > LONGEST_FALL) {
            land((ServerLevel) level());
        }
    }

    /**
     * Whether it comes down on anyone alive this tick, on its way down by the velocity given: if so it bonks them, the
     * only harm it does, and shatters.
     */
    private boolean bonk(ServerLevel level, Vec3 velocity) {
        for (Entity hit : level.getEntities(this, getBoundingBox().expandTowards(velocity), entity -> entity instanceof LivingEntity && entity.isAlive() && !entity.isSpectator())) {
            level.playSound(null, hit.getX(), hit.getEyeY(), hit.getZ(), SkylinersRegistry.BLUE_ICE_BONK.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
            if (hit instanceof ServerPlayer player) {
                player.displayClientMessage(Component.translatable("message.skyliners.blue_ice.bonk"), true);
            }
            shatter(level);
            return true;
        }
        return false;
    }

    /** Down: on top of solid ground with nothing there it lies as a lump of lav ice; it cracks either way. */
    private void land(ServerLevel level) {
        BlockPos at = blockPosition();
        BlockState lump = SkylinersRegistry.LAV_ICE.get().defaultBlockState();
        if (onGround() && !isInWater() && level.getBlockState(at).isAir() && lump.canSurvive(level, at)) {
            level.setBlock(at, lump, Block.UPDATE_ALL);
        }
        shatter(level);
    }

    /** Bursts into shards of ice with a crack, and is gone. */
    private void shatter(ServerLevel level) {
        Vec3 middle = getBoundingBox().getCenter();
        level.playSound(null, middle.x, middle.y, middle.z, SkylinersRegistry.BLUE_ICE_CRACK.get(), SoundSource.NEUTRAL, CRACK_VOLUME, 0.9F + random.nextFloat() * 0.2F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, SkylinersRegistry.LAV_ICE.get().defaultBlockState()),
            middle.x, middle.y, middle.z, SHARDS, SHARD_SPREAD, SHARD_SPREAD, SHARD_SPREAD, SHARD_SPEED);
        level.sendParticles(ParticleTypes.SNOWFLAKE, middle.x, middle.y, middle.z, FLAKES, SHARD_SPREAD, SHARD_SPREAD, SHARD_SPREAD, SHARD_SPEED / 2);
        discard();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double range = SEEN_FROM * getViewScale();
        return distance < range * range;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }
}
