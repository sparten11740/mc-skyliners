package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import dev.skyliners.autopilot.VoiceLength;
import dev.skyliners.compat.SableFlight;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The freighter's boarding ramp, kept by its panel (see {@link RampPanelBlockEntity}): a plate as wide as the well it
 * lies in, hinged along its top edge at the head of the well, level with her deck. Stowed, it lies flat as the well's
 * floor; that floor, her belly's skin under it and whatever stands between are its hatch, taken away as it opens and
 * put back as it shuts. Coming down, the latches let go in a burst of steam and the hatch's blocks go (tick 0), the
 * rams take its weight ({@link #PRESSURE}), the plate swings down on them to the hydraulics' whine, falling one block
 * in two to the ground she stands on ({@link #SWUNG}), and its foot lip unfolds with a thud and a second breath of
 * steam ({@link #OUT}); then the deck it is walked on is laid (see {@link RampDeckBlock}). Going away runs backwards,
 * once nobody stands on it, the steam last as it seals. All the while it swings, blocks under its plate (see
 * {@link RampPlateBlock}) are set where it is each tick, so whoever walks onto it on its way is held up: coming down it
 * carries them down with it; going up it never lifts anyone, holding where it is while anyone stands on it, as it holds
 * coming down while anyone stands under it and shutting while anyone stands in its hatch, waiting with a chime until
 * they move. It will not come down where anything stands in its way, or while someone stands on the stowed plate: it
 * sounds a low note, or waits with a chime until they step off. The server runs it and tells every client where it is
 * going, each client running the same clock to draw it in between.
 */
public final class BoardingRamp {
    /** The ticks of coming down: the rams pressed, the plate swung all the way down, and the foot lip unfolded. */
    public static final int PRESSURE = 10;
    public static final int SWUNG = 80;
    public static final int OUT = 90;
    /** While someone stands on it or on the stowed plate, how often it looks again whether they have stepped off. */
    private static final int CLEAR_CHECK_TICKS = 10;
    /**
     * While it waits so, how often its chime sounds again, in ticks, so whoever holds it hears it waits for them for as
     * long as it does, not only as it stops, with no need to read anything.
     */
    private static final int CHIME_AGAIN_TICKS = 40;
    /** How thick the plate is under its top, in blocks, as it is drawn. */
    public static final float THICKNESS = 0.25F;
    /**
     * How far over the ground the plate's foot stops, in blocks: half its foot lip's thickness and a hair, so the lip
     * lies on the ground rather than in it, its top never in the same plane as the ground's, which flickers through it.
     */
    public static final double FOOT_CLEAR = THICKNESS / 2 + 0.01;
    /**
     * How far under the plate's top the blocks set under it reach at most, in blocks: whoever stands that close under
     * it as it comes down is in its way.
     */
    private static final double SOLID_UNDER = 1.0;
    /**
     * How far under the plate's top someone's feet may be and still stand on it rather than under it, in blocks: its
     * blocks' little steps, and the sixteenths they are cut to, keep a foot on it within this.
     */
    private static final double STANDING_ON = 0.25;
    /** How far in from a block of the hatch someone must be to stand in its way as it shuts, in blocks. */
    private static final double IN_THE_HATCH = 0.05;
    /**
     * How the blocks under the plate are set and taken away each tick it swings: told to the clients, but to nothing
     * round them, as nothing there needs to know, so it costs little.
     */
    private static final int PLATE_UPDATE = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** How loud its sounds are, as shares of full: the latch, the steam, the hydraulics and the foot's thud. */
    private static final float LATCH_VOLUME = 1.0F;
    private static final float STEAM_VOLUME = 1.0F;
    private static final float SECOND_STEAM_VOLUME = 0.6F;
    private static final float HYDRAULICS_VOLUME = 1.0F;
    private static final float THUD_VOLUME = 1.0F;
    /** Her computer's line as it comes down, heard from the panel, as a share of full. */
    private static final float VOICE_VOLUME = 0.8F;
    /**
     * How long that line holds her cockpit's floor if its sound cannot be read, in ticks: tools/generate_sounds.mjs
     * keeps it no longer than this (its COMPUTER_LONGEST).
     */
    private static final int VOICE_LONGEST = 90;
    /** The latch clunks higher coming free than going home. */
    private static final float UNLATCH_PITCH = 1.25F;
    /** How many puffs of steam a client sees each tick the latches let go or the plate seals, either side. */
    private static final int STEAM_PUFFS = 2;
    /** Either edge of the well, where the steam blows out. */
    private static final int[] EDGES = {-1, 1};
    /** How far the steam blows out sideways from the well's edges, in blocks a tick. */
    private static final double STEAM_SPEED = 0.08;
    /** What stands in the ramp's way, in the world or aboard: anything solid but its own deck and plate. */
    private static final Predicate<BlockState> IN_THE_WAY = state -> !state.isAir() && !state.canBeReplaced()
        && !(state.getBlock() instanceof RampDeckBlock) && !(state.getBlock() instanceof RampPlateBlock);

    /** The block entity that keeps a ramp, where it is: its panel. */
    interface Keeper {
        BlockPos getBlockPos();

        @Nullable
        Level getLevel();

        /** Tells the clients where the ramp is going, which draw it. */
        void sync();

        /** Marks what is saved of it as changed, without telling the clients, which count the ticks themselves. */
        void setChanged();
    }

    /**
     * Where the ramp lies, from its panel: the block at its head in the well's middle column, in her deck's row, whose
     * top's inner edge is the hinge; the way out of her, down the ramp; how many blocks wide it is; and how far it
     * falls, in blocks, from her deck's top to the ground under her feet, over twice as far out.
     */
    public record Frame(BlockPos head, Direction out, int width, int rise) {
        /** How far out the ramp reaches, down on the ground, in blocks: twice as far as it falls. */
        public int run() {
            return 2 * rise;
        }

        /** How long the plate is, from its hinge to its foot, in blocks. */
        public double length() {
            return Math.hypot(run(), rise);
        }

        /** How steeply the plate lies once down, in degrees: its foot just clear of the ground (see {@link #FOOT_CLEAR}). */
        public double slope() {
            return Math.toDegrees(Math.atan2(rise - FOOT_CLEAR, run()));
        }

        /** The way across the ramp, to the right seen from outside, looking at her. */
        public Direction right() {
            return out.getCounterClockWise();
        }
    }

    private final Keeper panel;
    private final Frame frame;
    /** The hatch, shut: where each block of it is from the panel, and what it is. */
    private final Hatch hatch;
    /** Down or coming down; and how far through coming down, in ticks, now and a tick ago. */
    private boolean deploying;
    private int ticks;
    private int lastTicks;
    /** Someone stands on it, or on the stowed plate, and it waits for them to step off; the chime has sounded. */
    private boolean held;
    private int cooldown;
    /** How long it has waited so, in ticks, for its chime to sound again; not saved. */
    private int waitedFor;
    /** Whether its deck is laid, and the hatch's blocks taken away. */
    private boolean laid;
    private boolean hatchOpen;

    BoardingRamp(Keeper panel, Frame frame, Hatch hatch) {
        this.panel = panel;
        this.frame = frame;
        this.hatch = hatch;
    }

    /** Where it lies, from its panel. */
    public Frame frame() {
        return frame;
    }

    /** The floor its plate is part of while it is up, the top of its hatch, which its plate shows on top as it moves. */
    public BlockState floor() {
        return hatch.top();
    }

    /** Whether it is down, or on its way down. */
    public boolean down() {
        return deploying;
    }

    /** Whether it waits for someone to step off it, or off the stowed plate. */
    boolean held() {
        return held;
    }

    /** Whether it is all the way down, its deck laid. */
    boolean landed() {
        return laid;
    }

    /** Whether it is away and staying that way: up, the hatch shut. */
    boolean stowed() {
        return !deploying && ticks == 0 && !hatchOpen;
    }

    /** How far through coming down it is between the last tick and this one, in ticks; 0 while stowed. */
    public float ticks(float partialTick) {
        return Mth.lerp(partialTick, lastTicks, ticks);
    }

    /**
     * How far down the plate has swung so far through coming down, in degrees: still while the rams take its weight,
     * then eased down to its slope, where it lies once down.
     */
    public double angle(double at) {
        double share = Math.clamp((at - PRESSURE) / (double) (SWUNG - PRESSURE), 0, 1);
        return frame.slope() * share * share * (3 - 2 * share);
    }

    /** Whether the hatch's blocks are away, so the plate is drawn in their place. */
    public boolean hatchOpen() {
        return hatchOpen;
    }

    /** Where a block of its deck goes: so far out from its head, half as far down, in one of its columns. */
    private BlockPos deck(int out, int column) {
        return panel.getBlockPos().offset(frame.head()).relative(frame.out(), out).below(out / 2).relative(frame.right(), column);
    }

    /** Its columns, from its left seen from outside to its right: as many either side of the middle one. */
    private int firstColumn() {
        return -(frame.width() / 2);
    }

    /** How many blocks its deck has: a row across it for each block it reaches out. */
    private int cells() {
        return frame.run() * frame.width();
    }

    /** A block of its deck by its number, row by row out from its head and across each from its left. */
    private BlockPos cell(int cell) {
        return deck(cell / frame.width(), firstColumn() + cell % frame.width());
    }

    /** Whether a block is part of its deck, laid, or of the blocks under its plate as it swings now. */
    boolean holds(Level level, BlockPos pos) {
        if (level != panel.getLevel()) {
            return false;
        }
        if (!laid) {
            return hatchOpen && plateAt(angle(ticks)).containsKey(pos);
        }
        for (int cell = 0; cell < cells(); cell++) {
            if (cell(cell).equals(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets it off down or up, unless it is already going that way. Gives whether it is going that way now: not down
     * where something stands in its way.
     */
    boolean go(ServerLevel level, boolean toDown) {
        if (toDown && !deploying) {
            return deploy(level);
        }
        if (!toDown && deploying) {
            fold(level);
        }
        return true;
    }

    /**
     * Sets it coming down, unless something stands in its way, which a low note says; with someone on the stowed
     * plate, it waits for them to step off. Gives whether it set off or waits.
     */
    private boolean deploy(ServerLevel level) {
        boolean shut = ticks == 0 && !hatchOpen;
        if (shut && !clear(level)) {
            level.playSound(null, panel.getBlockPos(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, BoardingAirstairs.REFUSED_VOLUME,
                BoardingAirstairs.REFUSED_PITCH);
            return false;
        }
        deploying = true;
        held = false;
        if (shut && AirstairControllerBlockEntity.anyoneIn(level, panel.getBlockPos(), plateSpots())) {
            waitFor(level);
        } else if (shut) {
            unlatch(level);
        } else if (swinging()) {
            sound(level, SkylinersRegistry.RAMP_HYDRAULICS.get(), HYDRAULICS_VOLUME, 1.0F);
        }
        return true;
    }

    /** The latches let go: the hatch's blocks go, in a burst of steam, her computer saying so. */
    private void unlatch(ServerLevel level) {
        openHatch(level);
        setPlate(level, plateAt(angle(ticks)));
        sound(level, SkylinersRegistry.AIRSTAIR_LATCH.get(), LATCH_VOLUME, UNLATCH_PITCH);
        sound(level, SkylinersRegistry.RAMP_STEAM.get(), STEAM_VOLUME, 1.0F);
        sayLowering(level);
        panel.sync();
    }

    /**
     * Her computer says the ramp is coming down, from the panel, holding her cockpit's floor while it does, so that
     * none of her announcements, such as the one saying she is down, is made over it.
     */
    private void sayLowering(ServerLevel level) {
        level.playSound(null, panel.getBlockPos(), SkylinersRegistry.FREIGHTER_COMPUTER_RAMP_LOWERING.get(), SoundSource.BLOCKS,
            VOICE_VOLUME, 1.0F);
        AutopilotBlockEntity freighter = AutopilotBlockEntity.framedBy(level, panel.getBlockPos(), Craft.FREIGHTER);
        if (freighter != null) {
            freighter.holdFloor(VoiceLength.ticks(SkylinersRegistry.FREIGHTER_COMPUTER_RAMP_LOWERING, VOICE_LONGEST));
        }
    }

    /** Sets it going away: at once, or once nobody stands on it. */
    private void fold(ServerLevel level) {
        deploying = false;
        held = false;
        if (laid && AirstairControllerBlockEntity.anyoneIn(level, panel.getBlockPos(), deckSpots())) {
            waitFor(level);
            return;
        }
        takeUp(level);
        if (swinging()) {
            sound(level, SkylinersRegistry.RAMP_HYDRAULICS.get(), HYDRAULICS_VOLUME, 1.0F);
        }
    }

    /**
     * Whether the plate is part way through its swing, turned round there: the hydraulics are heard again from there,
     * as they are from the swing's start (see {@link #arrive} and {@link #leave}).
     */
    private boolean swinging() {
        return ticks > PRESSURE && ticks < SWUNG;
    }

    /** Someone stands where it would move: it waits, with a chime, looking again now and then. */
    private void waitFor(ServerLevel level) {
        held = true;
        cooldown = CLEAR_CHECK_TICKS;
        waitedFor = 0;
        chime(level);
    }

    /** The chime that says it waits for someone to step off or out of its way. */
    private void chime(ServerLevel level) {
        level.playSound(null, panel.getBlockPos(), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, BoardingAirstairs.WAITING_VOLUME,
            BoardingAirstairs.WAITING_PITCH);
    }

    /** Once a tick, on the server: a tick further on its way, waiting while someone stands where it would move. */
    void tick(ServerLevel level) {
        lastTicks = ticks;
        if (held) {
            waitForClear(level);
            return;
        }
        if (deploying && ticks < OUT) {
            if (anyoneInTheSwing(level, ticks, ticks + 1)) {
                hold(level);
                return;
            }
            ticks++;
            arrive(level, ticks);
            panel.setChanged();
        } else if (!deploying && (ticks > 0 || hatchOpen)) {
            if (inTheWayGoingUp(level)) {
                hold(level);
                return;
            }
            ticks = Math.max(0, ticks - 1);
            leave(level, ticks);
            panel.setChanged();
        }
    }

    /** Someone stands where it would move on its way: it stops where it is, with a chime, telling the clients. */
    private void hold(ServerLevel level) {
        waitFor(level);
        panel.sync();
    }

    /** Whether anyone stands where it would move next going up: in its hatch as it would shut, or in its swing. */
    private boolean inTheWayGoingUp(ServerLevel level) {
        return ticks <= 1 ? AirstairControllerBlockEntity.anyoneIn(level, panel.getBlockPos(), hatchSpots()) : anyoneInTheSwing(level, ticks, ticks - 1);
    }

    /**
     * Coming down, what a tick brings: the blocks under the plate where it has swung to, the hydraulics as it swings,
     * the thud at its foot, then its deck in their place.
     */
    private void arrive(ServerLevel level, int at) {
        setPlate(level, at == OUT ? Map.of() : plateAt(angle(at)));
        if (at == PRESSURE) {
            sound(level, SkylinersRegistry.RAMP_HYDRAULICS.get(), HYDRAULICS_VOLUME, 1.0F);
        } else if (at == SWUNG) {
            sound(level, SkylinersRegistry.RAMP_THUD.get(), THUD_VOLUME, 1.0F);
            sound(level, SkylinersRegistry.RAMP_STEAM.get(), SECOND_STEAM_VOLUME, 1.0F);
        } else if (at == OUT) {
            lay(level);
            panel.sync();
        }
    }

    /**
     * Going away, what a tick brings: the blocks under the plate where it has swung back to, the hydraulics as it
     * swings, the seal's steam, the hatch shut in their place.
     */
    private void leave(ServerLevel level, int at) {
        setPlate(level, at == 0 ? Map.of() : plateAt(angle(at)));
        if (at == SWUNG) {
            sound(level, SkylinersRegistry.RAMP_HYDRAULICS.get(), HYDRAULICS_VOLUME, 1.0F);
        } else if (at == PRESSURE) {
            sound(level, SkylinersRegistry.RAMP_STEAM.get(), STEAM_VOLUME, 1.0F);
        } else if (at == 0) {
            closeHatch(level);
            sound(level, SkylinersRegistry.AIRSTAIR_LATCH.get(), LATCH_VOLUME, 1.0F);
            panel.sync();
        }
    }

    /**
     * Held up by someone where it would move: now and then a look whether they have stepped off or out of its way, and
     * then on its way again, the hydraulics heard again where the plate is part way through its swing.
     */
    private void waitForClear(ServerLevel level) {
        if (++waitedFor % CHIME_AGAIN_TICKS == 0) {
            chime(level);
        }
        if (--cooldown > 0) {
            return;
        }
        cooldown = CLEAR_CHECK_TICKS;
        boolean opening = deploying && ticks == 0 && !hatchOpen;
        if (stillInTheWay(level, opening)) {
            return;
        }
        held = false;
        if (opening) {
            unlatch(level);
        } else if (!deploying && laid) {
            takeUp(level);
        }
        if (swinging()) {
            sound(level, SkylinersRegistry.RAMP_HYDRAULICS.get(), HYDRAULICS_VOLUME, 1.0F);
        }
        panel.sync();
    }

    /**
     * Whether whoever held it still stands where it would move: on the stowed plate as it would open, on its deck as
     * it would fold, in its hatch as it would shut, or where its plate would swing through next.
     */
    private boolean stillInTheWay(ServerLevel level, boolean opening) {
        if (opening) {
            return AirstairControllerBlockEntity.anyoneIn(level, panel.getBlockPos(), plateSpots());
        }
        if (deploying) {
            return anyoneInTheSwing(level, ticks, ticks + 1);
        }
        return laid ? AirstairControllerBlockEntity.anyoneIn(level, panel.getBlockPos(), deckSpots()) : inTheWayGoingUp(level);
    }

    /** On a client, each tick: the same clock the server runs, and steam as the latches let go or the plate seals. */
    void clientTick() {
        lastTicks = ticks;
        if (held) {
            return;
        }
        ticks = Math.clamp(ticks + (deploying ? 1 : -1), 0, OUT);
        boolean steaming = hatchOpen && ticks < PRESSURE && ticks != lastTicks;
        if (steaming || deploying && ticks > SWUNG && ticks < OUT) {
            steam();
        }
    }

    /** A puff of steam blowing out either side of the well at its head, where the rams' seals are. */
    private void steam() {
        Level level = panel.getLevel();
        if (level == null) {
            return;
        }
        Direction right = frame.right();
        Vec3 head = Vec3.atCenterOf(panel.getBlockPos().offset(frame.head())).add(0, 0.5, 0);
        double side = frame.width() / 2.0;
        for (int puff = 0; puff < STEAM_PUFFS; puff++) {
            for (int edge : EDGES) {
                double along = level.random.nextDouble() * 2;
                Vec3 at = head.add(right.getStepX() * side * edge + frame.out().getStepX() * along, 0,
                    right.getStepZ() * side * edge + frame.out().getStepZ() * along);
                Vec3 world = SableFlight.toWorld(level, at);
                level.addParticle(ParticleTypes.CLOUD, world.x, world.y, world.z, right.getStepX() * STEAM_SPEED * edge, STEAM_SPEED / 2,
                    right.getStepZ() * STEAM_SPEED * edge);
            }
        }
    }

    /**
     * Whether nothing stands in its way: where its deck goes, nor in the room over it, but for the hatch's own blocks,
     * which go as it opens.
     */
    private boolean clear(ServerLevel level) {
        for (int cell = 0; cell < cells(); cell++) {
            if (blocked(level, cell(cell)) || blocked(level, cell(cell).above())) {
                return false;
            }
        }
        return true;
    }

    private boolean blocked(ServerLevel level, BlockPos cell) {
        Vec3 world = SableFlight.toWorld(level, Vec3.atCenterOf(cell));
        return !hatch.covers(panel.getBlockPos(), cell) && SableFlight.find(level, world, IN_THE_WAY) != null;
    }

    /** The room over its deck, laid, where someone standing holds it down. */
    private List<AABB> deckSpots() {
        List<AABB> spots = new ArrayList<>();
        int lastColumn = firstColumn() + frame.width() - 1;
        for (int out = 0; out < frame.run(); out++) {
            spots.add(AABB.encapsulatingFullBlocks(deck(out, firstColumn()), deck(out, lastColumn).above(2)));
        }
        return spots;
    }

    /** The room over the stowed plate, the well's floor, where someone standing holds it up. */
    private List<AABB> plateSpots() {
        BlockPos left = deck(0, firstColumn()).above();
        BlockPos right = deck(0, firstColumn() + frame.width() - 1).relative(frame.out(), (int) Math.ceil(frame.length()) - 1).above(2);
        return List.of(AABB.encapsulatingFullBlocks(left, right));
    }

    /** Lays its deck, each block where nothing else stands now its plate's blocks are away. */
    private void lay(ServerLevel level) {
        BlockState block = SkylinersRegistry.RAMP_DECK.get().defaultBlockState();
        Direction up = frame.out().getOpposite();
        for (int cell = 0; cell < cells(); cell++) {
            RampDeckBlock.Half half = cell / frame.width() % 2 == 0 ? RampDeckBlock.Half.UPPER : RampDeckBlock.Half.LOWER;
            if (level.getBlockState(cell(cell)).canBeReplaced()) {
                level.setBlock(cell(cell), RampDeckBlock.deck(block, up, half), Block.UPDATE_ALL);
            }
        }
        laid = true;
    }

    /**
     * Takes its deck up, and any of it she left standing in the world as she was assembled with it down, the blocks
     * under its plate set in its place as it starts back up.
     */
    private void takeUp(ServerLevel level) {
        for (int cell = 0; cell < cells(); cell++) {
            BlockPos at = cell(cell);
            takeUpAt(level, at, RampDeckBlock.class);
            BlockPos world = BlockPos.containing(SableFlight.toWorld(level, Vec3.atCenterOf(at)));
            if (!world.equals(at) && level.isLoaded(world)) {
                takeUpAt(level, world, RampDeckBlock.class);
            }
        }
        laid = false;
        if (hatchOpen) {
            setPlate(level, plateAt(angle(ticks)));
        }
    }

    /** Takes up a block of its deck or of its plate, if it is still one. */
    private static void takeUpAt(ServerLevel level, BlockPos at, Class<? extends Block> kind) {
        if (kind.isInstance(level.getBlockState(at).getBlock())) {
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /**
     * The blocks under its plate swung so far down, each where it goes and what it is: in each block's run out from its
     * hinge as far as the plate reaches over, across its width, the block its top passes through at the run's edge
     * toward the hinge and, where its top dips out of that block across the run, the block under too.
     */
    private Map<BlockPos, BlockState> plateAt(double angle) {
        Map<BlockPos, BlockState> plate = new HashMap<>();
        double fall = Math.tan(Math.toRadians(angle));
        int drop = (int) Math.round(fall * RampDeckBlock.FULL);
        long runs = Math.round(frame.length() * Math.cos(Math.toRadians(angle)));
        BlockState base = SkylinersRegistry.RAMP_PLATE.get().defaultBlockState();
        Direction up = frame.out().getOpposite();
        for (int out = 0; out < runs; out++) {
            double near = -out * fall * RampDeckBlock.FULL;
            int row = (int) Math.ceil(near / RampDeckBlock.FULL);
            int top = (int) Math.round(near - RampDeckBlock.FULL * (row - 1));
            if (top <= 0) {
                row--;
                top += RampDeckBlock.FULL;
            }
            plateRun(plate, out, row, RampPlateBlock.plate(base, up, top, drop));
            if (RampDeckBlock.dipsOut(top, drop)) {
                plateRun(plate, out, row - 1, RampPlateBlock.plate(base, up, top + RampDeckBlock.FULL, drop));
            }
        }
        return plate;
    }

    /** A block of the plate so many rows over its head's, in a run out from its hinge, across its width; none for none. */
    private void plateRun(Map<BlockPos, BlockState> plate, int out, int row, @Nullable BlockState block) {
        if (block == null) {
            return;
        }
        BlockPos middle = panel.getBlockPos().offset(frame.head()).relative(frame.out(), out).above(row);
        for (int column = firstColumn(); column < firstColumn() + frame.width(); column++) {
            plate.put(middle.relative(frame.right(), column), block);
        }
    }

    /**
     * Sets the blocks under its plate where it lies now, in open room only, and takes away any of them lying elsewhere
     * in the room it swings through, as they did a tick ago; with none given, takes all of them away, and any left
     * standing in the world as she was assembled with it swinging.
     */
    private void setPlate(ServerLevel level, Map<BlockPos, BlockState> plate) {
        BlockPos head = panel.getBlockPos().offset(frame.head());
        int runs = (int) Math.ceil(frame.length());
        for (int out = 0; out <= runs; out++) {
            for (int row = -frame.rise() - 1; row <= 0; row++) {
                setPlateRow(level, plate, head.relative(frame.out(), out).above(row));
            }
        }
        if (!plate.isEmpty()) {
            return;
        }
        for (int cell = 0; cell < cells(); cell++) {
            BlockPos world = BlockPos.containing(SableFlight.toWorld(level, Vec3.atCenterOf(cell(cell))));
            if (!world.equals(cell(cell)) && level.isLoaded(world)) {
                takeUpAt(level, world, RampPlateBlock.class);
            }
        }
    }

    /** One row across the room its plate swings through, each block of it set as the plate has it now. */
    private void setPlateRow(ServerLevel level, Map<BlockPos, BlockState> plate, BlockPos middle) {
        for (int column = firstColumn(); column < firstColumn() + frame.width(); column++) {
            BlockPos at = middle.relative(frame.right(), column);
            BlockState there = level.getBlockState(at);
            BlockState wanted = plate.get(at);
            boolean ours = there.getBlock() instanceof RampPlateBlock;
            if (wanted != null && there != wanted && (ours || there.canBeReplaced())) {
                level.setBlock(at, wanted, PLATE_UPDATE);
            } else if (wanted == null && ours) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), PLATE_UPDATE);
            }
        }
    }

    /**
     * Whether anyone stands where its plate would swing through between two ticks of its way, seen in her frame
     * wherever she is: over or under its plate, across its width and out as far as it reaches.
     */
    private boolean anyoneInTheSwing(ServerLevel level, int from, int to) {
        double before = Math.tan(Math.toRadians(angle(from)));
        double after = Math.tan(Math.toRadians(angle(to)));
        if (before == after) {
            return false;
        }
        double reach = frame.length() * Math.cos(Math.atan(Math.min(before, after)));
        Vec3 out = Vec3.atLowerCornerOf(frame.out().getNormal());
        Vec3 hinge = Vec3.atBottomCenterOf(panel.getBlockPos().offset(frame.head()).above()).subtract(out.scale(0.5));
        Vec3 middle = SableFlight.toWorld(level, hinge.add(out.scale(reach / 2)).add(0, -frame.rise() / 2.0, 0));
        AABB near = new AABB(middle, middle).inflate(reach / 2 + frame.rise() + frame.width());
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, near, LivingEntity::isAlive)) {
            Vec3 centre = SableFlight.toPlot(level, panel.getBlockPos(), entity.getBoundingBox().getCenter(), false).subtract(hinge);
            if (inTheSwing(entity, centre, reach, before, after)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether someone, their middle so far from its hinge, stands where its plate would swing through from where it
     * falls so much a block to where it falls so much: coming down, under it, as close under its top as its blocks
     * reach; going up, over its well, under her deck's top and over the plate's underside, on it as much as under where
     * it would rise to. Over the plate across its width and out as far as it reaches, checked at the side of them
     * nearer its hinge for the top over them and the further side for the underside.
     */
    private boolean inTheSwing(LivingEntity entity, Vec3 centre, double reach, double before, double after) {
        double along = centre.dot(Vec3.atLowerCornerOf(frame.out().getNormal()));
        double across = centre.dot(Vec3.atLowerCornerOf(frame.right().getNormal()));
        double half = entity.getBbWidth() / 2;
        if (Math.abs(across) >= frame.width() / 2.0 + half || along <= -half || along - half >= reach) {
            return false;
        }
        double feet = centre.y - entity.getBbHeight() / 2;
        double head = feet + entity.getBbHeight();
        double nearSide = Math.max(0, along - half);
        double farSide = Math.min(reach, along + half);
        if (after > before) {
            return feet < -nearSide * before - STANDING_ON && head > -farSide * after - SOLID_UNDER;
        }
        return along > 0 && feet < -STANDING_ON / 2 && feet < -nearSide * after + STANDING_ON && head > -farSide * before - SOLID_UNDER;
    }

    /** The room each block of its hatch fills once shut, where someone standing holds it open. */
    private List<AABB> hatchSpots() {
        return hatch.cells(panel.getBlockPos()).stream().map(cell -> new AABB(cell).deflate(IN_THE_HATCH)).toList();
    }

    /** Takes the hatch's blocks away, as it opens. */
    private void openHatch(ServerLevel level) {
        hatch.open(level, panel.getBlockPos());
        hatchOpen = true;
    }

    /** Puts the hatch's blocks back as they were, where nothing else has taken their place. */
    private void closeHatch(ServerLevel level) {
        hatch.close(level, panel.getBlockPos());
        hatchOpen = false;
    }

    /**
     * Whether the ground lies under its foot: something solid in the world under where its last block of deck would
     * go, or in it, for it to come down onto.
     */
    boolean groundUnderFoot(ServerLevel level) {
        BlockPos foot = deck(frame.run() - 1, 0);
        for (BlockPos cell : new BlockPos[]{foot, foot.below()}) {
            BlockPos world = BlockPos.containing(SableFlight.toWorld(level, Vec3.atCenterOf(cell)));
            if (IN_THE_WAY.test(level.getBlockState(world))) {
                return true;
            }
        }
        return false;
    }

    /** A sound from the head of the ramp, where its hinge and rams are. */
    private void sound(ServerLevel level, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, panel.getBlockPos().offset(frame.head()), sound, SoundSource.BLOCKS, volume, pitch);
    }

    void save(CompoundTag tag) {
        tag.put("Head", NbtUtils.writeBlockPos(frame.head()));
        tag.putString("Out", frame.out().getSerializedName());
        tag.putInt("Width", frame.width());
        tag.putInt("Rise", frame.rise());
        hatch.save(tag);
        tag.putBoolean("Deploying", deploying);
        tag.putInt("Ticks", ticks);
        tag.putBoolean("Held", held);
        tag.putBoolean("Laid", laid);
        tag.putBoolean("HatchOpen", hatchOpen);
    }

    /** The ramp as saved, turned by the quarter turn its panel has turned since (see {@link Turning}). */
    static BoardingRamp load(Keeper panel, @Nullable BoardingRamp before, CompoundTag tag, Rotation turn, HolderLookup.Provider registries) {
        Direction out = Direction.byName(tag.getString("Out"));
        Direction saved = out == null || out.getAxis().isVertical() ? Direction.EAST : out;
        Frame frame = new Frame(Turning.turn(NbtUtils.readBlockPos(tag, "Head").orElse(BlockPos.ZERO), turn), turn.rotate(saved),
            Math.max(1, tag.getInt("Width")), Math.max(1, tag.getInt("Rise")));
        BoardingRamp ramp = new BoardingRamp(panel, frame, Hatch.load(tag, turn, registries));
        ramp.deploying = tag.getBoolean("Deploying");
        int told = Math.clamp(tag.getInt("Ticks"), 0, OUT);
        ramp.ticks = before == null ? told : ToldTicks.count(panel.getLevel(), before.ticks, told, ramp.deploying);
        ramp.lastTicks = before != null && ramp.ticks == before.ticks ? before.lastTicks : ramp.ticks;
        ramp.held = tag.getBoolean("Held");
        ramp.laid = tag.getBoolean("Laid");
        ramp.hatchOpen = tag.getBoolean("HatchOpen");
        return ramp;
    }
}
