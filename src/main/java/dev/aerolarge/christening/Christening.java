package dev.aerolarge.christening;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.airframe.Livery;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.block.AutopilotBlock;
import dev.aerolarge.block.FlagHalyardBlockEntity;
import dev.aerolarge.block.ShipsPlaqueBlockEntity;
import dev.aerolarge.compat.SableFlight;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.entity.BannerPatterns;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * A zeppelin's christening: a Christening Bottle (see {@link ChristeningBottleItem}) broken on her nose, as a ship's
 * sponsor breaks one on her bow. The glass smashes and the wine sprays with a bang, and a flock of white doves flies
 * off, as the airship Akron's 48 pigeons did when she was christened; and her plaque in the control car (see
 * {@link ShipsPlaqueBlockEntity}) is engraved with the name the bottle was given at an anvil and with her crest, the
 * colours flown from her Sky Deck's flagstaff, or a gold globe on blue where none are, which her flanks are then
 * painted with (see the client's {@code ChristeningPaint}). A new bottle christens her afresh.
 */
public final class Christening {
    /** How far aft of the nose's tip a bottle can be broken on her cover, in blocks. */
    private static final double NOSE_REACH = 30;
    /**
     * How many doves fly off, as the Akron's pigeons did, and how far round where the bottle broke they start, in
     * blocks.
     */
    private static final int DOVES = 48;
    private static final double FLOCK = 1.5;
    /** How many drops of wine spray and splinters of glass fly, and how far, in blocks. */
    private static final int SPRAY = 40;
    private static final double SPRAY_SPREAD = 0.6;
    /**
     * How loud the smash and the bang are (heard 16 blocks off for each), and how far out from the cover the bottle
     * breaks, in blocks.
     */
    private static final float SMASH_VOLUME = 2;
    private static final float BANG_VOLUME = 8;
    private static final double OFF_THE_COVER = 0.6;
    /** The longest name she is given, in letters: longer ones are cut short. */
    private static final int LONGEST_NAME = 32;

    private Christening() {
    }

    /** What came of swinging a bottle at a block. */
    public enum Outcome {
        /** It broke on her nose and she is christened. */
        CHRISTENED,
        /** It was swung at her, but not at her nose. */
        NOT_HER_NOSE,
        /** It was not swung at a zeppelin's cover at all. */
        NOT_A_ZEPPELIN
    }

    /**
     * Swings a bottle at a block, struck at a point, from where the swing came: if the block is a zeppelin's cover by
     * her nose, the bottle breaks there and she is christened.
     */
    public static Outcome christen(ServerLevel level, BlockPos struck, Vec3 at, Vec3 from, Player sponsor, ItemStack bottle) {
        AutopilotBlockEntity zeppelin = AutopilotBlockEntity.zeppelinFraming(level, struck);
        if (zeppelin == null || !level.getBlockState(struck).is(AerolargeRegistry.ZEPPELIN_COVER.get())) {
            return Outcome.NOT_A_ZEPPELIN;
        }
        BoundingBox box = zeppelin.frame();
        Direction nose = zeppelin.getBlockState().getValue(AutopilotBlock.FACING);
        Livery.Spot spot = Livery.Spot.of(struck, new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ()), nose);
        if (spot.along() > NOSE_REACH) {
            return Outcome.NOT_HER_NOSE;
        }
        Vec3 world = SableFlight.toWorld(level, at);
        smash(level, struck, world.add(from.subtract(world).normalize().scale(OFF_THE_COVER)));
        ShipsPlaqueBlockEntity plaque = plaque(level, box);
        if (plaque != null) {
            plaque.christen(name(bottle), colours(level, box), sponsor.getName().getString());
        }
        return Outcome.CHRISTENED;
    }

    /** The name a bottle gives her: what it was named at an anvil, cut short if long; empty for none. */
    public static String name(ItemStack bottle) {
        Component named = bottle.get(DataComponents.CUSTOM_NAME);
        String name = named == null ? "" : named.getString().strip();
        return name.length() > LONGEST_NAME ? name.substring(0, LONGEST_NAME) : name;
    }

    /** Her plaque, in her frame; null for none. */
    @Nullable
    private static ShipsPlaqueBlockEntity plaque(ServerLevel level, BoundingBox frame) {
        for (ShipsPlaqueBlockEntity plaque : ShipsPlaqueBlockEntity.loaded(level)) {
            if (frame.isInside(plaque.getBlockPos())) {
                return plaque;
            }
        }
        return null;
    }

    /** Her crest: the colours flown from her flagstaff, or else a gold globe on blue in a gold border. */
    private static ItemStack colours(ServerLevel level, BoundingBox frame) {
        for (FlagHalyardBlockEntity halyard : FlagHalyardBlockEntity.loaded(level)) {
            if (frame.isInside(halyard.getBlockPos()) && halyard.flying()) {
                return halyard.colours();
            }
        }
        Registry<BannerPattern> patterns = level.registryAccess().registryOrThrow(Registries.BANNER_PATTERN);
        ItemStack crest = new ItemStack(Items.BLUE_BANNER);
        crest.set(DataComponents.BANNER_PATTERNS, new BannerPatternLayers.Builder()
            .add(patterns.getHolderOrThrow(BannerPatterns.GLOBE), DyeColor.YELLOW)
            .add(patterns.getHolderOrThrow(BannerPatterns.BORDER), DyeColor.YELLOW)
            .build());
        return crest;
    }

    /** The bottle breaking at a point in the world: the smash, the bang, the spray of wine and glass, and the doves. */
    private static void smash(ServerLevel level, BlockPos struck, Vec3 at) {
        level.playSound(null, struck, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, SMASH_VOLUME, 1.0F);
        level.playSound(null, struck, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, BANG_VOLUME, 1.0F);
        level.playSound(null, struck, SoundEvents.PARROT_FLY, SoundSource.PLAYERS, SMASH_VOLUME, 0.8F);
        for (ServerPlayer watcher : level.players()) {
            send(level, watcher, AerolargeRegistry.DOVE.get(), at, DOVES, FLOCK);
            send(level, watcher, ParticleTypes.SPLASH, at, SPRAY, SPRAY_SPREAD);
            send(level, watcher, new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.GLASS_BOTTLE)), at, SPRAY / 2, SPRAY_SPREAD);
        }
    }

    /** Particles round a point, to one watcher, however far off they are. */
    private static void send(ServerLevel level, ServerPlayer watcher, ParticleOptions particle, Vec3 at, int count, double spread) {
        level.sendParticles(watcher, particle, true, at.x, at.y, at.z, count, spread, spread, spread, 0.1);
    }
}
