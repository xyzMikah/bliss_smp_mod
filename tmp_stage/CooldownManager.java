package net.mk.blisssmp.item.custom;

public class CooldownManager {

    private static boolean testing = false;

    public static void setTestingMode(boolean t) { testing = t; }
    public static boolean isTestingMode()         { return testing; }

    /** Returns 1 in testing mode, or normalValue in production. */
    public static int get(int normalValue) { return testing ? 0 : normalValue; }

    // ── ASTRA GEM ─────────────────────────────────────────────────────────
    public static int astraBoom()   { return get(600);  } // 30s — Sonic Boom
    public static int astraSnare()  { return get(200);  } // 10s — Sculk Snare

    // ── FIRE GEM ──────────────────────────────────────────────────────────
    public static int fireUlt()     { return get(500);  } // 25s — Soul Flamethrower ring
    public static int fireShot()    { return get(40);   } //  2s — Inferno Shot

    // ── FLUX GEM ──────────────────────────────────────────────────────────
    public static int fluxThunder() { return get(2400); } // 2 min — Thundergod
    public static int fluxZap()     { return get(60);   } //  3s — Arc Tag
    public static int fluxLine()    { return get(500);  } // 25s — Lightning Line

    // ── LIFE GEM ──────────────────────────────────────────────────────────
    public static int lifeSteal()   { return get(60);   } //  3s — Life Steal
    public static int lifeBank()    { return get(60);   } //  3s — Blood Bank use
    public static int lifeSave()    { return get(12000);} // 10 min — Death Save

    // ── PUFF GEM ──────────────────────────────────────────────────────────
    public static int puffVortex()  { return get(120);  } //  6s — Vortex Chain
    public static int puffLaunch()  { return get(160);  } //  8s — Jet Launch
    public static int puffSkyRip()  { return get(240);  } // 12s — Sky Rip

    // ── SPEED GEM ─────────────────────────────────────────────────────────
    public static int speedDash()   { return get(120);  } //  6s — Sonic Dash
    public static int speedStun()   { return get(160);  } //  8s — Stun Strike

    // ── WEALTH GEM ────────────────────────────────────────────────────────
    public static int wealthOreSense(){ return get(300); } // 15s — Ore Sense
    public static int wealthDrain()   { return get(240); } // 12s — Greed Drain
    public static int wealthLoot()    { return get(400); } // 20s — Loot Drop

    // ── STRENGTH GEM ──────────────────────────────────────────────────────
    public static int strengthBerserk()  { return get(200); } // 10s — Berserker Strike
    public static int strengthDeathWish(){ return get(500); } // 25s — Death Wish
}
