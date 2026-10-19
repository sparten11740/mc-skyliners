package dev.aerolarge.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aerolarge.block.AutopilotBlock;
import dev.aerolarge.block.AutopilotBlockEntity;
import dev.aerolarge.block.AutopilotBlockEntity.Lateral;
import dev.aerolarge.block.AutopilotBlockEntity.Phase;
import dev.aerolarge.block.AutopilotBlockEntity.Settings;
import dev.aerolarge.block.OpenAutopilotScreen;
import dev.aerolarge.block.SetAutopilot;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The autopilot's settings, opened by using the autopilot or its screen on the flight deck. At the top, live: what
 * the autopilot is doing, how the airliner flies now and what the autopilot is set to fly. Below, the settings: hold a
 * heading or circle left or right, then the heading, altitude, speed and circle radius, each typed in or stepped with
 * the buttons either side of it and kept to what the autopilot flies. Switched off, engaging flies them (taking off
 * first on the ground); engaged, they can be applied, or the autopilot switched off. In the air it can also be told
 * to land. The server checks it all again (see {@link SetAutopilot}). The game goes on behind it, and it closes once
 * the player is out of the autopilot's reach.
 */
public class AutopilotSettingsScreen extends Screen {
    /** The panel's size and the space inside its edge. */
    private static final int WIDTH = 300;
    private static final int HEIGHT = 222;
    private static final int PADDING = 10;
    /** From the panel's top: the lines of how it flies, the rule under them, and the first row of settings. */
    private static final int NOW_Y = 28;
    private static final int SET_Y = 42;
    private static final int RULE_Y = 60;
    private static final int ROWS_Y = 68;
    /** How far along a line of how it flies its values start, after its name. */
    private static final int VALUE_X = 50;
    /** A row of settings: its height, the column of names, a step button, an edit box, and the space between them. */
    private static final int ROW = 24;
    private static final int NAME_WIDTH = 84;
    private static final int STEP_WIDTH = 28;
    private static final int BOX_WIDTH = 50;
    private static final int GAP = 4;
    private static final int CONTROL_HEIGHT = 20;
    /** The width of a setting's controls together: its step buttons and the box between them. */
    private static final int CONTROLS_WIDTH = 2 * STEP_WIDTH + BOX_WIDTH + 2 * GAP;
    /** What can be typed into a setting: a whole number of up to four digits, perhaps below zero. */
    private static final Pattern NUMBER = Pattern.compile("-?\\d{0,4}");
    private static final int MAX_LENGTH = 5;
    /** What is typed: light, or red while it cannot be read or is out of range (it is brought into range when sent). */
    private static final int TYPED = 0xFFE0E0E0;
    private static final int UNREADABLE = 0xFFFF5555;
    /** The highest compass bearing; bearings go round past it. */
    private static final int LAST_BEARING = 359;

    private final BlockPos pos;
    /** Whether engaging takes off, so the button says so. */
    private final boolean takeoff;
    private Lateral lateral;
    private final Setting heading;
    private final Setting altitude;
    private final Setting speed;
    private final Setting radius;
    /** The panel's top left corner on the screen. */
    private int left;
    private int top;
    private Button engage;
    private Button apply;
    private Button disengage;
    private Button land;

    private AutopilotSettingsScreen(OpenAutopilotScreen opened) {
        super(Component.translatable("screen.aerolarge.autopilot_settings.title"));
        Settings settings = opened.settings();
        pos = opened.pos();
        takeoff = opened.takeoff();
        lateral = settings.lateral();
        heading = new Setting("heading", settings.heading(), 0, LAST_BEARING, AutopilotBlockEntity.HEADING_STEP, true);
        altitude = new Setting("altitude", settings.altitude(), opened.minAltitude(), opened.maxAltitude(), AutopilotBlockEntity.ALTITUDE_STEP, false);
        speed = new Setting("speed", settings.speed(), AutopilotBlockEntity.MIN_SPEED, AutopilotBlockEntity.MAX_SPEED, AutopilotBlockEntity.SPEED_STEP, false);
        radius = new Setting("radius", settings.radius(), AutopilotBlockEntity.MIN_RADIUS, AutopilotBlockEntity.MAX_RADIUS, AutopilotBlockEntity.RADIUS_STEP, false);
    }

    /** Opens the settings as the server sent them, over whatever else is open. */
    public static void open(OpenAutopilotScreen opened) {
        Minecraft.getInstance().setScreen(new AutopilotSettingsScreen(opened));
    }

    /** The rows of settings under the mode, top to bottom. */
    private List<Setting> rows() {
        return List.of(heading, altitude, speed, radius);
    }

    private int rowY(int row) {
        return top + ROWS_Y + row * ROW;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        int x = left + PADDING + NAME_WIDTH;
        addRenderableWidget(CycleButton.<Lateral>builder(mode -> Component.translatable("screen.aerolarge.autopilot_settings.mode." + mode.name().toLowerCase(Locale.ROOT)))
            .withValues(Lateral.values())
            .withInitialValue(lateral)
            .displayOnlyValue()
            .create(x, rowY(0), CONTROLS_WIDTH, CONTROL_HEIGHT, Component.translatable("screen.aerolarge.autopilot_settings.mode"), (button, mode) -> {
                lateral = mode;
                refresh();
            }));
        List<Setting> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).place(x, rowY(i + 1));
        }

        // Along the bottom in four places: engage across the first two, or apply and disengage in them once engaged;
        // then land, and cancel.
        int buttonWidth = (WIDTH - 2 * PADDING - 3 * GAP) / 4;
        int buttonX = left + PADDING;
        int buttonY = top + HEIGHT - PADDING - CONTROL_HEIGHT;
        String engageKey = takeoff ? "screen.aerolarge.autopilot_settings.take_off" : "screen.aerolarge.autopilot_settings.engage";
        engage = addRenderableWidget(Button.builder(Component.translatable(engageKey), button -> send(SetAutopilot.Action.FLY))
            .bounds(buttonX, buttonY, 2 * buttonWidth + GAP, CONTROL_HEIGHT).build());
        apply = addRenderableWidget(Button.builder(Component.translatable("screen.aerolarge.autopilot_settings.apply"), button -> send(SetAutopilot.Action.FLY))
            .bounds(buttonX, buttonY, buttonWidth, CONTROL_HEIGHT).build());
        disengage = addRenderableWidget(Button.builder(Component.translatable("screen.aerolarge.autopilot_settings.disengage"), button -> send(SetAutopilot.Action.OFF))
            .bounds(buttonX + buttonWidth + GAP, buttonY, buttonWidth, CONTROL_HEIGHT).build());
        land = addRenderableWidget(Button.builder(Component.translatable("screen.aerolarge.autopilot_settings.land"), button -> send(SetAutopilot.Action.LAND))
            .bounds(buttonX + 2 * (buttonWidth + GAP), buttonY, buttonWidth, CONTROL_HEIGHT).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
            .bounds(buttonX + 3 * (buttonWidth + GAP), buttonY, buttonWidth, CONTROL_HEIGHT).build());
        refresh();
    }

    /** The autopilot, as this client has it, if it is still there. */
    @Nullable
    private AutopilotBlockEntity autopilot() {
        Level level = minecraft != null ? minecraft.level : null;
        return level != null && level.getBlockEntity(pos) instanceof AutopilotBlockEntity autopilot ? autopilot : null;
    }

    private static Direction nose(AutopilotBlockEntity autopilot) {
        return autopilot.getBlockState().getValue(AutopilotBlock.FACING);
    }

    /** Shows the buttons for the autopilot as it is now, and the settings the chosen mode flies. */
    private void refresh() {
        AutopilotBlockEntity autopilot = autopilot();
        boolean assembled = autopilot != null && SableClient.attitude(autopilot, nose(autopilot), 1) != null;
        Phase phase = autopilot != null ? autopilot.phase() : Phase.OFF;
        boolean on = phase != Phase.OFF;
        engage.visible = !on;
        engage.active = assembled;
        apply.visible = on;
        // Once down after landing it only brakes to a stop.
        apply.active = phase != Phase.ROLLOUT;
        disengage.visible = on;
        // Only in the air, and not while it lands already.
        land.active = assembled && (on ? phase == Phase.CLIMB || phase == Phase.DESCENT || phase == Phase.CRUISE : !takeoff);
        heading.setActive(lateral == Lateral.HEADING);
        radius.setActive(lateral != Lateral.HEADING);
    }

    @Override
    public void tick() {
        AutopilotBlockEntity autopilot = autopilot();
        LocalPlayer player = minecraft != null ? minecraft.player : null;
        if (autopilot == null || player == null || !autopilot.canBeWorkedBy(player)) {
            onClose();
            return;
        }
        refresh();
    }

    /** Sends the settings as they stand, to fly them, to land or with the autopilot switched off, and closes. */
    private void send(SetAutopilot.Action action) {
        Settings settings = new Settings(speed.value(), altitude.value(), heading.value(), lateral, radius.value());
        PacketDistributor.sendToServer(new SetAutopilot(pos, action, settings));
        onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        // Enter, while no button has the keyboard, flies the settings, as the button there would.
        boolean enter = keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER;
        Button fly = apply.visible ? apply : engage;
        if (enter && fly.active) {
            send(SetAutopilot.Action.FLY);
            return true;
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // The world dimmed behind it, as behind a container, rather than blurred: the flight goes on.
        renderTransparentBackground(graphics);
        graphics.fill(left - 1, top - 1, left + WIDTH + 1, top + HEIGHT + 1, AutopilotScreenRenderer.BUTTON_EDGE);
        graphics.fill(left, top, left + WIDTH, top + HEIGHT, AutopilotScreenRenderer.CASING);
        graphics.fill(left + PADDING, top + RULE_Y, left + WIDTH - PADDING, top + RULE_Y + 1, AutopilotScreenRenderer.BUTTON_EDGE);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = left + PADDING;
        graphics.drawString(font, title, x, top + PADDING, AutopilotScreenRenderer.WHITE, false);

        // What it is doing, as its screen on the flight deck says it, and how the airliner flies.
        AutopilotBlockEntity autopilot = autopilot();
        Level level = autopilot != null ? autopilot.getLevel() : null;
        SableClient.Attitude attitude = autopilot != null ? SableClient.attitude(autopilot, nose(autopilot), partialTick) : null;
        boolean on = AutopilotScreenRenderer.engaged(autopilot);
        Component state = AutopilotScreenRenderer.state(autopilot, attitude);
        int stateColour = attitude == null ? AutopilotScreenRenderer.GREY : on ? AutopilotScreenRenderer.GREEN : AutopilotScreenRenderer.AMBER;
        graphics.drawString(font, state, left + WIDTH - PADDING - font.width(state), top + PADDING, stateColour, false);
        if (attitude != null && level != null) {
            Vec3 here = SableClient.toWorld(level, Vec3.atCenterOf(pos));
            line(graphics, "now", AutopilotScreenRenderer.now(attitude, here), NOW_Y, AutopilotScreenRenderer.WHITE);
            if (on) {
                line(graphics, "set", AutopilotScreenRenderer.targets(autopilot), SET_Y, AutopilotScreenRenderer.TARGET);
            } else {
                String hint = takeoff ? "screen.aerolarge.autopilot_settings.take_off_hint" : "screen.aerolarge.autopilot_settings.engage_hint";
                graphics.drawString(font, Component.translatable(hint), x, top + SET_Y, AutopilotScreenRenderer.GREY, false);
            }
        }

        int drop = (CONTROL_HEIGHT - font.lineHeight) / 2 + 1;
        graphics.drawString(font, Component.translatable("screen.aerolarge.autopilot_settings.mode"), x, rowY(0) + drop, AutopilotScreenRenderer.WHITE, false);
        List<Setting> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).label(graphics, x, rowY(i + 1) + drop);
        }
    }

    /** A line of how it flies: its name, then its values. */
    private void line(GuiGraphics graphics, String key, Component values, int y, int colour) {
        graphics.drawString(font, Component.translatable("screen.aerolarge.autopilot_settings." + key), left + PADDING, top + y, AutopilotScreenRenderer.GREY, false);
        graphics.drawString(font, values, left + PADDING + VALUE_X, top + y, colour, false);
    }

    /** One setting: an edit box between buttons that step it down and up, kept to its range; a bearing goes round. */
    private final class Setting {
        private final String key;
        private final int min;
        private final int max;
        private final int step;
        private final boolean bearing;
        /** What it was last set to, for when what is typed cannot be read. */
        private int value;
        @Nullable
        private EditBox box;
        @Nullable
        private Button down;
        @Nullable
        private Button up;

        Setting(String key, int value, int min, int max, int step, boolean bearing) {
            this.key = key;
            this.min = min;
            this.max = max;
            this.step = step;
            this.bearing = bearing;
            this.value = fit(value);
        }

        /** Lays its controls out along a row from x, keeping what was typed into them before. */
        void place(int x, int y) {
            value = value();
            down = addRenderableWidget(Button.builder(Component.translatable("screen.aerolarge.autopilot_settings.down", step), button -> show(value() - step))
                .bounds(x, y, STEP_WIDTH, CONTROL_HEIGHT).build());
            EditBox typed = addRenderableWidget(new EditBox(font, x + STEP_WIDTH + GAP, y, BOX_WIDTH, CONTROL_HEIGHT, name()));
            typed.setMaxLength(MAX_LENGTH);
            typed.setFilter(text -> NUMBER.matcher(text).matches());
            typed.setValue(format(value));
            typed.setResponder(text -> typed.setTextColor(readable(text) ? TYPED : UNREADABLE));
            box = typed;
            up = addRenderableWidget(Button.builder(Component.translatable("screen.aerolarge.autopilot_settings.up", step), button -> show(value() + step))
                .bounds(x + STEP_WIDTH + BOX_WIDTH + 2 * GAP, y, STEP_WIDTH, CONTROL_HEIGHT).build());
        }

        /** What it is set to: what is typed, brought into range, or what it was last set to if that cannot be read. */
        int value() {
            if (box == null) {
                return value;
            }
            try {
                return fit(Integer.parseInt(box.getValue()));
            } catch (NumberFormatException e) {
                return value;
            }
        }

        private void show(int set) {
            value = fit(set);
            if (box != null) {
                box.setValue(format(value));
            }
        }

        private boolean readable(String text) {
            try {
                int typed = Integer.parseInt(text);
                return typed >= min && typed <= max;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        private int fit(int set) {
            return bearing ? Math.floorMod(set, max + 1) : Mth.clamp(set, min, max);
        }

        private String format(int set) {
            return bearing ? String.format(Locale.ROOT, "%03d", set) : Integer.toString(set);
        }

        /** Greys the setting out while the mode chosen does not fly it. */
        void setActive(boolean active) {
            if (box != null && down != null && up != null) {
                box.setEditable(active);
                box.active = active;
                down.active = active;
                up.active = active;
            }
        }

        private Component name() {
            return Component.translatable("screen.aerolarge.autopilot_settings." + key);
        }

        /** Its name before its controls and its range after them. */
        void label(GuiGraphics graphics, int x, int y) {
            boolean active = box == null || box.active;
            graphics.drawString(font, name(), x, y, active ? AutopilotScreenRenderer.WHITE : AutopilotScreenRenderer.GREY, false);
            Component range = Component.translatable("screen.aerolarge.autopilot_settings.range." + key, format(min), format(max));
            graphics.drawString(font, range, x + NAME_WIDTH + CONTROLS_WIDTH + 2 * GAP, y, AutopilotScreenRenderer.GREY, false);
        }
    }
}
