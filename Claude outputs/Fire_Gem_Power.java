package net.mk.blisssmp.item.custom;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityTypes;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Fire_Gem_Power extends Item {

    private static final int ULT_DURATION = 1200; // 60 seconds at 20 tps
    private static final int RING_RADIUS  = 8;

    // Track when each player activated their ult (server tick time)
    private static final Map<UUID, Long> ultActiveUntil = new HashMap<>();
    // Track last fireball launch time per player
    private static final Map<UUID, Long> lastFireball   = new HashMap<>();

    public Fire_Gem_Power(Item.Settings settings) {
        super(settings);
    }

    // ─── RIGHT-CLICK: Fire Ult ───────────────────────────────────────────────
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient) return TypedActionResult.pass(player.getStackInHand(hand));

        ServerWorld serverWorld = (ServerWorld) world;
        long now = world.getTime();
        long ultEndTick = ultActiveUntil.getOrDefault(player.getUuid(), 0L);

        // If ult is still active, block re-cast
        if (now < ultEndTick) {
            long remaining = (ultEndTick - now) / 20;
            player.sendMessage(Text.literal("§6[Fire Gem] §cFire Ult active! " + remaining + "s remaining"), true);
            return TypedActionResult.fail(player.getStackInHand(hand));
        }

        // Check cooldown (after ult expires)
        int cd = CooldownManager.fireUlt();
        long cooldownEnd = ultEndTick + cd;
        if (now < cooldownEnd && ultEndTick != 0) {
            long remaining = (cooldownEnd - now) / 20 + 1;
            player.sendMessage(Text.literal("§6[Fire Gem] §cCooldown: " + remaining + "s"), true);
            return TypedActionResult.fail(player.getStackInHand(hand));
        }

        // Activate ult
        ultActiveUntil.put(player.getUuid(), now + ULT_DURATION);

        // Fire Resistance for 75 seconds (ULT_DURATION + buffer)
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 1500, 0, false, false));

        // Expanding fire ring
        BlockPos origin = player.getBlockPos();
        expandFireRing(serverWorld, origin, RING_RADIUS);

        player.sendMessage(Text.literal("§6[Fire Gem] §eFire Ult active! Fire ring deployed."), true);
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 0.8f);

        player.getStackInHand(hand).damage(1, player, LivingEntity.getSlotForHand(hand));
        return TypedActionResult.success(player.getStackInHand(hand));
    }

    // ─── LEFT-CLICK HIT: Burn ────────────────────────────────────────────────
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof PlayerEntity player)) return false;
        if (attacker.getWorld().isClient) return false;

        if (player.isShiftKeyDown()) {
            // SHIFT+LEFT: launch fireball
            launchFireball(player, (ServerWorld) player.getWorld());
        } else {
            // LEFT: burn target always
            target.setOnFireFor(5); // 5 seconds
            // Extra fire damage: 6% of max HP
            target.damage(player.getWorld().getDamageSources().inFire(),
                    target.getMaxHealth() * 0.06f);
            player.sendMessage(Text.literal("§6[Fire Gem] §eBurning!"), true);
        }

        stack.damage(1, player, LivingEntity.getSlotForHand(Hand.MAIN_HAND));
        return true;
    }

    private void launchFireball(PlayerEntity player, ServerWorld world) {
        long now = world.getTime();
        long last = lastFireball.getOrDefault(player.getUuid(), 0L);
        int cd = CooldownManager.fireBall();
        if (now - last < cd) {
            long remaining = (cd - (now - last)) / 20 + 1;
            player.sendMessage(Text.literal("§6[Fire Gem] §cFireball cooldown: " + remaining + "s"), true);
            return;
        }
        lastFireball.put(player.getUuid(), now);

        Vec3d look = player.getRotationVec(1.0f);
        Vec3d pos  = player.getEyePos();

        // Spawn a small fireball via the entity type registry (avoids direct class import)
        var fireball = EntityTypes.SMALL_FIREBALL.create(world);
        if (fireball != null) {
            fireball.setPos(pos.x + look.x * 1.5, pos.y + look.y * 1.5, pos.z + look.z * 1.5);
            fireball.setVelocity(look.x * 2.0, look.y * 2.0, look.z * 2.0);
            world.spawnEntity(fireball);
        }

        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.PLAYERS, 1.0f, 1.2f);
        player.sendMessage(Text.literal("§6[Fire Gem] §eLaunched fireball!"), true);
    }

    // ─── PASSIVE: fire resistance while holding ───────────────────────────────
    public static void onPlayerTick(PlayerEntity player) {
        if (player.getWorld().isClient) return;
        boolean holding = player.getMainHandStack().getItem() instanceof Fire_Gem_Power
                || player.getOffHandStack().getItem() instanceof Fire_Gem_Power;
        if (holding) {
            StatusEffectInstance current = player.getStatusEffect(StatusEffects.FIRE_RESISTANCE);
            if (current == null || current.getDuration() < 40) {
                player.addStatusEffect(new StatusEffectInstance(
                        StatusEffects.FIRE_RESISTANCE, 60, 0, false, false));
            }
        }
    }

    // ─── FIRE RING HELPER ─────────────────────────────────────────────────────
    private static void expandFireRing(ServerWorld world, BlockPos origin, int maxRadius) {
        for (int r = 1; r <= maxRadius; r++) {
            placeFirePerimeter(world, origin, r);
        }
    }

    private static void placeFirePerimeter(ServerWorld world, BlockPos origin, int radius) {
        int ox = origin.getX();
        int oy = origin.getY();
        int oz = origin.getZ();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (Math.abs(dx) == radius || Math.abs(dz) == radius) {
                    BlockPos surface = findSurface(world, ox + dx, oy, oz + dz);
                    if (surface != null) {
                        BlockPos firePos = surface.up();
                        if (world.getBlockState(firePos).isAir()) {
                            world.setBlockState(firePos, Blocks.FIRE.getDefaultState());
                        }
                    }
                }
            }
        }
    }

    private static BlockPos findSurface(ServerWorld world, int x, int yStart, int z) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos pos = new BlockPos(x, yStart + dy, z);
            if (world.getBlockState(pos).isSolidBlock(world, pos)
                    && world.getBlockState(pos.up()).isAir()) {
                return pos;
            }
        }
        return null;
    }

    // ─── ULT STATE QUERIES ────────────────────────────────────────────────────
    public static boolean isUltActive(PlayerEntity player) {
        long now = player.getWorld().getTime();
        long endTick = ultActiveUntil.getOrDefault(player.getUuid(), 0L);
        return now < endTick;
    }
}
