package dev.skyliners.autopilot;

import dev.skyliners.block.VillageSpotter;
import dev.skyliners.compat.SableFlight;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;

/**
 * What one kind of craft adds to its autopilot (see {@link AutopilotBlockEntity}): what it is wired to and how it is
 * flown each tick, what it does when told to land or switched off in the air, how high it may be sent, how high over
 * the ground it counts itself, what it saves and shows the clients, and what it says. The autopilot itself knows only
 * this: each kind is a class of its own, made by its {@link Craft}, so a new kind of craft is a new class and a new
 * constant there, and the autopilot is left as it is.
 *
 * <p>What every craft must decide is abstract; the rest answers as a craft that needs nothing more does. The airliner's
 * own flying stays in the autopilot, which flies it through {@link AirlinerFlight}.
 */
public abstract class CraftFlight {
    /** What it flies. */
    public abstract Craft craft();

    /**
     * Whether what it flies is assembled through Sable from the blocks in its frame (see {@link ZeppelinAssembly}),
     * too many for the Physics Assembler, as the zeppelin and the rocket are; the airliner's own Physics Assembler
     * assembles it otherwise.
     */
    abstract boolean assembledThroughSable();

    /** Whether it has what it drives wired to it. */
    boolean wired() {
        return true;
    }

    /** The lang key of what it says when it has nothing to fly with (see {@link #wired}). */
    abstract String unwiredKey();

    /** The lang key of what it says as it is assembled to fly. */
    abstract String assemblingKey();

    /** The lang key of what it says switched off, as it hands the craft back. */
    abstract String offKey();

    /**
     * Every relay and control it works, as offsets from the autopilot, for mending its links (see
     * {@link dev.skyliners.block.Turning#mend}).
     */
    List<BlockPos> links() {
        return List.of();
    }

    /** Its links turned as the autopilot's block has turned. */
    void turn(Rotation turn) {
    }

    /** What it is wired to and has learnt, into the autopilot's full save. */
    void save(CompoundTag tag) {
    }

    /** What it saved, turned as the autopilot's block has turned since; anything missing starts afresh. */
    void load(CompoundTag tag, Rotation turn) {
    }

    /** What the clients are told of it and it saves alike, as they draw and paint by it. */
    void saveShared(CompoundTag tag) {
    }

    /** What it saved or was sent alike (see {@link #saveShared}), from the save or from an update. */
    void loadShared(CompoundTag tag) {
    }

    /** What the clients are shown of it in the autopilot's update only, as they play and draw it. */
    void saveShown(CompoundTag tag) {
    }

    /** What it was shown, from an update or from the save, which may hold none. */
    void loadShown(CompoundTag tag) {
    }

    /** Engaged: it begins afresh from how the craft is now. */
    void engage(AutopilotBlockEntity autopilot, SableFlight.State state) {
    }

    /** Each tick while what it flies is assembled, on the server: it flies it, engaged or not. */
    abstract void tick(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State flight);

    /** Switched off: everything it drives let go. */
    void stop(ServerLevel level, AutopilotBlockEntity autopilot) {
    }

    /**
     * Told to land in the air, engaged already: what it says as it comes down, or null for the airliner's own landing
     * ahead.
     */
    @Nullable
    Component comeDown(AutopilotBlockEntity autopilot, SableFlight.State state) {
        return null;
    }

    /**
     * Switched off in the air: what it says if, held up by nothing else, it comes down first and switches off once
     * down; null if it is simply switched off there.
     */
    @Nullable
    Component offInTheAir(AutopilotBlockEntity autopilot, SableFlight.State state) {
        return null;
    }

    /** Whether, in a phase, it is coming down to land in a way of its own, besides coming in as the airliner does. */
    boolean landing(AutopilotBlockEntity.Phase phase) {
        return false;
    }

    /** Its landing called off by new settings: it goes on as it should then. */
    void goAround() {
    }

    /** The mode it flies, as it says it, if it says it its own way; null for the usual heading, circle or pin. */
    @Nullable
    Component modeSaid(AutopilotBlockEntity autopilot) {
        return null;
    }

    /** How far it rises over the ground engaged there, in blocks. */
    abstract int climb();

    /** The speed it cruises at engaged on the ground, in blocks a second. */
    abstract double cruise();

    /** The highest height it may be set to hold, the world's highest given. */
    double ceiling(Level level, AutopilotBlockEntity autopilot, double highest) {
        return highest;
    }

    /**
     * How high it counts itself over the ground, in blocks, from how high its autopilot is over the ground under it.
     */
    double radio(Level level, AutopilotBlockEntity autopilot, double radio) {
        return radio;
    }

    /**
     * What every ship assembled through Sable does each tick before it is flown: kept loaded while it flies or anyone
     * is near (see {@link AutopilotBlockEntity#keepLoaded}), struck by lightning in a storm, noting the villages below;
     * how high it is over the ground, in blocks.
     */
    static double keptAloft(ServerLevel level, AutopilotBlockEntity autopilot, SableFlight.State flight) {
        double radio = autopilot.radioAltitude(level, flight);
        autopilot.keepLoaded(level, flight, radio);
        autopilot.lightning.tick(level, autopilot, flight.position(), radio);
        VillageSpotter.tick(level, autopilot, flight.position());
        return radio;
    }

    /**
     * Notes the craft in the air, or down again, after it has flown its tick (see {@link
     * AutopilotBlockEntity#watchArrival}).
     */
    static void watchArrival(AutopilotBlockEntity autopilot, SableFlight.State flight, double radio) {
        autopilot.watchArrival(flight, radio + flight.apex() - flight.position().y > AutopilotBlockEntity.AIRBORNE);
    }
}
