package com.renanpontes.isekaimahou.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.api.Statuses.Kind;
import com.renanpontes.isekaimahou.core.event.ElementReactionEvent;
import com.renanpontes.isekaimahou.core.status.ReactionFx;
import com.renanpontes.isekaimahou.core.status.StatusEngine;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Reações elementais + resistências/fraquezas dos mobs + condições de status aplicadas pelo elemento (v0.8, §2.1–2.2).
 * Chamado por {@link SpellContext#damage} para todo dano de magia (depois do dano base, antes do evento de dano).
 *
 * <p>Ordem em {@link #onSpellHit}: multiplicador do tipo de mob → Exposto → bônus passivos das condições (Em Chamas
 * +8%/acúmulo no Fogo, Eletrizado +15%/acúmulo no Raio) → no máx. UMA reação de condição → reações de sequência
 * (Ruptura, Areia nos Olhos) → condição automática do elemento. Cada reação: partículas, som, texto flutuante,
 * {@link ElementReactionEvent} e contadores {@code reaction.<id>} / {@code reaction.any} para jogadores.
 *
 * <p>Módulos registram resistências/fraquezas das suas criaturas com {@link #setMultiplier}.
 *
 * <p>Release 1.0 (perf) — reações mais LEVES, com a mesma força: o dano e as condições não mudaram. O que mudou: alvo sem
 * nenhuma condição pula toda a checagem de reações; a explosão de cada reação é UM pacote montado no cliente pelo Preset
 * Gráfico (as rajadas de partículas do vanilla saíram do servidor); os arcos do Eletrocutar são um item do lote cada; e
 * há um orçamento por tick ({@link #FX_FULL_PER_TICK} anúncios completos, até {@link #FX_MAX_PER_TICK} explosões
 * enxutas) para uma magia de área em 20 alvos não virar 20 textos, 40 sons e 20 explosões no mesmo instante.
 */
public final class ElementReactions {
    private static final String TAG = "isekai_mahou_last";
    /** Janela das reações de sequência (Luz→Sombra, Terra→Vento): 3 s. */
    private static final long SEQUENCE_TICKS = 60;
    /** Reações por tick que ganham tudo (explosão completa + som + texto flutuante). */
    public static final int FX_FULL_PER_TICK = 4;
    /** Reações por tick que ainda ganham explosão (as além das completas: versão enxuta, sem som nem texto). */
    public static final int FX_MAX_PER_TICK = 12;
    private static long fxTick = Long.MIN_VALUE;
    private static int fxThisTick;
    /** Tipo de entidade -> (elemento base -> multiplicador de dano). <1 resiste, >1 fraco, 0 imune. */
    private static final Map<ResourceLocation, Map<Element, Float>> MULTIPLIERS = new HashMap<>();

    /** As reações. {@code id} vira o contador {@code reaction.<id>} e a chave {@code reaction.isekai_mahou.<id>}. */
    public enum Reaction {
        /** Molhado + Raio: dano ×2, atordoa 2 s, consome Molhado, arco para Molhados a ≤4 blocos. */
        ELECTROCUTE("electrocute", 0x7FDBFF),
        /** Molhado + Gelo: Congelado direto, consome Molhado. */
        FREEZE("freeze", 0xA8E8FF),
        /** Em Chamas + Água: apaga, nuvem de vapor cega em volta. */
        VAPOR("vapor", 0xD8E6EE),
        /** Em Chamas + Vento: espalha Em Chamas a ≤3 blocos. */
        WILDFIRE("wildfire", 0xFF8A2B),
        /** Em Chamas + Gelo, ou Gelado/Congelado + Fogo: dano ×1,6, remove as duas. */
        MELT("melt", 0xFFB060),
        /** Em Chamas + Raio: mini-explosão de 2,5 blocos (sem quebrar blocos), remove Em Chamas. */
        OVERLOAD("overload", 0xFF5FA0),
        /** Gelado + Raio: Exposto II por 5 s. */
        SUPERCONDUCT("superconduct", 0xB4A8FF),
        /** Congelado/Petrificado + Terra ou golpe físico corpo a corpo: dano ×1,75, quebra o estado. */
        SHATTER("shatter", 0xD8F6FF),
        /** Sangrando + Vento: +3 Sangrando. */
        LACERATE("lacerate", 0xE0303A),
        /** Luz e Sombra em sequência (≤3 s): dano ×1,3. */
        RUPTURE("rupture", 0xE8D0FF),
        /** Terra e Vento em sequência (≤3 s): cegueira 3 s. */
        SAND_EYES("sand_eyes", 0xE3C77A);

        private final String id;
        private final int color;

        Reaction(String id, int color) {
            this.id = id;
            this.color = color;
        }

        public String id() { return id; }
        public int color() { return color; }
        /** "ELETROCUTAR!", "DERRETER!"... */
        public MutableComponent displayName() {
            return Component.translatable("reaction.isekai_mahou." + id).withColor(color);
        }

        private static final Reaction[] VALUES = values();

        @Nullable
        public static Reaction byOrdinal(int i) { return i >= 0 && i < VALUES.length ? VALUES[i] : null; }
    }

    static {
        setMultiplier(id("blaze"), Element.FIRE, 0F);
        setMultiplier(id("blaze"), Element.WATER, 2F);
        setMultiplier(id("magma_cube"), Element.FIRE, 0F);
        setMultiplier(id("magma_cube"), Element.WATER, 1.75F);
        setMultiplier(id("strider"), Element.FIRE, 0F);
        setMultiplier(id("ghast"), Element.FIRE, 0.25F);
        setMultiplier(id("wither_skeleton"), Element.FIRE, 0.25F);
        setMultiplier(id("snow_golem"), Element.FIRE, 2F);
        setMultiplier(id("stray"), Element.FIRE, 1.5F);
        setMultiplier(id("drowned"), Element.WATER, 0.25F);
        setMultiplier(id("guardian"), Element.WATER, 0.25F);
        setMultiplier(id("elder_guardian"), Element.WATER, 0.25F);
        setMultiplier(id("enderman"), Element.WATER, 1.75F);
        setMultiplier(id("enderman"), Element.SHADOW, 0.5F);
        setMultiplier(id("phantom"), Element.LIGHT, 1.75F);
        setMultiplier(id("warden"), Element.SHADOW, 0.25F);
        setMultiplier(id("iron_golem"), Element.EARTH, 0.5F);
        setMultiplier(id("breeze"), Element.WIND, 0.25F);
    }

    private static ResourceLocation id(String path) { return ResourceLocation.withDefaultNamespace(path); }

    // =====================================================================================
    //  Resistências / fraquezas
    // =====================================================================================
    /** Define o multiplicador de dano de um elemento BASE para um tipo de entidade (fusões contam os 2 pais). */
    public static void setMultiplier(ResourceLocation entityType, Element base, float mult) {
        MULTIPLIERS.computeIfAbsent(entityType, k -> new HashMap<>()).put(base, mult);
    }

    public static float multiplier(EntityType<?> type, Element el) {
        Map<Element, Float> m = MULTIPLIERS.get(EntityType.getKey(type));
        float mult = 1F;
        if (m != null) {
            for (Map.Entry<Element, Float> e : m.entrySet()) {
                if (el.matches(e.getKey())) mult *= e.getValue();
            }
        }
        // Mortos-vivos são fracos contra Luz
        if (el.matches(Element.LIGHT) && type.is(net.minecraft.tags.EntityTypeTags.UNDEAD)) mult *= 1.5F;
        return mult;
    }

    // =====================================================================================
    //  Último elemento (reações de sequência e XP de abate)
    // =====================================================================================
    /** Marca o último elemento que atingiu a entidade (para reações combinadas e XP de abate). */
    public static void mark(LivingEntity target, Element el, UUID caster, long gameTime) {
        CompoundTag t = target.getPersistentData().getCompound(TAG);
        t.putString("prev_el", t.getString("el"));
        t.putLong("prev_time", t.getLong("time"));
        t.putString("el", el.id);
        t.putLong("time", gameTime);
        if (caster != null) t.putUUID("caster", caster);
        target.getPersistentData().put(TAG, t);
    }

    /** Último elemento que atingiu a entidade nos últimos {@code maxAge} ticks (ou null). */
    public static Element lastElement(LivingEntity target, long now, long maxAge) {
        CompoundTag t = target.getPersistentData().getCompound(TAG);
        if (t.isEmpty() || now - t.getLong("time") > maxAge) return null;
        return Element.byId(t.getString("el"));
    }

    public static UUID lastCaster(LivingEntity target) {
        CompoundTag t = target.getPersistentData().getCompound(TAG);
        return t.hasUUID("caster") ? t.getUUID("caster") : null;
    }

    // =====================================================================================
    //  Classes de elemento para as reações
    // =====================================================================================
    /** Fogo "de verdade" (aplica Em Chamas): Fogo, Magma, Sol, Chama Infernal. */
    public static boolean isFire(Element e) {
        return e == Element.FIRE || e == Element.MAGMA || e == Element.SUN || e == Element.HELLFIRE;
    }

    /** Água (apaga, Vapor): todo elemento com Água como pai, menos Gelo. */
    public static boolean isWater(Element e) {
        return e.matches(Element.WATER) && e != Element.ICE;
    }

    public static boolean isIce(Element e) { return e == Element.ICE; }

    public static boolean isLightning(Element e) { return e == Element.LIGHTNING; }

    /** Vento (Incêndio, Lacerar, Areia nos Olhos): Vento e fusões de Vento, menos Gelo e Raio (têm reações próprias). */
    public static boolean isWind(Element e) {
        return e.matches(Element.WIND) && e != Element.ICE && e != Element.LIGHTNING;
    }

    public static boolean isEarth(Element e) { return e.matches(Element.EARTH); }

    // =====================================================================================
    //  Dano de magia
    // =====================================================================================
    /** Aplica multiplicadores, condições de status e reações. Retorna o dano final. */
    public static float onSpellHit(SpellContext ctx, LivingEntity target, Element el, float dmg) {
        ServerLevel level = ctx.level();
        long now = level.getGameTime();
        dmg *= multiplier(target.getType(), el);
        if (dmg <= 0) return 0;

        LivingEntity causer = ctx.caster();
        float power = statusPower(ctx);
        // Caminho rápido: sem nenhum efeito ativo e sem fogo, não existe condição para reagir (a maioria dos golpes).
        // Todas as condições são efeitos de poção; Em Chamas também vale pelo fogo do vanilla.
        boolean clean = target.getActiveEffectsMap().isEmpty() && !target.isOnFire();

        // Exposto: +25% por nível
        MobEffectInstance exposed = clean ? null : target.getEffect(CoreRegistries.EXPOSED);
        if (exposed != null) dmg *= 1.0F + 0.25F * (exposed.getAmplifier() + 1);

        int burning = clean ? 0 : Statuses.stacks(target, Kind.BURNING);
        int chilled = clean ? 0 : Statuses.stacks(target, Kind.CHILLED);
        int shocked = clean ? 0 : Statuses.stacks(target, Kind.SHOCKED);
        int bleeding = clean ? 0 : Statuses.stacks(target, Kind.BLEEDING);
        boolean wet = !clean && Statuses.has(target, Kind.WET);
        boolean frozen = !clean && Statuses.has(target, Kind.FROZEN);
        boolean stone = !clean && Statuses.stacks(target, Kind.PETRIFIED) >= 3;

        // Bônus passivos
        if (isFire(el) && burning > 0) dmg *= 1.0F + 0.08F * burning;
        if (isLightning(el) && shocked > 0) dmg *= 1.0F + 0.15F * shocked;

        // ---------- Uma reação de condição por golpe ----------
        Reaction reaction = null;
        boolean skipAuto = false;
        if (clean) {
            // nada a reagir
        } else if (isLightning(el) && wet) {
            reaction = Reaction.ELECTROCUTE;
            dmg *= 2.0F;
            StatusEngine.clear(target, Kind.WET);
            Statuses.apply(target, Kind.STUNNED, 1, 40, causer, power);
            electrocuteArcs(ctx, target, dmg * 0.5F);
        } else if (isIce(el) && wet && !Statuses.has(target, Kind.FROZEN) && !StatusEngine.frozenImmune(target)) {
            // (Imune a Congelado: cai na condição automática — Gelado em dobro por estar Molhado)
            reaction = Reaction.FREEZE;
            StatusEngine.clear(target, Kind.WET);
            Statuses.apply(target, Kind.FROZEN, 1, StatusEngine.FREEZE_TICKS, causer, power);
            skipAuto = true;
        } else if ((isFire(el) && (chilled > 0 || frozen)) || (isIce(el) && burning > 0)) {
            reaction = Reaction.MELT;
            dmg *= 1.6F;
            StatusEngine.clear(target, Kind.BURNING);
            StatusEngine.clear(target, Kind.CHILLED);
            StatusEngine.clear(target, Kind.FROZEN);
            skipAuto = true;
        } else if (isLightning(el) && burning > 0) {
            reaction = Reaction.OVERLOAD;
            StatusEngine.clear(target, Kind.BURNING);
            dmg += overload(ctx, target, 3.0F * power);
        } else if (isLightning(el) && chilled > 0) {
            reaction = Reaction.SUPERCONDUCT;
            target.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, Statuses.isBossLike(target) ? 50 : 100, 1), causer);
        } else if (isWater(el) && burning > 0) {
            reaction = Reaction.VAPOR;
            StatusEngine.clear(target, Kind.BURNING);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(2.5),
                    e -> e != causer && !ctx.isAlly(e) && e.isAlive())) {
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0), causer);
            }
            skipAuto = true;
        } else if (isWind(el) && burning > 0) {
            reaction = Reaction.WILDFIRE;
            int spread = Math.min(3, burning);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(3.0),
                    e -> e != causer && e != target && !ctx.isAlly(e) && e.isAlive())) {
                Statuses.apply(e, Kind.BURNING, spread, Statuses.DEFAULT_TICKS, causer, power);
            }
        } else if ((frozen || stone) && isEarth(el)) {
            reaction = Reaction.SHATTER;
            dmg *= 1.75F;
            StatusEngine.clear(target, Kind.FROZEN);
            StatusEngine.clear(target, Kind.PETRIFIED);
            skipAuto = true;
        } else if (isWind(el) && bleeding > 0) {
            reaction = Reaction.LACERATE;
            Statuses.apply(target, Kind.BLEEDING, 3, Statuses.DEFAULT_TICKS, causer, power);
        } else if (isFire(el) && wet) {
            // Fogo em alvo Molhado: −50% e só evapora a água (sem reação com nome)
            dmg *= 0.5F;
            StatusEngine.clear(target, Kind.WET);
            StatusEngine.hiss(target);
            skipAuto = true;
        }
        if (reaction != null) trigger(target, reaction, ctx, causer, el);

        // ---------- Reações de sequência ----------
        Element prev = lastElement(target, now, SEQUENCE_TICKS);
        if (prev != null && prev != el) {
            if ((isEarth(prev) && isWind(el)) || (isWind(prev) && isEarth(el))) {
                target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), causer);
                trigger(target, Reaction.SAND_EYES, ctx, causer, el);
            }
            if ((prev.matches(Element.LIGHT) && el.matches(Element.SHADOW)) || (prev.matches(Element.SHADOW) && el.matches(Element.LIGHT))) {
                dmg *= 1.3F;
                trigger(target, Reaction.RUPTURE, ctx, causer, el);
            }
        }

        // ---------- Condição automática do elemento ----------
        if (!skipAuto && target.isAlive()) autoApply(target, el, causer, power);
        return dmg;
    }

    /** Condição de cada elemento (§2.1). Terra, Vento, Luz, Sombra e Arcano: nada automático. */
    private static void autoApply(LivingEntity target, Element el, @Nullable LivingEntity causer, float power) {
        switch (el) {
            case FIRE, MAGMA, SUN, HELLFIRE -> Statuses.apply(target, Kind.BURNING, 1, Statuses.DEFAULT_TICKS, causer, power);
            case WATER, ABYSS, STEAM -> Statuses.apply(target, Kind.WET, 1, Statuses.DEFAULT_TICKS, causer, power);
            case ICE -> Statuses.apply(target, Kind.CHILLED, 1, Statuses.DEFAULT_TICKS, causer, power);
            case LIGHTNING -> Statuses.apply(target, Kind.SHOCKED, 1, Statuses.DEFAULT_TICKS, causer, power);
            case CRYSTAL -> Statuses.apply(target, Kind.PETRIFIED, 1, Statuses.DEFAULT_TICKS, causer, power);
            // "Corrompido curto": até II pelo automático (feixes não empilham até V); glifos podem passar disso
            case TWILIGHT -> StatusEngine.applyUpTo(target, Kind.CORRUPTED, 1, 60, causer, power, 2);
            default -> { }
        }
    }

    /** Poder das condições aplicadas por esta magia (poder da conjuração × poder mágico do conjurador). */
    private static float statusPower(SpellContext ctx) {
        float p = ctx.powerMult() * ctx.casterSpellPower();
        return Float.isFinite(p) ? Math.max(0.25F, Math.min(6.0F, p)) : 1.0F;
    }

    /** Eletrocutar: arcos para outros alvos Molhados a ≤4 blocos (sem novas reações em cadeia). */
    private static void electrocuteArcs(SpellContext ctx, LivingEntity target, float arcDamage) {
        ServerLevel level = ctx.level();
        LivingEntity causer = ctx.caster();
        Vec3 from = StatusEngine.center(target);
        int arcs = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(4.0),
                e -> e != target && e != causer && e.isAlive() && !ctx.isAlly(e) && e.hasEffect(CoreRegistries.SOAKED))) {
            if (e.distanceToSqr(target) > 16.0 || arcs++ >= 6) continue;
            // Arco elétrico: um item no lote do tick (antes, até 25 pacotes de faísca por arco)
            ElementFx.line(level, from, StatusEngine.center(e), Element.LIGHTNING);
            StatusEngine.clear(e, Kind.WET);
            e.hurt(ctx.damageSource(null), arcDamage);
            Statuses.apply(e, Kind.SHOCKED, 1, Statuses.DEFAULT_TICKS, causer, statusPower(ctx));
        }
    }

    /** Sobrecarga: mini-explosão de 2,5 blocos (sem quebrar blocos). Retorna o dano extra para o próprio alvo. */
    private static float overload(SpellContext ctx, LivingEntity target, float blast) {
        ServerLevel level = ctx.level();
        LivingEntity causer = ctx.caster();
        Vec3 c = StatusEngine.center(target);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, target.getBoundingBox().inflate(2.5),
                e -> e != target && e != causer && e.isAlive() && !ctx.isAlly(e))) {
            if (e.position().distanceToSqr(target.position()) > 2.5 * 2.5 + e.getBbWidth()) continue;
            e.hurt(ctx.damageSource(null), blast);
            Vec3 away = e.position().subtract(target.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
            e.push(away.x * 0.6, 0.35, away.z * 0.6);
            e.hurtMarked = true;
        }
        // Visual da explosão: a própria reação (FxBurst REACTION/OVERLOAD) já desenha clarão, anéis e brasas
        ElementFx.shake(level, c, 14, 0.5F, 8);
        return blast;
    }

    // =====================================================================================
    //  Disparo de uma reação (efeitos, texto, evento, contadores)
    // =====================================================================================
    private static void trigger(LivingEntity target, Reaction r, @Nullable SpellContext ctx, @Nullable LivingEntity causer,
                                @Nullable Element el) {
        if (!(target.level() instanceof ServerLevel level)) return;
        // A mesma reação repetida no mesmo alvo em <0,5 s (feixes): a mecânica vale, o anúncio/contador não repete
        if (!ReactionFx.claim(target, r)) return;
        // Orçamento por tick: as primeiras ganham explosão completa + som + texto; as seguintes, explosão enxuta; depois
        // disso só a mecânica (evento e contadores continuam valendo para todas)
        long now = level.getGameTime();
        if (now != fxTick) {
            fxTick = now;
            fxThisTick = 0;
        }
        int n = ++fxThisTick;
        if (n <= FX_FULL_PER_TICK) {
            ReactionFx.play(level, target, r); // som + texto flutuante
            ElementFx.reaction(level, target, r, false); // explosão própria da reação, montada no cliente pelo preset
        } else if (n <= FX_MAX_PER_TICK) {
            ElementFx.reaction(level, target, r, true);
        }
        NeoForge.EVENT_BUS.post(new ElementReactionEvent(target, r, ctx, causer, el));
        ServerPlayer sp = causer instanceof ServerPlayer p ? p : null;
        if (sp != null) {
            MagicApi.addCounter(sp, "reaction." + r.id(), 1);
            MagicApi.addCounter(sp, "reaction.any", 1);
        }
    }

    /**
     * Estilhaçar por golpe corpo a corpo (o dano ×1,75 já foi aplicado no evento de dano): quebra o gelo/pedra e
     * dispara a reação. Chamado pelo núcleo de status no fim do tick.
     */
    public static void shatterByMelee(LivingEntity target, @Nullable LivingEntity attacker) {
        if (target == null || target.level().isClientSide) return;
        boolean was = Statuses.has(target, Kind.FROZEN) || Statuses.stacks(target, Kind.PETRIFIED) >= 3;
        if (!was) return;
        StatusEngine.clear(target, Kind.FROZEN);
        StatusEngine.clear(target, Kind.PETRIFIED);
        trigger(target, Reaction.SHATTER, null, attacker, null);
    }

    private ElementReactions() {}
}
