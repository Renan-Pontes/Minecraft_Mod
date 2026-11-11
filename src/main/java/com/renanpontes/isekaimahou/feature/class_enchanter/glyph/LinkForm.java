package com.renanpontes.isekaimahou.feature.class_enchanter.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.feature.class_enchanter.EnchFx;
import com.renanpontes.isekaimahou.feature.class_enchanter.Enchants;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * ELO DE ENCANTAMENTO (forma de origem do Encantador): fios de luz ligam você ao seu GRUPO (Party), aos seus mascotes e a
 * aliados por perto — sem precisar mirar nem enxergar — e os efeitos chegam a todos de uma vez (e em você).
 * Alcance 24 blocos (Encantador: 36). Até 2 aliados +2 por Dividir +1 a cada 3 Círculos (máx. 7). Efeitos ruins
 * não atingem aliados (a regra normal), então é a forma ideal para encantar a equipe.
 * Custo por cópia: os efeitos contam 1 + 0,5 por aliado possível.
 */
public class LinkForm extends FormGlyph {
    public LinkForm() {
        super("enchant_link", 8);
        element(Element.ARCANE);
        origin(MagicPath.ENCHANTER);
        tier(2);
    }

    @Override
    public int argb() { return 0xFF000000 | EnchFx.TEAL; }

    @Override
    public int baseCastTicks() { return 10; }

    @Override
    public int baseCooldown() { return 40; }

    /** Aliados que o Elo alcança além de você. */
    public static int maxAllies(SpellStats stats, int circle) {
        return Math.min(7, 2 + 2 * Math.max(0, stats.split) + Math.max(1, circle) / 3);
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return 1F + 0.5F * maxAllies(stats, circle);
    }

    @Override
    public float hintRange() { return 24F; }

    @Override
    public float hintRadius() { return -1F; }

    @Override
    public int hintDuration() { return -1; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        int circle = caster instanceof ServerPlayer sp ? MagicApi.get(sp).circle() : SpellCost.MOB_CIRCLE;
        double range = Enchants.linkRange(caster);
        List<LivingEntity> allies = gather(ctx, caster, range, maxAllies(ctx.formStats(), circle));

        EnchanterGlyphs.blessCaster(ctx);
        Vec3 from = EnchFx.center(caster).add(0, 0.3, 0);
        for (LivingEntity a : allies) {
            EnchFx.beam(level, from, EnchFx.center(a), EnchFx.TEAL, 0.7F);
            ctx.resolveOnEntity(a, null);
            EnchFx.ring(level, a.position().add(0, 0.1, 0), Math.max(0.7, a.getBbWidth()), EnchFx.WHITE, 14, 0.8F);
        }
        EnchFx.sigil(level, caster.position().add(0, 0.08, 0), 1.6, EnchFx.TEAL, EnchFx.WHITE, caster.tickCount * 0.1F);
        EnchFx.sound(level, caster.position(), SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.6F);
        EnchFx.sound(level, caster.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 1.2F);
    }

    /** Grupo primeiro (qualquer distância até o alcance), depois mascotes, depois aliados perto — do mais perto ao mais longe. */
    static List<LivingEntity> gather(SpellContext ctx, LivingEntity caster, double range, int max) {
        List<LivingEntity> out = new ArrayList<>();
        double r2 = range * range;
        if (caster instanceof ServerPlayer sp) {
            for (ServerPlayer m : Party.onlineMembers(sp)) {
                if (m == sp || !m.isAlive() || m.isSpectator() || m.level() != sp.level() || m.distanceToSqr(sp) > r2) continue;
                out.add(m);
            }
            out.sort(Comparator.comparingDouble(e -> e.distanceToSqr(caster)));
        }
        AABB box = caster.getBoundingBox().inflate(Math.min(range, 16.0));
        List<LivingEntity> near = new ArrayList<>(ctx.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e != caster && e.isAlive() && !e.isSpectator() && !out.contains(e)));
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(caster)));
        List<LivingEntity> pets = new ArrayList<>(), others = new ArrayList<>();
        for (LivingEntity e : near) {
            if (e instanceof OwnableEntity o && caster.getUUID().equals(o.getOwnerUUID())) pets.add(e);
            else if (ctx.isAlly(e) && (e instanceof Player || !(caster instanceof Player))) others.add(e);
        }
        out.addAll(pets);
        out.addAll(others);
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }
}
