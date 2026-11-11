package com.renanpontes.isekaimahou.feature.fusions_c.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.fusions_c.FcFx;
import com.renanpontes.isekaimahou.feature.fusions_c.FcRegistries;
import com.renanpontes.isekaimahou.feature.fusions_c.FcUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Tsunami de Areia: uma parede de areia de {@code scale()} de largura sobe e corre pelo chão na direção da magia,
 * acompanhando o relevo. Quem ela alcança leva dano e é ARRASTADO junto; o terreno por onde passa é soterrado
 * (Destruction CRUSH — vira cascalho/pedregulho e afunda; respeita a regra). Quando a onda quebra, todos os arrastados
 * ficam SOTERRADOS (casulo de areia: presos, cegos, sufocando).
 */
public class SandTsunamiEntity extends FcSpellEntity {
    private static final int MAX_PER_OWNER = 2;
    public static final float HEIGHT = 5F;

    private Vec3 dir = new Vec3(0, 0, 1);
    private double speed = 0.55;
    private float damage = 10F;
    private int buryTicks = 60;
    private final Set<UUID> swept = new HashSet<>();
    private final List<LivingEntity> carried = new ArrayList<>();
    private Vec3 lastCrush;
    private SpellStats stats = new SpellStats();

    public SandTsunamiEntity(EntityType<? extends SandTsunamiEntity> type, Level level) {
        super(type, level);
    }

    public static void spawn(SpellContext ctx, SpellStats stats, Vec3 start, Vec3 direction, float width, float distance) {
        ServerLevel sl = ctx.level();
        UUID owner = ctx.casterId();
        if (owner != null) {
            List<SandTsunamiEntity> mine = new ArrayList<>(sl.getEntities(FcRegistries.SAND_TSUNAMI.get(), w -> w.isAlive() && owner.equals(w.ownerId())));
            while (mine.size() >= MAX_PER_OWNER) mine.remove(0).discard();
        }
        SandTsunamiEntity w = new SandTsunamiEntity(FcRegistries.SAND_TSUNAMI.get(), sl);
        Vec3 d = FcUtil.flat(direction);
        Vec3 g = FcUtil.ground(sl, start, 6);
        w.setPos(g.x, g.y, g.z);
        w.dir = d;
        w.setYRot((float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F);
        w.speed = 0.55 * stats.speedMult();
        int ticks = Math.max(20, (int) (distance / w.speed));
        w.setup(ctx, stats, Element.SAND, ticks, width);
        w.stats = stats;
        w.buryTicks = stats.duration(60);
        w.lastCrush = g;
        sl.addFreshEntity(w);
        FcFx.sound(sl, g, SoundEvents.SAND_FALL, 2F, 0.4F);
        FcFx.sound(sl, g, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.5F, 0.5F);
        BossKit.shake(sl, g, 24, 0.8F, ticks);
    }

    /** Altura da parede: sobe no começo, desaba no fim. */
    public float waveHeight(float pt) {
        float rise = Math.min(1F, (age() + pt) / 8F);
        float fall = Math.min(1F, (lifeLeft() - pt) / 8F);
        return HEIGHT * (0.5F + 0.5F * rise) * Math.max(0.15F, fall);
    }

    @Override
    protected void serverTick(ServerLevel sl) {
        // Avança acompanhando o relevo (sobe degraus e desce barrancos)
        Vec3 next = position().add(dir.scale(speed));
        Vec3 g = FcUtil.ground(sl, next.add(0, 2.5, 0), 8);
        setPos(g.x, g.y, g.z);
        setYRot((float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90F);
        float half = scale() * 0.5F;
        Vec3 side = new Vec3(-dir.z, 0, dir.x);
        // Alcança quem está na frente da parede
        for (LivingEntity le : enemiesIn(position().add(0, 1.5, 0), half + 2.5)) {
            Vec3 rel = le.position().subtract(position());
            double along = rel.dot(dir), across = Math.abs(rel.dot(side));
            if (along < -1.5 || along > 2.0 || across > half + 0.4 || rel.y > HEIGHT) continue;
            if (swept.add(le.getUUID())) {
                hit(le, damage);
                carried.add(le);
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                        le.getX(), le.getY() + 1, le.getZ(), 20, 0.4, 0.6, 0.4, 0.2);
            }
        }
        // Arrastados: vão junto com a onda (chefes escorregam bem menos)
        carried.removeIf(le -> !le.isAlive() || le.level() != sl);
        for (LivingEntity le : carried) {
            if (FcUtil.untouchable(le)) continue;
            Vec3 v = dir.scale(FcUtil.bossLike(le) ? speed * 0.25 : speed * 1.05);
            FcUtil.setMotion(le, new Vec3(v.x, Math.max(le.getDeltaMovement().y, 0.08), v.z));
            le.resetFallDistance();
        }
        // Soterra o terreno a cada ~3 blocos percorridos
        if (lastCrush == null || lastCrush.distanceToSqr(position()) > 9) {
            lastCrush = position();
            Destruction.surface(sl, position(), half * 0.85F, owner(), Destruction.Mode.CRUSH);
        }
        if (age() % 2 == 0) {
            for (int i = 0; i < 6; i++) {
                Vec3 p = position().add(side.scale((random.nextDouble() - 0.5) * 2 * half)).add(dir.scale(0.5));
                sl.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()),
                        p.x, p.y + waveHeight(0) * random.nextDouble(), p.z, 1, 0.2, 0.2, 0.2, 0);
            }
            Vec3 crest = position().add(0, waveHeight(0), 0).add(dir.scale(0.6));
            sl.sendParticles(ParticleTypes.DUST_PLUME, crest.x, crest.y, crest.z, 4, half * 0.8, 0.2, half * 0.8, 0.02);
        }
        if (age() % 8 == 0) FcFx.sound(sl, position(), SoundEvents.SAND_BREAK, 1.6F, 0.5F);
    }

    @Override
    protected void onExpire(ServerLevel sl) {
        // A onda quebra: quem foi arrastado fica soterrado
        Vec3 c = position();
        if (ctx != null) {
            for (LivingEntity le : carried) {
                if (le.isAlive() && le.level() == sl && le.distanceToSqr(c) < 14 * 14) {
                    SandCoffinEntity.wrap(ctx, stats, le, true, buryTicks, 0F);
                }
            }
        }
        sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SAND.defaultBlockState()),
                c.x, c.y + 1.5, c.z, 80, scale() * 0.4, 1.2, scale() * 0.4, 0.3);
        sl.sendParticles(ParticleTypes.DUST_PLUME, c.x, c.y + 1, c.z, 30, scale() * 0.4, 1, scale() * 0.4, 0.05);
        FcFx.sound(sl, c, SoundEvents.SAND_FALL, 2.2F, 0.4F);
        FcFx.sound(sl, c, SoundEvents.GENERIC_EXPLODE.value(), 0.7F, 0.5F);
    }

    @Override
    protected void clientTick() {
        float half = scale() * 0.5F;
        Vec3 d = Vec3.directionFromRotation(0, getYRot());
        Vec3 side = new Vec3(-d.z, 0, d.x);
        for (int i = 0; i < 3; i++) {
            Vec3 p = position().add(side.scale((random.nextDouble() - 0.5) * 2 * half));
            level().addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SANDSTONE.defaultBlockState()),
                    p.x, p.y + random.nextDouble() * waveHeight(0), p.z, 0, 0, 0);
        }
    }
}
