package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Boarding stairs as integral airstairs (see {@link AirstairFlight}), kept by a stairs button: a zeppelin's, the
 * airliner's flight two blocks wide hung in her belly, or a rocket's, three wide out of her front door. The sill is the
 * floor at the head of the stairs, a zeppelin's lower deck's or the rocket's entry story's at her door, and the flight
 * comes down along {@link #out} to the ground. A zeppelin's stowed flight lies folded level in the row under the deck's
 * floor, right over its hatch: the patch of her cover under it, the floor over it and whatever stands between, as the
 * airliner's hatch is its hull's skin; coming down, the hatch's blocks are taken away and the flight swings straight
 * down through the opening. A rocket's lies in a bay under her entry floor behind the mouth in her skin below the door
 * ({@link Bay}), as the airliner's does: its hatch is her skin over the mouth, and coming out the flight slides out of
 * the bay, rises to the sill and swings down. Either way it comes out with the clunk of its latch, its sections run out
 * to the ground and its handrails rise, to the hum of the motor; once down, the steps it is walked on are laid (see
 * {@link AirstairSteps}). Going away it waits, with a chime, until nobody is on it, runs back, and the hatch's blocks
 * come back last; out of a door, the door opens as it sets out and shuts once it is back off the sill (see
 * {@link Keeper#doorTo}). Its button keeps it and works it with the rest of her stairs (see
 * {@link BoardingStairsBlockEntity} and {@link RocketStairsBlockEntity}); the server runs it and tells every client
 * where it is going, each client running the same clock to draw it in between.
 */
final class BoardingAirstairs {
    private static final float MOTOR_VOLUME = 0.9F;
    private static final float LATCH_VOLUME = 1.0F;
    /** The latch clunks higher coming free than going home. */
    private static final float UNLATCH_PITCH = 1.25F;
    /** The low note of stairs that cannot come down, or a button that does nothing now: how loud, and its pitch. */
    static final float REFUSED_VOLUME = 1.0F;
    static final float REFUSED_PITCH = 0.6F;
    /** The feet's legs not measured yet. */
    private static final float UNMEASURED = -1;

    /** The block entity that keeps a flight, where it is: its button. */
    interface Keeper {
        BlockPos getBlockPos();

        @Nullable
        Level getLevel();

        /** Tells the clients where the flight is going, which draw it. */
        void sync();

        /** Marks what is saved of it as changed, without telling the clients, which count the ticks themselves. */
        void setChanged();

        /**
         * Opens the door the flight comes out of as it sets out, or shuts it once it is back off the sill; a flight
         * hung in a zeppelin's belly has none.
         */
        default void doorTo(ServerLevel level, boolean open) {
        }
    }

    /**
     * The bay a flight out of a door stows in, behind the mouth in the skin below the door: how far in from the door's
     * face the skin over the mouth lies, in blocks, where the hatch is drawn swinging, and how deep the mouth is, in
     * blocks in from the door's face, the folded flight lying behind it.
     */
    record Bay(float hatchInset, int mouth) {
    }

    private final Keeper button;
    /**
     * Where the stairs' frame stands, from their button: the block at their head whose bottom is the sill; the way out,
     * down the flight, and the way to the right seen from outside.
     */
    private final BlockPos head;
    private final Direction out;
    private final Direction right;
    /**
     * How many blocks the flight comes down from the sill to the ground she stands on, in how many sections, and how
     * many blocks wide it is.
     */
    private final int rise;
    private final int sections;
    private final int width;
    /** The hatch, shut: where each block of it is from the button, and what it is. */
    private final Hatch hatch;
    /** The bay it slides out of, out of a door; none hung in a zeppelin's belly. */
    @Nullable
    private final Bay bay;
    /** Out or coming out; and how far through coming out, in ticks, now and a tick ago. */
    private boolean deploying;
    private int ticks;
    private int lastTicks;
    /** How many blocks down the stairs reach this time. */
    private int length;
    /**
     * How far the flight's feet run down to the ground once it is out, in blocks, measured as it sets out; below 0 not
     * measured yet, as for stairs built down or saved before they had feet, measured on the next tick.
     */
    private float legs = UNMEASURED;
    /** The wait while folding is held up by someone on the stairs, until they step off. */
    private final StandClear standClear = new StandClear(StandClear.CHIME_ONCE);
    /** Whether the steps are laid, a foot below them, and the hatch's blocks taken away. */
    private boolean laid;
    private boolean foot;
    private boolean hatchOpen;
    /** Going away, the door is still to be shut, once the flight is back off the sill (see {@link Keeper#doorTo}). */
    private boolean doorToShut;

    BoardingAirstairs(Keeper button, BlockPos head, Direction out, Direction right, int rise, int sections, int width,
                      Hatch hatch, @Nullable Bay bay) {
        this.button = button;
        this.head = head;
        this.out = out;
        this.right = right;
        this.rise = rise;
        this.sections = sections;
        this.width = width;
        this.hatch = hatch;
        this.bay = bay;
    }

    /** Whether the stairs are down, or on their way down. */
    boolean down() {
        return deploying;
    }

    /** Whether folding is held up by someone on the stairs. */
    boolean held() {
        return standClear.held();
    }

    /** Whether the stairs are all away and staying that way: folded in, the hatch shut. */
    boolean stowed() {
        return !deploying && ticks == 0 && !hatchOpen;
    }

    Airstairs.Frame frame() {
        return new Airstairs.Frame(button.getBlockPos().offset(head), out, right);
    }

    /** Where a step goes: so far down from the sill and as far out, in one of the flight's columns. */
    private BlockPos step(int down, int column) {
        return button.getBlockPos().offset(head).relative(right, column).relative(out, down).below(down);
    }

    /** Where the stairs are between the last tick and this one, to draw them; null while stowed behind the shut hatch. */
    @Nullable
    AirstairFlight.Motion motion(float partialTick) {
        if (ticks == 0 && lastTicks == 0 && !hatchOpen) {
            return null;
        }
        float lerped = Mth.lerp(partialTick, lastTicks, ticks);
        if (bay == null) {
            return new AirstairFlight.Motion(lerped, length, rise, sections, width, 0, 1, hatchOpen, true, Math.max(0, legs));
        }
        return new AirstairFlight.Motion(lerped, length, rise, sections, width, bay.hatchInset(), bay.mouth(), hatchOpen, false, sillGap(),
            Math.max(0, legs));
    }

    /**
     * How far the floor's edge at the head of the stairs stands back from the door's face, in blocks, for the bridge
     * plate to span: her skin at her sill is cut to her curve, and where the cut leaves the outer part of the block
     * open, the top tread would stand off her floor. Read from the sill's shape in the door's middle column, as the
     * client has it; none where nothing stands there to bridge to.
     */
    private float sillGap() {
        Level level = button.getLevel();
        if (level == null) {
            return 0;
        }
        BlockPos sill = button.getBlockPos().offset(head).relative(right, width / 2).below();
        VoxelShape shape = level.getBlockState(sill).getShape(level, sill);
        if (shape.isEmpty()) {
            return 0;
        }
        Direction.Axis axis = out.getAxis();
        double reach = out.getAxisDirection() == Direction.AxisDirection.POSITIVE ? shape.max(axis) : 1 - shape.min(axis);
        return (float) (1 - reach);
    }

    /** Whether a block is one of the steps laid under the flight. */
    boolean holds(Level level, BlockPos pos) {
        return laid && level == button.getLevel() && AirstairSteps.holds(this::step, width, laidDown(), pos);
    }

    private int laidDown() {
        return length + (foot ? 1 : 0);
    }

    /** Sets the stairs off down or up, unless they are already going that way. */
    void go(ServerLevel level, boolean toDown) {
        if (toDown && !deploying) {
            deploy(level);
        } else if (!toDown && deploying) {
            fold(level);
        }
    }

    /** Sets the stairs coming down, unless something stands in the way right under the hatch. */
    private void deploy(ServerLevel level) {
        if (ticks == 0) {
            int reach = AirstairSteps.reach(level, this::step, width, rise, cell -> hatch.covers(button.getBlockPos(), cell));
            if (reach < AirstairFlight.shortest(rise, sections)) {
                level.playSound(null, button.getBlockPos(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, REFUSED_VOLUME, REFUSED_PITCH);
                return;
            }
            length = reach;
            legs = AirstairSteps.legs(level, this::step, width, length);
            openHatch(level);
            sound(level, SkylinersRegistry.AIRSTAIR_LATCH.get(), LATCH_VOLUME, UNLATCH_PITCH);
        }
        deploying = true;
        standClear.release();
        doorToShut = false;
        button.doorTo(level, true);
        if (ticks < AirstairFlight.OUT) {
            sound(level, SkylinersRegistry.AIRSTAIR_MOTOR.get(), MOTOR_VOLUME, 1.0F);
        }
    }

    /** Sets the stairs going away: at once, or once nobody is on them. */
    private void fold(ServerLevel level) {
        deploying = false;
        doorToShut = true;
        if (laid && AirstairControllerBlockEntity.anyoneIn(level, button.getBlockPos(), AirstairSteps.spots(this::step, width, length))) {
            standClear.hold(level, button.getBlockPos());
            return;
        }
        takeUp(level);
        sound(level, SkylinersRegistry.AIRSTAIR_MOTOR.get(), MOTOR_VOLUME, 1.0F);
    }

    /** Once a tick, on the server: the stairs a tick further on their way, waiting while someone is on them. */
    void tick(ServerLevel level) {
        lastTicks = ticks;
        if (deploying && legs < 0) {
            legs = AirstairSteps.legs(level, this::step, width, length);
            button.sync();
        }
        if (standClear.held()) {
            waitForClear(level);
            return;
        }
        if (deploying && ticks < AirstairFlight.OUT) {
            ticks++;
            if (stepEnds(ticks)) {
                sound(level, SkylinersRegistry.AIRSTAIR_MOTOR.get(), MOTOR_VOLUME, 1.0F);
            }
            if (ticks == AirstairFlight.OUT) {
                foot = AirstairSteps.lay(level, this::step, width, length, out.getOpposite());
                laid = true;
                sound(level, SkylinersRegistry.AIRSTAIR_LATCH.get(), LATCH_VOLUME, 1.0F);
                button.sync();
            }
            button.setChanged();
        } else if (!deploying && (ticks > 0 || hatchOpen)) {
            ticks = Math.max(0, ticks - 1);
            if (doorToShut && ticks <= AirstairFlight.SLIDE) {
                button.doorTo(level, false);
                doorToShut = false;
            }
            if (stepEnds(ticks)) {
                sound(level, SkylinersRegistry.AIRSTAIR_MOTOR.get(), MOTOR_VOLUME, 1.0F);
            }
            if (ticks == 0) {
                closeHatch(level);
                sound(level, SkylinersRegistry.AIRSTAIR_LATCH.get(), LATCH_VOLUME, 1.0F);
                button.sync();
            }
            button.setChanged();
        }
    }

    /**
     * Whether one of the steps of coming out (see {@link AirstairFlight}) ends at a tick, where the motor is heard
     * again: the slide out of a bay only out of a door.
     */
    private boolean stepEnds(int at) {
        return at == AirstairFlight.HATCH || at == AirstairFlight.SWING || at == AirstairFlight.EXTEND || bay != null && at == AirstairFlight.SLIDE;
    }

    /** Held up by someone on the stairs: now and then a look whether they have stepped off, and then away. */
    private void waitForClear(ServerLevel level) {
        if (!standClear.looksAgain(level, button.getBlockPos())) {
            return;
        }
        if (!AirstairControllerBlockEntity.anyoneIn(level, button.getBlockPos(), AirstairSteps.spots(this::step, width, length))) {
            standClear.release();
            takeUp(level);
            sound(level, SkylinersRegistry.AIRSTAIR_MOTOR.get(), MOTOR_VOLUME, 1.0F);
            button.sync();
        }
    }

    /** On a client, each tick: the same clock the server runs, toward where the stairs are going. */
    void clientTick() {
        lastTicks = ticks;
        if (!standClear.held()) {
            ticks = Math.clamp(ticks + (deploying ? 1 : -1), 0, AirstairFlight.OUT);
        }
    }

    /** Takes the steps up, and any of them she left standing in the world as she was assembled with them down. */
    private void takeUp(ServerLevel level) {
        AirstairSteps.takeUp(level, this::step, width, laidDown());
        AirstairSteps.takeUpLeftBehind(level, this::step, width, laidDown());
        laid = false;
        foot = false;
    }

    /** Takes the hatch's blocks away, as it opens. */
    private void openHatch(ServerLevel level) {
        hatch.open(level, button.getBlockPos());
        hatchOpen = true;
    }

    /** Puts the hatch's blocks back as they were, where nothing else has taken their place. */
    private void closeHatch(ServerLevel level) {
        hatch.close(level, button.getBlockPos());
        hatchOpen = false;
    }

    /**
     * Whether she stands on the ground: something solid in the world anywhere along the flight's way down, where a step
     * would go or just under it, from her sill to the foot of the whole flight, for it to come down onto. A rocket's
     * flight reaches lower than her fins stand, for ground falling away under her door, and on a flat world the foot of
     * the whole flight lies under the world's floor.
     */
    boolean onGround(ServerLevel level) {
        for (int down = 1; down <= rise; down++) {
            if (groundAt(level, down)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the world has something solid where a row of steps so far down would go, or just under it. */
    private boolean groundAt(ServerLevel level, int down) {
        for (int column = 0; column < width; column++) {
            BlockPos step = step(down, column);
            if (solidInWorld(level, step) || solidInWorld(level, step.below())) {
                return true;
            }
        }
        return false;
    }

    /** Whether the world has something solid where a block of hers is, under her rather than among her blocks. */
    private static boolean solidInWorld(ServerLevel level, BlockPos pos) {
        Vec3 at = SableFlight.toWorld(level, Vec3.atCenterOf(pos));
        return AirstairSteps.IN_THE_WAY.test(level.getBlockState(BlockPos.containing(at)));
    }

    /** A sound from the head of the stairs, where the hatch is. */
    private void sound(ServerLevel level, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, step(1, 0), sound, SoundSource.BLOCKS, volume, pitch);
    }

    void save(CompoundTag tag) {
        tag.put("Head", NbtUtils.writeBlockPos(head));
        tag.putString("Out", out.getSerializedName());
        tag.putString("Right", right.getSerializedName());
        tag.putInt("Rise", rise);
        tag.putInt("Sections", sections);
        tag.putInt("Width", width);
        hatch.save(tag);
        if (bay != null) {
            CompoundTag saved = new CompoundTag();
            saved.putFloat("HatchInset", bay.hatchInset());
            saved.putInt("Mouth", bay.mouth());
            tag.put("Bay", saved);
        }
        tag.putBoolean("Deploying", deploying);
        tag.putInt("Ticks", ticks);
        tag.putInt("Length", length);
        tag.putFloat("Legs", legs);
        standClear.save(tag);
        tag.putBoolean("Laid", laid);
        tag.putBoolean("Foot", foot);
        tag.putBoolean("HatchOpen", hatchOpen);
        tag.putBoolean("DoorToShut", doorToShut);
    }

    /**
     * The stairs as saved, turned by the quarter turn their button has turned since (see {@link Turning}). Saved
     * before stairs had a width or a bay, they are a zeppelin's: two wide, hung in her belly.
     */
    static BoardingAirstairs load(Keeper button, @Nullable BoardingAirstairs before, CompoundTag tag, Rotation turn, HolderLookup.Provider registries) {
        Direction out = Direction.byName(tag.getString("Out"));
        Direction right = Direction.byName(tag.getString("Right"));
        Hatch parts = Hatch.load(tag, turn, registries);
        int rise = Math.max(0, tag.getInt("Rise"));
        int width = tag.contains("Width") ? Math.max(1, tag.getInt("Width")) : AirstairFlight.WIDTH;
        CompoundTag saved = tag.getCompound("Bay");
        Bay bay = tag.contains("Bay") ? new Bay(saved.getFloat("HatchInset"), Math.max(1, saved.getInt("Mouth"))) : null;
        BoardingAirstairs stairs = new BoardingAirstairs(button, Turning.turn(NbtUtils.readBlockPos(tag, "Head").orElse(BlockPos.ZERO), turn),
            turn.rotate(out == null ? Direction.NORTH : out), turn.rotate(right == null ? Direction.WEST : right), rise,
            Math.clamp(tag.getInt("Sections"), 1, AirstairFlight.MOST_SECTIONS), width, parts, bay);
        stairs.deploying = tag.getBoolean("Deploying");
        int told = Math.clamp(tag.getInt("Ticks"), 0, AirstairFlight.OUT);
        stairs.ticks = before == null ? told : ToldTicks.count(button.getLevel(), before.ticks, told, stairs.deploying);
        stairs.lastTicks = before != null && stairs.ticks == before.ticks ? before.lastTicks : stairs.ticks;
        stairs.length = Math.clamp(tag.getInt("Length"), 0, rise);
        stairs.legs = tag.contains("Legs") ? Math.min(tag.getFloat("Legs"), AirstairSteps.LEGS_LONGEST) : UNMEASURED;
        stairs.standClear.load(tag);
        stairs.laid = tag.getBoolean("Laid");
        stairs.foot = tag.getBoolean("Foot");
        stairs.hatchOpen = tag.getBoolean("HatchOpen");
        stairs.doorToShut = tag.getBoolean("DoorToShut");
        return stairs;
    }
}
