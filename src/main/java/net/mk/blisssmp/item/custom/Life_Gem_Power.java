package net.mk.blisssmp.item.custom;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * LIFE GEM
 *
 * Passive  – Absorption I while held.
 *
 * Active 1 – Life Steal (left-hit):
 *            Deals magic damage to target; banks stolen HP as diamonds. Crit if falling. CD = 3s.
 *            Strong mobs (10+ HP): full credit. Weak mobs: 25% credit.
 *
 * Active 2 – Blood Heal (right-click):
 *            Spends 2 hearts (4 HP) from your stolen bank to heal yourself. No cooldown.
 *            Empty bank = no heal.
 *
 * Death Save – When you would die with the gem anywhere in inventory:
 *              Restores to 5 hearts + Absorption + Regen. CD = 10 min.
 *
 * HUD: left = Life Steal CD | right = bank diamonds (◆ per heart) | ✦ = death save ready
 */
public class Life_Gem_Power extends Item {

    private static final float DEATH_SAVE_HP    = 10.0f; // restore to 5 hearts
    private static final float STEAL_VS_MOB     = 4.0f;  // 2 hearts damage
    private static final float STEAL_VS_PLAYER  = 3.0f;  // 1.5 hearts damage
    private static final float HEAL_PER_USE     = 4.0f;  // 2 hearts healed per RC
    private static final float BANK_COST        = 4.0f;  // 4 HP drained from bank per RC

    private static final float WEAK_MOB_HP      = 10.0f; // max HP threshold for "weak" mobs
    private static final float WEAK_STEAL_MULT  = 0.25f; // 25% bank credit vs weak mobs
    private static final float MAX_BANK_HP      = 40.0f; // 20 hearts max bank

    private static final Map<UUID, Long>  lastSteal  = new HashMap<>();
    private static final Map<UUID, Long>  lastSave   = new HashMap<>();
    private static final Map<UUID, Float> bank       = new HashMap<>(); // stolen HP bank

    public Life_Gem_Power(Properties settings) { super(settings); }

    private static void overlay(Player p, String msg) {
        if (p instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal(msg), true);
    }

    private static boolean hasGem(Player p) {
        return p.getMainHandItem().getItem() instanceof Life_Gem_Power
            || p.getOffhandItem().getItem() instanceof Life_Gem_Power;
    }

    // ── Passive tick ──────────────────────────────────────────────────────
    public static void onPlayerTick(Player player) {
        if (player.level().isClientSide()) return;
        if (!hasGem(player)) return;

        ServerLevel world = (ServerLevel) player.level();
        long now = world.getGameTime();
        UUID pid = player.getUUID();

        // Absorption I — refresh before it expires
        if (!player.hasEffect(MobEffects.ABSORPTION) ||
                player.getEffect(MobEffects.ABSORPTION).getDuration() < 40) {
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 80, 0, false, false));
        }

        // Green heart particles every 5 ticks
        if (now % 5 == 0) {
            double angle = Math.toRadians((now * 15) % 360);
            world.sendParticles(ParticleTypes.HEART,
                    player.getX() + Math.cos(angle), player.getY() + 1.2, player.getZ() + Math.sin(angle),
                    1, 0.05, 0.05, 0.05, 0);
        }

        // ── HUD ──────────────────────────────────────────────────────────
        int stealCd = CooldownManager.lifeSteal();
        Long stealLast = lastSteal.get(pid);
        boolean stealReady = stealCd == 0 || stealLast == null || (now - stealLast) >= stealCd;

        // Left — Life Steal CD
        String leftText;
        if (!stealReady) {
            long rem = stealCd - (now - stealLast);
            leftText = "§c" + (rem + 19) / 20 + "s";
        } else {
            leftText = "§aReady";
        }

        // Death Save status
        Long saveLast = lastSave.get(pid);
        int saveCd = CooldownManager.lifeSave();
        boolean saveReady = saveCd == 0 || saveLast == null || (now - saveLast) >= saveCd;
        String saveText = saveReady ? "§a✦" : "§c✦";

        // Bank diamonds — 1 diamond per 2 HP (1 heart) in bank, max 20
        float banked = bank.getOrDefault(pid, 0f);
        int diamonds = Math.min((int)(banked / 2f), 20);
        String bankText = diamonds > 0 ? "§b" + "◆".repeat(diamonds) : "§7empty";

        overlay(player, String.format("%-12s%s  %s", leftText, saveText, bankText));
    }

    // ── Right-click: Blood Heal (spends from bank) ───────────────────────
    @Override
    public InteractionResult use(Level world, Player player, InteractionHand hand) {
        boolean mainHasGem = player.getMainHandItem().getItem() instanceof Life_Gem_Power;
        if (mainHasGem && hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!world.isClientSide()) doBloodHeal(world, player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        boolean mainHasGem = player.getMainHandItem().getItem() instanceof Life_Gem_Power;
        if (mainHasGem && ctx.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!ctx.getLevel().isClientSide()) doBloodHeal(ctx.getLevel(), player);
        return InteractionResult.SUCCESS;
    }

    private static void doBloodHeal(Level world, Player player) {
        if (world.isClientSide()) return;
        UUID pid = player.getUUID();

        float banked = bank.getOrDefault(pid, 0f);
        if (banked < BANK_COST) {
            overlay(player, "§cNo blood in the bank! Steal some hearts first.");
            return;
        }

        // Spend from bank, heal player
        bank.put(pid, banked - BANK_COST);
        player.heal(HEAL_PER_USE);

        if (world instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART,
                    player.getX(), player.getY() + 1, player.getZ(), 10, 0.4, 0.4, 0.4, 0.1);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_DRINK, player.getSoundSource(), 1f, 1.2f);

        float remaining = bank.getOrDefault(pid, 0f);
        int heartsLeft = (int)(remaining / 2f);
        overlay(player, "§a+2 hearts! §7(" + heartsLeft + " hearts in bank)");
    }

    // ── Left-click hit: Life Steal ────────────────────────────────────────
    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof Player player)) return;
        doLifeSteal(player, target);
    }

    // Offhand support — called from BlissSmpMod AttackEntityCallback
    public static void onAttack(Player player, LivingEntity target) {
        if (!hasGem(player)) return;
        if (player.getMainHandItem().getItem() instanceof Life_Gem_Power) return;
        doLifeSteal(player, target);
    }

    private static void doLifeSteal(Player player, LivingEntity target) {
        Level world = player.level();
        if (world.isClientSide()) return;
        long now = world.getGameTime();
        UUID pid = player.getUUID();
        int cd = CooldownManager.lifeSteal();

        Long last = lastSteal.get(pid);
        if (cd != 0 && last != null && now - last < cd) return;

        boolean isPlayer = target instanceof Player;
        float amount = isPlayer ? STEAL_VS_PLAYER : STEAL_VS_MOB;

        // Crit if falling
        boolean crit = !player.onGround() && player.getDeltaMovement().y < 0;
        if (crit) amount *= 1.5f;

        // Weak mob check — still steal, but reduced diamond credit
        boolean weakMob = !isPlayer && target.getMaxHealth() < WEAK_MOB_HP;
        float creditAmount = weakMob ? amount * WEAK_STEAL_MULT : amount;

        target.hurtServer((ServerLevel) world, world.damageSources().magic(), amount);
        player.heal(amount * 0.5f);

        // Add to bank (capped at max)
        float current = bank.getOrDefault(pid, 0f);
        bank.put(pid, Math.min(current + creditAmount, MAX_BANK_HP));

        if (world instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.HEART,
                    player.getX(), player.getY() + 1, player.getZ(), 5, 0.3, 0.3, 0.3, 0.05);
        }
        world.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.GENERIC_DRINK, target.getSoundSource(), 1f, 0.8f);

        if (crit) overlay(player, "§aLife Steal §6CRIT§a! +" + (amount * 0.5f) + " HP");
        else overlay(player, "§aLife Steal! +" + (amount * 0.5f) + " HP");

        if (cd != 0) lastSteal.put(pid, now);
    }

    // ── Death Save — called from BlissSmpMod ALLOW_DEATH ─────────────────
    public static boolean preventDeath(Player player) {
        if (player.level().isClientSide()) return false;
        long now = player.level().getGameTime();
        UUID pid = player.getUUID();
        int cd = CooldownManager.lifeSave();

        Long last = lastSave.get(pid);
        if (cd != 0 && last != null && now - last < cd) return false;

        lastSave.put(pid, now);
        bank.remove(pid); // wipe bank on death save
        player.setHealth(DEATH_SAVE_HP);
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 1, false, false));

        if (player instanceof ServerPlayer sp)
            sp.sendSystemMessage(Component.literal("§a★ Death Save activated! ★"), false);
        return true;
    }
}
