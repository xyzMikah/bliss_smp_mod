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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Astra_Gem_Power extends Item {

    // ── Passive ───────────────────────────────────────────────────────────
    private static final int    RESONANCE_INTERVAL = 100;
    private static final double RESONANCE_RADIUS   = 5.0;

    // ── Sonic Boom ────────────────────────────────────────────────────────
    private static final double CONE_RANGE  = 25.0;
    private static final double CONE_ANGLE  = 0.7;
    private static final float  BOOM_DAMAGE = 20.0f;
    private static final int    CD_HIT      = 1800; // 1.5 min
    private static final int    CD_MISS     = 600;  // 30s

    // ── Sculk Snare ───────────────────────────────────────────────────────
    private static final int SNARE_DURATION = 60; // 3s

    // ── Flash: ticks until blue flash expires per player ──────────────────
    private static final int FLASH_DURATION = 20; // 1s flash

    private static final Map<UUID, Long> boomExpiry      = new HashMap<>();
    private static final Map<UUID, Long> lastSnare       = new HashMap<>();
    private static final Map<UUID, Long> snareFlashUntil = new HashMap<>(); // flash blue when snare ready
    private static final Map<UUID, Long> boomFlashUntil  = new HashMap<>(); // flash blue when boom ready

    // Track previous state to detect when cd expires
    private static final Map<UUID, Boolean> prevSnareReady = new HashMap<>();
    private static final Map<UUID, Boolean> prevBoomReady  = new HashMap<>();

    public Astra_Gem_Power(Properties settings) { super(settings); }

    private static void overlay(Player p, String msg) {
        if (p instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal(msg), true);
    }

    private static boolean hasGem(Player p) {
        return p.getMainHandItem().getItem() instanceof Astra_Gem_Power
            || p.getOffhandItem().getItem() instanceof Astra_Gem_Power;
    }

    // ── Passive tick ──────────────────────────────────────────────────────
    public static void onPlayerTick(Player player) {
        if (player.level().isClientSide()) return;
        if (!hasGem(player)) return;

        ServerLevel world = (ServerLevel) player.level();
        long now = world.getGameTime();

        // Sculk soul orbit particles every 2 ticks
        if (now % 2 == 0) {
            double angle = Math.toRadians((now * 15) % 360);
            world.sendParticles(ParticleTypes.SCULK_SOUL,
                    player.getX() + Math.cos(angle), player.getY() + 1.2,
                    player.getZ() + Math.sin(angle), 1, 0, 0, 0, 0);
            world.sendParticles(ParticleTypes.SCULK_SOUL,
                    player.getX() - Math.cos(angle), player.getY() + 1.2,
                    player.getZ() - Math.sin(angle), 1, 0, 0, 0, 0);
        }

        // Auto resonance pulse every 5s
        if (now % RESONANCE_INTERVAL == 0) {
            AABB area = player.getBoundingBox().inflate(RESONANCE_RADIUS);
            List<LivingEntity> nearby = world.getEntitiesOfClass(LivingEntity.class, area, e -> e != player);
            if (!nearby.isEmpty()) {
                for (LivingEntity e : nearby) {
                    e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, true));
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,  40, 2, false, true));
                }
                world.sendParticles(ParticleTypes.SCULK_SOUL,
                        player.getX(), player.getY() + 1, player.getZ(), 20, 2, 1, 2, 0.05);
                world.playSound(null, player.getX(), player.getY(), player.getZ(),
                        SoundEvents.SCULK_CLICKING_STOP, player.getSoundSource(), 1f, 0.8f);
            }
        }

        // ── HUD: left = Snare, right = Boom — same position every tick ───
        UUID id = player.getUUID();
        int snareCd = CooldownManager.astraSnare();
        int boomCd  = CooldownManager.astraBoom();

        Long snareLastUsed = lastSnare.get(id);
        boolean snareReady = snareCd == 0 || snareLastUsed == null || (now - snareLastUsed) >= snareCd;

        Long boomExp = boomExpiry.get(id);
        boolean boomReady = boomCd == 0 || boomExp == null || now >= boomExp;

        // Detect transitions from not-ready → ready → trigger flash
        boolean wasSnareReady = prevSnareReady.getOrDefault(id, true);
        boolean wasBoomReady  = prevBoomReady.getOrDefault(id, true);
        if (!wasSnareReady && snareReady) snareFlashUntil.put(id, now + FLASH_DURATION);
        if (!wasBoomReady  && boomReady)  boomFlashUntil.put(id, now + FLASH_DURATION);
        prevSnareReady.put(id, snareReady);
        prevBoomReady.put(id, boomReady);

        // Build left side (Snare)
        String leftColor;
        String leftText;
        if (!snareReady) {
            long rem = snareCd - (now - (snareLastUsed == null ? now : snareLastUsed));
            leftColor = "§c";
            leftText  = leftColor + (rem + 19) / 20 + "s";
        } else {
            Long flash = snareFlashUntil.get(id);
            leftColor = (flash != null && now < flash) ? "§b" : "§a";
            leftText  = leftColor + "Ready";
        }

        // Build right side (Boom)
        String rightColor;
        String rightText;
        if (!boomReady) {
            long rem = boomExp - now;
            rightColor = "§c";
            rightText  = rightColor + (rem + 19) / 20 + "s";
        } else {
            Long flash = boomFlashUntil.get(id);
            rightColor = (flash != null && now < flash) ? "§b" : "§a";
            rightText  = rightColor + "Ready";
        }

        // Fixed-width padding to keep positions stable
        String hud = String.format("%-12s%s", leftText, rightText);
        overlay(player, hud);
    }

    // ── Right-click: Sonic Boom ───────────────────────────────────────────
    @Override
    public InteractionResult use(Level world, Player player, InteractionHand hand) {
        boolean mainHasGem = player.getMainHandItem().getItem() instanceof Astra_Gem_Power;
        if (mainHasGem && hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player.getCooldowns().isOnCooldown(player.getMainHandItem())) return InteractionResult.FAIL;
        if (!world.isClientSide()) {
            doSonicBoom(world, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        boolean mainHasGem = player.getMainHandItem().getItem() instanceof Astra_Gem_Power;
        if (mainHasGem && ctx.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player.getCooldowns().isOnCooldown(player.getMainHandItem())) return InteractionResult.FAIL;
        if (!ctx.getLevel().isClientSide()) {
            doSonicBoom(ctx.getLevel(), player);
        }
        return InteractionResult.SUCCESS;
    }

    private static void doSonicBoom(Level world, Player player) {
        if (world.isClientSide()) return;
        ServerLevel sl = (ServerLevel) world;
        long now = world.getGameTime();
        Vec3 origin = player.getEyePosition();
        Vec3 look   = player.getViewVector(1f);

        for (int i = 0; i < 50; i++) {
            double t = (i / 50.0) * CONE_RANGE;
            sl.sendParticles(ParticleTypes.SONIC_BOOM,
                    origin.x + look.x * t, origin.y + look.y * t, origin.z + look.z * t,
                    1, 0, 0, 0, 0);
        }

        AABB box = new AABB(origin.x - CONE_RANGE, origin.y - 5, origin.z - CONE_RANGE,
                            origin.x + CONE_RANGE, origin.y + 5, origin.z + CONE_RANGE);
        boolean hit = false;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, ent -> ent != player)) {
            Vec3 toE = e.position().subtract(origin).normalize();
            if (look.dot(toE) > CONE_ANGLE && origin.distanceTo(e.position()) <= CONE_RANGE) {
                e.hurtServer(sl, e.damageSources().sonicBoom(player), BOOM_DAMAGE);
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, true));
                hit = true;
            }
        }

        int cd = CooldownManager.astraBoom();
        if (hit) {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, true));
            if (cd != 0) {
                boomExpiry.put(player.getUUID(), now + CD_HIT);
                player.getCooldowns().addCooldown(player.getMainHandItem(), CD_HIT);
            }
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WARDEN_SONIC_BOOM, player.getSoundSource(), 1f, 1f);
        } else {
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 300, 0, false, true));
            if (cd != 0) {
                boomExpiry.put(player.getUUID(), now + CD_MISS);
                player.getCooldowns().addCooldown(player.getMainHandItem(), CD_MISS);
            }
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.SCULK_CLICKING_STOP, player.getSoundSource(), 0.8f, 0.6f);
        }
    }

    // ── Left-click hit: Sculk Snare ───────────────────────────────────────
    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof Player player)) return;
        doSculkSnare(player, target);
    }

    public static void onAttack(Player player, LivingEntity target) {
        if (!hasGem(player)) return;
        if (player.getMainHandItem().getItem() instanceof Astra_Gem_Power) return;
        doSculkSnare(player, target);
    }

    private static void doSculkSnare(Player player, LivingEntity target) {
        Level world = player.level();
        if (world.isClientSide()) return;
        long now = world.getGameTime();
        int cd = CooldownManager.astraSnare();
        Long last = lastSnare.get(player.getUUID());
        if (cd != 0 && last != null && now - last < cd) return;

        if (cd != 0) lastSnare.put(player.getUUID(), now);

        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,  SNARE_DURATION, 9, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, SNARE_DURATION, 0, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING,   SNARE_DURATION * 4, 0, false, true));

        if (world instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SCULK_SOUL,
                    target.getX(), target.getY() + 1, target.getZ(), 30, 0.4, 0.8, 0.4, 0.06);
            sl.sendParticles(ParticleTypes.SCULK_SOUL,
                    target.getX(), target.getY() + 0.5, target.getZ(), 15, 0.6, 0.3, 0.6, 0.02);
        }

        world.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.SCULK_SHRIEKER_SHRIEK, target.getSoundSource(), 1f, 0.5f);
    }

    // ── Melee kill: reduce Sonic Boom CD by 5s (100 ticks) ───────────────
    public static void onKill(Player player) {
        if (!hasGem(player)) return;
        UUID id = player.getUUID();
        Long expiry = boomExpiry.get(id);
        if (expiry == null) return;
        long now = player.level().getGameTime();
        long newExpiry = expiry - 100;
        if (newExpiry <= now) {
            boomExpiry.remove(id); // fully expired
        } else {
            boomExpiry.put(id, newExpiry);
        }
    }
}
