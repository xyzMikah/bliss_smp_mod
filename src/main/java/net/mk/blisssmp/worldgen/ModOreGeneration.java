package net.mk.blisssmp.worldgen;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.mk.blisssmp.BlissSmpMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import java.util.Set;

public class ModOreGeneration {

    private static final String[] GEMS = {"life", "puff", "speed", "strength", "wealth"};
    private static final String[] ALL_GEMS = {"astra", "life", "puff", "speed", "strength", "wealth"};

    // Outer End biomes — everything except the main island (Biomes.THE_END)
    private static final Set<ResourceKey<Biome>> OUTER_END_BIOMES = Set.of(
            Biomes.END_HIGHLANDS,
            Biomes.END_MIDLANDS,
            Biomes.END_BARRENS,
            Biomes.SMALL_END_ISLANDS
    );

    public static void registerOreGeneration() {
        // ─── OVERWORLD: deepslate ores ───────────────────────────────────────
        for (String gem : GEMS) {
            Identifier id = Identifier.fromNamespaceAndPath(BlissSmpMod.MOD_ID, gem + "_ore");
            ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE, id);
            BiomeModifications.addFeature(
                    BiomeSelectors.foundInOverworld(),
                    GenerationStep.Decoration.UNDERGROUND_ORES,
                    key
            );
        }


        // ─── ASTRA: single block, deep dark only (ancient city depth) ─────────
        {
            Identifier astraId = Identifier.fromNamespaceAndPath(BlissSmpMod.MOD_ID, "astra_ore");
            ResourceKey<PlacedFeature> astraKey = ResourceKey.create(Registries.PLACED_FEATURE, astraId);
            BiomeModifications.addFeature(
                    context -> context.getBiomeKey().equals(Biomes.DEEP_DARK),
                    GenerationStep.Decoration.UNDERGROUND_ORES,
                    astraKey
            );
        }


                // ─── NETHER: netherrack ores (near lava level) ──────────────────────
        for (String gem : ALL_GEMS) {
            Identifier id = Identifier.fromNamespaceAndPath(BlissSmpMod.MOD_ID, "netherrack_" + gem + "_ore");
            ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE, id);
            BiomeModifications.addFeature(
                    BiomeSelectors.foundInTheNether(),
                    GenerationStep.Decoration.UNDERGROUND_ORES,
                    key
            );
        }

        // ─── END: outer islands only (not the main island) ──────────────────
        for (String gem : ALL_GEMS) {
            Identifier id = Identifier.fromNamespaceAndPath(BlissSmpMod.MOD_ID, "end_stone_" + gem + "_ore");
            ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE, id);
            BiomeModifications.addFeature(
                    context -> OUTER_END_BIOMES.contains(context.getBiomeKey()),
                    GenerationStep.Decoration.UNDERGROUND_ORES,
                    key
            );
        }

        BlissSmpMod.LOGGER.info("Ore generation registered for " + BlissSmpMod.MOD_ID);
    }
}
