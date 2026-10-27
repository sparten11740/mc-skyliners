package dev.aerolarge.client;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.airframe.Livery;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.Craft;
import dev.aerolarge.block.PropellerBlockEntity;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Sleigh bells with a festive zeppelin's engines (see {@link Livery#FESTIVE}): while its propellers turn they ring now
 * and then, the faster they turn the more often, each time a little higher or lower. Aboard, they are heard all over
 * the ship, as if in the air round whoever listens, since the engine cars are far aft of most of it; on the ground
 * they ring from its fastest propeller as it flies over. Only this client hears them.
 */
public final class SleighBells {
    /** How fast its propellers must turn to ring them, a share of full speed. */
    private static final float LEAST_SPIN = 0.2F;
    /** How often they ring with its engines at full speed, on average, in ticks. */
    private static final int AT_FULL_SPEED_EVERY = 50;
    /** How loud they are heard aboard and from the ground (16 blocks off for each), and how far their pitch strays. */
    private static final float ABOARD = 0.6F;
    private static final float FROM_THE_ENGINES = 6F;
    private static final float PITCH_STRAY = 0.08F;

    private static final RandomSource RANDOM = RandomSource.create();

    private SleighBells() {
    }

    /** Once a tick: each festive zeppelin whose engines turn may ring them. */
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        LocalPlayer player = Minecraft.getInstance().player;
        if (level == null || player == null || Minecraft.getInstance().isPaused()) {
            return;
        }
        for (AutopilotBlockEntity autopilot : AutopilotBlockEntity.loaded(level)) {
            BoundingBox box = autopilot.craft() == Craft.ZEPPELIN && autopilot.livery() == Livery.FESTIVE ? autopilot.frame() : null;
            PropellerBlockEntity fastest = box == null ? null : fastest(level, box);
            if (fastest != null && RANDOM.nextFloat() * AT_FULL_SPEED_EVERY < fastest.spin()) {
                ring(level, player, autopilot, fastest);
            }
        }
    }

    /** The fastest turning of the propellers in a zeppelin's box, turning fast enough to ring them; null for none. */
    @Nullable
    private static PropellerBlockEntity fastest(ClientLevel level, BoundingBox box) {
        PropellerBlockEntity fastest = null;
        for (PropellerBlockEntity propeller : PropellerBlockEntity.loaded(level)) {
            if (box.isInside(propeller.getBlockPos()) && propeller.spin() >= LEAST_SPIN && (fastest == null || propeller.spin() > fastest.spin())) {
                fastest = propeller;
            }
        }
        return fastest;
    }

    /**
     * Rings them: round the player aboard, or else at the propeller where it is in the world (aboard a zeppelin in
     * flight its block is in Sable's plot, where a sound played on this client alone would be heard by nobody).
     */
    private static void ring(ClientLevel level, LocalPlayer player, AutopilotBlockEntity autopilot, PropellerBlockEntity propeller) {
        float pitch = 1 + (RANDOM.nextFloat() * 2 - 1) * PITCH_STRAY;
        if (SableClient.isAboard(player, autopilot)) {
            level.playLocalSound(player.getX(), player.getEyeY(), player.getZ(), AerolargeRegistry.SLEIGH_BELLS.get(), SoundSource.BLOCKS, ABOARD, pitch, false);
            return;
        }
        Vec3 at = SableClient.toWorld(level, Vec3.atCenterOf(propeller.getBlockPos()));
        level.playLocalSound(at.x, at.y, at.z, AerolargeRegistry.SLEIGH_BELLS.get(), SoundSource.BLOCKS, FROM_THE_ENGINES, pitch, false);
    }
}
