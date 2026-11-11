package com.renanpontes.isekaimahou.feature.domain;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.feature.domain.entity.DomainDomeEntity;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Lei "Ruína" (ARCANO, Círculo 6): o domínio destrói o terreno dentro dele aos poucos — rachaduras que afundam o
 * chão (CRUSH), erupções que arrancam blocos e lançam os inimigos para cima (ERASE + escombros do núcleo), fissuras
 * que se abrem sob os pés dos inimigos — cada vez mais rápido até o fim; quando o domínio acaba de verdade, uma
 * CRATERA engole o centro. Tudo pela API {@link Destruction} (respeita a regra {@code mahouSpellDestruction},
 * cidades e a dimensão das Provações). Sem destruição permitida, só o visual e o tremor.
 */
public final class RuinLaw implements Domains.LawHandler {
    public static final String ID = "law_ruin";

    @Override
    public void onOpen(Domains.Info d) {
        ServerLevel l = d.level();
        Vec3 c = d.center();
        l.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_EMERGE, SoundSource.PLAYERS, 3.0F, 0.6F);
        l.playSound(null, c.x, c.y, c.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 3.0F, 0.4F);
        DomainFx.shake(l, c, d.radius() * 1.5 + 16, 1.0F, 30);
        if (!Destruction.allowed(l, d.owner()) && d.owner() instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.domain_ruin_disabled").withStyle(ChatFormatting.GRAY), true);
        }
    }

    @Override
    public void onTick(Domains.Info d, int age) {
        ServerLevel l = d.level();
        RandomSource rnd = l.random;
        float r = d.radius();
        float prog = d.durationTicks() > 0 ? Mth.clamp(age / (float) d.durationTicks(), 0F, 1F) : 0.5F;
        boolean allowed = Destruction.allowed(l, d.owner());

        // Rachaduras correndo pelo chão (visual, sempre)
        if (age % 4 == 0) {
            int n = 1 + (int) (prog * 3);
            for (int i = 0; i < n; i++) crack(l, d, rnd);
        }
        // Afundamentos: o chão esmaga e cede, cada vez mais rápido e maior
        int interval = Math.max(4, 14 - (int) (prog * 10));
        if (age % interval == 0) {
            Vec3 p = randomGround(d, rnd);
            if (p != null) {
                float rad = 1.6F + prog * 2.4F + Math.min(2F, r / 30F);
                if (allowed) Destruction.sphere(l, p.add(0, -0.6, 0), rad, d.owner(), Destruction.Mode.CRUSH);
                dust(l, p, rad);
                l.playSound(null, p.x, p.y, p.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 1.6F, 0.5F);
            }
        }
        // Erupções: o chão explode para cima, arranca blocos e arremessa quem está perto
        int eruptEvery = Math.max(16, 36 - (int) (prog * 20));
        if (age % eruptEvery == eruptEvery / 2) {
            Vec3 p = randomGround(d, rnd);
            if (p != null) erupt(l, d, p, prog, allowed);
        }
        if (age % 20 == 0) DomainFx.shake(l, d.center(), r + 12, 0.25F + prog * 0.6F, 12);
    }

    @Override
    public void onEnemyTick(Domains.Info d, LivingEntity e, int age) {
        // Fissura sob os pés de cada inimigo, espalhada no tempo (não todos no mesmo tick)
        if ((age + e.getId()) % 40 != 0 || !e.onGround()) return;
        ServerLevel l = d.level();
        Vec3 feet = e.position();
        float prog = d.durationTicks() > 0 ? Mth.clamp(age / (float) d.durationTicks(), 0F, 1F) : 0.5F;
        if (Destruction.allowed(l, d.owner())) Destruction.sphere(l, feet.add(0, -1.2, 0), 1.6F + prog, d.owner(), Destruction.Mode.CRUSH);
        e.push(0, 0.55 + 0.25 * prog, 0);
        e.hurtMarked = true;
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2, false, true, true));
        hurt(d, e, 3F + 3F * prog);
        BlockState ground = l.getBlockState(BlockPos.containing(feet).below());
        if (!ground.isAir()) {
            l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), feet.x, feet.y + 0.2, feet.z, 30, 0.6, 0.3, 0.6, 0.3);
        }
        l.playSound(null, feet.x, feet.y, feet.z, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.PLAYERS, 1.0F, 0.5F);
    }

    @Override
    public void onClose(Domains.Info d) {
        // Só quando o domínio acaba de verdade (tempo, quebra, dono saiu/morreu): chunk descarregando, domínio
        // substituído por outro do mesmo dono ou /mahou domain clear não abrem cratera
        DomainDomeEntity dome = DomainDomeEntity.byId(d.id());
        if (dome == null || !dome.endedForReal()) return;
        ServerLevel l = d.level();
        Vec3 c = d.center();
        if (!l.isLoaded(BlockPos.containing(c))) return;
        float craterR = Mth.clamp(d.radius() * 0.45F, 6F, 40F);
        int n = Destruction.crater(l, c, craterR, d.owner());
        // O dono (e os aliados) estão no centro do próprio domínio: descem devagar para o fundo em vez de morrer na queda
        if (n > 0) {
            double reach = craterR + 3;
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(reach),
                    e -> e.isAlive() && (e == d.owner() || !d.isEnemy(e)) && e.position().distanceToSqr(c) <= reach * reach)) {
                e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false, true));
            }
        }
        DomainFx.far(l, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 1, c.z, 3 + (int) (craterR / 6), craterR * 0.4, 1.0, craterR * 0.4, 0);
        DomainFx.far(l, ElementFx.dust(d.element(), 3.0F), c.x, c.y + 2, c.z, 120, craterR * 0.5, 2.0, craterR * 0.5, 0);
        BlockParticleOption rubble = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        DomainFx.far(l, rubble, c.x, c.y + 1, c.z, 160, craterR * 0.5, 1.5, craterR * 0.5, 0.6);
        l.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 6.0F, 0.4F);
        l.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 4.0F, 0.5F);
        if (n == 0) DomainFx.shake(l, c, craterR * 5 + 24, 2.2F, 30);
    }

    // =====================================================================================
    //  Ajudantes
    // =====================================================================================
    private static void erupt(ServerLevel l, Domains.Info d, Vec3 p, float prog, boolean allowed) {
        float rad = 2.5F + prog * 2.5F;
        if (allowed) Destruction.sphere(l, p, rad, d.owner(), Destruction.Mode.ERASE);
        DomainFx.far(l, ParticleTypes.EXPLOSION, p.x, p.y + 1, p.z, 4, rad * 0.4, 0.5, rad * 0.4, 0);
        BlockState st = l.getBlockState(BlockPos.containing(p).below());
        if (!st.isAir()) {
            DomainFx.far(l, new BlockParticleOption(ParticleTypes.BLOCK, st), p.x, p.y + 0.5, p.z, 60, rad * 0.4, 1.0, rad * 0.4, 0.8);
            DomainFx.far(l, new BlockParticleOption(ParticleTypes.DUST_PILLAR, st), p.x, p.y + 0.1, p.z, 30, rad * 0.5, 0, rad * 0.5, 0);
        }
        DomainFx.far(l, ElementFx.dust(d.element(), 2.2F), p.x, p.y + 2, p.z, 30, rad * 0.5, 2.0, rad * 0.5, 0);
        l.playSound(null, p.x, p.y, p.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.5F, 0.6F);
        l.playSound(null, p.x, p.y, p.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, 2.0F, 0.4F);
        DomainFx.shake(l, p, 24, 0.9F, 12);
        // Quem está perto voa junto com os escudos de pedra
        double reach = rad + 2.5;
        for (LivingEntity e : d.reachableEnemies()) {
            double dist = e.position().distanceTo(p);
            if (dist > reach) continue;
            double k = 1.0 - dist / reach;
            Vec3 away = e.position().subtract(p).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? Vec3.ZERO : away.normalize();
            e.push(away.x * 0.6 * k, 0.8 + 0.6 * k, away.z * 0.6 * k);
            e.hurtMarked = true;
            hurt(d, e, (6F + 6F * prog) * (float) (0.5 + 0.5 * k));
        }
    }

    private static void hurt(Domains.Info d, LivingEntity e, float amount) {
        SpellContext c = d.ctx();
        e.invulnerableTime = 0;
        if (c != null) c.damage(e, amount, c.formStats(), null);
        else e.hurt(d.level().damageSources().indirectMagic(d.owner(), d.owner()), amount);
    }

    /** Risco de rachadura no chão: poeira e lascas ao longo de uma linha curta. */
    private static void crack(ServerLevel l, Domains.Info d, RandomSource rnd) {
        Vec3 p = randomGround(d, rnd);
        if (p == null) return;
        BlockState st = l.getBlockState(BlockPos.containing(p).below());
        if (st.isAir()) return;
        double a = rnd.nextDouble() * Math.PI * 2;
        double len = 2.5 + rnd.nextDouble() * 4;
        BlockParticleOption chip = new BlockParticleOption(ParticleTypes.BLOCK, st);
        for (int i = 0; i <= 8; i++) {
            double t = i / 8.0 * len;
            double wob = Math.sin(i * 1.7 + a) * 0.35;
            double x = p.x + Math.cos(a) * t - Math.sin(a) * wob, z = p.z + Math.sin(a) * t + Math.cos(a) * wob;
            l.sendParticles(chip, x, p.y + 0.05, z, 2, 0.05, 0.02, 0.05, 0.05);
            if (i % 3 == 0) l.sendParticles(ParticleTypes.SMOKE, x, p.y + 0.1, z, 1, 0.05, 0.05, 0.05, 0.01);
        }
        if (rnd.nextInt(3) == 0) l.playSound(null, p.x, p.y, p.z, SoundEvents.STONE_BREAK, SoundSource.PLAYERS, 0.8F, 0.5F);
    }

    private static void dust(ServerLevel l, Vec3 p, float rad) {
        BlockState st = l.getBlockState(BlockPos.containing(p).below());
        if (st.isAir()) return;
        DomainFx.far(l, new BlockParticleOption(ParticleTypes.DUST_PILLAR, st), p.x, p.y + 0.1, p.z, 18, rad * 0.4, 0, rad * 0.4, 0);
        l.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y + 0.4, p.z, 3, rad * 0.3, 0.1, rad * 0.3, 0.01);
    }

    /**
     * Ponto de chão aleatório dentro do domínio (até 85% do raio), a 8+ blocos do dono (ele não cai no próprio buraco).
     * Nunca lê chunk descarregado. Null se não achou.
     */
    @Nullable
    private static Vec3 randomGround(Domains.Info d, RandomSource rnd) {
        ServerLevel l = d.level();
        Vec3 c = d.center();
        float r = d.radius() * 0.85F;
        Vec3 owner = d.owner().position();
        for (int attempt = 0; attempt < 4; attempt++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double dist = Math.sqrt(rnd.nextDouble()) * r;
            double x = c.x + Math.cos(a) * dist, z = c.z + Math.sin(a) * dist;
            double dx = x - owner.x, dz = z - owner.z;
            // Longe do dono: as erupções apagam até ~5 blocos em volta, e ele não pode cair no próprio buraco
            if (dx * dx + dz * dz < 64) continue;
            BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(c.y + Math.min(12, r * 0.5)), Mth.floor(z));
            if (!l.isLoaded(m)) continue;
            int minY = Mth.floor(c.y - 16);
            boolean open = false;
            for (; m.getY() >= minY; m.move(0, -1, 0)) {
                BlockState st = l.getBlockState(m);
                if (st.isAir() || st.canBeReplaced()) {
                    open = true;
                    continue;
                }
                if (open && st.isCollisionShapeFullBlock(l, m)) return new Vec3(x, m.getY() + 1, z);
            }
        }
        return null;
    }
}
