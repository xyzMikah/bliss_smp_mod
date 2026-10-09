package net.mk.blisssmp.block;

import net.mk.blisssmp.BlissSmpMod;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {

    private static Block registerOre(String name, UniformInt xp, Block template, boolean requiresTool) {
        Identifier id = Identifier.fromNamespaceAndPath(BlissSmpMod.MOD_ID, name);

        BlockBehaviour.Properties settings = BlockBehaviour.Properties.ofFullCopy(template)
                .setId(ResourceKey.create(Registries.BLOCK, id));

        if (requiresTool) {
            settings = settings.requiresCorrectToolForDrops();
        }

        Block block = new DropExperienceBlock(xp, settings);

        // Register block
        Registry.register(BuiltInRegistries.BLOCK, id, block);

        // Register BlockItem
        Item.Properties itemSettings = new Item.Properties()
                .setId(ResourceKey.create(Registries.ITEM, id));
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, itemSettings));

        return block;
    }

    // Overworld (deepslate template) - require tool like deepslate
    public static final Block ASTRA_ORE    = registerOre("astra_ore", UniformInt.of(1, 2), Blocks.DEEPSLATE, true);
    public static final Block LIFE_ORE     = registerOre("life_ore", UniformInt.of(1, 2), Blocks.DEEPSLATE, true);
    public static final Block PUFF_ORE     = registerOre("puff_ore", UniformInt.of(1, 2), Blocks.DEEPSLATE, true);
    public static final Block SPEED_ORE    = registerOre("speed_ore", UniformInt.of(1, 2), Blocks.DEEPSLATE, true);
    public static final Block STRENGTH_ORE = registerOre("strength_ore", UniformInt.of(1, 2), Blocks.DEEPSLATE, true);
    public static final Block WEALTH_ORE   = registerOre("wealth_ore", UniformInt.of(1, 2), Blocks.DEEPSLATE, true);

    // End - require tool like end stone
    public static final Block END_STONE_ASTRA_ORE    = registerOre("end_stone_astra_ore", UniformInt.of(1, 2), Blocks.END_STONE, true);
    public static final Block END_STONE_LIFE_ORE     = registerOre("end_stone_life_ore", UniformInt.of(1, 2), Blocks.END_STONE, true);
    public static final Block END_STONE_PUFF_ORE     = registerOre("end_stone_puff_ore", UniformInt.of(1, 2), Blocks.END_STONE, true);
    public static final Block END_STONE_SPEED_ORE    = registerOre("end_stone_speed_ore", UniformInt.of(1, 2), Blocks.END_STONE, true);
    public static final Block END_STONE_STRENGTH_ORE = registerOre("end_stone_strength_ore", UniformInt.of(1, 2), Blocks.END_STONE, true);
    public static final Block END_STONE_WEALTH_ORE   = registerOre("end_stone_wealth_ore", UniformInt.of(1, 2), Blocks.END_STONE, true);

    // Nether
    public static final Block NETHERRACK_ASTRA_ORE    = registerOre("netherrack_astra_ore", UniformInt.of(1, 1), Blocks.NETHERRACK, true);
    public static final Block NETHERRACK_LIFE_ORE     = registerOre("netherrack_life_ore", UniformInt.of(1, 1), Blocks.NETHERRACK, true);
    public static final Block NETHERRACK_PUFF_ORE     = registerOre("netherrack_puff_ore", UniformInt.of(1, 1), Blocks.NETHERRACK, true);
    public static final Block NETHERRACK_SPEED_ORE    = registerOre("netherrack_speed_ore", UniformInt.of(1, 1), Blocks.NETHERRACK, true);
    public static final Block NETHERRACK_STRENGTH_ORE = registerOre("netherrack_strength_ore", UniformInt.of(1, 1), Blocks.NETHERRACK, true);
    public static final Block NETHERRACK_WEALTH_ORE   = registerOre("netherrack_wealth_ore", UniformInt.of(1, 1), Blocks.NETHERRACK, true);

    public static void registerModBlocks() {
        BlissSmpMod.LOGGER.info("Registering ModBlocks for {}", BlissSmpMod.MOD_ID);
    }
}
