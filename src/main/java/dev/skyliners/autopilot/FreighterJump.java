package dev.skyliners.autopilot;

import dev.skyliners.Aboard;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.compat.SableFlight;
import dev.skyliners.engineering.JumpDriveBlock;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The freighter's hyperspace jump to the pin she is set to fly to (see {@link FreighterFlight#jump}), asked for from
 * her console's jump button while she flies along. Her drive in engineering spools up as her computer says so, and
 * meanwhile the land round the pin is loaded, generated where it has to be, out to the server's view distance, by a
 * ticket of its own, a ring at a time from the land under her hull out, as the server goes, so nothing stalls as she
 * comes out there; once the land under her is in, or after {@link #LONGEST_WAIT} at most, she lets go, and a moment
 * later she is moved straight there, her middle over the pin, as high as she was or higher, so her lowest block keeps
 * {@link #CLEARANCE} over the highest block anywhere under her and round her, everyone aboard with her (see
 * {@link SableFlight#jump}). Then her autopilot carries on as if she had flown there: over the pin, she hovers or comes
 * down on it. Everyone aboard is told each step (see {@link HyperspaceJump}) and sees and hears it; everyone near where
 * she goes from or comes out but not aboard sees and hears it from outside (see {@link HyperspaceWake}). The ticket lets
 * go by itself a while after. Called off if she stops flying along before she goes.
 */
public final class FreighterJump {
    /** How long her drive spools up before she can let go, in ticks: as long as her computer takes to say so. */
    static final int SPOOL_TICKS = 70;
    /** How long after she lets go she jumps, in ticks: her computer's last word as the stars race. */
    static final int GO_TICKS = 25;
    /**
     * The longest she waits for the land under her where she comes out to load before she lets go all the same, in
     * ticks after the button: a few seconds, so a slow server never holds her for ever. A server in play loads some
     * twenty new chunks a second while it ticks, and the land under her hull is some hundred and twenty of them.
     */
    static final int LONGEST_WAIT = 200;
    /**
     * How far out from the pin the land is loaded as she jumps, in chunks at most: her view of it, the land under her
     * hull first and the rest after, loading on behind as she comes out.
     */
    private static final int MOST_PRELOAD = 10;
    /**
     * The land round the pin is asked for a ring at a time, from the chunks under her out, a ring every so many ticks,
     * so the server loads it spread over the spool rather than all at once and the land under her first.
     */
    private static final int RING_TICKS = 2;
    /**
     * Where a chunk round the pin is not loaded yet as she jumps, how high its ground is is asked of the world's
     * generator instead, at so many columns across and along it, and so much is allowed over that for trees and
     * buildings it does not know of, in blocks.
     */
    private static final int ESTIMATES = 4;
    private static final int UNSEEN_GROWTH = 24;
    /**
     * How many chunks round where she comes out are looked at a tick as she waits, those not in yet guessed at by the
     * generator then, so none is left to guess all at once as she jumps: some eighty chunks lie round her, in a spool
     * of seventy ticks.
     */
    private static final int GUESSES_PER_TICK = 2;
    /**
     * How long the ticket loading the land round the pin lasts, in ticks: as long as she may wait, jump and settle
     * there, after which it lets go by itself, the players aboard keeping the land round them loaded as ever.
     */
    private static final int TICKET_TICKS = LONGEST_WAIT + GO_TICKS + 200;
    /** The ticket that loads the land round the pin, its own kind, by the pin's chunk. */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("skyliners_hyperspace_jump", Comparator.comparingLong(ChunkPos::toLong),
        TICKET_TICKS);
    /** The nearest a pin may be for her to jump to it, in blocks along the ground: any nearer, she flies there. */
    static final double NEAREST = 128;
    /** How far her lowest block keeps over the highest block under her and round her where she comes out, in blocks. */
    static final int CLEARANCE = 10;
    /**
     * For how many ticks after she jumps those aboard as she went are looked after, from how many ticks after it, once
     * Sable has her where she jumped to, and how far from where they were among her blocks one counts as left behind,
     * in blocks.
     */
    private static final int SETTLE_TICKS = 5;
    private static final int SETTLE_FROM = 2;
    private static final double LEFT_BEHIND = 2;
    /** How far past her hull's reach the land is looked over for its highest block, in blocks. */
    private static final int MARGIN = 4;

    /** What asking her to jump comes to, each with its own word in the hotbar and its own line from her computer. */
    public enum Answer implements StringRepresentable {
        /** Her drive spools up. */
        JUMPING,
        /** She has no pin to jump to. */
        NO_PIN,
        /** She is not flying along: on the ground, lifting off, coming down or switched off. */
        NOT_FLYING,
        /** Her pin is too near to bother: she flies there. */
        TOO_NEAR,
        /** She is jumping already. */
        BUSY,
        /** The server's config has her jump switched off. */
        SWITCHED_OFF;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Whether she is on her way into a jump, and how many ticks since the button. */
    private boolean jumping;
    private int ticks;
    /** The tick she lets go on, -1 before she does. */
    private int goAt = -1;
    /** The pin she jumps to, in the world, its chunk, and how far out round it the land is loaded, in chunks. */
    private double toX;
    private double toZ;
    private ChunkPos toChunk = ChunkPos.ZERO;
    private int preload;
    /** How far out round the pin, in chunks, the land under her hull reaches, which must be in before she goes. */
    private int under;
    /** How far out round the pin the land has been asked for so far, in chunks. */
    private int asked;
    /**
     * How far round the pin her hull reaches and a margin, in blocks; the chunks within it, and how high the generator
     * guesses each reaches that was not in when looked at, by chunk, and how many have been looked at so far.
     */
    private int around;
    /** How far her hull reaches out from her middle along the ground, in blocks, for those watching from outside. */
    private int hull;
    private final List<ChunkPos> footprint = new ArrayList<>();
    private final Long2IntMap guesses = new Long2IntOpenHashMap();
    private int guessed;
    /** How many ticks after the button the land round the pin was in, for play-testing; -1 before it is. */
    private int loadedAfter = -1;
    /**
     * Those aboard as she jumped, each by where they were among her blocks, to put back aboard any she left behind,
     * and for how many ticks more they are looked after.
     */
    private final List<Rider> riders = new ArrayList<>();
    private int settling;

    /** One aboard her as she jumped, or the seat they sat in, and where among her blocks it was. */
    private record Rider(Entity entity, Vec3 aboard) {
    }

    /** Whether she is on her way into a jump. */
    boolean jumping() {
        return jumping;
    }

    /** How many ticks after the button the land round the pin was in last jump, for play-testing; -1 before. */
    int loadedAfter() {
        return loadedAfter;
    }

    /**
     * Asked to jump, on the server, as she flies with her middle where given: her drive spools up and the land round
     * her pin loads, or why not.
     */
    Answer start(ServerLevel level, AutopilotBlockEntity autopilot, Vec3 middle, @Nullable BlockPos drive) {
        if (jumping) {
            return Answer.BUSY;
        }
        Answer refused = refusal(autopilot, middle);
        if (refused != null) {
            return refused;
        }
        jumping = true;
        ticks = 0;
        goAt = -1;
        loadedAfter = -1;
        toX = autopilot.orbitX;
        toZ = autopilot.orbitZ;
        toChunk = new ChunkPos(BlockPos.containing(toX, 0, toZ));
        hull = reach(autopilot, autopilot.frame());
        around = hull + MARGIN;
        under = Mth.ceil(around / (double) SectionPos.SECTION_SIZE);
        lookAround(Mth.floor(toX), Mth.floor(toZ));
        preload = Math.max(under, Math.min(MOST_PRELOAD, level.getServer().getPlayerList().getViewDistance()));
        asked = under;
        level.getChunkSource().addRegionTicket(TICKET, toChunk, asked, toChunk);
        tell(autopilot, HyperspaceJump.Stage.SPOOL, SPOOL_TICKS);
        JumpDriveBlock.spoolForJump(level, drive == null ? null : autopilot.getBlockPos().offset(drive));
        return Answer.JUMPING;
    }

    /** Why she will not jump now, or null if she will. */
    @Nullable
    static Answer refusal(AutopilotBlockEntity autopilot, Vec3 middle) {
        if (!SkylinersConfig.FREIGHTER_HYPERSPACE_JUMP.get()) {
            return Answer.SWITCHED_OFF;
        }
        if (autopilot.lateral != AutopilotBlockEntity.Lateral.DESTINATION) {
            return Answer.NO_PIN;
        }
        if (!flyingAlong(autopilot)) {
            return Answer.NOT_FLYING;
        }
        return Math.hypot(autopilot.orbitX - middle.x, autopilot.orbitZ - middle.z) < NEAREST ? Answer.TOO_NEAR : null;
    }

    /** Whether her autopilot flies her along, climbing or cruising: not lifting off or coming down. */
    static boolean flyingAlong(AutopilotBlockEntity autopilot) {
        return (autopilot.phase == AutopilotBlockEntity.Phase.CLIMB || autopilot.phase == AutopilotBlockEntity.Phase.CRUISE) && !autopilot.comingIn();
    }

    /**
     * Once a tick of hers, on the server, on her way into a jump: spooled up and the land round the pin in, or waited
     * long enough, she lets go; a moment later she jumps. Called off if she has stopped flying along before she goes.
     */
    void tick(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State state, Vec3 middle, FreighterPilot pilot, @Nullable BlockPos drive) {
        settle(level, autopilot);
        if (!jumping) {
            return;
        }
        ticks++;
        if (goAt < 0 && !flyingAlong(autopilot)) {
            callOff(level, autopilot, drive);
            return;
        }
        if (goAt < 0) {
            letGoWhenReady(level, autopilot, state, middle);
        } else if (ticks >= goAt) {
            arrive(level, autopilot, state, middle, pilot, drive);
        }
    }

    /** The land round the pin asked for a ring further out, every {@link #RING_TICKS}, until it reaches her view. */
    private void askFurther(ServerLevel level) {
        if (asked < preload && ticks % RING_TICKS == 0) {
            asked++;
            level.getChunkSource().addRegionTicket(TICKET, toChunk, asked, toChunk);
        }
    }

    /** The chunks within {@link #around} of the pin, to look at as she waits, none guessed at yet. */
    private void lookAround(int x, int z) {
        footprint.clear();
        guesses.clear();
        guessed = 0;
        for (int chunkX = (x - around) >> 4; chunkX <= (x + around) >> 4; chunkX++) {
            for (int chunkZ = (z - around) >> 4; chunkZ <= (z + around) >> 4; chunkZ++) {
                footprint.add(new ChunkPos(chunkX, chunkZ));
            }
        }
    }

    /** The next few chunks round the pin looked at, those not in yet guessed at by the generator. */
    private void guessAhead(ServerLevel level) {
        int until = Math.min(footprint.size(), guessed + GUESSES_PER_TICK);
        for (; guessed < until; guessed++) {
            ChunkPos chunk = footprint.get(guessed);
            if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) == null) {
                guesses.put(chunk.toLong(), estimated(level, chunk));
            }
        }
    }

    /**
     * Spooled up and the land under her where she comes out in, or waited as long as she may, she lets go: those aboard
     * told, those watching from outside shown light gathering on her, and those near where she is to come out streaks
     * rushing in to it.
     */
    private void letGoWhenReady(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State state, Vec3 middle) {
        askFurther(level);
        guessAhead(level);
        boolean loaded = loadedAfter >= 0 || landIn(level, under);
        if (loaded && loadedAfter < 0) {
            loadedAfter = ticks;
        }
        if (ticks >= SPOOL_TICKS && (loaded || ticks >= LONGEST_WAIT)) {
            goAt = ticks + GO_TICKS;
            tell(autopilot, HyperspaceJump.Stage.GO, GO_TICKS);
            showLettingGo(level, autopilot, state, middle);
        }
    }

    /**
     * Those watching from outside shown light gathering on her as she lets go, and those near where she is to come out
     * streaks rushing in to it, as high as she would come out by the land there as it stands now.
     */
    private void showLettingGo(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State state, Vec3 middle) {
        if (!SkylinersConfig.FREIGHTER_HYPERSPACE_WAKE.get()) {
            return;
        }
        List<ServerPlayer> aboard = Aboard.players(autopilot);
        Vec3 among = SableFlight.toPlot(level, autopilot.getBlockPos(), middle, false);
        show(level, new HyperspaceWake(HyperspaceWake.Stage.GATHER, middle, among, state.nose(), hull, GO_TICKS), aboard);
        Vec3 out = new Vec3(toX, middle.y + heightOut(level, autopilot, state) - state.position().y, toZ);
        show(level, new HyperspaceWake(HyperspaceWake.Stage.INBOUND, out, out, state.nose(), hull, GO_TICKS), aboard);
    }

    /**
     * Whether every chunk round the pin out so far, in chunks, is in, whole: loaded and generated, not only asked for.
     */
    private boolean landIn(ServerLevel level, int out) {
        for (int x = -out; x <= out; x++) {
            if (!columnIn(level, toChunk.x + x, out)) {
                return false;
            }
        }
        return true;
    }

    private boolean columnIn(ServerLevel level, int chunkX, int out) {
        for (int z = -out; z <= out; z++) {
            if (level.getChunkSource().getChunkNow(chunkX, toChunk.z + z) == null) {
                return false;
            }
        }
        return true;
    }

    /**
     * She jumps: moved straight to the pin, her middle over it, as high as she was or so high her lowest block keeps
     * {@link #CLEARANCE} over the highest block under her and round her, everyone aboard with her; her autopilot set to
     * hold that height at least, her drive in engineering firing, everyone aboard told she is out, and those near where
     * she went from and where she came out shown it from outside.
     */
    private void arrive(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State state, Vec3 middle, FreighterPilot pilot,
                        @Nullable BlockPos drive) {
        jumping = false;
        double height = heightOut(level, autopilot, state);
        Vec3 by = new Vec3(toX - middle.x, height - state.position().y, toZ - middle.z);
        List<ServerPlayer> aboard = Aboard.players(autopilot);
        riders.clear();
        aboard.stream().map(Entity::getRootVehicle).distinct().filter(rider -> !SableFlight.inPlot(rider))
            .forEach(rider -> riders.add(new Rider(rider, SableFlight.toPlot(level, autopilot.getBlockPos(), rider.position(), false))));
        Vec3 among = SableFlight.toPlot(level, autopilot.getBlockPos(), middle, false);
        if (!SableFlight.jump(autopilot, by)) {
            callOff(level, autopilot, drive);
            return;
        }
        settling = SETTLE_FROM + SETTLE_TICKS;
        autopilot.targetAltitude = Math.max(autopilot.targetAltitude, height);
        pilot.jumped();
        aboard.forEach(player -> PacketDistributor.sendToPlayer(player, new HyperspaceJump(HyperspaceJump.Stage.ARRIVE, GO_TICKS)));
        show(level, new HyperspaceWake(HyperspaceWake.Stage.DEPART, middle, middle, state.nose(), hull, 0), aboard);
        Vec3 out = middle.add(by);
        show(level, new HyperspaceWake(HyperspaceWake.Stage.ARRIVE, out, among, state.nose(), hull, 0), aboard);
        JumpDriveBlock.fireForJump(level, drive == null ? null : autopilot.getBlockPos().offset(drive));
        autopilot.changed();
    }

    /**
     * How high she comes out at the pin, as Sable has her: as high as she flies, or so high her lowest block keeps
     * {@link #CLEARANCE} over the highest block under her and round her there.
     */
    private double heightOut(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State state) {
        BoundingBox frame = autopilot.frame();
        double below = frame == null ? 0 : autopilot.getBlockPos().getY() - frame.minY();
        int highest = highestAround(level, Mth.floor(toX), Mth.floor(toZ));
        return Math.max(state.position().y, highest + CLEARANCE + below + 1);
    }

    /**
     * A step of the jump shown to everyone near enough to see it from outside, but not those aboard her, who see it
     * from inside; to nobody when the server's config leaves the sight out.
     */
    private static void show(ServerLevel level, HyperspaceWake wake, List<ServerPlayer> aboard) {
        if (!SkylinersConfig.FREIGHTER_HYPERSPACE_WAKE.get()) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(wake.at()) < HyperspaceWake.SEEN_WITHIN * HyperspaceWake.SEEN_WITHIN && !aboard.contains(player)) {
                PacketDistributor.sendToPlayer(player, wake);
            }
        }
    }

    /**
     * For a few ticks after she jumps, anyone aboard her as she went, or the seat they sat in, who is not where they
     * were among her blocks is put back there, their motion stopped as hers is: Sable carries those standing on her as
     * a rule, so most are where they were, and moved twice they would be left a jump's length off her. From the tick
     * after she jumps, whatever she carried there lets go of the speed she carried it at, a jump's length a tick (see
     * {@link SableFlight#letGoAboard}).
     */
    private void settle(ServerLevel level, AutopilotBlockEntity autopilot) {
        if (settling <= 0) {
            return;
        }
        settling--;
        SableFlight.letGoAboard(autopilot);
        if (settling >= SETTLE_TICKS) {
            return;
        }
        for (Rider rider : riders) {
            Vec3 there = SableFlight.toWorld(level, rider.aboard());
            if (rider.entity().isAlive() && rider.entity().position().distanceTo(there) > LEFT_BEHIND) {
                rider.entity().setDeltaMovement(Vec3.ZERO);
                rider.entity().teleportTo(there.x, there.y, there.z);
            }
        }
        if (settling == 0) {
            riders.clear();
        }
    }

    /** How far her hull reaches out from her middle along the ground, in blocks, from the box round her blocks. */
    private static int reach(AutopilotBlockEntity autopilot, @Nullable BoundingBox frame) {
        Vec3 middle = FreighterFlight.middleOf(autopilot);
        if (frame == null || middle == null) {
            return 0;
        }
        double across = Math.max(Math.abs(frame.minX() - middle.x), Math.abs(frame.maxX() + 1 - middle.x));
        double along = Math.max(Math.abs(frame.minZ() - middle.z), Math.abs(frame.maxZ() + 1 - middle.z));
        return (int) Math.ceil(Math.hypot(across, along));
    }

    /**
     * The highest block, trees and buildings and all, anywhere within {@link #around} of a column, as the world's
     * motion-blocking heightmap has it; where a chunk is not in yet, as only when she has waited as long as she may,
     * as high as the world's generator guessed it reaches as she waited (see {@link #guessAhead}), never loading it
     * here, which would hold the server up while every chunk asked for loaded.
     */
    private int highestAround(ServerLevel level, int x, int z) {
        int highest = level.getMinBuildHeight();
        for (ChunkPos at : footprint) {
            ChunkAccess chunk = level.getChunkSource().getChunkNow(at.x, at.z);
            highest = Math.max(highest, chunk != null ? highestIn(chunk, x, z, around) : guess(level, at));
        }
        return highest;
    }

    /** How high the generator guessed a chunk not in yet reaches, or guesses now if it was in when looked at. */
    private int guess(ServerLevel level, ChunkPos chunk) {
        return guesses.containsKey(chunk.toLong()) ? guesses.get(chunk.toLong()) : estimated(level, chunk);
    }

    /**
     * How high a chunk not loaded yet may reach, without loading it: the highest ground the world's generator gives
     * across it, and {@link #UNSEEN_GROWTH} over that.
     */
    private static int estimated(ServerLevel level, ChunkPos chunk) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        int highest = level.getMinBuildHeight();
        int step = SectionPos.SECTION_SIZE / ESTIMATES;
        for (int column = 0; column < (ESTIMATES + 1) * (ESTIMATES + 1); column++) {
            int x = chunk.getMinBlockX() + Math.min(column % (ESTIMATES + 1) * step, SectionPos.SECTION_MAX_INDEX);
            int z = chunk.getMinBlockZ() + Math.min(column / (ESTIMATES + 1) * step, SectionPos.SECTION_MAX_INDEX);
            highest = Math.max(highest, generator.getBaseHeight(x, z, Heightmap.Types.MOTION_BLOCKING, level, level.getChunkSource().randomState()));
        }
        return highest + UNSEEN_GROWTH;
    }

    /** The highest block of a chunk's within so many blocks of a column. */
    private static int highestIn(ChunkAccess chunk, int x, int z, int reach) {
        int highest = Integer.MIN_VALUE;
        int fromX = Math.max(chunk.getPos().getMinBlockX(), x - reach);
        int toX = Math.min(chunk.getPos().getMaxBlockX(), x + reach);
        for (int column = fromX; column <= toX; column++) {
            highest = Math.max(highest, highestAlong(chunk, column, x, z, reach));
        }
        return highest;
    }

    private static int highestAlong(ChunkAccess chunk, int column, int x, int z, int reach) {
        int highest = Integer.MIN_VALUE;
        int across = column - x;
        int fromZ = Math.max(chunk.getPos().getMinBlockZ(), z - reach);
        int toZ = Math.min(chunk.getPos().getMaxBlockZ(), z + reach);
        for (int row = fromZ; row <= toZ; row++) {
            int along = row - z;
            if (across * across + along * along <= reach * reach) {
                highest = Math.max(highest, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, column & 15, row & 15));
            }
        }
        return highest;
    }

    /** The jump called off before she goes: everyone aboard told, her drive spun down. */
    private void callOff(ServerLevel level, AutopilotBlockEntity autopilot, @Nullable BlockPos drive) {
        jumping = false;
        tell(autopilot, HyperspaceJump.Stage.CANCEL, 0);
        JumpDriveBlock.spinDown(level, drive == null ? null : autopilot.getBlockPos().offset(drive));
    }

    /** Tells everyone aboard a step of the jump. */
    private static void tell(AutopilotBlockEntity autopilot, HyperspaceJump.Stage stage, int ticks) {
        HyperspaceJump step = new HyperspaceJump(stage, ticks);
        Aboard.players(autopilot).forEach(player -> PacketDistributor.sendToPlayer(player, step));
    }
}
