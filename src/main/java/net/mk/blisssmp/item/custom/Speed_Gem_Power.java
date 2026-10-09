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
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SPEED GEM
 * Passive  – Velocity: permanent Speed II while holding (2x run speed).
 * Active 1 – Sonic Dash (right-click): dash forward fast in look direction. Cooldown: 6s.
 * Active 2 – Stun Strike (left-click hit): hit stuns mob/player for 2s (Slowness VI)
 *            + Slowness II for 4s after. Cooldown: 8s.
 */
public class Speed_Gem_Power extends Item {

    private static final Map<UUID, Long> lastDash  = new HashMap<>();
    private static final Map<UUID, Long> lastStun  = new HashMap<>();

    public Speed_Gem_Power(Properties settings) { super(settings); }
    private static void overlay(Player p, String msg) {
        if (p instanceof ServerPlayer sp) sp.sendSystemMessage(Component.literal(msg), true);
    }

    // ── Passive tick ──────────────────────────────────────────────────────
    public static void onPlayerTick(Player player) {
        if (player.level().isClientSide()) return;
        boolean holding = player.getMainHandItem().getItem() instanceof Speed_Gem_Power
                       || player.getOffhandItem().getItem() instanceof Speed_Gem_Power;
        if (holding && player.level().getGameTime() % 40 == 0) {
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 60, 1, false, false));
        }
    }

    // ── Right-click: Sonic Dash ───────────────────────────────────────────
    @Override
    public InteractionResult use(Level world, Player user, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        long now = world.getGameTime();
        Long lastD = lastDash.get(user.getUUID());
        int cdD = CooldownManager.speedDash();
        boolean dashCd = lastD != null && now - lastD < cdD;
        Long lastSt = lastStun.get(user.getUUID());
        int cdSt = CooldownManager.speedStun();
        boolean stunCd = lastSt != null && now - lastSt < cdSt;

        if (!world.isClientSide()) {
            overlay(user,
                (dashCd ? String.format("§b⚡ Sonic Dash: %ds", ((cdD-(now-lastD))+19)/20) : "§b⚡ Sonic Dash: Ready!")
                + "  "
                + (stunCd ? String.format("§b❄ Stun Strike: %ds", ((cdSt-(now-lastSt))+19)/20) : "§b❄ Stun Strike: Ready!"));
        }
        if (dashCd) return InteractionResult.FAIL;
        return doSonicDash(world, user);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) { return doSonicDash(ctx.getLevel(), ctx.getPlayer()); }

    private InteractionResult doSonicDash(Level world, Player player) {
        if (player == null) return InteractionResult.PASS;
        long now = world.getGameTime();
        Long last = lastDash.get(player.getUUID());
        if (last != null && now - last < CooldownManager.speedDash()) return InteractionResult.FAIL;

        if (!world.isClientSide()) {
            lastDash.put(player.getUUID(), now);
            Vec3 look = player.getViewVector(1f);
            player.setDeltaMovement(look.x * 3.0, look.y * 3.0 + 0.2, look.z * 3.0);
            player.hurtMarked = true;
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, 30, 3, false, false));

            if (world instanceof ServerLevel sl) {
                Vec3 pos = player.position();
                for (int i = 0; i < 20; i++)
                    sl.sendParticles(ParticleTypes.SWEEP_ATTACK,
                            pos.x + (Math.random()-0.5), pos.y + Math.random()*2,
                            pos.z + (Math.random()-0.5), 1, 0, 0, 0, 0.1);
            }
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BREEZE_SHOOT, player.getSoundSource(), 1f, 1.4f);
            overlay(player, "§b[Speed Gem] §eSonic Dash!");
        }
        return InteractionResult.SUCCESS;
    }

    // ── Left-click hit: Stun Strike ───────────────────────────────────────
    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!(attacker instanceof Player player)) return;
        Level world = player.level();
        long now = world.getGameTime();
        Long last = lastStun.get(player.getUUID());
        int cd = CooldownManager.speedStun();
        if (last != null && now - last < cd) {
            if (!world.isClientSide())
                overlay(player, String.format("§b❄ Stun Strike: %ds", ((cd-(now-last))+19)/20));
            return;
        }
        if (!world.isClientSide()) {
            lastStun.put(player.getUUID(), now);
            // 2s stun = Slowness VI (basically frozen), then linger Slowness II 4s
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40,  5, false, true)); // 2s freeze
            target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 120, 1, false, true)); // 4s slow after (lower amp but longer)
            if (world instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SNOWFLAKE,
                        target.getX(), target.getY()+1, target.getZ(), 15, 0.5, 0.8, 0.5, 0.05);
            }
            world.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.PLAYER_HURT_FREEZE, target.getSoundSource(), 1f, 1f);
            overlay(player, "§b[Speed Gem] §eStun Strike! Frozen 2s then slowed.");
        }
    }
}
