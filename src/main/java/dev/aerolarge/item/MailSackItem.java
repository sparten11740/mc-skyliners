package dev.aerolarge.item;

import dev.aerolarge.entity.FallingCrate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A mail sack, dropped from a zeppelin's franking machine over a village (see
 * {@link dev.aerolarge.block.FrankingMachineBlock}) and parachuted down (see {@link FallingCrate}): it lands as a sack
 * on the ground with a soft thump and a flutter of letters, and in a village its bell rings out for the post and the
 * villagers round it brighten, so its coming is seen and heard. Put down by hand it is only a sack.
 */
public class MailSackItem extends BlockItem implements FallingCrate.Landing {
    /** How far from where it lands a village's bell rings for it and villagers brighten, in blocks. */
    private static final int VILLAGE_REACH = 48;
    /** How many letters flutter up as it lands, and how many sparkles over each villager. */
    private static final int LETTERS = 12;
    private static final int SPARKLES = 6;
    /**
     * How loud the village's bell rings for it, as far as it carries a share of it: heard from the zeppelin that
     * dropped it, high over the village; and how far off its letters and the villagers' sparkles are seen, in blocks.
     */
    private static final float BELL_VOLUME = 5;
    private static final double SEEN = 160;

    public MailSackItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void land(ServerLevel level, Vec3 at, ItemStack carried, @Nullable UUID dropper) {
        BlockPos pos = BlockPos.containing(at);
        if (level.getBlockState(pos).canBeReplaced()) {
            level.setBlock(pos, getBlock().defaultBlockState(), Block.UPDATE_ALL);
        } else {
            Block.popResource(level, pos, carried.copyWithCount(1));
        }
        level.playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1.5F, 0.8F);
        List<ServerPlayer> watching = level.getPlayers(player -> player.distanceToSqr(at) < SEEN * SEEN);
        sparkle(level, watching, ParticleTypes.POOF, at.add(0, 0.5, 0), LETTERS, 0.05);
        bell(level, pos).ifPresent(bell -> level.playSound(null, bell, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, BELL_VOLUME, 1.0F));
        for (Villager villager : level.getEntitiesOfClass(Villager.class, new AABB(pos).inflate(VILLAGE_REACH))) {
            sparkle(level, watching, ParticleTypes.HAPPY_VILLAGER, villager.position().add(0, villager.getEyeHeight() + 0.5, 0), SPARKLES, 0);
        }
    }

    /** Particles at a point, seen by each of the players given however far off they are within {@link #SEEN}. */
    private static void sparkle(ServerLevel level, List<ServerPlayer> watching, SimpleParticleType kind, Vec3 at, int count, double speed) {
        for (ServerPlayer player : watching) {
            level.sendParticles(player, kind, true, at.x, at.y, at.z, count, 0.4, 0.4, 0.4, speed);
        }
    }

    /** The village bell nearest where it landed, within reach; none outside a village. */
    private static Optional<BlockPos> bell(ServerLevel level, BlockPos pos) {
        return level.getPoiManager().findClosest(MailSackItem::meeting, pos, VILLAGE_REACH, PoiManager.Occupancy.ANY);
    }

    private static boolean meeting(Holder<PoiType> type) {
        return type.is(PoiTypes.MEETING);
    }
}
