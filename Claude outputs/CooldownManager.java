package net.mk.blisssmp.item.custom;

/**
 * Centralized cooldown manager for all Bliss SMP gem power classes.
 * Toggle between testing mode (0 ticks) and normal production values
 * via the /bliss cd command.
 */
public class CooldownManager {

    private static boolean testingMode = false;

    // ── Fire Gem ──────────────────────────────────────────────────────────────
    public static final int FIRE_ULT_NORMAL    = 1200; // 60s
    public static final int FIRE_BALL_NORMAL   = 40;   // 2s

    // ── Speed Gem ─────────────────────────────────────────────────────────────
    public static final int SPEED_DASH_NORMAL  = 60;   // 3s
    public static final int SPEED_BLINK_NORMAL = 100;  // 5s

    // ── Puff Gem ──────────────────────────────────────────────────────────────
    public static final int PUFF_LAUNCH_NORMAL = 60;   // 3s
    public static final int PUFF_GUST_NORMAL   = 80;   // 4s

    // ── Flux Gem ──────────────────────────────────────────────────────────────
    public static final int FLUX_STORM_NORMAL  = 100;  // 5s
    public static final int FLUX_ZAP_NORMAL    = 40;   // 2s

    // ── Life Gem ──────────────────────────────────────────────────────────────
    public static final int LIFE_HEAL_NORMAL   = 600;  // 30s
    public static final int LIFE_STEAL_NORMAL  = 160;  // 8s
    public static final int LIFE_SAVE_NORMAL   = 12000; // 10 min

    // ── Wealth Gem ────────────────────────────────────────────────────────────
    public static final int WEALTH_SURGE_NORMAL = 400; // 20s

    // ── Strength Gem ─────────────────────────────────────────────────────────
    public static final int STRENGTH_NORMAL    = 100;  // 5s

    // ── Astra Gem ─────────────────────────────────────────────────────────────
    public static final int ASTRA_NORMAL       = 200;  // 10s

    // ─────────────────────────────────────────────────────────────────────────

    public static boolean isTestingMode() {
        return testingMode;
    }

    public static void setTestingMode(boolean testing) {
        testingMode = testing;
    }

    /** Returns 1 in testing mode, or the provided normal value otherwise. */
    public static int get(int normalValue) {
        return testingMode ? 1 : normalValue;
    }

    // ── Convenience getters ──────────────────────────────────────────────────

    public static int fireUlt()       { return get(FIRE_ULT_NORMAL); }
    public static int fireBall()      { return get(FIRE_BALL_NORMAL); }

    public static int speedDash()     { return get(SPEED_DASH_NORMAL); }
    public static int speedBlink()    { return get(SPEED_BLINK_NORMAL); }

    public static int puffLaunch()    { return get(PUFF_LAUNCH_NORMAL); }
    public static int puffGust()      { return get(PUFF_GUST_NORMAL); }

    public static int fluxStorm()     { return get(FLUX_STORM_NORMAL); }
    public static int fluxZap()       { return get(FLUX_ZAP_NORMAL); }

    public static int lifeHeal()      { return get(LIFE_HEAL_NORMAL); }
    public static int lifeSteal()     { return get(LIFE_STEAL_NORMAL); }
    public static int lifeSave()      { return get(LIFE_SAVE_NORMAL); }

    public static int wealthSurge()   { return get(WEALTH_SURGE_NORMAL); }
}
