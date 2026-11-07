package dev.skyliners.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.HyperspaceJump;
import dev.skyliners.block.AutopilotBlock;
import java.lang.ref.WeakReference;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * A freighter's hyperspace jump as everyone aboard sees and hears it (see {@link HyperspaceJump}), our own take on the
 * old idea of stars stretching into light: as her drive spools up, stars come out over the view and stretch into
 * streaks rushing out from its middle toward the viewer, longer and faster, the view widening a little and deepening
 * to blue; as she lets go they race at full length; she jumps in a white flash, and coming out at the pin the streaks
 * fall back to points and are gone. Called off, they fade. Her computer says each step, in her own words, with her
 * drive's whine and its whump as she goes; so the picture and the sound both tell it, and nobody needs to read.
 *
 * <p>The streaks are stars strewn round the way she flies, fixed to her and not to the viewer's head, so turning to
 * look shows other streaks, as through a real window. They and the blue are drawn out past her glass, not over the
 * whole view: the blue on a sheet across the view {@link
 * #SHEET} blocks ahead, after the world's solid blocks and before its glass, never hiding anything nearer, so her
 * cockpit's struts and console and whoever sits there stand in front of them and they are seen only through her glass,
 * covering the land beyond it. Only the flash as she jumps covers the whole view.
 */
public final class HyperspaceOverlay {
    /** How many stars stretch over the view. */
    private static final int STARS = 220;
    /** How far ahead of the eye the sheet the streaks are drawn on lies, in blocks: out past her cockpit's glass. */
    private static final float SHEET = 8;
    /** How long a streak grows as her drive spools up and as she lets go, as a share of its longest. */
    private static final float SPOOL_STRETCH = 0.9F;
    private static final float GO_STRETCH = 3.2F;
    /**
     * Where the stars lie round the way she flies, from the eye, in blocks: how far out from that line, nearest and
     * furthest, out past her hull from wherever aboard she is seen; and the stretch of the line they are strewn along,
     * from behind the eye to ahead of it, through which they rush and come round again.
     */
    private static final float NEAREST = 14;
    private static final float FURTHEST = 70;
    private static final float BEHIND = 60;
    private static final float AHEAD = 180;
    /** How fast the stars rush back past her at full stretch, in blocks a tick, and how long a streak is then, in blocks. */
    private static final float RUSH = 14;
    private static final float LONGEST = 40;
    /** A streak's width, in blocks, near and as much more again for every block out from the line. */
    private static final float WIDTH = 0.12F;
    private static final float WIDER = 0.004F;
    /** How long the flash and the streaks falling back take coming out of the jump, and a call off's fade, in ticks. */
    private static final int ARRIVE_TICKS = 18;
    private static final int CANCEL_TICKS = 12;
    /** How long after she comes out her computer says so, in ticks: once the whump has died down. */
    private static final int ARRIVED_LINE_AFTER = 30;
    /** How much the view widens at the streaks' longest, as a share of its width. */
    private static final float WIDENING = 0.22F;
    /** How deep the blue over the view is at the streaks' longest, its opacity from 0 to 1. */
    private static final float DEEPEST_BLUE = 0.45F;
    private static final int BLUE = 0x0A1A4A;
    private static final int STREAK = 0xD8ECFF;
    private static final int STREAK_CYAN = 0x8CE6FF;
    /** How loud the jump's sounds are, heard wherever aboard she is. */
    private static final float VOLUME = 1.0F;

    /** The step showing, when it began on this client's drawing clock, how long it lasts at the least, and its stars. */
    private static HyperspaceJump.Stage stage;
    private static double began;
    private static int lasts;
    private static long seed;
    /** The way the viewer looked as her drive began to spool, for the streaks of one who rides no ship. */
    private static Vec3 lookedAhead = new Vec3(0, 0, 1);
    /** The tick to say she is out of the jump on, -1 for none due. */
    private static long arrivedLineAt = -1;
    /** The level the jump showing began in: changing world or dimension forgets it. */
    private static WeakReference<ClientLevel> shownIn = new WeakReference<>(null);

    private HyperspaceOverlay() {
    }

    /** A step of a jump the server tells: shown from now, with its sounds. */
    public static void told(HyperspaceJump step) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (step.stage() == HyperspaceJump.Stage.SPOOL) {
            seed = minecraft.level.getGameTime();
            lookedAhead = new Vec3(minecraft.gameRenderer.getMainCamera().getLookVector());
        }
        shownIn = new WeakReference<>(minecraft.level);
        stage = step.stage();
        began = DrawingClock.ticks(0);
        lasts = Math.max(1, step.ticks());
        switch (step.stage()) {
            case SPOOL -> {
                play(SkylinersRegistry.JUMP_DRIVE_SPOOL.get());
                play(SkylinersRegistry.FREIGHTER_COMPUTER_JUMP_SPOOLING.get());
            }
            case GO -> play(SkylinersRegistry.FREIGHTER_COMPUTER_JUMP_GO.get());
            case ARRIVE -> {
                play(SkylinersRegistry.JUMP_DRIVE_JUMP.get());
                arrivedLineAt = DrawingClock.wholeTicks() + ARRIVED_LINE_AFTER;
            }
            case CANCEL -> play(SkylinersRegistry.FREIGHTER_COMPUTER_JUMP_FAULT.get());
        }
    }

    /** Once a client tick: her computer says she is out of the jump a moment after she is. */
    public static void tick(ClientTickEvent.Post event) {
        if (arrivedLineAt >= 0 && DrawingClock.wholeTicks() >= arrivedLineAt) {
            arrivedLineAt = -1;
            play(SkylinersRegistry.FREIGHTER_COMPUTER_JUMP_ARRIVED.get());
        }
    }

    private static void play(SoundEvent sound) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, VOLUME));
    }

    /** The view widened a little as the streaks stretch. */
    public static void widen(ComputeFovModifierEvent event) {
        float stretch = stretch(DrawingClock.ticks(0));
        if (stretch > 0) {
            event.setNewFovModifier(event.getNewFovModifier() * (1 + WIDENING * stretch / GO_STRETCH));
        }
    }

    /** The flash over the whole view as she jumps, clearing as she comes out. */
    public static void overlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        ClientLevel level = Minecraft.getInstance().level;
        if (stage == null || level == null || level != shownIn.get()) {
            return;
        }
        float flash = flash(DrawingClock.ticks(deltaTracker.getGameTimeDeltaPartialTick(false)));
        if (flash > 0) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), Math.round(flash * 255) << 24 | 0xFFFFFF);
        }
    }

    /**
     * After the world's solid blocks, before its glass: the blue and the streaks on a sheet {@link #SHEET} blocks
     * ahead, behind whatever is nearer.
     */
    public static void world(RenderLevelStageEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || stage == null || level == null || level != shownIn.get()) {
            return;
        }
        double now = DrawingClock.ticks(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        float shown = shown(now);
        if (shown <= 0) {
            return;
        }
        float stretch = stretch(now);
        Sheet sheet = new Sheet(new Matrix4f(RenderSystem.getProjectionMatrix()));
        Matrix4fStack view = RenderSystem.getModelViewStack();
        view.pushMatrix();
        view.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float blue = DEEPEST_BLUE * shown * Math.min(1, stretch / SPOOL_STRETCH);
        sheet.quad(buffer, -1, -1, 1, 1, Math.round(blue * 255) << 24 | BLUE);
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        streaks(buffer, event.getModelViewMatrix(), event.getCamera().getPosition(), ahead(partialTick), (float) (now - began), stretch, shown);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        view.popMatrix();
        RenderSystem.applyModelViewMatrix();
    }

    /**
     * The sheet the streaks are drawn on, {@link #SHEET} blocks ahead of the eye, onto which a point of the screen,
     * given as the view's own coordinates from -1 to 1 across and up, is put in the eye's own frame. Anything nearer
     * than the sheet hides the streaks; anything farther, a long corridor of her own or the land outside, is drawn over.
     */
    private static final class Sheet {
        private final Matrix4f unproject;
        private final float depth;
        private final Vector4f point = new Vector4f();

        Sheet(Matrix4f projection) {
            unproject = projection.invert(new Matrix4f());
            Vector4f ahead = projection.transform(new Vector4f(0, 0, -SHEET, 1));
            depth = ahead.z / ahead.w;
        }

        /** Adds the corner of a quad at a point on the screen, so coloured. */
        void corner(BufferBuilder buffer, float across, float up, int colour) {
            unproject.transform(point.set(across, up, depth, 1));
            buffer.addVertex(point.x / point.w, point.y / point.w, point.z / point.w).setColor(colour);
        }

        /** Adds a quad across the screen from one corner to the other, so coloured. */
        void quad(BufferBuilder buffer, float left, float bottom, float right, float top, int colour) {
            corner(buffer, left, bottom, colour);
            corner(buffer, right, bottom, colour);
            corner(buffer, right, top, colour);
            corner(buffer, left, top, colour);
        }
    }

    /** How long the streaks are now, as a share of how far out their stars lie: growing, racing, falling back. */
    private static float stretch(double now) {
        if (stage == null) {
            return 0;
        }
        float t = (float) (now - began);
        return switch (stage) {
            case SPOOL -> SPOOL_STRETCH * eased(Math.min(1, t / lasts));
            case GO -> Mth.lerp(eased(Math.min(1, t / lasts)), SPOOL_STRETCH, GO_STRETCH);
            case ARRIVE -> GO_STRETCH * Math.max(0, 1 - t / ARRIVE_TICKS);
            case CANCEL -> SPOOL_STRETCH * Math.max(0, 1 - t / CANCEL_TICKS);
        };
    }

    /** How strongly the jump shows now, from 0 to 1: coming in as the spool begins, fading as it ends or is called off. */
    private static float shown(double now) {
        float t = (float) (now - began);
        return switch (stage) {
            case SPOOL -> Math.min(1, t / (lasts * 0.3F));
            case GO -> 1;
            case ARRIVE -> Math.max(0, 1 - t / ARRIVE_TICKS);
            case CANCEL -> Math.max(0, 1 - t / CANCEL_TICKS);
        };
    }

    /** The white flash as she jumps, its opacity from 0 to 1, clearing as she comes out. */
    private static float flash(double now) {
        if (stage != HyperspaceJump.Stage.ARRIVE) {
            return 0;
        }
        float fade = Math.max(0, 1 - (float) (now - began) / ARRIVE_TICKS);
        return fade * fade;
    }

    /** Eased in and out, from 0 to 1. */
    private static float eased(float share) {
        return share * share * (3 - 2 * share);
    }

    /**
     * The way she flies, in the world: her nose, as her autopilot's control head points, turned as Sable has her now;
     * the way the viewer looked as her drive began to spool if they ride no ship.
     */
    private static Vec3 ahead(float partialTick) {
        LocalPlayer player = Minecraft.getInstance().player;
        AutopilotBlockEntity autopilot = player == null ? null : SableClient.autopilot(player);
        if (autopilot == null) {
            return lookedAhead;
        }
        Direction nose = autopilot.getBlockState().getValue(AutopilotBlock.FACING);
        SableClient.Placing placing = SableClient.placing(Vec3.atCenterOf(autopilot.getBlockPos()), partialTick);
        if (placing == null) {
            return Vec3.atLowerCornerOf(nose.getNormal());
        }
        Vector3d turned = placing.turn().transform(new Vector3d(nose.getStepX(), nose.getStepY(), nose.getStepZ()));
        return new Vec3(turned.x, turned.y, turned.z).normalize();
    }

    /**
     * The stars as streaks round the way she flies, from the eye: each strewn at its own angle round that line and
     * distance out from it, rushing back past along it faster as she stretches into the jump, coming round again ahead,
     * its streak trailing ahead of it, the further out the wider so it still shows.
     */
    private static void streaks(BufferBuilder buffer, Matrix4f view, Vec3 eye, Vec3 ahead, float age, float stretch, float shown) {
        Vec3 up = Math.abs(ahead.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = ahead.cross(up).normalize();
        Vec3 over = right.cross(ahead).normalize();
        float span = BEHIND + AHEAD;
        float rushed = age * RUSH * (0.15F + stretch / GO_STRETCH);
        float length = 1 + LONGEST * stretch / GO_STRETCH;
        RandomSource random = RandomSource.create(seed);
        for (int star = 0; star < STARS; star++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float out = Mth.lerp(random.nextFloat(), NEAREST, FURTHEST);
            float along = AHEAD - Mth.positiveModulo(random.nextFloat() * span + rushed, span);
            boolean cyan = random.nextInt(5) == 0;
            Vec3 round = right.scale(Mth.cos(angle) * out).add(over.scale(Mth.sin(angle) * out));
            Vec3 head = ahead.scale(along).add(round);
            Vec3 tail = ahead.scale(along + length).add(round);
            int alpha = Math.round(255 * shown * Math.min(1, 0.4F + stretch / GO_STRETCH));
            streak(buffer, view, head, tail, WIDTH + WIDER * out, alpha << 24 | (cyan ? STREAK_CYAN : STREAK));
        }
    }

    /**
     * One streak between two points, from the eye, so wide, turned to face the eye, put in the eye's own frame by the
     * view's turn.
     */
    private static void streak(BufferBuilder buffer, Matrix4f view, Vec3 head, Vec3 tail, float width, int colour) {
        Vector3f near = view.transformPosition((float) head.x, (float) head.y, (float) head.z, new Vector3f());
        Vector3f far = view.transformPosition((float) tail.x, (float) tail.y, (float) tail.z, new Vector3f());
        Vector3f side = new Vector3f(far).sub(near).cross(near);
        if (side.lengthSquared() < 1e-6F) {
            return;
        }
        side.normalize(width / 2);
        buffer.addVertex(near.x + side.x, near.y + side.y, near.z + side.z).setColor(colour);
        buffer.addVertex(far.x + side.x, far.y + side.y, far.z + side.z).setColor(colour);
        buffer.addVertex(far.x - side.x, far.y - side.y, far.z - side.z).setColor(colour);
        buffer.addVertex(near.x - side.x, near.y - side.y, near.z - side.z).setColor(colour);
    }
}
