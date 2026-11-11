package com.renanpontes.isekaimahou.feature.class_gambler.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.GbRegistries;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.JackpotService;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckBand;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckService;
import com.renanpontes.isekaimahou.feature.class_gambler.rank.HouseRanks;
import com.renanpontes.isekaimahou.glyph.LawGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.core.MagicApi;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * EXPANSÃO DE DOMÍNIO: MORTE OCIOSA (forma G do rank V do Apostador; homenagem ao domínio de pachinko do apostador mais
 * sortudo do anime). Um salão de pachinko em neon (o modelo do Cassino com outra textura) se fecha em volta do
 * conjurador; registrado no núcleo como domínio com Acerto Garantido + as Leis da magia.
 * <ul>
 *   <li>A cada {@link #ROUND} ticks começa uma RODADA: a sorte é rolada (HUD) e todos os inimigos dentro recebem os
 *   efeitos com esse poder; depois vem o "Reach" — Verde, Vermelho, Ouro ou Arco-Íris (a Sorte e as rodadas puxam
 *   para as cores boas) — e cada cor tem a sua chance de JACKPOT (6% / 22% / 50% / 100%, +3% por rodada).</li>
 *   <li>JACKPOT: o domínio se desfaz em confete e começa o <b>Jackpot Imortal</b> ({@link JackpotService#startImmortal}):
 *   251 ticks (os "4:11" viraram ticks) + 2 s por rank, recurso infinito, regeneração total e golpes fatais não matam.</li>
 *   <li>Quem aprendeu por Lição tem metade da chance de Jackpot.</li>
 * </ul>
 * Um por conjurador. Não é salvo no mundo.
 */
public class IdleDeathDomainEntity extends Entity implements GeoEntity {
    public static final int ROUND = 40;
    /** Chance de Jackpot por cor do Reach (1..4). */
    public static final float[] REACH_JACKPOT = {0F, 0.06F, 0.22F, 0.50F, 1.0F};
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("animation.casino_domain.spin");
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(IdleDeathDomainEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_LIFE = SynchedEntityData.defineId(IdleDeathDomainEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_REACH = SynchedEntityData.defineId(IdleDeathDomainEntity.class, EntityDataSerializers.INT);
    private static final Map<UUID, IdleDeathDomainEntity> BY_OWNER = new HashMap<>();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private SpellContext ctx;
    @Nullable
    private UUID ownerId;
    @Nullable
    private Domains.Info info;
    private Set<ResourceLocation> laws = Set.of();
    private int rounds;

    public IdleDeathDomainEntity(EntityType<? extends IdleDeathDomainEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    public static IdleDeathDomainEntity open(SpellContext ctx, LivingEntity owner, float radius, int life) {
        IdleDeathDomainEntity old = BY_OWNER.get(owner.getUUID());
        if (old != null && !old.isRemoved()) old.close(false);
        IdleDeathDomainEntity e = new IdleDeathDomainEntity(GbRegistries.IDLE_DEATH.get(), ctx.level());
        e.setPos(owner.getX(), owner.getY(), owner.getZ());
        e.ctx = ctx;
        e.ownerId = owner.getUUID();
        e.entityData.set(DATA_RADIUS, radius);
        e.entityData.set(DATA_LIFE, life);
        Set<ResourceLocation> l = new HashSet<>();
        l.add(Domains.SURE_HIT);
        for (LawGlyph law : ctx.spell().laws()) l.add(law.id());
        e.laws = l;
        BY_OWNER.put(owner.getUUID(), e);
        return e;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 11F);
        builder.define(DATA_LIFE, 300);
        builder.define(DATA_REACH, 0);
    }

    public float radius() {
        return entityData.get(DATA_RADIUS);
    }

    public int life() {
        return entityData.get(DATA_LIFE);
    }

    /** Cor do último Reach (0 = nenhum ainda; o renderer tinge o salão). */
    public int reach() {
        return entityData.get(DATA_REACH);
    }

    public float openness(float partialTick) {
        float t = tickCount + partialTick;
        return Math.max(0F, Math.min(1F, Math.min(t / 15F, (life() - t) / 15F)));
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientFx();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        LivingEntity owner = ownerId != null && sl.getEntity(ownerId) instanceof LivingEntity le ? le : null;
        if (ctx == null || owner == null || !owner.isAlive() || owner.level() != sl || tickCount >= life()) {
            close(false);
            return;
        }
        float r = radius();
        Vec3 c = position();
        if (info == null) {
            info = new Domains.Info(getId(), sl, owner, c.add(0, 1, 0), r, true, laws, ctx.element(), ctx, sl.getGameTime(), life());
            Domains.opened(info);
            GbFx.sound(sl, c, SoundEvents.END_PORTAL_SPAWN, 0.8F, 1.3F);
            GbFx.sound(sl, c, SoundEvents.BELL_BLOCK, 1.0F, 0.7F);
            for (int k = 0; k < 4; k++) GbFx.groundRing(sl, c, r * (0.25 + k * 0.25), k % 2 == 0 ? 0x3AE07A : GbFx.PINK, 48);
        }
        Domains.ticked(info, tickCount);
        int reach = reach();
        if (tickCount % 5 == 0) GbFx.groundRing(sl, c, r, reach > 0 ? GbFx.reachColor(reach, tickCount) : 0x3AE07A, 56);
        if (tickCount % 10 == 0) pillars(sl, c, r, reach);
        if (tickCount > 0 && tickCount % ROUND == 0) round(sl, owner, r);
    }

    /** Quatro colunas de luz (os "pachinkos") na borda, na cor do Reach. */
    private void pillars(ServerLevel sl, Vec3 c, float r, int reach) {
        int rgb = reach > 0 ? GbFx.reachColor(reach, tickCount) : 0x9AF0C8;
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 2 * k + tickCount * 0.01;
            double x = c.x + Math.cos(a) * r * 0.85, z = c.z + Math.sin(a) * r * 0.85;
            for (int y = 0; y < 4; y++) sl.sendParticles(GbFx.dust(rgb, 1.3F), x, c.y + 0.4 + y * 0.7, z, 1, 0.05, 0.1, 0.05, 0);
        }
    }

    private void round(ServerLevel sl, LivingEntity owner, float r) {
        rounds++;
        LuckService.Roll roll = LuckService.rollAndShow(owner, LuckService.options(ctx.spell(), owner), LuckService.Kind.SLOT, 0);
        SpellContext hit = ctx.copy(ctx.powerMult() * roll.mult());
        for (LivingEntity e : info.reachableEnemies()) {
            e.invulnerableTime = 0;
            hit.resolveOnEntity(e, this);
            sl.sendParticles(GbFx.dust(roll.band().rgb, 1.2F), e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 6, 0.2, 0.1, 0.2, 0);
        }
        if (owner instanceof ServerPlayer sp && LuckService.isGambler(sp)) LuckService.addFever(sp, 6);
        // o Reach
        int luck = LuckService.luck(owner);
        float[] w = {0F, Math.max(10F, 60F - 3F * luck - 2F * rounds), 25F + luck, 12F + 1.5F * luck + rounds, 3F + 0.5F * luck + 0.5F * rounds};
        float total = w[1] + w[2] + w[3] + w[4];
        float pick = sl.random.nextFloat() * total;
        int reach = 1;
        for (int k = 1; k <= 4; k++) {
            pick -= w[k];
            if (pick < 0) {
                reach = k;
                break;
            }
        }
        entityData.set(DATA_REACH, reach);
        float chance = REACH_JACKPOT[reach] + 0.03F * rounds;
        if (!LuckService.isGambler(owner)) chance *= 0.5F;
        boolean jackpot = roll.band() == LuckBand.JACKPOT || sl.random.nextFloat() < chance;
        int rgb = GbFx.reachColor(reach, tickCount);
        GbFx.groundRing(sl, position(), r * 0.6, rgb, 40);
        sl.sendParticles(GbFx.dust(rgb, 1.6F), owner.getX(), owner.getY() + owner.getBbHeight() + 0.6, owner.getZ(), 16, 0.4, 0.2, 0.4, 0);
        GbFx.sound(sl, position(), SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 0.6F + reach * 0.3F);
        GbFx.sound(sl, position(), SoundEvents.NOTE_BLOCK_BIT.value(), 0.9F, 0.8F + reach * 0.25F);
        if (owner instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gambler.reach", reachName(reach),
                    Math.round(Math.min(1F, chance) * 100)).withStyle(reach >= 3 ? ChatFormatting.GOLD : ChatFormatting.GREEN), true);
        }
        if (jackpot) jackpot(sl, owner);
    }

    private static Component reachName(int reach) {
        return Component.translatable(switch (reach) {
            case 2 -> "message.isekai_mahou.gambler.reach_red";
            case 3 -> "message.isekai_mahou.gambler.reach_gold";
            case 4 -> "message.isekai_mahou.gambler.reach_rainbow";
            default -> "message.isekai_mahou.gambler.reach_green";
        });
    }

    /** JACKPOT: confete, letreiro e o Jackpot Imortal; o domínio se desfaz. */
    private void jackpot(ServerLevel sl, LivingEntity owner) {
        Vec3 c = owner.position().add(0, 1.2, 0);
        sl.sendParticles(ParticleTypes.FIREWORK, c.x, c.y + 1, c.z, 80, radius() * 0.3, 1.5, radius() * 0.3, 0.2);
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, c.x, c.y, c.z, 80, 1.0, 1.2, 1.0, 0.6);
        GbFx.coins(sl, c.add(0, 1, 0), 30);
        GbFx.sound(sl, c, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.2F, 1.0F);
        GbFx.sound(sl, c, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.2F, 0.9F);
        Announce.send(owner, Component.translatable("announce.isekai_mahou.gambler.immortal_jackpot"), 0x4AFFB4, Announce.STYLE_ULTIMATE);
        if (owner instanceof ServerPlayer sp) {
            JackpotService.startImmortal(sp);
            MagicApi.addCounter(sp, "gambler.immortal_jackpot", 1);
            if (HouseRanks.rank(MagicApi.get(sp)) >= 5) MagicApi.addCounter(sp, "gambler.big_win", 3);
        }
        close(true);
    }

    /** Fecha: avisa o núcleo e some. */
    public void close(boolean confetti) {
        if (isRemoved()) return;
        if (info != null) Domains.closed(info);
        info = null;
        if (ownerId != null && BY_OWNER.get(ownerId) == this) BY_OWNER.remove(ownerId);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(GbFx.dust(confetti ? GbFx.PINK : 0x3AE07A, 1.5F), getX(), getY() + 0.5, getZ(), 50, radius() * 0.4, 0.6, radius() * 0.4, 0);
            GbFx.sound(sl, position(), SoundEvents.BEACON_DEACTIVATE, 0.8F, 1.1F);
        }
        discard();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (info != null && !level().isClientSide) {
            Domains.closed(info);
            info = null;
        }
        if (ownerId != null && BY_OWNER.get(ownerId) == this) BY_OWNER.remove(ownerId);
        super.remove(reason);
    }

    private void clientFx() {
        float r = radius() * openness(0);
        if (r <= 0.5F) return;
        for (int i = 0; i < 2; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = Math.sqrt(random.nextDouble()) * r;
            level().addParticle(i == 0 ? ParticleTypes.NOTE : ParticleTypes.END_ROD, getX() + Math.cos(a) * d, getY() + 0.3,
                    getZ() + Math.sin(a) * d, i == 0 ? random.nextDouble() : 0, 0.05, 0);
        }
    }

    public static void forget(UUID owner) {
        IdleDeathDomainEntity e = BY_OWNER.remove(owner);
        if (e != null && !e.isRemoved()) e.close(false);
    }

    public static void clearAll() {
        BY_OWNER.clear();
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
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "spin", 0, state -> state.setAndContinue(SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
