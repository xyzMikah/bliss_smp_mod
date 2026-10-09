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
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FIRE GEM
 * Passive  – Ember Aura: permanent Fire Resistance while holding. 5% chance each tick to spawn
 *            a spark particle (visual only). While in lava you get Night Vision (lava vision).
 * Active 1 – Inferno Shot (shift+right-click): shoot a line of fire 20 blocks forward, burning
 *            all enemies in the path for 2 hearts + sets ground on fire. Knockback push included.
 *            Cooldown: 2s.
 * Active 2 – Soul Flamethrower (right-click): activate a 25-second soul-fire ring that deals
 *            bonus damage in lava and sets the ground ablaze with soul fire. Cooldown: 25s.
 */
public class Fire_Gem_Power extends Item {

    private static final int  RING_RADIUS   = 8;
    private static final int  ULT_DURATION  = 500; // 25s

    private static final Map<UUID, Long> lastShot      = new HashMap<>();
    private static final Map<UUID, Long> ultActiveUntil = new HashMap<>();

    public Fire_Gem_Power(Properties settings) { super(settings); }
    private static void overlay(Player p, String msg) {
        if (p instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal(msg), true);
    }

    // ── Passive tick ──────────────────────────────────────────────────────
    public static void onPlayerTick(Player player) {
        if (player.level().isClientSide()) return;
        boolean holding = player.getMainHandItem().getItem() instanceof Fire_Gem_Power
                       || player.getOffhandItem().getItem() instanceof Fire_Gem_Power;
        if (!holding) return;

        // Fire Resistance always on
        if (!player.hasEffect(MobEffects.FIRE_RESISTANCE) ||
                player.getEffect(MobEffects.FIRE_RESISTANCE).getDuration() < 40) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 60, 0, false, false));
        }

        // Lava vision
        if (player.isInLava()) {
            if (!player.hasEffect(MobEffects.NIGHT_VISION) ||
                    player.getEffect(MobEffects.NIGHT_VISION).getDuration() < 40) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 60, 0, false, false));
            }
        }

        // 5% chance spark particle per tick
        if (!((ServerLevel) player.level()).isClientSide() && Math.random() < 0.05) {
            ServerLevel sl = (ServerLevel) player.level();
            sl.sendParticles(ParticleTypes.FLAME,
                    player.getX() + (Math.random()-0.5)*0.8,
                    player.getY() + Math.random()*2,
                    player.getZ() + (Math.random()-0.5)*0.8,
                    1, 0, 0.05, 0, 0.02);
        }
    }

    // ── Right-click: Soul Flamethrower ring ult ───────────────────────────
    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        long now = world.getGameTime();

        if (user.isShiftKeyDown()) {
            // Shift+right-click = Inferno Shot
            return doInfernoShot(world, user);
        }

        long ultEnd = ultActiveUntil.getOrDefault(user.getUUID(), 0L);
        int cd = CooldownManager.fireUlt();
        boolean onCd = ultEnd != 0 && now < ultEnd + cd;
        Long lastS = lastShot.get(user.getUUID());
        boolean shotCd = lastS != null && now - lastS < CooldownManager.fireShot();

        if (!world.isClientSide()) {
            overlay(user,
                (now < ultEnd ? "§6🔥 Soul Ring: " + (ultEnd-now)/20 + "s active!" :
                 onCd ? String.format("§6🔥 Soul Ring: %ds", ((ultEnd+cd-now)+19)/20) : "§6🔥 Soul Ring: Ready!")
                + "  "
                + (shotCd ? String.format("§6💥 Inferno Shot: %ds", ((CooldownManager.fireShot()-(now-lastS))+19)/20) : "§6💥 Inferno Shot: Ready! (shift+RC)"));
        }
        if (now < ultEnd || onCd) return InteractionResult.FAIL;
        return doSoulRing(world, user);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player p = ctx.getPlayer();
        if (p != null && p.isShiftKeyDown()) return doInfernoShot(ctx.getLevel(), p);
        return doSoulRing(ctx.getLevel(), p);
    }

    private InteractionResult doSoulRing(Level world, Player player) {
        if (player == null) return InteractionResult.PASS;
        long now = world.getGameTime();
        long ultEnd = ultActiveUntil.getOrDefault(player.getUUID(), 0L);
        int cd = CooldownManager.fireUlt();
        if (now < ultEnd || (ultEnd != 0 && now < ultEnd + cd)) return InteractionResult.FAIL;

        if (!world.isClientSide()) {
            ultActiveUntil.put(player.getUUID(), now + ULT_DURATION);
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, ULT_DURATION + 40, 0, false, false));

            if (world instanceof ServerLevel sl) {
                placeSoulFireRing(sl, player.blockPosition(), RING_RADIUS);
                Vec3 pos = player.position();
                for (int i = 0; i < 36; i++) {
                    double a = Math.toRadians(i * 10);
                    sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                            pos.x + Math.cos(a)*4, pos.y + 0.5, pos.z + Math.sin(a)*4,
                            2, 0.2, 0.3, 0.2, 0.05);
                }
            }
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLAZE_SHOOT, player.getSoundSource(), 1f, 0.6f);
            overlay(player, "§6[Fire Gem] §eSoul Flamethrower! Ring deployed for 25s.");
        }
        return InteractionResult.SUCCESS;
    }

    // ── Shift+right-click: Inferno Shot (line of fire) ────────────────────
    private InteractionResult doInfernoShot(Level world, Player player) {
        if (player == null) return InteractionResult.PASS;
        long now = world.getGameTime();
        Long last = lastShot.get(player.getUUID());
        int cd = CooldownManager.fireShot();
        if (last != null && now - last < cd) {
            if (!world.isClientSide())
                overlay(player, String.format("§6💥 Inferno Shot: %ds", ((cd-(now-last))+19)/20));
            return InteractionResult.FAIL;
        }

        if (!world.isClientSide()) {
            lastShot.put(player.getUUID(), now);
            ServerLevel sl = (ServerLevel) world;
            Vec3 origin = player.getEyePosition();
            Vec3 look = player.getViewVector(1f);

            double len = 20.0;
            for (int i = 1; i <= 40; i++) {
                double t = i / 40.0;
                Vec3 pt = origin.add(look.scale(len * t));
                // Particles along the line
                sl.sendParticles(ParticleTypes.FLAME, pt.x, pt.y, pt.z, 3, 0.2, 0.2, 0.2, 0.04);
                // Set ground on fire every 2 steps
                if (i % 2 == 0) {
                    BlockPos bp = BlockPos.containing(pt.x, pt.y - 0.5, pt.z);
                    if (sl.getBlockState(bp).isSolidRender() && sl.getBlockState(bp.above()).isAir())
                        sl.setBlock(bp.above(), Blocks.FIRE.defaultBlockState(), 3);
                }
                // Hit entities in a small radius around each point
                AABB box = new AABB(pt.x-0.8, pt.y-0.8, pt.z-0.8, pt.x+0.8, pt.y+0.8, pt.z+0.8);
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, ent -> ent != player)) {
                    e.hurtServer(sl, player.damageSources().inFire(), 4f); // 2 hearts
                    e.setRemainingFireTicks(60);
                    // Knock back along line direction
                    e.setDeltaMovement(e.getDeltaMovement().add(look.scale(0.8)));
                    e.hurtMarked = true;
                }
            }
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLAZE_SHOOT, player.getSoundSource(), 1f, 1.3f);
            overlay(player, "§6[Fire Gem] §eInferno Shot!");
        }
        return InteractionResult.SUCCESS;
    }

    // ── Soul fire ring placement ───────────────────────────────────────────
    private static void placeSoulFireRing(ServerLevel world, BlockPos origin, int radius) {
        for (int r = 1; r <= radius; r++) {
            int ox = origin.getX(), oy = origin.getY(), oz = origin.getZ();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    BlockPos surface = findSurface(world, ox+dx, oy, oz+dz);
                    if (surface == null) continue;
                    BlockPos fire = surface.above();
                    if (world.getBlockState(fire).isAir())
                        world.setBlock(fire, Blocks.SOUL_FIRE.defaultBlockState(), 3);
                }
            }
        }
    }

    private static BlockPos findSurface(ServerLevel world, int x, int y, int z) {
        for (int dy = 2; dy >= -3; dy--) {
            BlockPos p = new BlockPos(x, y+dy, z);
            if (world.getBlockState(p).isSolidRender() && world.getBlockState(p.above()).isAir()) return p;
        }
        return null;
    }

    public static boolean isUltActive(Player player) {
        return player.level().getGameTime() < ultActiveUntil.getOrDefault(player.getUUID(), 0L);
    }
}
