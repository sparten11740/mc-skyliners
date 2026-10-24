package dev.aerolarge.cabin;

import dev.aerolarge.Aboard;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSoundEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Lightning striking the airliner: flying through a thunderstorm, now and then a bolt cracks into it, mostly at its
 * extremities (a wingtip, the nose, the tail), as on a real airliner. It is a real bolt, with the sky's flash and the
 * crack of thunder right there, but only to look at: like a real airliner it comes through unharmed, nothing aboard
 * hurt and nothing set alight. Everyone aboard sees a blinding flash and the cabin flicker dark, the flight deck's
 * screens fill with static for a moment and the radio crackles (see the client's {@code LightningFlash}). One per
 * airliner, worked by its autopilot block beside the rough air (see {@link Turbulence}).
 */
public final class LightningStrikes {
    /** Only this high above the ground, in blocks: in the air, not standing on the runway in the rain. */
    private static final double MIN_HEIGHT = 15;
    /** The chance each tick of a strike in a thunderstorm: about once a minute. */
    private static final double CHANCE = 1.0 / (60 * 20);
    /** From this high above the ground up, in blocks, strikes come twice as often, up among the storm clouds. */
    private static final double HIGH = 150;
    private static final double HIGH_CHANCE = 2;
    /**
     * How many columns of the airliner's blocks are looked at for where a bolt strikes, the one furthest out taken: so it
     * mostly finds a wingtip, the nose or the tail, without searching the whole airliner.
     */
    private static final int TRIES = 6;

    /** One tick, with how high the airliner flies above the ground. */
    public void tick(ServerLevel level, BlockEntity anchor, Vec3 middle, double height) {
        if (!AerolargeConfig.LIGHTNING_STRIKES.get() || height < MIN_HEIGHT || Weather.at(level, middle) != Weather.STORM) {
            return;
        }
        double chance = CHANCE * (height >= HIGH ? HIGH_CHANCE : 1);
        if (level.getRandom().nextDouble() < chance) {
            strike(level, anchor);
        }
    }

    /** A bolt into the airliner: drawn and heard by all, and everyone aboard told, for the flash and the static. */
    private static void strike(ServerLevel level, BlockEntity anchor) {
        BoundingBox blocks = SableFlight.blocks(level, Vec3.atCenterOf(anchor.getBlockPos()));
        BlockPos struck = blocks == null ? null : extremity(level, blocks, level.getRandom());
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (struck == null || bolt == null) {
            return;
        }
        bolt.setVisualOnly(true);
        bolt.moveTo(SableFlight.toWorld(level, Vec3.atCenterOf(struck)));
        level.addFreshEntity(bolt);
        long seed = level.getRandom().nextLong();
        for (ServerPlayer player : Aboard.players(anchor)) {
            PacketDistributor.sendToPlayer(player, LightningStrikePayload.INSTANCE);
            player.connection.send(new ClientboundSoundEntityPacket(AerolargeRegistry.RADIO_CRACKLE, SoundSource.AMBIENT, player, 1.0F, 0.8F, seed));
        }
    }

    /** The top of the airliner at the column furthest out from its middle of a few picked at random; null for none. */
    @Nullable
    private static BlockPos extremity(Level level, BoundingBox blocks, RandomSource random) {
        BlockPos furthest = null;
        double out = -1;
        for (int i = 0; i < TRIES; i++) {
            int x = blocks.minX() + random.nextInt(blocks.getXSpan());
            int z = blocks.minZ() + random.nextInt(blocks.getZSpan());
            BlockPos top = top(level, blocks, x, z);
            double away = Math.max(Math.abs(x - blocks.getCenter().getX()) / (double) blocks.getXSpan(),
                Math.abs(z - blocks.getCenter().getZ()) / (double) blocks.getZSpan());
            if (top != null && away > out) {
                furthest = top;
                out = away;
            }
        }
        return furthest;
    }

    /** The highest block of the airliner in a column of its box; null if the column is empty. */
    @Nullable
    private static BlockPos top(Level level, BoundingBox blocks, int x, int z) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos(x, blocks.maxY(), z);
        for (; at.getY() >= blocks.minY(); at.move(0, -1, 0)) {
            if (!level.getBlockState(at).isAir()) {
                return at.immutable();
            }
        }
        return null;
    }
}
