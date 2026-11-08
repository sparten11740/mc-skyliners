package dev.skyliners.stickers;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * A sticker on the sticker map (see {@link StickerMapBlock}): a kind of land a child knows at a glance, each a little
 * picture of its own, in the board's three rows of {@link #ROW} from the top left: the green lands, the warm lands, and
 * the cold, wet and far-off ones, space last. Each is earned by landing in one of its biomes, named here by id or by
 * tag ({@code #} first), the common {@code c:} tags letting other mods' biomes count too; space by climbing to its
 * edge. Saved by name.
 */
public enum Sticker {
    PLAINS("#c:is_plains"),
    FLOWERS("#c:is_floral"),
    FOREST("#minecraft:is_forest", "#c:is_forest"),
    TAIGA("#minecraft:is_taiga", "#c:is_taiga"),
    DARK_FOREST("minecraft:dark_forest"),
    CHERRY_GROVE("minecraft:cherry_grove"),
    DESERT("#c:is_desert"),
    SAVANNA("#minecraft:is_savanna", "#c:is_savanna"),
    BADLANDS("#minecraft:is_badlands", "#c:is_badlands"),
    JUNGLE("#minecraft:is_jungle", "#c:is_jungle"),
    BAMBOO("minecraft:bamboo_jungle"),
    SWAMP("#c:is_swamp"),
    SNOWY("#c:is_snowy", "minecraft:frozen_river", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean"),
    ICE_SPIKES("minecraft:ice_spikes"),
    PEAKS("#c:is_mountain", "#minecraft:is_hill", "#c:is_windswept"),
    SEASIDE("#minecraft:is_ocean", "#minecraft:is_beach", "#minecraft:is_river", "#c:is_stony_shores"),
    MUSHROOM("#c:is_mushroom"),
    SPACE;

    /** How many stickers a row of the board holds, and how many rows it has. */
    public static final int ROW = 6;
    public static final int ROWS = 3;
    /**
     * The order a biome is matched in, the rarest and most particular first, so a biome in two groups gets the sticker
     * that says the most about it: a snowy mountain is a peak, a flower forest flowers, a bamboo jungle bamboo.
     */
    private static final List<Sticker> MATCHED_IN_ORDER = List.of(MUSHROOM, CHERRY_GROVE, ICE_SPIKES, BAMBOO, FLOWERS, DARK_FOREST, BADLANDS,
        DESERT, SAVANNA, JUNGLE, SWAMP, PEAKS, SNOWY, SEASIDE, TAIGA, FOREST, PLAINS);

    /** The biomes that earn it by their ids, and those that earn it by their tags. */
    private final List<ResourceKey<Biome>> biomes;
    private final List<TagKey<Biome>> tags;

    Sticker(String... names) {
        biomes = Arrays.stream(names).filter(name -> !name.startsWith("#"))
            .map(name -> ResourceKey.create(Registries.BIOME, ResourceLocation.parse(name))).toList();
        tags = Arrays.stream(names).filter(name -> name.startsWith("#"))
            .map(name -> TagKey.create(Registries.BIOME, ResourceLocation.parse(name.substring(1)))).toList();
    }

    /** The sticker a biome earns; null for one no sticker shows, such as the Nether's, a cave's or the void. */
    @Nullable
    public static Sticker of(Holder<Biome> biome) {
        for (Sticker sticker : MATCHED_IN_ORDER) {
            if (sticker.matches(biome)) {
                return sticker;
            }
        }
        return null;
    }

    /** Whether a biome earns it: one of its biomes, or in one of its tags. */
    private boolean matches(Holder<Biome> biome) {
        return biomes.stream().anyMatch(biome::is) || tags.stream().anyMatch(biome::is);
    }

    /** Which row of the board it is in, from the top, and where along it, from the left. */
    public int row() {
        return ordinal() / ROW;
    }

    public int column() {
        return ordinal() % ROW;
    }

    /** Its name as saved and in the resources. */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The sticker saved under a name; null for one this build does not have. */
    @Nullable
    public static Sticker byKey(String key) {
        for (Sticker sticker : values()) {
            if (sticker.key().equals(key)) {
                return sticker;
            }
        }
        return null;
    }
}
