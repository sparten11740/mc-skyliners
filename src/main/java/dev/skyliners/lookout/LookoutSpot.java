package dev.skyliners.lookout;

import dev.skyliners.Skyliners;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.SetAutopilot;
import dev.skyliners.block.LookoutGlassBlockEntity;
import dev.skyliners.compat.SableFlight;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent by a player looking through the Lookout's Glass (see the client's {@code LookoutView}): drop my flag on the land
 * here, at this x and z. Nothing in it is taken on trust: the server only drops it for a player who can reach a glass
 * standing there already loaded, on land it has loaded, within the glass's range and where the player is looking. The
 * ship's bell is struck twice at the glass, heard through the control car; and if the lookout is alone aboard while
 * the autopilot flies, nobody else there to pick the flag on its map, the flag is made its pin as if picked.
 */
public record LookoutSpot(BlockPos glass, int x, int z) implements CustomPacketPayload {
    /** How far off a spot can be marked through the glass, in blocks: about as far as the land is loaded. */
    public static final int RANGE = 256;
    /**
     * How far off the way the player looks the spot may be, in degrees: their look reaches the server a moment late on
     * a moving ship.
     */
    private static final double AIM_SLACK = 15;
    /** How loud the bell is struck at the glass: heard some 24 blocks round, the length of the control car. */
    private static final float BELL_VOLUME = 1.5F;

    public static final Type<LookoutSpot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "lookout_spot"));
    public static final StreamCodec<FriendlyByteBuf, LookoutSpot> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, LookoutSpot::glass,
        ByteBufCodecs.VAR_INT, LookoutSpot::x,
        ByteBufCodecs.VAR_INT, LookoutSpot::z,
        LookoutSpot::new
    );

    @Override
    public Type<LookoutSpot> type() {
        return TYPE;
    }

    /** On the server: drops the lookout's flag, for a player at a glass who could see the spot through it. */
    public static void handle(LookoutSpot payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator() || !SkylinersConfig.LOOKOUT.get()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = payload.glass();
        // Checked loaded first, so that a position from a client never loads a chunk.
        if (!level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof LookoutGlassBlockEntity glass) || !Container.stillValidBlockEntity(glass, player)) {
            return;
        }
        long now = level.getGameTime();
        Vec3 spot = ground(level, payload.x(), payload.z());
        if (spot == null || !glass.ready(now) || !seen(player, spot)) {
            return;
        }
        glass.spot(player, payload.x(), payload.z(), now);
        level.playSound(null, pos, SkylinersRegistry.LOOKOUT_BELL.get(), SoundSource.BLOCKS, BELL_VOLUME, 1);
        AutopilotBlockEntity autopilot = AutopilotBlockEntity.zeppelinFraming(level, pos);
        if (autopilot != null) {
            pinIfAlone(level, player, glass, autopilot, new BlockPos(payload.x(), 0, payload.z()));
        }
    }

    /** The top of the land at an x and z, if its chunk is loaded already and it is inside the world's border. */
    @Nullable
    private static Vec3 ground(ServerLevel level, int x, int z) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        if (chunk == null || !level.getWorldBorder().isWithinBounds(x, z)) {
            return null;
        }
        return new Vec3(x + 0.5, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1, z + 0.5);
    }

    /** Whether a spot is within the glass's range of the player's eye, and about where they look. */
    private static boolean seen(ServerPlayer player, Vec3 spot) {
        Vec3 toward = spot.subtract(player.getEyePosition());
        if (toward.length() > RANGE + 1 || toward.lengthSqr() < 1) {
            return false;
        }
        double cosine = toward.normalize().dot(player.getLookAngle());
        return cosine >= Math.cos(Math.toRadians(AIM_SLACK));
    }

    /**
     * Makes the flag the autopilot's pin, circling it, when the lookout is the only one aboard and it flies and is not
     * coming in to land: nobody else is there to pick it on the map, so the spot a lookout alone marks is where they
     * mean to go, without leaving the glass for the autopilot's screen.
     */
    private static void pinIfAlone(ServerLevel level, ServerPlayer lookout, LookoutGlassBlockEntity glass, AutopilotBlockEntity autopilot, BlockPos spot) {
        if (autopilot.phase() == AutopilotBlockEntity.Phase.OFF || autopilot.phase() == AutopilotBlockEntity.Phase.ROLLOUT || autopilot.comingIn()) {
            return;
        }
        for (ServerPlayer other : level.players()) {
            if (other != lookout && Boolean.TRUE.equals(SableFlight.rides(other, glass))) {
                return;
            }
        }
        AutopilotBlockEntity.Settings now = autopilot.offered();
        AutopilotBlockEntity.Settings pinned = new AutopilotBlockEntity.Settings(now.speed(), now.altitude(), now.heading(),
            AutopilotBlockEntity.Lateral.DESTINATION, now.circle(), spot, now.landAtPin());
        lookout.displayClientMessage(autopilot.set(SetAutopilot.Action.FLY, pinned), false);
    }
}
