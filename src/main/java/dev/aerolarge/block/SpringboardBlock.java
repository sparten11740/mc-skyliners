package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.cabin.HighDive;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A springboard: the padded tip of a diving board, or a little trampoline on its own. Whatever lands or stands on it
 * is thrown straight back up with a "boing" and a puff of cloud, the same height every time, so a bounce never goes
 * high enough to hurt coming down, and landing on it never hurts at all; sneaking, you stand on it without bouncing.
 * A bounce that ends in the moon pool of an airliner in flight is a Cannonball (see {@link HighDive}).
 * <p>
 * A player's own client moves them, so it bounces them and plays their boing at once, the first time too; the server
 * only hears of each landing after it (as a fall), and lets everyone else hear and see it then.
 */
public class SpringboardBlock extends Block {
    private static final MapCodec<SpringboardBlock> CODEC = simpleCodec(SpringboardBlock::new);
    /**
     * How fast it throws you up, in blocks a tick: about two and a half blocks high, under the three a fall needs to
     * hurt, and no more however hard you land.
     */
    private static final double BOUNCE = 0.6;
    /** How loud the boing is: a little under a block's own sounds, as it is heard over and over. */
    private static final float BOING_VOLUME = 0.8F;
    /** The puff of cloud at the feet on each bounce: how many, how far they spread and how fast they fly. */
    private static final int PUFFS = 8;
    private static final double PUFF_SPREAD = 0.3;
    private static final double PUFF_SPEED = 0.02;

    public SpringboardBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void fallOn(Level level, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        entity.causeFallDamage(fallDistance, 0.0F, level.damageSources().fall());
        if (entity instanceof ServerPlayer player && !player.isSuppressingBounce()) {
            boing(player.serverLevel(), player);
            HighDive.bounced(player);
        }
    }

    @Override
    public void updateEntityAfterFallOn(BlockGetter getter, Entity entity) {
        Vec3 motion = entity.getDeltaMovement();
        if (entity.isSuppressingBounce() || motion.y >= 0) {
            super.updateEntityAfterFallOn(getter, entity);
            return;
        }
        entity.setDeltaMovement(motion.x, BOUNCE, motion.z);
        Level level = entity.level();
        if (level.isClientSide && entity instanceof Player player) {
            level.playSound(player, entity.getX(), entity.getY(), entity.getZ(), AerolargeRegistry.SPRINGBOARD_BOING.get(), SoundSource.BLOCKS, BOING_VOLUME, 1.0F);
            for (int i = 0; i < PUFFS; i++) {
                level.addParticle(ParticleTypes.CLOUD, entity.getX() + spread(level), entity.getY(), entity.getZ() + spread(level),
                    spread(level) * PUFF_SPEED, PUFF_SPEED, spread(level) * PUFF_SPEED);
            }
        } else if (level instanceof ServerLevel server && !(entity instanceof Player)) {
            boing(server, entity);
        }
    }

    /**
     * The boing and the puff at the entity's feet, for everyone but the entity itself if it is a player, whose own
     * client has played them already. The entity is in the world, so its feet are where it is seen aboard an airliner too.
     */
    private static void boing(ServerLevel level, Entity entity) {
        Player except = entity instanceof Player player ? player : null;
        level.playSound(except, entity.getX(), entity.getY(), entity.getZ(), AerolargeRegistry.SPRINGBOARD_BOING.get(), SoundSource.BLOCKS,
            BOING_VOLUME, 1.0F);
        for (ServerPlayer watcher : level.players()) {
            if (watcher != except) {
                level.sendParticles(watcher, ParticleTypes.CLOUD, false, entity.getX(), entity.getY(), entity.getZ(),
                    PUFFS, PUFF_SPREAD, 0.05, PUFF_SPREAD, PUFF_SPEED);
            }
        }
    }

    private static double spread(Level level) {
        return (level.random.nextDouble() * 2 - 1) * PUFF_SPREAD;
    }
}
