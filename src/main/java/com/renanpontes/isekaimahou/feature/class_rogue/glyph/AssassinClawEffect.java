package com.renanpontes.isekaimahou.feature.class_rogue.glyph;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueCombat;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFx;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFxNet;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueTechniques;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * "Garra Assassina" (homenagem à mão-garra da família de assassinos de elite; Sombra, origem do Ladino): os dedos
 * viram garras — 9 de dano (×1,5 em quem já sangra) e 2 de Sangramento. Pelas costas, se a criatura ficar com 25% da
 * vida ou menos: <b>ARRANCAR O CORAÇÃO</b> — morte na hora e você recupera 15% da vida. Chefes não morrem assim; em
 * jogadores vira +40% de dano.
 */
public class AssassinClawEffect extends EffectGlyph {
    public static final float DAMAGE = 9.0F;
    public static final float EXECUTE_AT = 0.25F;

    public AssassinClawEffect() {
        super("rogue_assassin_claw", 14);
        origin(MagicPath.ROGUE);
        element(Element.SHADOW);
        tier(2);
        minCircle(2);
    }

    @Override
    public float baseDamage() { return DAMAGE; }

    @Override
    public int hintDuration() { return 80; }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        LivingEntity caster = ctx.caster();
        if (!(target instanceof LivingEntity le) || caster == null || le == caster || !le.isAlive()) return;
        ServerLevel sl = ctx.level();
        boolean back = RogueCombat.exposedBack(le, caster);
        boolean bleeding = Statuses.has(le, Statuses.Kind.BLEEDING);
        float dmg = DAMAGE * (bleeding ? 1.5F : 1F);
        if (back && le instanceof Player && le.getHealth() <= le.getMaxHealth() * EXECUTE_AT) dmg *= 1.4F;
        ctx.damage(le, dmg, stats, null, Element.SHADOW);
        claw(sl, le, caster);
        if (!le.isAlive()) return;
        Statuses.applyUpTo(le, Statuses.Kind.BLEEDING, 2, stats.duration(80), caster, stats.power() * ctx.powerMult(), 8);
        if (back && !(le instanceof Player) && !Statuses.isBossLike(le) && le.getHealth() <= le.getMaxHealth() * EXECUTE_AT) {
            Vec3 c = RogueFx.center(le);
            if (RogueTechniques.execute(le, ctx.damageSource(null))) {
                caster.heal(caster.getMaxHealth() * 0.15F);
                RogueFxNet.at(sl, RogueFxNet.HEART, c, Vec3.ZERO, 1F, RogueFx.BLOOD, le.getId());
                sl.sendParticles(ParticleTypes.HEART, caster.getX(), caster.getY() + 2.1, caster.getZ(), 3, 0.3, 0.1, 0.3, 0);
                RogueFx.sound(sl, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.2F, 0.5F);
                RogueFx.sound(sl, c, SoundEvents.WARDEN_HEARTBEAT, 1.2F, 0.6F);
                if (caster instanceof ServerPlayer sp) {
                    MagicApi.addCounter(sp, "rogue.heart_ripped", 1);
                    sp.displayClientMessage(Component.translatable("message.isekai_mahou.class_rogue.heart").withColor(RogueFx.BLOOD), true);
                }
            }
        }
    }

    /** Três riscos de garra cruzando o alvo. */
    private static void claw(ServerLevel sl, LivingEntity le, LivingEntity caster) {
        Vec3 c = RogueFx.center(le);
        Vec3 to = c.subtract(caster.position());
        Vec3 flat = new Vec3(to.x, 0, to.z);
        if (flat.lengthSqr() < 1.0E-4) flat = new Vec3(0, 0, 1);
        flat = flat.normalize();
        RogueFxNet.at(sl, RogueFxNet.CLAW, c, flat, 1F, RogueFx.BLOOD, le.getId());
        RogueFx.sound(sl, c, SoundEvents.PHANTOM_BITE, 1.0F, 1.2F + Mth.nextFloat(sl.random, 0F, 0.2F));
        RogueFx.sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.5F);
    }
}
