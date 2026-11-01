package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The giant rubber duck's origin: when it began to rise out of the pool, for its renderer, which draws it coming up
 * out of the water over {@link #RISE_TICKS} ticks, the water churning round it. Once it is up, the rest of its blocks
 * go in round the origin (until then only the origin is solid, so it knocks nobody about as it comes up) and it lets
 * out a huge squeak, with a fanfare and a burst of confetti and fireworks. Whoever found the last duck hears the squeak
 * wherever they are aboard, so they know to come and look.
 */
public class GiantRubberDuckBlockEntity extends BlockEntity {
    /** How long it takes to rise out of the water, in ticks. */
    public static final int RISE_TICKS = 50;
    /** The confetti: its colours, how much of each, how far it spreads, how high over the duck it bursts and how fast it flies. */
    private static final int[] CONFETTI_COLOURS = {0xff4060, 0xffd020, 0x40c0ff, 0x60e060, 0xff8020, 0xc060ff};
    private static final int CONFETTI = 30;
    private static final double CONFETTI_SPREAD = 2.0;
    private static final double CONFETTI_HEIGHT = 3.5;
    private static final double CONFETTI_SPEED = 0.2;
    private static final float CONFETTI_SIZE = 1.5F;
    /** The fireworks' sparks. */
    private static final int SPARKS = 60;
    private static final double SPARK_SPREAD = 0.5;
    private static final double SPARK_SPEED = 0.3;
    /** The huge squeak: loud enough to carry the length of the cabin, a sound carrying 16 blocks for each of its volume. */
    private static final float SQUEAK_VOLUME = 4.0F;
    private static final double EARSHOT = 16 * SQUEAK_VOLUME;
    /** The fanfare and the fireworks' bang. */
    private static final float FANFARE_VOLUME = 2.0F;
    /** The splash as it starts to rise: big and deep. */
    private static final float SPLASH_VOLUME = 2.0F;
    private static final float SPLASH_PITCH = 0.6F;
    /** While it rises: how far round it the water churns, in blocks, and how many splashes a tick. */
    private static final double CHURN_RADIUS = 1.8;
    private static final int CHURN = 4;
    /** The water's surface, in blocks up the origin's block from its bottom, where the churning shows. */
    private static final double WATER_LINE = 0.9;
    /** Long enough ago that it rose before anyone saw, for a duck whose rising was never heard of. */
    private static final long LONG_AGO = -1_000_000;

    /** When it began to rise, in game ticks. */
    private long rose = LONG_AGO;
    /** Whether it is up, the rest of its blocks in and its squeak let out. */
    private boolean up;
    /** Who found the last duck, while it rises; not kept once it is up. */
    @Nullable
    private UUID finder;

    public GiantRubberDuckBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.GIANT_RUBBER_DUCK_ENTITY.get(), pos, state);
    }

    /** Starts it rising, now, with a great splash, for the player who found the last duck; every client in sight hears of it. */
    void rise(ServerPlayer finder) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        rose = server.getGameTime();
        up = false;
        this.finder = finder.getUUID();
        setChanged();
        server.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        server.playSound(null, getBlockPos(), SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, SPLASH_VOLUME, SPLASH_PITCH);
    }

    /** How far it has risen at the time, from 0 at the bottom of the pool to 1 up and afloat, slowing as it surfaces. */
    public float risen(double time) {
        float share = (float) Mth.clamp((time - rose) / RISE_TICKS, 0, 1);
        return 1 - (1 - share) * (1 - share);
    }

    /** Once risen: the rest of its blocks in round it, and its squeak, fanfare and confetti. */
    void serverTick() {
        if (up || !(level instanceof ServerLevel server) || server.getGameTime() - rose < RISE_TICKS) {
            return;
        }
        up = true;
        setChanged();
        GiantRubberDuckBlock.fill(server, getBlockPos(), getBlockState().getValue(GiantRubberDuckBlock.FACING));
        celebrate(server, Vec3.atBottomCenterOf(getBlockPos()), finder != null && server.getPlayerByUUID(finder) instanceof ServerPlayer player ? player : null);
        finder = null;
    }

    /** While it rises, the water churns and bubbles round it. */
    void clientTick() {
        if (level == null || level.getGameTime() - rose >= RISE_TICKS) {
            return;
        }
        RandomSource random = level.random;
        BlockPos pos = getBlockPos();
        for (int i = 0; i < CHURN; i++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double x = pos.getX() + 0.5 + Math.cos(angle) * CHURN_RADIUS;
            double z = pos.getZ() + 0.5 + Math.sin(angle) * CHURN_RADIUS;
            level.addParticle(ParticleTypes.SPLASH, x, pos.getY() + WATER_LINE, z, 0, 0.2, 0);
            level.addParticle(ParticleTypes.BUBBLE, x, pos.getY() + WATER_LINE - random.nextDouble(), z, 0, 0.1, 0);
        }
    }

    /**
     * The giant duck's moment, at a spot: a huge squeak, a fanfare and a burst of confetti in all colours with the
     * sparks and bang of fireworks, for everyone near; and the squeak for whoever found the last duck, from wherever
     * they are, if that is out of earshot.
     */
    static void celebrate(ServerLevel level, Vec3 at, @Nullable ServerPlayer finder) {
        level.playSound(null, at.x, at.y, at.z, AerolargeRegistry.GIANT_RUBBER_DUCK_SQUEAK.get(), SoundSource.BLOCKS, SQUEAK_VOLUME, 1.0F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, FANFARE_VOLUME, 1.0F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.BLOCKS, FANFARE_VOLUME, 1.0F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.BLOCKS, FANFARE_VOLUME, 1.0F);
        double burst = at.y + CONFETTI_HEIGHT;
        for (int colour : CONFETTI_COLOURS) {
            Vector3f rgb = new Vector3f((colour >> 16 & 0xFF) / 255F, (colour >> 8 & 0xFF) / 255F, (colour & 0xFF) / 255F);
            level.sendParticles(new DustParticleOptions(rgb, CONFETTI_SIZE), at.x, burst, at.z, CONFETTI,
                CONFETTI_SPREAD, CONFETTI_SPREAD / 2, CONFETTI_SPREAD, CONFETTI_SPEED);
        }
        level.sendParticles(ParticleTypes.FIREWORK, at.x, burst, at.z, SPARKS, SPARK_SPREAD, SPARK_SPREAD, SPARK_SPREAD, SPARK_SPEED);
        if (finder != null && finder.level() == level && finder.position().distanceTo(SableFlight.toWorld(level, at)) > EARSHOT) {
            finder.playNotifySound(AerolargeRegistry.GIANT_RUBBER_DUCK_SQUEAK.get(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("Rose", rose);
        tag.putBoolean("Up", up);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        rose = tag.contains("Rose") ? tag.getLong("Rose") : LONG_AGO;
        up = tag.getBoolean("Up");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
