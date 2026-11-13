package com.renanpontes.isekaimahou.feature.vehicles.entity;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;
import com.renanpontes.isekaimahou.feature.vehicles.VehicleKind;
import com.renanpontes.isekaimahou.feature.vehicles.VehicleRegistry;
import com.renanpontes.isekaimahou.feature.vehicles.VehicleStacks;
import com.renanpontes.isekaimahou.feature.vehicles.VehicleUpgrades;
import com.renanpontes.isekaimahou.feature.vehicles.VehiclesRegistries;
import com.renanpontes.isekaimahou.feature.vehicles.item.VehiclePartItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Veículo mágico (todos os tipos de {@link VehicleKind} usam esta classe; o tipo vem do tipo da entidade).
 *
 * <ul>
 *   <li><b>Controles simples</b>: o veículo vira suave para onde o piloto olha; W/S/A/D andam; nos que voam, pulo sobe
 *       e agachar desce (perto do chão, agachar desmonta). Onda 10: aceleração e frenagem suaves e "pulinho" automático
 *       em degraus de 1 bloco (carroça, triciclo, barcos saindo para um cais baixo, tapete).</li>
 *   <li><b>Energia = mana ou cristal</b>: em uso, gasta a mana do piloto (sempre deixando 25% de reserva); sem mana
 *       sobrando, gasta a carga do tanque (clique com um Cristal de Mana para carregar). Onda 10: tanque vazio puxa
 *       sozinho um Cristal de Mana que estiver no baú do veículo. Parado no chão não gasta nada.</li>
 *   <li><b>Dono</b>: quem colocou. Só o dono pilota, abre a carga, aplica melhorias e guarda o veículo (agachado + bater
 *       devolve o item com carga, vida e melhorias). Amigos sobem nos outros lugares depois do dono; se o dono descer,
 *       ninguém pilota no lugar dele.</li>
 *   <li><b>Vida (onda 10)</b>: monstros, flechas de monstros e explosões estragam o veículo; jogadores NUNCA (nem PvP
 *       nem brincadeira). Com pouca vida solta fumaça e anda mais devagar; em zero ele é destruído (sem quebrar blocos,
 *       a carga cai no chão) e o Mecânico pode reconstruí-lo. Kit de Reparos ou o Mecânico consertam.</li>
 *   <li>Nada aqui quebra blocos nem fere ninguém: é só transporte.</li>
 * </ul>
 * Movimento: como o barco vanilla, quem pilota simula no próprio cliente e o servidor acompanha; sem piloto o
 * servidor só deixa o veículo pousar/boiar.
 */
public class MagicVehicle extends Entity implements GeoEntity {
    /** Carga máxima do tanque e quanto cada Cristal de Mana carrega. */
    public static final int MAX_FUEL = 1200;
    public static final int CRYSTAL_CHARGE = 240;
    /** Fração da mana do piloto que o veículo nunca usa (fica para lutar). */
    private static final float MANA_RESERVE = 0.25F;
    /** Mana mínima (fração) para o veículo voltar a usar a mana depois de ter passado para o tanque. */
    private static final float MANA_RESUME = 0.4F;
    /** Linha d'água do barco: quanto do casco fica submerso. */
    private static final double WATERLINE = 0.3;
    /** Quanto da vida o Kit de Reparos devolve. */
    public static final float KIT_REPAIR = 0.4F;
    /** Abaixo disto (fração da vida) o veículo solta fumaça preta e anda a 70%. */
    private static final float CRITICAL = 0.25F;
    /** Espaços do baú interno (o máximo com Porão Expandido; o resto fica escondido). */
    private static final int CARGO_CAP = 54;
    private static final byte EVENT_HURT = 71;

    /** Fonte de energia atual (sincronizada): sem energia, mana do piloto, tanque, grátis (criativo). */
    public static final byte SRC_NONE = 0;
    public static final byte SRC_MANA = 1;
    public static final byte SRC_TANK = 2;
    public static final byte SRC_FREE = 3;

    private static final EntityDataAccessor<Integer> DATA_FUEL = SynchedEntityData.defineId(MagicVehicle.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> DATA_SOURCE = SynchedEntityData.defineId(MagicVehicle.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_HEALTH = SynchedEntityData.defineId(MagicVehicle.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_UPG = SynchedEntityData.defineId(MagicVehicle.class, EntityDataSerializers.INT);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final VehicleKind kind;
    private final SimpleContainer cargo;
    private final RawAnimation animIdle;
    private final RawAnimation animMove;
    @Nullable
    private UUID owner;
    /** Identidade e geração (onda 10, {@link VehicleRegistry}). */
    @Nullable
    private UUID vehicleId;
    private int gen;
    /** Retirado pelo Mecânico/registro: a carga já foi entregue, não derruba de novo. */
    private boolean cargoHandled;
    private int invulnerable;

    // Interpolação nos clientes que só assistem
    private int lerpSteps;
    private double lerpX;
    private double lerpY;
    private double lerpZ;
    private double lerpYRot;
    private double lerpXRot;

    // Entradas do piloto (só no cliente dele; ver client/VehicleInput)
    private float inForward;
    private float inStrafe;
    private boolean inUp;
    private boolean inDown;
    private long inputTime = Long.MIN_VALUE;

    // Visual (cliente): rolagem nas curvas, inclinação ao subir/descer e tremida ao levar dano
    private float bank;
    private float bankO;
    private float tilt;
    private float tiltO;
    private int shake;
    private boolean moving;

    // Servidor: consumo de energia
    @Nullable
    private Vec3 lastFuelPos;

    public MagicVehicle(EntityType<? extends MagicVehicle> type, Level level) {
        super(type, level);
        this.kind = VehicleKind.of(type);
        this.blocksBuilding = true;
        this.animIdle = RawAnimation.begin().thenLoop("animation." + kind.id + ".idle");
        this.animMove = RawAnimation.begin().thenLoop("animation." + kind.id + ".move");
        this.cargo = new SimpleContainer(CARGO_CAP) {
            @Override
            public boolean stillValid(Player player) {
                return isAlive() && player.distanceToSqr(MagicVehicle.this) < 64.0;
            }
        };
    }

    public VehicleKind kind() {
        return kind;
    }

    // =====================================================================================
    //  Dados
    // =====================================================================================

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_FUEL, 0);
        builder.define(DATA_SOURCE, SRC_MANA);
        builder.define(DATA_HEALTH, -1F);
        builder.define(DATA_UPG, 0);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (owner != null) tag.putUUID("Owner", owner);
        tag.putInt("Fuel", getFuel());
        if (!cargo.isEmpty()) tag.put("Cargo", ContainerHelper.saveAllItems(new CompoundTag(), cargo.getItems(), registryAccess()));
        if (vehicleId != null) tag.putUUID("VehicleId", vehicleId);
        if (gen > 0) tag.putInt("VehicleGen", gen);
        tag.putFloat("Health", getHealth());
        if (upgrades() != 0) tag.putInt("Upgrades", upgrades());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        setFuel(tag.getInt("Fuel"));
        if (tag.contains("Cargo", Tag.TAG_COMPOUND)) ContainerHelper.loadAllItems(tag.getCompound("Cargo"), cargo.getItems(), registryAccess());
        vehicleId = tag.hasUUID("VehicleId") ? tag.getUUID("VehicleId") : null;
        gen = tag.getInt("VehicleGen");
        entityData.set(DATA_UPG, tag.getInt("Upgrades"));
        // veículo de antes da onda 10: sem vida salva = inteiro
        setHealth(tag.contains("Health") ? tag.getFloat("Health") : getMaxHealth());
    }

    public int getFuel() {
        return entityData.get(DATA_FUEL);
    }

    public void setFuel(int fuel) {
        entityData.set(DATA_FUEL, Mth.clamp(fuel, 0, MAX_FUEL));
    }

    /** De onde vem a energia agora ({@link #SRC_NONE} = sem energia). */
    public byte energySource() {
        return entityData.get(DATA_SOURCE);
    }

    public boolean isPowered() {
        return energySource() != SRC_NONE;
    }

    public int upgrades() {
        return entityData.get(DATA_UPG);
    }

    public void setUpgrades(int packed) {
        float frac = healthFraction();
        entityData.set(DATA_UPG, packed);
        // a Blindagem muda a vida máxima: mantém a mesma fração
        setHealth(frac * getMaxHealth());
    }

    public float getMaxHealth() {
        return kind.maxHealth * VehicleUpgrades.healthMult(upgrades());
    }

    public float getHealth() {
        float h = entityData.get(DATA_HEALTH);
        return h < 0F ? getMaxHealth() : h;
    }

    public void setHealth(float hp) {
        entityData.set(DATA_HEALTH, Mth.clamp(hp, 0F, getMaxHealth()));
    }

    public float healthFraction() {
        return Mth.clamp(getHealth() / Math.max(1F, getMaxHealth()), 0F, 1F);
    }

    public int cargoSlots() {
        return kind.cargoSlots(VehicleUpgrades.level(upgrades(), VehicleUpgrades.Part.STORAGE));
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    @Nullable
    public UUID ownerId() {
        return owner;
    }

    @Nullable
    public UUID vehicleId() {
        return vehicleId;
    }

    public int gen() {
        return gen;
    }

    /** Dá identidade, geração, vida e melhorias vindas do item (antes de entrar no mundo). */
    public void applyData(VehicleStacks.Data d) {
        vehicleId = d.id() != null ? d.id() : UUID.randomUUID();
        gen = d.gen();
        entityData.set(DATA_UPG, d.upg());
        setFuel(d.fuel());
        setHealth(d.hp() * getMaxHealth());
    }

    /** O que vai para o item quando o veículo é guardado. */
    public VehicleStacks.Data data() {
        return new VehicleStacks.Data(getFuel(), vehicleId, gen, Math.max(0.01F, healthFraction()), upgrades());
    }

    /** Dono (ou veículo sem dono) — ou jogador do criativo, para testes. */
    public boolean isOwner(Player player) {
        return owner == null || owner.equals(player.getUUID()) || player.getAbilities().instabuild;
    }

    // =====================================================================================
    //  Entradas do piloto e visual (cliente)
    // =====================================================================================

    /** Chamado todo tick pelo cliente de quem pilota. */
    public void setInput(float forward, float strafe, boolean up, boolean down) {
        this.inForward = forward;
        this.inStrafe = strafe;
        this.inUp = up;
        this.inDown = down;
        this.inputTime = level().getGameTime();
    }

    public float bank(float partialTick) {
        return Mth.lerp(partialTick, bankO, bank);
    }

    public float tilt(float partialTick) {
        return Mth.lerp(partialTick, tiltO, tilt);
    }

    /** Tremida rápida de quando o veículo leva dano (graus de rolagem extra). */
    public float shake(float partialTick) {
        if (shake <= 0) return 0F;
        return Mth.sin((tickCount + partialTick) * 2.2F) * shake * 0.9F;
    }

    /** Altura livre sobre o chão/água logo abaixo (até {@code max} blocos). */
    public double heightAbove(double max) {
        Vec3 from = position().add(0.0, 0.1, 0.0);
        BlockHitResult hit = level().clip(new ClipContext(from, from.add(0.0, -max - 0.1, 0.0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, this));
        return hit.getType() == HitResult.Type.MISS ? max : Math.max(0.0, from.y - hit.getLocation().y - 0.1);
    }

    // =====================================================================================
    //  Tick e física
    // =====================================================================================

    @Override
    public void tick() {
        super.tick();
        tickLerp();
        bankO = bank;
        tiltO = tilt;
        if (invulnerable > 0) invulnerable--;
        LivingEntity driver = getControllingPassenger();
        if (isControlledByLocalInstance()) {
            if (level().isClientSide && driver instanceof Player p) control(p);
            else drift();
            move(MoverType.SELF, getDeltaMovement());
        } else {
            setDeltaMovement(Vec3.ZERO);
        }
        double dx = getX() - xo;
        double dy = getY() - yo;
        double dz = getZ() - zo;
        moving = dx * dx + dz * dz > 4.0E-4 || kind.flies() && Math.abs(dy) > 0.02;
        if (level().isClientSide) {
            float turn = Mth.wrapDegrees(getYRot() - yRotO);
            bank += (Mth.clamp(turn * 3.0F, -20.0F, 20.0F) - bank) * 0.2F;
            float wantTilt = kind.flies() ? Mth.clamp((float) (-dy * 45.0), -18.0F, 18.0F) : 0.0F;
            tilt += (wantTilt - tilt) * 0.2F;
            if (shake > 0) shake--;
            clientFx(Math.sqrt(dx * dx + dz * dz));
        } else {
            serverEnergyTick(driver);
            if (tickCount % 20 == 7 && level() instanceof ServerLevel sl) registryTick(sl);
        }
    }

    private void tickLerp() {
        if (isControlledByLocalInstance()) {
            lerpSteps = 0;
            syncPacketPositionCodec(getX(), getY(), getZ());
        }
        if (lerpSteps > 0) {
            lerpPositionAndRotationStep(lerpSteps, lerpX, lerpY, lerpZ, lerpYRot, lerpXRot);
            lerpSteps--;
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
        lerpX = x;
        lerpY = y;
        lerpZ = z;
        lerpYRot = yRot;
        lerpXRot = xRot;
        lerpSteps = 10;
    }

    @Override
    public double lerpTargetX() {
        return lerpSteps > 0 ? lerpX : getX();
    }

    @Override
    public double lerpTargetY() {
        return lerpSteps > 0 ? lerpY : getY();
    }

    @Override
    public double lerpTargetZ() {
        return lerpSteps > 0 ? lerpZ : getZ();
    }

    @Override
    public float lerpTargetXRot() {
        return lerpSteps > 0 ? (float) lerpXRot : getXRot();
    }

    @Override
    public float lerpTargetYRot() {
        return lerpSteps > 0 ? (float) lerpYRot : getYRot();
    }

    /** Física com piloto (roda no cliente dele). */
    private void control(Player rider) {
        boolean fresh = level().getGameTime() - inputTime <= 2;
        double fwd = fresh ? inForward : 0.0;
        double strafe = fresh ? inStrafe : 0.0;
        boolean up = fresh && inUp;
        boolean down = fresh && inDown;
        if (fwd < 0.0) fwd *= 0.4;
        boolean input = fwd != 0.0 || strafe != 0.0;

        // Giro suave: rápido quando a diferença é grande, freando ao chegar no rumo (sem "estalo" no fim da curva)
        float diff = Mth.wrapDegrees(rider.getYRot() - getYRot());
        setYRot(getYRot() + Mth.clamp(diff * 0.3F, -kind.turnRate, kind.turnRate));
        boolean powered = isPowered();
        int upg = upgrades();
        double max = kind.speed * VehicleUpgrades.speedMult(upg) * (powered ? 1.0 : 0.15) * (healthFraction() < CRITICAL ? 0.7 : 1.0);
        // aceleração suave; sem tecla, freia um pouco mais rápido do que acelera
        double accel = kind.accel * VehicleUpgrades.speedMult(upg) * (input ? 1.0 : 1.6);
        float yaw = getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw);
        double fz = Mth.cos(yaw);
        // Alvo horizontal: frente + um pouco de lado (esquerda de quem olha = +X quando a frente é +Z)
        double wx = (fx * fwd + fz * strafe * 0.5) * max;
        double wz = (fz * fwd - fx * strafe * 0.5) * max;
        Vec3 mv = getDeltaMovement();
        double vx;
        double vy;
        double vz;
        switch (kind.mode) {
            case FLY, HOVER -> {
                double wy = 0.0;
                if (up) wy += kind.climb;
                if (down) wy -= kind.climb;
                if (kind == VehicleKind.BROOM && fwd > 0.0) {
                    // A vassoura vai para onde você olha: o olhar para cima/baixo vira subida/descida
                    float pitch = rider.getXRot() * Mth.DEG_TO_RAD;
                    wy += -Mth.sin(pitch) * max * fwd * 0.9;
                    double flat = Mth.cos(pitch);
                    wx *= flat;
                    wz *= flat;
                }
                if (kind.mode == VehicleKind.Mode.HOVER) {
                    double h = heightAbove(VehicleKind.HOVER_MAX + 3.0);
                    if (h > VehicleKind.HOVER_MAX) wy = Math.min(wy, -0.12);
                    // Acompanha o relevo: perto demais do chão sobe sozinho (a não ser que o piloto queira pousar)
                    else if (h < 1.2 && !down && input) wy = Math.max(wy, (1.2 - h) * 0.3);
                }
                if (!powered) wy = -0.1;
                vx = Mth.lerp(accel, mv.x, wx);
                vz = Mth.lerp(accel, mv.z, wz);
                vy = Mth.lerp(0.2, mv.y, wy);
                if (onGround() && vy < 0.0) vy = 0.0;
                // Tapete encostado numa parede baixa: sobe por cima em vez de ficar raspando
                if (kind.mode == VehicleKind.Mode.HOVER && horizontalCollision && input && powered && roomToHop(wx, wz)) {
                    vy = Math.max(vy, 0.25);
                }
            }
            case WATER -> {
                double fluid = getFluidHeight(FluidTags.WATER);
                if (fluid > 0.0) {
                    vx = Mth.lerp(accel, mv.x, wx);
                    vz = Mth.lerp(accel, mv.z, wz);
                    vy = Mth.lerp(0.4, mv.y, Mth.clamp((fluid - WATERLINE) * 0.35, -0.2, 0.25));
                    // cais/margem de 1 bloco: um pulinho para subir (barco encalhado anda devagar até voltar à água)
                    if (horizontalCollision && input && roomToHop(wx, wz)) vy = Math.max(vy, 0.48);
                } else {
                    // Fora d'água o barco só se arrasta
                    boolean ground = onGround();
                    vx = ground ? Mth.lerp(0.3, mv.x, wx * 0.1) : mv.x * 0.98;
                    vz = ground ? Mth.lerp(0.3, mv.z, wz * 0.1) : mv.z * 0.98;
                    vy = mv.y - 0.06;
                }
            }
            default -> {
                boolean wet = isInWater();
                double grip = wet ? 0.35 : 1.0;
                if (onGround() || wet) {
                    vx = Mth.lerp(accel, mv.x, wx * grip);
                    vz = Mth.lerp(accel, mv.z, wz * grip);
                } else {
                    vx = mv.x * 0.97;
                    vz = mv.z * 0.97;
                }
                vy = wet ? Math.max(mv.y - 0.02, -0.08) : (mv.y - 0.08) * 0.98;
                // degrau que o passo automático não pegou (saindo da água, meia-laje + bloco): pulinho
                if (horizontalCollision && input && (onGround() || wet) && roomToHop(wx, wz)) vy = Math.max(vy, wet ? 0.36 : 0.42);
            }
        }
        setDeltaMovement(vx, vy, vz);
    }

    /** Há espaço livre 1 bloco acima, um pouco à frente (degrau de 1 bloco, não muro/cerca)? */
    private boolean roomToHop(double wx, double wz) {
        double len = Math.sqrt(wx * wx + wz * wz);
        if (len < 1.0E-4) return false;
        double sx = wx / len * 0.6, sz = wz / len * 0.6;
        return level().noCollision(this, getBoundingBox().move(sx, 1.05, sz)) && level().noCollision(this, getBoundingBox().move(0, 1.05, 0));
    }

    /** Física sem piloto (servidor): os que voam pousam devagar, o barco boia, a carroça cai e para. */
    private void drift() {
        Vec3 mv = getDeltaMovement();
        double vx = mv.x * 0.8;
        double vz = mv.z * 0.8;
        double vy;
        switch (kind.mode) {
            case FLY, HOVER -> vy = Mth.lerp(0.2, mv.y, -0.09);
            case WATER -> {
                double fluid = getFluidHeight(FluidTags.WATER);
                vy = fluid > 0.0 ? Mth.lerp(0.4, mv.y, Mth.clamp((fluid - WATERLINE) * 0.35, -0.2, 0.25)) : mv.y - 0.06;
            }
            default -> vy = isInWater() ? Math.max(mv.y - 0.02, -0.08) : (mv.y - 0.08) * 0.98;
        }
        if (Math.abs(vx) < 0.003) vx = 0.0;
        if (Math.abs(vz) < 0.003) vz = 0.0;
        setDeltaMovement(vx, vy, vz);
    }

    // =====================================================================================
    //  Efeitos (cliente): rastro, faróis à noite, fumaça de avaria e o som do motor
    // =====================================================================================

    private void clientFx(double speed) {
        boolean manned = isVehicle();
        boolean powered = isPowered();
        if (moving && powered && manned && tickCount % 4 == 0) trailParticle();
        // faróis: com o veículo tripulado e ligado, no escuro (noite, caverna), duas luzinhas na frente
        if (manned && powered && tickCount % 3 == 0 && level().getMaxLocalRawBrightness(blockPosition().above()) < 8) {
            Vec3 fwd = Vec3.directionFromRotation(0.0F, getYRot());
            Vec3 side = new Vec3(-fwd.z, 0.0, fwd.x).scale(kind.width * 0.32);
            Vec3 lamp = position().add(fwd.scale(kind.width * 0.55 + 0.1)).add(0.0, kind.height * 0.55, 0.0);
            Vec3 p = (tickCount / 3) % 2 == 0 ? lamp.add(side) : lamp.subtract(side);
            level().addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, fwd.x * 0.03, 0.0, fwd.z * 0.03);
        }
        // avaria: fumaça cinza abaixo da metade da vida, preta e grossa no fim
        float hp = healthFraction();
        if (hp < 0.5F && tickCount % (hp < CRITICAL ? 3 : 8) == 0) {
            level().addParticle(hp < CRITICAL ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SMOKE,
                    getX() + (random.nextDouble() - 0.5) * kind.width * 0.6, getY() + kind.height * 0.7,
                    getZ() + (random.nextDouble() - 0.5) * kind.width * 0.6, 0.0, 0.03, 0.0);
        }
        // som do motor: baixo, só andando, cada um no seu ritmo (som local: nada pela rede)
        if (moving && powered && manned) {
            int every = switch (kind.mode) {
                case GROUND -> kind == VehicleKind.CART ? Math.max(6, 14 - (int) (speed * 20)) : 40;
                case WATER -> 18;
                default -> 50;
            };
            if (tickCount % every == 0) {
                SoundEvent s = switch (kind.mode) {
                    case GROUND -> kind == VehicleKind.CART ? SoundEvents.IRON_GOLEM_STEP : SoundEvents.BEACON_AMBIENT;
                    case WATER -> SoundEvents.BOAT_PADDLE_WATER;
                    default -> SoundEvents.BEACON_AMBIENT;
                };
                float vol = kind.mode == VehicleKind.Mode.GROUND && kind == VehicleKind.CART ? 0.35F : 0.3F;
                float pitch = 1.1F + (float) Math.min(0.6, speed * 0.8);
                level().playLocalSound(getX(), getY(), getZ(), s, SoundSource.NEUTRAL, vol, pitch, false);
            }
        }
    }

    private void trailParticle() {
        Vec3 back = Vec3.directionFromRotation(0.0F, getYRot()).scale(-kind.width * 0.5);
        double x = getX() + back.x + (random.nextDouble() - 0.5) * 0.3;
        double y = getY() + 0.25;
        double z = getZ() + back.z + (random.nextDouble() - 0.5) * 0.3;
        switch (kind.mode) {
            case WATER -> level().addParticle(ParticleTypes.BUBBLE_POP, x, y, z, 0.0, 0.02, 0.0);
            case GROUND -> {
                if (kind == VehicleKind.ROVER) level().addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0, 0.02, 0.0);
            }
            default -> level().addParticle(ParticleTypes.END_ROD, x, y, z, 0.0, -0.01, 0.0);
        }
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == EVENT_HURT) {
            shake = 8;
            return;
        }
        super.handleEntityEvent(id);
    }

    // =====================================================================================
    //  Energia (servidor)
    // =====================================================================================

    private void serverEnergyTick(@Nullable LivingEntity driver) {
        if (!(driver instanceof ServerPlayer sp)) {
            lastFuelPos = null;
            return;
        }
        if (lastFuelPos != null && tickCount % 20 != 0) return;
        boolean first = lastFuelPos == null;
        // Em uso = andou no último segundo, ou está sustentado no ar (pousado/parado não gasta nada)
        boolean active = !first && (position().distanceToSqr(lastFuelPos) > 0.25 || kind.flies() && heightAbove(1.0) > 0.3);
        lastFuelPos = position();
        byte before = energySource();
        byte now;
        if (sp.getAbilities().instabuild) {
            now = SRC_FREE;
        } else {
            MagicData d = MagicApi.get(sp);
            // Depois de sair da mana (tanque ou sem energia), só volta a usá-la com folga: senão a fonte ficaria
            // trocando a cada segundo enquanto a mana regenera aos poucos
            float keep = d.maxMana() * (first || before == SRC_MANA || before == SRC_FREE ? MANA_RESERVE : MANA_RESUME);
            if (d.mana() - kind.manaPerSecond >= keep) {
                now = SRC_MANA;
                if (active) d.consumeMana(kind.manaPerSecond);
            } else {
                // tanque no fim: puxa um Cristal de Mana do baú do veículo, se houver (nada de parar no meio do nada)
                if (getFuel() < kind.burnPerSecond) refuelFromCargo();
                int fuel = getFuel();
                if (fuel > 0) {
                    now = SRC_TANK;
                    if (active) setFuel(fuel - kind.burnPerSecond);
                } else {
                    now = SRC_NONE;
                }
            }
        }
        if (now != before) entityData.set(DATA_SOURCE, now);
        if (now == SRC_NONE && (before != SRC_NONE || first)) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.vehicle_no_energy",
                    MagicApi.resourceName(sp)).withStyle(ChatFormatting.RED), true);
        }
    }

    private void refuelFromCargo() {
        int slots = cargoSlots();
        for (int i = 0; i < slots; i++) {
            ItemStack st = cargo.getItem(i);
            if (st.is(CoreRegistries.MANA_CRYSTAL.get())) {
                st.shrink(1);
                cargo.setChanged();
                setFuel(getFuel() + CRYSTAL_CHARGE);
                playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.3F);
                return;
            }
        }
    }

    // =====================================================================================
    //  Caderno do Mecânico (servidor)
    // =====================================================================================

    /** A cada segundo: confere se esta cópia ainda é a válida e, de vez em quando, anota onde o veículo está. */
    private void registryTick(ServerLevel sl) {
        if (owner == null || isRemoved()) return;
        VehicleRegistry reg = VehicleRegistry.get(sl.getServer());
        if (vehicleId == null) {
            // veículo de antes da onda 10: ganha identidade agora
            vehicleId = UUID.randomUUID();
            reg.out(sl.getServer(), this);
            return;
        }
        VehicleRegistry.Entry e = reg.get(vehicleId);
        if (e == null) {
            reg.out(sl.getServer(), this);
            return;
        }
        boolean stale = e.gen > gen || e.state != VehicleRegistry.OUT || e.entity != null && !e.entity.equals(getUUID());
        if (stale) {
            // o Mecânico resgatou este veículo (ou outra cópia foi colocada): esta some, a carga fica no chão daqui
            ejectPassengers();
            discard();
            return;
        }
        if (tickCount % 100 == 7) reg.touch(sl.getServer(), this);
    }

    /** O Mecânico resgatou o veículo carregado: a carga vai para quem pediu e esta cópia some sem derrubar nada. */
    public void retireTo(Player player) {
        for (int i = 0; i < cargo.getContainerSize(); i++) {
            ItemStack st = cargo.removeItemNoUpdate(i);
            if (!st.isEmpty() && !player.getInventory().add(st)) player.drop(st, false);
        }
        cargoHandled = true;
        ejectPassengers();
        discard();
    }

    // =====================================================================================
    //  Passageiros
    // =====================================================================================

    /** Só o dono pilota (veículo sem dono: quem subir primeiro; criativo: para testes). */
    @Override
    @Nullable
    public LivingEntity getControllingPassenger() {
        List<Entity> ps = getPassengers();
        if (ps.isEmpty()) return null;
        for (Entity e : ps) {
            if (e instanceof Player p && owner != null && owner.equals(p.getUUID())) return p;
        }
        Entity first = ps.get(0);
        if (first instanceof Player p && (owner == null || p.getAbilities().instabuild)) return p;
        return null;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < kind.seats;
    }

    /** Assento 0 = piloto; os outros vão para os assentos de trás na ordem em que subiram. */
    private int seatIndex(Entity passenger) {
        Entity pilot = getControllingPassenger();
        if (passenger == pilot) return 0;
        int i = 1;
        for (Entity e : getPassengers()) {
            if (e == pilot) continue;
            if (e == passenger) return Math.min(i, kind.seatOffsets.size() - 1);
            i++;
        }
        return 0;
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
        return kind.seatOffsets.get(Math.max(0, seatIndex(passenger))).yRot(-getYRot() * Mth.DEG_TO_RAD);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        boolean client = level().isClientSide;
        if (stack.is(CoreRegistries.MANA_CRYSTAL.get())) {
            if (!client) {
                if (getFuel() >= MAX_FUEL) {
                    player.displayClientMessage(Component.translatable("message.isekai_mahou.vehicle_tank_full"), true);
                } else {
                    setFuel(getFuel() + CRYSTAL_CHARGE);
                    stack.consume(1, player);
                    playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.2F);
                    player.displayClientMessage(Component.translatable("message.isekai_mahou.vehicle_charged",
                            getFuel() * 100 / MAX_FUEL).withStyle(ChatFormatting.AQUA), true);
                }
            }
            return InteractionResult.sidedSuccess(client);
        }
        // Kit de Reparos: qualquer um pode ajudar a consertar (não é estrago, é ajuda)
        if (stack.is(VehiclesRegistries.REPAIR_KIT.get())) {
            if (!client) {
                if (healthFraction() >= 0.999F) {
                    player.displayClientMessage(Component.translatable("message.isekai_mahou.vehicle_whole"), true);
                } else {
                    setHealth(getHealth() + getMaxHealth() * KIT_REPAIR);
                    stack.consume(1, player);
                    playSound(SoundEvents.ANVIL_USE, 0.6F, 1.4F);
                    if (level() instanceof ServerLevel sl) {
                        sl.sendParticles(ParticleTypes.WAX_OFF, getX(), getY() + kind.height * 0.6, getZ(), 12, kind.width * 0.3, 0.3,
                                kind.width * 0.3, 0.05);
                    }
                    player.displayClientMessage(Component.translatable("message.isekai_mahou.vehicle_repaired",
                            Math.round(healthFraction() * 100)).withStyle(ChatFormatting.GREEN), true);
                }
            }
            return InteractionResult.sidedSuccess(client);
        }
        // Peça de melhoria: só o dono
        if (stack.getItem() instanceof VehiclePartItem part) {
            if (!client) applyPart(player, stack, part.part());
            return InteractionResult.sidedSuccess(client);
        }
        if (player.isSecondaryUseActive()) {
            if (cargoSlots() <= 0) return InteractionResult.PASS;
            if (!client) {
                if (!isOwner(player)) {
                    player.displayClientMessage(Component.translatable("message.isekai_mahou.vehicle_not_owner"), true);
                } else {
                    int rows = Math.max(1, Math.min(6, (cargoSlots() + 8) / 9));
                    player.openMenu(new SimpleMenuProvider((id, inv, p) -> new ChestMenu(menuType(rows), id, inv, cargo, rows),
                            getDisplayName()));
                }
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (!client) {
            if (getPassengers().isEmpty() && !isOwner(player)) {
                player.displayClientMessage(Component.translatable("message.isekai_mahou.vehicle_not_owner"), true);
            } else if (getPassengers().size() < kind.seats && player.startRiding(this) && player instanceof ServerPlayer sp) {
                boarded(sp);
            }
        }
        return InteractionResult.sidedSuccess(client);
    }

    private static MenuType<ChestMenu> menuType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3;
            case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5;
            default -> MenuType.GENERIC_9x6;
        };
    }

    private void applyPart(Player player, ItemStack stack, VehicleUpgrades.Part part) {
        if (!isOwner(player)) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.vehicle_not_owner"), true);
            return;
        }
        int upg = upgrades();
        int lv = VehicleUpgrades.level(upg, part);
        if (lv >= VehicleUpgrades.MAX) {
            player.displayClientMessage(Component.translatable("message.isekai_mahou.vehicle_part_max",
                    Component.translatable("item.isekai_mahou." + part.itemId())).withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        setUpgrades(VehicleUpgrades.with(upg, part, lv + 1));
        stack.consume(1, player);
        playSound(SoundEvents.SMITHING_TABLE_USE, 0.9F, 1.1F);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.ENCHANT, getX(), getY() + kind.height * 0.6, getZ(), 30, kind.width * 0.4, 0.4,
                    kind.width * 0.4, 0.4);
            VehicleRegistry.get(sl.getServer()).touch(sl.getServer(), this);
        }
        player.displayClientMessage(Component.translatable("message.isekai_mahou.vehicle_part_done",
                Component.translatable("item.isekai_mahou." + part.itemId()), lv + 1, VehicleUpgrades.MAX)
                .withStyle(ChatFormatting.AQUA), true);
    }

    private void boarded(ServerPlayer sp) {
        if (getControllingPassenger() != sp) return;
        lastFuelPos = null;
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.vehicle_controls." + kind.modeId(),
                Component.keybind("key.jump"), Component.keybind("key.sneak")).withStyle(ChatFormatting.AQUA), true);
        Hints.show(sp, "vehicles.energy", MagicApi.resourceName(sp));
        if (MagicApi.get(sp).counter("feat.vehicle_ride") == 0) MagicApi.addCounter(sp, "feat.vehicle_ride", 1);
        String key = "feat.vehicle." + kind.id;
        if (MagicApi.get(sp).counter(key) == 0) MagicApi.addCounter(sp, key, 1);
    }

    // =====================================================================================
    //  Guardar o veículo / dano
    // =====================================================================================

    /** Agachado + bater (o dono) guarda o veículo; qualquer outro golpe de jogador não faz nada. */
    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (!level().isClientSide && attacker instanceof Player p && isAlive()) {
            if (!isOwner(p)) {
                p.displayClientMessage(Component.translatable("message.isekai_mahou.vehicle_not_owner"), true);
            } else if (p.getAbilities().instabuild && !p.isShiftKeyDown()) {
                if (level() instanceof ServerLevel sl) VehicleRegistry.get(sl.getServer()).forget(vehicleId);
                discard();
            } else if (p.isShiftKeyDown()) {
                collect(p);
            } else {
                p.displayClientMessage(Component.translatable("message.isekai_mahou.vehicle_collect_hint",
                        Component.keybind("key.sneak")), true);
            }
        }
        return true;
    }

    private void collect(Player player) {
        if (vehicleId == null) vehicleId = UUID.randomUUID();
        VehicleStacks.Data d = data();
        ItemStack stack = VehicleStacks.write(new ItemStack(VehiclesRegistries.item(kind)), d);
        if (level() instanceof ServerLevel sl && owner != null) VehicleRegistry.get(sl.getServer()).packed(sl.getServer(), owner, kind, d);
        ejectPassengers();
        playSound(SoundEvents.ITEM_PICKUP, 0.8F, 0.8F);
        if (!player.getInventory().add(stack)) spawnAtLocation(stack);
        discard();
    }

    /**
     * Dano (onda 10): só o que vem do mundo — monstros, flechas de monstros, explosões, raio. Jogadores nunca estragam
     * veículos; fogo, lava, queda e sufocar também não (eles já não queimam nem caem com força).
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved() || isInvulnerableTo(source) || amount <= 0F) return false;
        Entity by = source.getEntity();
        if (by instanceof Player || source.getDirectEntity() instanceof Player) return false;
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypeTags.IS_DROWNING)
                || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CACTUS) || source.is(DamageTypes.SWEET_BERRY_BUSH)
                || source.is(DamageTypes.FLY_INTO_WALL) || source.is(DamageTypes.STALAGMITE) || source.is(DamageTypes.FALLING_STALACTITE)) {
            return false;
        }
        if (invulnerable > 0 && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)) return false;
        invulnerable = 10;
        float dmg = amount * VehicleUpgrades.damageMult(upgrades());
        setHealth(getHealth() - dmg);
        level().broadcastEntityEvent(this, EVENT_HURT);
        playSound(SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 0.5F, 1.5F + random.nextFloat() * 0.3F);
        if (getHealth() <= 0F) {
            wreck();
        } else if (healthFraction() < CRITICAL && getControllingPassenger() instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.vehicle_critical").withStyle(ChatFormatting.RED), true);
        }
        return true;
    }

    /** Destruído: todos descem, a carga cai, nenhum bloco é tocado; o Mecânico pode reconstruí-lo. */
    private void wreck() {
        if (!(level() instanceof ServerLevel sl)) return;
        ejectPassengers();
        sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + kind.height * 0.5, getZ(), 3, kind.width * 0.3, 0.2, kind.width * 0.3, 0.0);
        sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX(), getY() + kind.height * 0.5, getZ(), 20, kind.width * 0.4, 0.3, kind.width * 0.4, 0.03);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.NEUTRAL, 0.6F, 1.5F);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_BREAK, SoundSource.NEUTRAL, 1.0F, 0.6F);
        if (owner != null) {
            if (vehicleId == null) vehicleId = UUID.randomUUID();
            VehicleRegistry.get(sl.getServer()).wrecked(sl.getServer(), this);
            ServerPlayer o = sl.getServer().getPlayerList().getPlayer(owner);
            if (o != null) {
                o.sendSystemMessage(Component.translatable("message.isekai_mahou.vehicle_wrecked", getDisplayName(),
                        blockPosition().getX(), blockPosition().getY(), blockPosition().getZ()).withStyle(ChatFormatting.RED));
                Hints.show(o, "vehicles.wrecked");
            }
        }
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        // A carga nunca some junto com o veículo (guardado, destruído, /kill, resgatado longe)
        if (!level().isClientSide && reason.shouldDestroy() && !cargoHandled) Containers.dropContents(level(), this, cargo);
        super.remove(reason);
    }

    // =====================================================================================
    //  Regras de entidade
    // =====================================================================================

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Os que voam cuidam da própria queda (e o servidor não acusa "veículo flutuando" de quem pilota). */
    @Override
    public boolean isNoGravity() {
        return kind.flies();
    }

    @Override
    public float maxUpStep() {
        return kind.mode == VehicleKind.Mode.GROUND ? 1.1F : 0.6F;
    }

    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state,
                                   net.minecraft.core.BlockPos pos) {
        resetFallDistance();
    }

    @Override
    public boolean fireImmune() {
        return true;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(VehiclesRegistries.item(kind));
    }

    /** O balão do dirigível e as bordas do tapete passam da caixa de colisão. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(1.5, 2.5, 1.5);
    }

    // =====================================================================================
    //  Animações
    // =====================================================================================

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6,
                state -> state.setAndContinue(moving && isVehicle() ? animMove : animIdle)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
