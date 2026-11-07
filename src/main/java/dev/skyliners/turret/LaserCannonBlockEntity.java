package dev.skyliners.turret;

import dev.skyliners.Aboard;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.HeadTurn;
import dev.skyliners.block.LaserBolt;
import dev.skyliners.block.Loaded;
import dev.skyliners.compat.SableFlight;
import dev.skyliners.item.LaserFire;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The freighter's laser cannon on her top dome (see {@link LaserCannonBlock}): twin barrels on a swivel over a
 * pedestal, aimed by whoever sits in the gunner's seat under it, its barrels following where they look, from a little
 * below level to near straight up, all the way round. Sitting there, the gunner sees from its sight, over and behind
 * the swivel, as the client's {@code GunnerView} shows them. Holding attack fires it (see {@link GunnerTrigger}): bolts
 * from alternate barrels four times a second, each a beam drawn by every client near, with sparks where it strikes,
 * hurting hostile mobs only, never players, villagers, pets, animals or anyone aboard her, and never a block. Each bolt
 * heats it, and it cools once it rests a moment: twenty bolts' worth of heat and it overheats, hissing, and holds fire
 * until it has cooled right down. It holds its fire too where its barrels would put their bolts into her own hull, and
 * where the gunner looks lower than they go. A gunner sitting down hears her computer say the turret is ready.
 *
 * <p>The server works out the aim each tick: what the gunner looks at, along their look from the sight, and the way to
 * it from the cannon's pivot, turned into her frame, so its bolts land under the sight's crosshair at any range. It
 * tells the clients now and then, as it moves, and at each bolt, with its heat; each client eases its barrels toward
 * the aim (see {@link HeadTurn}) and counts the heat down itself as it cools.
 */
public class LaserCannonBlockEntity extends BlockEntity {
    private static final Loaded<LaserCannonBlockEntity> LOADED = new Loaded<>(LaserCannonBlockEntity.class, true);
    /**
     * How far from the cannon's middle its gunner's eyes may be, seated, in blocks: the seat under her dome's glass.
     */
    public static final double GUNNER_REACH = 4;
    /**
     * How far its barrels tilt, in degrees: down from level as far as clears her dome, and up from level to near
     * straight up.
     */
    public static final float LOWEST = 15;
    private static final float HIGHEST = 88;
    /**
     * Where its barrels pivot, in blocks up from its block's foot, over the top of its pedestal; how long they are from
     * the pivot to their muzzles, and how far either side of its middle line they stand, in blocks.
     */
    public static final double PIVOT_UP = 1.19;
    public static final double BARREL = 1.9;
    public static final double BARRELS_APART = 0.28;
    /**
     * Where the gunner's sight is from the barrels' pivot, in blocks: so far over it and so far back of it, square to
     * the gunner's look, as if fixed to the swivel; clear of the housing, the muzzles low in the view.
     */
    private static final double SIGHT_UP = 1.1;
    private static final double SIGHT_BACK = 0.3;
    /**
     * How often it fires while its trigger is held, in ticks a bolt, and how long a held trigger lasts unheard, in
     * ticks.
     */
    private static final int BOLT_EVERY = 5;
    private static final int TRIGGER_LAPSES = 10;
    /**
     * How much heat overheats it, in bolts' worth; how long after a bolt it starts to cool, and how long a bolt's worth
     * of heat takes to go, in ticks. Fired steadily, it overheats after twenty bolts, and then cools for a couple of
     * seconds before it fires again.
     */
    public static final int MOST_HEAT = 20;
    private static final int COOLS_AFTER = 10;
    private static final double COOL_STEP = 2;
    /** How long a rest the clients are told at most, in ticks: far past cooling down from overheating. */
    private static final int RESTED_TOLD = 1000;
    /**
     * How far its bolts reach, in blocks, how much one hurts a hostile mob, in half hearts, and how far off it is seen.
     */
    public static final double RANGE = 64;
    private static final float DAMAGE = 6;
    private static final double BOLTS_SEEN = 160;
    /** How often, at most, its aim is sent to the clients, in ticks, and how far it must have moved, in degrees. */
    private static final int AIM_SENT_EVERY = 4;
    private static final float AIM_SENT_FROM = 1;
    /** How fast the clients' barrels follow its aim, a share of the way a tick, and at most so many degrees a tick. */
    private static final float SWING_EASE = 0.45F;
    private static final float SWING_STEP = 20;
    /** How loud a bolt, its overheating and her computer's word are, as shares of full. */
    private static final float SHOT_VOLUME = 1.4F;
    private static final float OVERHEAT_VOLUME = 0.8F;
    private static final float VOICE_VOLUME = 0.8F;

    /** Where its barrels point, in degrees: round from her bow, as Minecraft turns, and up from level. */
    private float yaw;
    private float pitch;
    /** The aim the clients were last told, whether it held its fire then, and the game tick they were told it. */
    private float toldYaw;
    private float toldPitch;
    private boolean toldHeld;
    private long toldAt;
    /** Whether the clients are owed word of its heat at once, a bolt having been fired, on the server. */
    private boolean fired;
    /** Who sits at it, on the server; none while the seat is empty. */
    @Nullable
    private Player gunner;
    /** Whether its trigger is held, and the game tick its gunner last said so. */
    private boolean triggerHeld;
    private long heardAt;
    /** The game tick of its last bolt, on the server, and which barrel is next. */
    private long lastBolt;
    private boolean leftBarrel;
    /**
     * Its heat as of its last bolt, in bolts' worth; whether it overheated and is cooling down; and whether it holds
     * its fire where its gunner looks, its barrels pointing into her own hull or the look lower than they go. The
     * server keeps them and tells the clients.
     */
    private double heat;
    private boolean overheated;
    private boolean held;
    /**
     * On a client: how long it had rested since its last bolt when the server last told it its heat, and how many
     * ticks this client has counted since, in ticks.
     */
    private int rested = RESTED_TOLD;
    private int sinceTold;
    /** Its barrels as drawn on a client, easing toward its aim, put straight at it before first drawn. */
    private final HeadTurn shownYaw = new HeadTurn(SWING_EASE, SWING_STEP, true);
    private final HeadTurn shownPitch = new HeadTurn(SWING_EASE, SWING_STEP, false);
    private boolean drawn;

    /** Hears its servos on a client each tick; the client sets it, so the server never loads what it reaches for. */
    public static Consumer<LaserCannonBlockEntity> clientTicker = cannon -> { };
    /**
     * What this client's player sees from a cannon's sight now, none while they look from none; the client sets it, for
     * a play-test to read (see {@code BlockpilotCommands}).
     */
    public static Supplier<Sighting> clientSighting = () -> null;

    /** A view from a cannon's sight: where the sight is in the world, and whether it rests on a hostile mob in reach. */
    public record Sighting(Vec3 eye, boolean lockedOn) {
    }

    public LaserCannonBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.LASER_CANNON_ENTITY.get(), pos, state);
        yaw = bowYaw(state);
        toldYaw = yaw;
    }

    /** The laser cannons loaded in a level, on the server or a client. */
    public static List<LaserCannonBlockEntity> loaded(Level level) {
        return LOADED.in(level);
    }

    /** Its trigger held or let go by a player, on the server: only the player seated at it works it. */
    static void trigger(ServerPlayer player, boolean held) {
        for (LaserCannonBlockEntity cannon : LOADED.in(player.serverLevel())) {
            if (cannon.gunner == player) {
                cannon.triggerHeld = held;
                cannon.heardAt = player.serverLevel().getGameTime();
            }
        }
    }

    /** The way her bow is from a cannon, as Minecraft turns: the way it faces, which its barrels rest pointing. */
    private static float bowYaw(BlockState state) {
        return state.hasProperty(LaserCannonBlock.FACING) ? state.getValue(LaserCannonBlock.FACING).toYRot() : 0;
    }

    /** The way her bow is from it, in degrees as Minecraft turns, in her frame. */
    public float bowYaw() {
        return bowYaw(getBlockState());
    }

    /** Each tick on the server: who sits at it, where they look, its bolts while its trigger is held, and word of it. */
    void serverTick(ServerLevel level) {
        Player seated = seatedAt(level);
        if (seated != null && seated != gunner) {
            level.playSound(null, worldPosition, SkylinersRegistry.FREIGHTER_COMPUTER_TURRET_READY.get(), SoundSource.BLOCKS, VOICE_VOLUME, 1.0F);
        }
        gunner = seated;
        if (gunner == null) {
            triggerHeld = false;
            return;
        }
        aimAt(level, gunner);
        fire(level);
        tell(level);
    }

    /** The player sitting under it, seated, their eyes within {@link #GUNNER_REACH} of it; none for nobody. */
    @Nullable
    private Player seatedAt(ServerLevel level) {
        Vec3 middle = Vec3.atCenterOf(worldPosition);
        for (Player player : level.players()) {
            boolean near = middle.distanceTo(SableFlight.toPlot(level, worldPosition, player.getEyePosition(), false)) <= GUNNER_REACH;
            if (near && player.isPassenger() && !player.isSpectator()) {
                return player;
            }
        }
        return null;
    }

    /**
     * Turns its barrels from its pivot toward what its gunner looks at, in her frame, kept from {@link #LOWEST} below
     * level to {@link #HIGHEST} above: their look traced from the sight, or as far as its bolts reach if it meets
     * nothing. It holds its fire where they look lower than its barrels go, or where its barrels would put their bolts
     * into her own hull, looked for along them from between their muzzles.
     */
    private void aimAt(ServerLevel level, Player gunner) {
        Vec3 look = gunner.getLookAngle();
        Vec3 sight = SableFlight.toWorld(level, sight(gunner.getYRot(), gunner.getXRot()));
        Vec3 target = LaserFire.trace(level, gunner, sight, look, RANGE, notAboard()).getLocation();
        Vec3 pivot = SableFlight.toWorld(level, pivot());
        Vec3 way = SableFlight.turnToPlot(level, worldPosition, target.subtract(pivot)).normalize();
        yaw = (float) Math.toDegrees(Math.atan2(-way.x, way.z));
        float wanted = (float) Math.toDegrees(Math.asin(Mth.clamp(way.y, -1, 1)));
        pitch = Mth.clamp(wanted, -LOWEST, HIGHEST);
        held = wanted < -LOWEST || intoHer(level, gunner);
    }

    /** What its bolts and its aim pass through: anyone aboard her. */
    private Predicate<Entity> notAboard() {
        return entity -> !Aboard.isAboard(entity, this);
    }

    /**
     * Whether its barrels, as they aim, would put their bolts into a block of her own within their reach, once she is
     * assembled: the first block along them from between their muzzles is one of hers.
     */
    private boolean intoHer(ServerLevel level, Player gunner) {
        UUID her = SableFlight.bodyAt(level, worldPosition);
        if (her == null) {
            return false;
        }
        Vec3 muzzles = pivot().add(way(yaw, pitch).scale(BARREL));
        Vec3 from = SableFlight.toWorld(level, muzzles);
        Vec3 far = SableFlight.toWorld(level, muzzles.add(way(yaw, pitch).scale(RANGE)));
        BlockHitResult met = level.clip(new ClipContext(from, far, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, gunner));
        return met.getType() == HitResult.Type.BLOCK && her.equals(SableFlight.bodyAt(level, met.getBlockPos()));
    }

    /** Where its barrels pivot, among her blocks. */
    private Vec3 pivot() {
        return Vec3.atBottomCenterOf(worldPosition).add(0, PIVOT_UP, 0);
    }

    /**
     * Where its gunner's sight is among her blocks, looking round and up as a seated player's turn has it, in her
     * frame: over and back of the barrels' pivot, square to that look.
     */
    public Vec3 sight(float yRot, float xRot) {
        return pivot().add(sightUp(yRot, xRot).scale(SIGHT_UP)).subtract(Vec3.directionFromRotation(xRot, yRot).scale(SIGHT_BACK));
    }

    /** The sight's up, square to a look round and up as a seated player's turn has it, in her frame. */
    public static Vec3 sightUp(float yRot, float xRot) {
        double turn = Math.toRadians(yRot);
        double up = Math.toRadians(-xRot);
        return new Vec3(Math.sin(turn) * Math.sin(up), Math.cos(up), -Math.cos(turn) * Math.sin(up));
    }

    /**
     * Fires a bolt if its trigger is held, it is due, not too hot and not holding its fire, heating it; overheated, it
     * waits until it has cooled right down.
     */
    private void fire(ServerLevel level) {
        long now = level.getGameTime();
        if (overheated && heatAfter(heat, now - lastBolt) <= 0) {
            overheated = false;
        }
        boolean pulled = triggerHeld && now - heardAt <= TRIGGER_LAPSES && SkylinersConfig.FREIGHTER_TURRETS.get();
        if (!pulled || overheated || held || now - lastBolt < BOLT_EVERY) {
            return;
        }
        heat = heatAfter(heat, now - lastBolt) + 1;
        lastBolt = now;
        fired = true;
        bolt(level);
        if (heat >= MOST_HEAT) {
            overheated = true;
            level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, OVERHEAT_VOLUME, 0.8F);
        }
    }

    /**
     * How much of so much heat at a bolt is left after resting so long since, in ticks: all of it for a moment, then a
     * bolt's worth every {@link #COOL_STEP} ticks, down to none.
     */
    private static double heatAfter(double heat, double rested) {
        return Math.max(0, heat - Math.max(0, rested - COOLS_AFTER) / COOL_STEP);
    }

    /**
     * A bolt from the muzzle of its next barrel along its aim: hostile mobs hurt, never anyone aboard her; drawn for
     * every client near, sparks where it strikes, and its shot heard.
     */
    private void bolt(ServerLevel level) {
        Vec3 way = way(yaw, pitch);
        Vec3 across = way.cross(new Vec3(0, 1, 0)).normalize().scale(leftBarrel ? -BARRELS_APART : BARRELS_APART);
        leftBarrel = !leftBarrel;
        Vec3 muzzle = pivot().add(way.scale(BARREL)).add(across);
        Vec3 from = SableFlight.toWorld(level, muzzle);
        Vec3 along = SableFlight.toWorld(level, muzzle.add(way)).subtract(from);
        HitResult hit = LaserFire.trace(level, gunner, from, along, RANGE, notAboard());
        LaserFire.strike(level, gunner, from, hit, DAMAGE, LaserBolt.RED, BOLTS_SEEN, null);
        level.playSound(null, from.x, from.y, from.z, SkylinersRegistry.BALL_TURRET_SHOT.get(), SoundSource.BLOCKS, SHOT_VOLUME,
            0.95F + level.getRandom().nextFloat() * 0.1F);
    }

    /** The way its barrels point at an aim, in her frame. */
    public static Vec3 way(float yaw, float pitch) {
        double turn = Math.toRadians(yaw);
        double tilt = Math.toRadians(pitch);
        return new Vec3(-Math.sin(turn) * Math.cos(tilt), Math.sin(tilt), Math.cos(turn) * Math.cos(tilt));
    }

    /**
     * Tells the clients where it aims and whether it holds its fire, now and then as they change, and its heat at
     * once, with every bolt.
     */
    private void tell(ServerLevel level) {
        long now = level.getGameTime();
        boolean moved = Math.abs(Mth.wrapDegrees(yaw - toldYaw)) >= AIM_SENT_FROM || Math.abs(pitch - toldPitch) >= AIM_SENT_FROM;
        if (fired || (moved || held != toldHeld) && now - toldAt >= AIM_SENT_EVERY) {
            toldYaw = yaw;
            toldPitch = pitch;
            toldHeld = held;
            toldAt = now;
            fired = false;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Each tick on a client: its barrels a tick's way toward its aim, its servos heard as they swing, its heat cooling. */
    void clientTick() {
        if (!drawn) {
            shownYaw.snap(yaw);
            shownPitch.snap(pitch);
            drawn = true;
        }
        shownYaw.tick();
        shownPitch.tick();
        shownYaw.toward(yaw);
        shownPitch.toward(pitch);
        sinceTold++;
        clientTicker.accept(this);
    }

    /** How far its barrels swung this last tick on a client, round and up together, in degrees. */
    public float swing() {
        return Math.abs(shownYaw.at(1) - shownYaw.at(0)) + Math.abs(shownPitch.at(1) - shownPitch.at(0));
    }

    /** How far round its barrels are drawn at a partial tick, in degrees, as Minecraft turns. */
    public float shownYaw(float partialTick) {
        return shownYaw.at(partialTick);
    }

    /** How far up its barrels are drawn at a partial tick, in degrees. */
    public float shownPitch(float partialTick) {
        return shownPitch.at(partialTick);
    }

    /** How hot it is on a client at a partial tick, as a share of the heat that overheats it: 0 cold, 1 overheated. */
    public float heat(float partialTick) {
        return (float) (heatAfter(heat, rested + sinceTold + partialTick) / MOST_HEAT);
    }

    /** Whether it is overheated on a client, holding its fire until it has cooled right down. */
    public boolean overheated(float partialTick) {
        return overheated && heat(partialTick) > 0;
    }

    /**
     * Whether it holds its fire where its gunner looks, its barrels pointing into her own hull or the look lower than
     * they go, as the server last told this client.
     */
    public boolean held() {
        return held;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LOADED.add(this);
    }

    /** Gone, it lets go of its gunner, so it never keeps a player who has left. */
    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
        gunner = null;
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
        gunner = null;
    }

    /**
     * Only its aim, its heat and whether it holds its fire go to the clients; nothing of it is saved, the aim following
     * whoever next sits at it and the heat long gone by the time it is loaded again.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        tag.putFloat("Heat", (float) heat);
        long now = level == null ? lastBolt : level.getGameTime();
        tag.putInt("Rested", (int) Mth.clamp(now - lastBolt, 0, RESTED_TOLD));
        tag.putBoolean("Overheated", overheated);
        tag.putBoolean("Held", held);
        return tag;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        yaw = tag.contains("Yaw") ? tag.getFloat("Yaw") : bowYaw();
        pitch = Mth.clamp(tag.getFloat("Pitch"), -LOWEST, HIGHEST);
        heat = Mth.clamp(tag.getFloat("Heat"), 0, MOST_HEAT);
        rested = tag.contains("Rested") ? tag.getInt("Rested") : RESTED_TOLD;
        sinceTold = 0;
        overheated = tag.getBoolean("Overheated");
        held = tag.getBoolean("Held");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
