package net.mk.blisssmp.item.custom;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class Wealth_Gem_Power extends Item {

    private static final Map<UUID, Long> lastSurge = new HashMap<>();
    private static final Random RNG = new Random();

    public Wealth_Gem_Power(Item.Settings settings) {
        super(settings);
    }

    // ─── RIGHT-CLICK: Fortune Surge ──────────────────────────────────────────
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));

        long now = world.getTime();
        long last = lastSurge.getOrDefault(player.getUuid(), 0L);
        int cd = CooldownManager.wealthSurge();

        if (now - last < cd) {
            long remaining = (cd - (now - last)) / 20 + 1;
            player.sendMessage(Text.literal("§e[Wealth Gem] §cFortune Surge cooldown: " + remaining + "s"), true);
            return TypedActionResult.fail(player.getStackInHand(hand));
        }
        lastSurge.put(player.getUuid(), now);

        // Give Fortune III effect for 30 seconds (tracked separately by mining event)
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 600, 2, false, true));
        player.sendMessage(Text.literal("§e[Wealth Gem] §6Fortune Surge activated! (30s)"), true);

        player.getStackInHand(hand).damage(1, player, LivingEntity.getSlotForHand(hand));
        return TypedActionResult.success(player.getStackInHand(hand));
    }

    // ─── LEFT-CLICK HIT: Gold Drain ───────────────────────────────────────────
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof PlayerEntity player)) return false;
        if (attacker.getWorld().isClient) return false;

        // Drain gold nuggets from the target on hit
        int nuggets = 1 + RNG.nextInt(3); // 1-3 nuggets
        for (int i = 0; i < nuggets; i++) {
            ItemEntity nugget = new ItemEntity(
                    player.getWorld(),
                    target.getX(), target.getY() + 0.5, target.getZ(),
                    new ItemStack(Items.GOLD_NUGGET));
            nugget.setPickupDelay(0);
            player.getWorld().spawnEntity(nugget);
        }

        player.sendMessage(Text.literal("§e[Wealth Gem] §6Drained " + nuggets + " gold nuggets!"), true);
        stack.damage(1, player, LivingEntity.getSlotForHand(Hand.MAIN_HAND));
        return true;
    }

    // ─── MINING BONUS: Fortune III equivalent + 5% double drop ───────────────
    /**
     * Call this from PlayerBlockBreakEvents.AFTER when the player is holding a Wealth Gem.
     * Returns bonus drops to be spawned at the break position.
     */
    public static List<ItemStack> getExtraDrops(ItemStack stack) {
        List<ItemStack> extra = new ArrayList<>();
        // Fortune III-style: give 1-4 extra drops with weighted chance
        int bonus = 1 + RNG.nextInt(4); // 1 to 4 extra
        // We pass back an empty list here; the calling code should duplicate the primary drop
        // 5% chance for full duplicate
        boolean duplicate = RNG.nextFloat() < 0.05f;

        // Represent bonus as a count multiplier (caller spawns actual items)
        // We encode as a special marker item count
        // Actually, return an integer-worthy ItemStack count via NBT tag approach:
        // For simplicity, return `bonus` copies of a GOLD_NUGGET as signal;
        // caller spawns the real items.
        // Better: return simple List<Integer> wrapper... but that changes API.
        // Instead: signal via single ItemStack with count = bonus + (duplicate ? 100 : 0)
        ItemStack signal = new ItemStack(Items.GOLD_NUGGET, bonus + (duplicate ? 100 : 0));
        extra.add(signal);
        return extra;
    }

    /**
     * Simpler version: returns the drop bonus count (how many extras to give).
     * Call this from AFTER mining event. Returns 0-4 extra drops.
     * 5% chance to return the count + original stack.
     */
    public static int getBonusDropCount() {
        return 1 + RNG.nextInt(4); // Fortune III equivalent: 1-4 extra
    }

    public static boolean shouldDuplicateOre() {
        return RNG.nextFloat() < 0.05f; // 5% chance
    }
}
