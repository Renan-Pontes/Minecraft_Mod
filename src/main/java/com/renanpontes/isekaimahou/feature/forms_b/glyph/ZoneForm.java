package com.renanpontes.isekaimahou.feature.forms_b.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.forms_b.FormsBRegistry;
import com.renanpontes.isekaimahou.feature.forms_b.entity.SpellZoneEntity;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Zona: círculo mágico no chão (onde você mira, até 24 blocos) que aplica os efeitos a cada segundo.
 * Como estágio de magia programável (ex.: Feixe → Ao Acertar → Zona) nasce no chão do ponto atingido.
 * A zona avisa o núcleo quando atinge alguém (Ao Acertar/Saltar) e quando some (Ao Terminar).
 */
public class ZoneForm extends FormGlyph {
    public ZoneForm() {
        super("zone", 12);
        element(Element.ARCANE);
        tier(2);
        minCircle(2);
    }

    @Override
    public boolean isPersistent() { return true; }

    @Override
    public boolean stageable() { return true; }

    @Override
    public int baseCastTicks() { return 10; }

    @Override
    public int baseCooldown() { return 60; }

    @Override
    public void cast(SpellContext ctx) {
        if (ctx.caster() == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        Vec3 at = FormsBUtil.aimGround(ctx, 24);
        float r = s.radius(3.0F);
        SpellZoneEntity zone = new SpellZoneEntity(FormsBRegistry.SPELL_ZONE.get(), level);
        zone.setup(ctx, s.duration(100), r);
        zone.setVariant(SpellZoneEntity.ZONE);
        zone.setPos(at.x, at.y + 0.01, at.z);
        level.addFreshEntity(zone);
        int pts = Math.max(20, (int) (r * 10));
        for (Element e : FormsBUtil.spellElements(ctx)) {
            ElementFx.ring(level, at, e, r, pts);
            ElementFx.burst(level, at.add(0, 0.3, 0), e, 12, 0.4);
        }
        // Visual: onda que abre até a borda da zona (+ pilar no centro nas magias fortes)
        int tier = com.renanpontes.isekaimahou.glyph.fx.SpellVisuals.tier(ctx);
        ElementFx.shockwave(level, at.add(0, 0.1, 0), ctx.color() & 0xFFFFFF, r * 1.1F, 12, null);
        if (tier >= 3) ElementFx.pillar(level, at, ctx.color() & 0xFFFFFF, 0.5F + 0.15F * tier, 4.0F + tier, 16);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.2F);
    }
}
