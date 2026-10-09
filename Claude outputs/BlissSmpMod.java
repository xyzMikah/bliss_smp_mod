package net.mk.blisssmp;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.mk.blisssmp.block.ModBlocks;
import net.mk.blisssmp.item.ModItems;
import net.mk.blisssmp.item.ModItemGroups;
import net.mk.blisssmp.command.BlissCommand;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;

import net.minecraft.block.BlockState;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import net.mk.blisssmp.item.custom.Fire_Gem_Power;
import net.mk.blisssmp.item.custom.Flux_Gem_Power;
import net.mk.blisssmp.item.custom.Life_Gem_Power;
import net.mk.blisssmp.item.custom.Puff_Gem_Power;
import net.mk.blisssmp.item.custom.Speed_Gem_Power;
import net.mk.blisssmp.item.custom.Strength_Gem_Power;
import net.mk.blisssmp.item.custom.Wealth_Gem_Power;

public class BlissSmpMod implements ModInitializer {
    public static final String MOD_ID = "bliss_smp_mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Bliss SMP Mod loaded!");

        ModItems.registerModItems();
        ModBlocks.registerModBlocks();
        ModItemGroups.registerItemGroups();

        // ── /bliss cd command ──────────────────────────────────────────────
        BlissCommand.register();

        // ── Per-tick passive effects ──────────────────────────────────────
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerWorld world : server.getWorlds()) {
                for (PlayerEntity p : world.getPlayers()) {

                    // ── Puff Gem: fall damage immunity tracking ──────────────
                    if (Puff_Gem_Power.hasFallDamageImmunity(p)) {
                        p.fallDistance = 0.0F;
                        if ((p.isOnGround() || p.isTouchingWater()) && p.getVelocity().y <= 0.0) {
                            Puff_Gem_Power.clearFallImmunity(p);
                            p.sendMessage(Text.literal("Landed safely!"), true);
                        }
                    }

                    // ── Puff Gem: Jump Boost II passive ─────────────────────
                    Puff_Gem_Power.onPlayerTick(p);

                    // ── Speed Gem: Speed II passive ──────────────────────────
                    Speed_Gem_Power.onPlayerTick(p);

                    // ── Fire Gem: Fire Resistance I passive ──────────────────
                    Fire_Gem_Power.onPlayerTick(p);

                    // ── Flux Gem: Electric Touch + blue particles ─────────────
                    Flux_Gem_Power.onPlayerTick(p);

                    // ── Life Gem: Absorption I passive ───────────────────────
                    boolean holdingLifeGem =
                            p.getMainHandStack().getItem() instanceof Life_Gem_Power ||
                            p.getOffHandStack().getItem() instanceof Life_Gem_Power;
                    Life_Gem_Power.applyPassiveEffects(p, holdingLifeGem);
                }

                // ── Strength Gem: bleed entity cleanup (every second) ────────
                if (server.getTicks() % 20 == 0) {
                    Strength_Gem_Power.cleanupBleedingEntities(world);
                }
            }
        });

        // ── Wealth Gem: bonus mining drops ────────────────────────────────
        PlayerBlockBreakEvents.AFTER.register(
                (world, player, pos, state, blockEntity) -> {
                    if (world.isClient) return;
                    boolean holdingWealth =
                            player.getMainHandStack().getItem() instanceof Wealth_Gem_Power ||
                            player.getOffHandStack().getItem() instanceof Wealth_Gem_Power;
                    if (!holdingWealth) return;

                    // Get bonus drop count (Fortune III equivalent: 1-4 extra)
                    int bonus = Wealth_Gem_Power.getBonusDropCount();
                    boolean duplicate = Wealth_Gem_Power.shouldDuplicateOre();

                    // Spawn bonus copies of the primary block's drops
                    ItemStack mainDrop = new ItemStack(state.getBlock().asItem());
                    if (mainDrop.isEmpty()) return;

                    for (int i = 0; i < bonus; i++) {
                        ItemEntity extra = new ItemEntity(
                                world,
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                mainDrop.copy());
                        world.spawnEntity(extra);
                    }

                    if (duplicate) {
                        // Give one extra stack directly to inventory
                        ItemStack dupeStack = mainDrop.copy();
                        if (!player.getInventory().insertStack(dupeStack)) {
                            ItemEntity drop = new ItemEntity(
                                    world,
                                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                                    dupeStack);
                            world.spawnEntity(drop);
                        }
                        player.sendMessage(Text.literal("§e[Wealth Gem] §6★ Ore duplicated! ★"), true);
                    }
                });

        // ── Life Gem: death prevention ────────────────────────────────────
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (!(entity instanceof PlayerEntity player)) return true;

            // Check hands and inventory for Life Gem
            boolean hasLifeGem =
                    player.getMainHandStack().getItem() instanceof Life_Gem_Power ||
                    player.getOffHandStack().getItem() instanceof Life_Gem_Power;

            if (!hasLifeGem) {
                for (int i = 0; i < player.getInventory().size(); i++) {
                    if (player.getInventory().getStack(i).getItem() instanceof Life_Gem_Power) {
                        hasLifeGem = true;
                        break;
                    }
                }
            }

            if (hasLifeGem) {
                boolean saved = Life_Gem_Power.preventDeath(player);
                if (saved) {
                    LOGGER.info("Death Save activated for: " + player.getName().getString());
                    return false; // Cancel death
                }
            }
            return true;
        });

        LOGGER.info("Bliss SMP Mod events registered!");
    }
}
