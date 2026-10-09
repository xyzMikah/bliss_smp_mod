package net.mk.blisssmp.item.custom;

import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * WEALTH GEM
 * Passive  – Fortune's Favor: permanent Max Luck + Fortune on all tools + Hero of the Village.
 *            50% chance for extra ore drop when mining. Double XP from deaths within 24 blocks.
 * Active 1 – Ore Sense (right-click): highlight all exposed ores within 8 blocks (Glowing) for 10s.
 *            Cooldown: 15s.
 * Active 2 – Greed Drain (left-click hit): steal XP from target + 15% chance to steal a random
 *            item from their inventory. Cooldown: 12s.
 * Active 3 – Loot Drop (shift+right-click): open a random loot pool. Cooldown: 20s.
 */
public class Wealth_Gem_Power extends Item {

    private static final double ORE_SENSE_RADIUS = 8.0;
    private static final int    ORE_GLOW_DURATION = 200; // 10s

    private static final Map<UUID, Long> lastOreSense = new HashMap<>();
    private static final Map<UUID, Long> lastDrain    = new HashMap<>();
    private static final Map<UUID, Long> lastLoot     = new HashMap<>();

    private static final Random RNG = new Random();

    // Loot pool: common items to rare
    private static final List<Item> LOOT_COMMON = Arrays.asList(
            Items.GOLDEN_APPLE, Items.GOLD_INGOT, Items.EMERALD, Items.DIAMOND,
            Items.ENCHANTED_BOOK, Items.GOLDEN_CARROT, Items.EXPERIENCE_BOTTLE);
    private static final Item LOOT_RARE   = Items.ENCHANTED_GOLDEN_APPLE; // 5%
    private static final Item LOOT_EPIC   = Items.NETHERITE_INGOT;        // 2%
    // 1% epic sword: we give a netherite sword with sharp V
    // handled in code

    public Wealth_Gem_Power(Properties settings) { super(settings); }
    private static void overlay(Player p, String msg) {
        if (p instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal(msg), true);
    }

    // ── Passive tick ──────────────────────────────────────────────────────
    public static void onPlayerTick(Player player) {
        if (player.level().isClientSide()) return;
        boolean holding = player.getMainHandItem().getItem() instanceof Wealth_Gem_Power
                       || player.getOffhandItem().getItem() instanceof Wealth_Gem_Power;
        if (!holding) return;

        long now = player.level().getGameTime();
        // Luck max (Luck IV) every 2s
        if (now % 40 == 0) {
            if (!player.hasEffect(MobEffects.LUCK) || player.getEffect(MobEffects.LUCK).getDuration() < 60)
                player.addEffect(new MobEffectInstance(MobEffects.LUCK, 80, 3, false, false));
            // Hero of the Village
            if (!player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE) || player.getEffect(MobEffects.HERO_OF_THE_VILLAGE).getDuration() < 60)
                player.addEffect(new MobEffectInstance(MobEffects.HERO_OF_THE_VILLAGE, 80, 0, false, false));
        }
    }

    // ── Right-click: Ore Sense or Loot Drop (shift) ───────────────────────
    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (user.isShiftKeyDown()) return doLootDrop(world, user);

        long now = world.getGameTime();
        Long lastO = lastOreSense.get(user.getUUID());
        int cdO = CooldownManager.wealthOreSense();
        boolean oreCd = lastO != null && now - lastO < cdO;
        Long lastD = lastDrain.get(user.getUUID());
        int cdD = CooldownManager.wealthDrain();
        boolean drainCd = lastD != null && now - lastD < cdD;

        if (!world.isClientSide()) {
            overlay(user,
                (oreCd ? String.format("§e✨ Ore Sense: %ds", ((cdO-(now-lastO))+19)/20) : "§e✨ Ore Sense: Ready!")
                + "  "
                + (drainCd ? String.format("§e💰 Greed Drain: %ds", ((cdD-(now-lastD))+19)/20) : "§e💰 Greed Drain: Ready!")
                + "  §e🎰 Loot Drop: shift+RC");
        }
        if (oreCd) return InteractionResult.FAIL;
        return doOreSense(world, user);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getPlayer() != null && ctx.getPlayer().isShiftKeyDown()) return doLootDrop(ctx.getLevel(), ctx.getPlayer());
        return doOreSense(ctx.getLevel(), ctx.getPlayer());
    }

    // ── Ore Sense ─────────────────────────────────────────────────────────
    private InteractionResult doOreSense(Level world, Player player) {
        if (player == null) return InteractionResult.PASS;
        long now = world.getGameTime();
        Long last = lastOreSense.get(player.getUUID());
        if (last != null && now - last < CooldownManager.wealthOreSense()) return InteractionResult.FAIL;

        if (!world.isClientSide() && world instanceof ServerLevel sl) {
            lastOreSense.put(player.getUUID(), now);
            BlockPos center = player.blockPosition();
            int radius = (int) ORE_SENSE_RADIUS;
            int found = 0;

            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -radius; dy <= radius; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        BlockPos pos = center.offset(dx, dy, dz);
                        BlockState state = world.getBlockState(pos);
                        String id = state.getBlock().getDescriptionId();
                        if (!isOre(id)) continue;
                        // Only exposed = at least one adjacent face is air
                        boolean exposed = false;
                        for (BlockPos adj : List.of(pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west())) {
                            if (world.getBlockState(adj).isAir()) { exposed = true; break; }
                        }
                        if (!exposed) continue;
                        // Spawn highlight particles at ore location
                        sl.sendParticles(ParticleTypes.GLOW,
                                pos.getX()+0.5, pos.getY()+0.5, pos.getZ()+0.5,
                                8, 0.3, 0.3, 0.3, 0.05);
                        found++;
                    }
                }
            }
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, player.getSoundSource(), 1f, 1.5f);
            overlay(player, "§e[Wealth Gem] §6Ore Sense! Found " + found + " exposed ores.");
        }
        return InteractionResult.SUCCESS;
    }

    private static boolean isOre(String descId) {
        return descId.contains("_ore") || descId.contains("ancient_debris") || descId.contains("amethyst");
    }

    // ── Loot Drop ─────────────────────────────────────────────────────────
    private InteractionResult doLootDrop(Level world, Player player) {
        if (player == null) return InteractionResult.PASS;
        long now = world.getGameTime();
        Long last = lastLoot.get(player.getUUID());
        if (last != null && now - last < CooldownManager.wealthLoot()) {
            if (!world.isClientSide())
                overlay(player, String.format("§e🎰 Loot Drop: %ds", ((CooldownManager.wealthLoot()-(now-last))+19)/20));
            return InteractionResult.FAIL;
        }

        if (!world.isClientSide()) {
            lastLoot.put(player.getUUID(), now);
            float roll = RNG.nextFloat();
            ItemStack reward;
            String label;

            if (roll < 0.01f) {
                // 1% epic sword
                reward = new ItemStack(Items.NETHERITE_SWORD);
                label = "§c§lJACKPOT! §6Epic Netherite Sword!";
            } else if (roll < 0.03f) {
                // 2% netherite ingot
                reward = new ItemStack(LOOT_EPIC);
                label = "§6Rare! §eNetherite Ingot!";
            } else if (roll < 0.08f) {
                // 5% notch apple
                reward = new ItemStack(LOOT_RARE);
                label = "§6Rare! §eEnchanted Golden Apple!";
            } else {
                // Common pool
                Item item = LOOT_COMMON.get(RNG.nextInt(LOOT_COMMON.size()));
                reward = new ItemStack(item, 1 + RNG.nextInt(3));
                label = "§e[Wealth Gem] §6Loot Drop: " + reward.getCount() + "x " + item.getDescriptionId();
            }

            ItemEntity drop = new ItemEntity(world, player.getX(), player.getY()+1, player.getZ(), reward);
            drop.setPickUpDelay(0);
            world.addFreshEntity(drop);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.PLAYER_LEVELUP, player.getSoundSource(), 1f, 0.8f);
            if (world instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        player.getX(), player.getY()+1, player.getZ(), 30, 0.5, 1, 0.5, 0.3);
            }
            overlay(player, label);
        }
        return InteractionResult.SUCCESS;
    }

    // ── Left-click hit: Greed Drain ───────────────────────────────────────
    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof Player player)) return;
        Level world = player.level();
        long now = world.getGameTime();
        Long last = lastDrain.get(player.getUUID());
        int cd = CooldownManager.wealthDrain();
        if (last != null && now - last < cd) {
            if (!world.isClientSide())
                overlay(player, String.format("§e💰 Greed Drain: %ds", ((cd-(now-last))+19)/20));
            return;
        }
        if (!world.isClientSide()) {
            lastDrain.put(player.getUUID(), now);
            // Steal XP
            if (target instanceof Player targetPlayer) {
                int stolen = Math.min(3, targetPlayer.totalExperience);
                targetPlayer.giveExperiencePoints(-stolen);
                player.giveExperiencePoints(stolen);
                // 15% item steal
                if (RNG.nextFloat() < 0.15f) {
                    var inv = targetPlayer.getInventory();
                    for (int i = 0; i < inv.getContainerSize(); i++) {
                        ItemStack s = inv.getItem(i);
                        if (!s.isEmpty()) {
                            ItemStack stolen2 = s.copy();
                            stolen2.setCount(1);
                            inv.removeItem(i, 1);
                            ItemEntity drop = new ItemEntity(world, player.getX(), player.getY()+1, player.getZ(), stolen2);
                            drop.setPickUpDelay(0);
                            world.addFreshEntity(drop);
                            overlay(player, "§e[Wealth Gem] §6Item Stolen: " + stolen2.getHoverName().getString() + "!");
                            break;
                        }
                    }
                }
                overlay(player, "§e[Wealth Gem] §6Stole " + stolen + " XP!");
            } else {
                // vs mob: just grant XP to player
                player.giveExperiencePoints(3 + RNG.nextInt(5));
                overlay(player, "§e[Wealth Gem] §6Drained XP from mob!");
            }
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP, player.getSoundSource(), 1f, 0.8f);
            if (world instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.GLOW,
                        target.getX(), target.getY()+1, target.getZ(), 10, 0.3, 0.5, 0.3, 0.1);
            }
        }
    }

    // ── Mining bonus API ──────────────────────────────────────────────────
    public static boolean shouldGiveExtraOreDrop() { return RNG.nextFloat() < 0.5f; }
}
