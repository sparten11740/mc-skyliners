package dev.skyliners.client;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.LedColour;
import dev.skyliners.block.LedMode;
import dev.skyliners.block.LedPanelBlockEntity;
import dev.skyliners.block.LedSetting;
import dev.skyliners.block.LedStripBlock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The rocket's LED strips as this client draws them (see {@link LedStripBlock}): the tint each strip is meshed with,
 * from the setting of the story whose box it lies in (see {@link LedPanelBlockEntity}), and how the animated modes
 * move, by the game clock, so every strip and every player agrees. As {@code ZeppelinPaint} does a ship's livery, each
 * tick it takes a fresh look at the panels loaded into a list the threads that mesh the world read as it stands, and
 * has a story's sections meshed again only where its colour or its mode has changed: nothing is meshed again or relit
 * as the strips animate, which their panel's renderer draws over them (see {@link LedPanelRenderer}). Every animated
 * mode changes a strip at most a few times a second, never flashing faster than three times a second.
 */
public final class LedLights {
    /**
     * A story's strips as last seen: its panel, the box round them, the wedge they lie in for a room shaped as one
     * (null for a story that is its whole box), and what they are set to.
     */
    private record Lit(BlockPos panel, BoundingBox box, @Nullable LedPanelBlockEntity.Wedge wedge, LedSetting setting) {
        boolean covers(BlockPos pos) {
            return box.isInside(pos) && (wedge == null || wedge.contains(pos));
        }
    }

    /** How bright a strip's face is meshed at each brightness, as a share of its colour full on. */
    private static final float[] FACE_SHARE = {0, 0.5F, 0.75F, 1};
    /** An unlit strip's dark diffuser. */
    private static final int UNLIT = 0x2C3036;
    /** How bright a strip of an animated mode is meshed, as a share of its brightness: the renderer adds the rest. */
    static final float ANIMATED_BASE = 0.3F;
    /** A breath: how long each of its steps lasts, in ticks, and how bright each is, as a share, through one breath. */
    private static final int BREATH_STEP_TICKS = 10;
    private static final float[] BREATH = {0.3F, 0.55F, 0.8F, 1, 1, 0.8F, 0.55F, 0.3F};
    /**
     * The chase: how long each band and each gap between are round the ring, and how long it takes to move a block on.
     */
    static final int CHASE_BAND = 2;
    private static final int CHASE_STEP_TICKS = 4;
    /** The club: how bright a pulse has faded to by the next beat, as a share. */
    private static final float CLUB_LOW = 0.15F;

    /** The stories as last seen, read by the meshing threads while the client's thread puts the next in its place. */
    private static volatile List<Lit> lit = List.of();
    /** The same, by where each one's panel is: what was meshed for it, to mesh again what changes. */
    private static final Map<BlockPos, Lit> MESHED = new HashMap<>();

    private LedLights() {
    }

    /** Hooks the strips' tint, the remeshing and the panels' renderers in. */
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener((RegisterColorHandlersEvent.Block event) -> event.register(LedLights::tint, SkylinersRegistry.LED_STRIP.get()));
        modEventBus.addListener(LedLights::renderers);
        NeoForge.EVENT_BUS.addListener(LedLights::tick);
    }

    private static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(SkylinersRegistry.LED_PANEL_ENTITY.get(), context -> new LedPanelRenderer());
        event.registerBlockEntityRenderer(SkylinersRegistry.LED_CONSOLE_ENTITY.get(), context -> new LedConsoleRenderer());
    }

    /**
     * The tint a strip is meshed with where it stands: its story's colour at its own brightness, dimmer for a mode the
     * renderer brightens; as the strips are built where no story is known.
     */
    public static int tint(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int tintIndex) {
        LedSetting setting = pos == null ? LedSetting.PLAIN : settingAt(pos);
        int brightness = state.hasProperty(LedStripBlock.BRIGHTNESS) ? state.getValue(LedStripBlock.BRIGHTNESS) : setting.brightness();
        if (brightness <= 0) {
            return UNLIT;
        }
        return scaled(setting.colour().rgb(), share(brightness) * (setting.mode().animated() ? ANIMATED_BASE : 1));
    }

    /** The setting of the story a block lies in, as this client last saw it. */
    private static LedSetting settingAt(BlockPos pos) {
        for (Lit story : lit) {
            if (story.covers(pos)) {
                return story.setting();
            }
        }
        return LedSetting.PLAIN;
    }

    /** Once a tick: the stories' settings as their panels have them now, and what has to be meshed again for them. */
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            lit = List.of();
            MESHED.clear();
            return;
        }
        List<Lit> now = new ArrayList<>();
        for (LedPanelBlockEntity panel : LedPanelBlockEntity.loaded(level)) {
            BoundingBox box = panel.box();
            if (box != null) {
                now.add(new Lit(panel.getBlockPos().immutable(), box, panel.wedge(), panel.setting()));
            }
        }
        lit = List.copyOf(now);
        for (Lit story : now) {
            remesh(MESHED.get(story.panel()), story);
        }
        MESHED.clear();
        now.forEach(story -> MESHED.put(story.panel(), story));
    }

    /**
     * Has a story's sections meshed again where its strips are meshed differently from before: a new colour, or a mode
     * that animates where it did not or the other way round; for a story new to this client, where it is not set as
     * strips are meshed without one.
     */
    private static void remesh(@Nullable Lit before, Lit after) {
        LedSetting was = before == null ? LedSetting.PLAIN : before.setting();
        LedSetting is = after.setting();
        if (was.colour() == is.colour() && was.mode().animated() == is.mode().animated()) {
            return;
        }
        BoundingBox box = after.box();
        for (BlockPos section : BlockPos.betweenClosed(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minY()),
            SectionPos.blockToSectionCoord(box.minZ()), SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxY()),
            SectionPos.blockToSectionCoord(box.maxZ()))) {
            Minecraft.getInstance().levelRenderer.setSectionDirty(section.getX(), section.getY(), section.getZ());
        }
    }

    /** How bright a strip's face is at a brightness, as a share of its colour full on. */
    static float share(int brightness) {
        return FACE_SHARE[Math.max(0, Math.min(brightness, LedStripBlock.BRIGHTEST))];
    }

    /** A colour as RGB, so much as bright. */
    static int scaled(int rgb, float share) {
        int red = Math.min(255, Math.round((rgb >> 16 & 0xFF) * share));
        int green = Math.min(255, Math.round((rgb >> 8 & 0xFF) * share));
        int blue = Math.min(255, Math.round((rgb & 0xFF) * share));
        return red << 16 | green << 8 | blue;
    }

    /**
     * How bright an animated strip is now, as a share of its brightness: a story's breath, the bands of its chase,
     * running one way round the floor's ring and the other round the ceiling's, so far round its ring it is, in blocks,
     * or the club's pulse, fading from each beat to the next; full for a steady one.
     */
    static float intensity(LedMode mode, double ticks, double along, boolean ceiling) {
        return switch (mode) {
            case STEADY -> 1;
            case BREATHE -> BREATH[Math.floorMod((long) Math.floor(ticks / BREATH_STEP_TICKS), BREATH.length)];
            case CHASE -> chaseLit(along, ticks, ceiling) ? 1 : ANIMATED_BASE;
            case CLUB -> pulse(ticks);
        };
    }

    /** Whether a strip so far round its ring, in blocks, is in one of the chase's bands now. */
    static boolean chaseLit(double along, double ticks, boolean ceiling) {
        long step = (long) Math.floor(ticks / CHASE_STEP_TICKS);
        double moved = ceiling ? along + step : along - step;
        return Math.floorMod((long) Math.floor(moved / CHASE_BAND), 2) == 0;
    }

    /** The club's pulse now: full on each beat of the techno club's music, fading away to the next. */
    static float pulse(double ticks) {
        double into = ticks / TechnoRoom.BEAT_TICKS % 1;
        return (float) (CLUB_LOW + (1 - CLUB_LOW) * (1 - into) * (1 - into));
    }

    /**
     * The colour a strip of a story glows in now: its own, but in the club, its partner on every other beat, the
     * floor's ring and the ceiling's taking turns.
     */
    static LedColour colourNow(LedSetting setting, double ticks, boolean ceiling) {
        if (setting.mode() != LedMode.CLUB) {
            return setting.colour();
        }
        boolean offBeat = Math.floorMod((long) Math.floor(ticks / TechnoRoom.BEAT_TICKS), 2) == 1;
        return offBeat != ceiling ? setting.colour().partner() : setting.colour();
    }

    /** How a strip of a story looks now, all told, as ARGB: for the panels' pictures of it. */
    static int looks(LedSetting setting, double ticks, double along, boolean ceiling) {
        if (setting.brightness() <= 0) {
            return 0xFF000000 | UNLIT;
        }
        float bright = share(setting.brightness()) * intensity(setting.mode(), ticks, along, ceiling);
        return 0xFF000000 | scaled(colourNow(setting, ticks, ceiling).rgb(), bright);
    }
}
