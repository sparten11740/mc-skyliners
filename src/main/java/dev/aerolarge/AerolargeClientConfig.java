package dev.aerolarge;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings: what each player feels aboard the airliner, and how much their computer draws for it. */
public final class AerolargeClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CAMERA_SHAKE;
    public static final ModConfigSpec.BooleanValue TAIL_CAMERA_FEED;
    public static final ModConfigSpec.IntValue TAIL_CAMERA_FPS;
    public static final ModConfigSpec.IntValue TAIL_CAMERA_RESOLUTION;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        CAMERA_SHAKE = builder
            .comment("Shake the camera aboard the airliner: rumbling down the runway, thumping onto it, swaying in flight.")
            .define("cameraShake", true);
        TAIL_CAMERA_FEED = builder
            .comment("Show the live picture from the camera on top of the fin on the flight deck's tail camera display. It draws",
                "the world a second time, small, while a display is in sight; turn it off if that costs too much. It is never",
                "drawn with a shader pack or Fabulous graphics: the display shows its test card then.")
            .define("tailCameraFeed", true);
        TAIL_CAMERA_FPS = builder
            .comment("How many times a second, at most, the tail camera's picture is drawn anew.")
            .defineInRange("tailCameraFps", 15, 1, 30);
        TAIL_CAMERA_RESOLUTION = builder
            .comment("How many pixels tall the tail camera's picture is drawn; it is a little over twice as wide.")
            .defineInRange("tailCameraResolution", 144, 72, 360);
        SPEC = builder.build();
    }

    private AerolargeClientConfig() {
    }
}
