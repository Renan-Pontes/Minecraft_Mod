package com.renanpontes.isekaimahou.glyph;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * v1.0 — Detalhamento de uma magia ({@link ParsedSpell#breakdown()}) para o Grimório e tooltips.
 * <p>
 * Todos os custos aqui já estão em MANA de verdade (com o custo por cópia, elementos extras, Eficiente, Instantâneo e
 * a escala ×3). Danos/curas são ESTIMATIVAS: número base do glifo ({@link EffectGlyph#baseDamage()}) × poder dos
 * aumentos × {@code DAMAGE_SCALE} — sem crítico, reações, resistências, Carregar no cântico ou bônus de equipamento.
 * Classe comum (sem imports de cliente): pode ser usada nos dois lados.
 *
 * @param primary      elemento primário
 * @param manaCost     custo final em mana (igual a {@link ParsedSpell#manaCost()})
 * @param baseCost     custo "antigo" (sem cópias nem escala) — ver {@link ParsedSpell#baseCost()}
 * @param castTicks    tempo de conjuração (ticks)
 * @param cooldown     recarga (ticks)
 * @param glyphCount   glifos na receita
 * @param ultimate     forma de ultimate (slot G, usa a carga)
 * @param stages       estágios em ordem (o primeiro é a raiz)
 * @param damage       dano estimado somando tudo (todas as cópias de todos os estágios, um alvo por cópia)
 * @param heal         cura estimada somando tudo
 * @param perCopyExtra quanto do custo vem das cópias extras (0 = magia sem cópias)
 */
public record SpellBreakdown(Element primary, int manaCost, int baseCost, int castTicks, int cooldown, int glyphCount,
                             boolean ultimate, List<StagePart> stages, float damage, float heal, int perCopyExtra) {

    /** Fração do custo vinda de cópias a partir da qual o Grimório deve avisar ("custo por cópia alto"). */
    public static final float HEAVY_COPY_FRACTION = 0.5F;

    /**
     * Um efeito dentro de um estágio.
     *
     * @param effect       o glifo
     * @param stats        aumentos aplicados nele
     * @param costPerCopy  mana deste efeito (com os aumentos dele) por cópia
     * @param multiplier   quantas vezes ele roda por cópia: (1 + Repetir) × (1 + Encadear) do próprio efeito
     * @param copies       cópias do estágio × disparos do estágio (instâncias que pagam este efeito)
     * @param totalCost    mana deste efeito somando todas as cópias
     * @param damagePerHit dano estimado por acerto (0 = sem dano direto)
     * @param damageTotal  dano estimado somando as cópias (um alvo por cópia)
     * @param healPerHit   cura estimada por acerto
     * @param healTotal    cura estimada somando as cópias
     * @param durationTicks duração estimada (ticks; &lt; 0 = instantâneo)
     * @param radius       raio estimado (blocos; &lt; 0 = alvo único)
     */
    public record EffectPart(EffectGlyph effect, SpellStats stats, int costPerCopy, float multiplier, float copies,
                             int totalCost, float damagePerHit, float damageTotal, float healPerHit, float healTotal,
                             int durationTicks, float radius) {}

    /**
     * Um estágio.
     *
     * @param index        0 = primeiro
     * @param form         forma do estágio
     * @param linkIn       ligação que dispara este estágio (null no estágio 0)
     * @param formCost     mana da forma + aumentos dela (vezes os disparos do estágio)
     * @param fixedCost    mana de gatilhos/ligação/leis deste estágio
     * @param copies       cópias da forma (suavizadas)
     * @param instances    cópias × Repetir × Encadear da forma
     * @param triggers     quantas vezes o estágio pode disparar (estágio 0 = 1)
     * @param range        alcance estimado (blocos; &lt; 0 = desconhecido)
     * @param radius       raio estimado da forma (blocos; &lt; 0 = sem área)
     * @param durationTicks duração estimada da forma (ticks; &lt; 0 = instantânea)
     * @param effects      efeitos do estágio
     * @param totalCost    mana do estágio inteiro
     * @param damage       dano estimado do estágio
     * @param heal         cura estimada do estágio
     */
    public record StagePart(int index, FormGlyph form, @Nullable LinkGlyph.LinkKind linkIn, int formCost, int fixedCost,
                            float copies, float instances, float triggers, float range, float radius, int durationTicks,
                            List<EffectPart> effects, int totalCost, float damage, float heal) {}

    /** Monta o detalhamento a partir da raiz (ou de um estágio, contando dali em diante). */
    public static SpellBreakdown of(ParsedSpell root) {
        float dmgScale = SpellCost.damageScale();
        List<StagePart> stages = new ArrayList<>();
        float damage = 0F, heal = 0F;
        float rawAll = 0F, rawSingle = 0F;
        LinkGlyph.LinkKind linkIn = null;
        int index = 0;
        for (ParsedSpell st : root.stages()) {
            ParsedSpell.StageCost c = st.stageCost();
            float scale = c.scale();
            float trig = c.triggers();
            List<EffectPart> parts = new ArrayList<>();
            float stDamage = 0F, stHeal = 0F;
            for (int i = 0; i < st.effects().size(); i++) {
                ParsedSpell.EffectEntry entry = st.effects().get(i);
                EffectGlyph eff = entry.effect();
                SpellStats s = entry.stats();
                int perCopyRaw = i < c.effectCosts().size() ? c.effectCosts().get(i) : Math.max(1, eff.cost());
                float mult = i < c.effectMults().size() ? c.effectMults().get(i) : 1F;
                float copies = c.instances() * trig;
                int perCopy = Math.round(perCopyRaw * scale);
                int total = Math.round(perCopyRaw * mult * copies * scale);
                float dmgHit = eff.baseDamage() * s.power() * dmgScale;
                float healHit = eff.baseHeal() * s.power();
                float dmgTotal = dmgHit * mult * copies;
                float healTotal = healHit * mult * copies;
                int dur = eff.hintDuration() >= 0 ? s.duration(eff.hintDuration()) : -1;
                float rad = eff.hintRadius() >= 0 ? s.radius(eff.hintRadius()) : -1F;
                parts.add(new EffectPart(eff, s, perCopy, mult, copies, total, dmgHit, dmgTotal, healHit, healTotal, dur, rad));
                stDamage += dmgTotal;
                stHeal += healTotal;
                rawSingle += perCopyRaw;
            }
            rawSingle += c.formCost() + c.fixedCost();
            rawAll += c.total() / Math.max(1.0E-4F, scale);
            SpellStats fs = st.formStats();
            FormGlyph form = st.form();
            float range = form.hintRange();
            float radius = form.hintRadius() >= 0 ? fs.radius(form.hintRadius()) : -1F;
            int dur = form.hintDuration() >= 0 ? fs.duration(form.hintDuration()) : -1;
            stages.add(new StagePart(index, form, linkIn, Math.round(c.formCost() * trig * scale), Math.round(c.fixedCost() * trig * scale),
                    c.copies(), c.instances(), trig, range, radius, dur, List.copyOf(parts), c.total(), stDamage, stHeal));
            damage += stDamage;
            heal += stHeal;
            linkIn = st.linkKind();
            index++;
        }
        float scale = root.stageCost().scale();
        int extra = Math.max(0, Math.round((rawAll - rawSingle) * scale));
        return new SpellBreakdown(root.primary(), root.manaCost(), root.baseCost(), root.castTicks(), root.cooldown(),
                root.glyphCount(), root.isUltimate(), List.copyOf(stages), damage, heal, Math.min(extra, root.manaCost()));
    }

    /** Mais da metade do custo vem de cópias extras (o Grimório mostra um aviso). */
    public boolean heavyCopies() {
        return manaCost > 0 && perCopyExtra >= manaCost * HEAVY_COPY_FRACTION;
    }

    /**
     * Linhas prontas (tooltip/painel): totais, cada estágio e cada efeito com custo/cópias/dano/duração, e o aviso de
     * custo por cópia. O texto vem das traduções {@code spell_info.isekai_mahou.*}.
     */
    public List<Component> lines() {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("spell_info.isekai_mahou.total", manaCost, seconds(castTicks), seconds(cooldown))
                .withStyle(ChatFormatting.AQUA));
        if (damage > 0 || heal > 0) {
            MutableComponent l = Component.translatable("spell_info.isekai_mahou.estimate");
            if (damage > 0) l.append(" ").append(Component.translatable("spell_info.isekai_mahou.damage", num(damage)).withStyle(ChatFormatting.RED));
            if (heal > 0) l.append(" ").append(Component.translatable("spell_info.isekai_mahou.heal", num(heal)).withStyle(ChatFormatting.GREEN));
            out.add(l.withStyle(ChatFormatting.GRAY));
        }
        for (StagePart st : stages) {
            MutableComponent head = Component.translatable("spell_info.isekai_mahou.stage", st.index() + 1, st.form().displayName(), st.totalCost())
                    .withStyle(ChatFormatting.GOLD);
            if (st.linkIn() != null) {
                head.append(" ").append(Component.translatable("spell_info.isekai_mahou.link." + st.linkIn().name().toLowerCase(Locale.ROOT))
                        .withStyle(ChatFormatting.DARK_AQUA));
            }
            out.add(head);
            MutableComponent form = Component.literal("  ").append(Component.translatable("spell_info.isekai_mahou.form_cost", st.formCost()));
            if (st.instances() > 1.001F || st.triggers() > 1.001F) {
                form.append(" · ").append(Component.translatable("spell_info.isekai_mahou.copies", num(st.instances() * st.triggers())));
            }
            if (st.range() >= 0) form.append(" · ").append(Component.translatable("spell_info.isekai_mahou.range", num(st.range())));
            if (st.radius() >= 0) form.append(" · ").append(Component.translatable("spell_info.isekai_mahou.radius", num(st.radius())));
            if (st.durationTicks() >= 0) form.append(" · ").append(Component.translatable("spell_info.isekai_mahou.duration", seconds(st.durationTicks())));
            out.add(form.withStyle(ChatFormatting.GRAY));
            for (EffectPart p : st.effects()) {
                MutableComponent l = Component.literal("  • ").append(p.effect().displayName().copy().withColor(p.effect().argb() & 0xFFFFFF))
                        .append(" ");
                if (p.copies() * p.multiplier() > 1.001F) {
                    l.append(Component.translatable("spell_info.isekai_mahou.effect_cost_copies", p.costPerCopy(), num(p.copies() * p.multiplier()), p.totalCost()));
                } else {
                    l.append(Component.translatable("spell_info.isekai_mahou.effect_cost", p.totalCost()));
                }
                if (p.damagePerHit() > 0) l.append(" · ").append(Component.translatable("spell_info.isekai_mahou.damage_hit", num(p.damagePerHit())).withStyle(ChatFormatting.RED));
                if (p.healPerHit() > 0) l.append(" · ").append(Component.translatable("spell_info.isekai_mahou.heal_hit", num(p.healPerHit())).withStyle(ChatFormatting.GREEN));
                if (p.durationTicks() >= 0) l.append(" · ").append(Component.translatable("spell_info.isekai_mahou.duration", seconds(p.durationTicks())));
                if (p.radius() >= 0) l.append(" · ").append(Component.translatable("spell_info.isekai_mahou.radius", num(p.radius())));
                out.add(l.withStyle(ChatFormatting.GRAY));
            }
        }
        if (perCopyExtra > 0) {
            out.add(Component.translatable(heavyCopies() ? "spell_info.isekai_mahou.copies_warning" : "spell_info.isekai_mahou.copies_note", perCopyExtra)
                    .withStyle(heavyCopies() ? ChatFormatting.YELLOW : ChatFormatting.DARK_GRAY));
        }
        return out;
    }

    /** "12" ou "12.5" (uma casa só quando precisa). */
    public static String num(float v) {
        if (Math.abs(v - Math.round(v)) < 0.05F) return Integer.toString(Math.round(v));
        return String.format(Locale.ROOT, "%.1f", v);
    }

    /** Ticks em segundos ("1.5"). */
    public static String seconds(int ticks) {
        return num(Math.max(0, ticks) / 20F);
    }
}
