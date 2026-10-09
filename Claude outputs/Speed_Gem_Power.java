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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Speed_Gem_Power extends Item {

    private static final double DASH_SPEED = 3.2; // ~16 blocks

    private static final Map<UUID, Long> lastDash  = new HashMap<>();
    private static final Map<UUID, Long> lastBlink = new HashMap<>();

    public Speed_Gem_Power(Item.Settings settings) {
        super(settings);
    }

    // ─── RIGHT-CLICK: Dash ──────────────────────────────────────────────────
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));

        long now = world.getTime();
        long last = lastDash.getOrDefault(player.getUuid(), 0L);
        int cd = CooldownManager.speedDash();

        if (now - last < cd) {
            long remaining = (cd - (now - last)) / 20 + 1;
            player.sendMessage(Text.literal("§b[Speed Gem] §cDash cooldown: " + remaining + "s"), true);
            return TypedActionResult.fail(player.getStackInHand(hand));
        }
        lastDash.put(player.getUuid(), now);

        // Launch player in look direction
        Vec3d look = player.getRotationVec(1.0f);
        player.setVelocity(look.x * DASH_SPEED, look.y * DASH_SPEED + 0.3, look.z * DASH_SPEED);
        player.velocityModified = true;

        // Speed IV + Jump Boost II for 3 seconds
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 3, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 60, 1, false, false));

        player.sendMessage(Text.literal("§b[Speed Gem] §eDash!"), true);
        player.getStackInHand(hand).damage(1, player, LivingEntity.getSlotForHand(hand));
        return TypedActionResult.success(player.getStackInHand(hand));
    }

    // ─── LEFT-CLICK HIT: Slowness / Blink Behind ─────────────────────────────
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof PlayerEntity player)) return false;
        if (attacker.getWorld().isClient) return false;

        if (player.isShiftKeyDown()) {
            // SHIFT+LEFT: blink behind target
            long now = player.getWorld().getTime();
            long last = lastBlink.getOrDefault(player.getUuid(), 0L);
            int cd = CooldownManager.speedBlink();

            if (now - last < cd) {
                long remaining = (cd - (now - last)) / 20 + 1;
                player.sendMessage(Text.literal("§b[Speed Gem] §cBlink cooldown: " + remaining + "s"), true);
                return false;
            }
            lastBlink.put(player.getUuid(), now);

            // Teleport behind the target
            Vec3d targetLook = target.getRotationVec(1.0f);
            double bx = target.getX() - targetLook.x * 1.5;
            double by = target.getY();
            double bz = target.getZ() - targetLook.z * 1.5;
            player.teleport(bx, by, bz);

            // Blind target briefly
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0, false, false));
            player.sendMessage(Text.literal("§b[Speed Gem] §eBlinked behind target!"), true);

        } else {
            // LEFT: Slowness on target, brief speed for self
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 2, false, true));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 40, 0, false, false));
            player.sendMessage(Text.literal("§b[Speed Gem] §eSlowed target!"), true);
        }

        stack.damage(1, player, LivingEntity.getSlotForHand(Hand.MAIN_HAND));
        return true;
    }

    // ─── PASSIVE: Speed II while holding ─────────────────────────────────────
    public static void onPlayerTick(PlayerEntity player) {
        if (player.getWorld().isClient) return;
        boolean holding = player.getMainHandStack().getItem() instanceof Speed_Gem_Power
                || player.getOffHandStack().getItem() instanceof Speed_Gem_Power;

        if (holding) {
            StatusEffectInstance current = player.getStatusEffect(StatusEffects.SPEED);
            // Refresh every ~40 ticks so it doesn't flicker
            if (current == null || current.getDuration() < 40) {
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.SPEED, 80, 1, false, false)); // Speed II
            }
        }
    }
}
