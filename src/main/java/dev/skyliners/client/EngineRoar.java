package dev.skyliners.client;

import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A ship's engines heard from this client, what the rocket's roar (see {@link RocketRoar}) and the freighter's are
 * made of: one of her engine sounds looped, going toward how loud and how high it should be at so much a tick (see
 * {@link #keep}); one played once (see {@link #once}); how low over the ground her exhaust is, for the thunder close
 * by (see {@link #lowness}); and the ground shaking under this player near her as her engines fire low over it (see
 * {@link #shakeGround}). Outside, each is heard from where it is among her blocks, so that in flight Sable carries it
 * along; aboard her, it comes through her hull, round the listener wherever they stand in her.
 */
public final class EngineRoar extends AbstractTickableSoundInstance {
    /** How loud her engines are heard aboard her, through her hull, as a share of how loud they are outside. */
    static final float ABOARD_SHARE = 0.7F;
    /**
     * How fast a loop swells or fades otherwise, as the listener boards her or leaves her, or first hears her, a share
     * of full a tick; and how fast its pitch follows her engines, a share a tick.
     */
    static final float FADE = 0.1F;
    static final float PITCH_RATE = 0.01F;
    /** The pitch her roar falls to as it dies away when her engines stop. */
    static final float CUT_PITCH = 0.7F;
    /**
     * For how many ticks the ground shaking near her engines is kept up each tick: as long as a shake takes to die away
     * (CameraShake's TURBULENCE_FADE_TICKS), so it holds at full while renewed and fades once she has climbed away.
     */
    static final int SHAKE_KEPT = 20;
    /** What a ship's engines are heard doing before they are first heard: neither firing nor still. */
    static final float UNHEARD = -1;

    private final AutopilotBlockEntity ship;
    private float wanted;
    private float wantedPitch;
    private float step;
    private float pitchStep;

    private EngineRoar(AutopilotBlockEntity ship, SoundEvent sound, boolean aboard, float pitch, Vec3 at) {
        super(sound, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.ship = ship;
        looping = true;
        delay = 0;
        volume = 0;
        this.pitch = pitch;
        wantedPitch = pitch;
        relative = aboard;
        attenuation = aboard ? Attenuation.NONE : Attenuation.LINEAR;
        Vec3 from = aboard ? Vec3.ZERO : at;
        x = from.x;
        y = from.y;
        z = from.z;
    }

    /**
     * Each ship of a kind loaded on this client, with what was last heard of her, kept until her autopilot goes away
     * or this client leaves her level; nothing while no level is loaded.
     */
    static <T> void forEach(Map<AutopilotBlockEntity, T> heard, Craft craft, Supplier<T> fresh, BiConsumer<AutopilotBlockEntity, T> each) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            heard.clear();
            return;
        }
        heard.keySet().removeIf(ship -> ship.isRemoved() || ship.getLevel() != level);
        for (AutopilotBlockEntity autopilot : AutopilotBlockEntity.loaded(level)) {
            if (autopilot.craft() == craft && autopilot.built()) {
                each.accept(autopilot, heard.computeIfAbsent(autopilot, ship -> fresh.get()));
            }
        }
    }

    /**
     * One of her engine sounds looped, kept going toward how loud and how high it should be, at so much a tick:
     * outside, from where it is among her blocks; aboard, round the listener. Begun once it should be heard, let go
     * once it has died away.
     */
    @Nullable
    static EngineRoar keep(Minecraft minecraft, AutopilotBlockEntity ship, @Nullable EngineRoar roar, SoundEvent sound, Vec3 at, boolean aboard,
                           float volume, float pitch, float step, float pitchStep) {
        EngineRoar kept = roar != null && roar.isStopped() ? null : roar;
        if (kept == null && volume > 0) {
            kept = new EngineRoar(ship, sound, aboard, pitch, at);
            minecraft.getSoundManager().play(kept);
        }
        if (kept != null) {
            kept.toward(volume, pitch, step, pitchStep);
        }
        return kept;
    }

    /**
     * One of her engine sounds once, as loud as given: outside, from where it is among her blocks; aboard her, as it is
     * heard through her hull, round the listener, so much quieter.
     */
    static void once(Minecraft minecraft, Vec3 at, SoundEvent outside, SoundEvent aboard, float volume, boolean inside, float aboardShare) {
        if (inside) {
            minecraft.getSoundManager().play(new SimpleSoundInstance(aboard.getLocation(), SoundSource.BLOCKS, volume * aboardShare, 1.0F,
                RandomSource.create(), false, 0, SoundInstance.Attenuation.NONE, 0, 0, 0, true));
            return;
        }
        minecraft.getSoundManager().play(new SimpleSoundInstance(outside, SoundSource.BLOCKS, volume, 1.0F, RandomSource.create(), at.x, at.y, at.z));
    }

    /**
     * How low over the ground a point in the world is, for her roar's thunder close by: 1 up to so high over it, in
     * blocks, falling to 0 by so high.
     */
    static float lowness(ClientLevel level, Vec3 point, double low, double high) {
        double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(point.x), Mth.floor(point.z));
        return (float) Mth.clamp(1 - (point.y - ground - low) / (high - low), 0, 1);
    }

    /**
     * The ground shaking under this player standing near her engines firing low over it, at a point in the world: as
     * hard as given right by it, as a share of the roughest clear air (see {@link CameraShake#turbulence}), easing off
     * to nothing so far from it, in blocks.
     */
    static void shakeGround(Minecraft minecraft, Vec3 point, float low, float hardest, double felt) {
        double off = minecraft.player.position().distanceTo(point);
        float near = (float) Mth.clamp(1 - off / felt, 0, 1);
        if (near * low > 0) {
            CameraShake.turbulence(hardest * near * low, SHAKE_KEPT);
        }
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    /** How loud and how high it should be, and how far it may go toward each a tick. */
    private void toward(float volume, float pitch, float step, float pitchStep) {
        wanted = volume;
        wantedPitch = pitch;
        this.step = step;
        this.pitchStep = pitchStep;
    }

    @Override
    public void tick() {
        if (ship.isRemoved()) {
            stop();
            return;
        }
        volume = volume < wanted ? Math.min(wanted, volume + step) : Math.max(wanted, volume - step);
        pitch = pitch < wantedPitch ? Math.min(wantedPitch, pitch + pitchStep) : Math.max(wantedPitch, pitch - pitchStep);
        if (wanted <= 0 && volume <= 0) {
            stop();
        }
    }
}
