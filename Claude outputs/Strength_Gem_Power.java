package net.mk.blisssmp.item.custom;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class Strength_Gem_Power extends Item {

    // Track entities that are bleeding (simplified: just track UUID → bleed end tick)
    private static final Map<UUID, Long> bleedingEntities = new HashMap<>();
    private static final Map<UUID, Long> lastAbility      = new HashMap<>();

    public Strength_Gem_Power(Item.Settings settings) {
        super(settings);
    }

    // ─── RIGHT-CLICK: Berserker Rage ─────────────────────────────────────────
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));

        long now = world.getTime();
        long last = lastAbility.getOrDefault(player.getUuid(), 0L);
        int cd = CooldownManager.get(CooldownManager.STRENGTH_NORMAL);

        if (now - last < cd) {
            long remaining = (cd - (now - last)) / 20 + 1;
            player.sendMessage(Text.literal("§c[Strength Gem] §cCooldown: " + remaining + "s"), true);
            return TypedActionResult.fail(player.getStackInHand(hand));
        }
        lastAbility.put(player.getUuid(), now);

        // Strength III + Resistance I for 10 seconds
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 2, false, true));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 0, false, true));
        player.sendMessage(Text.literal("§c[Strength Gem] §eBerserker Rage!"), true);

        player.getStackInHand(hand).damage(1, player, LivingEntity.getSlotForHand(hand));
        return TypedActionResult.success(player.getStackInHand(hand));
    }

    // ─── LEFT-CLICK HIT: Bleed ───────────────────────────────────────────────
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof PlayerEntity player)) return false;
        if (attacker.getWorld().isClient) return false;

        // Apply bleeding (modeled as poison)
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 60, 0, false, true));
        bleedingEntities.put(target.getUuid(), target.getWorld().getTime() + 60);

        player.sendMessage(Text.literal("§c[Strength Gem] §eTarget is bleeding!"), true);
        stack.damage(1, player, LivingEntity.getSlotForHand(Hand.MAIN_HAND));
        return true;
    }

    // ─── Bleeding cleanup ─────────────────────────────────────────────────────
    public static void cleanupBleedingEntities(ServerWorld world) {
        long now = world.getTime();
        bleedingEntities.entrySet().removeIf(e -> now > e.getValue());
    }
}
