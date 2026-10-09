package net.mk.blisssmp.item;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.mk.blisssmp.BlissSmpMod;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.mk.blisssmp.item.custom.Astra_Gem_Power;
import net.mk.blisssmp.item.custom.Fire_Gem_Power;
import net.mk.blisssmp.item.custom.Flux_Gem_Power;
import net.mk.blisssmp.item.custom.Life_Gem_Power;
import net.mk.blisssmp.item.custom.Puff_Gem_Power;
import net.mk.blisssmp.item.custom.Speed_Gem_Power;
import net.mk.blisssmp.item.custom.Strength_Gem_Power;
import net.mk.blisssmp.item.custom.Wealth_Gem_Power;

import java.util.function.Function;

public class ModItems {

    // ─── POWER GEMS ──────────────────────────────────────────────────────────
    public static final Item ASTRA_GEM = registerItem("astra_gem",
            settings -> new Astra_Gem_Power(settings.maxDamage(32)));
    public static final Item FIRE_GEM = registerItem("fire_gem",
            settings -> new Fire_Gem_Power(settings.maxDamage(32)));
    public static final Item FLUX_GEM = registerItem("flux_gem",
            settings -> new Flux_Gem_Power(settings.maxDamage(32)));
    public static final Item LIFE_GEM = registerItem("life_gem",
            settings -> new Life_Gem_Power(settings.maxDamage(32)));
    public static final Item PUFF_GEM = registerItem("puff_gem",
            settings -> new Puff_Gem_Power(settings.maxDamage(32)));
    public static final Item SPEED_GEM = registerItem("speed_gem",
            settings -> new Speed_Gem_Power(settings.maxDamage(32)));
    public static final Item STRENGTH_GEM = registerItem("strength_gem",
            settings -> new Strength_Gem_Power(settings.maxDamage(32)));
    public static final Item WEALTH_GEM = registerItem("wealth_gem",
            settings -> new Wealth_Gem_Power(settings.maxDamage(32)));

    // ─── RAW GEMS ────────────────────────────────────────────────────────────
    public static final Item RAW_ASTRA    = registerSimple("raw_astra");
    public static final Item RAW_LIFE     = registerSimple("raw_life");
    public static final Item RAW_PUFF     = registerSimple("raw_puff");
    public static final Item RAW_SPEED    = registerSimple("raw_speed");
    public static final Item RAW_WEALTH   = registerSimple("raw_wealth");
    public static final Item RAW_STRENGTH = registerSimple("raw_strength");

    // ─── REGISTER HELPERS ─────────────────────────────────────────────────────

    private static Item registerSimple(String name) {
        Identifier id = Identifier.of(BlissSmpMod.MOD_ID, name);
        Item.Settings settings = new Item.Settings()
                .registryKey(RegistryKey.of(RegistryKeys.ITEM, id));
        return Registry.register(Registries.ITEM, id, new Item(settings));
    }

    private static Item registerItem(String name, Function<Item.Settings, Item> factory) {
        Identifier id = Identifier.of(BlissSmpMod.MOD_ID, name);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        Item.Settings settings = new Item.Settings().registryKey(key);
        Item item = factory.apply(settings);
        return Registry.register(Registries.ITEM, key, item);
    }

    // ─── CREATIVE TAB ─────────────────────────────────────────────────────────
    private static void addItemsToIngredientItemGroup(FabricItemGroupEntries entries) {
        entries.add(ASTRA_GEM);
        entries.add(FIRE_GEM);
        entries.add(FLUX_GEM);
        entries.add(LIFE_GEM);
        entries.add(PUFF_GEM);
        entries.add(SPEED_GEM);
        entries.add(STRENGTH_GEM);
        entries.add(WEALTH_GEM);

        entries.add(RAW_ASTRA);
        entries.add(RAW_LIFE);
        entries.add(RAW_PUFF);
        entries.add(RAW_SPEED);
        entries.add(RAW_WEALTH);
        entries.add(RAW_STRENGTH);
    }

    public static void registerModItems() {
        BlissSmpMod.LOGGER.info("Registering Mod Items for " + BlissSmpMod.MOD_ID);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS)
                .register(ModItems::addItemsToIngredientItemGroup);
    }
}
