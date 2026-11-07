package dev.skyliners.client;

import dev.skyliners.item.LaserFire;
import dev.skyliners.turret.LaserCannonBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The view from the freighter's laser cannon (see {@link LaserCannonBlockEntity}), on this client: sitting down at a
 * cannon moves the player's view up to its sight, over and behind its swivel on its pedestal, turning with their look,
 * so they see all round her and up into the sky, the cannon's muzzles low in the view; sneaking to get up gives them
 * their own view back at once, as does anything else that takes them from the seat. The sight rides with her, placed
 * afresh every frame as she is drawn, and leans with her. The player stays seated and turns as ever, so the server
 * hears their look as it always does; it traces it from the same sight, so the bolts land under its crosshair. Their
 * hand, the block outline and the game's crosshair give way to the sight's own (see {@link GunnerSight}).
 *
 * <p>Once a tick it also looks along the sight for what the cannon would strike, for the sight to show: a hostile mob
 * in reach locks it on, with a beep; and pulling the trigger while it cannot fire (too hot, aimed into her own hull or
 * lower than its barrels go) gets a dry click.
 */
public final class GunnerView {
    /** How loud and how high the beep is as the sight locks on to a hostile mob. */
    private static final float LOCK_VOLUME = 0.45F;
    private static final float LOCK_PITCH = 2.0F;
    /** How loud and how low the dry click is as the trigger is pulled while the cannon cannot fire. */
    private static final float DRY_VOLUME = 0.6F;
    private static final float DRY_PITCH = 1.4F;

    /** The view from a cannon, while there is one. */
    @Nullable
    private static Gunning gunning;

    private GunnerView() {
    }

    /** The view from a cannon: which one, what it is drawn from, and what to give back afterwards. */
    private static final class Gunning {
        final LaserCannonBlockEntity cannon;
        final Lens lens;
        final CameraType cameraType;
        /** How far the view leans with her this frame, in degrees. */
        float roll;
        /** Whether the sight rests on a hostile mob it would strike, and for how many ticks it has. */
        boolean locked;
        int lockedTicks;
        /** Whether the trigger was held last tick. */
        boolean triggerHeld;

        Gunning(LaserCannonBlockEntity cannon, Lens lens, CameraType cameraType) {
            this.cannon = cannon;
            this.lens = lens;
            this.cameraType = cameraType;
        }
    }

    /** The cannon whose sight the player looks through now; none while they look through none. */
    @Nullable
    static LaserCannonBlockEntity cannon() {
        return gunning == null ? null : gunning.cannon;
    }

    /** Whether the sight rests on a hostile mob in reach. */
    static boolean locked() {
        return gunning != null && gunning.locked;
    }

    /** How many ticks the sight has rested on the hostile mob it is locked on to; none while it is locked on to none. */
    static int lockedTicks() {
        return gunning != null && gunning.locked ? gunning.lockedTicks : 0;
    }

    /** What the player sees from a cannon's sight now, for a play-test to read; none while they look from none. */
    @Nullable
    public static LaserCannonBlockEntity.Sighting sighting() {
        Gunning view = gunning;
        return view == null ? null : new LaserCannonBlockEntity.Sighting(view.lens.getEyePosition(), view.locked);
    }

    /**
     * Whether the cannon holds its fire where the sight looks: its barrels pointing into her own hull, or the look
     * lower than they go, as the server says.
     */
    static boolean held() {
        LaserCannonBlockEntity cannon = cannon();
        return cannon != null && cannon.held();
    }

    /**
     * At the end of each tick: the view goes to the cannon the player sits down at and back to them as they get up,
     * sneak or anything else takes them from it; while it lasts, it is kept in first person and looks for what the
     * sight rests on.
     */
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean gettingUp = minecraft.options.keyShift.isDown();
        LaserCannonBlockEntity cannon = player == null || gettingUp ? null : GunnerControls.cannon(player);
        Gunning view = gunning;
        if (view != null && (cannon != view.cannon || minecraft.getCameraEntity() != view.lens)) {
            leave(minecraft);
        }
        if (gunning == null && cannon != null && minecraft.getCameraEntity() == player) {
            enter(minecraft, player, cannon);
        }
        view = gunning;
        if (view == null || player == null) {
            return;
        }
        if (!minecraft.options.getCameraType().isFirstPerson()) {
            minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        }
        look(minecraft, player, view);
        dryClick(minecraft, view);
    }

    /** The view goes up to the cannon's sight. */
    private static void enter(Minecraft minecraft, LocalPlayer player, LaserCannonBlockEntity cannon) {
        Gunning view = new Gunning(cannon, new Lens(player.level(), 0), minecraft.options.getCameraType());
        place(view, player, minecraft.getTimer().getGameTimeDeltaPartialTick(true));
        gunning = view;
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.setCameraEntity(view.lens);
    }

    /** Gives the player their own view back, as it was; unless something else has taken the view over meanwhile. */
    private static void leave(Minecraft minecraft) {
        Gunning view = gunning;
        if (view == null) {
            return;
        }
        gunning = null;
        if (minecraft.getCameraEntity() == view.lens && minecraft.player != null) {
            minecraft.options.setCameraType(view.cameraType);
            minecraft.setCameraEntity(minecraft.player);
        }
    }

    /**
     * Puts the lens at the cannon's sight as she is drawn at this partial tick, looking the way the player looks among
     * her blocks and leaning with her.
     */
    private static void place(Gunning view, LocalPlayer player, float partialTick) {
        float yRot = player.getViewYRot(partialTick);
        float xRot = player.getViewXRot(partialTick);
        Vec3 sight = view.cannon.sight(yRot, xRot);
        Vec3 ahead = Vec3.directionFromRotation(xRot, yRot);
        Vec3 up = LaserCannonBlockEntity.sightUp(yRot, xRot);
        SableClient.View seen = SableClient.view(sight, ahead, up, partialTick);
        view.roll = seen == null ? view.lens.point(sight, ahead, up) : view.lens.point(seen.eye(), seen.forward(), seen.up());
    }

    /**
     * What the sight rests on, as the cannon's bolts would find it (see {@link LaserFire#trace}): the first block it
     * meets within their reach, among a ship's blocks too, or the first one standing before that block who may catch a
     * bolt, passing anyone aboard her. Locked on if that is a hostile mob, beeping as it locks on.
     */
    private static void look(Minecraft minecraft, LocalPlayer player, Gunning view) {
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        HitResult hit = LaserFire.trace(level, player, view.lens.getEyePosition(), view.lens.getLookAngle(), LaserCannonBlockEntity.RANGE,
            entity -> !SableClient.isAboard(entity, view.cannon));
        boolean locked = hit instanceof EntityHitResult struck && struck.getEntity() instanceof Enemy && struck.getEntity().isAlive();
        if (locked && !view.locked) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), LOCK_PITCH, LOCK_VOLUME));
        }
        view.lockedTicks = locked ? view.lockedTicks + 1 : 0;
        view.locked = locked;
    }

    /** A dry click as the trigger is pulled while the cannon cannot fire: holding its fire, or overheated. */
    private static void dryClick(Minecraft minecraft, Gunning view) {
        boolean pulled = minecraft.screen == null && minecraft.options.keyAttack.isDown();
        if (pulled && !view.triggerHeld && (view.cannon.held() || view.cannon.overheated(0))) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.DISPENSER_FAIL, DRY_PITCH, DRY_VOLUME));
        }
        view.triggerHeld = pulled;
    }

    /** Before each frame is drawn, the lens goes to the sight as she is drawn now. */
    public static void frame(RenderFrameEvent.Pre event) {
        Gunning view = gunning;
        LocalPlayer player = Minecraft.getInstance().player;
        if (view != null && player != null) {
            place(view, player, event.getPartialTick().getGameTimeDeltaPartialTick(true));
        }
    }

    /** The view leans with her. */
    public static void angles(ViewportEvent.ComputeCameraAngles event) {
        Gunning view = gunning;
        if (view != null && event.getCamera().getEntity() == view.lens) {
            event.setRoll(event.getRoll() + view.roll);
        }
    }

    /** No hand or held item in the view from the sight. */
    public static void hand(RenderHandEvent event) {
        if (gunning != null) {
            event.setCanceled(true);
        }
    }

    /** No outline round the block next to the sight, which the player could not reach. */
    public static void highlight(RenderHighlightEvent.Block event) {
        if (gunning != null) {
            event.setCanceled(true);
        }
    }

    /** No game crosshair over the sight's own. */
    public static void guiLayer(RenderGuiLayerEvent.Pre event) {
        if (gunning != null && event.getName().equals(VanillaGuiLayers.CROSSHAIR)) {
            event.setCanceled(true);
        }
    }

    /** Leaving the game gives the player their own view back, so the next world does not open on a lens. */
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        leave(Minecraft.getInstance());
    }
}
