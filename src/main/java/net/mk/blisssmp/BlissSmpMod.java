package net.mk.blisssmp;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.mk.blisssmp.block.ModBlocks;
import net.mk.blisssmp.item.ModItems;
import net.mk.blisssmp.item.ModItemGroups;
import net.mk.blisssmp.command.BlissCommand;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;

import net.mk.blisssmp.item.custom.Astra_Gem_Power;
import net.mk.blisssmp.item.custom.Fire_Gem_Power;
import net.mk.blisssmp.item.custom.Flux_Gem_Power;
import net.mk.blisssmp.item.custom.Life_Gem_Power;
import net.mk.blisssmp.item.custom.Puff_Gem_Power;
import net.mk.blisssmp.item.custom.Speed_Gem_Power;
import net.mk.blisssmp.item.custom.Strength_Gem_Power;
import net.mk.blisssmp.item.custom.Wealth_Gem_Power;
import net.mk.blisssmp.worldgen.ModOreGeneration;

public class BlissSmpMod implements ModInitializer {
    public static final String MOD_ID = "bliss_smp_mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Bliss SMP Mod loaded!");

        ModItems.registerModItems();
        ModBlocks.registerModBlocks();
        ModItemGroups.registerItemGroups();
        ModOreGeneration.registerOreGeneration();

        // ── /bliss cd command ─────────────────────────────────────────────
        BlissCommand.register();

        // ── Per-tick passive effects ──────────────────────────────────────
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerLevel world : server.getAllLevels()) {
                for (Player p : world.players()) {

                    // Astra Gem: sculk orbit + resonance pulse passive
                    Astra_Gem_Power.onPlayerTick(p);

                    // Puff Gem: fall immunity + Jump Boost II passive
                    if (Puff_Gem_Power.hasFallImmunity(p)) {
                        p.fallDistance = 0.0F;
                        if ((p.onGround() || p.isInWater()) && p.getDeltaMovement().y <= 0.0) {
                            Puff_Gem_Power.clearFallImmunity(p);
                            if (p instanceof ServerPlayer sp) {
                                sp.sendSystemMessage(Component.literal("Landed safely!"), true);
                            }
                        }
                    }
                    Puff_Gem_Power.onPlayerTick(p);

                    // Speed Gem: Speed II passive
                    Speed_Gem_Power.onPlayerTick(p);

                    // Fire Gem: Fire Resistance I passive
                    Fire_Gem_Power.onPlayerTick(p);

                    // Flux Gem: Electric Touch + blue orbit particles
                    Flux_Gem_Power.onPlayerTick(p);

                    // Life Gem: Absorption I passive
                    Life_Gem_Power.onPlayerTick(p);

                    // Strength Gem: berserker miss check + death wish expiry
                    Strength_Gem_Power.onPlayerTick(p);
                }
            }
        });

        // ── Offhand melee support ─────────────────────────────────────────
        // hurtEnemy only fires for the main-hand item; this covers offhand gem hits
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide() && entity instanceof LivingEntity target) {
                Astra_Gem_Power.onAttack(player, target);
                Flux_Gem_Power.onAttack(player, target);
                Puff_Gem_Power.onAttack(player, target);
                Life_Gem_Power.onAttack(player, target);
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

        // ── Astra Gem: kill reduces Sonic Boom CD ─────────────────────────
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (damageSource.getEntity() instanceof Player killer) {
                Astra_Gem_Power.onKill(killer);
            }
        });

        // ── Wealth Gem: bonus mining drops ────────────────────────────────
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide()) return;
            boolean holdingWealth =
                    player.getMainHandItem().getItem() instanceof Wealth_Gem_Power ||
                    player.getOffhandItem().getItem() instanceof Wealth_Gem_Power;
            if (!holdingWealth) return;

            ItemStack toolStack = player.getMainHandItem().getItem() instanceof Wealth_Gem_Power
                    ? player.getMainHandItem() : player.getOffhandItem();

            java.util.List<ItemStack> drops = net.minecraft.world.level.block.Block.getDrops(
                    state, (ServerLevel) world, pos, blockEntity, player, toolStack);

            for (ItemStack drop : drops) {
                if (Wealth_Gem_Power.shouldGiveExtraOreDrop()) {
                    ItemEntity entity = new ItemEntity(world,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop.copy());
                    entity.setPickUpDelay(0);
                    world.addFreshEntity(entity);
                }
            }
        });

        // ── Life Gem: death prevention ────────────────────────────────────
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, damageSource, damageAmount) -> {
            if (!(entity instanceof Player player)) return true;

            boolean hasLifeGem =
                    player.getMainHandItem().getItem() instanceof Life_Gem_Power ||
                    player.getOffhandItem().getItem() instanceof Life_Gem_Power;

            if (!hasLifeGem) {
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    if (player.getInventory().getItem(i).getItem() instanceof Life_Gem_Power) {
                        hasLifeGem = true;
                        break;
                    }
                }
            }

            if (hasLifeGem) {
                boolean saved = Life_Gem_Power.preventDeath(player);
                if (saved) {
                    LOGGER.info("Death Save activated for: " + player.getName().getString());
                    return false;
                }
            }
            return true;
        });

        LOGGER.info("Bliss SMP Mod events registered!");
    }
}
