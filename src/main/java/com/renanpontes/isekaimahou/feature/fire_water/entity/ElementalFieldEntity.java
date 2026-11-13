package com.renanpontes.isekaimahou.feature.fire_water.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.fire_water.FwRegistries;
import com.renanpontes.isekaimahou.feature.fire_water.FwUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Campo elemental criado pelos efeitos de Fogo/Água: Fumaça, Névoa, Chuva Local, Dilúvio e Muralha de Fogo.
 * Não tem modelo: o servidor aplica os efeitos a cada meio segundo e o cliente desenha só partículas
 * (o que economiza rede: nada de enviar partículas do servidor a cada tick).
 */
public class ElementalFieldEntity extends Entity {
    public enum Kind {
        SMOKE, MIST, RAIN, DELUGE, FIRE_WALL;

        static Kind byId(int i) {
            Kind[] v = values();
            return i >= 0 && i < v.length ? v[i] : SMOKE;
        }
    }

    /** Máximo de campos do mesmo tipo por conjurador (o Dilúvio é único). */
    private static final int MAX_SAME_KIND = 2;

    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(ElementalFieldEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(ElementalFieldEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFE = SynchedEntityData.defineId(ElementalFieldEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> WALL_YAW = SynchedEntityData.defineId(ElementalFieldEntity.class, EntityDataSerializers.FLOAT);

    private UUID ownerId;
    private CompoundTag spellTag = new CompoundTag();
    private SpellContext ctx;
    private boolean ctxFailed;
    /** Dano por pulso (já com o poder dos aumentos; o contexto aplica o resto). */
    private float damage;
    private int amp;
    private int life;
    /** Contagem regressiva do lado cliente (para o fade das partículas). */
    private int clientLife = 100;

    public ElementalFieldEntity(EntityType<? extends ElementalFieldEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // =====================================================================================
    //  Criação
    // =====================================================================================

    /**
     * Cria (ou reforça) um campo. Se o mesmo conjurador já tem um campo igual bem perto, ele só é renovado —
     * assim zonas e repetições não empilham dezenas de nuvens no mesmo lugar.
     */
    public static ElementalFieldEntity spawn(SpellContext ctx, Kind kind, Vec3 pos, float radius, int life,
                                             float damage, int amp, float yaw) {
        ServerLevel level = ctx.level();
        UUID owner = ctx.casterId();
        if (owner != null) {
            List<ElementalFieldEntity> same = new ArrayList<>();
            for (ElementalFieldEntity f : level.getEntities(FwRegistries.FIELD.get(),
                    f -> f.isAlive() && owner.equals(f.ownerId) && f.kind() == kind)) {
                double merge = Math.max(1.0, radius * 0.5);
                if (f.position().distanceToSqr(pos) < merge * merge) {
                    f.life = Math.max(f.life, life);
                    f.entityData.set(LIFE, f.life);
                    f.entityData.set(RADIUS, Math.max(f.radius(), radius));
                    f.damage = Math.max(f.damage, damage);
                    f.amp = Math.max(f.amp, amp);
                    return f;
                }
                same.add(f);
            }
            int limit = kind == Kind.DELUGE ? 1 : MAX_SAME_KIND;
            while (same.size() >= limit) {
                ElementalFieldEntity oldest = same.get(0);
                for (ElementalFieldEntity f : same) if (f.life < oldest.life) oldest = f;
                oldest.discard();
                same.remove(oldest);
            }
        }
        ElementalFieldEntity f = new ElementalFieldEntity(FwRegistries.FIELD.get(), level);
        f.setPos(pos.x, pos.y, pos.z);
        f.setYRot(yaw);
        f.ownerId = owner;
        f.ctx = ctx;
        f.spellTag = ctx.save();
        f.damage = damage;
        f.amp = amp;
        f.life = life;
        f.entityData.set(KIND, (byte) kind.ordinal());
        f.entityData.set(RADIUS, radius);
        f.entityData.set(LIFE, life);
        f.entityData.set(WALL_YAW, yaw);
        level.addFreshEntity(f);
        f.startFx(level);
        return f;
    }

    public Kind kind() { return Kind.byId(entityData.get(KIND)); }
    public float radius() { return entityData.get(RADIUS); }
    public float wallYaw() { return entityData.get(WALL_YAW); }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, (byte) 0);
        builder.define(RADIUS, 3.0F);
        builder.define(LIFE, 100);
        builder.define(WALL_YAW, 0.0F);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (LIFE.equals(key)) clientLife = entityData.get(LIFE);
    }

    private SpellContext context() {
        if (!(level() instanceof ServerLevel sl)) return null;
        // O conjurador morreu (renasce como outra entidade), saiu ou voltou ao mundo: recarrega o contexto para
        // que dano, XP e abates sejam creditados à entidade atual, e não a uma já removida.
        if (ctx != null && ownerId != null && tickCount % 20 == 0) {
            LivingEntity cur = ctx.caster();
            boolean stale = cur != null && cur.isRemoved();
            boolean back = cur == null && sl.getEntity(ownerId) instanceof LivingEntity;
            if (stale || back) {
                SpellContext fresh = SpellContext.load(sl, spellTag);
                if (fresh != null) ctx = fresh;
            }
        }
        if (ctx == null && !ctxFailed) {
            ctx = SpellContext.load(sl, spellTag);
            ctxFailed = ctx == null;
        }
        return ctx;
    }

    private boolean ally(Entity e) {
        SpellContext c = context();
        if (c != null) return c.isAlly(e);
        return ownerId != null && ownerId.equals(e.getUUID());
    }

    private void hit(LivingEntity target, float amount) {
        target.invulnerableTime = 0;
        SpellContext c = context();
        if (c != null) c.damage(target, amount, FwUtil.PLAIN, this);
        else target.hurt(damageSources().magic(), amount);
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientParticles();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        life--;
        if (life <= 0) {
            endFx(sl);
            discard();
            return;
        }
        if (life % 10 == 0) entityData.set(LIFE, life);
        Kind k = kind();
        if (k == Kind.DELUGE) swirl();
        if (tickCount % 10 != 0) return;
        switch (k) {
            case SMOKE -> smokePulse();
            case MIST -> mistPulse();
            case RAIN -> rainPulse(sl, false);
            case DELUGE -> rainPulse(sl, true);
            case FIRE_WALL -> wallPulse(sl);
        }
    }

    /** Entidades vivas dentro do cilindro do campo. */
    private List<LivingEntity> inside(double r, double up, double down) {
        Vec3 c = position();
        AABB box = new AABB(c.x - r, c.y - down, c.z - r, c.x + r, c.y + up, c.z + r);
        return level().getEntitiesOfClass(LivingEntity.class, box, e -> {
            if (!e.isAlive() || e.isSpectator()) return false;
            double dx = e.getX() - c.x, dz = e.getZ() - c.z;
            return dx * dx + dz * dz <= r * r;
        });
    }

    private boolean isInside(Entity e, double r) {
        double dx = e.getX() - getX(), dz = e.getZ() - getZ();
        return dx * dx + dz * dz <= r * r && e.getY() > getY() - 1.5 && e.getY() < getY() + 3.5;
    }

    private void smokePulse() {
        double r = radius();
        for (LivingEntity e : inside(r, 3.0, 1.0)) {
            if (ally(e)) continue;
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40 + 10 * amp, 0, false, false, true));
            // Dentro da fumaça o mob perde de vista quem estava perseguindo
            if (e instanceof Mob m && m.getTarget() != null && random.nextFloat() < 0.35F) m.setTarget(null);
        }
    }

    private void mistPulse() {
        double r = radius();
        for (LivingEntity e : inside(r, 3.0, 1.0)) {
            if (ally(e)) {
                e.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, true, false, true));
            } else {
                e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40 + 10 * amp, 0, false, false, true));
            }
        }
        // Inimigos de fora perdem o rastro de aliados escondidos na névoa
        for (Mob m : level().getEntitiesOfClass(Mob.class, getBoundingBox().inflate(r + 16, 8, r + 16),
                m -> m.getTarget() != null && !ally(m))) {
            LivingEntity t = m.getTarget();
            if (t != null && ally(t) && isInside(t, r) && random.nextFloat() < 0.5F) m.setTarget(null);
        }
    }

    private void rainPulse(ServerLevel sl, boolean deluge) {
        double r = radius();
        boolean heavyPulse = tickCount % 20 == 0;
        for (LivingEntity e : inside(r, 6.0, 1.5)) {
            if (e.isOnFire()) e.clearFire();
            e.removeEffect(FwRegistries.LINGERING_EMBER);
            if (ally(e)) {
                if (deluge && heavyPulse && e.getHealth() < e.getMaxHealth()) {
                    // Lendário: cura de verdade (~2/s com poder normal), não só um respingo
                    e.heal(Math.max(1.5F, damage * 1.25F));
                    sl.sendParticles(ParticleTypes.HEART, e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 1, 0.2, 0.1, 0.2, 0);
                }
                continue;
            }
            e.addEffect(new MobEffectInstance(CoreRegistries.SOAKED, 60 + 20 * amp, 0));
            if (deluge) {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0));
                e.addEffect(new MobEffectInstance(FwRegistries.WATER_CHOKE, 50, Math.min(amp, 2)));
                if (heavyPulse) hit(e, damage);
            }
        }
        weatherBlocks(sl, r, deluge);
        if (tickCount % 30 == 0) {
            FwUtil.sound(sl, position(), SoundEvents.WEATHER_RAIN, deluge ? 1.0F : 0.5F, deluge ? 0.8F : 1.0F);
        }
    }

    /** A chuva apaga fogo, molha a terra arada, enche caldeirões e ajuda as plantações. */
    private void weatherBlocks(ServerLevel sl, double r, boolean deluge) {
        SpellContext c = context();
        if (c == null) return;
        RandomSource rnd = sl.random;
        int samples = (int) (r * r * 0.5) + 4;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int i = 0; i < samples; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * r;
            int x = (int) Math.floor(getX() + Math.cos(a) * d), z = (int) Math.floor(getZ() + Math.sin(a) * d);
            for (int y = (int) Math.floor(getY()) + 4; y >= (int) Math.floor(getY()) - 3; y--) {
                p.set(x, y, z);
                BlockState s = sl.getBlockState(p);
                if (s.isAir()) continue;
                // Escritas usam uma posição imutável: o mundo pode guardar a referência (POI, ticks agendados)
                BlockPos at = p.immutable();
                if (s.getBlock() instanceof BaseFireBlock) {
                    if (FwUtil.canPlace(c, at)) {
                        sl.removeBlock(at, false);
                        sl.sendParticles(Vfx.largeSmoke(), x + 0.5, y + 0.3, z + 0.5, 4, 0.2, 0.1, 0.2, 0.01);
                    }
                    continue;
                }
                if (s.is(Blocks.FARMLAND) && s.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE) {
                    if (FwUtil.canPlace(c, at)) sl.setBlock(at, s.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), 2);
                } else if (s.is(Blocks.CAULDRON) && rnd.nextFloat() < (deluge ? 0.3F : 0.1F)) {
                    if (FwUtil.canPlace(c, at)) sl.setBlockAndUpdate(at, Blocks.WATER_CAULDRON.defaultBlockState());
                } else if (s.is(Blocks.WATER_CAULDRON) && s.getValue(LayeredCauldronBlock.LEVEL) < 3 && rnd.nextFloat() < 0.15F) {
                    if (FwUtil.canPlace(c, at)) sl.setBlockAndUpdate(at, s.setValue(LayeredCauldronBlock.LEVEL, s.getValue(LayeredCauldronBlock.LEVEL) + 1));
                } else if (s.isRandomlyTicking() && s.getFluidState().isEmpty() && !s.isSolidRender(sl, at)
                        && rnd.nextFloat() < (deluge ? 0.25F : 0.12F)) {
                    s.randomTick(sl, at, rnd); // plantas crescem mais rápido na chuva
                }
                break;
            }
        }
    }

    /** Redemoinho do Dilúvio: inimigos giram e são puxados para o centro. */
    private void swirl() {
        double r = radius();
        Vec3 c = position();
        for (LivingEntity e : inside(r, 5.0, 1.5)) {
            if (ally(e)) continue;
            Vec3 rel = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
            double dist = rel.length();
            if (dist < 0.6) continue;
            Vec3 n = rel.scale(1.0 / dist);
            Vec3 tangent = new Vec3(-n.z, 0, n.x);
            Vec3 add = tangent.scale(0.045).add(n.scale(-0.025));
            e.setDeltaMovement(e.getDeltaMovement().add(add));
            e.hasImpulse = true;
            if (e instanceof Player && tickCount % 4 == 0) e.hurtMarked = true;
        }
    }

    private void wallPulse(ServerLevel sl) {
        double half = radius();
        Vec3 look = Vec3.directionFromRotation(0, wallYaw());
        Vec3 side = new Vec3(-look.z, 0, look.x);
        Vec3 c = position();
        for (LivingEntity e : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(half + 1, 3, half + 1))) {
            if (!e.isAlive() || e.isSpectator() || ally(e)) continue;
            Vec3 rel = e.position().subtract(c);
            double along = rel.x * side.x + rel.z * side.z;
            double across = rel.x * look.x + rel.z * look.z;
            double w = e.getBbWidth() * 0.5;
            if (Math.abs(along) > half + w || Math.abs(across) > 0.7 + w || rel.y < -1.5 || rel.y > 2.8) continue;
            hit(e, damage);
            e.igniteForSeconds(3 + amp);
            // A muralha repele para o lado de onde a criatura veio
            double dir = across >= 0 ? 1 : -1;
            FwUtil.addMotion(e, look.scale(dir * 0.45).add(0, 0.15, 0));
        }
        // Flechas que atravessam a muralha pegam fogo
        for (AbstractArrow a : level().getEntitiesOfClass(AbstractArrow.class, getBoundingBox().inflate(half, 3, half))) {
            Vec3 rel = a.position().subtract(c);
            if (Math.abs(rel.x * look.x + rel.z * look.z) < 0.8) a.igniteForSeconds(5);
        }
        if (tickCount % 20 == 0) FwUtil.sound(sl, c, SoundEvents.FIRE_AMBIENT, 1.2F, 0.8F);
    }

    private void startFx(ServerLevel sl) {
        Vec3 c = position();
        switch (kind()) {
            case SMOKE -> {
                FwUtil.sound(sl, c, SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.6F);
                sl.sendParticles(Vfx.largeSmoke(), c.x, c.y + 0.5, c.z, 40, radius() * 0.4, 0.5, radius() * 0.4, 0.05);
            }
            case MIST -> {
                FwUtil.sound(sl, c, SoundEvents.AMBIENT_UNDERWATER_ENTER, 0.8F, 1.3F);
                FwUtil.shockwave(sl, c.add(0, 0.3, 0), Vfx.cloud(), 24, 0.25);
            }
            case RAIN -> FwUtil.sound(sl, c, SoundEvents.WEATHER_RAIN, 1.0F, 1.0F);
            case DELUGE -> {
                FwUtil.sound(sl, c, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.5F, 0.7F);
                FwUtil.sound(sl, c, SoundEvents.WEATHER_RAIN, 1.5F, 0.6F);
                FwUtil.shockwave(sl, c.add(0, 0.3, 0), Vfx.splash(), 60, 0.6);
            }
            case FIRE_WALL -> {
                FwUtil.sound(sl, c, SoundEvents.BLAZE_SHOOT, 1.0F, 0.7F);
                FwUtil.sound(sl, c, SoundEvents.FIRECHARGE_USE, 1.0F, 0.8F);
            }
        }
    }

    private void endFx(ServerLevel sl) {
        Vec3 c = position();
        switch (kind()) {
            case SMOKE, MIST -> sl.sendParticles(Vfx.poof(), c.x, c.y + 1, c.z, 12, radius() * 0.4, 0.4, radius() * 0.4, 0.02);
            case FIRE_WALL -> {
                Vec3 look = Vec3.directionFromRotation(0, wallYaw());
                Vec3 side = new Vec3(-look.z, 0, look.x);
                for (double t = -radius(); t <= radius(); t += 0.75) {
                    Vec3 p = c.add(side.scale(t));
                    sl.sendParticles(Vfx.largeSmoke(), p.x, p.y + 0.6, p.z, 2, 0.15, 0.3, 0.15, 0.02);
                }
                FwUtil.sound(sl, c, SoundEvents.FIRE_EXTINGUISH, 0.8F, 1.0F);
            }
            default -> {}
        }
    }

    // =====================================================================================
    //  Cliente: partículas
    // =====================================================================================

    private void clientParticles() {
        if (clientLife > 0) clientLife--;
        float fade = Math.min(1F, tickCount / 10F) * Math.min(1F, clientLife / 20F);
        float mult = FwUtil.reducedParticles() ? 0.35F : 1F;
        float r = radius();
        RandomSource rnd = random;
        Level lv = level();
        switch (kind()) {
            case SMOKE -> {
                int n = Math.round((2 + r * r * 0.6F) * fade * mult);
                for (int i = 0; i < n; i++) {
                    Vec3 p = disk(rnd, r, rnd.nextDouble() * 2.4);
                    lv.addParticle(Vfx.largeSmoke(), p.x, p.y, p.z, (rnd.nextDouble() - 0.5) * 0.03, 0.006, (rnd.nextDouble() - 0.5) * 0.03);
                    if (rnd.nextInt(3) == 0) lv.addParticle(ParticleTypes.SMOKE, p.x, p.y, p.z, 0, 0.01, 0);
                }
                if (tickCount % 4 == 0 && fade > 0.5F) {
                    Vec3 p = disk(rnd, r * 0.7, 0.2);
                    lv.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y, p.z, 0, 0.012, 0);
                }
            }
            case MIST -> {
                int n = Math.round((1 + r * r * 0.25F) * fade * mult);
                ParticleOptions fog = FwUtil.dust(0xDCEBFF, 3.6F);
                for (int i = 0; i < n; i++) {
                    Vec3 p = disk(rnd, r, rnd.nextDouble() * 2.6);
                    lv.addParticle(fog, p.x, p.y, p.z, 0, 0, 0);
                    if (rnd.nextInt(4) == 0) lv.addParticle(ParticleTypes.WHITE_SMOKE, p.x, p.y, p.z, 0, 0.004, 0);
                }
            }
            case RAIN, DELUGE -> {
                boolean deluge = kind() == Kind.DELUGE;
                ParticleOptions cloud = FwUtil.dust(deluge ? 0x4F5A6E : 0x8A93A6, 3.2F);
                int nc = Math.round((1 + r * r * 0.12F) * fade * mult);
                for (int i = 0; i < nc; i++) {
                    Vec3 p = disk(rnd, r, 4.6 + rnd.nextDouble() * 0.9);
                    lv.addParticle(cloud, p.x, p.y, p.z, 0, 0, 0);
                }
                int nd = Math.round((2 + r * r * (deluge ? 0.55F : 0.45F)) * fade * mult);
                for (int i = 0; i < nd; i++) {
                    Vec3 p = disk(rnd, r, 4.3);
                    lv.addParticle(Vfx.water(), p.x, p.y, p.z, 0, 0, 0);
                    if (rnd.nextInt(3) == 0) {
                        Vec3 g = disk(rnd, r, 0.05);
                        lv.addParticle(ParticleTypes.RAIN, g.x, groundY(g), g.z, 0, 0, 0);
                    }
                }
                if (deluge) {
                    // Ondas girando no chão
                    double spin = tickCount * 0.12;
                    ParticleOptions wave = FwUtil.dust(0x3FA9FF, 1.6F);
                    for (int ring = 1; ring <= 3; ring++) {
                        double rr = r * ring / 3.0;
                        for (int i = 0; i < 3; i++) {
                            double a = spin * (4 - ring) * 0.5 + i * Math.PI * 2 / 3;
                            double x = getX() + Math.cos(a) * rr, z = getZ() + Math.sin(a) * rr;
                            lv.addParticle(wave, x, getY() + 0.15, z, 0, 0, 0);
                            if (rnd.nextInt(3) == 0) lv.addParticle(ParticleTypes.SPLASH, x, getY() + 0.2, z, 0, 0.05, 0);
                        }
                    }
                    if (rnd.nextInt(2) == 0) {
                        Vec3 b = disk(rnd, r, 0.2);
                        lv.addParticle(ParticleTypes.BUBBLE_POP, b.x, b.y, b.z, 0, 0.02, 0);
                    }
                }
            }
            case FIRE_WALL -> {
                Vec3 look = Vec3.directionFromRotation(0, wallYaw());
                Vec3 side = new Vec3(-look.z, 0, look.x);
                int n = Math.round((2 + r * 2.6F) * fade * mult);
                for (int i = 0; i < n; i++) {
                    double t = (rnd.nextDouble() * 2 - 1) * r;
                    double h = rnd.nextDouble() * rnd.nextDouble() * 2.6;
                    double j = (rnd.nextDouble() - 0.5) * 0.5;
                    Vec3 p = position().add(side.scale(t)).add(look.scale(j)).add(0, h, 0);
                    lv.addParticle(rnd.nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME,
                            p.x, p.y, p.z, 0, 0.02 + rnd.nextDouble() * 0.05, 0);
                    if (rnd.nextInt(40) == 0) lv.addParticle(ParticleTypes.LAVA, p.x, p.y, p.z, 0, 0, 0);
                    if (rnd.nextInt(8) == 0) lv.addParticle(ParticleTypes.SMOKE, p.x, p.y + 1.0, p.z, 0, 0.03, 0);
                }
                Vec3 g = position().add(side.scale((rnd.nextDouble() * 2 - 1) * r));
                lv.addParticle(FwUtil.dust(0xFF5A1A, 1.4F), g.x, g.y + 0.08, g.z, 0, 0, 0);
            }
        }
    }

    private Vec3 disk(RandomSource rnd, double r, double h) {
        double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * r;
        return new Vec3(getX() + Math.cos(a) * d, getY() + h, getZ() + Math.sin(a) * d);
    }

    private double groundY(Vec3 p) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = (int) Math.floor(getY()) + 2; y >= (int) Math.floor(getY()) - 3; y--) {
            m.set(Math.floor(p.x), y, Math.floor(p.z));
            BlockState s = level().getBlockState(m);
            if (!s.getCollisionShape(level(), m).isEmpty()) return y + s.getCollisionShape(level(), m).max(net.minecraft.core.Direction.Axis.Y) + 0.02;
        }
        return getY() + 0.05;
    }

    // =====================================================================================
    //  Entidade "fantasma": sem colisão, sem dano, salva no mundo
    // =====================================================================================

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isAttackable() { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    public boolean ignoreExplosion(Explosion explosion) { return true; }

    @Override
    public PushReaction getPistonPushReaction() { return PushReaction.IGNORE; }

    @Override
    public boolean isIgnoringBlockTriggers() { return true; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(KIND, tag.getByte("Kind"));
        entityData.set(RADIUS, tag.getFloat("Radius"));
        entityData.set(WALL_YAW, tag.getFloat("WallYaw"));
        life = tag.getInt("Life");
        entityData.set(LIFE, life);
        damage = tag.getFloat("Damage");
        amp = tag.getInt("Amp");
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        spellTag = tag.getCompound("Spell");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putByte("Kind", entityData.get(KIND));
        tag.putFloat("Radius", radius());
        tag.putFloat("WallYaw", wallYaw());
        tag.putInt("Life", life);
        tag.putFloat("Damage", damage);
        tag.putInt("Amp", amp);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.put("Spell", spellTag);
    }
}
