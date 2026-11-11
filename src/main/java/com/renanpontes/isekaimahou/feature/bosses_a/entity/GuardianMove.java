package com.renanpontes.isekaimahou.feature.bosses_a.entity;

/**
 * Um golpe de chefe: duração (= duração da animação "animation.&lt;chefe&gt;.&lt;id&gt;"), recarga, alcance,
 * fases em que pode ser usado e peso no sorteio. O chefe executa tick a tick em {@code performMove}.
 */
public final class GuardianMove {
    public final String id;
    public final int duration;
    private int cooldown = 60;
    private double minRange = 0;
    private double maxRange = 32;
    private int minPhase = 1;
    private int maxPhase = 3;
    private int weight = 10;
    private boolean announce;
    private boolean rooted = true;
    private boolean forced;
    private int aimUntil = Integer.MAX_VALUE;

    private GuardianMove(String id, int duration) {
        this.id = id;
        this.duration = duration;
    }

    public static GuardianMove of(String id, int duration) {
        return new GuardianMove(id, duration);
    }

    public GuardianMove cooldown(int ticks) { this.cooldown = ticks; return this; }
    public GuardianMove range(double min, double max) { this.minRange = min; this.maxRange = max; return this; }
    public GuardianMove phases(int min, int max) { this.minPhase = min; this.maxPhase = max; return this; }
    public GuardianMove weight(int w) { this.weight = w; return this; }
    /** Anuncia o nome do golpe na tela (estilo chefe). */
    public GuardianMove announce() { this.announce = true; return this; }
    /** O chefe controla o próprio movimento durante o golpe (avanços, mergulhos); a perseguição normal para. */
    public GuardianMove selfMoving() { this.rooted = false; return this; }
    /** Nunca sorteado: só começa quando o chefe força (anti-trapaça, roteiro). */
    public GuardianMove forced() { this.forced = true; return this; }
    /**
     * O chefe só gira para o alvo até este tick do golpe; depois a direção fica TRAVADA (a telegrafia desenhada no
     * chão é exatamente onde o golpe acerta — quem sai dela desvia).
     */
    public GuardianMove aimUntil(int tick) { this.aimUntil = tick; return this; }

    public int cooldown() { return cooldown; }
    public double minRange() { return minRange; }
    public double maxRange() { return maxRange; }
    public int weight() { return weight; }
    public boolean announces() { return announce; }
    public boolean rooted() { return rooted; }
    public boolean isForced() { return forced; }
    public int aimUntil() { return aimUntil; }

    public boolean allowedIn(int phase) {
        return !forced && phase >= minPhase && phase <= maxPhase;
    }
}
