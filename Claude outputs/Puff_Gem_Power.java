package net.mk.blisssmp.item.custom;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import java.util.HashMap;

import java.util.Map;
import java.util.UUID;

public class Puff_Gem_Power extends Item {

    private static final double LAUNCH_VELOCITY = 4.0;   // ~48 blocks up
    private static final double FLING_POWER     = 3.0;
    private static final double GUST_RADIUS     = 8.0;
    private static final double GUST_POWER      = 2.5;

    // Players with active fall immunity
    private static final Map<UUID, Boolean> fallImmunity = new HashMap<>();
    private static final Map<UUID, Long> lastLaunch      = new HashMap<>();
    private static final Map<UUID, Long> lastGust        = new HashMap<>();

    public Puff_Gem_Power(Item.Settings settings) {
        super(settings);
    }

    // ─── RIGHT-CLICK: Launch ─────────────────────────────────────────────────
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));

        long now = world.getTime();
        long last = lastLaunch.getOrDefault(player.getUuid(), 0L);
        int cd = CooldownManager.puffLaunch();

        if (now - last < cd) {
            long remaining = (cd - (now - last)) / 20 + 1;
            player.sendMessage(Text.literal("§d[Puff Gem] §cLaunch cooldown: " + remaining + "s"), true);
            return TypedActionResult.fail(player.getStackInHand(hand));
        }
        lastLaunch.put(player.getUuid(), now);

        // Launch upward
        Vec3d vel = player.getVelocity();
        player.setVelocity(vel.x, LAUNCH_VELOCITY, vel.z);
        player.velocityModified = true;

        // Grant fall damage immunity
        fallImmunity.put(player.getUuid(), true);

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.PLAYERS, 1.0f, 0.6f);
        player.sendMessage(Text.literal("§d[Puff Gem] §eLaunching to the sky!"), true);

        player.getStackInHand(hand).damage(1, player, LivingEntity.getSlotForHand(hand));
        return TypedActionResult.success(player.getStackInHand(hand));
    }

    // ─── LEFT-CLICK HIT: Fling / Gust ────────────────────────────────────────
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof PlayerEntity player)) return false;
        if (attacker.getWorld().isClient) return false;

        if (player.isShiftKeyDown()) {
            // SHIFT+LEFT: Gust — push all nearby entities outward
            long now = player.getWorld().getTime();
            long last = lastGust.getOrDefault(player.getUuid(), 0L);
            int cd = CooldownManager.puffGust();
            if (now - last < cd) {
                long remaining = (cd - (now - last)) / 20 + 1;
                player.sendMessage(Text.literal("§d[Puff Gem] §cGust cooldown: " + remaining + "s"), true);
                return false;
            }
            lastGust.put(player.getUuid(), now);

            Vec3d center = player.getPos();
            Box area = new Box(
                    center.x - GUST_RADIUS, center.y - GUST_RADIUS, center.z - GUST_RADIUS,
                    center.x + GUST_RADIUS, center.y + GUST_RADIUS, center.z + GUST_RADIUS);

            var nearby = player.getWorld().getOtherEntities(player, area);
            for (var e : nearby) {
                Vec3d dir = e.getPos().subtract(center).normalize();
                e.setVelocity(dir.x * GUST_POWER, GUST_POWER * 0.5, dir.z * GUST_POWER);
                e.velocityModified = true;
            }

            player.getWorld().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 1.5f, 1.2f);
            player.sendMessage(Text.literal("§d[Puff Gem] §eGust!"), true);

        } else {
            // LEFT: Fling target in look direction
            Vec3d look = player.getRotationVec(1.0f);
            target.setVelocity(look.x * FLING_POWER, FLING_POWER * 0.5 + 0.5, look.z * FLING_POWER);
            target.velocityModified = true;
            player.sendMessage(Text.literal("§d[Puff Gem] §eFlung!"), true);
        }

        stack.damage(1, player, LivingEntity.getSlotForHand(Hand.MAIN_HAND));
        return true;
    }

    // ─── PASSIVE: Jump Boost II while holding ────────────────────────────────
    public static void onPlayerTick(PlayerEntity player) {
        if (player.getWorld().isClient) return;
        boolean holding = player.getMainHandStack().getItem() instanceof Puff_Gem_Power
                || player.getOffHandStack().getItem() instanceof Puff_Gem_Power;

        if (holding) {
            StatusEffectInstance current = player.getStatusEffect(StatusEffects.JUMP_BOOST);
            if (current == null || current.getDuration() < 40) {
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.JUMP_BOOST, 80, 1, false, false)); // Jump Boost II
            }
        }
    }

    // ─── Fall Damage Immunity API ──────────────────────────────────────────────
    public static boolean hasFallDamageImmunity(PlayerEntity player) {
        return fallImmunity.getOrDefault(player.getUuid(), false);
    }

    public static void clearFallImmunity(PlayerEntity player) {
        fallImmunity.remove(player.getUuid());
    }
}
