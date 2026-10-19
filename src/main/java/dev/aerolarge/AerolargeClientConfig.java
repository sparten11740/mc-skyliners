package dev.aerolarge;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client settings: what each player feels aboard the airliner. */
public final class AerolargeClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CAMERA_SHAKE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        CAMERA_SHAKE = builder
            .comment("Shake the camera aboard the airliner: rumbling down the runway, thumping onto it, swaying in flight.")
            .define("cameraShake", true);
        SPEC = builder.build();
    }

    private AerolargeClientConfig() {
    }
}
