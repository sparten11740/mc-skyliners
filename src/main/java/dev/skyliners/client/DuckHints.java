package dev.skyliners.client;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.FirstFlight;
import dev.skyliners.block.RubberDuckBlock;
import dev.skyliners.block.RubberDuckBlockEntity;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.CriterionProgress;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientAdvancements;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The hint a hidden rubber duck gives the player who has not found it yet: while they are within {@link #RANGE}
 * blocks, every few seconds it squeaks softly and wiggles, with a note over it, each duck to its own beat. A squeak
 * and a wiggle a child can follow without reading, getting louder as they get nearer, as sounds do. Whether the player
 * has found it is told by the server with the duck, for its airliner's own hunt; once found, it falls quiet for them
 * only, and aboard that airliner only.
 */
public final class DuckHints {
    /** How near the player has to be for a hidden duck to give its hint, in blocks. */
    private static final double RANGE = 6;
    /** How often a hidden duck squeaks its hint, in ticks. */
    private static final int PERIOD = 60;
    /** The hint's squeak: soft, and a little higher than a squeeze's. */
    private static final float VOLUME = 0.35F;
    private static final float PITCH = 1.25F;
    /** How high over the duck's block the note starts, and its colour along the note particle's scale (0 to 1). */
    private static final double NOTE_HEIGHT = 0.7;
    private static final double NOTE_COLOUR = 0.25;

    private DuckHints() {
    }

    /** Each tick of a duck on the client: on its beat, the hint, if it is a hidden duck the player is near and has not found. */
    public static void tick(RubberDuckBlockEntity duck) {
        Level level = duck.getLevel();
        BlockPos pos = duck.getBlockPos();
        if (duck.duck() <= 0 || level == null || Math.floorMod(level.getGameTime() + pos.hashCode(), PERIOD) != 0) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        Vec3 middle = Vec3.atCenterOf(pos);
        // Heard where the duck is in the world: aboard an airliner in flight its block is in Sable's plot, where a
        // sound played on this client alone is heard by nobody.
        Vec3 heard = SableClient.toWorld(level, middle);
        if (player == null || player.distanceToSqr(heard) > RANGE * RANGE || found(duck, player)) {
            return;
        }
        duck.wiggle();
        level.playLocalSound(heard.x, heard.y, heard.z, SkylinersRegistry.RUBBER_DUCK_SQUEAK.get(), SoundSource.BLOCKS, VOLUME, PITCH, false);
        level.addParticle(ParticleTypes.NOTE, middle.x, pos.getY() + NOTE_HEIGHT + RubberDuckBlock.lift(duck.getBlockState()), middle.z, NOTE_COLOUR, 0, 0);
    }

    /**
     * Whether the player has found this hidden duck in its airliner's hunt, as the server last told; one hidden before
     * each airliner had a hunt, as their Duck Hunt progress has it.
     */
    private static boolean found(RubberDuckBlockEntity duck, Player player) {
        return duck.hunt() != null ? duck.foundBy(player.getUUID()) : foundEver(duck.duck());
    }

    /** Whether the player has ever found the hidden duck, from 1, as their Duck Hunt progress has it. */
    private static boolean foundEver(int duck) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return false;
        }
        ClientAdvancements advancements = connection.getAdvancements();
        AdvancementHolder hunt = advancements.get(FirstFlight.DUCK_HUNT.id());
        AdvancementProgress progress = hunt == null ? null : advancements.progress.get(hunt);
        CriterionProgress criterion = progress == null ? null : progress.getCriterion(RubberDuckBlockEntity.criterion(duck));
        return criterion != null && criterion.isDone();
    }
}
