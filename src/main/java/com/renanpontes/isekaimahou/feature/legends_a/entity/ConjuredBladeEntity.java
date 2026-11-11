package com.renanpontes.isekaimahou.feature.legends_a.entity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.feature.legends_a.LegendFx;
import com.renanpontes.isekaimahou.feature.legends_a.LegendsARegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Arma conjurada (espada, lança, machado...) desenhada com o modelo do item. Três usos:
 * <ul>
 *   <li><b>Portador</b> (Portão do Arsenal): voa e, ao acertar, aplica TODOS os efeitos da forma (avisa o impacto
 *       para as ligações Ao Acertar/Saltar);</li>
 *   <li><b>Golpe</b> (Arma Conjurada): voa até o alvo e causa o dano do efeito;</li>
 *   <li><b>Broto</b> (Campo de Lâminas): brota cravada no chão, fere quem está em cima e depois se lança num inimigo.</li>
 * </ul>
 * Perfura N alvos e, ao bater num bloco, fica cravada alguns segundos e some. Não é salva (vive poucos segundos).
 */
public class ConjuredBladeEntity extends Entity {
    public static final byte FLY = 0;
    public static final byte STUCK = 1;
    public static final byte RISING = 2;
    public static final byte FADE = 3;

    public static final int RISE_TICKS = 6;
    public static final int FADE_TICKS = 10;
    /** Quanto a espada do Campo de Lâminas sobe ao se arrancar do chão. */
    public static final double LAUNCH_LIFT = 0.9;
    /** Lâminas ativas por conjurador (todas as lendas somadas): acima disso os efeitos causam o dano direto. */
    public static final int MAX_PER_OWNER = 48;

    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(ConjuredBladeEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(ConjuredBladeEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Vector3f> DATA_DIR = SynchedEntityData.defineId(ConjuredBladeEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> DATA_SPEED = SynchedEntityData.defineId(ConjuredBladeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SCALE = SynchedEntityData.defineId(ConjuredBladeEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(ConjuredBladeEntity.class, EntityDataSerializers.INT);

    /** Profundidade de chamadas "a lâmina portadora está aplicando a magia" (thread do servidor). */
    private static int carrierDepth;
    @Nullable
    private static ConjuredBladeEntity currentCarrier;

    // ---------- servidor ----------
    private SpellContext ctx;
    private SpellStats stats;
    private boolean carrier;
    private float damage;
    private int pierceLeft;
    private int maxFlight = 40;
    private int stuckTicks = 60;
    private int modeTicks;
    private int launchAt;
    private float launchRange;
    private Vec3 fieldCenter;
    private UUID ownerId;
    private final Set<UUID> hitIds = new HashSet<>();
    /** Já saiu do bloco onde nasceu (ver {@link #clipBlocks}). Vale nos dois lados. */
    private boolean escaped;

    // ---------- cliente ----------
    /** Tick (do cliente) em que o modo atual começou, para animar subida/sumiço. */
    public int clientModeStart;
    /** Direção desenhada (suavizada no cliente ao trocar de modo). */
    public Vec3 renderDir = new Vec3(0, -1, 0);
    public Vec3 prevRenderDir = new Vec3(0, -1, 0);

    public ConjuredBladeEntity(EntityType<? extends ConjuredBladeEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    // =====================================================================================
    //  Criação
    // =====================================================================================

    /** Lâmina PORTADORA: aplica os efeitos da forma ao acertar (Portão do Arsenal). */
    public static ConjuredBladeEntity carrier(SpellContext ctx, Vec3 from, Vec3 dir, ItemStack item, int color, float scale,
                                              int pierce, float speed) {
        ConjuredBladeEntity b = base(ctx, from, item, color, scale);
        b.carrier = true;
        b.pierceLeft = pierce;
        b.launch(dir, speed, 36);
        return b;
    }

    /** Lâmina de GOLPE: causa {@code damage} (com os aumentos do efeito) em quem atravessar. */
    public static ConjuredBladeEntity striker(SpellContext ctx, SpellStats stats, float damage, Vec3 from, Vec3 dir, ItemStack item,
                                              int color, float scale, int pierce, float speed) {
        ConjuredBladeEntity b = base(ctx, from, item, color, scale);
        b.stats = stats;
        b.damage = damage;
        b.pierceLeft = pierce;
        b.launch(dir, speed, 30);
        return b;
    }

    /** Lâmina que BROTA do chão (Campo de Lâminas) e se lança num inimigo a até {@code range} de {@code center}. */
    public static ConjuredBladeEntity sprout(SpellContext ctx, SpellStats stats, float damage, Vec3 ground, Vec3 center, float range,
                                             int launchAt, ItemStack item, int color, float scale, Vec3 tilt) {
        ConjuredBladeEntity b = base(ctx, ground, item, color, scale);
        b.stats = stats;
        b.damage = damage;
        b.pierceLeft = 1 + Math.max(0, stats.pierce);
        b.fieldCenter = center;
        b.launchRange = range;
        b.launchAt = launchAt;
        b.setMode(RISING);
        b.setDir(tilt);
        return b;
    }

    private static ConjuredBladeEntity base(SpellContext ctx, Vec3 at, ItemStack item, int color, float scale) {
        ConjuredBladeEntity b = new ConjuredBladeEntity(LegendsARegistry.CONJURED_BLADE.get(), ctx.level());
        b.ctx = ctx;
        b.ownerId = ctx.casterId();
        b.setPos(at.x, at.y, at.z);
        b.entityData.set(DATA_ITEM, item);
        b.entityData.set(DATA_COLOR, color);
        b.entityData.set(DATA_SCALE, scale);
        return b;
    }

    private void launch(Vec3 dir, float speed, int maxFlight) {
        this.maxFlight = maxFlight;
        entityData.set(DATA_SPEED, speed);
        setDir(dir);
        setMode(FLY);
    }

    /** Quantas lâminas deste conjurador existem agora no nível (para o limite {@link #MAX_PER_OWNER}). */
    public static int countFor(ServerLevel level, @Nullable UUID owner) {
        if (owner == null) return 0;
        int[] n = {0};
        level.getEntities(LegendsARegistry.CONJURED_BLADE.get(), b -> {
            if (owner.equals(b.ownerId)) n[0]++;
            return false;
        });
        return n[0];
    }

    /** true enquanto uma lâmina portadora aplica a magia num alvo (a Arma Conjurada não invoca OUTRA arma). */
    public static boolean isCarrierHit() {
        return carrierDepth > 0;
    }

    @Nullable
    public static ConjuredBladeEntity currentCarrier() {
        return currentCarrier;
    }

    // =====================================================================================
    //  Dados
    // =====================================================================================
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM, new ItemStack(Items.IRON_SWORD));
        builder.define(DATA_MODE, FLY);
        builder.define(DATA_DIR, new Vector3f(0, -1, 0));
        builder.define(DATA_SPEED, 1.5F);
        builder.define(DATA_SCALE, 1.4F);
        builder.define(DATA_COLOR, LegendFx.GOLD);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (!level().isClientSide) return;
        // (a posição nova chega no mesmo tick, antes dos dados: o servidor manda as duas juntas quando os dados mudam)
        if (DATA_MODE.equals(key)) clientModeStart = tickCount;
        // Ao nascer, já aponta para a direção certa (sem girar a partir de "para baixo")
        if (DATA_DIR.equals(key) && tickCount == 0) {
            renderDir = dir();
            prevRenderDir = renderDir;
        }
    }

    public ItemStack item() { return entityData.get(DATA_ITEM); }
    public byte mode() { return entityData.get(DATA_MODE); }
    public float speed() { return entityData.get(DATA_SPEED); }
    public float scale() { return entityData.get(DATA_SCALE); }
    public int color() { return entityData.get(DATA_COLOR); }

    public Vec3 dir() {
        Vector3f v = entityData.get(DATA_DIR);
        Vec3 d = new Vec3(v.x(), v.y(), v.z());
        return d.lengthSqr() < 1.0E-6 ? new Vec3(0, -1, 0) : d.normalize();
    }

    private void setDir(Vec3 d) {
        Vec3 n = d.lengthSqr() < 1.0E-6 ? new Vec3(0, -1, 0) : d.normalize();
        entityData.set(DATA_DIR, new Vector3f((float) n.x, (float) n.y, (float) n.z));
    }

    private void setMode(byte m) {
        entityData.set(DATA_MODE, m);
        modeTicks = 0;
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        modeTicks++;
        if (ctx == null) {
            discard();
            return;
        }
        switch (mode()) {
            case FLY -> flyTick(sl);
            case STUCK -> {
                if (modeTicks >= stuckTicks) setMode(FADE);
            }
            case RISING -> risingTick(sl);
            default -> {
                if (modeTicks >= FADE_TICKS) discard();
            }
        }
        if (tickCount > 400) discard();
    }

    private void flyTick(ServerLevel sl) {
        Vec3 d = dir();
        Vec3 from = position();
        Vec3 to = from.add(d.scale(speed()));

        BlockHitResult block = clipBlocks(from, to);
        double blockDist = block.getType() == HitResult.Type.BLOCK ? from.distanceTo(block.getLocation()) : Double.MAX_VALUE;

        // Entidades no caminho deste tick, da mais perto para a mais longe (perfuração)
        record Hit(Entity e, Vec3 at, double dist) {}
        List<Hit> hits = new ArrayList<>();
        for (Entity e : sl.getEntities(this, new AABB(from, to).inflate(1.0), this::canStrike)) {
            Optional<Vec3> clip = e.getBoundingBox().inflate(0.35).clip(from, to);
            if (clip.isEmpty() && e.getBoundingBox().inflate(0.35).contains(from)) clip = Optional.of(from);
            clip.ifPresent(at -> hits.add(new Hit(e, at, from.distanceTo(at))));
        }
        hits.sort(Comparator.comparingDouble(Hit::dist));
        for (Hit h : hits) {
            if (h.dist() > blockDist) break;
            hitIds.add(h.e().getUUID());
            strike(sl, h.e(), h.at(), d);
            if (pierceLeft-- <= 0) {
                setPos(h.at().x, h.at().y, h.at().z);
                setMode(FADE);
                return;
            }
        }

        if (block.getType() == HitResult.Type.BLOCK) {
            Vec3 at = block.getLocation().add(d.scale(0.15));
            setPos(at.x, at.y, at.z);
            if (carrier) {
                ctx.impact(block.getLocation(), d, null);
                ctx.resolveOnBlock(block, this);
            }
            BlockState st = sl.getBlockState(block.getBlockPos());
            if (!st.isAir()) {
                sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), at.x, at.y, at.z, 12, 0.2, 0.15, 0.2, 0.12);
            }
            sl.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.1, at.z, 6, 0.15, 0.1, 0.15, 0.2);
            LegendFx.sound(sl, at, SoundEvents.TRIDENT_HIT_GROUND, 0.8F, 0.9F + random.nextFloat() * 0.3F);
            stuckTicks = fieldCenter != null ? 50 : 60;
            setMode(STUCK);
            return;
        }

        setPos(to.x, to.y, to.z);
        if (modeTicks >= maxFlight) setMode(FADE);
    }

    /**
     * Colisão com blocos do trecho deste tick. Uma arma que NASCE dentro de um bloco (portal encostado numa parede)
     * atravessa até sair dele; depois disso bate normalmente. Mesma regra no cliente e no servidor.
     */
    private BlockHitResult clipBlocks(Vec3 from, Vec3 to) {
        if (!escaped) {
            if (insideSolid()) return BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to));
            escaped = true;
        }
        return level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
    }

    private boolean insideSolid() {
        BlockPos p = blockPosition();
        VoxelShape shape = level().getBlockState(p).getCollisionShape(level(), p);
        if (shape.isEmpty()) return false;
        Vec3 local = position().subtract(p.getX(), p.getY(), p.getZ());
        for (AABB box : shape.toAabbs()) {
            if (box.contains(local)) return true;
        }
        return false;
    }

    private boolean canStrike(Entity e) {
        if (!(e instanceof LivingEntity) || !e.isAlive() || e.isSpectator()) return false;
        if (hitIds.contains(e.getUUID())) return false;
        if (ownerId != null && ownerId.equals(e.getUUID())) return false;
        // Portão como estágio: ignora o alvo do estágio anterior só logo no começo (regra do núcleo, expira sozinha);
        // as rajadas seguintes voltam a acertá-lo
        if (carrier && ctx.ignores(e)) return false;
        return !ctx.isAlly(e);
    }

    /** Uma lâmina acertou: cada arma é um golpe próprio (zera a invencibilidade curta do alvo). */
    private void strike(ServerLevel sl, Entity target, Vec3 at, Vec3 d) {
        if (target instanceof LivingEntity le) le.invulnerableTime = 0;
        if (carrier) {
            ctx.impact(at, d, target);
            carrierDepth++;
            ConjuredBladeEntity prev = currentCarrier;
            currentCarrier = this;
            try {
                ctx.resolveOnEntity(target, this);
            } finally {
                carrierDepth--;
                currentCarrier = prev;
            }
        } else {
            ctx.damage(target, damage, stats, this);
        }
        if (target.isAlive()) {
            target.push(d.x * 0.35, 0.12, d.z * 0.35);
            target.hurtMarked = true;
        }
        sl.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.25);
        sl.sendParticles(LegendFx.dust(color(), 1.2F), at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0);
        LegendFx.sound(sl, at, SoundEvents.TRIDENT_HIT, 0.7F, 0.8F + random.nextFloat() * 0.4F);
    }

    /** Campo de Lâminas: brota, fere quem está em cima e, na hora certa, se lança num inimigo próximo. */
    private void risingTick(ServerLevel sl) {
        if (modeTicks == RISE_TICKS / 2) {
            Vec3 p = position();
            BlockState st = sl.getBlockState(BlockPos.containing(p.x, p.y - 0.3, p.z));
            if (!st.isAir()) sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), p.x, p.y + 0.1, p.z, 14, 0.25, 0.1, 0.25, 0.15);
            sl.sendParticles(ParticleTypes.CRIT, p.x, p.y + 0.6, p.z, 5, 0.1, 0.4, 0.1, 0.1);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(1.1, 1.6, 1.1), this::canStrike)) {
                e.invulnerableTime = 0;
                ctx.damage(e, damage * 0.4F, stats, this);
                e.push(0, 0.42, 0);
                e.hurtMarked = true;
            }
            LegendFx.sound(sl, p, SoundEvents.CHAIN_PLACE, 0.6F, 0.6F + random.nextFloat() * 0.3F);
        }
        if (modeTicks < launchAt) return;
        LivingEntity target = pickFieldTarget(sl);
        if (target == null) {
            stuckTicks = 40;
            setMode(STUCK);
            return;
        }
        // Arranca do chão e dispara no alvo
        Vec3 start = position().add(0, LAUNCH_LIFT, 0);
        setPos(start.x, start.y, start.z);
        Vec3 aim = LegendFx.center(target).subtract(start);
        LegendFx.sound(sl, start, SoundEvents.TRIDENT_THROW, 0.6F, 1.1F + random.nextFloat() * 0.3F);
        sl.sendParticles(LegendFx.dust(color(), 1.0F), start.x, start.y, start.z, 6, 0.1, 0.2, 0.1, 0);
        launch(aim, 1.5F, 24);
    }

    /** Um dos inimigos mais próximos do centro do campo (sorteado entre os 3 primeiros, para espalhar as espadas). */
    @Nullable
    private LivingEntity pickFieldTarget(ServerLevel sl) {
        Vec3 c = fieldCenter != null ? fieldCenter : position();
        List<LivingEntity> foes = new ArrayList<>(sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(launchRange),
                e -> canStrike(e) && e.distanceToSqr(c) <= launchRange * launchRange));
        if (foes.isEmpty()) return null;
        foes.sort(Comparator.comparingDouble(e -> e.distanceToSqr(position())));
        return foes.get(random.nextInt(Math.min(3, foes.size())));
    }

    // =====================================================================================
    //  Cliente
    // =====================================================================================
    private void clientTick() {
        prevRenderDir = renderDir;
        Vec3 target = dir();
        // Ao trocar de modo (brotar → voar) a lâmina gira suavemente até a nova direção
        renderDir = renderDir.lerp(target, 0.45);
        if (renderDir.lengthSqr() < 1.0E-4) renderDir = target;
        renderDir = renderDir.normalize();

        if (mode() == FLY) {
            Vec3 d = dir();
            Vec3 from = position();
            Vec3 to = from.add(d.scale(speed()));
            // Previsão no cliente: para no bloco (o servidor confirma a posição final em seguida)
            BlockHitResult block = clipBlocks(from, to);
            Vec3 next = block.getType() == HitResult.Type.BLOCK ? block.getLocation() : to;
            setPos(next.x, next.y, next.z);
            int rgb = color();
            for (int i = 0; i < 2; i++) {
                double f = random.nextDouble();
                Vec3 p = from.lerp(next, f);
                level().addParticle(LegendFx.dust(rgb, 0.9F), p.x, p.y, p.z, 0, 0, 0);
            }
            if (random.nextInt(3) == 0) level().addParticle(ParticleTypes.END_ROD, from.x, from.y, from.z, 0, 0, 0);
        } else if (mode() == FADE && random.nextInt(2) == 0) {
            Vec3 p = position();
            level().addParticle(LegendFx.dust(color(), 0.8F), p.x + (random.nextDouble() - 0.5) * 0.5, p.y + random.nextDouble() * 0.8,
                    p.z + (random.nextDouble() - 0.5) * 0.5, 0, 0.03, 0);
        }
    }

    /** 0..1 da animação do modo atual (cliente). */
    public float modeProgress(float partialTick, float duration) {
        return Mth.clamp((tickCount - clientModeStart + partialTick) / duration, 0, 1);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 96 * 96;
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
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
