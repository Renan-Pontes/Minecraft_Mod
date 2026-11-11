package com.renanpontes.isekaimahou.feature.class_rogue.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueCombat;
import com.renanpontes.isekaimahou.feature.class_rogue.RogueFx;
import com.renanpontes.isekaimahou.feature.class_rogue.entity.RogueAfterimageEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Forma "Ritmo de Eco" (homenagem à técnica de passos em ritmo do jovem assassino; Sombra, origem do Ladino, Círculo 3):
 * você corre em volta do inimigo na mira (até 14 blocos; Acelerar +3) num ritmo que confunde os olhos — 3 imagens
 * residuais (+1 por Dividir, até 6) giram em volta dele enquanto você some. O alvo fica perdido (monstros largam o
 * alvo e ficam lentos). Depois de 1,2 s cada imagem avança e aplica os efeitos com 40% do poder, e você aparece NAS
 * COSTAS dele e aplica os efeitos inteiros (Apunhalar ×2,5!). Sem alvo: as imagens ficam em volta de você como iscas.
 * Custo por cópia: 1 + 0,4 por imagem.
 */
public class RhythmEchoForm extends FormGlyph {
    public static final float RANGE = 14.0F;
    public static final int SPIN = 24;
    public static final int COLOR = 0xB06BFF;

    public RhythmEchoForm() {
        super("rogue_rhythm_echo", 16);
        origin(MagicPath.ROGUE);
        element(Element.SHADOW);
        tier(3);
        minCircle(3);
    }

    @Override
    public int baseCastTicks() { return 4; }

    @Override
    public int baseCooldown() { return 80; }

    @Override
    public float copies(SpellStats stats, int circle) {
        return 1F + 0.4F * images(stats);
    }

    @Override
    public float hintRange() { return RANGE; }

    @Override
    public int hintDuration() { return SPIN; }

    private static int images(SpellStats s) {
        return Math.min(6, 3 + Math.max(0, s.split));
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null || !caster.isAlive()) return;
        ServerLevel sl = ctx.level();
        SpellStats s = ctx.formStats();
        int n = images(s);
        float range = RANGE + 3F * Math.max(0, s.speed);
        LivingEntity target = ShadowStepForm.aimTarget(ctx, caster, range);
        caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, SPIN + 6, 0, false, false));
        RogueFx.vanish(sl, caster.position());
        RogueFx.sound(sl, caster.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 1.2F);
        if (!s.silent) Announce.send(caster, Component.translatable("announce.isekai_mahou.rogue_rhythm_echo"), COLOR, Announce.STYLE_SPELL);
        LivingEntity center = target != null ? target : caster;
        List<RogueAfterimageEntity> imgs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            RogueAfterimageEntity img = RogueAfterimageEntity.orbit(sl, caster, center, target != null ? 2.4 : 1.8, a,
                    0.32, target != null ? SPIN + 2 * n + 6 : 60, COLOR, false);
            sl.addFreshEntity(img);
            imgs.add(img);
        }
        if (target == null) {
            // sem alvo: iscas — monstros perto largam você
            for (Mob mob : sl.getEntitiesOfClass(Mob.class, caster.getBoundingBox().inflate(10))) {
                if (mob.getTarget() == caster && !Statuses.isBossLike(mob)) mob.setTarget(null);
            }
            RogueCombat.startAmbush(caster, 40);
            ctx.end(caster.position(), ctx.look());
            return;
        }
        // o alvo se perde no ritmo
        if (target instanceof Mob mob && !Statuses.isBossLike(mob)) mob.setTarget(null);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SPIN, 1), caster);
        for (int i = 0; i < n; i++) {
            final RogueAfterimageEntity img = imgs.get(i);
            SpellScheduler.schedule(SPIN + i * 2, () -> {
                if (!target.isAlive() || img.isRemoved()) return;
                img.lunge();
                ctx.copy(ctx.powerMult() * 0.4F).resolveOnEntity(target, null);
                RogueFx.sound(sl, target.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.3F + 0.1F * (img.getId() % 3));
            });
        }
        SpellScheduler.schedule(SPIN + n * 2 + 2, () -> {
            if (!caster.isAlive() || !target.isAlive() || caster.level() != sl) return;
            if (RogueCombat.teleportBehind(sl, caster, target, range + 6)) {
                RogueCombat.forceBack(caster, true);
                try {
                    ctx.resolveOnEntity(target, null);
                } finally {
                    RogueCombat.forceBack(caster, false);
                }
                RogueFx.slash(sl, target, RogueFx.BLOOD);
                RogueFx.sound(sl, target.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.7F);
            }
            ctx.end(target.position(), ctx.look());
        });
    }
}
