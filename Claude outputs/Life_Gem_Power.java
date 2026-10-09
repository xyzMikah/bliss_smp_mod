package net.mk.blisssmp.item.custom;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Life_Gem_Power extends Item {

    private static final float HEAL_AMOUNT   = 10.0f; // 5 hearts
    private static final float DEATH_SAVE_HP = 10.0f; // restore to 5 hearts

    private static final float STEAL_VS_MOB    = 4.0f; // 2 hearts
    private static final float STEAL_VS_PLAYER = 3.0f; // 1.5 hearts

    private static final Map<UUID, Long> lastHeal  = new HashMap<>();
    private static final Map<UUID, Long> lastSteal = new HashMap<>();
    private static final Map<UUID, Long> lastSave  = new HashMap<>();

    public Life_Gem_Power(Item.Settings settings) {
        super(settings);
    }

    // ─── RIGHT-CLICK: Heal + Cleanse ─────────────────────────────────────────
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));

        long now = world.getTime();
        long last = lastHeal.getOrDefault(player.getUuid(), 0L);
        int cd = CooldownManager.lifeHeal();

        if (now - last < cd) {
            long remaining = (cd - (now - last)) / 20 + 1;
            player.sendMessage(Text.literal("§a[Life Gem] §cHeal cooldown: " + remaining + "s"), true);
            return TypedActionResult.fail(player.getStackInHand(hand));
        }
        lastHeal.put(player.getUuid(), now);

        // Heal 10 HP (5 hearts)
        player.heal(HEAL_AMOUNT);

        // Cleanse debuffs
        player.removeStatusEffect(StatusEffects.POISON);
        player.removeStatusEffect(StatusEffects.WITHER);
        player.removeStatusEffect(StatusEffects.NAUSEA);
        player.removeStatusEffect(StatusEffects.BLINDNESS);

        player.sendMessage(Text.literal("§a[Life Gem] §eHealed and cleansed!"), true);
        player.getStackInHand(hand).damage(1, player, LivingEntity.getSlotForHand(hand));
        return TypedActionResult.success(player.getStackInHand(hand));
    }

    // ─── LEFT-CLICK HIT: Life Steal ──────────────────────────────────────────
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof PlayerEntity player)) return false;
        if (attacker.getWorld().isClient) return false;

        long now = player.getWorld().getTime();
        long last = lastSteal.getOrDefault(player.getUuid(), 0L);
        int cd = CooldownManager.lifeSteal();

        if (now - last < cd) {
            long remaining = (cd - (now - last)) / 20 + 1;
            player.sendMessage(Text.literal("§a[Life Gem] §cSteal cooldown: " + remaining + "s"), true);
            return false;
        }
        lastSteal.put(player.getUuid(), now);

        boolean isPlayer = target instanceof PlayerEntity;
        float stealAmount = isPlayer ? STEAL_VS_PLAYER : STEAL_VS_MOB;

        // Check for critical hit (falling and not on ground)
        boolean crit = !player.isOnGround() && player.getVelocity().y < 0;
        if (crit) stealAmount *= 1.5f;

        // Deal magic damage to target
        target.damage(player.getWorld().getDamageSources().magic(), stealAmount);

        // Heal self
        player.heal(stealAmount * 0.5f); // heal half the damage dealt

        String critMsg = crit ? " §6(CRIT)" : "";
        player.sendMessage(Text.literal("§a[Life Gem] §eStealed §c" + stealAmount + "§e HP!" + critMsg), true);

        stack.damage(1, player, LivingEntity.getSlotForHand(Hand.MAIN_HAND));
        return true;
    }

    // ─── PASSIVE: Absorption I while holding ─────────────────────────────────
    public static void applyPassiveEffects(PlayerEntity player, boolean holding) {
        if (player.getWorld().isClient) return;
        if (holding) {
            StatusEffectInstance current = player.getStatusEffect(StatusEffects.ABSORPTION);
            if (current == null || current.getDuration() < 40) {
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.ABSORPTION, 80, 0, false, false));
            }
        }
    }

    // ─── DEATH SAVE ───────────────────────────────────────────────────────────
    public static boolean preventDeath(PlayerEntity player) {
        if (player.getWorld().isClient) return false;

        long now = player.getWorld().getTime();
        long last = lastSave.getOrDefault(player.getUuid(), 0L);
        int cd = CooldownManager.lifeSave();

        if (now - last < cd && last != 0) return false; // Death save on cooldown

        lastSave.put(player.getUuid(), now);

        // Set HP to DEATH_SAVE_HP (5 hearts)
        player.setHealth(DEATH_SAVE_HP);

        // Buff them briefly
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 0, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1, false, false));

        player.sendMessage(Text.literal("§a[Life Gem] §6★ Death Save activated! ★"), false);
        return true;
    }
}
