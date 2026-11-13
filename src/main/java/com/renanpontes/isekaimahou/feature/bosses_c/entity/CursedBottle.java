package com.renanpontes.isekaimahou.feature.bosses_c.entity;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.boss.BossCurses;
import com.renanpontes.isekaimahou.feature.bosses_c.BossesCRegistry;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Maldição engarrafada da Bruxa Corrompida. Quatro maldições:
 * Podridão (veneno + fome), Malefício (corrompido + fraqueza), Treva (cegueira + lentidão) e Sapo (levitação + náusea).
 * Na fase 2 as de Podridão/Malefício deixam uma nuvem.
 * <p>
 * Arremesso em ARCO ({@link #lob}): a garrafa sobe e cai exatamente no ponto marcado no chão (telegrafia da bruxa) no
 * tick previsto — quem aplica a maldição é a marca ({@link #splash}), então o que o jogador vê no chão é exatamente
 * onde ela pega. Sob um teto o arco fica mais baixo ({@link #clearFlight}); se algo ainda cruzar o caminho, ela passa.
 */
public class CursedBottle extends ThrowableItemProjectile {
    /** Raio do estouro (o mesmo da marca no chão). */
    public static final float SPLASH_RADIUS = 3.0F;
    private static final double GRAVITY = 0.05;
    private static final double DRAG = 0.99;

    public enum Curse {
        ROT(0x4E9A06, 0x7CFF4A), HEX(0x6B2F8C, 0xB45CFF), GLOOM(0x1F1F3A, 0x6F7CFF), TOAD(0xB0E05A, 0xD8FF5A);

        /** Cor do líquido (garrafa e respingo). */
        public final int color;
        /** Cor (mais viva) da marca no chão. */
        public final int mark;

        Curse(int color, int mark) {
            this.color = color;
            this.mark = mark;
        }
    }

    private Curse curse = Curse.ROT;
    private boolean lingering;
    /** Arremesso em arco: estoura neste tick (0 = garrafa comum, estoura ao bater). */
    private int flight;

    public CursedBottle(EntityType<? extends CursedBottle> type, Level level) {
        super(type, level);
    }

    public static CursedBottle create(LivingEntity thrower, Curse curse, boolean lingering) {
        CursedBottle b = new CursedBottle(BossesCRegistry.CURSED_BOTTLE.get(), thrower.level());
        b.setOwner(thrower);
        b.setPos(thrower.getX(), thrower.getEyeY() - 0.1, thrower.getZ());
        b.curse = curse;
        b.lingering = lingering;
        ItemStack stack = new ItemStack(Items.SPLASH_POTION);
        stack.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.of(curse.color), List.of()));
        b.setItem(stack);
        return b;
    }

    /**
     * Garrafa em arco de {@code from} até {@code landing}, chegando em exatamente {@code ticks} ticks (física do
     * projétil: anda, depois velocidade ×0.99 e gravidade). Não acerta ninguém sozinha: só estoura no fim.
     */
    public static CursedBottle lob(LivingEntity thrower, Curse curse, Vec3 from, Vec3 landing, int ticks) {
        CursedBottle b = create(thrower, curse, false);
        b.setPos(from.x, from.y, from.z);
        b.flight = Math.max(1, ticks);
        b.setDeltaMovement(arcVelocity(from, landing, b.flight));
        return b;
    }

    /** Velocidade inicial que leva a garrafa de {@code from} a {@code landing} em exatamente {@code ticks} ticks. */
    private static Vec3 arcVelocity(Vec3 from, Vec3 landing, int ticks) {
        double s = (1 - Math.pow(DRAG, ticks)) / (1 - DRAG);
        Vec3 d = landing.subtract(from);
        double vy = (d.y + GRAVITY * (ticks - s) / (1 - DRAG)) / s;
        return new Vec3(d.x / s, vy, d.z / s);
    }

    /**
     * Tempo de voo para um arco que não bate no teto: começa em {@code ticks} (arco alto, aviso longo) e encurta —
     * arco mais baixo e rápido — enquanto a trajetória cruzar blocos, até {@code minTicks}. Dentro da cabana a
     * garrafa passa por baixo do telhado em vez de sumir nele. Sem arco livre (parede no meio), usa o mais baixo.
     */
    public static int clearFlight(Level level, Entity thrower, Vec3 from, Vec3 landing, int ticks, int minTicks) {
        for (int t = ticks; t > minTicks; t -= 2) {
            if (arcIsClear(level, thrower, from, landing, t)) return t;
        }
        return Math.min(ticks, minTicks);
    }

    private static boolean arcIsClear(Level level, Entity thrower, Vec3 from, Vec3 landing, int ticks) {
        Vec3 v = arcVelocity(from, landing, ticks);
        Vec3 p = from;
        // Os 2 últimos ticks descem até o chão marcado: encostar nele ali é o pouso, não um teto.
        for (int k = 0; k < ticks - 2; k++) {
            Vec3 next = p.add(v);
            if (level.clip(new ClipContext(p, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, thrower)).getType()
                    != HitResult.Type.MISS) {
                return false;
            }
            v = new Vec3(v.x * DRAG, v.y * DRAG - GRAVITY, v.z * DRAG);
            p = next;
        }
        return true;
    }

    @Override
    protected Item getDefaultItem() {
        return Items.SPLASH_POTION;
    }

    @Override
    protected double getDefaultGravity() {
        return GRAVITY;
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) return;
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) level().addParticle(ParticleTypes.WITCH, getX(), getY() + 0.1, getZ(), 0, 0, 0);
            return;
        }
        if (flight > 0 && tickCount >= flight) shatter((ServerLevel) level(), position());
    }

    /** Passa pelas aliadas da bruxa (as outras bruxas e os gatos) em vez de estourar nelas; a de arco não acerta ninguém. */
    @Override
    protected boolean canHitEntity(Entity target) {
        return flight <= 0 && super.canHitEntity(target) && !BossAllies.isAlly(getOwner(), target);
    }

    @Override
    protected void onHit(HitResult result) {
        // Em arco: atravessa telhados/galhos e só estoura no ponto marcado.
        if (flight > 0) return;
        super.onHit(result);
        if (!(level() instanceof ServerLevel sl)) return;
        Vec3 at = result.getLocation();
        LivingEntity owner = getOwner() instanceof LivingEntity le ? le : null;
        splash(sl, owner, curse, lingering, at, sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(3.5, 2.0, 3.5)), 0F);
        shatter(sl, at);
    }

    /** Estouro visual (vidro, respingo da cor da maldição) e some. */
    private void shatter(ServerLevel sl, Vec3 at) {
        sl.levelEvent(LevelEvent.PARTICLES_INSTANT_POTION_SPLASH, net.minecraft.core.BlockPos.containing(at), curse.color);
        sl.sendParticles(ParticleTypes.WITCH, at.x, at.y + 0.3, at.z, 20, 0.8, 0.3, 0.8, 0.05);
        sl.sendParticles(BossFx.dust(curse.mark, 1.4F), at.x, at.y + 0.3, at.z, 15, 0.8, 0.3, 0.8, 0);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.HOSTILE, 1.0F, 0.8F + random.nextFloat() * 0.2F);
        discard();
    }

    /**
     * Aplica a maldição em quem estiver na área (mais forte no centro), com dano mágico opcional, e deixa a nuvem
     * (fase 2, Podridão/Malefício). Ignora aliados da bruxa e jogadores no criativo.
     */
    public static void splash(ServerLevel sl, @Nullable LivingEntity owner, Curse curse, boolean lingering, Vec3 at,
                              List<LivingEntity> hits, float damage) {
        for (LivingEntity e : hits) {
            if (!e.isAlive() || BossAllies.isAlly(owner, e)) continue;
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            double d = Math.hypot(e.getX() - at.x, e.getZ() - at.z);
            if (d > SPLASH_RADIUS + 1.0) continue;
            if (damage > 0) e.hurt(owner != null ? sl.damageSources().indirectMagic(owner, owner) : sl.damageSources().magic(), damage);
            if (e.isAffectedByPotions()) applyCurse(e, curse, Math.max(0.35F, (float) (1.0 - d / (SPLASH_RADIUS + 1.0))), owner);
        }
        if (lingering && (curse == Curse.ROT || curse == Curse.HEX)) spawnCloud(sl, owner, curse, at);
    }

    private static void applyCurse(LivingEntity e, Curse curse, float k, @Nullable LivingEntity owner) {
        switch (curse) {
            case ROT -> {
                add(e, MobEffects.POISON, 120, 1, k, owner);
                add(e, MobEffects.HUNGER, 200, 1, k, owner);
            }
            case HEX -> {
                add(e, CoreRegistries.CORRUPTED, 120, 0, k, owner);
                add(e, MobEffects.WEAKNESS, 160, 0, k, owner);
                // ASSINATURA: Mente Invertida — frente vira trás, esquerda vira direita
                add(e, BossCurses.INVERTED, 90, 0, k, owner);
                tell(e, "message.isekai_mahou.corrupted_witch.inverted", 0xC77DFF);
            }
            case GLOOM -> {
                add(e, MobEffects.BLINDNESS, 70, 0, k, owner);
                add(e, MobEffects.MOVEMENT_SLOWDOWN, 100, 1, k, owner);
            }
            case TOAD -> {
                // ASSINATURA: Maldição do Sapo — encolhe, quase não causa dano, pula alto e coaxa
                add(e, BossCurses.FROG, 140, 0, k, owner);
                add(e, MobEffects.CONFUSION, 80, 0, k, owner);
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.45, 0));
                e.hurtMarked = true;
                if (e.level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.ITEM_SLIME, e.getX(), e.getY() + 0.5, e.getZ(), 20, 0.4, 0.5, 0.4, 0.1);
                    sl.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 0.5, e.getZ(), 12, 0.4, 0.5, 0.4, 0.05);
                    sl.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.FROG_LONG_JUMP, SoundSource.HOSTILE, 1.5F, 0.8F);
                }
                tell(e, "message.isekai_mahou.corrupted_witch.frogged", 0x9BE05A);
            }
        }
    }

    private static void tell(LivingEntity e, String key, int rgb) {
        if (e instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable(key).withColor(rgb), true);
    }

    private static void add(LivingEntity e, Holder<MobEffect> effect, int ticks, int amp, float k, @Nullable LivingEntity owner) {
        e.addEffect(new MobEffectInstance(effect, Math.max(20, (int) (ticks * k)), amp), owner);
    }

    private static void spawnCloud(ServerLevel sl, @Nullable LivingEntity owner, Curse curse, Vec3 at) {
        AreaEffectCloud cloud = new AreaEffectCloud(sl, at.x, at.y, at.z);
        if (owner != null) cloud.setOwner(owner);
        cloud.setRadius(2.5F);
        cloud.setRadiusOnUse(-0.3F);
        cloud.setWaitTime(10);
        cloud.setDuration(120);
        cloud.setRadiusPerTick(-cloud.getRadius() / cloud.getDuration());
        cloud.setParticle(ParticleTypes.WITCH);
        cloud.addEffect(curse == Curse.ROT ? new MobEffectInstance(MobEffects.POISON, 60, 0)
                : new MobEffectInstance(CoreRegistries.CORRUPTED, 60, 0));
        sl.addFreshEntity(cloud);
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Curse", curse.ordinal());
        tag.putBoolean("Lingering", lingering);
        tag.putInt("Flight", flight);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        int i = tag.getInt("Curse");
        curse = i >= 0 && i < Curse.values().length ? Curse.values()[i] : Curse.ROT;
        lingering = tag.getBoolean("Lingering");
        flight = tag.getInt("Flight");
    }
}
