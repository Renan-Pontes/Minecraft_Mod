package com.renanpontes.isekaimahou.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.core.ux.PeaceMode;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Maestria INFINITA — as MUTAÇÕES. Depois do X ({@link GlyphMastery#MAX_LEVEL}) a magia não só fica mais forte: em
 * níveis marcados ela muda de comportamento e de visual, cada vez mais BIZARRA. Tudo é determinístico (glifo + nível;
 * nada de sorte) e só vale para conjurações de JOGADOR pela tecla ({@link SpellContext#isPlayerCast()}).
 *
 * <h3>Quem decide cada mutação</h3>
 * <ul>
 *   <li>A FORMA (nível da forma): {@link Family#PROJECTILE} (Projétil, Feixe...: fragmentos e eco tardio),
 *       {@link Family#AREA} (formas de área/zona: réplicas, vórtice, eco), {@link Family#CLOSE} (toque, si mesmo,
 *       domínio, ultimate: pulso e revelação).</li>
 *   <li>O EFEITO (nível do efeito que causou o dano): {@link Family#HARM} (impacto pesado, arcos, resíduo, ruptura),
 *       {@link Family#HELP} (bênçãos em quem está perto). {@link Family#WORK} (quebrar/colher) só tem as visuais.</li>
 *   <li>A MAGIA INTEIRA (média da forma e dos efeitos, igual ao {@link GlyphMastery#visualTier}): círculo desperto,
 *       cósmico, profano, eclipse (o céu escurece), o mundo treme, pilar singular.</li>
 *   <li>Aumentos, gatilhos, ligações e leis ({@link Family#SUPPORT}) não mutam: só ficam mais baratos.</li>
 * </ul>
 *
 * <h3>Limites (desempenho e equilíbrio)</h3>
 * Ecos/fragmentos: no máx. {@link #ECHO_BUDGET} conjurações extras a cada {@link #ECHO_WINDOW} ticks por jogador, e
 * eles NÃO dão maestria nem mutam de novo (são {@code castFrom}, não conjuração de jogador). Arcos ≤ {@link #MAX_ARCS};
 * resíduos ≤ {@link #MAX_RESIDUES} por jogador e {@link #MAX_RESIDUES_GLOBAL} no servidor; cada mutação de acerto tem
 * intervalo próprio por jogador (uma Chuva em 30 alvos não vira 30 réplicas). Dano extra: metade em jogadores e
 * chefes; áreas extras só pegam inimigos (monstros ou quem mira o conjurador) — nunca aldeões, animais ou jogadores à
 * toa. Nenhuma mutação mexe em blocos. Os visuais usam {@link ElementFx} (o cliente aplica o preset gráfico/FxQuality).
 */
public final class MasteryMutations {
    /** Família de um glifo para as mutações. */
    public enum Family {
        PROJECTILE, AREA, CLOSE, HARM, HELP, WORK, SUPPORT;

        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    /**
     * Uma mutação: chave de texto, nível em que desperta e se é da MAGIA INTEIRA ({@code shared}: conta a média).
     * Textos: {@code mastery.isekai_mahou.mut.<key>}, {@code .desc} (o que faz) e {@code .hint} (dica vaga).
     */
    public record Mut(String key, int level, boolean shared) {
        public MutableComponent displayName() { return Component.translatable("mastery.isekai_mahou.mut." + key); }
        public MutableComponent description() { return Component.translatable("mastery.isekai_mahou.mut." + key + ".desc"); }
        public MutableComponent hint() { return Component.translatable("mastery.isekai_mahou.mut." + key + ".hint"); }
    }

    // ---------- Magia inteira (média) ----------
    public static final int HALO = 11, COSMIC = 20, ELDRITCH = 35, ECLIPSE = 50, QUAKE = 75, SINGULAR = 100;
    // ---------- Forma: projétil ----------
    public static final int FRAGMENT = 12, ECHO = 18, FRAGMENT_2 = 25, ECHO_2 = 40, SWARM = 60, ECHO_3 = 100;
    // ---------- Forma: área ----------
    public static final int AFTERSHOCK = 12, VORTEX = 18, AFTERSHOCK_2 = 25, AREA_ECHO = 40, SINGULARITY = 60, AFTERSHOCK_3 = 100;
    // ---------- Forma: perto/si mesmo ----------
    public static final int PULSE = 12, REVEAL = 18, PULSE_2 = 25, ABYSS_PULSE = 60;
    // ---------- Efeito: dano ----------
    public static final int HEAVY = 11, ARC = 15, RESIDUE = 22, ARC_2 = 30, RUPTURE = 50, ARC_3 = 75, RESIDUE_2 = 100;
    // ---------- Efeito: ajuda ----------
    public static final int BLESSING = 15, AEGIS = 30, BLESSING_2 = 50, GRACE = 100;

    // ---------- Limites ----------
    public static final int ECHO_BUDGET = 6;
    public static final int ECHO_WINDOW = 40;
    public static final int MAX_ARCS = 3;
    public static final int MAX_RESIDUES = 3;
    public static final int MAX_RESIDUES_GLOBAL = 40;
    private static final double ARC_RANGE = 6.0;

    private static final List<Mut> SHARED = List.of(
            new Mut("halo", HALO, true), new Mut("cosmic", COSMIC, true), new Mut("eldritch", ELDRITCH, true),
            new Mut("eclipse", ECLIPSE, true), new Mut("quake", QUAKE, true), new Mut("singular", SINGULAR, true));
    private static final Map<Family, List<Mut>> ALL = new EnumMap<>(Family.class);

    static {
        Map<Family, List<Mut>> own = new EnumMap<>(Family.class);
        own.put(Family.PROJECTILE, List.of(new Mut("fragment", FRAGMENT, false), new Mut("echo", ECHO, false),
                new Mut("fragment_2", FRAGMENT_2, false), new Mut("echo_2", ECHO_2, false),
                new Mut("swarm", SWARM, false), new Mut("echo_3", ECHO_3, false)));
        own.put(Family.AREA, List.of(new Mut("aftershock", AFTERSHOCK, false), new Mut("vortex", VORTEX, false),
                new Mut("aftershock_2", AFTERSHOCK_2, false), new Mut("area_echo", AREA_ECHO, false),
                new Mut("singularity", SINGULARITY, false), new Mut("aftershock_3", AFTERSHOCK_3, false)));
        own.put(Family.CLOSE, List.of(new Mut("pulse", PULSE, false), new Mut("reveal", REVEAL, false),
                new Mut("pulse_2", PULSE_2, false), new Mut("abyss_pulse", ABYSS_PULSE, false)));
        own.put(Family.HARM, List.of(new Mut("heavy", HEAVY, false), new Mut("arc", ARC, false),
                new Mut("residue", RESIDUE, false), new Mut("arc_2", ARC_2, false), new Mut("rupture", RUPTURE, false),
                new Mut("arc_3", ARC_3, false), new Mut("residue_2", RESIDUE_2, false)));
        own.put(Family.HELP, List.of(new Mut("blessing", BLESSING, false), new Mut("aegis", AEGIS, false),
                new Mut("blessing_2", BLESSING_2, false), new Mut("grace", GRACE, false)));
        own.put(Family.WORK, List.of());
        for (Family f : Family.values()) {
            if (f == Family.SUPPORT) {
                ALL.put(f, List.of());
                continue;
            }
            List<Mut> l = new ArrayList<>(own.getOrDefault(f, List.of()));
            l.addAll(SHARED);
            l.sort(Comparator.comparingInt(Mut::level).thenComparing(m -> m.shared() ? 1 : 0));
            ALL.put(f, List.copyOf(l));
        }
    }

    // =====================================================================================
    //  Tabela (cliente e servidor)
    // =====================================================================================

    /** Família do glifo (decide quais mutações ele tem). */
    public static Family family(@Nullable Glyph g) {
        if (g instanceof FormGlyph f) {
            if (f.isUltimate() || f.acceptsLaws()) return Family.CLOSE;
            if (f.hintRadius() > 0F || f.isPersistent()) return Family.AREA;
            if (f.stageable() && f.hintRange() >= 8F) return Family.PROJECTILE;
            return Family.CLOSE;
        }
        if (g instanceof EffectGlyph e) {
            if (e.isBeneficial()) return Family.HELP;
            if (e.baseDamage() <= 0F && e.wantsBlocks()) return Family.WORK;
            return Family.HARM;
        }
        return Family.SUPPORT;
    }

    public static MutableComponent familyName(Family f) {
        return Component.translatable("mastery.isekai_mahou.family." + f.key());
    }

    /** Todas as mutações do glifo, em ordem de nível (vazia para aumentos/gatilhos/leis). */
    public static List<Mut> mutations(@Nullable Glyph g) {
        return ALL.getOrDefault(family(g), List.of());
    }

    /** Mutações já despertas no nível dado. */
    public static List<Mut> active(@Nullable Glyph g, int level) {
        List<Mut> out = new ArrayList<>();
        for (Mut m : mutations(g)) if (m.level() <= level) out.add(m);
        return out;
    }

    /** Próxima mutação acima do nível dado (null = nenhuma — glifo de apoio ou já passou de todas). */
    @Nullable
    public static Mut next(@Nullable Glyph g, int level) {
        for (Mut m : mutations(g)) if (m.level() > level) return m;
        return null;
    }

    // =====================================================================================
    //  Servidor: estado e eventos
    // =====================================================================================

    /** Intervalos por jogador (não salvos). */
    private static final class PState {
        long nextVisual, nextEclipse, nextQuake, nextSingular;
        long nextAftershock, nextVortex, nextArc, nextResidue, nextHeavy, nextRupture;
        long nextPulse, nextReveal, nextBless, nextAegis;
        long echoWindow = Long.MIN_VALUE / 2;
        int echoes;
        int residues;
    }

    private static final Map<UUID, PState> STATES = new HashMap<>();
    private static int globalResidues;
    /** Evita uma mutação disparar outra (o dano extra não passa pelos eventos de magia, mas por via das dúvidas). */
    private static boolean applying;

    /** Chamado por {@link GlyphMastery#init()}. */
    static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, SpellCastEvent.Post.class, MasteryMutations::onCast);
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, SpellDamageEvent.Pre.class, MasteryMutations::onPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, false, SpellDamageEvent.Post.class, MasteryMutations::onHit);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> STATES.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            STATES.clear();
            globalResidues = 0;
        });
    }

    private static PState state(ServerPlayer sp) {
        return STATES.computeIfAbsent(sp.getUUID(), k -> new PState());
    }

    /** O conjurador de verdade (jogador pela tecla; FakePlayer/espectador não). */
    @Nullable
    private static ServerPlayer player(@Nullable SpellContext ctx) {
        if (ctx == null || !ctx.isPlayerCast()) return null;
        ServerPlayer sp = ctx.casterPlayer();
        return sp == null || sp instanceof FakePlayer || sp.isSpectator() ? null : sp;
    }

    // ---------------------------------------------------------------- PvP: poder infinito pela metade

    /** Em jogadores, a parte do poder que veio de DEPOIS do X conta pela metade ({@link GlyphMastery#powerMultPvp}). */
    private static void onPre(SpellDamageEvent.Pre e) {
        if (!(e.target() instanceof Player)) return;
        SpellStats s = e.stats();
        if (s == null || s.masteryLevel <= GlyphMastery.MAX_LEVEL || s.masteryPower <= 1F) return;
        e.setAmount(e.getAmount() * GlyphMastery.powerMultPvp(s.masteryLevel) / s.masteryPower);
    }

    // ---------------------------------------------------------------- Na conjuração

    private static void onCast(SpellCastEvent.Post e) {
        SpellContext ctx = e.getContext();
        ServerPlayer sp = player(ctx);
        if (sp == null || e.getCaster() != sp) return;
        ParsedSpell spell = ctx.spell();
        ServerLevel level = ctx.level();
        long now = level.getGameTime();
        PState st = state(sp);
        float avg = GlyphMastery.averageLevel(spell);
        if (avg >= HALO) castVisuals(level, sp, ctx, avg, st, now);

        FormGlyph form = spell.form();
        int formLv = spell.formStats().masteryLevel;
        boolean channel = spell.formStats().channel;
        switch (family(form)) {
            case PROJECTILE -> {
                if (!channel && echoable(form)) projectileCast(sp, ctx, spell, formLv, st, now);
            }
            case AREA -> {
                if (!channel && formLv >= AREA_ECHO && echoable(form) && !form.isPersistent() && takeEcho(st, now)) {
                    echoCast(sp, ctx, 0.35F * effectPower(spell), ctx.eyePos(), ctx.look(), 30);
                }
            }
            case CLOSE -> closeCast(level, sp, ctx, formLv, st, now);
            default -> {}
        }
        int help = 0;
        for (ParsedSpell.EffectEntry en : spell.effects()) {
            if (family(en.effect()) == Family.HELP) help = Math.max(help, en.stats().masteryLevel);
        }
        if (help >= BLESSING) helpCast(level, sp, ctx, help, st, now);
    }

    /** Formas que podem ser repetidas por {@link SpellCaster#castFrom} sem duplicar algo preso ao conjurador. */
    private static boolean echoable(FormGlyph f) {
        return f.stageable() && !f.isUltimate() && !f.acceptsLaws();
    }

    /** Poder médio que a maestria dá aos efeitos do estágio 0 (os ecos são conjurados sem maestria). */
    private static float effectPower(ParsedSpell spell) {
        float p = 1F;
        for (ParsedSpell.EffectEntry en : spell.effects()) p = Math.max(p, en.stats().masteryPower);
        return p;
    }

    /** Cabe mais um eco/fragmento na janela deste jogador? */
    private static boolean takeEcho(PState st, long now) {
        if (now - st.echoWindow >= ECHO_WINDOW) {
            st.echoWindow = now;
            st.echoes = 0;
        }
        if (st.echoes >= ECHO_BUDGET) return false;
        st.echoes++;
        return true;
    }

    /** Fragmentos (projéteis laterais) e eco tardio de formas de projétil/feixe. */
    private static void projectileCast(ServerPlayer sp, SpellContext ctx, ParsedSpell spell, int formLv, PState st, long now) {
        Vec3 eye = ctx.eyePos();
        Vec3 look = ctx.look();
        float ep = effectPower(spell);
        int split = formLv >= SWARM ? 3 : formLv >= FRAGMENT_2 ? 2 : formLv >= FRAGMENT ? 1 : 0;
        for (int i = 1; i <= split; i++) {
            if (!takeEcho(st, now)) break;
            int side = (i % 2 == 1) ? 1 : -1;
            float deg = side * 8F * ((i + 1) / 2);
            echoCast(sp, ctx, 0.3F * ep, eye, look.yRot((float) Math.toRadians(deg)), 2);
        }
        int echoes = formLv >= ECHO_3 ? 2 : formLv >= ECHO ? 1 : 0;
        float power = (formLv >= ECHO_2 ? 0.5F : 0.35F) * ep;
        for (int i = 1; i <= echoes; i++) {
            if (!takeEcho(st, now)) break;
            echoCast(sp, ctx, power, eye, look, 30 * i);
        }
    }

    /**
     * Repete a receita (sem custo, sem maestria, sem eventos) de {@code origin} na direção {@code dir} depois de
     * {@code delay} ticks — o "fantasma" da conjuração. Cancela se o jogador morreu, trocou de mundo ou ligou o Modo Paz.
     */
    private static void echoCast(ServerPlayer sp, SpellContext ctx, float power, Vec3 origin, Vec3 dir, int delay) {
        ServerLevel level = ctx.level();
        SpellScheduler.schedule(delay, () -> {
            if (!sp.isAlive() || sp.isRemoved() || sp.level() != level || PeaceMode.isOn(sp)) return;
            if (delay > 5) ElementFx.flash(level, origin, ctx.color(), 0.9F);
            SpellCaster.castFrom(sp, ctx.element(), ctx.recipe(), power, origin, dir, false);
        });
    }

    /** Formas "de perto" (toque, si mesmo, domínio, ultimate): pulso que empurra e revelação. */
    private static void closeCast(ServerLevel level, ServerPlayer sp, SpellContext ctx, int formLv, PState st, long now) {
        int r = formLv >= ABYSS_PULSE ? 7 : formLv >= PULSE_2 ? 5 : formLv >= PULSE ? 3 : 0;
        if (r > 0 && now >= st.nextPulse) {
            st.nextPulse = now + 20;
            double knock = formLv >= ABYSS_PULSE ? 1.0 : 0.6;
            for (LivingEntity e : hostilesAround(ctx, sp, sp.position(), r, null)) {
                if (BossKit.isBossLike(e)) continue;
                e.knockback(knock, sp.getX() - e.getX(), sp.getZ() - e.getZ());
                e.hurtMarked = true;
                if (formLv >= PULSE_2) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, formLv >= ABYSS_PULSE ? 1 : 0), sp);
                }
            }
            ElementFx.shockwave(level, sp.position().add(0, 0.1, 0), ctx.color(), r, 12, null);
        }
        if (formLv >= REVEAL && now >= st.nextReveal) {
            st.nextReveal = now + 100;
            for (LivingEntity e : hostilesAround(ctx, sp, sp.position(), 12, null)) {
                e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0), sp);
            }
        }
    }

    /** Efeitos de ajuda: bênçãos em quem conjurou e aliados por perto (com intervalo longo). */
    private static void helpCast(ServerLevel level, ServerPlayer sp, SpellContext ctx, int help, PState st, long now) {
        if (now < st.nextBless) return;
        st.nextBless = now + 200;
        double r = help >= BLESSING_2 ? 8 : 6;
        boolean aegis = help >= AEGIS && now >= st.nextAegis;
        if (aegis) st.nextAegis = now + 400;
        for (Player p : level.getEntitiesOfClass(Player.class, new AABB(sp.position(), sp.position()).inflate(r),
                p -> p.isAlive() && !p.isSpectator() && (p == sp || ctx.isAlly(p)) && p.distanceToSqr(sp) <= r * r)) {
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, help >= BLESSING_2 ? 1 : 0), sp);
            if (aegis) p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0), sp);
            if (help >= GRACE) p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0), sp);
        }
        ElementFx.ring(level, sp.position().add(0, 0.2, 0), ctx.element(), r, 24);
    }

    /** Visual da magia inteira pela média: círculo desperto → cósmico → profano → eclipse → tremor → pilar. */
    private static void castVisuals(ServerLevel level, ServerPlayer sp, SpellContext ctx, float avg, PState st, long now) {
        if (now < st.nextVisual) return;
        st.nextVisual = now + 10;
        Vec3 feet = sp.position().add(0, 0.05, 0);
        int rgb = shifted(ctx.color(), avg);
        float r = Math.min(3.6F, 1.6F + (avg - HALO) * 0.03F);
        ElementFx.runeCircle(level, feet, rgb, r, 24, avg >= ELDRITCH ? 5 : avg >= COSMIC ? 3 : 1);
        if (avg >= COSMIC) ElementFx.ring(level, sp.position().add(0, 1.4, 0), ctx.element(), r * 0.8, 16);
        if (avg >= ELDRITCH) {
            ElementFx.shockwave(level, feet, 0x12001E, r * 1.6F, 16, null);
            ElementFx.runeCircle(level, feet.add(0, 0.02, 0), 0x3DFFA0, r * 1.45F, 30, 5);
        }
        if (avg >= ECLIPSE && now >= st.nextEclipse) {
            // "o céu escurece": tela tingida de quase-preto para quem está perto (pouco alfa, some sozinho)
            st.nextEclipse = now + 200;
            BossKit.flashNear(level, sp.position(), 32, 0x0A0014, 0.4F, 50);
        }
        if (avg >= QUAKE && now >= st.nextQuake) {
            st.nextQuake = now + 100;
            ElementFx.shake(level, sp.position(), 24, 0.8F, 18);
        }
        if (avg >= SINGULAR && now >= st.nextSingular) {
            st.nextSingular = now + 100;
            ElementFx.pillar(level, sp.position(), 0xFFFFFF, 0.9F, 40F, 30);
        }
    }

    /** A cor da magia vai "mutando": azul cósmico (20+), verde profano (35+), branco singular (100+). */
    private static int shifted(int rgb, float avg) {
        if (avg >= SINGULAR) return mix(rgb, 0xFFFFFF, 0.6F);
        if (avg >= ELDRITCH) return mix(rgb, 0x3DFFA0, 0.5F);
        if (avg >= COSMIC) return mix(rgb, 0x6F7BFF, 0.4F);
        return mix(rgb, 0xFFFFFF, 0.15F);
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    // ---------------------------------------------------------------- No acerto (dano de magia)

    private static void onHit(SpellDamageEvent.Post e) {
        if (applying || !e.landed()) return;
        SpellContext ctx = e.ctx();
        ServerPlayer sp = player(ctx);
        if (sp == null) return;
        float amount = e.getAmount();
        if (!(amount > 0F)) return;
        LivingEntity target = e.target();
        ServerLevel level = ctx.level();
        long now = level.getGameTime();
        PState st = state(sp);
        Vec3 at = target.position();

        // Forma de área: réplicas e vórtice
        int formLv = ctx.formStats().masteryLevel;
        if (family(ctx.spell().form()) == Family.AREA) {
            if (formLv >= AFTERSHOCK && now >= st.nextAftershock) {
                st.nextAftershock = now + 10;
                int n = formLv >= AFTERSHOCK_2 ? 2 : 1;
                float frac = formLv >= AFTERSHOCK_3 ? 0.3F : 0.2F;
                for (int i = 1; i <= n; i++) {
                    float dmg = amount * frac;
                    SpellScheduler.schedule(12 * i, () -> aftershock(ctx, sp, target, at, dmg));
                }
            }
            if (formLv >= VORTEX && now >= st.nextVortex) {
                st.nextVortex = now + 20;
                vortex(ctx, sp, at, formLv >= SINGULARITY ? 8 : 5, formLv >= SINGULARITY ? 0.6 : 0.35);
            }
        }

        // Efeito de dano: impacto pesado, arcos, resíduo, ruptura
        ParsedSpell.EffectEntry entry = entryOf(ctx, e.stats());
        Family ef = entry != null ? family(entry.effect()) : Family.HARM;
        int effLv = entry != null ? entry.stats().masteryLevel : (e.stats() != null ? e.stats().masteryLevel : 1);
        if (ef != Family.HARM || effLv < HEAVY) return;
        if (now >= st.nextHeavy) {
            st.nextHeavy = now + 10;
            ElementFx.shake(level, at, 10, Math.min(1.2F, 0.25F + effLv * 0.005F), 6);
        }
        if (effLv >= ARC && now >= st.nextArc) {
            st.nextArc = now + 6;
            int n = Math.min(MAX_ARCS, effLv >= ARC_3 ? 3 : effLv >= ARC_2 ? 2 : 1);
            float frac = effLv >= ARC_3 ? 0.3F : 0.25F;
            arcs(ctx, sp, target, n, amount * frac);
        }
        if (effLv >= RESIDUE && now >= st.nextResidue && st.residues < MAX_RESIDUES && globalResidues < MAX_RESIDUES_GLOBAL) {
            st.nextResidue = now + 20;
            residue(ctx, sp, st, at, effLv >= RESIDUE_2 ? 6 : 3, amount * (effLv >= RESIDUE_2 ? 0.10F : 0.08F));
        }
        if (effLv >= RUPTURE && e.killed() && now >= st.nextRupture) {
            st.nextRupture = now + 10;
            ElementFx.shockwave(level, at.add(0, 0.1, 0), shifted(ctx.color(), RUPTURE), 4F, 12, null);
            for (LivingEntity v : hostilesAround(ctx, sp, at, 4, target)) hurt(ctx, v, amount * 0.5F);
        }
    }

    /** O efeito (forma + stats) que causou o dano — pela identidade dos stats; null se o dano veio de outro lugar. */
    @Nullable
    private static ParsedSpell.EffectEntry entryOf(SpellContext ctx, @Nullable SpellStats stats) {
        if (stats == null) return null;
        for (ParsedSpell.EffectEntry en : ctx.spell().effects()) if (en.stats() == stats) return en;
        return null;
    }

    private static void aftershock(SpellContext ctx, ServerPlayer sp, LivingEntity first, Vec3 at, float dmg) {
        ServerLevel level = ctx.level();
        if (!sp.isAlive() || sp.level() != level) return;
        ElementFx.shockwave(level, at.add(0, 0.1, 0), ctx.color(), 3F, 10, null);
        List<LivingEntity> hit = hostilesAround(ctx, sp, at, 3, null);
        if (first.isAlive() && !hit.contains(first) && first.distanceToSqr(at) <= 9 && !ctx.isAlly(first)) hit.add(first);
        for (LivingEntity v : hit) hurt(ctx, v, dmg);
    }

    private static void vortex(SpellContext ctx, ServerPlayer sp, Vec3 at, double r, double strength) {
        for (LivingEntity v : hostilesAround(ctx, sp, at, r, null)) {
            if (BossKit.isBossLike(v)) continue;
            Vec3 d = at.subtract(v.position());
            if (d.lengthSqr() < 0.25) continue;
            v.push(d.x / d.length() * strength, 0.12, d.z / d.length() * strength);
            v.hurtMarked = true;
        }
        ElementFx.ring(ctx.level(), at.add(0, 0.3, 0), ctx.element(), r * 0.7, 18);
    }

    private static void arcs(SpellContext ctx, ServerPlayer sp, LivingEntity from, int n, float dmg) {
        List<LivingEntity> near = hostilesAround(ctx, sp, from.position(), ARC_RANGE, from);
        near.sort(Comparator.comparingDouble(v -> v.distanceToSqr(from)));
        Vec3 a = from.position().add(0, from.getBbHeight() * 0.5, 0);
        for (int i = 0; i < Math.min(n, near.size()); i++) {
            LivingEntity v = near.get(i);
            ElementFx.line(ctx.level(), a, v.position().add(0, v.getBbHeight() * 0.5, 0), ctx.element());
            hurt(ctx, v, dmg);
        }
    }

    private static void residue(SpellContext ctx, ServerPlayer sp, PState st, Vec3 at, int pulses, float dmg) {
        st.residues++;
        globalResidues++;
        ServerLevel level = ctx.level();
        for (int i = 1; i <= pulses; i++) {
            SpellScheduler.schedule(20 * i, () -> {
                if (!sp.isAlive() || sp.level() != level) return;
                ElementFx.ring(level, at.add(0, 0.15, 0), ctx.element(), 2.0, 10);
                for (LivingEntity v : hostilesAround(ctx, sp, at, 2, null)) hurt(ctx, v, dmg);
            });
        }
        SpellScheduler.schedule(20 * pulses + 1, () -> {
            st.residues = Math.max(0, st.residues - 1);
            globalResidues = Math.max(0, globalResidues - 1);
        });
    }

    /**
     * Inimigos numa esfera: monstros ou criaturas que miram o conjurador. Nunca jogadores, aliados, suportes de
     * armadura, aldeões ou animais à toa (cidades são sagradas). {@code except} fica de fora.
     */
    private static List<LivingEntity> hostilesAround(SpellContext ctx, ServerPlayer sp, Vec3 c, double r, @Nullable LivingEntity except) {
        return new ArrayList<>(ctx.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                v -> v != except && v != sp && v.isAlive() && !(v instanceof Player) && !(v instanceof ArmorStand)
                        && v.distanceToSqr(c) <= r * r && !ctx.isAlly(v)
                        && (v instanceof Enemy || (v instanceof Mob m && m.getTarget() == sp))));
    }

    /** Dano extra de mutação (fonte = a magia do jogador): metade em jogadores e chefes. */
    private static void hurt(SpellContext ctx, LivingEntity v, float amount) {
        if (!(amount > 0F) || !v.isAlive()) return;
        float a = amount * (v instanceof Player || BossKit.isBossLike(v) ? 0.5F : 1F);
        applying = true;
        try {
            v.hurt(ctx.damageSource(null), a);
        } finally {
            applying = false;
        }
    }

    private MasteryMutations() {}
}
