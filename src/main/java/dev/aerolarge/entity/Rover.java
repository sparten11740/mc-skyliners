package dev.aerolarge.entity;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.RoverBayBlockEntity;
import dev.aerolarge.compat.SableFlight;
import dev.aerolarge.item.RoverRemoteItem;
import dev.aerolarge.rover.RoverRoute;
import dev.aerolarge.rover.RoverRoute.Leg;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The rocket's rover: a six-wheeled explorer on rocker-bogie legs, a white body in a band of gold foil under a dark
 * blue solar deck, a mast with a twin-lensed camera head and an amber beacon that blinks while it drives. It rides in
 * her cargo bay on its cradle, is lowered to the ground from a platform her bay's door folds down into and hoisted
 * back the same way (see {@link RoverRoute} and {@code block.RoverBayBlockEntity}, which works its door and its
 * winch), and once down is driven by whoever holds its paired remote (see {@link RoverRemoteItem}), looking at it
 * from a camera behind and over it. It climbs a block as a horse does, and never goes further than {@link #range}
 * blocks from her. Nothing hurts it.
 *
 * <p>Stowed, it lives among her blocks, in Sable's plot, its type in {@code #sable:retain_in_sub_level}, so Sable
 * carries it with her as it does Create's seats: its bay sets it in its cradle every tick. Everywhere else it lives in
 * the world, its bay moving it along its way down and up while she stands still, and its own wheels moving it once
 * down. Its bay holds it every tick, telling it where the bay is among her blocks; left without its bay a while, it
 * lets itself down where it is, and once its bay is there again but holds another rover, it goes.
 *
 * <p>Driving, the driver's client sends how they steer (see {@code rover.DriveRover}); the rover keeps to the last of
 * it for half a second and then stops. Called home, by its remote, by its bay's panel, when its driver has gone or
 * when her countdown waits for it, it drives back under the platform by itself and is hoisted aboard; stuck on the
 * way, it is hoisted from where it stands.
 */
public class Rover extends Entity {
    /** What it is doing: in its cradle, on its way down, out on the ground, on its way back up. */
    public enum State {
        STOWED, LOWERING, OUT, HOISTING;

        /** The state saved under a name, or stowed for a name not known. */
        static State named(String name) {
            for (State state : values()) {
                if (state.name().equals(name)) {
                    return state;
                }
            }
            return STOWED;
        }
    }

    /** How far from her it may go, across the ground from her bay's door, in blocks, at most (see {@link #range}). */
    public static final double RANGE = 96;
    /**
     * Where its driver may be, at most, in blocks from it: aboard her, a fair way back from her door, with the rover
     * at the edge of its range on the other side; no further than the server shows its driver an entity.
     */
    public static final double DRIVER_REACH = 160;
    /**
     * How far inside what the server ticks and shows round its driver its range is kept, in blocks: its driver's body
     * stands about so far from her door; and how short its range is at least, in blocks, however short that is.
     */
    private static final double DRIVER_MARGIN = 24;
    private static final double SHORTEST_RANGE = 32;
    /** How often its range is worked out again from the server's view and simulation distances, in ticks. */
    private static final int RANGE_LOOK = 100;

    private static final EntityDataAccessor<Byte> STATE = SynchedEntityData.defineId(Rover.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> LEG = SynchedEntityData.defineId(Rover.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<BlockPos> BAY = SynchedEntityData.defineId(Rover.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> HOOK = SynchedEntityData.defineId(Rover.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> MOVING = SynchedEntityData.defineId(Rover.class, EntityDataSerializers.BOOLEAN);
    /** How far from her it may go now, in blocks (see {@link #range}), so that its driver's screen shows it too. */
    private static final EntityDataAccessor<Float> RANGE_NOW = SynchedEntityData.defineId(Rover.class, EntityDataSerializers.FLOAT);

    /**
     * Driving: its top speed forward and in reverse, in blocks a tick (six and three blocks a second), how quickly it
     * gathers speed and how quickly it sheds it, in blocks a tick each tick, and how fast it turns at most, in degrees
     * a tick, on the spot as well as on the move, as a rover with steered wheels at its corners does.
     */
    public static final double TOP_SPEED = 0.3;
    private static final double REVERSE_SPEED = 0.15;
    private static final double ACCELERATION = 0.015;
    private static final double BRAKING = 0.04;
    private static final double TURN_RATE = 4.5;
    /** How much of its speed it keeps in water, and of what it was going when it runs into something. */
    private static final double IN_WATER = 0.5;
    private static final double BUMPED = 0.5;
    /** Gravity on it, in blocks a tick each tick, what the air leaves of its fall each tick, and its fastest fall. */
    private static final double GRAVITY = 0.08;
    private static final double AIR = 0.98;
    private static final double FASTEST_FALL = 2;
    /** How high it climbs without a jump, in blocks: a block, as a horse does. */
    private static final float STEP_UP = 1.0F;
    /** Below this speed, in blocks a tick, and this turn, in degrees a tick, it counts as standing still. */
    private static final double STILL = 0.01;
    private static final double NOT_TURNING = 0.1;
    /** How long it keeps to its driver's last word, in ticks, before it stops: half a second. */
    private static final int INPUT_TIMEOUT = 10;
    /** How long its horn takes before it sounds again, in ticks. */
    private static final int HONK_TICKS = 12;
    /** How often it looks whether anyone is still near enough to drive it, in ticks. */
    private static final int LEFT_ALONE_LOOK = 20;
    /**
     * How long since its bay last held it it still counts as held, in ticks: its bay ticks after it, so it is held a
     * tick late; and how long it goes without, in ticks, before it lets itself down where it is.
     */
    private static final int HELD = 2;
    private static final int BAY_LOST = 40;
    /**
     * How long it waits once its bay's place holds another rover's bay or none, in ticks, before it goes; and once her
     * body is not there at all, a minute, as long as she might take to load.
     */
    private static final int ORPHAN_TICKS = 100;
    private static final int GONE_TICKS = 60 * 20;
    /**
     * The winch: how fast it lowers and hoists the rover and lets the empty hook down to it, in blocks a tick; and the
     * longest it lowers for, in ticks, should the ground never come (eighty blocks).
     */
    private static final double LOWERING = 0.2;
    private static final double HOISTING = 0.2;
    private static final double FETCHING = 0.4;
    private static final int LONGEST_DROP = 400;
    /** The fewest ticks the empty hook takes to come down to it, and the hoist to lift it. */
    private static final int FEWEST_FETCH = 10;
    private static final int FEWEST_HOIST = 20;
    /**
     * Driving home by itself: how near the spot under the boom's tip it counts as there, in blocks; how far off its
     * way it turns at full lock, in degrees; how far short of the spot it slows down, in blocks; how far off its way
     * it creeps rather than drives, in degrees, and the share of its speed it creeps at, and the least it drives at.
     */
    private static final double ARRIVED = 1.5;
    private static final double FULL_LOCK = 25;
    private static final double SLOW_DOWN = 4;
    private static final double CREEP_OFF = 60;
    private static final double CREEP = 0.15;
    private static final double LEAST_HOMING = 0.25;
    /**
     * And how it knows it is stuck: how often it looks, in ticks, how much nearer it must have come each time, in
     * blocks, and after how many looks without it is hoisted from where it is; and the longest it drives home for, in
     * ticks, before it is hoisted from wherever it is.
     */
    private static final int STUCK_LOOK = 20;
    private static final double PROGRESS = 0.3;
    private static final int STUCK_LOOKS = 4;
    private static final int HOMING_LIMIT = 60 * 20;
    /** How far below it the ground is looked for when it is let down where it is, in blocks. */
    private static final double GROUND_BELOW = 256;
    /** Further than this from where a client had it, in blocks, it is put there at once rather than slid there. */
    private static final double SNAP = 8;
    /** Its wheels' radius, in blocks, which their turning goes by. */
    public static final double WHEEL_RADIUS = 0.3125;
    /** How quickly the steered wheels follow its turning, as a share each tick. */
    private static final float STEERING = 0.25F;

    /**
     * What the client does each tick with each rover it has, its motor's whine: set by the client, so nothing of it is
     * loaded on a server.
     */
    public static Consumer<Rover> clientTicker = rover -> { };

    /** The way its bay's door faces out of her, as its bay last held it; null until it has. */
    @Nullable
    private Direction out;
    /** How far its bay's door is open, 0 shut to 1 open, and where its hinge is in the world, as last held. */
    private double open;
    @Nullable
    private Vec3 home;
    /** How many ticks it has been since its bay last held it, and since its bay's place held another bay or none. */
    private int unheld;
    private int orphaned;
    /** The game time it last ticked on the server: standing where the land is not ticking, it does not. */
    private long tickedAt = Long.MIN_VALUE;
    /** How many ticks into its leg of the way it is, and how many the leg lasts where it goes by distance. */
    private int legTicks;
    private int legLength;
    /** The way it is hoisted along, in the world, from where it was hooked; worked out as the hoist begins. */
    @Nullable
    private List<Vec3> hoistPath;
    private float hoistYaw;
    /** Saved mid-way down or up: put at the nearer end of the way on its first tick. */
    private boolean snapping;

    /** Its driver's last word: how hard ahead or back, how hard right or left, its horn, and when it came. */
    private float forward;
    private float turn;
    private boolean honk;
    private long heardAt = Long.MIN_VALUE;
    @Nullable
    private UUID driver;
    /**
     * Whether it has a driver now, who heard it beep on; and whether its horn was held last tick, and the game time it
     * rests till.
     */
    private boolean driven;
    private boolean honkHeld;
    private long honkReady;
    /** Its speed along the way it faces, in blocks a tick, negative in reverse. */
    private double speed;
    /**
     * Driving home by itself; called home by her countdown, which its driver cannot overrule; and called home while
     * still on its way down, to drive home once there.
     */
    private boolean homing;
    private boolean forced;
    private boolean homeOnceDown;
    private int homingTicks;
    private int stuckLooks;
    private double lastDistance = Double.MAX_VALUE;

    /** On a client: where the server last had it, to slide to over so many ticks. */
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private float lerpYRot;
    private int lerpSteps;
    /** On a client: how far its wheels have turned, in radians, and how far its corner wheels are steered, -1 to 1. */
    private float wheelTurn;
    private float lastWheelTurn;
    private float steer;
    private float lastSteer;

    public Rover(EntityType<? extends Rover> type, Level level) {
        super(type, level);
    }

    /** A rover stowed in the cradle of a bay whose door is at a block among her blocks, facing out the way given. */
    public static Rover stowedIn(Level level, BlockPos bay, Direction out) {
        Rover rover = new Rover(AerolargeRegistry.ROVER.get(), level);
        rover.entityData.set(BAY, bay);
        rover.out = out;
        rover.moveTo(RoverRoute.cradle(bay, out), out.toYRot(), 0);
        return rover;
    }

    /** The rover a remote is paired with, by its id, if it is loaded here; on the server only. */
    @Nullable
    public static Rover find(Level level, @Nullable UUID id) {
        return id != null && level instanceof ServerLevel server && server.getEntity(id) instanceof Rover rover && !rover.isRemoved() ? rover : null;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(STATE, (byte) State.STOWED.ordinal());
        builder.define(LEG, (byte) Leg.WAIT_DOOR.ordinal());
        builder.define(BAY, BlockPos.ZERO);
        builder.define(HOOK, -1F);
        builder.define(MOVING, false);
        builder.define(RANGE_NOW, (float) RANGE);
    }

    public State state() {
        return State.values()[Mth.clamp(entityData.get(STATE), 0, State.values().length - 1)];
    }

    private void setState(State state) {
        entityData.set(STATE, (byte) state.ordinal());
    }

    /** Its leg of the way down or up; only meant while it is on its way. */
    public Leg leg() {
        return Leg.values()[Mth.clamp(entityData.get(LEG), 0, Leg.values().length - 1)];
    }

    private void setLeg(Leg leg) {
        entityData.set(LEG, (byte) leg.ordinal());
        legTicks = 0;
        legLength = 0;
    }

    /** Where its bay's door is, among her blocks. */
    public BlockPos bay() {
        return entityData.get(BAY);
    }

    /**
     * Where its winch's hook is, as a share of the way up from where its slings meet over it to the trolley's pulley:
     * 0 hooked on, 1 wound up; negative while the rover is not on the hook, and its bay draws the hook.
     */
    public float hook() {
        return entityData.get(HOOK);
    }

    /** Whether it is on the move or turning, its beacon blinking and its motor whining. */
    public boolean moving() {
        return entityData.get(MOVING);
    }

    /**
     * Whether a player is near enough to work it from its remote, both as seen in the world: stowed, its place is
     * among her blocks, far from where she is seen. On the server.
     */
    public boolean withinReach(Player player) {
        double reach = driverReach();
        return player.position().distanceToSqr(SableFlight.toWorld(level(), position())) <= reach * reach;
    }

    /**
     * How far from her it may go now, across the ground from her bay's door, in blocks: {@link #RANGE}, or less on a
     * server whose view or simulation distance is short, where it would otherwise drive on past where its driver's
     * game still shows it or the server still moves it.
     */
    public double range() {
        return entityData.get(RANGE_NOW);
    }

    /** How far from its driver it may be, in blocks: {@link #DRIVER_REACH}, or less where the server shows less. */
    private double driverReach() {
        return level().getServer() == null ? DRIVER_REACH : Math.min(DRIVER_REACH, level().getServer().getPlayerList().getViewDistance() * 16.0);
    }

    /** Works its range out again from how far round its driver the server shows and moves things. */
    private void lookAtRange(ServerLevel level) {
        int chunks = Math.min(level.getServer().getPlayerList().getViewDistance(), level.getServer().getPlayerList().getSimulationDistance());
        entityData.set(RANGE_NOW, (float) Mth.clamp((chunks - 1) * 16.0 - DRIVER_MARGIN, SHORTEST_RANGE, RANGE));
    }

    /** Whether it is on the hook, being let down, hoisted or carried along the boom. */
    public boolean onTheHook() {
        return (state() == State.LOWERING || state() == State.HOISTING) && leg().hooked();
    }

    /** On a client: how far its wheels have turned, in radians, between the last tick and this one. */
    public float wheelTurn(float partialTick) {
        return Mth.lerp(partialTick, lastWheelTurn, wheelTurn);
    }

    /** On a client: how far its corner wheels are steered, -1 hard left to 1 hard right, between ticks. */
    public float steer(float partialTick) {
        return Mth.lerp(partialTick, lastSteer, steer);
    }

    /**
     * Held by its bay this tick, from among her blocks: where the bay's door is and the way it faces out, and how far
     * open it is, 0 shut to 1 open.
     */
    public void hold(ServerLevel level, BlockPos bay, Direction out, double open) {
        entityData.set(BAY, bay);
        this.out = out;
        this.open = open;
        home = SableFlight.toWorld(level, RoverRoute.hinge(bay));
        unheld = 0;
        orphaned = 0;
    }

    /** Sent down to the ground from its cradle, its bay's door opening first; whether it was in its cradle to go. */
    public boolean deploy() {
        if (state() != State.STOWED) {
            return false;
        }
        setState(State.LOWERING);
        setLeg(Leg.WAIT_DOOR);
        homeOnceDown = false;
        return true;
    }

    /**
     * Called home: out on the ground, it drives back under the platform and is hoisted aboard; on its way down, it
     * does so once down. Forced, by her countdown, its driver cannot call it off.
     */
    public void comeHome(boolean forced) {
        if (state() == State.LOWERING) {
            homeOnceDown = true;
            this.forced |= forced;
            return;
        }
        if (state() != State.OUT) {
            return;
        }
        if (!homing) {
            homingTicks = 0;
            stuckLooks = 0;
            lastDistance = Double.MAX_VALUE;
        }
        homing = true;
        this.forced |= forced;
    }

    /** Her countdown no longer calls it home: its driver may take it over again. */
    public void release() {
        forced = false;
    }

    /**
     * Put straight back in its cradle, as its bay does once it has stood where the land is not ticking too long for her
     * countdown to wait: it cannot drive home from there.
     */
    public void stowNow() {
        setState(State.STOWED);
        homing = false;
        forced = false;
        homeOnceDown = false;
        placeStowed();
    }

    /** Whether it has ticked on the server lately: standing where the land round it is not ticking, it does not. */
    public boolean ticking(ServerLevel level) {
        return level.getGameTime() - tickedAt <= HELD;
    }

    /**
     * Its driver's word, on the server: how hard ahead (1) or back (-1) and right (1) or left (-1), already held
     * within those, and whether its horn is held. Only out on the ground does it answer; its driver steering it calls
     * off its driving home, unless her countdown called it.
     */
    public void drive(ServerPlayer player, float forward, float turn, boolean honk) {
        this.forward = forward;
        this.turn = turn;
        this.honk = honk;
        heardAt = level().getGameTime();
        driver = player.getUUID();
        if (state() != State.OUT) {
            return;
        }
        if (!driven) {
            driven = true;
            heard(AerolargeRegistry.ROVER_START.get());
        }
        if (homing && !forced && (forward != 0 || turn != 0)) {
            homing = false;
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        ServerLevel level = (ServerLevel) level();
        tickedAt = level.getGameTime();
        if (tickCount % RANGE_LOOK == 1) {
            lookAtRange(level);
        }
        if (snapping) {
            snap(level);
        }
        unheld++;
        if (unheld > BAY_LOST) {
            letDown(level);
        }
        boolean held = out != null && unheld <= HELD;
        switch (state()) {
            case STOWED -> {
                if (held) {
                    placeStowed();
                }
            }
            case LOWERING, HOISTING -> {
                if (held) {
                    followRoute(level);
                }
            }
            case OUT -> drive(level);
        }
    }

    /**
     * Left without its bay a while, as when her blocks have gone: out on the ground wherever it is, falling there if it
     * hangs; and gone once the place its bay was at holds a bay not its own, or none (see {@link #watchForBay}).
     */
    private void letDown(ServerLevel level) {
        if (state() != State.OUT) {
            becomeOut();
        }
        watchForBay(level);
    }

    /** Set in its cradle among her blocks, facing out of her, as she lies: Sable carries it there. */
    private void placeStowed() {
        Vec3 cradle = RoverRoute.cradle(bay(), out);
        if (!position().equals(cradle)) {
            setPos(cradle);
        }
        setYRot(out.toYRot());
        setDeltaMovement(Vec3.ZERO);
        entityData.set(HOOK, -1F);
        entityData.set(MOVING, false);
    }

    /**
     * Without its bay: once the place its bay was at is loaded and holds a bay that is not its own, or none, it goes
     * after a while; so it does, after longer, once that place is among the blocks of a body of Sable's that is not
     * there, as when she has been blown up. While that place is not loaded, as when she is out of reach, it waits.
     */
    private void watchForBay(ServerLevel level) {
        BlockPos bay = bay();
        boolean another = level.isLoaded(bay) && !ownedBy(level, bay);
        boolean gone = SableFlight.inPlotGrid(level, bay) && SableFlight.bodyAt(level, bay) == null;
        if (!another && !gone) {
            orphaned = 0;
            return;
        }
        if (++orphaned > (another ? ORPHAN_TICKS : GONE_TICKS)) {
            discard();
        }
    }

    /** Whether the bay at a place among her blocks holds this rover. */
    private boolean ownedBy(ServerLevel level, BlockPos bay) {
        return level.getBlockEntity(bay) instanceof RoverBayBlockEntity own && getUUID().equals(own.roverId());
    }

    /**
     * Saved mid-way down or up, it is put at the nearer end of the way: still in or by the bay, back in its cradle
     * (where its bay sets it as it holds it); out over the drop or on the ground, on the ground under where it is.
     */
    private void snap(ServerLevel level) {
        snapping = false;
        Leg leg = leg();
        boolean inBay = leg == Leg.WAIT_DOOR || leg == Leg.ROLL_OUT || leg == Leg.HOOK_ON || leg.ordinal() >= Leg.TROLLEY_IN.ordinal();
        if (inBay) {
            setState(State.STOWED);
            entityData.set(HOOK, -1F);
            return;
        }
        becomeOut();
        HitResult ground = level.clip(new ClipContext(position(), position().subtract(0, GROUND_BELOW, 0), ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE, this));
        if (ground.getType() == HitResult.Type.BLOCK) {
            setPos(ground.getLocation());
        }
    }

    /** One tick along its way down or up, each leg on to the next as it ends. */
    private void followRoute(ServerLevel level) {
        Leg leg = leg();
        legTicks++;
        double share = leg.ticks > 0 ? (double) legTicks / leg.ticks : 0;
        boolean done = switch (leg) {
            case WAIT_DOOR -> waitForDoor(level);
            case LOWER -> lower(level);
            case UNHOOK -> letHookUp(share);
            case FETCH -> fetch(level);
            case HOIST -> hoist(level);
            default -> alongBoard(level, leg, share);
        };
        if (done) {
            nextLeg(level, leg);
        }
    }

    /** In its cradle with the door opening: on its way once the door is open. */
    private boolean waitForDoor(ServerLevel level) {
        placeIn(level, RoverRoute.cradle(bay(), out));
        setYRot(outward(level));
        return open >= 1;
    }

    /**
     * The way out of her through its bay's door, as a heading in the world as she lies now: her blocks keep the way
     * they faced as she was assembled, however she has turned since.
     */
    private float outward(ServerLevel level) {
        Vec3 hinge = SableFlight.toWorld(level, RoverRoute.hinge(bay()));
        Vec3 way = SableFlight.toWorld(level, RoverRoute.at(bay(), out, 1, 0)).subtract(hinge);
        return (float) Math.toDegrees(Math.atan2(-way.x, way.z));
    }

    /**
     * Along a leg in the bay or on the platform, facing out of her: rolling out or in, its wheels turning; hooked on,
     * lifted, run along the boom, set down or let go; the hook going down to it or up from it.
     */
    private boolean alongBoard(ServerLevel level, Leg leg, double share) {
        double[] at = RoverRoute.onBoard(leg, share);
        placeIn(level, RoverRoute.at(bay(), out, at[0], at[1]));
        setYRot(outward(level));
        float hook = switch (leg) {
            case HOOK_ON -> (float) (1 - RoverRoute.ease(share));
            case LET_GO -> (float) RoverRoute.ease(share);
            case ROLL_OUT, ROLL_IN -> -1F;
            default -> 0F;
        };
        entityData.set(HOOK, hook);
        entityData.set(MOVING, leg == Leg.ROLL_OUT || leg == Leg.ROLL_IN);
        return share >= 1;
    }

    /** Where a point among her blocks is in the world, the rover put there. */
    private void placeIn(ServerLevel level, Vec3 aboard) {
        Vec3 at = SableFlight.toWorld(level, aboard);
        if (!position().equals(at)) {
            setPos(at);
        }
        setDeltaMovement(Vec3.ZERO);
    }

    /** Lowered on the rope from past the platform's end until it rests on the ground. */
    private boolean lower(ServerLevel level) {
        Vec3 over = SableFlight.toWorld(level, RoverRoute.at(bay(), out, RoverRoute.OVERHANG, RoverRoute.DECK + RoverRoute.LIFT));
        if (legTicks == 1) {
            setPos(over);
        }
        setPos(over.x, getY(), over.z);
        move(MoverType.SELF, new Vec3(0, -LOWERING, 0));
        entityData.set(HOOK, 0F);
        return onGround() || legTicks > LONGEST_DROP;
    }

    /** Down on the ground: the hook let go and wound back up to the trolley. */
    private boolean letHookUp(double share) {
        entityData.set(HOOK, (float) RoverRoute.ease(share));
        return share >= 1;
    }

    /** Coming home: the empty hook let down from the boom's tip to where it stands. */
    private boolean fetch(ServerLevel level) {
        if (legTicks == 1) {
            Vec3 pulley = SableFlight.toWorld(level, RoverRoute.at(bay(), out, RoverRoute.OVERHANG, RoverRoute.PULLEY));
            legLength = Math.max(FEWEST_FETCH, (int) Math.ceil(pulley.distanceTo(position()) / FETCHING));
        }
        double share = (double) legTicks / legLength;
        entityData.set(HOOK, (float) (1 - RoverRoute.ease(share)));
        return share >= 1;
    }

    /**
     * Hoisted from where it was hooked to past the platform's end: straight up first, to the boom's height or as high
     * as it already is, then across, then down to it, turning to face out of her as it goes.
     */
    private boolean hoist(ServerLevel level) {
        if (legTicks == 1 || hoistPath == null) {
            Vec3 from = position();
            Vec3 to = SableFlight.toWorld(level, RoverRoute.at(bay(), out, RoverRoute.OVERHANG, RoverRoute.DECK + RoverRoute.LIFT));
            double top = Math.max(from.y, to.y);
            hoistPath = List.of(from, new Vec3(from.x, top, from.z), new Vec3(to.x, top, to.z), to);
            hoistYaw = getYRot();
            legLength = Math.max(FEWEST_HOIST, (int) Math.ceil(length(hoistPath) / HOISTING));
        }
        double share = Math.min(1, (double) legTicks / legLength);
        double eased = RoverRoute.ease(share);
        setPos(along(hoistPath, eased * length(hoistPath)));
        setYRot(Mth.rotLerp((float) eased, hoistYaw, outward(level)));
        setDeltaMovement(Vec3.ZERO);
        entityData.set(HOOK, 0F);
        return share >= 1;
    }

    private static double length(List<Vec3> path) {
        double length = 0;
        for (int i = 1; i < path.size(); i++) {
            length += path.get(i).distanceTo(path.get(i - 1));
        }
        return length;
    }

    /** The point so far along a path, in blocks. */
    private static Vec3 along(List<Vec3> path, double distance) {
        double left = distance;
        for (int i = 1; i < path.size(); i++) {
            Vec3 from = path.get(i - 1);
            double step = path.get(i).distanceTo(from);
            if (left <= step && step > 0) {
                return from.lerp(path.get(i), left / step);
            }
            left -= step;
        }
        return path.getLast();
    }

    /** On to the next leg of the way, or, at its end, out on the ground or back in its cradle. */
    private void nextLeg(ServerLevel level, Leg leg) {
        Leg next = leg.next();
        if (next == null && leg == Leg.UNHOOK) {
            becomeOut();
        } else if (next == null) {
            setState(State.STOWED);
            placeStowed();
        } else {
            setLeg(next);
        }
    }

    /** Out on the ground, its own wheels its to drive; driving home at once if it was called home on the way down. */
    private void becomeOut() {
        setState(State.OUT);
        entityData.set(HOOK, -1F);
        entityData.set(MOVING, false);
        speed = 0;
        hoistPath = null;
        homing = false;
        if (homeOnceDown) {
            homeOnceDown = false;
            comeHome(forced);
        }
    }

    /** Hooked where it stands to be hoisted home: the empty hook comes down to it first. */
    private void startHoist() {
        setState(State.HOISTING);
        setLeg(Leg.FETCH);
        homing = false;
        forced = false;
        speed = 0;
        hoistPath = null;
        setDeltaMovement(Vec3.ZERO);
        entityData.set(MOVING, false);
    }

    /**
     * One tick out on the ground: steered by its driver while their word holds, or home by itself, its speed eased
     * toward what is asked, turning on its corner wheels, falling where there is nothing under it, climbing a block,
     * and never further than its range from her; its horn; and home by itself once nobody is near enough to drive it.
     */
    private void drive(ServerLevel level) {
        long now = level.getGameTime();
        boolean heard = now - heardAt <= INPUT_TIMEOUT;
        if (driven && !heard) {
            driven = false;
            heard(AerolargeRegistry.ROVER_STOP.get());
        }
        double ahead = heard && !forced ? forward : 0;
        double right = heard && !forced ? turn : 0;
        if (homing && out != null) {
            double[] steering = steerHome(level);
            if (steering == null) {
                return;
            }
            ahead = steering[0];
            right = steering[1];
        }
        roll(ahead, right);
        honk(level, heard && honk && !forced);
        if (now % LEFT_ALONE_LOOK == 0 && !homing && !heard && level.getNearestPlayer(this, driverReach()) == null) {
            comeHome(false);
        }
    }

    /** Its wheels driven so hard ahead and so hard round, each from -1 to 1. */
    private void roll(double ahead, double right) {
        double wanted = ahead * (ahead >= 0 ? TOP_SPEED : REVERSE_SPEED) * (isInWater() ? IN_WATER : 1);
        boolean gathering = Math.abs(wanted) > Math.abs(speed) && Math.signum(wanted) * Math.signum(speed) >= 0;
        speed = approach(speed, wanted, gathering ? ACCELERATION : BRAKING);
        float turning = (float) (right * TURN_RATE);
        setYRot(getYRot() + turning);
        double yaw = Math.toRadians(getYRot());
        Vec3 across = withinRange(new Vec3(-Math.sin(yaw) * speed, 0, Math.cos(yaw) * speed));
        double fall = onGround() ? -GRAVITY : Math.max(-FASTEST_FALL, (getDeltaMovement().y - GRAVITY) * AIR);
        move(MoverType.SELF, new Vec3(across.x, fall, across.z));
        setDeltaMovement(across.x, onGround() ? 0 : fall, across.z);
        if (horizontalCollision) {
            speed *= BUMPED;
        }
        fallDistance = 0;
        entityData.set(MOVING, Math.abs(speed) > STILL || Math.abs(turning) > NOT_TURNING);
    }

    private static double approach(double from, double to, double step) {
        return from < to ? Math.min(to, from + step) : Math.max(to, from - step);
    }

    /** A step across the ground with whatever of it would take the rover past its range from her taken out. */
    private Vec3 withinRange(Vec3 step) {
        if (home == null) {
            return step;
        }
        double dx = getX() + step.x - home.x;
        double dz = getZ() + step.z - home.z;
        double away = Math.hypot(dx, dz);
        if (away <= range()) {
            return step;
        }
        Vec3 outward = new Vec3(dx / away, 0, dz / away);
        double going = step.dot(outward);
        return going > 0 ? step.subtract(outward.scale(going)) : step;
    }

    /**
     * Driving home: how hard ahead and round to steer toward the spot under the boom's tip, slowing as it comes near
     * and creeping while it turns toward it; null once it is there, or stuck, or has driven home too long, and is
     * being hoisted from where it stands.
     */
    @Nullable
    private double[] steerHome(ServerLevel level) {
        Vec3 spot = SableFlight.toWorld(level, RoverRoute.at(bay(), out, RoverRoute.OVERHANG, 0));
        double dx = spot.x - getX();
        double dz = spot.z - getZ();
        double distance = Math.hypot(dx, dz);
        homingTicks++;
        if (distance < ARRIVED || homingTicks > HOMING_LIMIT || stuck(distance)) {
            startHoist();
            return null;
        }
        double off = Mth.wrapDegrees(Math.toDegrees(Math.atan2(-dx, dz)) - getYRot());
        double ahead = Math.abs(off) < CREEP_OFF ? Mth.clamp(distance / SLOW_DOWN, LEAST_HOMING, 1) : CREEP;
        return new double[]{ahead, Mth.clamp(off / FULL_LOCK, -1, 1)};
    }

    /** Whether it has come no nearer home for a while, looked at every so often. */
    private boolean stuck(double distance) {
        if (homingTicks % STUCK_LOOK != 0) {
            return false;
        }
        stuckLooks = lastDistance - distance < PROGRESS ? stuckLooks + 1 : 0;
        lastDistance = distance;
        return stuckLooks >= STUCK_LOOKS;
    }

    /** Its horn, sounded as its driver presses it, no faster than it rests between. */
    private void honk(ServerLevel level, boolean held) {
        if (held && !honkHeld && level.getGameTime() >= honkReady) {
            heard(AerolargeRegistry.ROVER_HORN.get());
            honkReady = level.getGameTime() + HONK_TICKS;
        }
        honkHeld = held;
    }

    /**
     * A sound of the rover's, heard round it by everyone but its driver, whose own client sounds it as they drive:
     * their ears are with the camera at the rover, and the server sends a sound only to those near it.
     */
    private void heard(SoundEvent sound) {
        Player except = driver == null ? null : level().getPlayerByUUID(driver);
        level().playSound(except, getX(), getY(), getZ(), sound, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    /** On a client: slid to where the server has it, its wheels turned as far as it rolled, its corners steered. */
    private void clientTick() {
        if (lerpSteps > 0) {
            lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYRot, getXRot());
            lerpSteps--;
        }
        lastWheelTurn = wheelTurn;
        lastSteer = steer;
        Vec3 moved = position().subtract(xo, yo, zo);
        if (moved.lengthSqr() < SNAP * SNAP) {
            double yaw = Math.toRadians(getYRot());
            double rolled = -moved.x * Math.sin(yaw) + moved.z * Math.cos(yaw);
            wheelTurn += (float) (rolled / WHEEL_RADIUS);
        }
        float turned = Mth.wrapDegrees(getYRot() - yRotO);
        steer += (Mth.clamp(turned / (float) TURN_RATE, -1, 1) - steer) * STEERING;
        clientTicker.accept(this);
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        if (distanceToSqr(x, y, z) > SNAP * SNAP) {
            lerpSteps = 0;
            setPos(x, y, z);
            setYRot(yRot);
            setOldPosAndRot();
            return;
        }
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpSteps = Math.max(1, steps);
    }

    @Override
    public double lerpTargetX() {
        return lerpSteps > 0 ? lerpX : getX();
    }

    @Override
    public double lerpTargetY() {
        return lerpSteps > 0 ? lerpY : getY();
    }

    @Override
    public double lerpTargetZ() {
        return lerpSteps > 0 ? lerpZ : getZ();
    }

    @Override
    public float lerpTargetYRot() {
        return lerpSteps > 0 ? lerpYRot : getYRot();
    }

    @Override
    public float maxUpStep() {
        return STEP_UP;
    }

    /** A remote used on it is paired with it. */
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (!held.is(AerolargeRegistry.ROVER_REMOTE.get())) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer server) {
            RoverRemoteItem.pair(held, this, server);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    /** It never goes through a portal: it would be left in another dimension, far from her. */
    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("State", state().name());
        tag.putString("Leg", leg().name());
        tag.put("Bay", NbtUtils.writeBlockPos(bay()));
        tag.putBoolean("Homing", homing);
        tag.putBoolean("Forced", forced);
    }

    /**
     * A rover loaded from the save waits for its bay to hold it again: out on the ground, it stays out, still paired;
     * saved mid-way down or up, it is put at the nearer end of the way on its first tick.
     */
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        State state = State.named(tag.getString("State"));
        setState(state);
        entityData.set(LEG, (byte) Leg.named(tag.getString("Leg"), Leg.WAIT_DOOR).ordinal());
        NbtUtils.readBlockPos(tag, "Bay").ifPresent(pos -> entityData.set(BAY, pos));
        homing = state == State.OUT && tag.getBoolean("Homing");
        forced = homing && tag.getBoolean("Forced");
        snapping = state == State.LOWERING || state == State.HOISTING;
    }
}
