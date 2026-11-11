package com.renanpontes.isekaimahou.feature.domain.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.feature.domain.DomainFx;
import com.renanpontes.isekaimahou.feature.domain.DomainGlyphs;
import com.renanpontes.isekaimahou.feature.domain.entity.DomainDomeEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Expansão de Domínio (ultimate, aceita Leis): ergue um domo centrado no conjurador. Liberada no Círculo de
 * {@link Progression#domainAllowed}. Preso ao conjurador: não pode ser estágio 2+ (mas pode ser o estágio 0 de uma
 * magia programável: Ao Abater/Ao Acertar durante os pulsos, Ao Terminar quando o domo acaba).
 * v0.8: raio 12 + 2,5·Círculo + 6 por Expandir (Concentrar ×0,7; Barreira Aberta ×1,6, ×1,75 com Domínio Absoluto;
 * Lei Santuário Maligno do legends_c força aberto ×1,5), duração 250 ticks (aumentos de duração valem).
 * Barreira com integridade 60 + 20·Círculo + 0,5·mana (+25% com Domínio Absoluto): quebra POR FORA, não por dentro.
 * Com Inverter logo depois da forma: barreira INVERTIDA (quebra só por dentro; custo +10% de mana).
 * Lá dentro os efeitos da magia acertam os inimigos a cada segundo. Exemplo ("Inferno Sem Saída"):
 * Expansão de Domínio → Incendiar → Explosão → Lei: Acerto Garantido → Lei: Sem Fuga.
 */
public class DomainExpansionForm extends FormGlyph {
    public static final float BASE_RADIUS = 12.0F;
    public static final float RADIUS_PER_CIRCLE = 2.5F;
    public static final float RADIUS_PER_AOE = 6.0F;
    public static final float MAX_RADIUS = 96.0F;
    public static final int BASE_DURATION = 250;
    /** Custo extra (fração do mana da magia) da barreira invertida. */
    public static final float INVERT_COST = 0.10F;

    public DomainExpansionForm() {
        super("domain_expansion", 120);
        tier(5);
        minCircle(DomainGlyphs.firstCircle(Progression::domainAllowed));
    }

    @Override
    public boolean isUltimate() { return true; }

    @Override
    public boolean acceptsLaws() { return true; }

    @Override
    public int baseCastTicks() { return 10; }

    @Override
    public int baseCooldown() { return 300; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        ParsedSpell spell = ctx.spell();
        SpellStats fs = ctx.formStats();

        int flags = DomainGlyphs.lawFlags(spell);
        boolean absolute = (flags & DomainDomeEntity.F_ABSOLUTE) != 0;
        boolean open = (flags & DomainDomeEntity.F_OPEN) != 0;
        int circle = DomainDomeEntity.circleOf(caster);
        float radius = BASE_RADIUS + RADIUS_PER_CIRCLE * circle + RADIUS_PER_AOE * Math.max(0, fs.aoe);
        if (fs.concentrate) radius *= 0.7F;
        if (spell.hasLaw(DomainGlyphs.OPEN_BARRIER)) radius *= absolute ? 1.75F : 1.6F;
        else if (spell.hasLaw(DomainGlyphs.SHRINE)) radius *= absolute ? 1.75F : 1.5F;
        radius = Math.min(MAX_RADIUS, radius);
        int duration = fs.duration(BASE_DURATION);

        // Inverter logo depois da forma: barreira invertida (só domínio fechado), +10% de mana
        if (fs.inverted && !open) {
            if (payInvert(caster, spell)) flags |= DomainDomeEntity.F_INVERTED;
        }
        // v1.0: custo BASE (sem cópias/escala) — a integridade continua no patamar da v0.8
        float integrity = 60F + 20F * circle + 0.5F * spell.baseCost();
        if (absolute) integrity *= 1.25F;

        Vec3 center = caster.position();
        DomainDomeEntity.replaceOld(level, caster.getUUID(), false);
        DomainDomeEntity dome = DomainDomeEntity.create(ctx, center, radius, duration, flags,
                DomainDomeEntity.scoreOf(caster, spell));
        if (!open) dome.initBarrier(integrity);
        level.addFreshEntity(dome);
        dome.clashOnCreate(level);

        float vol = fs.silent ? 0.4F : 1.0F;
        level.playSound(null, center.x, center.y, center.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 1.4F * vol, 1.2F);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 3.0F * vol, 0.6F);
        for (Element e : dome.elements()) {
            DomainFx.far(level, ElementFx.signature(e), center.x, center.y + 1, center.z, 50 / dome.elements().size(),
                    0.6, 1.0, 0.6, 0.15);
        }
        DomainFx.shake(level, center, radius * 2 + 8, 0.9F, 14);
    }

    /** Cobra os +10% de mana da barreira invertida (criativo e mobs não pagam). Sem mana: barreira normal + aviso. */
    private static boolean payInvert(LivingEntity caster, ParsedSpell spell) {
        if (!(caster instanceof ServerPlayer sp) || sp.getAbilities().instabuild) return true;
        float extra = Math.max(1F, spell.manaCost() * INVERT_COST);
        if (MagicApi.get(sp).consumeMana(extra)) return true;
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.domain_invert_no_mana", (int) Math.ceil(extra))
                .withStyle(ChatFormatting.RED), true);
        return false;
    }
}
