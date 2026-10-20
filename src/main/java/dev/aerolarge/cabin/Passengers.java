package dev.aerolarge.cabin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.contraptions.actors.seat.SeatBlock;
import com.simibubi.create.content.contraptions.actors.seat.SeatEntity;
import dev.aerolarge.Aboard;
import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.FirstFlight;
import dev.aerolarge.block.CabinSeatBlock;
import dev.aerolarge.block.PilotSeatBlock;
import dev.aerolarge.compat.SableFlight;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * Villagers flying as passengers, when the server settings allow it. Handed a boarding pass, a villager notes where
 * it boarded and who checked it in, then makes for the nearest free seat inside an airliner (a cabin seat or one of
 * Create's, in an airliner standing as blocks or assembled) and sits down once it gets there. Unseated aboard, as it
 * is when the airliner is assembled around it, it sits straight back down. Once the airliner has flown it far enough
 * and stands still with the seatbelt signs off, it gets up and pays its fare in emeralds to whoever checked it in.
 * With no seat to be had for a few minutes it gives the pass back.
 */
public final class Passengers {
    /** How often a passenger looks about, in ticks. */
    private static final int CHECK_TICKS = 10;
    /** How far across, and up and down, a passenger looks for a seat, in blocks. */
    private static final int SEEK_ACROSS = 24;
    private static final int SEEK_UP_DOWN = 6;
    /** How near a seat a passenger has to be to sit down in it, in blocks. */
    private static final double SIT_REACH = 2.0;
    /** How fast a passenger walks to its seat, as a share of its full speed. */
    private static final float WALK_SPEED = 0.6F;
    /** How long a passenger goes without a seat before it gives up, in ticks: three minutes. */
    private static final int GIVE_UP_TICKS = 3 * 60 * 20;
    /** How far a flight has to take a passenger for each emerald of its fare, in blocks, and the most it pays. */
    private static final double BLOCKS_PER_EMERALD = 200;
    private static final int MOST_EMERALDS = 32;
    /** Slower than this, in blocks a second, the airliner stands still; for this many looks in a row, it has arrived. */
    private static final double STILL = 0.5;
    private static final int STILL_LOOKS = 6;

    private Passengers() {
    }

    /**
     * A passenger's trip: where in the world it boarded and who checked it in; how long it has gone without a seat,
     * in ticks, and for how many looks in a row its airliner has stood still.
     */
    public record Trip(Vec3 origin, Optional<UUID> agent, int unseated, int still) {
        public static final Codec<Trip> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Vec3.CODEC.fieldOf("origin").forGetter(Trip::origin),
            UUIDUtil.CODEC.optionalFieldOf("agent").forGetter(Trip::agent),
            Codec.INT.optionalFieldOf("unseated", 0).forGetter(Trip::unseated),
            Codec.INT.optionalFieldOf("still", 0).forGetter(Trip::still)
        ).apply(instance, Trip::new));

        public static Trip boarding(Vec3 origin, @Nullable UUID agent) {
            return new Trip(origin, Optional.ofNullable(agent), 0, 0);
        }
    }

    /** Whether villagers fly as passengers in this world. */
    public static boolean allowed() {
        return AerolargeConfig.VILLAGER_PASSENGERS.get();
    }

    /** Handing a villager a boarding pass makes it a passenger, before it can show its trades. */
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        ItemStack pass = event.getItemStack();
        if (!pass.is(AerolargeRegistry.BOARDING_PASS.get()) || !(event.getTarget() instanceof Villager villager)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Player player = event.getEntity();
        if (!allowed()) {
            player.displayClientMessage(Component.translatable("message.aerolarge.passengers.off"), true);
            return;
        }
        if (villager.hasData(AerolargeRegistry.TRIP)) {
            player.displayClientMessage(Component.translatable("message.aerolarge.boarding_pass.already", villager.getDisplayName()), true);
            return;
        }
        Vec3 here = SableFlight.toWorld(level, villager.position());
        villager.setData(AerolargeRegistry.TRIP, Trip.boarding(here, player.getUUID()));
        pass.consume(1, player);
        player.displayClientMessage(Component.translatable("message.aerolarge.boarding_pass.boarding", villager.getDisplayName()), true);
        cheer(level, here, SoundEvents.VILLAGER_YES);
    }

    public static void onTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager) || villager.tickCount % CHECK_TICKS != 0
            || !(villager.level() instanceof ServerLevel level) || !allowed()) {
            return;
        }
        Trip trip = villager.getExistingDataOrNull(AerolargeRegistry.TRIP);
        if (trip == null) {
            return;
        }
        Trip next = villager.getVehicle() instanceof SeatEntity seat ? seated(level, villager, seat, trip) : seek(level, villager, trip);
        if (next == null) {
            villager.removeData(AerolargeRegistry.TRIP);
        } else {
            villager.setData(AerolargeRegistry.TRIP, next);
        }
    }

    /** Without a seat: sits down if a free one is in reach, or walks to the nearest; gives the pass back in the end. */
    @Nullable
    private static Trip seek(ServerLevel level, Villager villager, Trip trip) {
        Vec3 here = SableFlight.toWorld(level, villager.position());
        if (trip.unseated() >= GIVE_UP_TICKS) {
            villager.spawnAtLocation(new ItemStack(AerolargeRegistry.BOARDING_PASS.get()));
            cheer(level, here, SoundEvents.VILLAGER_NO);
            return null;
        }
        BlockPos seat = SableFlight.nearest(level, villager.position(), SEEK_ACROSS, SEEK_UP_DOWN, at -> freeSeat(level, at));
        if (seat != null) {
            Vec3 seatInWorld = SableFlight.toWorld(level, Vec3.atBottomCenterOf(seat));
            if (seatInWorld.distanceTo(here) <= SIT_REACH) {
                SeatBlock.sitDown(level, seat, villager);
                return new Trip(trip.origin(), trip.agent(), 0, 0);
            }
            // Seats are no place to walk across, so the villager heads for beside it; the brain is reminded each look.
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(seatInWorld, WALK_SPEED, 1));
        }
        return new Trip(trip.origin(), trip.agent(), trip.unseated() + CHECK_TICKS, 0);
    }

    /**
     * Seated: once flown far enough, and still for long enough with the signs off, it gets up and pays its fare. The
     * seat, not the villager, is where the airliner is: in an assembled airliner it stays among its blocks.
     */
    @Nullable
    private static Trip seated(ServerLevel level, Villager villager, SeatEntity seat, Trip trip) {
        Vec3 here = SableFlight.toWorld(level, seat.position());
        boolean still = SableFlight.velocity(level, seat.position()).length() < STILL && !Seatbelts.areOn(villager);
        int stillFor = still ? trip.still() + 1 : 0;
        double flown = Math.hypot(here.x - trip.origin().x, here.z - trip.origin().z);
        if (flown < BLOCKS_PER_EMERALD || stillFor < STILL_LOOKS) {
            return new Trip(trip.origin(), trip.agent(), 0, stillFor);
        }
        villager.stopRiding();
        int emeralds = (int) Math.min(MOST_EMERALDS, flown / BLOCKS_PER_EMERALD);
        ItemStack fare = new ItemStack(Items.EMERALD, emeralds);
        Player agent = trip.agent().map(level::getPlayerByUUID).orElse(null);
        if (agent == null) {
            villager.spawnAtLocation(fare);
        } else {
            agent.getInventory().placeItemBackInInventory(fare);
            agent.displayClientMessage(Component.translatable("message.aerolarge.passengers.fare", villager.getDisplayName(), emeralds, (int) flown), false);
            if (agent instanceof ServerPlayer collector) {
                FirstFlight.FREQUENT_FLYER.award(collector);
            }
        }
        cheer(level, here, SoundEvents.VILLAGER_CELEBRATE);
        return null;
    }

    /**
     * Whether there is one of Create's seats at the spot, inside an airliner's hull, with nobody in it; not a pilot's
     * seat, though it is one of Create's underneath, nor the top of a cabin seat's back, which is no seat to sit in.
     */
    private static boolean freeSeat(Level level, BlockPos at) {
        BlockState state = level.getBlockState(at);
        Block seat = state.getBlock();
        return seat instanceof SeatBlock && !(seat instanceof PilotSeatBlock) && !CabinSeatBlock.isBack(state) && Aboard.insideHull(level, at.above())
            && !SeatBlock.isSeatOccupied(level, at);
    }

    /** A villager's sound and a puff of green sparkles where it is in the world, to show what it makes of things. */
    private static void cheer(ServerLevel level, Vec3 at, SoundEvent sound) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.NEUTRAL, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.x, at.y + 1.8, at.z, 6, 0.3, 0.3, 0.3, 0.0);
    }
}
