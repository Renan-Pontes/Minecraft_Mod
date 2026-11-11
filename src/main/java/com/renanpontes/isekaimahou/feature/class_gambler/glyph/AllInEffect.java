package com.renanpontes.isekaimahou.feature.class_gambler.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_gambler.AnimeTricks;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.GbRegistries;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.GamblerDiceEntity;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckService;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * APOSTA TOTAL (homenagem ao anime das apostas compulsivas na escola): um DUELO de dados contra o alvo. Os dois rolam
 * 2d6 (dados gigantes aparecem sobre cada um) e quem tirar mais ROUBA do outro:
 * <ul>
 *   <li><b>Você vence</b>: 10 + 2 × diferença de dano no alvo (pela magia), você cura metade do que causou, rouba 1 efeito
 *   bom dele (+1 por Amplificar) e — se for jogador — 15% da mana atual; ele fica Azarado e você Sortudo.</li>
 *   <li><b>Empate</b>: os dois levam um pouco.</li>
 *   <li><b>A casa perde</b> (ele tirou mais): VOCÊ leva 6 + 1,5 × diferença (×1,5 da inflação) de dano mágico e fica
 *   Azarado; o alvo cura 30% disso. Toda aposta tem risco.</li>
 * </ul>
 * A Sorte de quem aposta pode rolar de novo um dado baixo; Dados Viciados: faces ≥ 3. Um Apostador com Sorte contra
 * outro Apostador: os dois usam a Sorte. Chefes não perdem efeitos.
 */
public class AllInEffect extends EffectGlyph {
    public static final float WIN_BASE = 10F;
    public static final float LOSE_BASE = 6F;
    private static final int SETTLE = 26;

    public AllInEffect() {
        super("gambler_all_in", 22);
        origin(MagicPath.GAMBLER);
        tier(4);
        minCircle(5);
    }

    @Override
    public float baseDamage() {
        return WIN_BASE + 4F;
    }

    @Override
    public int hintDuration() {
        return 160;
    }

    private static int face(RandomSource r, boolean loaded, int luck) {
        int f = loaded ? 3 + r.nextInt(4) : 1 + r.nextInt(6);
        if (f <= 2 && luck > 0 && r.nextFloat() < 0.08F * luck) f = Math.max(f, 1 + r.nextInt(6));
        return f;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        LivingEntity caster = ctx.caster();
        if (caster == null || target == caster || ctx.isAlly(target) || !(target instanceof LivingEntity le) || !le.isAlive()) return;
        ServerLevel level = ctx.level();
        boolean loaded = ctx.spell().anyStats(s -> s.custom(GamblerGlyphs.LOADED_DICE_ID) > 0);
        int myLuck = LuckService.luck(caster);
        int theirLuck = LuckService.luck(le);
        int[] mine = {face(level.random, loaded, myLuck), face(level.random, loaded, myLuck)};
        int[] theirs = {face(level.random, false, theirLuck), face(level.random, false, theirLuck)};
        showDice(level, caster, mine, 0xFFC94A);
        showDice(level, le, theirs, 0xC0504A);
        // o fio de ouro da aposta entre os dois
        Vec3 a = caster.position().add(0, caster.getBbHeight() + 0.6, 0), b = le.position().add(0, le.getBbHeight() + 0.6, 0);
        int steps = (int) Math.min(48, a.distanceTo(b) * 3);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = a.lerp(b, i / (double) Math.max(1, steps));
            level.sendParticles(GbFx.dust(i % 2 == 0 ? GbFx.GOLD : GbFx.PINK, 0.9F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        if (!ctx.formStats().silent) {
            Announce.send(caster, Component.translatable("announce.isekai_mahou.gambler.all_in"), GbFx.GOLD, Announce.STYLE_SPELL);
        }
        GbFx.sound(level, a, SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, 0.5F);
        GbFx.sound(level, b, SoundEvents.BELL_BLOCK, 0.8F, 0.6F);
        SpellScheduler.schedule(SETTLE, () -> resolve(ctx, caster, le, stats, mine[0] + mine[1], theirs[0] + theirs[1]));
    }

    private static void showDice(ServerLevel level, LivingEntity who, int[] faces, int rgb) {
        Vec3 top = who.position().add(0, who.getBbHeight() + 2.2, 0);
        level.addFreshEntity(GamblerDiceEntity.show(level, top.add(-0.4, 0, 0), new Vec3(-0.04, 0.12, 0.02), rgb, faces[0], SETTLE - 4));
        level.addFreshEntity(GamblerDiceEntity.show(level, top.add(0.4, 0, 0), new Vec3(0.04, 0.12, -0.02), rgb, faces[1], SETTLE - 4));
    }

    private static void resolve(SpellContext ctx, LivingEntity caster, LivingEntity target, SpellStats stats, int mine, int theirs) {
        ServerLevel level = ctx.level();
        if (!caster.isAlive() || !target.isAlive() || caster.level() != level || target.level() != level) return;
        int margin = Math.abs(mine - theirs);
        Vec3 tc = target.position().add(0, target.getBbHeight() * 0.5, 0);
        Vec3 cc = caster.position().add(0, caster.getBbHeight() * 0.5, 0);
        if (mine > theirs) {
            target.invulnerableTime = 0;
            float dealt = ctx.damage(target, WIN_BASE + 2F * margin, stats, null);
            if (dealt > 0F) caster.heal(dealt * 0.5F);
            if (!AnimeTricks.isBoss(target)) steal(caster, target, 1 + Math.max(0, stats.amplify));
            if (caster instanceof ServerPlayer sp && target instanceof ServerPlayer tp) {
                MagicData their = MagicApi.get(tp);
                float stolen = their.mana() * 0.15F;
                their.setMana(their.mana() - stolen);
                MagicApi.get(sp).addMana(stolen);
            }
            ctx.status(target, GbRegistries.JINXED, 160, 0, stats);
            caster.addEffect(new MobEffectInstance(GbRegistries.LUCKY, 160, 0, false, true, true), caster);
            GbFx.coins(level, tc, 14);
            level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, cc.x, cc.y, cc.z, 20, 0.4, 0.6, 0.4, 0.3);
            GbFx.sound(level, cc, SoundEvents.PLAYER_LEVELUP, 0.8F, 1.4F);
            if (caster instanceof ServerPlayer sp) MagicApi.addCounter(sp, "gambler.all_in_won", 1);
        } else if (mine == theirs) {
            target.invulnerableTime = 0;
            ctx.damage(target, WIN_BASE * 0.4F, stats, null);
            caster.hurt(caster.damageSources().magic(), LOSE_BASE * 0.5F * Progression.DAMAGE_SCALE);
            level.sendParticles(ParticleTypes.SMOKE, tc.x, tc.y, tc.z, 10, 0.3, 0.3, 0.3, 0.02);
            GbFx.sound(level, cc, SoundEvents.NOTE_BLOCK_HAT.value(), 1.0F, 1.0F);
        } else {
            float loss = (LOSE_BASE + 1.5F * margin) * Progression.DAMAGE_SCALE * Math.max(0.5F, stats.power());
            caster.hurt(caster.damageSources().indirectMagic(target, target), loss);
            target.heal(loss * 0.3F);
            caster.addEffect(new MobEffectInstance(GbRegistries.JINXED, 120, 0), target);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, cc.x, cc.y, cc.z, 14, 0.3, 0.4, 0.3, 0.02);
            GbFx.sound(level, cc, SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), 1.0F, 0.5F);
            GbFx.sound(level, cc, SoundEvents.NOTE_BLOCK_BASS.value(), 1.0F, 0.5F);
        }
        if (caster instanceof ServerPlayer sp) {
            String key = mine > theirs ? "message.isekai_mahou.gambler.all_in_win" : mine == theirs ? "message.isekai_mahou.gambler.all_in_tie"
                    : "message.isekai_mahou.gambler.all_in_lose";
            sp.displayClientMessage(Component.translatable(key, mine, theirs), true);
        }
    }

    /** Rouba até {@code n} efeitos bons do alvo (passam para quem venceu, com o tempo que sobrava). */
    private static void steal(LivingEntity winner, LivingEntity loser, int n) {
        List<MobEffectInstance> good = new ArrayList<>();
        for (MobEffectInstance i : loser.getActiveEffects()) {
            if (i.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL && !i.isInfiniteDuration()) good.add(i);
        }
        for (int k = 0; k < n && k < good.size(); k++) {
            MobEffectInstance i = good.get(k);
            MobEffectInstance copy = new MobEffectInstance(i.getEffect(), Math.min(i.getDuration(), 1200), i.getAmplifier(), i.isAmbient(),
                    i.isVisible(), i.showIcon());
            loser.removeEffect(i.getEffect());
            winner.addEffect(copy, winner);
        }
    }
}
