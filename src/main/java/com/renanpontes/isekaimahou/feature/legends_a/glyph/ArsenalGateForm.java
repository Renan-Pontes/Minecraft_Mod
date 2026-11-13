package com.renanpontes.isekaimahou.feature.legends_a.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.legends_a.LegendFx;
import com.renanpontes.isekaimahou.feature.legends_a.entity.ArsenalPortalEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Forma "Portão do Arsenal" (Arcano, tier 4): abre 4 + 2·Dividir portais dourados num arco atrás do conjurador por
 * 3 s (× aumentos de duração). Cada portal dispara uma arma a cada segundo no ponto mirado — ou nos inimigos perto
 * dele — e cada arma aplica os efeitos da magia ao acertar (Perfurar: atravessa mais alvos; Acelerar: armas mais rápidas).
 * Cada arma usa 45% do poder (são muitas). Como estágio de magia programável, o portão abre atrás do ponto de
 * impacto e dispara nele; as armas avisam os impactos (Ao Acertar/Saltar) e o fechamento avisa Ao Terminar.
 */
public class ArsenalGateForm extends FormGlyph {
    /**
     * Poder de cada arma (fração do poder da magia). Com 8 portais são 24 armas em 3 s: 45% mantém o "Portão do
     * Tesouro" forte (~100 de dano num alvo só se todas acertarem) sem derreter um Guardião (420 de vida) em 3 conjurações.
     */
    public static final float SHOT_POWER = 0.45F;
    public static final int BASE_DURATION = 60;
    public static final int INTERVAL = 20;
    public static final int MAX_PORTALS = 12;

    public ArsenalGateForm() {
        super("arsenal_gate", 30);
        element(Element.ARCANE);
        // v1.0: o caminho das lâminas é do Ferreiro (glifo de origem: qualquer elemento; outras classes só por Lição)
        origin(MagicPath.SMITH);
        tier(4);
        minCircle(4);
    }

    @Override
    public int baseCastTicks() { return 8; }

    /** O portão fica aberto 3 s: a recarga de 5 s impede manter um portão sempre aberto (rajada, pausa, rajada). */
    @Override
    public int baseCooldown() { return 100; }

    @Override
    public boolean stageable() { return true; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        ServerLevel sl = ctx.level();
        if (caster == null || caster.level() != sl) return;
        SpellStats s = ctx.formStats();
        int slots = Math.min(MAX_PORTALS, 4 + 2 * Math.max(0, s.split));
        int lifetime = ArsenalPortalEntity.OPEN_TICKS + s.duration(BASE_DURATION) + ArsenalPortalEntity.CLOSE_TICKS;
        int color = LegendFx.colorOf(ctx, LegendFx.GOLD);
        float size = Math.min(1.6F, 0.85F + 0.12F * Math.max(0, s.aoe));

        boolean bound = !ctx.hasOrigin();
        Vec3 dir = ctx.look();
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        Vec3 forward = flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
        // Estágio: o arco fica atrás do ponto de origem (o portão "abre" de onde a magia veio)
        Vec3 base = bound ? caster.position() : ctx.eyePos().subtract(forward.scale(3.0)).add(0, -0.5, 0);

        SpellContext shot = ctx.copy(ctx.powerMult() * SHOT_POWER);
        ArsenalGate gate = new ArsenalGate(shot, slots, bound, base, forward, color);
        for (int i = 0; i < slots; i++) {
            ArsenalPortalEntity portal = ArsenalPortalEntity.create(sl, gate, i, bound ? caster : null, color, lifetime, INTERVAL, size);
            sl.addFreshEntity(portal);
        }

        Vec3 center = gate.slotPos(slots / 2);
        ArsenalPortalEntity.openSound(sl, center);
        sl.sendParticles(LegendFx.dust(color, 1.6F), center.x, center.y, center.z, 30, 1.6, 0.8, 1.6, 0);
        sl.sendParticles(Vfx.endRod(), center.x, center.y, center.z, 12, 1.4, 0.6, 1.4, 0.02);
    }
}
