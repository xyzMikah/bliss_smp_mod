package net.mk.blisssmp.item.custom;

import net.minecraft.entity.EntityTypes;
import net.minecraft.entity.LightningBolt;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Flux_Gem_Power extends Item {

    private static final double STORM_RADIUS = 5.0;
    private static final double TOUCH_RADIUS = 1.8;

    // Blue orbit particles  (ARGB packed: alpha=FF, R=33, G=99, B=FF)
    private static final DustParticleEffect BLUE_SPARK =
            new DustParticleEffect(0xFF3399FF, 1.2f);
    // Red zap-back particles (ARGB packed: alpha=FF, R=FF, G=1A, B=1A)
    private static final DustParticleEffect RED_SPARK =
            new DustParticleEffect(0xFFFF1A1A, 1.2f);

    private static final Map<UUID, Long> lastStorm      = new HashMap<>();
    private static final Map<UUID, Long> lastZap        = new HashMap<>();
    // Per-entity cooldown for Electric Touch to avoid spam
    private static final Map<UUID, Long> touchCooldown  = new HashMap<>();

    public Flux_Gem_Power(Item.Settings settings) {
        super(settings);
    }

    // ─── RIGHT-CLICK: Thunder Storm ──────────────────────────────────────────
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));

        ServerWorld serverWorld = (ServerWorld) world;
        long now = world.getTime();
        long last = lastStorm.getOrDefault(player.getUuid(), 0L);
        int cd = CooldownManager.fluxStorm();

        if (now - last < cd) {
            long remaining = (cd - (now - last)) / 20 + 1;
            player.sendMessage(Text.literal("§3[Flux Gem] §cStorm cooldown: " + remaining + "s"), true);
            return TypedActionResult.fail(player.getStackInHand(hand));
        }
        lastStorm.put(player.getUuid(), now);

        // Strike all entities within STORM_RADIUS (player's block is safe)
        Vec3d center = player.getPos();
        Box area = new Box(
                center.x - STORM_RADIUS, center.y - STORM_RADIUS, center.z - STORM_RADIUS,
                center.x + STORM_RADIUS, center.y + STORM_RADIUS, center.z + STORM_RADIUS);

        var nearby = world.getOtherEntities(player, area);
        int hits = 0;
        for (var e : nearby) {
            if (e instanceof LivingEntity le) {
                summonLightning(serverWorld, e.getPos(), false);
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 4, false, true)); // stun
                hits++;
            }
        }

        player.sendMessage(Text.literal("§3[Flux Gem] §eThunder storm hit " + hits + " entities!"), true);
        player.getStackInHand(hand).damage(1, player, LivingEntity.getSlotForHand(hand));
        return TypedActionResult.success(player.getStackInHand(hand));
    }

    // ─── LEFT-CLICK HIT: Burn+Stun / Direct Zap ──────────────────────────────
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof PlayerEntity player)) return false;
        if (attacker.getWorld().isClient) return false;

        ServerWorld serverWorld = (ServerWorld) attacker.getWorld();

        if (player.isShiftKeyDown()) {
            // SHIFT+LEFT: Direct lightning zap with cooldown
            long now = serverWorld.getTime();
            long last = lastZap.getOrDefault(player.getUuid(), 0L);
            int cd = CooldownManager.fluxZap();
            if (now - last < cd) {
                long remaining = (cd - (now - last)) / 20 + 1;
                player.sendMessage(Text.literal("§3[Flux Gem] §cZap cooldown: " + remaining + "s"), true);
                return false;
            }
            lastZap.put(player.getUuid(), now);

            summonLightning(serverWorld, target.getPos(), false);
            target.setOnFireFor(3);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 4, false, true));
            player.sendMessage(Text.literal("§3[Flux Gem] §eZapped!"), true);

        } else {
            // LEFT: Burn + Stun (always works, no cooldown)
            target.setOnFireFor(3);
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 4, false, true)); // stun
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0, false, true));

            // Visual lightning (no damage)
            summonLightning(serverWorld, target.getPos(), true);
            player.sendMessage(Text.literal("§3[Flux Gem] §eBurning and stunned!"), true);
        }

        stack.damage(1, player, LivingEntity.getSlotForHand(Hand.MAIN_HAND));
        return true;
    }

    // ─── PASSIVE: Electric Touch + blue orbit particles ───────────────────────
    public static void onPlayerTick(PlayerEntity player) {
        if (player.getWorld().isClient) return;
        boolean holding = player.getMainHandStack().getItem() instanceof Flux_Gem_Power
                || player.getOffHandStack().getItem() instanceof Flux_Gem_Power;
        if (!holding) return;

        ServerWorld world = (ServerWorld) player.getWorld();
        long now = world.getTime();

        // Blue orbit particles every 3 ticks
        if (now % 3 == 0) {
            double angle = (now * 20) % 360;
            double rad = Math.toRadians(angle);
            double px = player.getX() + Math.cos(rad) * 1.0;
            double py = player.getY() + 1.0;
            double pz = player.getZ() + Math.sin(rad) * 1.0;
            world.spawnParticles(BLUE_SPARK, px, py, pz, 2, 0.1, 0.1, 0.1, 0.0);

            // Second orbit particle on opposite side
            world.spawnParticles(BLUE_SPARK,
                    player.getX() - Math.cos(rad) * 1.0,
                    player.getY() + 1.0,
                    player.getZ() - Math.sin(rad) * 1.0,
                    2, 0.1, 0.1, 0.1, 0.0);
        }

        // Electric Touch: zap anyone within TOUCH_RADIUS
        Vec3d center = player.getPos();
        Box area = new Box(
                center.x - TOUCH_RADIUS, center.y - TOUCH_RADIUS, center.z - TOUCH_RADIUS,
                center.x + TOUCH_RADIUS, center.y + TOUCH_RADIUS, center.z + TOUCH_RADIUS);

        var nearby = world.getOtherEntities(player, area);
        for (var e : nearby) {
            if (!(e instanceof LivingEntity le)) continue;

            long lastTouch = touchCooldown.getOrDefault(e.getUuid(), 0L);
            if (now - lastTouch < 40) continue; // 2-second cooldown per entity

            touchCooldown.put(e.getUuid(), now);

            // Apply stun + damage
            le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 4, false, true));
            le.damage(world.getDamageSources().lightningBolt(), 4.0f);

            // Red spark particles around the touched entity
            world.spawnParticles(RED_SPARK,
                    le.getX(), le.getY() + 0.5, le.getZ(),
                    12, 0.5, 0.5, 0.5, 0.0);

            // Visual lightning bolt
            LightningBolt bolt = new LightningBolt(EntityTypes.LIGHTNING_BOLT, world);
            bolt.setPos(le.getX(), le.getY(), le.getZ());
            bolt.setVisualOnly(true);
            world.spawnEntity(bolt);
        }
    }

    // ─── Lightning Helper ─────────────────────────────────────────────────────
    private static void summonLightning(ServerWorld world, Vec3d pos, boolean visualOnly) {
        LightningBolt bolt = new LightningBolt(EntityTypes.LIGHTNING_BOLT, world);
        bolt.setPos(pos.x, pos.y, pos.z);
        bolt.setVisualOnly(visualOnly);
        world.spawnEntity(bolt);
    }
}
