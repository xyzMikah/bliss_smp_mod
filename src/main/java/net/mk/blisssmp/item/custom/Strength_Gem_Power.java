package net.mk.blisssmp.item.custom;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * STRENGTH GEM — HIGH RISK, HIGH REWARD. For the best players.
 *
 * Passive  – Glass Cannon: permanent max Strength (Strength V) while holding,
 *            but you take 25% MORE damage from all sources.
 * Active 1 – Berserker Strike (left-click hit with sword — works via hurtEnemy):
 *            Next sword hit does 3x damage. MISS (no hit entity in 0.5s) = Weakness II for 5s.
 *            Cooldown: 10s.
 * Active 2 – Death Wish (right-click): double damage for 8s. If you didn't deal 2x the damage
 *            you took during it → Weakness + Slowness for 8s after. Cooldown: 25s.
 */
public class Strength_Gem_Power extends Item {

    private static final float PASSIVE_DAMAGE_MULT = 1.25f; // +25% damage taken

    private static final Map<UUID, Long>    lastBerserk    = new HashMap<>();
    private static final Map<UUID, Long>    berserkReady   = new HashMap<>(); // when armed (waiting for hit)
    private static final Map<UUID, Long>    lastDeathWish  = new HashMap<>();
    private static final Map<UUID, Long>    deathWishUntil = new HashMap<>();
    private static final Map<UUID, Float>   deathWishDealt = new HashMap<>(); // damage dealt during DW
    private static final Map<UUID, Float>   deathWishTaken = new HashMap<>(); // damage taken during DW

    public Strength_Gem_Power(Properties settings) { super(settings); }
    private static void overlay(Player p, String msg) {
        if (p instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal(msg), true);
    }

    // ── Passive tick ──────────────────────────────────────────────────────
    public static void onPlayerTick(Player player) {
        if (player.level().isClientSide()) return;
        boolean holding = player.getMainHandItem().getItem() instanceof Strength_Gem_Power
                       || player.getOffhandItem().getItem() instanceof Strength_Gem_Power;
        if (!holding) return;

        long now = player.level().getGameTime();
        // Max Strength (Strength V = amplifier 4)
        if (now % 40 == 0) {
            if (!player.hasEffect(MobEffects.STRENGTH) || player.getEffect(MobEffects.STRENGTH).getDuration() < 60)
                player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 80, 4, false, false));
        }

        // Berserk miss check: if armed for > 10 ticks (0.5s) with no hit, apply weakness
        Long armed = berserkReady.get(player.getUUID());
        if (armed != null && now - armed > 10) {
            berserkReady.remove(player.getUUID());
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1, false, true));
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,  40, 0, false, true));
            overlay(player, "§4[Strength Gem] §cMissed! Weakness II for 5s.");
        }

        // Death Wish expiry check
        Long dwEnd = deathWishUntil.get(player.getUUID());
        if (dwEnd != null && now >= dwEnd) {
            deathWishUntil.remove(player.getUUID());
            float dealt = deathWishDealt.getOrDefault(player.getUUID(), 0f);
            float taken = deathWishTaken.getOrDefault(player.getUUID(), 0f);
            deathWishDealt.remove(player.getUUID());
            deathWishTaken.remove(player.getUUID());

            if (taken > 0 && dealt < taken * 2) {
                // Failed: didn't deal 2x what you took
                player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1, false, true));
                player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 160, 1, false, true));
                overlay(player, "§4[Strength Gem] §cDeath Wish FAILED! Dealt " + (int)dealt + " hp, took " + (int)taken + " hp → punished!");
            } else {
                overlay(player, "§4[Strength Gem] §aDeath Wish success! Dealt " + (int)dealt + " / took " + (int)taken);
            }
        }
    }

    // ── API: record damage dealt/taken during Death Wish ─────────────────
    public static void recordDeathWishDamageDealt(Player player, float amount) {
        if (deathWishUntil.containsKey(player.getUUID()))
            deathWishDealt.merge(player.getUUID(), amount, Float::sum);
    }
    public static void recordDeathWishDamageTaken(Player player, float amount) {
        if (deathWishUntil.containsKey(player.getUUID()))
            deathWishTaken.merge(player.getUUID(), amount, Float::sum);
    }

    // ── API: is Death Wish active ─────────────────────────────────────────
    public static boolean isDeathWishActive(Player player) {
        Long end = deathWishUntil.get(player.getUUID());
        return end != null && player.level().getGameTime() < end;
    }

    // ── API: does this player take +25% damage ────────────────────────────
    public static boolean hasGlassCannon(Player player) {
        return player.getMainHandItem().getItem() instanceof Strength_Gem_Power
            || player.getOffhandItem().getItem() instanceof Strength_Gem_Power;
    }

    // ── Right-click: Death Wish ───────────────────────────────────────────
    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        long now = world.getGameTime();
        Long lastDW = lastDeathWish.get(user.getUUID());
        int cd = CooldownManager.strengthDeathWish();
        boolean onCd = lastDW != null && now - lastDW < cd;
        Long lastB = lastBerserk.get(user.getUUID());
        int cdB = CooldownManager.strengthBerserk();
        boolean bCd = lastB != null && now - lastB < cdB;

        if (!world.isClientSide()) {
            overlay(user,
                (onCd ? String.format("§4💀 Death Wish: %ds", ((cd-(now-lastDW))+19)/20) : "§4💀 Death Wish: Ready!")
                + "  "
                + (bCd ? String.format("§4⚔ Berserker: %ds", ((cdB-(now-lastB))+19)/20) : "§4⚔ Berserker: Ready!"));
        }
        if (onCd) return InteractionResult.FAIL;
        return doDeathWish(world, user);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) { return doDeathWish(ctx.getLevel(), ctx.getPlayer()); }

    private InteractionResult doDeathWish(Level world, Player player) {
        if (player == null) return InteractionResult.PASS;
        long now = world.getGameTime();
        Long last = lastDeathWish.get(player.getUUID());
        if (last != null && now - last < CooldownManager.strengthDeathWish()) return InteractionResult.FAIL;

        if (!world.isClientSide()) {
            lastDeathWish.put(player.getUUID(), now);
            deathWishUntil.put(player.getUUID(), now + 160); // 8s
            deathWishDealt.put(player.getUUID(), 0f);
            deathWishTaken.put(player.getUUID(), 0f);

            // Double damage: extra Strength during DW
            player.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 160, 9, false, true)); // Strength X during DW
            player.addEffect(new MobEffectInstance(MobEffects.HASTE,   160, 1, false, true));

            if (world instanceof ServerLevel sl) {
                Vec3 pos = player.position();
                for (int i = 0; i < 30; i++) {
                    double a = Math.toRadians(i * 12);
                    sl.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                            pos.x + Math.cos(a)*1.5, pos.y+1, pos.z + Math.sin(a)*1.5,
                            2, 0.2, 0.5, 0.2, 0.1);
                }
            }
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.RAVAGER_ROAR, player.getSoundSource(), 1f, 0.8f);
            overlay(player, "§4[Strength Gem] §cDEATH WISH! 8s — deal 2x what you take or be punished!");
        }
        return InteractionResult.SUCCESS;
    }

    // ── Left-click hit: Berserker Strike ─────────────────────────────────
    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof Player player)) return;
        Level world = player.level();
        long now = world.getGameTime();

        // If armed (berserk ready) — trigger the 3x hit
        if (berserkReady.containsKey(player.getUUID())) {
            berserkReady.remove(player.getUUID());
            if (!world.isClientSide()) {
                // Deal 3x hit (we apply 2 extra hearts of bonus on top of normal)
                float bonus = target.getMaxHealth() * 0.5f; // aggressive bonus
                if (world instanceof ServerLevel sl) {
                    target.hurtServer(sl, player.damageSources().playerAttack(player), bonus);
                }
                if (world instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.CRIT,
                            target.getX(), target.getY()+1, target.getZ(), 20, 0.5, 0.5, 0.5, 0.3);
                }
                world.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.PLAYER_ATTACK_CRIT, target.getSoundSource(), 1f, 0.7f);
                overlay(player, "§4[Strength Gem] §c💥 BERSERKER HIT! 3x damage!");
                // Track for death wish
                recordDeathWishDamageDealt(player, bonus);
            }
            return;
        }

        // Arm berserker for next hit
        Long last = lastBerserk.get(player.getUUID());
        int cd = CooldownManager.strengthBerserk();
        if (last != null && now - last < cd) {
            if (!world.isClientSide())
                overlay(player, String.format("§4⚔ Berserker: %ds", ((cd-(now-last))+19)/20));
            return;
        }
        if (!world.isClientSide()) {
            lastBerserk.put(player.getUUID(), now);
            berserkReady.put(player.getUUID(), now); // arm it
            overlay(player, "§4[Strength Gem] §e⚔ Berserker armed! Next hit = 3x damage. DONT MISS!");
        }
    }
}
