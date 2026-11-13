package com.renanpontes.isekaimahou.feature.glyphs_classes_b.augment;

import java.util.Map;
import java.util.WeakHashMap;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.class_quincy.Quincy;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbFx;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbIds;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbUtil;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Aumento do Quincy — <b>Sklaverei</b> (a "escravidão" do Reishi): cada inimigo que o glifo modificado ferir tem o
 * Reishi arrancado — fica Fraco (I) por 3 s e um fio azul de Reishi voa até você: +1 de Reishi guardado (até 3 por
 * conjuração). Se o golpe MATA, o corpo inteiro vira Reishi: +2 de Reishi e 3% do Foco. Quem não é Quincy (aprendeu por
 * Lição) não guarda Reishi: recupera 1,5% do recurso por alvo no lugar.
 */
public final class Sklaverei {
    public static final int MAX_PER_CAST = 3;
    public static final int KILL_BONUS = 2;
    public static final float KILL_FOCUS = 0.03F;
    public static final float NON_QUINCY_GAIN = 0.015F;

    /** Reishi já arrancado por conjuração (chave: stats da forma da raiz, um objeto por conjuração). */
    private static final Map<SpellStats, Integer> TAKEN = new WeakHashMap<>();

    public static void onSpellDamage(SpellDamageEvent.Post e) {
        if (!e.landed()) return;
        if (GcbUtil.here(e.stats(), e.ctx().formStats(), GcbIds.SKLAVEREI) <= 0) return;
        if (!(e.ctx().caster() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        LivingEntity target = e.target();
        if (!(target.level() instanceof ServerLevel sl) || e.ctx().isAlly(target)) return;
        SpellStats key = e.ctx().rootSpell().formStats();
        int taken = TAKEN.getOrDefault(key, 0);
        boolean quincy = Quincy.isQuincy(sp);
        int gained = 0;
        if (taken < MAX_PER_CAST) {
            TAKEN.put(key, taken + 1);
            if (quincy) gained += Quincy.addReishi(sp, 1);
            else MagicApi.get(sp).addMana(MagicApi.get(sp).maxMana() * NON_QUINCY_GAIN);
        }
        if (e.killed()) {
            if (quincy) {
                gained += Quincy.addReishi(sp, KILL_BONUS);
                Quincy.gainFocus(sp, KILL_FOCUS);
            }
        } else if (target.isAlive()) {
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0), sp);
        }
        // o fio de Reishi do alvo até a mão
        Vec3 from = GcbFx.center(target);
        Vec3 to = sp.position().add(0, sp.getBbHeight() * 0.6, 0);
        GcbFx.line(sl, from, to, GcbFx.dust(GcbFx.REISHI, 0.8F));
        sl.sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, e.killed() ? 12 : 4, 0.25, 0.35, 0.25, 0.03);
        GcbFx.sound(sl, from, SoundEvents.AMETHYST_BLOCK_HIT, 0.7F, e.killed() ? 0.9F : 1.7F);
        if (gained > 0) {
            sp.displayClientMessage(Quincy.reishiLine(MagicApi.get(sp)), true);
        }
    }

    public static void clear() {
        TAKEN.clear();
    }

    private Sklaverei() {}
}
