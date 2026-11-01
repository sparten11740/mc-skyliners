package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Course;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A zeppelin's speed boost (see {@link BoostButtonBlock}): pressed while her autopilot flies her level, the boosters
 * round her tail fire for {@link #BOOST_TICKS}, their plumes streaming behind her, while her pilot keeps her engines as
 * they were, holds her height by her burners as ever and steers her along the heading she had as it began (see the
 * {@code SpeedBoost} course). They push along her axis, through the hull's middle, so she gathers speed without her
 * nose rising, as it does with her engine cars, which hang below it, run full ahead. A roar goes up from the car and
 * the boosters. Once it is over she slows back to her autopilot's speed, and the button rests a while before it boosts
 * again. The boosters' relays it fires are kept as offsets from it, set as she is built; whether they are firing is
 * saved, so a boost cut short by the world closing is put out once it is open again.
 */
public class BoostButtonBlockEntity extends BlockEntity {
    private static final Loaded<BoostButtonBlockEntity> LOADED = new Loaded<>(BoostButtonBlockEntity.class);
    /** How long a boost lasts, in ticks: ten seconds. */
    private static final int BOOST_TICKS = 200;
    /** How long the button rests after a boost before it boosts again, in ticks. */
    private static final int REST_TICKS = 200;
    /** No boost begun. */
    private static final long NONE = Long.MIN_VALUE;
    private static final int FULL_SIGNAL = 15;
    private static final float ROAR_VOLUME = 3;
    private static final float LOW_NOTE_VOLUME = 0.6F;

    /** Her boosters' relays, as offsets from the button. */
    private final List<BlockPos> boosters = new ArrayList<>();
    /** When the boost under way ends, in game time, and the heading it holds her on, none until her pilot first asks. */
    private long until = NONE;
    private double heading = Double.NaN;
    /** Whether the boosters are firing. */
    private boolean firing;

    public BoostButtonBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.BOOST_BUTTON_ENTITY.get(), pos, state);
    }

    /** The boost buttons loaded in a level, on the server. */
    public static List<BoostButtonBlockEntity> loaded(Level level) {
        return LOADED.in(level);
    }

    /** Hands the button its zeppelin's boosters' relays, as offsets from it, as she is built. */
    public void rig(List<BlockPos> offsets) {
        boosters.clear();
        boosters.addAll(offsets);
        setChanged();
    }

    /**
     * Pressed, on the server: the boost begins if her autopilot flies her level and the button has rested since the
     * last; a low note otherwise.
     */
    void press(ServerLevel level) {
        long now = level.getGameTime();
        AutopilotBlockEntity autopilot = AutopilotBlockEntity.zeppelinFraming(level, worldPosition);
        boolean resting = until != NONE && now < until + REST_TICKS;
        if (autopilot == null || boosters.isEmpty() || !flyingLevel(autopilot) || resting) {
            sound(level, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), LOW_NOTE_VOLUME);
            return;
        }
        until = now + BOOST_TICKS;
        heading = Double.NaN;
        fire(level, true);
        sound(level, worldPosition, SkylinersRegistry.SPEED_BOOST.get(), ROAR_VOLUME);
        sound(level, worldPosition.offset(boosters.get(0)), SkylinersRegistry.SPEED_BOOST.get(), ROAR_VOLUME);
    }

    /** Once a tick, on the server: the boosters put out once the boost is over. */
    void serverTick(ServerLevel level) {
        if (firing && (until == NONE || level.getGameTime() >= until)) {
            fire(level, false);
        }
    }

    /**
     * How her pilot steers her this tick while she boosts, from the heading she has: on along the one she had as it
     * began, her engines as they were; null once it is over, or she no longer flies level, which ends it.
     */
    @Nullable
    public Course.Steer steer(ServerLevel level, AutopilotBlockEntity autopilot, double current) {
        if (!firing) {
            return null;
        }
        if (!flyingLevel(autopilot)) {
            until = level.getGameTime();
            fire(level, false);
            return null;
        }
        if (Double.isNaN(heading)) {
            heading = current;
        }
        return Course.Steer.holdingThrust(heading);
    }

    /** The boosters lit or put out, put out with their roar falling away and a pop, heard at the button and at them. */
    private void fire(ServerLevel level, boolean on) {
        boolean were = firing;
        firing = on;
        Relays.drive(level, worldPosition, boosters, on ? FULL_SIGNAL : 0);
        setChanged();
        if (were && !on && !boosters.isEmpty()) {
            sound(level, worldPosition, SkylinersRegistry.BOOSTER_CUTOUT.get(), ROAR_VOLUME);
            sound(level, worldPosition.offset(boosters.get(0)), SkylinersRegistry.BOOSTER_CUTOUT.get(), ROAR_VOLUME);
        }
    }

    /** Whether the autopilot flies her level, neither taking off, landing, nor switched off. */
    private static boolean flyingLevel(AutopilotBlockEntity autopilot) {
        AutopilotBlockEntity.Phase phase = autopilot.phase();
        return (phase == AutopilotBlockEntity.Phase.CLIMB || phase == AutopilotBlockEntity.Phase.CRUISE || phase == AutopilotBlockEntity.Phase.DESCENT)
            && !autopilot.comingIn();
    }

    private static void sound(ServerLevel level, BlockPos at, SoundEvent sound, float volume) {
        level.playSound(null, at, sound, SoundSource.BLOCKS, volume, 1);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag saved = new ListTag();
        for (BlockPos offset : boosters) {
            CompoundTag one = new CompoundTag();
            one.put("At", NbtUtils.writeBlockPos(offset));
            saved.add(one);
        }
        tag.put("Boosters", saved);
        tag.putBoolean("Firing", firing);
        Turning.save(tag, getBlockState());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Rotation turn = Turning.since(tag, getBlockState());
        boosters.clear();
        for (Tag entry : tag.getList("Boosters", Tag.TAG_COMPOUND)) {
            NbtUtils.readBlockPos((CompoundTag) entry, "At").ifPresent(offset -> boosters.add(Turning.turn(offset, turn)));
        }
        firing = tag.getBoolean("Firing");
    }
}
