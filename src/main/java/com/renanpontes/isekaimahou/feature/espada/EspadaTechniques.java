package com.renanpontes.isekaimahou.feature.espada;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.anim.MahouAnims;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.espada.aging.Aging;
import com.renanpontes.isekaimahou.feature.espada.aging.RespiraRot;
import com.renanpontes.isekaimahou.feature.espada.entity.EspadaBolt;
import com.renanpontes.isekaimahou.feature.espada.entity.EspadaFx;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * w10 — as TÉCNICAS dos Espada para os jogadores (as Magias Lendárias que os chefes soltam), agora iguais ao anime e
 * aos golpes dos chefes (mesmos projéteis, mesma nuvem, mesma explosão). Cada uma é uma FORMA de origem
 * ({@code MagicPath.NONE}: fica fora de todos os sorteios/lojas e vale em qualquer elemento); quem DERROTA o Espada
 * aprende a forma e ganha o pergaminho ({@link EspadaLegends#teach}).
 * <ul>
 *   <li><b>Desgarrón</b> (Vento, C6): cinco marcas de garra GIGANTES (azul-branco) lançadas lado a lado — voam 30 blocos
 *       (Expandir +4, máx. 40), atravessam todo mundo no caminho (cada garra corta cada alvo uma vez, máx. 12) e fazem
 *       sangrar; param em parede. Dividir: +1 garra (máx. 7).</li>
 *   <li><b>Respira</b> (Sombra, C7): o miasma explode em 360° em volta de quem sopra — uma cúpula de nuvem roxo-negra que
 *       se espalha até 10 blocos (Expandir +2, máx. 18) e fica 3 s (Estender +1 s). Quem está na nuvem ENVELHECE
 *       (Envelhecido), murcha (Wither I) e fica lento; o chão parece apodrecer (só na aparência, nunca em lugar
 *       protegido).</li>
 *   <li><b>Lanza del Relámpago</b> (Sombra, C8): carrega a lança verde e a ARREMESSA (48 blocos); no impacto, a explosão
 *       GIGANTE do Ulquiorra (núcleo de 10 blocos, onda de choque até 18; Expandir aumenta) — sem quebrar blocos.</li>
 * </ul>
 */
public final class EspadaTechniques {
    public static final String DESGARRON = "espada_tech_desgarron";
    public static final String RESPIRA = "espada_tech_respira";
    public static final String LANZA = "espada_tech_lanza";

    static void register() {
        GlyphRegistry.register(new DesgarronForm());
        GlyphRegistry.register(new RespiraForm());
        GlyphRegistry.register(new LanzaForm());
        NeoForge.EVENT_BUS.addListener(EspadaTechniques::tick);
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent e) -> CLOUDS.clear());
    }

    // =====================================================================================
    //  Desgarrón
    // =====================================================================================
    public static class DesgarronForm extends FormGlyph {
        public static final float DAMAGE = 9F;
        public static final double RANGE = 30.0;

        public DesgarronForm() {
            super(DESGARRON, 40);
            element(Element.WIND);
            origin(MagicPath.NONE);
            legendary();
            minCircle(6);
        }

        @Override
        public boolean requiresEffect() { return false; }

        @Override
        public int baseCastTicks() { return 6; }

        @Override
        public int baseCooldown() { return 200; }

        @Override
        public float hintRange() { return (float) RANGE; }

        @Override
        public float copies(SpellStats stats, int circle) { return 1F + 0.25F * Math.max(0, stats.split); }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity c = ctx.caster();
            if (c == null) return;
            ServerLevel sl = ctx.level();
            SpellStats s = ctx.formStats();
            int blades = Mth.clamp(5 + Math.max(0, s.split), 5, 7);
            double range = Math.min(40.0, RANGE + 4.0 * Math.max(0, s.aoe));
            float speed = 1.6F + 0.2F * Math.max(0, s.speed);
            float spacing = 1.3F;
            Vec3 look = ctx.look();
            Vec3 flat = new Vec3(look.x, 0, look.z);
            flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, c.getYRot()) : flat.normalize();
            // garras quase na horizontal (marcas de garra "em pé"), mas dá para mirar um pouco para cima/baixo
            Vec3 dir = new Vec3(flat.x, Mth.clamp(look.y, -0.3, 0.3), flat.z).normalize();
            Vec3 side = new Vec3(-flat.z, 0, flat.x);
            Vec3 base = c.position().add(0, 1.2, 0).add(flat.scale(1.0));
            EspadaFx.clawLaunch(sl, c, base, flat, spacing, blades, EspadaFx.GRIMM_BLUE);
            for (int i = 0; i < blades; i++) {
                Vec3 from = base.add(side.scale((i - (blades - 1) / 2.0) * spacing));
                EspadaBolt.shoot(sl, c, EspadaBolt.CLAW, from, dir, speed, DAMAGE, range).spell(ctx);
            }
            MahouAnims.play(c, MahouAnims.CAST_SLASH);
            sl.playSound(null, c.getX(), c.getY(), c.getZ(), EspadaSounds.CLAW.get(), SoundSource.PLAYERS, 1.6F, 0.7F);
            sl.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.4F, 0.5F);
            BossKit.shake(sl, c.position(), 16, 0.8F, 8);
            ctx.end(base.add(dir.scale(range)), dir);
        }
    }

    // =====================================================================================
    //  Respira (360°)
    // =====================================================================================
    public static class RespiraForm extends FormGlyph {
        public static final float DAMAGE = 2.5F;
        public static final float RADIUS = 10F;

        public RespiraForm() {
            super(RESPIRA, 45);
            element(Element.SHADOW);
            origin(MagicPath.NONE);
            legendary();
            minCircle(7);
        }

        @Override
        public boolean requiresEffect() { return false; }

        @Override
        public int baseCastTicks() { return 14; }

        @Override
        public int baseCooldown() { return 300; }

        @Override
        public float hintRadius() { return RADIUS; }

        @Override
        public int hintDuration() { return PUFF_LIFE; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity c = ctx.caster();
            if (c == null) return;
            ServerLevel sl = ctx.level();
            SpellStats s = ctx.formStats();
            float range = Math.min(18F, RADIUS + 2F * Math.max(0, s.aoe));
            int n = Math.min(28, 20 + 2 * Math.max(0, s.aoe));
            int life = Mth.clamp(PUFF_LIFE + 20 * s.duration, 40, 120);
            Vec3 center = c.position().add(0, c.getBbHeight() * 0.7, 0);
            double speed = range / (double) BREATH_TICKS;
            EspadaFx.respiraSphere(sl, c, center, speed, life, n);
            EspadaFx.explosion(sl, center, 2.5F, EspadaFx.RESPIRA_LIGHT, EspadaFx.STYLE_RESPIRA);
            sl.playSound(null, center.x, center.y, center.z, EspadaSounds.RESPIRA.get(), SoundSource.PLAYERS, 2.2F, 0.7F);
            MahouAnims.play(c, MahouAnims.CAST_AREA_BIG);
            Cloud cloud = new Cloud(sl, ctx, center);
            for (Vec3 d : EspadaFx.respiraDirs(n)) cloud.puffs.add(new Puff(center.add(d.scale(0.8)), d.scale(speed), 1.2, life));
            if (CLOUDS.size() >= MAX_CLOUDS) CLOUDS.remove(0);
            CLOUDS.add(cloud);
            ctx.end(center, ctx.look());
        }
    }

    /** Respira: ticks para os bolos chegarem ao alcance, e vida de cada bolo (como o do Barragan). */
    private static final int BREATH_TICKS = 40, PUFF_LIFE = 60;
    /** Limite de nuvens vivas no servidor inteiro (desempenho). */
    private static final int MAX_CLOUDS = 16;

    private static final class Puff {
        Vec3 pos;
        Vec3 vel;
        double radius;
        int life;

        Puff(Vec3 pos, Vec3 vel, double radius, int life) {
            this.pos = pos;
            this.vel = vel;
            this.radius = radius;
            this.life = life;
        }
    }

    private static final class Cloud {
        final ServerLevel level;
        final SpellContext ctx;
        final Vec3 center;
        final List<Puff> puffs = new ArrayList<>();
        final Map<UUID, Long> lastHit = new HashMap<>();
        int age;

        Cloud(ServerLevel level, SpellContext ctx, Vec3 center) {
            this.level = level;
            this.ctx = ctx;
            this.center = center;
        }
    }

    private static final List<Cloud> CLOUDS = new ArrayList<>();

    private static void tick(ServerTickEvent.Post event) {
        if (CLOUDS.isEmpty()) return;
        Iterator<Cloud> it = CLOUDS.iterator();
        while (it.hasNext()) {
            Cloud cl = it.next();
            if (!tickCloud(cl)) it.remove();
        }
    }

    /** Anda os bolos (param em paredes, crescem) e, a cada 3 ticks, envelhece quem está dentro. false = acabou. */
    private static boolean tickCloud(Cloud cl) {
        ServerLevel sl = cl.level;
        if (sl.getServer().getLevel(sl.dimension()) != sl) return false;
        cl.age++;
        double maxR = 0;
        Iterator<Puff> it = cl.puffs.iterator();
        while (it.hasNext()) {
            Puff p = it.next();
            Vec3 next = p.pos.add(p.vel);
            BlockPos bp = BlockPos.containing(next);
            if (sl.getBlockState(bp).getCollisionShape(sl, bp).isEmpty()) p.pos = next;
            else p.vel = Vec3.ZERO;
            p.radius = Math.min(3.2, p.radius + 0.035);
            if (--p.life <= 0) {
                it.remove();
                continue;
            }
            maxR = Math.max(maxR, p.pos.distanceTo(cl.center) + p.radius);
        }
        if (cl.puffs.isEmpty()) return false;
        if (cl.age % 3 != 0) return true;
        LivingEntity caster = cl.ctx.caster();
        long now = sl.getGameTime();
        // Uma busca só (a caixa da nuvem inteira) e depois o teste bolo a bolo.
        AABB box = new AABB(cl.center, cl.center).inflate(maxR + 1.0);
        int hits = 0;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && e != caster && !cl.ctx.isAlly(e)
                && !cl.ctx.ignores(e) && !(e instanceof Player pl && (pl.isCreative() || pl.isSpectator())))) {
            if (hits >= 24) break;
            Vec3 mid = e.position().add(0, e.getBbHeight() * 0.5, 0);
            boolean inside = false;
            for (Puff p : cl.puffs) {
                double r = p.radius + 0.6;
                if (mid.distanceToSqr(p.pos) < r * r) {
                    inside = true;
                    break;
                }
            }
            if (!inside) continue;
            Long last = cl.lastHit.get(e.getUUID());
            if (last != null && now - last < 10) continue;
            cl.lastHit.put(e.getUUID(), now);
            hits++;
            // A decadência do rei: envelhece, murcha e pesa nas pernas.
            Aging.apply(e, 1, caster);
            e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), caster);
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1), caster);
            cl.ctx.damage(e, RespiraForm.DAMAGE, cl.ctx.formStats(), caster, Element.SHADOW);
            if (cl.lastHit.size() == 1 || sl.random.nextInt(3) == 0) EspadaFx.crumble(sl, mid);
        }
        // O chão PARECE apodrecer (só visual, volta sozinho) — nunca em lugar protegido (cidades, torres, masmorras).
        if (cl.age % 6 == 0 && !cl.puffs.isEmpty()) {
            Puff p = cl.puffs.get(sl.random.nextInt(cl.puffs.size()));
            BlockPos at = BlockPos.containing(p.pos.x + (sl.random.nextDouble() - 0.5) * p.radius * 2, p.pos.y,
                    p.pos.z + (sl.random.nextDouble() - 0.5) * p.radius * 2);
            for (int dy = 0; dy < 5; dy++) {
                BlockPos b = at.below(dy);
                if (sl.getBlockState(b).isAir()) continue;
                if (!Destruction.warded(sl, b) && !Destruction.protectedAt(sl, b, caster) && RespiraRot.rot(sl, b, caster)) {
                    EspadaFx.crumble(sl, Vec3.atCenterOf(b).add(0, 0.5, 0));
                }
                break;
            }
        }
        return true;
    }

    // =====================================================================================
    //  Lanza del Relámpago
    // =====================================================================================
    public static class LanzaForm extends FormGlyph {
        public static final float DAMAGE = 30F;
        public static final double RANGE = 48.0;
        public static final float SPEED = 2.6F;

        public LanzaForm() {
            super(LANZA, 60);
            element(Element.SHADOW);
            origin(MagicPath.NONE);
            legendary();
            minCircle(8);
        }

        @Override
        public boolean requiresEffect() { return false; }

        /** A lança se forma na mão (1 s) antes do arremesso. */
        @Override
        public int baseCastTicks() { return 20; }

        @Override
        public int baseCooldown() { return 400; }

        @Override
        public float hintRange() { return (float) RANGE; }

        @Override
        public float hintRadius() { return 10F; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity c = ctx.caster();
            if (c == null) return;
            ServerLevel sl = ctx.level();
            SpellStats s = ctx.formStats();
            Vec3 look = ctx.look();
            Vec3 from = c.getEyePosition().add(0, 0.35, 0).add(look.scale(0.8));
            float speed = SPEED + 0.3F * Math.max(0, s.speed);
            EspadaBolt.shoot(sl, c, EspadaBolt.LANZA, from, look, speed, DAMAGE, RANGE).spell(ctx);
            EspadaFx.lanceForm(sl, c, 8);
            EspadaFx.flash(sl, from, 2.5F, 0xCFFFE0);
            MahouAnims.play(c, MahouAnims.THROW_OVERHAND);
            sl.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 2.0F, 0.5F);
            sl.playSound(null, c.getX(), c.getY(), c.getZ(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 1.2F, 1.4F);
            sl.playSound(null, c.getX(), c.getY(), c.getZ(), EspadaSounds.LANZA_CHARGE.get(), SoundSource.PLAYERS, 1.2F, 1.2F);
        }
    }

    private EspadaTechniques() {}
}
