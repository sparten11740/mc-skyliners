package dev.skyliners.client;

import dev.skyliners.Skyliners;
import dev.skyliners.item.LaserCrossbowItem;
import javax.annotation.Nullable;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * The laser crossbow's heat gauge, a row of little bars under the crosshair while the crossbow in hand is warm (see
 * {@link LaserCrossbowItem}), so that even a player who cannot read sees it heat up: one bar lit for each step of heat,
 * from yellow through orange to red, going out again one by one as it cools. Overheated, every bar flashes red and
 * white while it vents, going out one by one from the right until it may fire again. Nothing shows while it is cool.
 */
public final class LaserCrossbowGauge {
    /** How many bars it has, how wide and tall each is and the gap between them, in the GUI's pixels. */
    private static final int BARS = 6;
    private static final int BAR_WIDTH = 3;
    private static final int BAR_HEIGHT = 4;
    private static final int BAR_GAP = 1;
    /** How far below the middle of the screen its top is, clear of the crosshair, in the GUI's pixels. */
    private static final int BELOW_CROSSHAIR = 9;
    /** Each bar's colour when lit, from the first, cool end to the last, hot one (alpha first). */
    private static final int[] HEAT_COLOURS = {0xFFFFE040, 0xFFFFC030, 0xFFFF9A20, 0xFFFF7018, 0xFFFF4010, 0xFFFF2010};
    /** An unlit bar, and the dark backing behind them all (alpha first). */
    private static final int UNLIT = 0x50FFFFFF;
    private static final int BACKING = 0x90000000;
    /** The two colours the bars flash between as it vents, and how long each shows, in ticks. */
    private static final int VENT_RED = 0xFFFF2818;
    private static final int VENT_WHITE = 0xFFFFF0E8;
    private static final double FLASH_TICKS = 3;

    private LaserCrossbowGauge() {
    }

    /** Sets it up on this client, from the client's own setup. */
    static void register(IEventBus modEventBus) {
        modEventBus.addListener(LaserCrossbowGauge::onRegisterGuiLayers);
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.CROSSHAIR, ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "laser_crossbow_heat"),
            LaserCrossbowGauge::overlay);
    }

    /** Draws it, while its own player looks through their own eyes holding a warm laser crossbow. */
    static void overlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ItemStack crossbow = player == null ? null : held(player);
        if (crossbow == null || minecraft.options.hideGui || !minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        long now = (long) DrawingClock.gameTime(partialTick);
        boolean venting = LaserCrossbowItem.venting(crossbow, now);
        double share = venting ? LaserCrossbowItem.ventLeft(crossbow, now) : LaserCrossbowItem.heat(crossbow, now) / LaserCrossbowItem.MOST_HEAT;
        if (share <= 0) {
            return;
        }
        int lit = Mth.ceil(share * BARS);
        int width = BARS * BAR_WIDTH + (BARS - 1) * BAR_GAP;
        int left = (graphics.guiWidth() - width) / 2;
        int top = graphics.guiHeight() / 2 + BELOW_CROSSHAIR;
        graphics.fill(left - 1, top - 1, left + width + 1, top + BAR_HEIGHT + 1, BACKING);
        boolean white = venting && (int) (DrawingClock.ticks(partialTick) / FLASH_TICKS) % 2 == 0;
        for (int bar = 0; bar < BARS; bar++) {
            int colour = bar >= lit ? UNLIT : venting ? (white ? VENT_WHITE : VENT_RED) : HEAT_COLOURS[bar];
            int x = left + bar * (BAR_WIDTH + BAR_GAP);
            graphics.fill(x, top, x + BAR_WIDTH, top + BAR_HEIGHT, colour);
        }
    }

    /** The laser crossbow its own player holds, the main hand first; null when they hold none. */
    @Nullable
    private static ItemStack held(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof LaserCrossbowItem) {
                return stack;
            }
        }
        return null;
    }
}
