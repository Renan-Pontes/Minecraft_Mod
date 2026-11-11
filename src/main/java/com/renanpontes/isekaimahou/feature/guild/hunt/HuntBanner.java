package com.renanpontes.isekaimahou.feature.guild.hunt;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.guild.GuildContent;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Estandarte da Guilda fincado no centro de uma Área de Caça (GeckoLib): mastro com o pano verde e dourado da Guilda,
 * lanternas, flâmula no topo e um círculo rúnico no chão. Em volta, partículas desenham a BORDA da área (raio
 * {@link HuntingGrounds#RADIUS}) e um facho de luz sobe do topo (visível de longe).
 * <ul>
 *   <li>Estados: calmo (esperando o caçador), batalha (ondas em andamento: borda vermelha, pano agitado) e recolher
 *       (área concluída/sumiu: o pano enrola e o estandarte afunda em faíscas douradas).</li>
 *   <li>Temporário: não é salvo no mundo. O {@link HuntingGrounds} recoloca quando o dono chega perto; a cada segundo ele
 *       confere se a área ainda existe e, se não, se recolhe sozinho.</li>
 *   <li>Clique direito: mostra a situação da área na barra de ação.</li>
 * </ul>
 */
public class HuntBanner extends Entity implements GeoEntity {
    public static final int CALM = 0, BATTLE = 1, FOLD = 2;
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(HuntBanner.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(HuntBanner.class, EntityDataSerializers.INT);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.hunt_banner.idle");
    private static final RawAnimation BATTLE_ANIM = RawAnimation.begin().thenLoop("animation.hunt_banner.battle");
    private static final RawAnimation FOLD_ANIM = RawAnimation.begin().thenPlayAndHold("animation.hunt_banner.fold");
    private static final int FOLD_TICKS = 40;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private String placeId = "";
    private int foldAge;
    /** Cliente: ticks desde que começou a recolher (os círculos do chão encolhem). */
    private int clientFold;

    public HuntBanner(EntityType<? extends HuntBanner> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Finca um estandarte no centro (pés no chão) de uma área. */
    @Nullable
    public static HuntBanner plant(ServerLevel level, HuntState h, Component name, int rgb) {
        HuntBanner b = GuildContent.HUNT_BANNER.get().create(level);
        if (b == null) return null;
        b.placeId = h.placeId;
        b.entityData.set(DATA_COLOR, rgb);
        b.entityData.set(DATA_STATE, h.phase == HuntState.FIGHT ? BATTLE : CALM);
        b.setCustomName(name);
        b.setCustomNameVisible(true);
        b.moveTo(h.center.getX() + 0.5, h.center.getY(), h.center.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
        if (!level.addFreshEntity(b)) return null;
        level.playSound(null, b.getX(), b.getY(), b.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 1.0F, 0.7F);
        level.sendParticles(ParticleTypes.CLOUD, b.getX(), b.getY() + 0.2, b.getZ(), 16, 0.8, 0.1, 0.8, 0.02);
        return b;
    }

    public String placeId() {
        return placeId;
    }

    public int bannerState() {
        return entityData.get(DATA_STATE);
    }

    public void setBannerState(int s) {
        if (bannerState() != s && bannerState() != FOLD) entityData.set(DATA_STATE, s);
    }

    /** Recolhe (pano enrola, afunda, some em ~2 s). */
    public void fold() {
        if (bannerState() == FOLD) return;
        entityData.set(DATA_STATE, FOLD);
        foldAge = 0;
        if (level() instanceof ServerLevel sl) {
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.NEUTRAL, 1.0F, 0.5F);
            sl.sendParticles(ParticleTypes.WAX_OFF, getX(), getY() + 2.5, getZ(), 24, 0.6, 1.4, 0.6, 0.05);
        }
    }

    public int clientFoldTicks() {
        return clientFold;
    }

    public int color() {
        return entityData.get(DATA_COLOR);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, CALM);
        builder.define(DATA_COLOR, 0x6BD66B);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        if (!(level() instanceof ServerLevel sl)) return;
        if (bannerState() == FOLD) {
            if (++foldAge % 4 == 0) {
                sl.sendParticles(ParticleTypes.WAX_ON, getX(), getY() + 1.5, getZ(), 4, 0.5, 1.0, 0.5, 0.02);
            }
            if (foldAge >= FOLD_TICKS) {
                sl.sendParticles(ParticleTypes.POOF, getX(), getY() + 1.0, getZ(), 18, 0.4, 0.8, 0.4, 0.02);
                discard();
            }
            return;
        }
        // A área sumiu (concluída, abandonada, vencida) ou o estandarte é órfão: recolhe.
        if (tickCount % 20 == 3 && !HuntingGrounds.isLive(sl.getServer(), placeId)) fold();
    }

    // =====================================================================================
    //  Partículas (só no cliente; orçamento fixo por tick)
    // =====================================================================================
    private void clientFx() {
        int state = bannerState();
        Level level = level();
        boolean reduced = MahouConfig.REDUCED_PARTICLES.get();
        int rgb = state == BATTLE ? 0xFF5B4A : color();
        Vector3f c = new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
        DustParticleOptions dust = new DustParticleOptions(c, state == BATTLE ? 1.6F : 1.3F);
        if (state == FOLD) {
            clientFold++;
            if (tickCount % 2 == 0) {
                level.addParticle(ParticleTypes.WAX_ON, getX() + (random.nextDouble() - 0.5), getY() + random.nextDouble() * 3.5,
                        getZ() + (random.nextDouble() - 0.5), 0, 0.05, 0);
            }
            return;
        }
        // Borda da área: pontos ao acaso no anel + 12 "postes" de luz que giram devagar.
        int ring = reduced ? 1 : (state == BATTLE ? 4 : 3);
        double r = HuntingGrounds.RADIUS;
        for (int i = 0; i < ring; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            groundParticle(dust, getX() + Math.cos(a) * r, getZ() + Math.sin(a) * r, 0.03);
        }
        if (tickCount % (reduced ? 6 : 2) == 0) {
            int post = (tickCount / 2) % 12;
            double a = post * Math.PI / 6 + tickCount * 0.002;
            groundParticle(ParticleTypes.END_ROD, getX() + Math.cos(a) * r, getZ() + Math.sin(a) * r, 0.12);
        }
        // Círculo rúnico em volta do mastro.
        double spin = tickCount * 0.08;
        for (int i = 0; i < (reduced ? 1 : 2); i++) {
            double a = spin + i * Math.PI;
            level.addParticle(dust, getX() + Math.cos(a) * 3.5, getY() + 0.15, getZ() + Math.sin(a) * 3.5, 0, 0.01, 0);
        }
        if (tickCount % 5 == 0) {
            level.addParticle(ParticleTypes.ENCHANT, getX() + (random.nextDouble() - 0.5) * 5, getY() + 0.3,
                    getZ() + (random.nextDouble() - 0.5) * 5, 0, 0.4, 0);
        }
        // Facho de luz do topo (visível de longe).
        if (tickCount % (reduced ? 6 : 3) == 0) {
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, getX(), getY() + 4.3, getZ(),
                    (random.nextDouble() - 0.5) * 0.01, 0.35, (random.nextDouble() - 0.5) * 0.01);
        }
        if (state == BATTLE && tickCount % 4 == 0) {
            level.addParticle(ParticleTypes.FLAME, getX() + 0.55, getY() + 3.0, getZ(), 0, 0.02, 0);
            level.addParticle(ParticleTypes.FLAME, getX() - 0.55, getY() + 3.0, getZ(), 0, 0.02, 0);
        }
    }

    private void groundParticle(net.minecraft.core.particles.ParticleOptions p, double x, double z, double vy) {
        int y = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        if (y <= level().getMinBuildHeight() || Math.abs(y - getY()) > 24) return;
        level().addParticle(p, x, y + 0.1, z, 0, vy, 0);
    }

    // =====================================================================================
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp && hand == InteractionHand.MAIN_HAND) {
            HuntingGrounds.describe(sp, placeId);
            sp.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.1F);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return bannerState() != FOLD;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    /** O círculo rúnico do chão passa bem da caixa do mastro: não sumir quando só ele está na tela. */
    @Override
    public net.minecraft.world.phys.AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(4.0, 0.5, 4.0);
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        placeId = tag.getString("Place");
        entityData.set(DATA_COLOR, tag.contains("Color") ? tag.getInt("Color") : 0x6BD66B);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Place", placeId);
        tag.putInt("Color", color());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, state -> state.setAndContinue(switch (bannerState()) {
            case BATTLE -> BATTLE_ANIM;
            case FOLD -> FOLD_ANIM;
            default -> IDLE;
        })));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
