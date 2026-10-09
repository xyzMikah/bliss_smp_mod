package net.mk.blisssmp.item;

import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.mk.blisssmp.BlissSmpMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
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

    // ------------------- ITEMS -------------------
    public static final Item ASTRA_GEM = registerItem("astra_gem",
            settings -> new Astra_Gem_Power(settings.durability(32)));
    public static final Item FLUX_GEM = registerItem("flux_gem",
            settings -> new Flux_Gem_Power(settings.durability(32)));
    public static final Item LIFE_GEM = registerItem("life_gem",
            settings -> new Life_Gem_Power(settings.durability(32)));
    public static final Item PUFF_GEM = registerItem("puff_gem",
            settings -> new Puff_Gem_Power(settings.durability(32)));
    public static final Item SPEED_GEM = registerItem("speed_gem",
            settings -> new Speed_Gem_Power(settings.durability(32)));
    public static final Item WEALTH_GEM = registerItem("wealth_gem",
            settings -> new Wealth_Gem_Power(settings.durability(32)));
    public static final Item STRENGTH_GEM = registerItem("strength_gem",
            settings -> new Strength_Gem_Power(settings.durability(32)));
    public static final Item FIRE_GEM = registerItem("fire_gem",
            settings -> new Fire_Gem_Power(settings.durability(32)));

    public static final Item RAW_ASTRA     = registerSimple("raw_astra");
    public static final Item RAW_LIFE      = registerSimple("raw_life");
    public static final Item RAW_PUFF      = registerSimple("raw_puff");
    public static final Item RAW_SPEED     = registerSimple("raw_speed");
    public static final Item RAW_WEALTH    = registerSimple("raw_wealth");
    public static final Item RAW_STRENGTH  = registerSimple("raw_strength");

    // ------------------- REGISTER HELPERS -------------------

    private static Item registerSimple(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(BlissSmpMod.MOD_ID, name);
        Item.Properties settings = new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, id));
        return Registry.register(BuiltInRegistries.ITEM, id, new Item(settings));
    }

    private static Item registerItem(String name, Function<Item.Properties, Item> factory) {
        Identifier id = Identifier.fromNamespaceAndPath(BlissSmpMod.MOD_ID, name);
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, id);
        Item.Properties settings = new Item.Properties().setId(key);
        Item item = factory.apply(settings);
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    // ------------------- CREATIVE TAB -------------------
    public static void registerModItems() {
        BlissSmpMod.LOGGER.info("Registering Mod Items for " + BlissSmpMod.MOD_ID);
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.INGREDIENTS)
                .register(entries -> {
                    entries.accept(ASTRA_GEM);
                    entries.accept(PUFF_GEM);
                    entries.accept(LIFE_GEM);
                    entries.accept(SPEED_GEM);
                    entries.accept(WEALTH_GEM);
                    entries.accept(STRENGTH_GEM);

                    entries.accept(RAW_ASTRA);
                    entries.accept(RAW_PUFF);
                    entries.accept(RAW_LIFE);
                    entries.accept(RAW_SPEED);
                    entries.accept(RAW_WEALTH);
                    entries.accept(RAW_STRENGTH);
                });
    }
}
