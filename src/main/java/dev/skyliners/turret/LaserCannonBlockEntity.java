package dev.skyliners.turret;

import dev.skyliners.Aboard;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.LaserBolt;
import dev.skyliners.block.Loaded;
import dev.skyliners.compat.SableFlight;
import dev.skyliners.impact.ShipHits;
import dev.skyliners.item.LaserFire;
import dev.skyliners.spaceweather.SpaceRocks;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
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
 * <p>Either side of its housing, tilting with its barrels, it carries a rocket pod of two tubes, unless the server
 * config's {@code freighter.rockets} is off. Holding use launches a rocket (see {@link CannonRocket}) from the next
 * loaded tube every few ticks, flying to where the sight looks and homing in on the hostile mob or the space rock it
 * rests on, if any, until the pods are empty; a tap launches one. A moment after the last launch the pods load again, a
 * rocket at a time with a clack. It holds its rockets where it holds its bolts.
 *
 * <p>Far past the edge of space, its bolts and rockets shoot at the rocks of an asteroid storm too, out to {@link
 * #ROCK_REACH}, as nothing out there stops them: each bolt breaks the one block of a rock it meets, a rocket a hole in
 * it, and a rock worn through bursts (see {@link SpaceRocks}); its sight locks on to one.
 *
 * <p>The server works out the aim each tick: what the gunner looks at, along their look from the sight, and the way to
 * it from the cannon's pivot, turned into her frame, so its bolts land under the sight's crosshair at any range. It
 * tells the clients now and then, as it moves, and at each bolt, with its heat; each client swings its barrels after
 * the aim with their weight (see {@link HeavyTurn}), so on a quick turn the bolts reach the crosshair a moment before
 * the barrels catch up, and counts the heat down itself as it cools.
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
    /**
     * How far its bolts and its sight reach a rock of the space weather, in blocks, past what anything else is reached
     * at: nothing in space stops them, and a rock flying in is worth meeting far out.
     */
    public static final double ROCK_REACH = 256;
    /**
     * How long its sight stays locked on to a space rock after it last rested on it, in ticks, as a gun's lock-on holds
     * a quick target the gunner's look slips off; the same the client's sight shows (see {@code GunnerView}).
     */
    public static final int ROCK_LOCK_HOLDS = 30;
    private static final float DAMAGE = 6;
    private static final double BOLTS_SEEN = 160;
    /** How often, at most, its aim is sent to the clients, in ticks, and how far it must have moved, in degrees. */
    private static final int AIM_SENT_EVERY = 4;
    private static final float AIM_SENT_FROM = 1;
    /**
     * How fast the clients' barrels swing after its aim at most, in degrees a tick, and how much faster or slower they
     * may go each tick, in degrees a tick, so they gather speed and slow again as heavy guns do (see {@link HeavyTurn}).
     */
    private static final float SWING_FASTEST = 20;
    private static final float SWING_GATHER = 3;
    /** How loud a bolt, its overheating and her computer's word are, as shares of full. */
    private static final float SHOT_VOLUME = 1.4F;
    private static final float OVERHEAT_VOLUME = 0.8F;
    private static final float VOICE_VOLUME = 0.8F;
    /** How many rockets its pods hold: two tubes in each of its two pods. */
    public static final int ROCKETS = 4;
    /**
     * Its rocket pods: how far either side of its middle line they stand, how far above and below the pivot their
     * tubes are, and how far ahead of the pivot their mouths are, in blocks, in the frame its barrels tilt in.
     */
    public static final double POD_OUT = 0.64;
    public static final double TUBE_UP = 0.09;
    public static final double POD_MOUTH = 0.55;
    /** How far ahead of its tube's mouth a rocket starts, in blocks, clear of the pod. */
    private static final double LAUNCHED_AHEAD = 0.3;
    /**
     * How far off what the sight looks at must be for the rockets to fly there from their tubes, crossing under the
     * crosshair, rather than straight along its barrels, in blocks.
     */
    private static final double CONVERGE_FROM = 4;
    /** How often it launches a rocket while use is held, in ticks a rocket: all four in a little over a second. */
    private static final int LAUNCH_EVERY = 6;
    /**
     * How long after its last launch its pods start loading again, and how long each rocket takes to load, in ticks:
     * empty pods are full again eight seconds after the last rocket left.
     */
    private static final int LOADS_AFTER = 20;
    private static final int LOAD_EVERY = 35;
    /** How loud a rocket's launch is, past full so it carries thirty-two blocks, and a pod loading, as shares of full. */
    private static final float LAUNCH_VOLUME = 2.0F;
    private static final float LOAD_VOLUME = 0.7F;
    /** How many game ticks there are in a second. */
    private static final double TICKS_A_SECOND = 20;

    /** Where its barrels point, in degrees: round from her bow, as Minecraft turns, and up from level. */
    private float yaw;
    private float pitch;
    /** The aim the clients were last told, whether it held its fire then, and the game tick they were told it. */
    private float toldYaw;
    private float toldPitch;
    private boolean toldHeld;
    private long toldAt;
    /**
     * Whether the clients are owed word of it at once, on the server: of its heat, a bolt having been fired, or of its
     * rockets, one having been launched or loaded.
     */
    private boolean owed;
    /** Who sits at it, on the server; none while the seat is empty. */
    @Nullable
    private Player gunner;
    /** Whether its trigger is held, and the game tick its gunner last said so. */
    private boolean triggerHeld;
    private long heardAt;
    /**
     * The game tick of its last bolt, on the server, and which barrel is next: the left one as it faces, across its
     * middle line toward positive x in the frame its barrels tilt in, or the right. The clients are told which.
     */
    private long lastBolt;
    private boolean leftBarrel;
    /**
     * Whether its gunner holds use down, its rockets' trigger, and whether they pressed it since a rocket last left, so
     * a quick tap launches one; on the server.
     */
    private boolean rocketTriggerHeld;
    private boolean rocketPressed;
    /**
     * Where its gunner's sight looks, in the world, and the hostile mob or the space rock it rests on, none for none; on
     * the server.
     */
    @Nullable
    private Vec3 sighted;
    @Nullable
    private Entity lockedOn;
    @Nullable
    private SpaceRocks.Target lockedRock;
    /** The game tick its sight last rested on the space rock it is locked on to, on the server. */
    private long lockedRockAt;
    /**
     * How many rockets its pods hold, and the game ticks its last rocket was launched and its pods last loaded one, on
     * the server; the clients are told how many it holds.
     */
    private int rockets = ROCKETS;
    private long lastLaunch;
    private long lastLoad;
    /**
     * On a client: whether it has rocket pods at all, as the server's config says, and how long the next rocket had
     * been loading when the server last told it, in ticks, below none while its pods wait to start loading.
     */
    private boolean pods;
    private int loadingFor;
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
    private final HeavyTurn shownYaw = new HeavyTurn(SWING_FASTEST, SWING_GATHER, true);
    private final HeavyTurn shownPitch = new HeavyTurn(SWING_FASTEST, SWING_GATHER, false);
    private boolean drawn;

    /** Hears its servos on a client each tick; the client sets it, so the server never loads what it reaches for. */
    public static Consumer<LaserCannonBlockEntity> clientTicker = cannon -> { };
    /**
     * What this client's player sees from a cannon's sight now, none while they look from none; the client sets it, for
     * a play-test to read (see {@code BlockpilotCommands}).
     */
    public static Supplier<Sighting> clientSighting = () -> null;

    /**
     * A view from a cannon's sight: where the sight is in the world and the way it looks there, of length 1, whether it
     * is locked on to a hostile mob or a space rock in reach, how many rockets its pods hold, and how far the next is
     * loaded, as a share, as the sight shows them.
     */
    public record Sighting(Vec3 eye, Vec3 look, boolean lockedOn, int rockets, float loading) {
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

    /**
     * Its trigger and its rockets' trigger held or let go by a player, on the server: only the player seated at it
     * works them.
     */
    static void trigger(ServerPlayer player, boolean held, boolean rockets) {
        for (LaserCannonBlockEntity cannon : LOADED.in(player.serverLevel())) {
            if (cannon.gunner == player) {
                cannon.triggerHeld = held;
                cannon.rocketPressed |= rockets && !cannon.rocketTriggerHeld;
                cannon.rocketTriggerHeld = rockets;
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

    /**
     * Each tick on the server: who sits at it, where they look, its bolts and rockets while their triggers are held,
     * its pods loading, and word of it.
     */
    void serverTick(ServerLevel level) {
        Player seated = seatedAt(level);
        if (seated != null && seated != gunner) {
            level.playSound(null, worldPosition, SkylinersRegistry.FREIGHTER_COMPUTER_TURRET_READY.get(), SoundSource.BLOCKS, VOICE_VOLUME, 1.0F);
        }
        gunner = seated;
        load(level);
        if (gunner == null) {
            letGo();
        } else {
            aimAt(level, gunner);
            fire(level);
            launch(level);
        }
        tell(level);
    }

    /** With nobody seated at it, its triggers are let go and its sight rests on nothing. */
    private void letGo() {
        triggerHeld = false;
        rocketTriggerHeld = false;
        rocketPressed = false;
        sighted = null;
        lockedOn = null;
        lockedRock = null;
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
     * level to {@link #HIGHEST} above: their look traced from the sight, a space rock it rests on, or as far as its
     * bolts reach a rock if it meets nothing, so they cross the sight's line far out. It holds its fire where they look lower than its barrels go, or where its barrels would put their bolts
     * into her own hull, looked for along them from between their muzzles.
     */
    private void aimAt(ServerLevel level, Player gunner) {
        Vec3 look = gunner.getLookAngle();
        Vec3 sight = SableFlight.toWorld(level, sight(gunner.getYRot(), gunner.getXRot()));
        HitResult seen = LaserFire.trace(level, gunner, sight, look, RANGE, notAboard());
        SpaceRocks.Hit rock = SpaceRocks.along(level, sight, look, rockReach(seen, sight));
        Vec3 target = rock != null ? rock.at() : seen.getType() == HitResult.Type.MISS ? sight.add(look.scale(ROCK_REACH)) : seen.getLocation();
        sighted = target;
        lockedOn = rock == null && seen instanceof EntityHitResult hit && hit.getEntity() instanceof Enemy && hit.getEntity().isAlive() ? hit.getEntity()
            : null;
        lockRock(level, rock);
        Vec3 pivot = SableFlight.toWorld(level, pivot());
        Vec3 way = SableFlight.turnToPlot(level, worldPosition, target.subtract(pivot)).normalize();
        yaw = (float) Math.toDegrees(Math.atan2(-way.x, way.z));
        float wanted = (float) Math.toDegrees(Math.asin(Mth.clamp(way.y, -1, 1)));
        pitch = Mth.clamp(wanted, -LOWEST, HIGHEST);
        held = wanted < -LOWEST || intoHer(level, gunner);
    }

    /**
     * Its sight's lock on a space rock: on the one it rests on now, or still on the one it rested on last for
     * {@link #ROCK_LOCK_HOLDS}, while that one is about.
     */
    private void lockRock(ServerLevel level, @Nullable SpaceRocks.Hit rock) {
        long now = level.getGameTime();
        if (rock != null) {
            lockedRock = rock.target();
            lockedRockAt = now;
        } else if (lockedRock != null && (now - lockedRockAt > ROCK_LOCK_HOLDS || lockedRock.centre(level) == null)) {
            lockedRock = null;
        }
    }

    /**
     * How far along a shot from a point a space rock may be met, in blocks: as far as {@link #ROCK_REACH} where the shot
     * meets nothing else, and short of whatever it meets otherwise.
     */
    private static double rockReach(HitResult met, Vec3 from) {
        return met.getType() == HitResult.Type.MISS ? ROCK_REACH : met.getLocation().distanceTo(from);
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
        owed = true;
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
     * every client near, sparks where it strikes and a blast where it meets a block (see {@link TurretBlast}), another
     * ship rocked where it strikes one of hers (see {@link ShipHits}), the block of a space rock it strikes broken (see
     * {@link SpaceRocks}), and its shot heard.
     */
    private void bolt(ServerLevel level) {
        Vec3 way = way(yaw, pitch);
        Vec3 across = way.cross(new Vec3(0, 1, 0)).normalize().scale(leftBarrel ? -BARRELS_APART : BARRELS_APART);
        leftBarrel = !leftBarrel;
        Vec3 muzzle = pivot().add(way.scale(BARREL)).add(across);
        Vec3 from = SableFlight.toWorld(level, muzzle);
        Vec3 along = SableFlight.toWorld(level, muzzle.add(way)).subtract(from);
        HitResult hit = LaserFire.trace(level, gunner, from, along, SpaceRocks.flying(level) ? ROCK_REACH : RANGE, notAboard());
        LaserFire.strike(level, gunner, from, hit, DAMAGE, LaserBolt.RED, BOLTS_SEEN);
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            ShipHits.struck(level, block.getBlockPos(), hit.getLocation(), along, ShipHits.Shot.BOLT, SableFlight.bodyAt(level, worldPosition));
            SpaceRocks.breakBlock(level, block.getBlockPos(), hit.getLocation(), along);
        }
        TurretBlast.throwStruck(level, gunner, hit, along, notAboard(), DAMAGE);
        if (SkylinersConfig.FREIGHTER_TURRET_BLASTS.get()) {
            TurretBlast.at(level, gunner, from, hit, notAboard());
        }
        shotHeard(level, from);
    }

    /** A bolt's shot heard from where it left its muzzle. */
    private static void shotHeard(ServerLevel level, Vec3 from) {
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
     * Launches a rocket from its next loaded tube if its rockets' trigger is pressed or held, it is due, it has rockets
     * and is not holding its fire; with no rocket to launch or holding its fire, the press is let go.
     */
    private void launch(ServerLevel level) {
        long now = level.getGameTime();
        boolean pulled = rocketPressed || rocketTriggerHeld && now - heardAt <= TRIGGER_LAPSES;
        if (!pulled || !SkylinersConfig.FREIGHTER_ROCKETS.get() || held || rockets == 0) {
            rocketPressed = false;
            return;
        }
        if (now - lastLaunch < LAUNCH_EVERY) {
            return;
        }
        rocketPressed = false;
        int tube = ROCKETS - rockets;
        Vec3 mouth = pivot().add(tilted(yaw, pitch, tubeAcross(tube), tubeUp(tube), POD_MOUTH + LAUNCHED_AHEAD));
        Vec3 from = SableFlight.toWorld(level, mouth);
        Vec3 along = SableFlight.toWorld(level, mouth.add(way(yaw, pitch))).subtract(from);
        Vec3 flight = sighted != null && sighted.distanceTo(from) > CONVERGE_FROM ? sighted.subtract(from) : along;
        Vec3 drift = SableFlight.velocity(level, mouth).scale(1 / TICKS_A_SECOND);
        CannonRocket.launch(level, this, gunner, from, flight, drift, lockedOn, lockedRock);
        rockets--;
        lastLaunch = now;
        owed = true;
        level.playSound(null, from.x, from.y, from.z, SkylinersRegistry.CANNON_ROCKET_LAUNCH.get(), SoundSource.BLOCKS, LAUNCH_VOLUME,
            0.95F + level.getRandom().nextFloat() * 0.1F);
    }

    /** Loads a rocket into its pods once it is due, with a clack, until they are full. */
    private void load(ServerLevel level) {
        if (rockets >= ROCKETS || level.getGameTime() < loadingSince() + LOAD_EVERY) {
            return;
        }
        rockets++;
        lastLoad = level.getGameTime();
        owed = true;
        level.playSound(null, worldPosition, SkylinersRegistry.CANNON_ROCKET_LOAD.get(), SoundSource.BLOCKS, LOAD_VOLUME, 1.0F);
    }

    /** The game tick its pods' next rocket began loading: a while after its last launch, or as the last one loaded. */
    private long loadingSince() {
        return Math.max(lastLaunch + LOADS_AFTER, lastLoad);
    }

    /**
     * Where a tube of its pods stands across its middle line and up from its pivot, in blocks, in the frame its barrels
     * tilt in; its tubes numbered in the order they fire, left and right in turn, the upper two first.
     */
    public static double tubeAcross(int tube) {
        return tube % 2 == 0 ? -POD_OUT : POD_OUT;
    }

    public static double tubeUp(int tube) {
        return tube < 2 ? TUBE_UP : -TUBE_UP;
    }

    /**
     * A point in the frame its barrels tilt in, so far across, up and along them, turned to an aim in her frame, from
     * their pivot: as its renderer turns its housing.
     */
    public static Vec3 tilted(float yaw, float pitch, double across, double up, double along) {
        double turn = Math.toRadians(yaw);
        double tilt = Math.toRadians(pitch);
        double y = up * Math.cos(tilt) + along * Math.sin(tilt);
        double ahead = along * Math.cos(tilt) - up * Math.sin(tilt);
        return new Vec3(across * Math.cos(turn) - ahead * Math.sin(turn), y, across * Math.sin(turn) + ahead * Math.cos(turn));
    }

    /**
     * Tells the clients where it aims and whether it holds its fire, now and then as they change, and its heat and its
     * rockets at once, with every bolt and every rocket launched or loaded.
     */
    private void tell(ServerLevel level) {
        long now = level.getGameTime();
        boolean moved = Math.abs(Mth.wrapDegrees(yaw - toldYaw)) >= AIM_SENT_FROM || Math.abs(pitch - toldPitch) >= AIM_SENT_FROM;
        if (owed || (moved || held != toldHeld) && now - toldAt >= AIM_SENT_EVERY) {
            toldYaw = yaw;
            toldPitch = pitch;
            toldHeld = held;
            toldAt = now;
            owed = false;
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

    /**
     * How long since its last bolt on a client at a partial tick, in ticks, as the server last told it and this client
     * has counted since; long past any bolt once loaded.
     */
    public float sinceBolt(float partialTick) {
        return rested + sinceTold + partialTick;
    }

    /**
     * Which barrel its last bolt left from on a client, as the side of its middle line that barrel stands on in the
     * frame its barrels tilt in: 1 toward positive x, -1 toward negative x.
     */
    public int lastBarrel() {
        return leftBarrel ? -1 : 1;
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

    /** Whether it has rocket pods, as the server last told this client. */
    public boolean pods() {
        return pods;
    }

    /** How many rockets its pods hold, as the server last told this client. */
    public int rockets() {
        return rockets;
    }

    /** Whether a tube of its pods holds a rocket on a client: the last ones in the order they fire hold those left. */
    public boolean tubeLoaded(int tube) {
        return tube >= ROCKETS - rockets;
    }

    /**
     * How far its pods' next rocket is loaded on a client at a partial tick, as a share: 0 waiting to start, 1 loaded,
     * and 0 with its pods full.
     */
    public float loading(float partialTick) {
        return rockets >= ROCKETS ? 0 : Mth.clamp((loadingFor + sinceTold + partialTick) / LOAD_EVERY, 0, 1);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LOADED.add(this);
    }

    /** Gone, it lets go of its gunner and what its sight rested on, so it never keeps a player who has left. */
    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
        gunner = null;
        letGo();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
        gunner = null;
        letGo();
    }

    /**
     * Only its aim, its heat, which barrel fires next, whether it holds its fire, and its rockets go to the clients;
     * nothing of it is saved, the aim following whoever next sits at it, the heat long gone and its pods full again by
     * the time it is loaded again.
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
        tag.putBoolean("LeftNext", leftBarrel);
        tag.putBoolean("Held", held);
        tag.putBoolean("Pods", SkylinersConfig.FREIGHTER_ROCKETS.get());
        tag.putInt("Rockets", rockets);
        tag.putInt("Loading", (int) Mth.clamp(now - loadingSince(), -LOADS_AFTER, LOAD_EVERY));
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
        leftBarrel = tag.getBoolean("LeftNext");
        held = tag.getBoolean("Held");
        pods = tag.getBoolean("Pods");
        rockets = tag.contains("Rockets") ? Mth.clamp(tag.getInt("Rockets"), 0, ROCKETS) : ROCKETS;
        loadingFor = tag.getInt("Loading");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
