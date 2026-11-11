package com.renanpontes.isekaimahou.feature.journal.entity;

import java.util.UUID;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.feature.journal.JournalService;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Espírito Guia: um espírito luminoso que flutua ao lado do jogador quando um capítulo do Diário começa.
 * <ul>
 *   <li>Pertence a UM jogador: só ele recebe a entidade ({@link #broadcastToPlayer}), os sons e as partículas.</li>
 *   <li>Clique (direito ou esquerdo) do dono: conta a dica do objetivo atual (caixa de diálogo) e, quando termina de
 *       falar, se desfaz em luz. Durante o tutorial da entrada no jogo ele fica.</li>
 *   <li>Chamado com um objetivo que tem lugar ({@link #guideTo}): voa à frente na direção do alvo deixando um rastro de
 *       luz que dura alguns segundos, marca o ponto com um clarão e volta para o lado do dono.</li>
 *   <li>Some sozinho depois de 2 minutos, se o dono sair/morrer/mudar de dimensão, e não é salvo no mundo.</li>
 *   <li>Não colide, atravessa blocos, projéteis passam através dele, respira debaixo d'água (tag de merge) e é invulnerável
 *       (exceto /kill e o vazio). As magias do dono o tratam como aliado ({@link OwnableEntity}).</li>
 * </ul>
 */
public class GuideSpiritEntity extends Mob implements GeoEntity, OwnableEntity {
    public static final int LIFETIME = 20 * 120;
    public static final int VANISH_TICKS = 30;
    /** Duração do voo de guia: ida ({@link #GUIDE_FLY}), pausa sobre o ponto e então volta ao dono. */
    public static final int GUIDE_TICKS = 110;
    private static final int GUIDE_FLY = 70;
    /** Até onde o espírito voa na direção do alvo (blocos, no plano). */
    private static final double GUIDE_REACH = 30.0;
    /** Distância entre as luzes do rastro (blocos). */
    private static final double TRAIL_SPACING = 0.55;

    private static final EntityDataAccessor<Boolean> DATA_LEAVING = SynchedEntityData.defineId(GuideSpiritEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(GuideSpiritEntity.class, EntityDataSerializers.INT);

    // GeckoLib procura a animação pelo nome EXATO do arquivo (animation.guide_spirit.*).
    private static final RawAnimation APPEAR_IDLE = RawAnimation.begin()
            .thenPlay("animation.guide_spirit.appear").thenLoop("animation.guide_spirit.idle");
    private static final RawAnimation VANISH = RawAnimation.begin().thenPlayAndHold("animation.guide_spirit.vanish");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID owner;
    private int life = LIFETIME;
    private int vanishTicks = -1;
    private float orbitPhase;
    /** Voo de guia: ponto de partida, direção (no plano), alcance e ticks restantes (0 = parado ao lado do dono). */
    private Vec3 guideStart;
    private Vec3 guideDir;
    private double guideReach;
    private int guideTicks;
    /** Último ponto do rastro (o próximo trecho de luz começa aqui). */
    private Vec3 trailFrom;
    /** O voo passa por cima do relevo (céu aberto) ou segue reto (caverna, dimensão com teto). */
    private boolean followTerrain = true;

    public GuideSpiritEntity(EntityType<? extends GuideSpiritEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.orbitPhase = random.nextFloat() * Mth.TWO_PI;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.FLYING_SPEED, 0.6)
                .add(Attributes.MOVEMENT_SPEED, 0.3);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LEAVING, false);
        builder.define(DATA_COLOR, 0xFFE27A);
    }

    // =====================================================================================
    //  Dono e ciclo de vida
    // =====================================================================================
    public void setOwner(ServerPlayer player) {
        this.owner = player.getUUID();
    }

    public UUID owner() {
        return owner;
    }

    /** Dono como "aliado": as magias dele (área, teleguiadas, correntes) ignoram o espírito. */
    @Override
    public UUID getOwnerUUID() {
        return owner;
    }

    public boolean isOwnedBy(Player player) {
        return owner != null && owner.equals(player.getUUID());
    }

    public int color() {
        return entityData.get(DATA_COLOR);
    }

    public void setColor(int rgb) {
        entityData.set(DATA_COLOR, rgb & 0xFFFFFF);
    }

    public boolean isLeaving() {
        return entityData.get(DATA_LEAVING);
    }

    /** Renova a duração (chamado de novo pelo Diário). */
    public void refresh() {
        life = LIFETIME;
    }

    /**
     * Voa à frente na direção de {@code target} (até {@value #GUIDE_REACH} blocos, ou até o próprio alvo se estiver perto),
     * deixando um rastro de luz visível só para o dono; depois volta.
     */
    public void guideTo(Vec3 target) {
        if (owner == null || isLeaving() || !(level().getPlayerByUUID(owner) instanceof ServerPlayer player)) return;
        Vec3 start = player.getEyePosition().add(0, 0.4, 0);
        Vec3 flat = new Vec3(target.x - start.x, 0, target.z - start.z);
        double dist = flat.length();
        guideDir = dist < 1.0E-3 ? Vec3.directionFromRotation(0, player.getYRot()).multiply(1, 0, 1).normalize() : flat.scale(1 / dist);
        guideReach = Mth.clamp(dist - 1.5, 3.0, GUIDE_REACH);
        guideStart = start;
        // Em caverna (ou dimensão com teto, onde o "topo" é o teto) a superfície fica fora de vista: voa reto na altura
        // dos olhos. Folhas não contam (debaixo de uma árvore ainda é a superfície).
        BlockPos eye = BlockPos.containing(start);
        followTerrain = start.y >= level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, eye.getX(), eye.getZ()) - 3;
        guideTicks = GUIDE_TICKS;
        trailFrom = null;
        life = Math.max(life, GUIDE_TICKS + 200);
        player.playNotifySound(SoundEvents.ALLAY_AMBIENT_WITH_ITEM, SoundSource.NEUTRAL, 1.0F, 1.5F);
    }

    /** Começa a se desfazer em luz (animação "vanish") e some em {@link #VANISH_TICKS}. */
    public void vanish() {
        if (vanishTicks >= 0) return;
        vanishTicks = VANISH_TICKS;
        entityData.set(DATA_LEAVING, true);
        if (owner() != null && level().getPlayerByUUID(owner) instanceof ServerPlayer sp) {
            sp.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 0.8F, 1.6F);
        }
    }

    /** Ovo de criação: adota o jogador mais próximo como dono. */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType,
                                        SpawnGroupData data) {
        if (owner == null && level.getNearestPlayer(this, 16) instanceof ServerPlayer sp) owner = sp.getUUID();
        return super.finalizeSpawn(level, difficulty, spawnType, data);
    }

    @Override
    public boolean broadcastToPlayer(ServerPlayer player) {
        return owner == null || owner.equals(player.getUUID());
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void checkDespawn() {
        // A duração é controlada pelo próprio espírito.
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
    }

    // =====================================================================================
    //  Movimento: flutua ao lado do dono, olhando para ele
    // =====================================================================================
    @Override
    public void tick() {
        noPhysics = true;
        setNoGravity(true);
        super.tick();
        if (level().isClientSide) clientEffects();
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!(level() instanceof ServerLevel sl)) return;
        ServerPlayer player = owner == null ? null : sl.getPlayerByUUID(owner) instanceof ServerPlayer sp ? sp : null;
        if (player == null || !player.isAlive() || player.isSpectator()) {
            if (owner != null || tickCount > 40) discard();
            return;
        }
        if (vanishTicks >= 0) {
            setDeltaMovement(getDeltaMovement().scale(0.6).add(0, 0.02, 0));
            if (--vanishTicks <= 0) {
                sl.sendParticles(player, ParticleTypes.END_ROD, true, getX(), getY() + 0.7, getZ(), 30, 0.3, 0.4, 0.3, 0.08);
                sl.sendParticles(player, dust(1.4F), true, getX(), getY() + 0.7, getZ(), 24, 0.4, 0.4, 0.4, 0.02);
                player.playNotifySound(SoundEvents.ALLAY_ITEM_TAKEN, SoundSource.NEUTRAL, 0.9F, 1.4F);
                discard();
            }
            return;
        }
        if (--life <= 0) {
            vanish();
            return;
        }
        if (guideTicks > 0) {
            guideStep(player, sl);
            return;
        }
        // Ponto alvo: ao lado esquerdo do jogador, na borda da visão (não tampa a tela), um pouco abaixo dos olhos,
        // orbitando devagar. Fica a ~2,5 blocos, ainda ao alcance do clique para pedir dicas.
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double t = tickCount * 0.05 + orbitPhase;
        Vec3 forward = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        Vec3 target = player.position()
                .add(forward.scale(1.0 + 0.2 * Math.sin(t)))
                .add(right.scale(-2.2 + 0.25 * Math.cos(t * 0.7)))
                .add(0, player.getEyeHeight() - 0.85 + 0.18 * Math.sin(t * 1.6), 0);
        Vec3 delta = target.subtract(position());
        if (delta.lengthSqr() > 24 * 24) {
            moveTo(target.x, target.y, target.z, getYRot(), getXRot());
            setDeltaMovement(Vec3.ZERO);
        } else {
            Vec3 v = delta.scale(0.12);
            if (v.length() > 0.8) v = v.normalize().scale(0.8);
            setDeltaMovement(getDeltaMovement().scale(0.5).add(v.scale(0.5)));
        }
        faceOwner(player);
        if (tickCount % 90 == 45) player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.35F, 1.5F + random.nextFloat() * 0.4F);
    }

    /**
     * Um tick do voo de guia: sai do lado do dono, acelera na direção do alvo seguindo o relevo (passa POR CIMA dos morros,
     * para o rastro ficar visível), solta luz a cada tick (END_ROD dura ~3 s: o rastro fica no ar) e, ao chegar, marca o
     * ponto com um clarão e uma "seta" de luz apontando mais adiante.
     */
    private void guideStep(ServerPlayer player, ServerLevel sl) {
        int elapsed = GUIDE_TICKS - guideTicks;
        guideTicks--;
        double t = Math.min(1.0, elapsed / (double) GUIDE_FLY);
        double ease = 1 - Math.pow(1 - t, 3);
        Vec3 flat = guideStart.add(guideDir.scale(guideReach * ease));
        double y = guideStart.y + Math.sin(ease * Math.PI) * 1.5;
        BlockPos column = BlockPos.containing(flat.x, guideStart.y, flat.z);
        if (followTerrain && sl.isLoaded(column)) {
            y = Math.max(y, sl.getHeight(Heightmap.Types.MOTION_BLOCKING, column.getX(), column.getZ()) + 1.6);
        }
        Vec3 goal = new Vec3(flat.x, y + 0.15 * Math.sin(tickCount * 0.4), flat.z);
        Vec3 delta = goal.subtract(position());
        Vec3 v = delta.length() > 2.2 ? delta.normalize().scale(2.2) : delta;
        setDeltaMovement(v);
        face(guideDir);

        // Rastro: luz que fica no ar alguns segundos + poeira na cor do capítulo. No começo o voo anda >1 bloco por tick,
        // então a luz é espalhada pelo trecho percorrido desde o último tick (linha contínua). Parado sobre o ponto, só pulsa.
        boolean flying = elapsed <= GUIDE_FLY;
        Vec3 here = position().add(0, 0.6, 0);
        if (flying) {
            Vec3 from = trailFrom == null ? here : trailFrom;
            Vec3 seg = here.subtract(from);
            int n = Mth.clamp(Mth.ceil(seg.length() / TRAIL_SPACING), 1, 6);
            for (int i = 1; i <= n; i++) {
                Vec3 p = from.add(seg.scale(i / (double) n));
                sl.sendParticles(player, ParticleTypes.END_ROD, true, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
            }
            sl.sendParticles(player, dust(1.1F), true, here.x, here.y, here.z, 2, 0.12, 0.12, 0.12, 0.0);
            trailFrom = here;
        } else if (elapsed % 4 == 0) {
            sl.sendParticles(player, ParticleTypes.END_ROD, true, here.x, here.y, here.z, 1, 0.04, 0.04, 0.04, 0.0);
            sl.sendParticles(player, dust(1.1F), true, here.x, here.y, here.z, 2, 0.12, 0.12, 0.12, 0.0);
        }
        if (flying && elapsed % 10 == 0) {
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.NEUTRAL, 0.3F, 1.3F + (float) ease * 0.6F);
        }
        if (elapsed == GUIDE_FLY) {
            sl.sendParticles(player, ParticleTypes.END_ROD, true, here.x, here.y, here.z, 30, 0.4, 0.5, 0.4, 0.08);
            sl.sendParticles(player, dust(1.6F), true, here.x, here.y, here.z, 20, 0.5, 0.5, 0.5, 0.0);
            // "Seta" de luz continuando na direção do alvo (o alvo costuma estar bem mais longe que o voo).
            for (int i = 1; i <= 10; i++) {
                Vec3 p = here.add(guideDir.scale(i * 0.7));
                sl.sendParticles(player, ParticleTypes.END_ROD, true, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
            }
            player.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.NEUTRAL, 1.0F, 1.5F);
            player.playNotifySound(SoundEvents.ALLAY_ITEM_GIVEN, SoundSource.NEUTRAL, 0.8F, 1.2F);
        }
    }

    /** Vira o corpo para uma direção (no plano). */
    private void face(Vec3 dir) {
        if (dir.x * dir.x + dir.z * dir.z < 1.0E-4) return;
        float yaw = (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90F;
        setYRot(Mth.rotLerp(0.35F, getYRot(), yaw));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    /** Vira o corpo inteiro para o dono (o modelo não tem cabeça separada). */
    private void faceOwner(ServerPlayer player) {
        double dx = player.getX() - getX(), dz = player.getZ() - getZ();
        if (dx * dx + dz * dz < 1.0E-4) return;
        float face = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        setYRot(Mth.rotLerp(0.35F, getYRot(), face));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    /** O corpo segue o yRot (sincronizado pelo servidor) em vez da regra de "corpo atrasado" dos mobs. */
    @Override
    protected float tickHeadTurn(float yRot, float animStep) {
        yBodyRot = getYRot();
        yHeadRot = getYRot();
        return animStep;
    }

    @Override
    public void travel(Vec3 input) {
        if (isControlledByLocalInstance()) {
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().scale(0.85));
        }
    }

    private void clientEffects() {
        if (isLeaving()) {
            if (random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.END_ROD, getRandomX(0.6), getY() + random.nextDouble() * 1.1, getRandomZ(0.6),
                        random.nextGaussian() * 0.02, 0.06, random.nextGaussian() * 0.02);
            }
            return;
        }
        if (random.nextInt(4) == 0) {
            level().addParticle(dust(0.7F), getRandomX(0.4), getY() + 0.1 + random.nextDouble() * 0.4, getRandomZ(0.4), 0, -0.02, 0);
        }
        if (random.nextInt(9) == 0) {
            level().addParticle(ParticleTypes.END_ROD, getRandomX(0.5), getY() + 0.6 + random.nextDouble() * 0.5, getRandomZ(0.5),
                    random.nextGaussian() * 0.005, 0.015, random.nextGaussian() * 0.005);
        }
        // Ao surgir: uma espiral de luz na cor do capítulo se fecha em volta dele.
        if (tickCount < 24) {
            double a = tickCount * 0.6;
            double r = 1.2 - tickCount * 0.045;
            for (int i = 0; i < 2; i++) {
                double ang = a + i * Math.PI;
                level().addParticle(dust(0.9F), getX() + Math.cos(ang) * r, getY() + 0.2 + tickCount * 0.035, getZ() + Math.sin(ang) * r, 0, 0, 0);
            }
            if (tickCount == 1) {
                for (int i = 0; i < 16; i++) {
                    level().addParticle(ParticleTypes.END_ROD, getX(), getY() + 0.6, getZ(),
                            random.nextGaussian() * 0.08, random.nextGaussian() * 0.08, random.nextGaussian() * 0.08);
                }
            }
        }
    }

    private DustParticleOptions dust(float size) {
        int c = color();
        return new DustParticleOptions(new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F), size);
    }

    // =====================================================================================
    //  Interação: o dono clica para ouvir a dica
    // =====================================================================================
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        talk(player);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (!level().isClientSide && attacker instanceof Player p) talk(p);
        return true;
    }

    private void talk(Player player) {
        if (!(player instanceof ServerPlayer sp) || isLeaving()) return;
        if (owner == null) owner = sp.getUUID();
        if (!isOwnedBy(sp)) return;
        // A dica sai na caixa de diálogo; o espírito fica enquanto fala e depois se desfaz em luz (no tutorial, fica).
        boolean leave = JournalService.deliverHint(sp);
        ServerLevel sl = sp.serverLevel();
        sl.sendParticles(sp, ParticleTypes.END_ROD, false, getX(), getY() + 0.7, getZ(), 14, 0.25, 0.3, 0.25, 0.04);
        sp.playNotifySound(SoundEvents.ALLAY_AMBIENT_WITH_ITEM, SoundSource.NEUTRAL, 1.0F, 1.3F);
        if (leave) JournalService.dismissLater(sp);
    }

    // =====================================================================================
    //  Invulnerável e sem empurrões
    // =====================================================================================
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Clicável pelo dono, mas flechas e projéteis de magia passam através dele. */
    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public void knockback(double strength, double x, double z) {}

    @Override
    public boolean isPushedByFluid() {
        return false;
    }
    @Override
    protected void doPush(Entity entity) {}

    @Override
    protected void pushEntities() {}

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putInt("Life", life);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) owner = tag.getUUID("Owner");
        if (tag.contains("Life")) life = tag.getInt("Life");
    }

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<GuideSpiritEntity>(this, "main", 0, state -> {
            boolean leaving = isLeaving();
            // Ao surgir começa direto do "appear" (escala 0; uma transição mostraria o espírito inteiro encolhendo antes).
            // Ao sumir, transição curta a partir da pose do idle.
            state.getController().transitionLength(leaving ? 4 : 0);
            return state.setAndContinue(leaving ? VANISH : APPEAR_IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
