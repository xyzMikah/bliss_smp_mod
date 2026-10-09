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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PUFF GEM
 *
 * Passive  – Jump Boost II while held; no fall damage while held.
 *
 * Active 1 – Vortex Chain (right-click):
 *            Shoots wind hook, pulls hit mob + nearby mobs toward you.
 *            Steer mid-air with look direction for 2s. CD = 6s.
 *
 * Active 2 – Jet Launch (shift+right-click):
 *            Launches player up+forward in look direction. No fall damage. CD = 8s.
 *
 * Active 3 – Sky Rip (shift+left-hit):
 *            Levitates enemy 5s → falls to death. CD = 12s.
 *
 * HUD: left = Sky Rip | mid = Jet Launch | right = Vortex Chain
 */
public class Puff_Gem_Power extends Item {

    private static final double PULL_RADIUS   = 12.0;

    // Per-player state
    private static final Map<UUID, Long>    lastVortex = new HashMap<>();
    private static final Map<UUID, Long>    lastLaunch = new HashMap<>();
    private static final Map<UUID, Long>    lastSkyRip = new HashMap<>();
    private static final Map<UUID, Boolean> fallImmune    = new HashMap<>();
    private static final Map<UUID, UUID>    grabbedEntity  = new HashMap<>();
    private static final Map<UUID, Long>    grabStartTick  = new HashMap<>();
    private static final long GRAB_HOLD_TICKS = 100L; // 5s


    public Puff_Gem_Power(Properties settings) { super(settings); }

    private static void overlay(Player p, String msg) {
        if (p instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal(msg), true);
    }

    private static boolean hasGem(Player p) {
        return p.getMainHandItem().getItem() instanceof Puff_Gem_Power
            || p.getOffhandItem().getItem() instanceof Puff_Gem_Power;
    }

    // ── Passive tick ──────────────────────────────────────────────────────
    public static void onPlayerTick(Player player) {
        if (player.level().isClientSide()) return;
        if (!hasGem(player)) return;

        ServerLevel world = (ServerLevel) player.level();
        long now = world.getGameTime();
        UUID pid = player.getUUID();

        // Jump Boost II — refresh before it expires
        if (!player.hasEffect(MobEffects.JUMP_BOOST) ||
                player.getEffect(MobEffects.JUMP_BOOST).getDuration() < 40) {
            player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, 80, 1, false, false));
        }

        // Cloud orbit particles every 4 ticks
        if (now % 4 == 0) {
            double angle = Math.toRadians((now * 12) % 360);
            world.sendParticles(ParticleTypes.CLOUD,
                    player.getX() + Math.cos(angle), player.getY() + 1.1, player.getZ() + Math.sin(angle),
                    1, 0.02, 0.02, 0.02, 0);
            world.sendParticles(ParticleTypes.CLOUD,
                    player.getX() - Math.cos(angle), player.getY() + 1.1, player.getZ() - Math.sin(angle),
                    1, 0.02, 0.02, 0.02, 0);
        }

        // Trail grabbed entity behind the player
        UUID grabbed = grabbedEntity.get(pid);
        if (grabbed != null) {
            Long grabStart = grabStartTick.get(pid);
            boolean expired = grabStart != null && (now - grabStart) >= GRAB_HOLD_TICKS;
            AABB wide = player.getBoundingBox().inflate(40);
            boolean found = false;
            for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, wide,
                    ent -> ent.getUUID().equals(grabbed))) {
                if (!e.isAlive() || expired) {
                    // Auto-release: restore gravity, drop them in place
                    e.setNoGravity(false);
                    e.fallDistance = 10.0f; // guarantee fall damage on landing
                    grabbedEntity.remove(pid);
                    grabStartTick.remove(pid);
                    int cd = CooldownManager.puffVortex();
                    if (cd != 0) {
                        lastVortex.put(pid, now);
                        player.getCooldowns().addCooldown(Items.BARRIER.getDefaultInstance(), cd);
                    }
                    break;
                }
                found = true;
                // Trail position: 3 blocks behind player's look, at eye height
                Vec3 trailTarget = player.getEyePosition().subtract(player.getViewVector(1f).scale(3.0));
                Vec3 toTrail = trailTarget.subtract(e.position());
                double dist = toTrail.length();
                double speed = dist < 1.0 ? dist : Math.min(dist * 1.2, 5.0);
                e.setDeltaMovement(toTrail.normalize().scale(speed));
                e.setNoGravity(true);
                e.hurtMarked = true;
                world.sendParticles(ParticleTypes.CLOUD,
                        e.getX(), e.getY() + 1, e.getZ(), 3, 0.2, 0.2, 0.2, 0.02);
            }
            if (!found) {
                grabbedEntity.remove(pid);
                grabStartTick.remove(pid);
            }
        }

        // ── HUD ──────────────────────────────────────────────────────────
        int vortexCd = CooldownManager.puffVortex();
        int launchCd = CooldownManager.puffLaunch();
        int skyRipCd = CooldownManager.puffSkyRip();

        Long vortexLast  = lastVortex.get(pid);
        Long launchLast  = lastLaunch.get(pid);
        Long skyRipLast  = lastSkyRip.get(pid);

        boolean vortexReady  = vortexCd  == 0 || vortexLast  == null || (now - vortexLast)  >= vortexCd;
        boolean launchReady  = launchCd  == 0 || launchLast  == null || (now - launchLast)  >= launchCd;
        boolean skyRipReady  = skyRipCd  == 0 || skyRipLast  == null || (now - skyRipLast)  >= skyRipCd;

        // Left — Sky Rip (shift+left-click)
        String leftText;
        if (!skyRipReady) {
            long rem = skyRipCd - (now - skyRipLast);
            leftText = "§c" + (rem + 19) / 20 + "s";
        } else {
            leftText = "§aReady";
        }

        // Mid — Jet Launch (shift+right-click)
        String midText;
        if (!launchReady) {
            long rem = launchCd - (now - launchLast);
            midText = "§c" + (rem + 19) / 20 + "s";
        } else {
            midText = "§aReady";
        }

        // Right — Vortex Chain (right-click)
        String rightText;
        if (!vortexReady) {
            long rem = vortexCd - (now - vortexLast);
            rightText = "§c" + (rem + 19) / 20 + "s";
        } else {
            rightText = "§aReady";
        }

        overlay(player, String.format("%-12s%-12s%s", leftText, midText, rightText));
    }

    // ── Right-click: Vortex Chain / Jet Launch (shift) ───────────────────
    @Override
    public InteractionResult use(Level world, Player player, InteractionHand hand) {
        boolean mainHasGem = player.getMainHandItem().getItem() instanceof Puff_Gem_Power;
        if (mainHasGem && hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            // Shift+RC — Jet Launch: STICK dummy slot
            if (player.getCooldowns().isOnCooldown(Items.STICK.getDefaultInstance())) return InteractionResult.FAIL;
            if (!world.isClientSide()) doJetLaunch(world, player);
        } else {
            // RC — Vortex Grab/Fling: BARRIER dummy slot blocks re-grab after fling CD
            if (!grabbedEntity.containsKey(player.getUUID()) &&
                    player.getCooldowns().isOnCooldown(Items.BARRIER.getDefaultInstance())) return InteractionResult.FAIL;
            if (!world.isClientSide()) doVortexChain(world, player);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        boolean mainHasGem = player.getMainHandItem().getItem() instanceof Puff_Gem_Power;
        if (mainHasGem && ctx.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            if (player.getCooldowns().isOnCooldown(Items.STICK.getDefaultInstance())) return InteractionResult.FAIL;
            if (!ctx.getLevel().isClientSide()) doJetLaunch(ctx.getLevel(), player);
        } else {
            if (!grabbedEntity.containsKey(player.getUUID()) &&
                    player.getCooldowns().isOnCooldown(Items.BARRIER.getDefaultInstance())) return InteractionResult.FAIL;
            if (!ctx.getLevel().isClientSide()) doVortexChain(ctx.getLevel(), player);
        }
        return InteractionResult.SUCCESS;
    }

    // ── Vortex Grab / Fling ──────────────────────────────────────────────
    private static void doVortexChain(Level world, Player player) {
        if (world.isClientSide()) return;
        ServerLevel sl = (ServerLevel) world;
        long now = world.getGameTime();
        UUID pid = player.getUUID();

        // ── If already holding someone: FLING them ────────────────────────
        UUID held = grabbedEntity.get(pid);
        if (held != null) {
            AABB wide = player.getBoundingBox().inflate(40);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, wide,
                    ent -> ent.getUUID().equals(held))) {
                Vec3 fling = player.getViewVector(1f).scale(4.5).add(0, 1.2, 0);
                e.setDeltaMovement(fling);
                e.setNoGravity(false);
                e.fallDistance = 10.0f; // guarantee fall damage on landing
                e.hurtMarked = true;
                sl.sendParticles(ParticleTypes.CLOUD,
                        e.getX(), e.getY() + 1, e.getZ(), 20, 0.4, 0.4, 0.4, 0.15);
            }
            grabbedEntity.remove(pid);
            grabStartTick.remove(pid);
            int cd = CooldownManager.puffVortex();
            if (cd != 0) {
                lastVortex.put(pid, now);
                player.getCooldowns().addCooldown(Items.BARRIER.getDefaultInstance(), cd);
            }
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BREEZE_SHOOT, player.getSoundSource(), 1f, 1.3f);
            return;
        }

        // ── Not holding: GRAB nearest target in crosshair ─────────────────
        // No cooldown check on grab — cooldown starts on fling
        Vec3 origin = player.getEyePosition();
        Vec3 look   = player.getViewVector(1f);
        AABB scanBox = new AABB(origin.x - PULL_RADIUS, origin.y - PULL_RADIUS, origin.z - PULL_RADIUS,
                                origin.x + PULL_RADIUS, origin.y + PULL_RADIUS, origin.z + PULL_RADIUS);
        LivingEntity target = null;
        double closest = PULL_RADIUS + 1;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, scanBox, ent -> ent != player)) {
            Vec3 toE = e.position().subtract(origin);
            double dist = toE.length();
            if (look.dot(toE.normalize()) > 0.82 && dist < closest) {
                closest = dist;
                target = e;
            }
        }

        if (target == null) {
            sl.sendParticles(ParticleTypes.CLOUD,
                    origin.x + look.x * 6, origin.y + look.y * 6, origin.z + look.z * 6,
                    10, 0.5, 0.5, 0.5, 0.1);
            overlay(player, "§cNo target in range!");
            return;
        }

        grabbedEntity.put(pid, target.getUUID());
        grabStartTick.put(pid, now);
        target.setNoGravity(true);
        sl.sendParticles(ParticleTypes.CLOUD,
                target.getX(), target.getY() + 1, target.getZ(), 15, 0.4, 0.4, 0.4, 0.1);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.BREEZE_SHOOT, player.getSoundSource(), 1f, 0.5f);
        overlay(player, "§dGrabbed! RC again to fling.");
    }

    // ── Jet Launch ───────────────────────────────────────────────────────
    private static void doJetLaunch(Level world, Player player) {
        if (world.isClientSide()) return;
        long now = world.getGameTime();
        UUID pid = player.getUUID();
        int cd = CooldownManager.puffLaunch();

        Long last = lastLaunch.get(pid);
        if (cd != 0 && last != null && now - last < cd) return;

        Vec3 look   = player.getViewVector(1f);
        double hSpeed = 2.5;
        double vBoost = Math.max(look.y * 2.0, 0.8);
        player.setDeltaMovement(look.x * hSpeed, vBoost + 1.2, look.z * hSpeed);
        player.hurtMarked = true;
        fallImmune.put(pid, true);

        if (world instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CLOUD,
                    player.getX(), player.getY(), player.getZ(), 20, 0.5, 0.2, 0.5, 0.2);
        }
        if (cd != 0) {
            lastLaunch.put(pid, now);
            player.getCooldowns().addCooldown(Items.STICK.getDefaultInstance(), cd);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, player.getSoundSource(), 1f, 0.7f);
    }

    // ── Shift+left-hit: Sky Rip ───────────────────────────────────────────
    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof Player player)) return;
        if (!player.isShiftKeyDown()) return;
        doSkyRip(player, target);
    }

    public static void onAttack(Player player, LivingEntity target) {
        if (!hasGem(player)) return;
        if (player.getMainHandItem().getItem() instanceof Puff_Gem_Power) return;
        if (!player.isShiftKeyDown()) return;
        doSkyRip(player, target);
    }

    private static void doSkyRip(Player player, LivingEntity target) {
        Level world = player.level();
        if (world.isClientSide()) return;
        long now = world.getGameTime();
        UUID pid = player.getUUID();
        int cd = CooldownManager.puffSkyRip();

        Long last = lastSkyRip.get(pid);
        if (cd != 0 && last != null && now - last < cd) return;

        target.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100, 4, false, true));
        target.fallDistance = 10.0f; // pre-seed fall distance so landing after levitation deals damage

        if (world instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.CLOUD,
                    target.getX(), target.getY() + 1, target.getZ(), 15, 0.5, 0.2, 0.5, 0.15);
        }
        if (cd != 0) lastSkyRip.put(pid, now);
        world.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.BREEZE_SHOOT, target.getSoundSource(), 1f, 0.5f);
    }

    // ── Fall immunity API ─────────────────────────────────────────────────
    public static boolean hasFallImmunity(Player p) { return fallImmune.getOrDefault(p.getUUID(), false); }
    public static void clearFallImmunity(Player p)  { fallImmune.remove(p.getUUID()); }
}
