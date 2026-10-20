package dev.aerolarge.cabin;

import dev.aerolarge.Aboard;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.PlugDoorBlock;
import dev.aerolarge.block.PlugDoorBlockEntity;
import dev.aerolarge.compat.SableFlight;
import dev.aerolarge.entity.Basketball;
import dev.aerolarge.entity.PaperPlane;
import dev.aerolarge.entity.Trolley;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Explosive decompression: a door of an airliner opened in flight lets the cabin's air rush out through it. Whoever
 * stands near the door, and anything loose (mobs, villagers out of their seats, dropped items, a basketball, a
 * trolley, a paper plane), is pulled toward it, the harder the closer, and flung out through it into the open sky;
 * anyone seated is belted in and stays put. With a parachute on, being sucked out is only the start of a skydive.
 * While it blows, the wind roars at the door and streams out of it as fog, and everyone aboard is warned as it starts.
 * Ten seconds after it opened, a plug door shuts by itself again.
 *
 * <p>One per assembled airliner, worked by its autopilot, which reads the airliner each tick anyway. The airliner's
 * doors are found once, a slice of the fuselage a tick from the flight deck back, so after that only those few blocks
 * are looked at; only the doors in the hull's sides count, not the cockpit door or the WC's and the crew room's behind
 * it, which open from one room of the cabin into another. A door stands in the airliner's plot, so its wind and fog
 * are set there, where Sable carries them along with the airliner; the doorway is projected out into the world for the
 * pull, since whoever stands aboard a flying airliner is in the world. Sable carries them along with the cabin too, so
 * they are pushed relative to the cabin. Trolleys and basketballs live in the plot (see
 * {@link SableFlight#keepWithCabin}): each is seen projected out into the world like the rest, its push turned back
 * into the airliner's frame, and once clear of the sill it is sent out into the world at the airliner's speed. Any
 * other loose thing clear of the airliner is carried by nothing, and is sent off at the airliner's speed.
 */
public final class Decompression {
    /** Each assembled airliner's, by its autopilot: forgotten with it, so its doors are found afresh once reassembled. */
    private static final Map<BlockEntity, Decompression> AIRLINERS = new WeakHashMap<>();
    /** It starts only this high above the ground and this fast, in blocks and blocks a second, as rough air does. */
    private static final double MIN_HEIGHT = 20;
    private static final double MIN_SPEED = 20;
    /**
     * Once the air rushes out, it keeps on until the airliner is down to this share of that height or speed, so it does
     * not stop and start again (warning everyone each time) as the ground below rises and falls.
     */
    private static final double KEEP_ON = 0.75;
    /**
     * Where the doors are looked for, in blocks per unit of scale from the autopilot on the flight deck: back past the
     * aft doors (50 m from the nose), out past the skin to either side (the fuselage is 7.1 m wide), and down past the
     * cargo door in the hold however high the flight deck stands (the fuselage is 8.4 m tall).
     */
    private static final double SEARCH_BACK_PER_SCALE = 52;
    private static final double SEARCH_ACROSS_PER_SCALE = 4;
    private static final double SEARCH_BELOW_PER_SCALE = 9;
    /**
     * How far out to either side of the middle a door has to stand to be in the hull's side, in blocks per unit of
     * scale: the passenger doors stand at the fuselage's sides (it is 7.1 m wide), the cargo door in the hold over
     * 2.4 m out, and the doors inside the cabin by the aisle, within about a metre and a half of the middle.
     */
    private static final double HULL_SIDE_PER_SCALE = 2;
    /**
     * How far from the middle of a doorway its pull reaches, in blocks per unit of scale: a few steps along the aisle.
     * The furnished cabin keeps its shop counters out of it.
     */
    public static final double REACH_PER_SCALE = 3;
    /**
     * The pull right at the doorway, in blocks a tick each tick, fading to nothing at the edge of its reach: past the
     * middle of the reach, more than anyone can walk against.
     */
    private static final double PULL = 0.15;
    /**
     * Within this many blocks of the middle of the doorway, the air flings whatever it holds out through it, this hard
     * in blocks a tick each tick: out of the doorway in a couple of ticks and well clear of the hull.
     */
    private static final double FLING_RANGE = 1.5;
    private static final double FLING = 0.5;
    /**
     * The wind: the elytra's rush of air, played loud enough to fill the cabin around the door, and again each time it
     * runs out (it lasts about ten seconds), in ticks.
     */
    private static final float WIND_VOLUME = 3;
    private static final int WIND_TICKS = 200;
    /** How long a plug door stands open in flight before it shuts by itself, in ticks: ten seconds. */
    private static final int SHUT_AFTER = 10 * SharedConstants.TICKS_PER_SECOND;
    /** The bang as a door gives way in flight: a wind charge's burst, loud and deep. */
    private static final float BANG_VOLUME = 2;
    private static final float BANG_PITCH = 0.5F;
    /** Puffs of fog streaming out of each open doorway a tick, how far inside it they start, in blocks, and their speed. */
    private static final int PUFFS = 4;
    private static final double PUFF_INSIDE = 2;
    private static final double PUFF_SPEED = 0.4;
    /** How far in from the doorway's sill and top the fog starts, in blocks. */
    private static final double PUFF_MARGIN = 0.1;
    /** How many blocks tall each leaf of an older airliner's doors is. */
    private static final int LEAF_ROWS = 2;

    /**
     * The feet of the airliner's doors found so far, in the plot: each plug door's bottom left part, or each leaf's
     * lower half in an airliner built before them; and how many slices back have been searched.
     */
    private final List<BlockPos> doors = new ArrayList<>();
    private int searched;
    /**
     * Whether the air is rushing out; and, by the foot of each open door, the tick its wind runs out and the tick it shuts
     * by itself.
     */
    private boolean blowing;
    private final Map<BlockPos, Long> wind = new HashMap<>();
    private final Map<BlockPos, Long> shutAt = new HashMap<>();

    /**
     * One tick of the assembled airliner the autopilot is part of, its nose pointing the given way in the plot, with how
     * it flies and how high above the ground.
     */
    public static void tick(ServerLevel level, BlockEntity autopilot, Direction nose, SableFlight.State flight, double height) {
        AIRLINERS.computeIfAbsent(autopilot, ignored -> new Decompression()).blow(level, autopilot, nose, flight, height);
    }

    private void blow(ServerLevel level, BlockEntity autopilot, Direction nose, SableFlight.State flight, double height) {
        if (!AerolargeConfig.DECOMPRESSION.get()) {
            calm(autopilot);
            return;
        }
        search(level, autopilot.getBlockPos(), nose);
        double share = blowing ? KEEP_ON : 1;
        boolean flying = height >= MIN_HEIGHT * share && flight.airspeed() >= MIN_SPEED * share;
        List<Doorway> open = flying ? openDoorways(level) : List.of();
        if (open.isEmpty()) {
            calm(autopilot);
            return;
        }
        if (!blowing) {
            blowing = true;
            Component warning = Component.translatable("message.aerolarge.decompression").withStyle(ChatFormatting.RED);
            for (ServerPlayer player : Aboard.players(autopilot)) {
                player.displayClientMessage(warning, false);
            }
        }
        List<BlockPos> feet = open.stream().map(Doorway::foot).toList();
        wind.keySet().retainAll(feet);
        shutAt.keySet().retainAll(feet);
        shutInTime(level, feet);
        double reach = REACH_PER_SCALE * AerolargeConfig.SCALE.get();
        AABB around = null;
        for (Doorway doorway : open) {
            roar(level, doorway);
            AABB near = new AABB(doorway.world(), doorway.world()).inflate(reach);
            around = around == null ? near : around.minmax(near);
        }
        // Sable's lookup finds what lives among the airliner's blocks around the doorways too.
        for (Entity entity : level.getEntities((Entity) null, around, Decompression::loose)) {
            Vec3 centre = SableFlight.toWorld(level, entity.getBoundingBox().getCenter());
            pull(level, entity, centre, nearest(open, centre), flight.up(), reach);
        }
    }

    /**
     * Looks through the next slice of the fuselage back from the autopilot for doors in the hull's sides, until it has
     * been all the way. The autopilot stands on the centreline, so a column's way across from it is its way out from
     * the middle.
     */
    private void search(ServerLevel level, BlockPos autopilot, Direction nose) {
        double scale = AerolargeConfig.SCALE.get();
        if (searched > SEARCH_BACK_PER_SCALE * scale) {
            return;
        }
        BlockPos slice = autopilot.relative(nose.getOpposite(), searched++);
        int across = (int) Math.ceil(SEARCH_ACROSS_PER_SCALE * scale);
        int below = (int) Math.ceil(SEARCH_BELOW_PER_SCALE * scale);
        for (int side = -across; side <= across; side++) {
            if (Math.abs(side) < HULL_SIDE_PER_SCALE * scale) {
                continue;
            }
            BlockPos column = slice.relative(nose.getClockWise(), side);
            // All of an airliner's plot is loaded; never load a chunk just to look into it.
            if (!level.isLoaded(column)) {
                continue;
            }
            for (int down = 0; down <= below; down++) {
                BlockPos at = column.below(down);
                BlockState state = level.getBlockState(at);
                boolean leaf = state.is(AerolargeRegistry.AIRLINER_DOOR.get()) && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER;
                if (PlugDoorBlock.isController(state) || leaf) {
                    doors.add(at);
                }
            }
        }
    }

    /**
     * Shuts each plug door that has stood open in flight for {@link #SHUT_AFTER}, as an airliner's door would be pulled
     * shut again: it still waits for anyone in its way. An older airliner's leaves stay as they are.
     */
    private void shutInTime(ServerLevel level, List<BlockPos> feet) {
        long now = level.getGameTime();
        for (BlockPos foot : feet) {
            if (now >= shutAt.computeIfAbsent(foot, ignored -> now + SHUT_AFTER)) {
                PlugDoorBlockEntity door = PlugDoorBlockEntity.of(level, foot);
                if (door != null) {
                    door.set(false);
                }
                shutAt.remove(foot);
            }
        }
    }

    /** The doorways of the doors standing open. */
    private List<Doorway> openDoorways(ServerLevel level) {
        List<Doorway> open = new ArrayList<>();
        for (BlockPos foot : doors) {
            BlockState door = level.getBlockState(foot);
            if (PlugDoorBlock.isController(door) && door.getValue(PlugDoorBlock.OPEN)) {
                open.add(Doorway.of(level, foot, door.getValue(PlugDoorBlock.FACING), PlugDoorBlock.right(door), PlugDoorBlock.COLUMNS,
                    PlugDoorBlock.rows(level, foot)));
            } else if (door.is(AerolargeRegistry.AIRLINER_DOOR.get()) && door.getValue(DoorBlock.OPEN)) {
                // An older airliner's doors, a pair of leaves hung from outside, facing in: each leaf is a doorway.
                Direction out = door.getValue(DoorBlock.FACING).getOpposite();
                open.add(Doorway.of(level, foot, out, out.getCounterClockWise(), 1, LEAF_ROWS));
            }
        }
        return open;
    }

    /** The wind and the fog at an open doorway, and the bang as it gives way. */
    private void roar(ServerLevel level, Doorway doorway) {
        Vec3 middle = doorway.middle();
        long now = level.getGameTime();
        Long until = wind.get(doorway.foot());
        if (until == null) {
            level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.WIND_CHARGE_BURST, SoundSource.AMBIENT, BANG_VOLUME, BANG_PITCH);
        }
        if (until == null || now >= until) {
            level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.ELYTRA_FLYING, SoundSource.AMBIENT, WIND_VOLUME, 1.0F);
            wind.put(doorway.foot(), now + WIND_TICKS);
        }
        RandomSource random = level.getRandom();
        Vec3 out = Vec3.atLowerCornerOf(doorway.out().getNormal());
        Vec3 across = Vec3.atLowerCornerOf(doorway.across().getNormal());
        // The sill, a block under the middle.
        Vec3 sill = middle.subtract(0, 1, 0);
        for (int i = 0; i < PUFFS; i++) {
            // Anywhere across the doorway and up and down it, from a little inside it.
            Vec3 at = sill.add(out.scale(-random.nextDouble() * PUFF_INSIDE))
                .add(across.scale((random.nextDouble() - 0.5) * doorway.width()))
                .add(0, PUFF_MARGIN + random.nextDouble() * (doorway.rows() - 2 * PUFF_MARGIN), 0);
            level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 0, out.x, out.y, out.z, PUFF_SPEED);
        }
    }

    /** The air stops rushing out: the wind dies away for everyone aboard. */
    private void calm(BlockEntity autopilot) {
        if (!blowing) {
            return;
        }
        blowing = false;
        wind.clear();
        shutAt.clear();
        ClientboundStopSoundPacket stop = new ClientboundStopSoundPacket(SoundEvents.ELYTRA_FLYING.getLocation(), SoundSource.AMBIENT);
        for (ServerPlayer player : Aboard.players(autopilot)) {
            player.connection.send(stop);
        }
    }

    /**
     * Whether the rushing air takes the entity: anything alive that is not seated (belted in), flying or spectating,
     * and loose things: dropped items, basketballs, trolleys and paper planes.
     */
    private static boolean loose(Entity entity) {
        boolean takes = entity instanceof LivingEntity || entity instanceof ItemEntity || entity instanceof Basketball || entity instanceof Trolley
            || entity instanceof PaperPlane;
        return takes && !entity.isPassenger() && !entity.isSpectator() && !(entity instanceof Player player && player.getAbilities().flying);
    }

    /** The open doorway nearest an entity's middle in the world: the one whose air takes it. */
    private static Doorway nearest(List<Doorway> open, Vec3 centre) {
        return open.stream().min(Comparator.comparingDouble(doorway -> doorway.world().distanceToSqr(centre))).orElseThrow();
    }

    /**
     * Pulls the entity, its middle at the given point in the world, toward the doorway, the harder the closer, and
     * flings it out once it is in it. Only from inside, and only where the air has a clear way to the door: not up
     * through the floor from the hold, or round a wall. Whatever is already out past the middle of the doorway is only
     * sped on its way while still in it.
     */
    private static void pull(ServerLevel level, Entity entity, Vec3 centre, Doorway doorway, Vec3 up, double reach) {
        Vec3 offset = centre.subtract(doorway.world());
        double distance = offset.length();
        double depth = offset.dot(doorway.outward());
        if (distance >= reach) {
            return;
        }
        if (depth > 0) {
            if (distance < FLING_RANGE) {
                fling(level, entity, doorway, depth);
            }
            return;
        }
        // Inside the door's own block (out to half a block in from its middle) nothing can stand in the way.
        if (depth < -0.5 && !clearWay(level, entity, centre, doorway)) {
            return;
        }
        Vec3 push = offset.scale(-PULL * (1 - distance / reach) / Math.max(distance, 1e-3));
        if (distance < FLING_RANGE) {
            push = push.add(doorway.outward().scale(FLING));
        }
        if (!(entity instanceof LivingEntity)) {
            // Loose things slide along the floor: one lifted off it in the world is carried along with the cabin no more.
            push = push.subtract(up.scale(push.dot(up)));
        }
        push(level, entity, push);
    }

    /**
     * Speeds the entity out of the doorway, depth blocks out past its middle. Anything alive leaves keeping the speed
     * the cabin carried it at. A loose thing clear of the sill is carried by nothing any more: one that lived among the
     * airliner's blocks is sent out into the world at the airliner's speed plus its own, any other is sent off at the
     * airliner's speed, or it would stop dead in the air as the airliner flies on.
     */
    private static void fling(ServerLevel level, Entity entity, Doorway doorway, double depth) {
        Vec3 out = doorway.outward().scale(FLING);
        if (entity instanceof LivingEntity || depth <= 0.5 + entity.getBbWidth() / 2) {
            push(level, entity, out);
        } else if (SableFlight.sendOut(entity)) {
            push(level, entity, out);
        } else {
            entity.setDeltaMovement(doorway.velocity().add(out));
            entity.hurtMarked = true;
        }
    }

    /** Pushes the entity, in the world's directions: turned into the airliner's frame for what lives among its blocks. */
    private static void push(ServerLevel level, Entity entity, Vec3 push) {
        entity.push(SableFlight.turnToPlot(level, entity.blockPosition(), push));
        entity.hurtMarked = true;
    }

    /**
     * Whether the air has a clear way from a point in the world to the inside of the doorway: nothing solid between but
     * a door. Cast through the airliner's own blocks, in its plot.
     */
    private static boolean clearWay(ServerLevel level, Entity entity, Vec3 from, Doorway doorway) {
        Vec3 inside = doorway.middle().subtract(Vec3.atLowerCornerOf(doorway.out().getNormal()).scale(0.5));
        Vec3 start = SableFlight.toPlot(level, doorway.foot(), from, false);
        BlockHitResult hit = level.clip(new ClipContext(start, inside, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        BlockState hitState = level.getBlockState(hit.getBlockPos());
        return hit.getType() == HitResult.Type.MISS || PlugDoorBlock.isDoor(hitState) || hitState.is(AerolargeRegistry.AIRLINER_DOOR.get());
    }

    /**
     * An open doorway: the foot of its door (its bottom left part, seen from outside), the way out through it and the
     * way across it from there, how many blocks wide and tall it is, in the plot; the middle of the doorway across, a
     * block over its sill, in the plot and in the world; the way out in the world, and how fast the doorway moves
     * there, in blocks a tick.
     */
    private record Doorway(BlockPos foot, Direction out, Direction across, int width, int rows, Vec3 middle, Vec3 world, Vec3 outward,
                           Vec3 velocity) {
        static Doorway of(ServerLevel level, BlockPos foot, Direction out, Direction across, int width, int rows) {
            Vec3 middle = Vec3.atCenterOf(foot).add(0, 0.5, 0).add(Vec3.atLowerCornerOf(across.getNormal()).scale((width - 1) / 2.0));
            Vec3 world = SableFlight.toWorld(level, middle);
            Vec3 outward = SableFlight.toWorld(level, middle.add(Vec3.atLowerCornerOf(out.getNormal()))).subtract(world);
            Vec3 velocity = SableFlight.velocity(level, middle).scale(1.0 / SharedConstants.TICKS_PER_SECOND);
            return new Doorway(foot, out, across, width, rows, middle, world, outward, velocity);
        }
    }
}
