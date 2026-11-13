package com.renanpontes.isekaimahou.feature.class_rogue.glyph;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFx;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFxNet;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueTechniques;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * "Velocidade Divina" (homenagem ao corpo elétrico do herdeiro da família de assassinos; Raio, origem do Ladino,
 * Círculo 5). Converte a aura em eletricidade:
 * <ul>
 *   <li><b>Em você</b> (Si Mesmo) — "Rajada": 10 s (Estender +) de corpo elétrico: Velocidade III, Salto II e Pressa II;
 *   o primeiro golpe que viria a cada 1 s é DESVIADO por reflexo (fica uma imagem residual), você aparece nas costas de
 *   quem atacou e contra-ataca com 6 de raio; seus golpes corpo a corpo eletrocutam.</li>
 *   <li><b>Num inimigo</b> — "Trovão": um relâmpago desce no alvo (8 de dano de raio), Eletrocutado e Atordoado por 1 s
 *   (chefes não ficam atordoados).</li>
 * </ul>
 */
public class GodspeedEffect extends EffectGlyph {
    public static final float DAMAGE = 8.0F;
    public static final int TICKS = 200;
    public static final int COLOR = 0x9FE8FF;

    public GodspeedEffect() {
        super("rogue_godspeed", 24);
        origin(MagicPath.ROGUE);
        element(Element.LIGHTNING);
        tier(4);
        minCircle(5);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public int hintDuration() { return TICKS; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel sl = ctx.level();
        LivingEntity caster = ctx.caster();
        if (le == caster) {
            RogueTechniques.godspeed(le, ctx, stats, stats.duration(TICKS));
            if (!stats.silent) Announce.send(le, Component.translatable("announce.isekai_mahou.rogue_godspeed"), COLOR, Announce.STYLE_SPELL);
            return;
        }
        ctx.damage(le, DAMAGE, stats, null, Element.LIGHTNING);
        Vec3 c = RogueFx.center(le);
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
        if (bolt != null) {
            bolt.moveTo(le.getX(), le.getY(), le.getZ());
            bolt.setVisualOnly(true);
            sl.addFreshEntity(bolt);
        }
        RogueFxNet.at(sl, RogueFxNet.GODSPEED, c, Vec3.ZERO, 1F, COLOR, le.getId());
        RogueFx.sound(sl, c, SoundEvents.TRIDENT_THUNDER.value(), 0.6F, 1.6F);
        if (!le.isAlive()) return;
        Statuses.apply(le, Statuses.Kind.SHOCKED, 1, stats.duration(60), caster, stats.power() * ctx.powerMult());
        if (!Statuses.isBossLike(le)) le.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, 20, 0), caster);
    }
}
