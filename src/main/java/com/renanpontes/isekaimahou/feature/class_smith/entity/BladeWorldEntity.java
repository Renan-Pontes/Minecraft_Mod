package com.renanpontes.isekaimahou.feature.class_smith.entity;

import com.renanpontes.isekaimahou.core.anim.MahouAnims;

import java.util.List;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.class_smith.SmithFx;
import com.renanpontes.isekaimahou.feature.class_smith.SmithFxNet;
import com.renanpontes.isekaimahou.feature.class_smith.SmithRegistries;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeFamily;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeMaterial;
import com.renanpontes.isekaimahou.feature.class_smith.forge.ForgedBlade;
import com.renanpontes.isekaimahou.feature.class_smith.item.ForgedBladeItem;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * O "Mundo das Lâminas" (forma G do Ferreiro): um mundo interior que se sobrepõe à realidade. Primeiro o cântico (o
 * anel de fogo corre pelo chão), depois o mundo se abre: céu de cinzas, engrenagens girando no alto e um campo de
 * espadas cravadas até o horizonte do círculo. A cada meio segundo as espadas se arrancam do chão e voam nos inimigos
 * lá dentro (cada uma aplica os efeitos da magia); quem tenta sair é empurrado de volta pela borda de fogo.
 * Invisível (o visual é o campo, as espadas e as partículas). Não é salvo e não mexe em blocos.
 */
public class BladeWorldEntity extends Entity {
    /** Duração do cântico (o anel de fogo abrindo). */
    public static final int OPENING = 60;
    /** Intervalo entre as rajadas de espadas. */
    public static final int VOLLEY = 10;
    /** Inimigos atacados por rajada (no máximo). */
    public static final int PER_VOLLEY = 6;
    /** Espadas cravadas de enfeite (no máximo). */
    public static final int PLANTED = 26;
    /** Linhas do cântico (chaves {@code announce.isekai_mahou.smith_blade_world.<n>}). */
    public static final int ARIA_LINES = 5;
    private static final String ARIA_KEY = "announce.isekai_mahou.smith_blade_world.";

    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(BladeWorldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(BladeWorldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_OPEN = SynchedEntityData.defineId(BladeWorldEntity.class, EntityDataSerializers.BOOLEAN);

    @Nullable private SpellContext ctx;
    private int duration = 200;

    public BladeWorldEntity(EntityType<? extends BladeWorldEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static BladeWorldEntity create(ServerLevel level, SpellContext ctx, Vec3 center, float radius, int duration, int color) {
        BladeWorldEntity e = new BladeWorldEntity(SmithRegistries.BLADE_WORLD.get(), level);
        e.ctx = ctx;
        e.duration = duration;
        e.setPos(center.x, center.y, center.z);
        e.entityData.set(DATA_RADIUS, radius);
        e.entityData.set(DATA_COLOR, color & 0xFFFFFF);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 14F);
        builder.define(DATA_COLOR, 0xE8A04A);
        builder.define(DATA_OPEN, false);
    }

    /** Este mundo é do mesmo conjurador de {@code other}? */
    public boolean ownedBy(@Nullable SpellContext other) {
        return ctx != null && other != null && ctx.casterId() != null && ctx.casterId().equals(other.casterId());
    }

    public float radius() { return entityData.get(DATA_RADIUS); }
    public int color() { return entityData.get(DATA_COLOR); }
    public boolean open() { return entityData.get(DATA_OPEN); }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        LivingEntity caster = ctx == null ? null : ctx.caster();
        if (ctx == null || caster == null || !caster.isAlive() || caster.level() != sl) {
            close(sl);
            return;
        }
        int age = tickCount;
        float r = radius();
        Vec3 c = position();
        if (age < OPENING) {
            // cântico: uma linha a cada 12 ticks; o anel de fogo corre do centro até a borda
            if (age % 12 == 0 && age / 12 < ARIA_LINES) {
                String line = ARIA_KEY + (age / 12 + 1);
                Announce.send(caster, Component.translatable(line), color(), Announce.STYLE_SYSTEM);
                SmithFx.sound(sl, c, SoundEvents.ANVIL_USE, 0.7F, 0.5F + 0.1F * (age / 12));
            }
            // o anel de fogo correndo até a borda é animado no cliente durante todo o cântico
            if (age == 0) SmithFxNet.at(sl, SmithFxNet.WORLD_ARIA, c, Vec3.ZERO, r, color(), getId(), OPENING);
            return;
        }
        if (age == OPENING) {
            openWorld(sl, caster, c, r);
            MahouAnims.play(caster, MahouAnims.CAST_ULT_RELEASE);
        }
        int active = age - OPENING;
        if (active > duration) {
            close(sl);
            return;
        }
        if (active % VOLLEY == 0) volley(sl, caster, c, r);
        if (age % 4 == 0) border(sl, c, r);
        keepInside(sl, caster, c, r);
    }

    /** O mundo se abre: anúncio, estrondo e o campo de espadas cravadas. */
    private void openWorld(ServerLevel sl, LivingEntity caster, Vec3 c, float r) {
        entityData.set(DATA_OPEN, true);
        Announce.send(caster, Component.translatable("announce.isekai_mahou.smith_blade_world"), color(), Announce.STYLE_ULTIMATE);
        SmithFx.sound(sl, c, SoundEvents.END_PORTAL_SPAWN, 1.2F, 0.8F);
        SmithFx.sound(sl, c, SoundEvents.ANVIL_LAND, 1.5F, 0.4F);
        BossKit.shake(sl, c, (int) r + 12, 0.7F, 16);
        SmithFxNet.at(sl, SmithFxNet.WORLD_OPEN, c, Vec3.ZERO, r, color(), getId());
        int room = Math.max(0, SmithBladeEntity.MAX_PER_OWNER - SmithBladeEntity.countFor(sl, ctx.casterId()) - 12);
        int n = Math.min(PLANTED, room);
        RandomSource rnd = sl.random;
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(rnd.nextDouble()) * (r - 1.0);
            Vec3 guess = c.add(Math.cos(a) * d, 2.0, Math.sin(a) * d);
            Vec3 g = SmithFx.ground(sl, guess, 6);
            if (g == guess) continue;
            Vec3 tip = new Vec3((rnd.nextDouble() - 0.5) * 0.5, -1, (rnd.nextDouble() - 0.5) * 0.5);
            sl.addFreshEntity(SmithBladeEntity.planted(sl, ctx, g.add(0, 0.55, 0), tip, randomWeapon(rnd), color(),
                    1.0F + rnd.nextFloat() * 0.4F, duration + 10));
            SmithFx.groundBurst(sl, g, 4);
        }
    }

    /** Uma rajada: espadas brotam do chão perto de cada inimigo (ou caem do céu) e voam nele. */
    private void volley(ServerLevel sl, LivingEntity caster, Vec3 c, float r) {
        List<LivingEntity> enemies = ctx.enemiesAround(c, r);
        enemies.removeIf(e -> e == caster || ctx.ignores(e) || Math.abs(e.getY() - c.y) > r);
        if (enemies.isEmpty()) return;
        RandomSource rnd = sl.random;
        int shots = 0;
        for (LivingEntity e : enemies) {
            if (shots >= PER_VOLLEY) break;
            Vec3 tc = e.position().add(0, e.getBbHeight() * 0.5, 0);
            boolean sky = rnd.nextInt(3) == 0;
            Vec3 from;
            if (sky) {
                from = tc.add((rnd.nextDouble() - 0.5) * 6, 7 + rnd.nextDouble() * 3, (rnd.nextDouble() - 0.5) * 6);
            } else {
                double a = rnd.nextDouble() * Math.PI * 2;
                Vec3 g = SmithFx.ground(sl, e.position().add(Math.cos(a) * 3.5, 1.5, Math.sin(a) * 3.5), 5);
                from = g.add(0, 0.6, 0);
                SmithFx.groundBurst(sl, g, 6);
            }
            if (SmithBladeEntity.hasRoom(sl, ctx.casterId(), 1)) {
                sl.addFreshEntity(SmithBladeEntity.carrier(sl, ctx, from, tc.subtract(from), randomWeapon(rnd), color(),
                        1.1F + rnd.nextFloat() * 0.3F, 1.7F, 1, 24, 0F, 1F));
            } else {
                ctx.resolveOnEntity(e, null);
                SmithFx.line(sl, from, tc, SmithFx.dust(color(), 0.8F), 0.5);
            }
            shots++;
        }
        SmithFx.sound(sl, c, SoundEvents.TRIDENT_THROW.value(), 1.0F, 0.8F + rnd.nextFloat() * 0.3F);
        SmithFx.sound(sl, c, SoundEvents.CHAIN_BREAK, 0.6F, 0.7F);
    }

    /** A borda de fogo (anel) e brasas subindo. */
    private void border(ServerLevel sl, Vec3 c, float r) {
        SmithFxNet.at(sl, SmithFxNet.WORLD_BORDER, c, Vec3.ZERO, r, color(), getId());
    }

    /** Quem tenta sair pela borda é empurrado de volta (aliados e o conjurador saem à vontade). */
    private void keepInside(ServerLevel sl, LivingEntity caster, Vec3 c, float r) {
        for (LivingEntity e : ctx.enemiesAround(c, r + 1.5)) {
            if (e == caster) continue;
            Vec3 flat = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
            double d = flat.length();
            if (d < r - 0.8 || d < 1.0E-3) continue;
            Vec3 in = flat.scale(-1 / d);
            e.push(in.x * 0.35, 0.05, in.z * 0.35);
            e.hurtMarked = true;
            if (tickCount % 6 == 0) sl.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY() + 0.5, e.getZ(), 4, 0.2, 0.3, 0.2, 0.02);
        }
    }

    private void close(ServerLevel sl) {
        Vec3 c = position();
        if (ctx != null) ctx.end(c, new Vec3(0, 1, 0));
        float r = radius();
        SmithFxNet.at(sl, SmithFxNet.WORLD_CLOSE, c, Vec3.ZERO, r, color(), getId());
        SmithFx.sound(sl, c, SoundEvents.FIRE_EXTINGUISH, 1.2F, 0.6F);
        SmithFx.sound(sl, c, SoundEvents.BEACON_DEACTIVATE, 1.0F, 0.6F);
        discard();
    }

    /** Uma arma qualquer do "arsenal infinito" (espadas vanilla e Lâminas Forjadas de famílias e metais variados). */
    public static ItemStack randomWeapon(RandomSource rnd) {
        int k = rnd.nextInt(10);
        return switch (k) {
            case 0 -> new ItemStack(Items.IRON_SWORD);
            case 1 -> new ItemStack(Items.DIAMOND_SWORD);
            case 2 -> new ItemStack(Items.NETHERITE_SWORD);
            case 3 -> new ItemStack(Items.GOLDEN_SWORD);
            case 4 -> new ItemStack(Items.IRON_AXE);
            default -> {
                BladeFamily f = rnd.nextInt(4) == 0 ? BladeFamily.SPEAR : rnd.nextInt(5) == 0 ? BladeFamily.AXE : BladeFamily.BLADE;
                BladeMaterial m = BladeMaterial.VALUES[rnd.nextInt(BladeMaterial.VALUES.length)];
                ForgedBlade b = ForgedBlade.DEFAULT.withFamily(f).withMaterial(m)
                        .withPart(ForgedBlade.Part.LENGTH, 1 + rnd.nextInt(3))
                        .withPart(ForgedBlade.Part.CURVE, rnd.nextInt(3))
                        .withPart(ForgedBlade.Part.TIP, rnd.nextInt(3));
                yield ForgedBladeItem.create(b);
            }
        };
    }

    // ---------------------------------------------------------------- cliente
    private void clientFx() {
        Level l = level();
        float r = radius();
        Vec3 c = position();
        if (!open()) return;
        // cinzas caindo no céu do mundo interior
        for (int i = 0; i < 6; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(random.nextDouble()) * r;
            l.addParticle(random.nextBoolean() ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH, c.x + Math.cos(a) * d,
                    c.y + 2 + random.nextDouble() * 8, c.z + Math.sin(a) * d, 0, -0.02, 0);
        }
        if (random.nextInt(2) == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(random.nextDouble()) * r;
            l.addParticle(ParticleTypes.SMALL_FLAME, c.x + Math.cos(a) * d, c.y + 0.2, c.z + Math.sin(a) * d, 0, 0.03, 0);
        }
        // engrenagens gigantes girando no alto (anéis dentados de bronze)
        if (tickCount % 2 == 0) {
            int rgb = 0xC08A3A;
            DustParticleOptions bronze = new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F,
                    (rgb & 255) / 255F), 2.2F);
            for (int g = 0; g < 2; g++) {
                double gx = c.x + (g == 0 ? -r * 0.35 : r * 0.4);
                double gz = c.z + (g == 0 ? r * 0.2 : -r * 0.25);
                double gy = c.y + 14 + g * 3;
                double gr = r * (g == 0 ? 0.35 : 0.25);
                float spin = (tickCount * (g == 0 ? 0.02F : -0.03F));
                int teeth = 16;
                for (int t = 0; t < teeth * 2; t++) {
                    double a = spin + Mth.TWO_PI * t / (teeth * 2);
                    double rr = gr + ((t / 2) % 2 == 0 ? 0.6 : 0.0);
                    l.addParticle(bronze, gx + Math.cos(a) * rr, gy, gz + Math.sin(a) * rr, 0, 0, 0);
                }
            }
        }
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d) {
        return d < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
