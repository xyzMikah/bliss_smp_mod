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

public class Astra_Gem_Power extends Item {

    private static final Map<UUID, Long> lastAbility = new HashMap<>();

    public Astra_Gem_Power(Item.Settings settings) {
        super(settings);
    }

    // ─── RIGHT-CLICK: Phase Dash (pass through 1 block) ─────────────────────
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));

        long now = world.getTime();
        long last = lastAbility.getOrDefault(player.getUuid(), 0L);
        int cd = CooldownManager.get(CooldownManager.ASTRA_NORMAL);

        if (now - last < cd) {
            long remaining = (cd - (now - last)) / 20 + 1;
            player.sendMessage(Text.literal("§5[Astra Gem] §cCooldown: " + remaining + "s"), true);
            return TypedActionResult.fail(player.getStackInHand(hand));
        }
        lastAbility.put(player.getUuid(), now);

        // Teleport 8 blocks in look direction
        Vec3d look = player.getRotationVec(1.0f);
        double tx = player.getX() + look.x * 8;
        double ty = player.getY() + look.y * 8;
        double tz = player.getZ() + look.z * 8;
        player.teleport(tx, ty, tz);

        // Brief invulnerability
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 20, 4, false, false));
        player.sendMessage(Text.literal("§5[Astra Gem] §ePhase dash!"), true);

        player.getStackInHand(hand).damage(1, player, LivingEntity.getSlotForHand(hand));
        return TypedActionResult.success(player.getStackInHand(hand));
    }

    // ─── LEFT-CLICK HIT: Void Touch ──────────────────────────────────────────
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof PlayerEntity player)) return false;
        if (attacker.getWorld().isClient) return false;

        // Levitate target
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 40, 1, false, true));
        player.sendMessage(Text.literal("§5[Astra Gem] §eVoid Touch!"), true);

        stack.damage(1, player, LivingEntity.getSlotForHand(Hand.MAIN_HAND));
        return true;
    }
}
