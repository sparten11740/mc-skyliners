package dev.skyliners.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skyliners.SkylinersClientConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.Craft;
import dev.skyliners.autopilot.FreighterFlight;
import it.unimi.dsi.fastutil.ints.IntList;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A birthday greeting for whoever plays as the account the client config's {@code birthdayPlayer} names, called by its
 * {@code birthdayName}, on the day its {@code birthdayDate} gives: coming aboard a freighter, a banner sweeps down over
 * the top of the view, bunting and balloons round "HAPPY BIRTHDAY", their age when the year they were born is given,
 * and the name, in bouncing rainbow letters, confetti falling over everything, while her computer wishes them a happy
 * birthday to a fanfare and fireworks burst round them. Each freighter greets them once that day, kept on this client
 * until they leave the world, nothing saved, nothing sent. Playing as anyone else, on any other day, or with any of the
 * three settings empty, it looks for nothing.
 */
public final class BirthdayBanner {
    /** How far from her middle across counts as aboard her, in blocks. */
    private static final double ABOARD_ACROSS = 26;
    /** How far above or below her middle counts as aboard her, in blocks. */
    private static final double ABOARD_UP = 12;
    /**
     * How long off every freighter counts as having come aboard anew, in ticks: longer than a freighter takes to be
     * assembled round someone aboard, which moves her autopilot and so makes her look like another ship.
     */
    private static final int OFF_TICKS = 100;
    /** How long the banner shows, and how long it takes to sweep in and out, in ticks. */
    private static final int SHOWN_TICKS = 220;
    private static final int SWEEP_TICKS = 18;
    /** How many confetti pieces fall, and how many fireworks burst. */
    private static final int CONFETTI = 140;
    private static final int FIREWORKS = 5;
    /** How big the words are drawn at most, times the font's own size; a long name is drawn smaller to fit. */
    private static final float WORDS_SCALE = 2;
    /** How much room the words leave at each end of the ribbon, in GUI pixels. */
    private static final int WORDS_MARGIN = 10;
    /** Where the banner's top rests while it shows, and where it sweeps in from, above the view, in GUI pixels. */
    private static final int BANNER_TOP = 8;
    private static final int BANNER_HIDDEN_TOP = -80;
    /** How far apart the bunting's flags hang along it, in GUI pixels. */
    private static final int FLAG_PITCH = 14;
    /** The colours of its letters, bunting, balloons, confetti and fireworks, in turn: the rainbow and pink. */
    private static final int[] RAINBOW = {0xFFFF4D4D, 0xFFFF9F1C, 0xFFFFE14D, 0xFF5BE35B, 0xFF4DC3FF, 0xFFB46CFF, 0xFFFF6EC7};

    /** The freighters that have greeted them, each by where her autopilot is, and the day they did. */
    private static final Set<Long> greeted = new HashSet<>();
    private static LocalDate greetedOn;
    /** How long they have been off every freighter, in ticks, up to {@link #OFF_TICKS}. */
    private static int offFor = OFF_TICKS;
    private static double shownAt = Double.NaN;
    private static long seed;
    /** What the banner says, as {@link #cheer} has it. */
    private static String words = "";
    /** The client config's birthday as last read, and that setting as it was then, read again only when it changes. */
    private static Birthday birthday;
    private static String readDate = "";

    private BirthdayBanner() {
    }

    /**
     * Once a client tick: whether they have just come aboard a freighter that has not greeted them today, greeting them
     * if so. Out of the world, which freighters have greeted them is forgotten: the next world's are other ships.
     */
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            greeted.clear();
            offFor = OFF_TICKS;
            return;
        }
        String name = SkylinersClientConfig.BIRTHDAY_NAME.get().strip();
        LocalDate today = LocalDate.now();
        boolean theirs = isTheirs(player) && !name.isEmpty() && isBirthday(today);
        AutopilotBlockEntity aboard = theirs ? freighterAboard(level, player.position()) : null;
        if (aboard == null) {
            offFor = Math.min(OFF_TICKS, offFor + 1);
            return;
        }
        boolean cameAboard = offFor >= OFF_TICKS;
        offFor = 0;
        if (!today.equals(greetedOn)) {
            greeted.clear();
            greetedOn = today;
        }
        if (greeted.add(aboard.getBlockPos().asLong()) && cameAboard) {
            words = cheer(name, birthday.age(today));
            greet(minecraft, level, player);
        }
    }

    /** Whether they play as the account whose birthday the client config gives: never with none given. */
    private static boolean isTheirs(LocalPlayer player) {
        String account = SkylinersClientConfig.BIRTHDAY_PLAYER.get().strip();
        return !account.isEmpty() && account.equalsIgnoreCase(player.getGameProfile().getName());
    }

    /** Whether today is the birthday the client config gives. */
    private static boolean isBirthday(LocalDate today) {
        String date = SkylinersClientConfig.BIRTHDAY_DATE.get().strip();
        if (!date.equals(readDate)) {
            readDate = date;
            birthday = Birthday.read(date);
        }
        return birthday != null && birthday.isOn(today);
    }

    /** What the banner says: "HAPPY 8TH BIRTHDAY SAM!", or with no age when the year they were born is not given. */
    private static String cheer(String name, int age) {
        String turning = age > 0 ? age + ordinal(age) + " " : "";
        return "HAPPY " + turning + "BIRTHDAY " + name.toUpperCase(Locale.ROOT) + "!";
    }

    /** The English ending of an ordinal number: ST, ND, RD or TH, as in 1ST, 2ND, 3RD, 4TH and 11TH to 13TH. */
    private static String ordinal(int number) {
        if (number % 100 >= 11 && number % 100 <= 13) {
            return "TH";
        }
        return switch (number % 10) {
            case 1 -> "ST";
            case 2 -> "ND";
            case 3 -> "RD";
            default -> "TH";
        };
    }

    /** The autopilot of the freighter whose disc a point is in, so aboard her; null aboard none. */
    private static AutopilotBlockEntity freighterAboard(ClientLevel level, Vec3 at) {
        for (AutopilotBlockEntity autopilot : AutopilotBlockEntity.loaded(level)) {
            Vec3 middle = autopilot.craft() == Craft.FREIGHTER ? FreighterFlight.middleOf(autopilot) : null;
            if (middle != null && inDisc(at, LaserBolts.inWorld(SableClient.placing(middle, 0), middle))) {
                return autopilot;
            }
        }
        return null;
    }

    /** Whether a point is in the disc round a freighter's middle in the world, so aboard her. */
    private static boolean inDisc(Vec3 at, Vec3 middle) {
        return Math.abs(at.y - middle.y) <= ABOARD_UP && Math.hypot(at.x - middle.x, at.z - middle.z) <= ABOARD_ACROSS;
    }

    /** The greeting: the banner from now, her computer's words and a fanfare, and fireworks round them. */
    private static void greet(Minecraft minecraft, ClientLevel level, LocalPlayer player) {
        shownAt = DrawingClock.ticks(0);
        seed = level.getGameTime();
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SkylinersRegistry.FREIGHTER_COMPUTER_BIRTHDAY.get(), 1.0F, 1.0F));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 0.8F));
        RandomSource random = RandomSource.create(seed);
        for (int burst = 0; burst < FIREWORKS; burst++) {
            Vec3 at = player.getEyePosition().add(player.getLookAngle().scale(4)).add(random.nextGaussian() * 2, 1.5 + random.nextFloat() * 2,
                random.nextGaussian() * 2);
            level.createFireworks(at.x, at.y, at.z, 0, 0, 0, List.of(new FireworkExplosion(FireworkExplosion.Shape.STAR,
                IntList.of(RAINBOW[burst % RAINBOW.length] & 0xFFFFFF, RAINBOW[(burst + 3) % RAINBOW.length] & 0xFFFFFF),
                IntList.of(0xFFFFFF), true, true)));
        }
    }

    /** Over the view while it shows: the banner sweeping in and out, its letters bouncing, confetti falling. */
    public static void overlay(GuiGraphics graphics, DeltaTracker deltaTracker) {
        if (Double.isNaN(shownAt)) {
            return;
        }
        float age = (float) (DrawingClock.ticks(deltaTracker.getGameTimeDeltaPartialTick(false)) - shownAt);
        if (age < 0 || age > SHOWN_TICKS) {
            return;
        }
        int width = graphics.guiWidth();
        float sweep = Math.min(1, Math.min(age, SHOWN_TICKS - age) / SWEEP_TICKS);
        float eased = sweep * sweep * (3 - 2 * sweep);
        int top = Math.round(Mth.lerp(eased, BANNER_HIDDEN_TOP, BANNER_TOP));
        confetti(graphics, width, graphics.guiHeight(), age);
        banner(graphics, width, top, age);
    }

    /** The banner: a ribbon across the top, bunting along it, a balloon at each end and the words on it. */
    private static void banner(GuiGraphics graphics, int width, int top, float age) {
        int left = width / 8;
        int right = width - left;
        graphics.fill(left, top + 12, right, top + 52, 0xE0201030);
        graphics.fill(left, top + 12, right, top + 14, 0xFFFFD24D);
        graphics.fill(left, top + 50, right, top + 52, 0xFFFFD24D);
        for (int flag = 0, x = left; x < right - 10; flag++, x += FLAG_PITCH) {
            int colour = RAINBOW[flag % RAINBOW.length];
            for (int row = 0; row < 10; row++) {
                graphics.fill(x + row / 2, top + 2 + row, x + 12 - row / 2, top + 3 + row, colour);
            }
        }
        balloon(graphics, left - 14, top + 30, age, RAINBOW[0]);
        balloon(graphics, right + 14, top + 30, age + 20, RAINBOW[4]);
        words(graphics, left, right, top + 24, age);
    }

    /** A balloon bobbing on its string at a point. */
    private static void balloon(GuiGraphics graphics, int x, int y, float age, int colour) {
        int bob = Math.round(Mth.sin(age * 0.15F) * 3);
        for (int dy = -11; dy <= 11; dy++) {
            int half = Math.round((float) Math.sqrt(Math.max(0, 1 - (dy / 11F) * (dy / 11F))) * 8);
            graphics.fill(x - half, y + bob + dy, x + half, y + bob + dy + 1, colour);
        }
        graphics.fill(x - 3, y + bob - 7, x - 1, y + bob - 3, 0xAAFFFFFF);
        graphics.fill(x, y + bob + 11, x + 1, y + bob + 34, 0xFFDDDDDD);
    }

    /**
     * The words across the ribbon between its ends, twice their usual size or as big as fits, each letter its own
     * colour of the rainbow, bouncing in a wave.
     */
    private static void words(GuiGraphics graphics, int left, int right, int y, float age) {
        Font font = Minecraft.getInstance().font;
        float scale = Math.min(WORDS_SCALE, (right - left - 2F * WORDS_MARGIN) / Math.max(1, font.width(words)));
        float wide = font.width(words) * scale;
        PoseStack pose = graphics.pose();
        float x = (left + right - wide) / 2;
        for (int i = 0; i < words.length(); i++) {
            String letter = String.valueOf(words.charAt(i));
            float bounce = Mth.sin(age * 0.25F - i * 0.5F) * 2.5F;
            pose.pushPose();
            pose.translate(x, y - 4 * scale / WORDS_SCALE + bounce, 0);
            pose.scale(scale, scale, 1);
            graphics.drawString(font, Component.literal(letter), 0, 0, RAINBOW[i % RAINBOW.length], true);
            pose.popPose();
            x += font.width(letter) * scale;
        }
    }

    /** Confetti falling over the whole view, each piece tumbling down from its own spot at its own speed. */
    private static void confetti(GuiGraphics graphics, int width, int height, float age) {
        RandomSource random = RandomSource.create(seed);
        for (int piece = 0; piece < CONFETTI; piece++) {
            float x = random.nextFloat() * width + Mth.sin(age * 0.1F + piece) * 6;
            float speed = 0.8F + random.nextFloat() * 1.6F;
            float y = Mth.positiveModulo(random.nextFloat() * height + age * speed, height + 20) - 10;
            int colour = RAINBOW[piece % RAINBOW.length];
            boolean wide = Mth.sin(age * 0.3F + piece) > 0;
            graphics.fill(Math.round(x), Math.round(y), Math.round(x) + (wide ? 4 : 2), Math.round(y) + (wide ? 2 : 4), colour);
        }
    }

    /** A birthday as the client config gives it: its day of the year, and the year they were born when that is given. */
    private record Birthday(MonthDay day, int born) {
        /** The year they were born when it is not given. */
        private static final int NOT_GIVEN = 0;

        /** A birthday read from "MM-DD" or a whole date of birth, "YYYY-MM-DD"; null for nothing set or anything else. */
        static Birthday read(String date) {
            try {
                if (date.length() == "MM-DD".length()) {
                    return new Birthday(MonthDay.parse("--" + date), NOT_GIVEN);
                }
                LocalDate birth = LocalDate.parse(date);
                return new Birthday(MonthDay.from(birth), birth.getYear());
            } catch (DateTimeParseException unreadable) {
                return null;
            }
        }

        /** Whether it is on a day: a birthday on the 29th of February comes on the 28th in a year without one. */
        boolean isOn(LocalDate today) {
            MonthDay kept = day.isValidYear(today.getYear()) ? day : day.withDayOfMonth(day.getDayOfMonth() - 1);
            return kept.equals(MonthDay.from(today));
        }

        /** How old they turn in a day's year, or 0 when the year they were born is not given or not yet past. */
        int age(LocalDate today) {
            return born == NOT_GIVEN ? 0 : Math.max(0, today.getYear() - born);
        }
    }
}
