package com.renanpontes.isekaimahou.feature.transform_laws;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Motor das <b>transformações montadas</b> ({@link TransformForge}): uma SESSÃO por transformação em curso (a forma
 * conjurada, a forma racial com receita, uma liberação de classe com receita). Enquanto existe:
 * golpes aplicam os efeitos ofensivos, a aura pulsa os benéficos (e, com alcance, chega a aliados/inimigos) e o visual
 * do elemento da receita fica em volta do jogador ({@link ElementFx#aura}: desenhado no cliente pelo preset gráfico).
 * <p>
 * Nada aqui mexe em blocos nem cria criaturas: só efeitos de magia em entidades, pelas mesmas regras de alvo das magias
 * (aliados, grupo, PvP).
 * <p>
 * Onda 5: sessões presas a um efeito (a forma conjurada, a forma racial, Kaioken, formas de Druida...) SOBREVIVEM ao
 * relog — ao sair, a receita fica guardada ({@link ForgeData.Resume}); ao entrar, se o efeito da transformação ainda
 * está no jogador, a sessão volta sem cobrar a mana de novo. Liberações que vivem só na memória da classe (Shikai,
 * Bankai, Resurrección...) acabam ao sair, como a própria liberação. Morte encerra tudo.
 */
final class ForgeSessions {
    /** Espera pelo efeito-sonda aparecer (mesma folga das leis). */
    private static final int GRACE = 160;
    /** Teto de segurança de uma sessão (20 min). */
    private static final long MAX_TICKS = 20L * 60 * 20;
    /** De quanto em quanto tempo o visual da aura é renovado (ticks). */
    private static final int FX_EVERY = 40;

    /** Receita já validada e paga, pronta para virar sessão. */
    record Plan(SpellRecipe recipe, ParsedSpell spell, boolean resumed) {}

    static final class Session {
        final ResourceLocation source;
        final SpellRecipe recipe;
        final ParsedSpell spell;
        final Element element;
        final int rgb;
        final int tier;
        final float power;
        final float radius;
        final List<ParsedSpell.EffectEntry> strike;
        final List<ParsedSpell.EffectEntry> aura;
        final long start;
        final long endAt;
        @Nullable final Holder<MobEffect> probe;
        /** O efeito-sonda é o nosso (forma conjurada): tiramos ao encerrar por fora. */
        final boolean ownsProbe;
        SpellContext ctx;
        boolean seen;
        long nextPulse;
        long nextStrike;
        long nextFx;
        /** Onda 5: multiplicador de duração ainda por aplicar no efeito-sonda (1 = nada a fazer). */
        float stretch = 1F;
        /** Onda 5: a receita ENCURTOU a transformação base — hora de desfazer o efeito-sonda (0 = não). */
        long cutAt;
        /** Onda 5: sessão recolocada no login (a mana, o tempo e a recarga já foram acertados antes de sair). */
        boolean resumed;
        /** v1.1: a cutscene de começo ainda não foi tocada (forma conjurada). */
        boolean cutscene;

        Session(ResourceLocation source, SpellContext ctx, long start, long endAt, @Nullable Holder<MobEffect> probe, boolean ownsProbe) {
            this.source = source;
            this.ctx = ctx;
            this.recipe = ctx.recipe();
            this.spell = ctx.spell();
            this.element = ctx.element() == null ? Element.ARCANE : ctx.element();
            this.rgb = ctx.color() & 0xFFFFFF;
            this.tier = Mth.clamp(1 + spell.baseCost() / 70, 1, 4);
            this.power = ctx.powerMult();
            this.radius = TransformForge.auraRadius(spell.formStats());
            this.strike = TransformForge.effects(spell, TransformForge.Role.STRIKE);
            this.aura = TransformForge.effects(spell, TransformForge.Role.AURA);
            this.start = start;
            this.endAt = endAt;
            this.probe = probe;
            this.ownsProbe = ownsProbe;
            this.nextPulse = start;
            this.nextFx = start;
        }
    }

    private static final Map<UUID, List<Session>> ACTIVE = new ConcurrentHashMap<>();
    /** Último motivo de a receita de um espaço não ter entrado (para a API devolver). */
    private static final Map<UUID, Map<ResourceLocation, Component>> ERRORS = new ConcurrentHashMap<>();
    /** Onda 5: fontes cuja sessão voltou do relog — o próximo {@link #plan} delas não cobra mana de novo. */
    private static final Map<UUID, Set<ResourceLocation>> RESUMED = new ConcurrentHashMap<>();

    static void init() {
        TransformForge.registerSlot(TransformForge.RACE_SLOT, () -> slotTitle(TransformForge.RACE_SLOT), d -> true);
        TransformForge.registerSlot(TransformForge.HOLLOW_SLOT, () -> slotTitle(TransformForge.HOLLOW_SLOT),
                d -> d.path() == MagicPath.HOLLOW);
        // Liberações de Shinigami/Quincy: aparecem depois da primeira liberação que passar pelo sistema
        TransformForge.registerSlot(TransformForge.SHIKAI_SLOT, () -> slotTitle(TransformForge.SHIKAI_SLOT),
                d -> d.path() == MagicPath.SHINIGAMI && d.hasFlag(TransformForge.SEEN_FLAG + TransformForge.SHIKAI_SLOT));
        TransformForge.registerSlot(TransformForge.BANKAI_SLOT, () -> slotTitle(TransformForge.BANKAI_SLOT),
                d -> d.path() == MagicPath.SHINIGAMI && d.hasFlag(TransformForge.SEEN_FLAG + TransformForge.BANKAI_SLOT));
        TransformForge.registerSlot(TransformForge.VOLLSTANDIG_SLOT, () -> slotTitle(TransformForge.VOLLSTANDIG_SLOT),
                d -> d.path() == MagicPath.QUINCY && d.hasFlag(TransformForge.SEEN_FLAG + TransformForge.VOLLSTANDIG_SLOT));

        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, LivingDamageEvent.Post.class, ForgeSessions::onDamage);
        // v1.1: a cutscene da transformação sai DEPOIS da conjuração (por cima da cena genérica de ultimate da Suprema)
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, (SpellCastEvent.Post e) -> {
            if (e.getCaster() instanceof ServerPlayer sp && TransformForge.isForge(e.getContext().spell())) playCutscene(sp);
        });
        NeoForge.EVENT_BUS.addListener((MagicProgressEvent.GlyphLearned e) -> {
            if (TransformForge.FORM_ID.equals(e.glyph)) Hints.show(e.getPlayer(), "tforge_learned");
        });
    }

    static Component slotTitle(String slot) {
        return Component.translatable("screen.isekai_mahou.tforge.slot." + slot);
    }

    static void clearAll() {
        ACTIVE.clear();
        ERRORS.clear();
        RESUMED.clear();
    }

    /** Há uma sessão da fonte que COMEÇOU agora (não uma que voltou do relog)? */
    static boolean fresh(LivingEntity e, ResourceLocation source) {
        List<Session> list = ACTIVE.get(e.getUUID());
        if (list == null) return false;
        for (Session s : list) if (s.source.equals(source)) return !s.resumed;
        return false;
    }

    /** A magia da receita ativa na fonte (null = nenhuma). */
    @Nullable
    static ParsedSpell activeSpell(LivingEntity e, ResourceLocation source) {
        List<Session> list = ACTIVE.get(e.getUUID());
        if (list == null) return null;
        for (Session s : list) if (s.source.equals(source)) return s.spell;
        return null;
    }

    // =====================================================================================
    //  Consultas
    // =====================================================================================
    static boolean active(LivingEntity e, @Nullable ResourceLocation source) {
        List<Session> list = ACTIVE.get(e.getUUID());
        if (list == null) return false;
        for (Session s : list) if (source == null || s.source.equals(source)) return true;
        return false;
    }

    static List<Session> sessions(LivingEntity e) {
        List<Session> list = ACTIVE.get(e.getUUID());
        return list == null ? List.of() : List.copyOf(list);
    }

    @Nullable
    static Component lastError(ServerPlayer p, ResourceLocation source) {
        Map<ResourceLocation, Component> m = ERRORS.get(p.getUUID());
        return m == null ? null : m.get(source);
    }

    private static void error(ServerPlayer p, ResourceLocation source, Component msg) {
        ERRORS.computeIfAbsent(p.getUUID(), u -> new ConcurrentHashMap<>()).put(source, msg);
        p.displayClientMessage(msg.copy().withColor(0xFF7070), true);
    }

    // =====================================================================================
    //  Escolher a receita de um espaço (aba Votos)
    // =====================================================================================
    static void chooseRecipe(ServerPlayer sp, String slotId, SpellRecipe recipe) {
        TransformForge.Slot slot = TransformForge.slot(slotId);
        MagicData d = MagicApi.get(sp);
        if (slot == null || !slot.available().test(d)) return;
        if (active(sp, IsekaiMahou.id(slotId))) {
            fail(sp, Component.translatable("message.isekai_mahou.tforge.busy"));
            return;
        }
        if (recipe == null || recipe.isEmpty()) {
            if (ForgeData.recipe(sp, slotId).isEmpty()) return;
            ForgeData.set(sp, slotId, SpellRecipe.EMPTY);
            TlawFx.sound(sp, SoundEvents.CHAIN_BREAK, 0.7F, 1.0F);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.tforge.recipe_off", slot.title().get()), true);
            return;
        }
        // Onda 5: a forma é o corpo da transformação — só o que vem depois dela precisa ser conhecido
        SpellParser.Result res = TransformForge.validate(recipe, sp.getAbilities().instabuild ? null : d);
        if (!res.ok()) {
            fail(sp, res.error());
            return;
        }
        if (!TransformForge.isForge(res.spell())) {
            fail(sp, Component.translatable("message.isekai_mahou.tforge.not_forge"));
            return;
        }
        ForgeData.set(sp, slotId, recipe);
        TlawFx.sound(sp, SoundEvents.CHAIN_PLACE, 0.7F, 1.2F);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.tforge.recipe_on", recipeName(recipe), slot.title().get())
                .withColor(res.spell().primary().color), true);
    }

    static Component recipeName(SpellRecipe r) {
        return r.name().isEmpty() ? Component.translatable("screen.isekai_mahou.tforge.unnamed") : Component.literal(r.name());
    }

    private static void fail(ServerPlayer sp, Component msg) {
        sp.displayClientMessage(msg.copy().withColor(0xFF7070), true);
        TlawFx.sound(sp, SoundEvents.VILLAGER_NO, 0.6F, 1.0F);
    }

    // =====================================================================================
    //  Começar
    // =====================================================================================

    /**
     * Chamado por {@link TlawSessions#begin}: a fonte é um espaço de receita com receita escolhida? Valida de novo
     * (glifos, Círculo, limites) e cobra a mana. null = sem receita (ou não entrou: o motivo fica em {@link #lastError}).
     */
    @Nullable
    static Plan plan(ServerPlayer p, ResourceLocation source) {
        if (!IsekaiMahou.MODID.equals(source.getNamespace())) return null;
        TransformForge.Slot slot = TransformForge.slot(source.getPath());
        if (slot == null) return null;
        Map<ResourceLocation, Component> errs = ERRORS.get(p.getUUID());
        if (errs != null) errs.remove(source);
        MagicData d = MagicApi.get(p);
        String seen = TransformForge.SEEN_FLAG + slot.id();
        if (!d.hasFlag(seen)) d.setFlag(seen, true);
        SpellRecipe recipe = ForgeData.recipe(p, slot.id());
        Set<ResourceLocation> back = RESUMED.get(p.getUUID());
        boolean free = back != null && back.remove(source);
        if (recipe.isEmpty()) {
            // Primeira vez: conta que TODA transformação pode ser moldada na aba Votos (onda 5: sem exigir a forma)
            Hints.show(p, "tforge_shape");
            return null;
        }
        SpellParser.Result res = TransformForge.validate(recipe, p.getAbilities().instabuild ? null : d);
        if (!res.ok() || !TransformForge.isForge(res.spell())) {
            error(p, source, Component.translatable("message.isekai_mahou.tforge.invalid", slot.title().get(),
                    res.ok() ? Component.translatable("message.isekai_mahou.tforge.not_forge") : res.error()));
            return null;
        }
        // Voltou do relog com a transformação ainda ativa: a mana desta receita já foi paga antes de sair
        if (free && active(p, source)) return new Plan(recipe, res.spell(), true);
        int cost = TransformForge.slotMana(res.spell());
        if (!p.getAbilities().instabuild && !d.consumeMana(cost)) {
            error(p, source, Component.translatable("message.isekai_mahou.tforge.no_mana", cost));
            return null;
        }
        return new Plan(recipe, res.spell(), false);
    }

    /**
     * A receita de um espaço entra por cima da transformação que está começando.
     *
     * @param durationMult multiplicador total do tempo da sessão (leis × receita)
     * @param stretchProbe a sessão de LEIS não existe (receita sem leis): é esta sessão que estica/encurta o
     *                     efeito-sonda da transformação base pelo multiplicador da receita
     */
    static void startPlan(ServerPlayer sp, ResourceLocation source, Plan plan, int ticks, @Nullable Holder<MobEffect> probe,
                          float durationMult, boolean stretchProbe) {
        // Voltou do relog: a sessão já foi recolocada no login — nada de corpo, estrondo ou tempo esticado de novo
        if (plan.resumed()) return;
        ParsedSpell spell = plan.spell();
        Element el = spell.primary() == null ? Element.ARCANE : spell.primary();
        SpellContext ctx = new SpellContext(sp.serverLevel(), sp, el, plan.recipe(), spell);
        ctx.markPlayerCast();
        long now = sp.serverLevel().getGameTime();
        long endAt = probe == null ? now + Math.max(20, Math.round(Math.max(20, ticks) * durationMult)) : Long.MAX_VALUE;
        Session s = start(sp, source, ctx, endAt, probe, false, false);
        if (stretchProbe && probe != null) {
            s.stretch = TransformForge.durationMult(spell.formStats());
            if (s.seen) applyStretch(sp, s, now);
        }
    }

    /** Estica (mesmo efeito, mais tempo) ou marca a hora de desfazer o efeito-sonda da transformação base. */
    private static void applyStretch(ServerPlayer sp, Session s, long now) {
        float mult = s.stretch;
        s.stretch = 1F;
        if (mult == 1F || s.probe == null) return;
        MobEffectInstance inst = sp.getEffect(s.probe);
        if (inst == null || inst.isInfiniteDuration()) return;
        int scaled = Math.max(20, Math.round(inst.getDuration() * mult));
        if (mult > 1F) {
            sp.addEffect(new MobEffectInstance(s.probe, scaled, inst.getAmplifier(), inst.isAmbient(), inst.isVisible(), inst.showIcon()));
        } else {
            s.cutAt = now + scaled;
        }
    }

    /** A forma Transformação foi conjurada (já paga pelo SpellCaster). */
    static void beginCast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null || !caster.isAlive()) return;
        ParsedSpell spell = ctx.spell();
        if (!(caster instanceof ServerPlayer sp) || sp instanceof FakePlayer) {
            // Mobs/lendas: sem sessão — o corpo e os efeitos benéficos entram uma vez
            for (ParsedSpell.EffectEntry en : spell.effects()) {
                if (TransformForge.role(en) != TransformForge.Role.STRIKE) ctx.applyEffect(en, caster);
            }
            ElementFx.aura(ctx.level(), caster, ctx.element(), ctx.color() & 0xFFFFFF, 1.0F, 2, 100);
            return;
        }
        // v1.1: um corpo (O Todo-Poderoso: 5 min) dita o tempo da transformação inteira
        int ticks = TransformForge.castDuration(spell);
        Holder<MobEffect> probe = TlawContent.FORGED;
        // Reconjurar troca a transformação: a anterior acaba de verdade (as leis dela cobram o preço de fim — não dá
        // para fugir do custo reconjurando) e o tempo novo vale mesmo que seja menor que o que restava
        if (active(sp, TransformForge.FORM_ID)) TlawSessions.INSTANCE.end(sp, TransformForge.FORM_ID);
        sp.removeEffect(probe);
        sp.addEffect(new MobEffectInstance(probe, ticks, 0, false, false, true));
        Session s = start(sp, TransformForge.FORM_ID, ctx, Long.MAX_VALUE, probe, true, true);
        // a cutscene sai no SpellCastEvent.Post (mesmo tick); com Atraso a forma chega depois — aí sai no tick seguinte
        s.cutscene = true;
        SpellScheduler.schedule(1, () -> playCutscene(sp));
        Hints.show(sp, "tforge_cast");
    }

    /** Toca a cutscene pendente da forma conjurada (uma vez por ativação). */
    private static void playCutscene(ServerPlayer sp) {
        List<Session> list = ACTIVE.get(sp.getUUID());
        if (list == null || !sp.isAlive()) return;
        for (Session s : list) {
            if (!s.cutscene || !s.source.equals(TransformForge.FORM_ID)) continue;
            s.cutscene = false;
            ForgeBodies.cutscene(sp, s.spell, s.recipe);
        }
    }

    /** v1.1: a transformação conjurada acabou — os corpos dela se desfazem (O Todo-Poderoso, Bankai...). */
    private static void bodiesEnd(ServerPlayer sp, Session s) {
        if (!s.source.equals(TransformForge.FORM_ID)) return;
        for (ParsedSpell.EffectEntry en : TransformForge.bodies(s.spell)) {
            try {
                ((TransformBody) en.effect()).onTransformEnd(sp);
            } catch (RuntimeException ex) {
                IsekaiMahou.LOGGER.warn("Corpo {} falhou ao encerrar: {}", en.effect().id(), ex.toString());
            }
        }
    }

    private static Session start(ServerPlayer sp, ResourceLocation source, SpellContext ctx, long endAt,
                              @Nullable Holder<MobEffect> probe, boolean ownsProbe, boolean grand) {
        List<Session> list = ACTIVE.computeIfAbsent(sp.getUUID(), u -> new ArrayList<>());
        list.removeIf(s -> s.source.equals(source));
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        Session s = new Session(source, ctx, now, endAt, probe, ownsProbe);
        s.seen = probe == null || sp.hasEffect(probe);
        list.add(s);
        // Corpo: a transformação fixa da receita (Kaioken, Forma de Lobo...) entra agora, uma vez
        for (ParsedSpell.EffectEntry en : TransformForge.effects(s.spell, TransformForge.Role.BODY)) ctx.applyEffect(en, sp);

        Vec3 pos = sp.position();
        Vec3 center = pos.add(0, sp.getBbHeight() * 0.5, 0);
        ElementFx.burst(level, center, s.element, grand ? 28 : 14, grand ? 0.8 : 0.6);
        if (grand) {
            ElementFx.runeCircle(level, pos.add(0, 0.05, 0), s.rgb, 2.4F, 40, 3);
            ElementFx.shockwave(level, pos.add(0, 0.1, 0), s.rgb, 4.5F, 14, null);
            // Tremor pelo sistema dos chefes: o cliente já multiplica por MahouConfig.shakeScale()
            ElementFx.shake(level, pos, 14, 0.7F, 12);
            TlawFx.sound(sp, SoundEvents.BEACON_ACTIVATE, 0.8F, 1.3F);
        }
        return s;
    }

    // =====================================================================================
    //  Terminar
    // =====================================================================================

    /** Encerra a sessão da fonte (fim da transformação base, por qualquer motivo). */
    static void stop(ServerPlayer sp, ResourceLocation source) {
        List<Session> list = ACTIVE.get(sp.getUUID());
        if (list == null) return;
        for (Session s : List.copyOf(list)) if (s.source.equals(source)) finish(sp, list, s, true);
    }

    /**
     * Onda 5 — saída do jogo: guarda as sessões presas a um efeito (que continua no jogador ao voltar) para
     * {@link #login} retomar sem cobrar de novo. O efeito-sonda NÃO é tirado.
     */
    static void logout(ServerPlayer sp) {
        List<Session> list = ACTIVE.remove(sp.getUUID());
        ERRORS.remove(sp.getUUID());
        RESUMED.remove(sp.getUUID());
        List<ForgeData.Resume> keep = new ArrayList<>();
        if (list != null && sp.isAlive()) {
            for (Session s : list) {
                if (s.probe == null || !s.seen || !sp.hasEffect(s.probe)) continue;
                ResourceLocation probeId = s.probe.unwrapKey().map(ResourceKey::location).orElse(null);
                if (probeId != null) keep.add(new ForgeData.Resume(s.source.toString(), s.recipe, probeId.toString(), s.ownsProbe));
            }
        }
        ForgeData.saveResume(sp, keep);
    }

    /** Encerra tudo (comando). */
    static void stopAll(ServerPlayer sp, boolean fx) {
        List<Session> list = ACTIVE.remove(sp.getUUID());
        ERRORS.remove(sp.getUUID());
        RESUMED.remove(sp.getUUID());
        if (list == null) return;
        for (Session s : list) {
            if (s.ownsProbe && s.probe != null) sp.removeEffect(s.probe);
            if (fx && sp.isAlive()) endFx(sp, s);
            bodiesEnd(sp, s);
        }
    }

    /**
     * Entrada no mundo (onda 5): as sessões guardadas na saída voltam se o efeito da transformação ainda está no
     * jogador — sem cobrar mana de novo, sem o estrondo do começo. O que não puder voltar é esquecido; uma marca de
     * forma conjurada que ficou órfã é tirada.
     */
    static void login(ServerPlayer sp) {
        boolean forged = false;
        MagicData d = MagicApi.get(sp);
        for (ForgeData.Resume r : ForgeData.takeResume(sp)) {
            ResourceLocation source = ResourceLocation.tryParse(r.source());
            ResourceLocation probeId = ResourceLocation.tryParse(r.probe());
            if (source == null || probeId == null || r.recipe().isEmpty()) continue;
            Holder<MobEffect> probe = BuiltInRegistries.MOB_EFFECT.getHolder(ResourceKey.create(Registries.MOB_EFFECT, probeId)).orElse(null);
            if (probe == null || !sp.hasEffect(probe)) continue;
            boolean cast = source.equals(TransformForge.FORM_ID);
            SpellParser.Result res = cast ? SpellParser.parse(r.recipe(), Element.ARCANE, d, d) : TransformForge.validate(r.recipe(), d);
            if (!res.ok() || !TransformForge.isForge(res.spell())) continue;
            ParsedSpell spell = res.spell();
            // v1.1: corpos (O Todo-Poderoso, Bankai...) vivem só na memória da classe — a transformação deles não volta
            if (cast && !TransformForge.bodies(spell).isEmpty()) continue;
            SpellContext ctx = new SpellContext(sp.serverLevel(), sp, spell.primary() == null ? Element.ARCANE : spell.primary(),
                    r.recipe(), spell);
            ctx.markPlayerCast();
            Session s = resume(sp, source, ctx, probe, r.ownsProbe());
            if (cast) forged = true;
            // A transformação base (ex.: forma racial) vai chamar begin de novo ao ver a forma: essa chamada não cobra
            // (só a forma racial faz isso sozinha; as outras só chamam begin numa conjuração nova, que paga normal)
            else if (s != null && source.equals(TlawSessions.RACE_SOURCE)) {
                RESUMED.computeIfAbsent(sp.getUUID(), u -> ConcurrentHashMap.newKeySet()).add(source);
            }
        }
        if (!forged) sp.removeEffect(TlawContent.FORGED);
        ForgeData.sync(sp);
    }

    /** Recoloca uma sessão que voltou do relog (sem o corpo, sem FX de começo). */
    @Nullable
    private static Session resume(ServerPlayer sp, ResourceLocation source, SpellContext ctx, Holder<MobEffect> probe, boolean ownsProbe) {
        List<Session> list = ACTIVE.computeIfAbsent(sp.getUUID(), u -> new ArrayList<>());
        list.removeIf(x -> x.source.equals(source));
        Session s = new Session(source, ctx, sp.serverLevel().getGameTime(), Long.MAX_VALUE, probe, ownsProbe);
        s.seen = true;
        s.resumed = true;
        list.add(s);
        return s;
    }

    private static void finish(ServerPlayer sp, List<Session> list, Session s, boolean removeProbe) {
        list.remove(s);
        if (list.isEmpty()) ACTIVE.remove(sp.getUUID());
        if (removeProbe && s.ownsProbe && s.probe != null) sp.removeEffect(s.probe);
        if (s.seen && sp.isAlive()) endFx(sp, s);
        bodiesEnd(sp, s);
    }

    private static void endFx(ServerPlayer sp, Session s) {
        ElementFx.burst(sp.serverLevel(), sp.position().add(0, sp.getBbHeight() * 0.5, 0), s.element, 10, 0.5);
        TlawFx.sound(sp, SoundEvents.BEACON_DEACTIVATE, 0.5F, 1.4F);
    }

    // =====================================================================================
    //  Tick (a cada 5 ticks do jogador)
    // =====================================================================================
    static void tick(ServerPlayer sp) {
        List<Session> list = ACTIVE.get(sp.getUUID());
        if (list == null) return;
        if (!sp.isAlive()) {
            // Morte: tudo acaba sem enfeite (a marca some com os outros efeitos)
            ACTIVE.remove(sp.getUUID());
            return;
        }
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        for (Session s : List.copyOf(list)) {
            // Renasceu (outra entidade com o mesmo UUID): a sessão antiga não vale mais
            if (s.ctx.caster() != sp) {
                finish(sp, list, s, false);
                continue;
            }
            if (s.probe != null) {
                boolean present = sp.hasEffect(s.probe);
                if (!s.seen) {
                    if (present) {
                        s.seen = true;
                        applyStretch(sp, s, now);
                    } else if (now - s.start > GRACE) finish(sp, list, s, false);
                    if (!s.seen) continue;
                } else if (!present) {
                    finish(sp, list, s, false);
                    continue;
                } else if (s.cutAt > 0 && now >= s.cutAt) {
                    // A receita encurtou a transformação: a forma base é desfeita na hora marcada
                    sp.removeEffect(s.probe);
                    finish(sp, list, s, false);
                    continue;
                }
            } else if (now >= s.endAt) {
                finish(sp, list, s, false);
                continue;
            }
            if (now - s.start > MAX_TICKS) {
                finish(sp, list, s, true);
                continue;
            }
            // Mudou de dimensão: o contexto da magia é refeito no mundo novo
            if (s.ctx.level() != level) {
                SpellContext ctx = new SpellContext(level, sp, s.element, s.recipe, s.spell);
                ctx.markPlayerCast();
                s.ctx = ctx;
            }
            if (now >= s.nextFx) {
                s.nextFx = now + FX_EVERY;
                ElementFx.aura(level, sp, s.element, s.rgb, 1.0F, s.tier, FX_EVERY + 6);
            }
            if (now >= s.nextPulse) {
                s.nextPulse = now + TransformForge.PULSE_TICKS;
                pulse(sp, level, s);
            }
        }
    }

    /** Pulso da aura: benéficos em você (e aliados no alcance); com alcance, os ofensivos nos inimigos dentro dela. */
    private static void pulse(ServerPlayer sp, ServerLevel level, Session s) {
        if (!s.aura.isEmpty()) {
            SpellContext good = s.ctx.copy(s.power * TransformForge.AURA_POWER);
            for (ParsedSpell.EffectEntry en : s.aura) good.applyEffect(en, sp);
            if (s.radius > 0F) {
                int n = 0;
                for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(s.radius),
                        e -> e != sp && e.isAlive() && !e.isSpectator() && e.distanceToSqr(sp) <= s.radius * s.radius && s.ctx.isAlly(e))) {
                    if (++n > 12) break;
                    for (ParsedSpell.EffectEntry en : s.aura) good.applyEffect(en, ally);
                }
            }
        }
        if (s.radius > 0F) {
            // Cidades são sagradas: a aura ofensiva não pulsa em zona segura e só pega monstros, quem está te atacando e
            // jogadores inimigos — nunca moradores, animais ou bichos de alguém
            if (!s.strike.isEmpty() && !Places.isSafe(level, sp.blockPosition())) {
                SpellContext bad = s.ctx.copy(s.power * TransformForge.FOE_AURA_POWER);
                int n = 0;
                for (LivingEntity foe : s.ctx.enemiesAround(sp.position(), s.radius)) {
                    if (!hostile(sp, foe)) continue;
                    if (++n > 16) break;
                    for (ParsedSpell.EffectEntry en : s.strike) {
                        if (bad.isValidTarget(en.effect(), en.stats(), foe)) bad.applyEffect(en, foe);
                    }
                }
            }
            ElementFx.ring(level, sp.position().add(0, 0.15, 0), s.element, s.radius, Math.min(40, 10 + (int) (s.radius * 3)));
        }
    }

    private static boolean hostile(ServerPlayer sp, LivingEntity e) {
        if (e instanceof OwnableEntity o && o.getOwnerUUID() != null) return false;
        if (e instanceof Enemy || e instanceof Player) return true;
        return e instanceof Mob m && m.getTarget() == sp;
    }

    // =====================================================================================
    //  Golpes
    // =====================================================================================
    private static void onDamage(LivingDamageEvent.Post e) {
        if (ACTIVE.isEmpty() || e.getNewDamage() <= 0F) return;
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof ServerPlayer sp) || sp == e.getEntity()) return;
        List<Session> list = ACTIVE.get(sp.getUUID());
        if (list == null) return;
        // Só golpes de verdade (corpo a corpo, flechas, projéteis de arma): dano de magia não realimenta os efeitos
        if (src.is(CoreRegistries.SPELL_DAMAGE)) return;
        Entity direct = src.getDirectEntity();
        if (direct != sp && !(direct instanceof Projectile)) return;
        LivingEntity victim = e.getEntity();
        long now = sp.serverLevel().getGameTime();
        for (Session s : list) {
            if (!s.seen || s.strike.isEmpty() || now < s.nextStrike) continue;
            s.nextStrike = now + TransformForge.STRIKE_GAP;
            // No tick seguinte: nada de dano aninhado dentro do evento de dano do próprio alvo
            SpellScheduler.schedule(1, () -> strike(sp, s, victim));
        }
    }

    private static void strike(ServerPlayer sp, Session s, LivingEntity victim) {
        List<Session> list = ACTIVE.get(sp.getUUID());
        if (list == null || !list.contains(s) || !sp.isAlive() || !victim.isAlive()) return;
        if (victim.level() != s.ctx.level() || sp.level() != s.ctx.level()) return;
        SpellContext hit = s.ctx.copy(s.power * TransformForge.STRIKE_POWER);
        boolean any = false;
        for (ParsedSpell.EffectEntry en : s.strike) {
            if (!hit.isValidTarget(en.effect(), en.stats(), victim)) continue;
            // O golpe acabou de acertar: sem isso o dano do efeito cairia na janela de invulnerabilidade
            victim.invulnerableTime = 0;
            hit.applyEffect(en, victim);
            any = true;
        }
        if (any) {
            ElementFx.impact(s.ctx.level(), victim.position().add(0, victim.getBbHeight() * 0.55, 0), s.element, s.rgb, 1, null);
        }
    }

    private ForgeSessions() {}
}
