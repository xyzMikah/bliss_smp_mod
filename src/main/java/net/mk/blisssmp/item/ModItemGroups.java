package net.mk.blisssmp.item;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.mk.blisssmp.BlissSmpMod;
import net.mk.blisssmp.block.ModBlocks;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public class ModItemGroups {
    public static final ResourceKey<CreativeModeTab> BLISS_SMP_GEMS_KEY = ResourceKey.create(
            Registries.CREATIVE_MODE_TAB,
            Identifier.fromNamespaceAndPath(BlissSmpMod.MOD_ID, "bliss_smp_gems")
    );

    public static final CreativeModeTab BLISS_SMP_GEMS = FabricCreativeModeTab.builder()
            .title(Component.translatable("itemgroup.bliss_smp_gems"))
            .icon(() -> new ItemStack(ModItems.ASTRA_GEM))
            .displayItems((params, output) -> {
                output.accept(ModItems.ASTRA_GEM);
                output.accept(ModItems.FLUX_GEM);
                output.accept(ModItems.PUFF_GEM);
                output.accept(ModItems.LIFE_GEM);
                output.accept(ModItems.SPEED_GEM);
                output.accept(ModItems.WEALTH_GEM);
                output.accept(ModItems.STRENGTH_GEM);
                output.accept(ModItems.FIRE_GEM);

                output.accept(ModItems.RAW_ASTRA);
                output.accept(ModItems.RAW_PUFF);
                output.accept(ModItems.RAW_LIFE);
                output.accept(ModItems.RAW_SPEED);
                output.accept(ModItems.RAW_WEALTH);
                output.accept(ModItems.RAW_STRENGTH);

                output.accept(ModBlocks.ASTRA_ORE);
                output.accept(ModBlocks.PUFF_ORE);
                output.accept(ModBlocks.SPEED_ORE);
                output.accept(ModBlocks.LIFE_ORE);
                output.accept(ModBlocks.WEALTH_ORE);
                output.accept(ModBlocks.STRENGTH_ORE);

                output.accept(ModBlocks.END_STONE_ASTRA_ORE);
                output.accept(ModBlocks.END_STONE_PUFF_ORE);
                output.accept(ModBlocks.END_STONE_SPEED_ORE);
                output.accept(ModBlocks.END_STONE_LIFE_ORE);
                output.accept(ModBlocks.END_STONE_WEALTH_ORE);
                output.accept(ModBlocks.END_STONE_STRENGTH_ORE);

                output.accept(ModBlocks.NETHERRACK_ASTRA_ORE);
                output.accept(ModBlocks.NETHERRACK_PUFF_ORE);
                output.accept(ModBlocks.NETHERRACK_SPEED_ORE);
                output.accept(ModBlocks.NETHERRACK_LIFE_ORE);
                output.accept(ModBlocks.NETHERRACK_WEALTH_ORE);
                output.accept(ModBlocks.NETHERRACK_STRENGTH_ORE);
            })
            .build();

    public static void registerItemGroups() {
        BlissSmpMod.LOGGER.info("Registering Item Groups for " + BlissSmpMod.MOD_ID);
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, BLISS_SMP_GEMS_KEY, BLISS_SMP_GEMS);
        BlissSmpMod.LOGGER.debug("Registered item group: {}", BLISS_SMP_GEMS);
    }
}
