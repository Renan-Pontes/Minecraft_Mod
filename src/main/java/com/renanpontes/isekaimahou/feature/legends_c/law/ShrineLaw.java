package com.renanpontes.isekaimahou.feature.legends_c.law;

import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.legends_c.LcFx;
import com.renanpontes.isekaimahou.feature.legends_c.LcUtil;
import com.renanpontes.isekaimahou.feature.legends_c.entity.MalevolentShrineEntity;
import com.renanpontes.isekaimahou.feature.legends_c.glyph.CleaveEffect;
import com.renanpontes.isekaimahou.feature.legends_c.glyph.DismantleEffect;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Lei: Santuário Maligno.
 * <ul>
 *   <li>Ao abrir: o SANTUÁRIO aparece atrás do dono (modelo GeckoLib), tremor, rugido; o céu fica vermelho para quem
 *       está dentro (cliente).</li>
 *   <li>A cada 10 ticks: Desmantelar em pontos aleatórios do chão — o terreno é RETALHADO (com a destruição ligada);
 *       e cortes contra a BARREIRA de qualquer domínio fechado menor de outro dono que esteja dentro (o Santuário é
 *       aberto e maior: só ataca a barreira por fora, regra §11.1).</li>
 *   <li>A cada 10 ticks, em cada inimigo que o domínio alcança (o núcleo já tira os protegidos por barreiras menores):
 *       Clivar (6 + 5% da vida máx., 2% em chefes, + ½ armadura, Sangrando 3) e, alternando, um Desmantelar que passa
 *       por ele.</li>
 * </ul>
 * O domínio fica ABERTO (sem barreira) e com raio ×1,5 só por ter esta lei: quem faz isso é o módulo domain
 * ({@code DomainGlyphs.SHRINE} → {@code F_OPEN}; {@code DomainExpansionForm}: raio ×1,5). A lenda não precisa de
 * {@code law_open_barrier}.
 */
public class ShrineLaw implements Domains.LawHandler {
    private static final SpellStats STATS = new SpellStats();

    @Override
    public void onOpen(Domains.Info d) {
        ServerLevel l = d.level();
        LivingEntity owner = d.owner();
        MalevolentShrineEntity s = MalevolentShrineEntity.raise(d);
        Vec3 at = s.position();
        Announce.send(owner, Component.translatable("announce.isekai_mahou.legends_c.shrine"), LcFx.RED, Announce.STYLE_ULTIMATE);
        LcFx.sound(l, at, SoundEvents.WARDEN_EMERGE, 4F, 0.6F);
        LcFx.sound(l, at, SoundEvents.WITHER_SPAWN, 2F, 0.4F);
        LcFx.sound(l, at, SoundEvents.BELL_RESONATE, 3F, 0.5F);
        LcFx.far(l, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at.x, at.y + 1, at.z, 40, 4, 1, 4, 0.03);
        LcFx.wave(l, at.add(0, 0.5, 0), new Vec3(0, 1, 0), d.radius(), LcFx.RED, 30);
        BossKit.shake(l, at, d.radius() + 32, 2.0F, 40);
    }

    @Override
    public void onTick(Domains.Info d, int age) {
        ServerLevel l = d.level();
        MalevolentShrineEntity s = MalevolentShrineEntity.of(d.id());
        if (s != null) s.setRadius(d.radius(), d.center());
        else if (age % 40 == 0) MalevolentShrineEntity.raise(d, true);
        if (age % 60 == 0) LcFx.sound(l, d.center(), SoundEvents.WARDEN_ROAR, 2.5F, 0.4F);
        if (age < 20 || age % 10 != 0) return;
        shredTerrain(d, l);
        attackBarriers(d, l);
    }

    /** Cortes de Desmantelar em pontos aleatórios do chão dentro do domínio. */
    private void shredTerrain(Domains.Info d, ServerLevel l) {
        LivingEntity owner = d.owner();
        RandomSource rnd = l.random;
        boolean destroy = Destruction.allowed(l, owner);
        int n = Mth.clamp(3 + (int) (d.radius() / 12F), 3, 8);
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * d.radius() * 0.92;
            Vec3 top = d.center().add(Math.cos(a) * rr, 16, Math.sin(a) * rr);
            if (!LcUtil.hasGround(l, top, 40)) continue;
            Vec3 g = LcUtil.ground(l, top, 40);
            // Dentro da barreira fechada de outro dono: o Santuário não alcança
            Domains.Info inner = Domains.innermost(l, g);
            if (inner != null && inner.closed() && !LcUtil.friendly(inner, owner)) continue;
            double yaw = rnd.nextDouble() * Math.PI * 2;
            Vec3 dir = new Vec3(Math.cos(yaw), -0.12, Math.sin(yaw)).normalize();
            double len = 5 + rnd.nextDouble() * 7;
            Vec3 from = g.add(0, 1.0, 0).subtract(dir.scale(len * 0.5));
            Vec3 to = from.add(dir.scale(len));
            Domains.Info innerFrom = Domains.innermost(l, from);
            if (innerFrom != null && innerFrom.closed() && !LcUtil.friendly(innerFrom, owner)) continue;
            // O corte que cruzaria a barreira de um domínio fechado menor para nela (não retalha o lado de dentro)
            LcUtil.Wall wall = LcUtil.firstWall(l, from, to, owner);
            if (wall != null) {
                to = wall.point();
                if (to.distanceToSqr(from) < 1.0) continue;
            }
            if (destroy) Destruction.line(l, from, to, 0.5F, 2.6F, owner, Destruction.Mode.ERASE);
            LcFx.slash(l, from, to, 2.6F, LcFx.SLASH_CORE, 12);
            if (i == 0) LcFx.sound(l, g, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 1.5F);
        }
    }

    /** Domínios fechados menores de outro dono aqui dentro: os cortes batem na barreira, por fora. */
    private void attackBarriers(Domains.Info d, ServerLevel l) {
        LivingEntity owner = d.owner();
        float power = d.ctx() != null ? d.ctx().powerMult() : 1F;
        RandomSource rnd = l.random;
        for (Domains.Info o : Domains.active(l)) {
            if (o.id() == d.id() || !o.closed() || LcUtil.friendly(o, owner)) continue;
            if (o.radius() >= d.radius() || !d.contains(o.center())) continue;
            for (int k = 0; k < 2; k++) {
                double z = rnd.nextDouble() * 1.4 - 0.4, a = rnd.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - z * z);
                Vec3 u = new Vec3(Math.cos(a) * s, z, Math.sin(a) * s);
                Vec3 wall = o.center().add(u.scale(o.radius()));
                Vec3 outside = o.center().add(u.scale(o.radius() + 3));
                Domains.hitBarrier(l, wall, outside, owner, 5F * power);
                Vec3 tan = u.cross(new Vec3(0, 1, 0));
                tan = tan.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : tan.normalize();
                LcFx.slash(l, wall.subtract(tan.scale(2.5)), wall.add(tan.scale(2.5)), 1.5F, LcFx.SLASH_CORE, 12);
            }
        }
    }

    @Override
    public void onEnemyTick(Domains.Info d, LivingEntity e, int age) {
        if (age < 20 || (age + e.getId()) % 10 != 0) return;
        ServerLevel l = d.level();
        LivingEntity owner = d.owner();
        if (!LcUtil.budget(l, owner, "shrine_enemy", 24)) return;
        SpellContext ctx = d.ctx();
        if (ctx == null) {
            // Domínio sem magia (mob): corte direto
            float dmg = 6F + (LcUtil.isBoss(e) ? 0.02F : 0.05F) * e.getMaxHealth();
            e.hurt(l.damageSources().indirectMagic(owner, owner), dmg);
            Statuses.apply(e, Statuses.Kind.BLEEDING, 3, 120, owner, 1F);
            Vec3 c = LcUtil.center(e);
            LcFx.grid(l, c, e.getBbWidth() * 0.5F + 0.35F, e.getBbHeight() * 0.5F + 0.25F, l.random.nextFloat() * 6.28F, 7, LcFx.SLASH_CORE);
            return;
        }
        CleaveEffect.cleave(ctx, STATS, e, 6F, 0.05F, 0.02F, 0.5F, 3);
        if ((age / 10) % 2 == 0) {
            double yaw = l.random.nextDouble() * Math.PI * 2;
            Vec3 dir = new Vec3(Math.cos(yaw), (l.random.nextDouble() - 0.5) * 0.5, Math.sin(yaw)).normalize();
            Vec3 from = LcUtil.center(e).subtract(dir.scale(5));
            DismantleEffect.cut(ctx, STATS, from, dir, 10, 0.5F, 3F, 8F, 2, true);
        }
    }

    @Override
    public void onClose(Domains.Info d) {
        MalevolentShrineEntity.close(d.id());
    }
}
