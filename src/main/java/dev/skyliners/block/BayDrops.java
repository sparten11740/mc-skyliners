package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.Ultrasplosive;
import dev.skyliners.entity.FallingCrate;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * How each kind of load leaves the drop bay. A care package falls as a crate and opens its parachute, Ultrasplosive's
 * nuke leaves as its flying nuke (only from high enough to leave its blast behind), TNT leaves lit, and anything else
 * falls as itself, an item to pick up where it lands. Every drop is made where the airliner is seen in the world,
 * moving as the airliner moves there, never among its blocks in Sable's plot: Sable puts out whatever is added there
 * at once, without the airliner's speed, and in flight it would stop dead in the air.
 */
final class BayDrops {
    /**
     * The least height above the ground a nuke is dropped from, in blocks: its blast wave reaches 160, and the airliner
     * flies on a good way while the bomb falls.
     */
    private static final int SAFE_NUKE_HEIGHT = 200;
    /** The loads that leave their own way, looked at in turn; anything none of them takes falls as an item. */
    private static final List<Special> SPECIALS = List.of(
        new Special(stack -> stack.is(SkylinersRegistry.CARGO_CRATE_ITEM.get()), BayDrops::crate),
        new Special(Ultrasplosive::isNuke, BayDrops::nuke),
        new Special(stack -> stack.is(Items.TNT), BayDrops::tnt));

    private BayDrops() {
    }

    /** How a kind of load goes. */
    @FunctionalInterface
    private interface Drop {
        /**
         * Sends one load (a stack of one) out from the point given, moving as given (blocks a tick), dropped by whoever
         * is given: why it cannot go, or null once it has gone.
         */
        @Nullable
        Component send(ServerLevel level, Vec3 at, Vec3 velocity, ItemStack load, @Nullable Player by);
    }

    /** A kind of load that leaves its own way: what it takes, and how it goes. */
    private record Special(Predicate<ItemStack> takes, Drop drop) {
    }

    /** Sends one load out the way its kind goes (see {@link Drop#send}): why it cannot go, or null once it has gone. */
    @Nullable
    static Component drop(ServerLevel level, Vec3 at, Vec3 velocity, ItemStack load, @Nullable Player by) {
        Drop drop = BayDrops::item;
        for (Special special : SPECIALS) {
            if (special.takes().test(load)) {
                drop = special.drop();
                break;
            }
        }
        return drop.send(level, at, velocity, load, by);
    }

    /** A care package: a crate falling, its parachute opening once it is clear. */
    @Nullable
    private static Component crate(ServerLevel level, Vec3 at, Vec3 velocity, ItemStack load, @Nullable Player by) {
        level.addFreshEntity(FallingCrate.of(level, at, velocity, load, by == null ? null : by.getUUID()));
        return null;
    }

    /** A nuke: Ultrasplosive's flying nuke, only from high enough over the ground. */
    @Nullable
    private static Component nuke(ServerLevel level, Vec3 at, Vec3 velocity, ItemStack load, @Nullable Player by) {
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(at.x), Mth.floor(at.z));
        if (at.y - ground < SAFE_NUKE_HEIGHT) {
            return Component.translatable("message.skyliners.drop_bay.too_low", SAFE_NUKE_HEIGHT);
        }
        return Ultrasplosive.drop(level, at, velocity, by) ? null : Component.translatable("message.skyliners.drop_bay.no_bomb");
    }

    /** TNT: lit as it leaves, on the fuse TNT lit any other way burns. */
    @Nullable
    private static Component tnt(ServerLevel level, Vec3 at, Vec3 velocity, ItemStack load, @Nullable Player by) {
        PrimedTnt tnt = new PrimedTnt(level, at.x, at.y, at.z, by);
        tnt.setDeltaMovement(velocity);
        level.addFreshEntity(tnt);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 1.0F);
        return null;
    }

    /** Anything else: the item itself, falling, to be picked up where it lands. */
    @Nullable
    private static Component item(ServerLevel level, Vec3 at, Vec3 velocity, ItemStack load, @Nullable Player by) {
        ItemEntity item = new ItemEntity(level, at.x, at.y, at.z, load, velocity.x, velocity.y, velocity.z);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
        return null;
    }
}
