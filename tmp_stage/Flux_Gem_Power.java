package net.mk.blisssmp.item.custom;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FLUX GEM
 *
 * Passive  – blue sparks orbit; enemies within 1.8 blocks get zapped every 2s for 4 HP.
 *
 * Active 1 – Arc Tag (left-hit, main or offhand):
 *            Burns + freezes target; tags them for 3 min. Max 5 tags (oldest dropped).
 *            CD = 3s. CD=0 → no cooldown.
 *
 * Active 2 – Thundergod (right-click):
 *            Strikes ALL tagged targets within 24 blocks with real lightning for 16 HP.
 *            Hit CD = 2 min. Miss CD = 2.5 min. Blocked on cooldown.
 *
 * HUD: left = Arc Tag | right = Thundergod | [N tagged]
 */
public class Flux_Gem_Power extends Item {

    private static final double TOUCH_RADIUS    = 0.5;
    private static final double THUNDER_RADIUS  = 24.0;
    private static final long   TAG_DURATION    = 3600L; // 3 min in ticks
    private static final int    MAX_TAGS        = 5;

    private static final int CD_THUNDER_HIT  = 2400; // 2 min
    private static final int CD_THUNDER_MISS = 3000; // 2.5 min
    private static final int FLASH_DURATION  = 20;   // 1s

    private static final int    LINE_RANGE    = 16;
    private static final int    CD_LINE       = 500;  // 25s

    private static final DustParticleOptions BLUE_SPARK = new DustParticleOptions(0xFF3399FF, 1.2f);

    // Per-player tag map: player UUID → ordered map of (tagged entity UUID → expiry tick)
    private static final Map<UUID, LinkedHashMap<UUID, Long>> playerTags = new HashMap<>();

    private static final Map<UUID, Long> touchCd         = new HashMap<>(); // shock touch per-entity
    private static final Map<UUID, Long> lastArcTag      = new HashMap<>(); // Arc Tag CD per player
    private static final Map<UUID, Long> thunderExpiry   = new HashMap<>(); // Thundergod CD expiry
    private static final Map<UUID, Long> lastLine        = new HashMap<>(); // Lightning Line CD

    // Flash tracking
    private static final Map<UUID, Long>    arcFlashUntil     = new HashMap<>();
    private static final Map<UUID, Long>    thunderFlashUntil = new HashMap<>();
    private static final Map<UUID, Boolean> prevArcReady      = new HashMap<>();
    private static final Map<UUID, Boolean> prevThunderReady  = new HashMap<>();
    private static final Map<UUID, Long>    lineFlashUntil    = new HashMap<>();
    private static final Map<UUID, Boolean> prevLineReady     = new HashMap<>();

    public Flux_Gem_Power(Properties settings) { super(settings); }

    private static void overlay(Player p, String msg) {
        if (p instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal(msg), true);
    }

    private static boolean hasGem(Player p) {
        return p.getMainHandItem().getItem() instanceof Flux_Gem_Power
            || p.getOffhandItem().getItem() instanceof Flux_Gem_Power;
    }

    // ── Passive tick ──────────────────────────────────────────────────────
    public static void onPlayerTick(Player player) {
        if (player.level().isClientSide()) return;
        if (!hasGem(player)) return;

        ServerLevel world = (ServerLevel) player.level();
        long now = world.getGameTime();
        UUID pid = player.getUUID();

        // Orbit blue sparks every 3 ticks
        if (now % 3 == 0) {
            double angle = Math.toRadians((now * 20) % 360);
            world.sendParticles(BLUE_SPARK,
                    player.getX() + Math.cos(angle), player.getY() + 1, player.getZ() + Math.sin(angle),
                    2, 0.05, 0.05, 0.05, 0);
            world.sendParticles(BLUE_SPARK,
                    player.getX() - Math.cos(angle), player.getY() + 1, player.getZ() - Math.sin(angle),
                    2, 0.05, 0.05, 0.05, 0);
        }

        // Shock touch — zap enemies within 1.8 blocks every 2s (40 ticks) for 4 HP
        Vec3 center = player.position();
        AABB area = new AABB(center.x - TOUCH_RADIUS, center.y - TOUCH_RADIUS, center.z - TOUCH_RADIUS,
                             center.x + TOUCH_RADIUS, center.y + TOUCH_RADIUS, center.z + TOUCH_RADIUS);
        for (LivingEntity e : world.getEntitiesOfClass(LivingEntity.class, area, ent -> ent != player)) {
            long last = touchCd.getOrDefault(e.getUUID(), 0L);
            if (now - last < 40) continue;
            touchCd.put(e.getUUID(), now);
            e.hurtServer(world, world.damageSources().lightningBolt(), 4f);
        }

        // Expire stale tags + remove dead entities every 1s (20 ticks)
        LinkedHashMap<UUID, Long> tags = playerTags.get(pid);
        if (tags != null) {
            tags.entrySet().removeIf(e -> now > e.getValue());
            if (now % 20 == 0) {
                tags.keySet().removeIf(uid -> {
                    for (LivingEntity le : world.getEntitiesOfClass(LivingEntity.class,
                            player.getBoundingBox().inflate(THUNDER_RADIUS * 2))) {
                        if (le.getUUID().equals(uid)) return !le.isAlive();
                    }
                    return true; // not found nearby → remove
                });
            }
        }

        // ── HUD ──────────────────────────────────────────────────────────
        int arcCd      = CooldownManager.fluxZap();
        int thunderCd  = CooldownManager.fluxThunder();
        int lineCd     = CooldownManager.fluxLine();

        Long arcLast   = lastArcTag.get(pid);
        boolean arcReady = arcCd == 0 || arcLast == null || (now - arcLast) >= arcCd;

        Long tExp      = thunderExpiry.get(pid);
        boolean thunderReady = thunderCd == 0 || tExp == null || now >= tExp;

        Long lineLast  = lastLine.get(pid);
        boolean lineReady = lineCd == 0 || lineLast == null || (now - lineLast) >= lineCd;

        // Flash on transition
        boolean wasArc     = prevArcReady.getOrDefault(pid, true);
        boolean wasThunder = prevThunderReady.getOrDefault(pid, true);
        boolean wasLine    = prevLineReady.getOrDefault(pid, true);
        if (!wasArc && arcReady)         arcFlashUntil.put(pid, now + FLASH_DURATION);
        if (!wasThunder && thunderReady) thunderFlashUntil.put(pid, now + FLASH_DURATION);
        if (!wasLine && lineReady)       lineFlashUntil.put(pid, now + FLASH_DURATION);
        prevArcReady.put(pid, arcReady);
        prevThunderReady.put(pid, thunderReady);
        prevLineReady.put(pid, lineReady);

        // Left — Arc Tag
        String leftText;
        if (!arcReady) {
            long rem = arcCd - (now - (arcLast == null ? now : arcLast));
            leftText = "§c" + (rem + 19) / 20 + "s";
        } else {
            Long flash = arcFlashUntil.get(pid);
            leftText = ((flash != null && now < flash) ? "§b" : "§a") + "Ready";
        }

        // Mid — Thundergod
        String midText;
        if (!thunderReady) {
            long rem = tExp - now;
            midText = "§c" + (rem + 19) / 20 + "s";
        } else {
            Long flash = thunderFlashUntil.get(pid);
            midText = ((flash != null && now < flash) ? "§b" : "§a") + "Ready";
        }

        // Right — Lightning Line
        String rightText;
        if (!lineReady) {
            long rem = lineCd - (now - (lineLast == null ? now : lineLast));
            rightText = "§c" + (rem + 19) / 20 + "s";
        } else {
            Long flash = lineFlashUntil.get(pid);
            rightText = ((flash != null && now < flash) ? "§b" : "§a") + "Ready";
        }

        // Tag count
        int tagCount = tags == null ? 0 : tags.size();
        String tagText = "§7[" + tagCount + " tagged]";

        overlay(player, String.format("%-12s%-12s%-12s%s", leftText, midText, rightText, tagText));
    }

    // ── Right-click: Thundergod ───────────────────────────────────────────
    @Override
    public InteractionResult use(Level world, Player player, InteractionHand hand) {
        boolean mainHasGem = player.getMainHandItem().getItem() instanceof Flux_Gem_Power;
        if (mainHasGem && hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            // Shift+RC — Lightning Line
            if (!world.isClientSide()) {
                long now = world.getGameTime();
                int cd = CooldownManager.fluxLine();
                Long last = lastLine.get(player.getUUID());
                if (cd != 0 && last != null && now - last < cd) return InteractionResult.FAIL;
                doLightningLine(world, player);
            }
        } else {
            // RC — Thundergod
            if (player.getCooldowns().isOnCooldown(player.getMainHandItem())) return InteractionResult.FAIL;
            if (!world.isClientSide()) {
                doThundergod(world, player);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        boolean mainHasGem = player.getMainHandItem().getItem() instanceof Flux_Gem_Power;
        if (mainHasGem && ctx.getHand() != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player.isShiftKeyDown()) {
            if (!ctx.getLevel().isClientSide()) {
                long now = ctx.getLevel().getGameTime();
                int cd = CooldownManager.fluxLine();
                Long last = lastLine.get(player.getUUID());
                if (cd != 0 && last != null && now - last < cd) return InteractionResult.FAIL;
                doLightningLine(ctx.getLevel(), player);
            }
        } else {
            if (player.getCooldowns().isOnCooldown(player.getMainHandItem())) return InteractionResult.FAIL;
            if (!ctx.getLevel().isClientSide()) {
                doThundergod(ctx.getLevel(), player);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static void doThundergod(Level world, Player player) {
        if (world.isClientSide()) return;
        ServerLevel sl = (ServerLevel) world;
        long now = world.getGameTime();
        UUID pid = player.getUUID();
        int cd = CooldownManager.fluxThunder();

        LinkedHashMap<UUID, Long> tags = playerTags.get(pid);
        if (tags == null || tags.isEmpty()) {
            // No tags at all — block with 1-tick cooldown so client can't spam
            player.getCooldowns().addCooldown(player.getMainHandItem(), 1);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp)
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cNo tagged targets!"), true);
            return;
        }

        // Find tagged entities within 24 blocks
        AABB searchBox = player.getBoundingBox().inflate(THUNDER_RADIUS);
        List<LivingEntity> nearby = sl.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != player && tags.containsKey(e.getUUID()));

        if (nearby.isEmpty()) {
            // Tags exist but none in range — block with 1-tick cooldown
            player.getCooldowns().addCooldown(player.getMainHandItem(), 1);
            if (player instanceof net.minecraft.server.level.ServerPlayer sp)
                sp.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cTagged targets out of range! (24 blocks)"), true);
            return;
        }

        for (LivingEntity e : nearby) {
            summonLightning(sl, e.position(), false);
            e.hurtServer(sl, sl.damageSources().lightningBolt(), 16f);
        }

        // Clear tags that were hit
        for (LivingEntity e : nearby) tags.remove(e.getUUID());

        if (cd != 0) {
            thunderExpiry.put(pid, now + CD_THUNDER_HIT);
            player.getCooldowns().addCooldown(player.getMainHandItem(), CD_THUNDER_HIT);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, player.getSoundSource(), 1f, 0.7f);
    }

    // ── Left-click hit: Arc Tag ───────────────────────────────────────────
    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof Player player)) return;
        doArcTag(player, target);
    }

    // Offhand support — called from BlissSmpMod AttackEntityCallback
    public static void onAttack(Player player, LivingEntity target) {
        if (!hasGem(player)) return;
        if (player.getMainHandItem().getItem() instanceof Flux_Gem_Power) return;
        doArcTag(player, target);
    }

    private static void doArcTag(Player player, LivingEntity target) {
        Level world = player.level();
        if (world.isClientSide()) return;
        long now = world.getGameTime();
        UUID pid = player.getUUID();
        int cd = CooldownManager.fluxZap();
        Long last = lastArcTag.get(pid);
        if (cd != 0 && last != null && now - last < cd) return;

        if (cd != 0) lastArcTag.put(pid, now);

        // Manage tag map — max 5, drop oldest if full
        LinkedHashMap<UUID, Long> tags = playerTags.computeIfAbsent(pid, k -> new LinkedHashMap<>());
        if (tags.size() >= MAX_TAGS) {
            UUID oldest = tags.keySet().iterator().next();
            tags.remove(oldest);
        }
        tags.put(target.getUUID(), now + TAG_DURATION);

        // Burns + freezes target (no glow)
        target.setRemainingFireTicks(60);
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, 9, false, true)); // freeze 2s

        if (world instanceof ServerLevel sl) {
            sl.sendParticles(BLUE_SPARK, target.getX(), target.getY() + 1, target.getZ(),
                    20, 0.4, 0.6, 0.4, 0.05);
        }

        world.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, target.getSoundSource(), 0.5f, 1.5f);
    }

    // ── Lightning Line ────────────────────────────────────────────────────
    private static void doLightningLine(Level world, Player player) {
        if (world.isClientSide()) return;
        ServerLevel sl = (ServerLevel) world;
        long now = world.getGameTime();
        UUID pid = player.getUUID();
        int cd = CooldownManager.fluxLine();

        Vec3 origin = player.getEyePosition();
        Vec3 look   = player.getViewVector(1f).normalize();

        // Strike lightning every 2 blocks along the line, place fire at ground
        for (int i = 1; i <= LINE_RANGE; i += 2) {
            Vec3 pos = origin.add(look.scale(i));
            // Real lightning at each step
            summonLightning(sl, pos, false);
            // Particles along the line
            sl.sendParticles(BLUE_SPARK, pos.x, pos.y, pos.z, 5, 0.1, 0.1, 0.1, 0);
            // Place fire on the block below
            net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.containing(pos.x, pos.y - 1, pos.z);
            if (sl.getBlockState(bp).isAir() || sl.getBlockState(bp).getBlock() == net.minecraft.world.level.block.Blocks.FIRE) {
                sl.setBlock(bp, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_ALL);
            } else {
                net.minecraft.core.BlockPos above = bp.above();
                if (sl.getBlockState(above).isAir()) {
                    sl.setBlock(above, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState(),
                            net.minecraft.world.level.block.Block.UPDATE_ALL);
                }
            }
        }

        // Damage any entity caught in the line
        AABB lineBox = new AABB(
                origin.x - 1, origin.y - 2, origin.z - 1,
                origin.x + look.x * LINE_RANGE + 1, origin.y + look.y * LINE_RANGE + 2, origin.z + look.z * LINE_RANGE + 1);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, lineBox, ent -> ent != player)) {
            // Check entity is roughly on the line
            Vec3 toE = e.position().subtract(origin);
            double dot = toE.dot(look);
            if (dot < 0 || dot > LINE_RANGE) continue;
            Vec3 proj = origin.add(look.scale(dot));
            if (proj.distanceTo(e.position()) > 2.0) continue;
            summonLightning(sl, e.position(), false);
            e.hurtServer(sl, sl.damageSources().lightningBolt(), 12f);
        }

        if (cd != 0) lastLine.put(pid, now);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.LIGHTNING_BOLT_THUNDER, player.getSoundSource(), 1f, 0.8f);
    }

    // ── Helper ────────────────────────────────────────────────────────────
    private static void summonLightning(ServerLevel world, Vec3 pos, boolean visualOnly) {
        LightningBolt bolt = new LightningBolt(EntityTypes.LIGHTNING_BOLT, world);
        bolt.setPos(pos.x, pos.y, pos.z);
        bolt.setVisualOnly(visualOnly);
        world.addFreshEntity(bolt);
    }
}
