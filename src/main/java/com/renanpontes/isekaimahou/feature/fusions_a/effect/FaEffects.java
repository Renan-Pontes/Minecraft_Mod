package com.renanpontes.isekaimahou.feature.fusions_a.effect;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.feature.fusions_a.FaUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Efeitos de status das fusões (conjunto A). Comportamentos que dependem de dano (absorver, revidar, acordar)
 * ficam no {@code FaEvents}, para nunca mexer na lista de efeitos enquanto o jogo a percorre.
 */
public final class FaEffects {
    /** Chave (persistent data) do escudo restante da Bolha Protetora. */
    public static final String BUBBLE_KEY = "isekai_mahou.fa_bubble";
    /** Chave (persistent data) do ponto da miragem que os mobs confusos perseguem. */
    public static final String MIRAGE_KEY = "isekai_mahou.fa_mirage";
    /** Chave (persistent data) do id do mob que o confuso está atacando por engano. */
    public static final String DECOY_KEY = "isekai_mahou.fa_decoy";

    private static final AttributeModifier.Operation MUL_TOTAL = AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
    private static final AttributeModifier.Operation ADD = AttributeModifier.Operation.ADD_VALUE;

    private static ServerLevel server(LivingEntity e) {
        return e.level() instanceof ServerLevel sl ? sl : null;
    }

    // =====================================================================================
    //  Benéficos
    // =====================================================================================

    /** Armadura de Magma: armadura extra, imune a fogo; quem bate de perto pega fogo (FaEvents). */
    public static class MagmaArmor extends MobEffect {
        public MagmaArmor() {
            super(MobEffectCategory.BENEFICIAL, 0xFF4500);
            addAttributeModifier(Attributes.ARMOR, IsekaiMahou.id("effect.magma_armor"), 4.0, ADD);
            addAttributeModifier(Attributes.ARMOR_TOUGHNESS, IsekaiMahou.id("effect.magma_armor"), 2.0, ADD);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % 8 == 0;
        }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (e.isOnFire()) e.clearFire();
            ServerLevel sl = server(e);
            if (sl != null) {
                FaUtil.around(sl, e, FaUtil.dustFade(0xFF6A00, 0x3A1A10, 1.0F), 3, 0);
                if (e.getRandom().nextInt(3) == 0) {
                    sl.sendParticles(ParticleTypes.DRIPPING_LAVA, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 1,
                            e.getBbWidth() * 0.4, e.getBbHeight() * 0.3, e.getBbWidth() * 0.4, 0);
                }
                sl.sendParticles(ParticleTypes.SMALL_FLAME, e.getX(), e.getY() + 0.1, e.getZ(), 1, 0.3, 0.05, 0.3, 0.005);
            }
            return true;
        }
    }

    /** Armadura de Gelo: armadura e firmeza; quem bate de perto congela (FaEvents). Derrete com fogo. */
    public static class IceArmor extends MobEffect {
        public IceArmor() {
            super(MobEffectCategory.BENEFICIAL, 0xA8E8FF);
            addAttributeModifier(Attributes.ARMOR, IsekaiMahou.id("effect.ice_armor"), 5.0, ADD);
            addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id("effect.ice_armor"), 0.3, ADD);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % 8 == 0;
        }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (e.getTicksFrozen() > 0) e.setTicksFrozen(0);
            ServerLevel sl = server(e);
            if (sl != null) {
                FaUtil.around(sl, e, ParticleTypes.SNOWFLAKE, 2, 0.01);
                FaUtil.around(sl, e, FaUtil.dust(0xCFF4FF, 0.8F), 3, 0);
            }
            return true;
        }
    }

    /** Sobrecarga: velocidade extrema, ataques rápidos e passo alto. Ao acabar, cansa (FaEvents). */
    public static class Overcharge extends MobEffect {
        public Overcharge() {
            super(MobEffectCategory.BENEFICIAL, 0x7FDBFF);
            addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.overcharge"), 0.6, MUL_TOTAL);
            addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id("effect.overcharge"), 0.5, MUL_TOTAL);
            addAttributeModifier(Attributes.STEP_HEIGHT, IsekaiMahou.id("effect.overcharge"), 0.5, ADD);
            addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id("effect.overcharge"), 0.08, ADD);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % 3 == 0;
        }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            ServerLevel sl = server(e);
            if (sl == null) return true;
            Vec3 v = e.getDeltaMovement();
            boolean moving = v.horizontalDistanceSqr() > 0.003 || e.walkDist != e.walkDistO;
            sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(),
                    moving ? 3 : 1, e.getBbWidth() * 0.5, e.getBbHeight() * 0.4, e.getBbWidth() * 0.5, 0.05);
            if (moving) sl.sendParticles(FaUtil.dust(0x7FDBFF, 0.9F), e.getX(), e.getY() + 0.2, e.getZ(), 2, 0.2, 0.1, 0.2, 0);
            return true;
        }
    }

    /** Coroa Solar: marcador da aura (quem queima os inimigos é a zona que segue o portador). */
    public static class SolarCrown extends MobEffect {
        public SolarCrown() {
            super(MobEffectCategory.BENEFICIAL, 0xFFB300);
        }
    }

    /** Fotossíntese: sob o sol, regenera mana (jogadores) e um pouco de vida. */
    public static class Photosynthesis extends MobEffect {
        public Photosynthesis() {
            super(MobEffectCategory.BENEFICIAL, 0x7BD84A);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % 20 == 0;
        }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            ServerLevel sl = server(e);
            if (sl == null) return true;
            if (inSunlight(e)) {
                if (e instanceof ServerPlayer sp) {
                    MagicData d = MagicApi.get(sp);
                    d.addMana((2.5F + 1.5F * amplifier) * ResourceTraining.MANA_SCALE); // v1.0: mana ×3
                }
                if (e.getHealth() < e.getMaxHealth()) e.heal(0.5F + 0.5F * amplifier);
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 3,
                        e.getBbWidth() * 0.5, e.getBbHeight() * 0.3, e.getBbWidth() * 0.5, 0);
                sl.sendParticles(FaUtil.dustFade(0xFFF07A, 0x58C84A, 1.0F), e.getX(), e.getY() + e.getBbHeight() + 0.4,
                        e.getZ(), 4, 0.3, 0.25, 0.3, 0);
            } else {
                sl.sendParticles(FaUtil.dust(0x3E7A35, 0.7F), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 1,
                        0.3, 0.3, 0.3, 0);
            }
            return true;
        }

        /** Dia, sem chuva e com o céu visível. */
        public static boolean inSunlight(LivingEntity e) {
            Level lv = e.level();
            return lv.isDay() && !lv.isRainingAt(e.blockPosition()) && lv.canSeeSky(BlockPos.containing(e.getEyePosition()));
        }
    }

    /** Bolha Protetora: absorve muito dano até estourar (FaEvents) e segura empurrões. */
    public static class ProtectiveBubble extends MobEffect {
        public ProtectiveBubble() {
            super(MobEffectCategory.BENEFICIAL, 0x9FE8FF);
            addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id("effect.protective_bubble"), 0.8, ADD);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % 5 == 0;
        }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            ServerLevel sl = server(e);
            if (sl == null) return true;
            Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
            double r = Math.max(e.getBbWidth(), e.getBbHeight()) * 0.5 + 0.55;
            FaUtil.sphere(sl, c, FaUtil.dustFade(0xE8F8FF, 0x7FD4FF, 0.9F), r, 10);
            if (e.getRandom().nextInt(3) == 0) FaUtil.sphere(sl, c, ParticleTypes.BUBBLE_POP, r, 2);
            return true;
        }

        public static float pool(LivingEntity e) {
            return e.getPersistentData().getFloat(BUBBLE_KEY);
        }

        public static void setPool(LivingEntity e, float v) {
            CompoundTag t = e.getPersistentData();
            if (v <= 0) t.remove(BUBBLE_KEY);
            else t.putFloat(BUBBLE_KEY, v);
        }
    }

    // =====================================================================================
    //  Nocivos
    // =====================================================================================

    /** Chama Sagrada: fogo dourado que não apaga na água; mortos-vivos queimam em dobro. */
    public static class HolyFlame extends MobEffect {
        public HolyFlame() {
            super(MobEffectCategory.HARMFUL, 0xFFD54A);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            int interval = Math.max(10, 20 >> amplifier);
            return duration % interval == 0;
        }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            ServerLevel sl = server(e);
            if (sl == null) return true;
            float dmg = 1.0F + 0.5F * amplifier;
            if (FaUtil.isUndead(e)) dmg *= 2F;
            e.invulnerableTime = 0;
            e.hurt(FaUtil.creditedSource(e), dmg);
            FaUtil.around(sl, e, ParticleTypes.FLAME, 4, 0.02);
            FaUtil.around(sl, e, FaUtil.dustFade(0xFFF3B0, 0xFFB300, 1.1F), 5, 0);
            sl.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + e.getBbHeight() + 0.2, e.getZ(), 1, 0.2, 0.1, 0.2, 0.02);
            return true;
        }
    }

    /** Estátua de Gelo: completamente parado e sem atacar (os golpes são cancelados no FaEvents). */
    public static class IceStatue extends MobEffect {
        public IceStatue() {
            super(MobEffectCategory.HARMFUL, 0xCFF4FF);
            addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.ice_statue"), -1.0, MUL_TOTAL);
            addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id("effect.ice_statue"), -1.0, MUL_TOTAL);
            addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id("effect.ice_statue"), -0.95, MUL_TOTAL);
            addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id("effect.ice_statue"), 1.0, ADD);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return true;
        }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze() + 2));
            if (e.isOnFire()) e.clearFire();
            if (e.level().isClientSide) return true;
            if (!(e instanceof Player)) {
                Vec3 v = e.getDeltaMovement();
                e.setDeltaMovement(0, Math.min(0, v.y), 0);
            }
            if (e instanceof Mob mob) {
                mob.getNavigation().stop();
                mob.setTarget(null);
            }
            ServerLevel sl = server(e);
            if (sl != null && e.tickCount % 10 == 0) {
                FaUtil.around(sl, e, ParticleTypes.SNOWFLAKE, 2, 0.0);
                FaUtil.around(sl, e, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()), 2, 0);
            }
            return true;
        }
    }

    /** Sono dos Esporos: dorme parado até levar dano (acordar fica no FaEvents). */
    public static class SporeSleep extends MobEffect {
        public SporeSleep() {
            super(MobEffectCategory.HARMFUL, 0xB58AE6);
            addAttributeModifier(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("effect.spore_sleep"), -1.0, MUL_TOTAL);
            addAttributeModifier(Attributes.JUMP_STRENGTH, IsekaiMahou.id("effect.spore_sleep"), -1.0, MUL_TOTAL);
            addAttributeModifier(Attributes.ATTACK_SPEED, IsekaiMahou.id("effect.spore_sleep"), -0.9, MUL_TOTAL);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return true;
        }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            if (e.level().isClientSide) return true;
            if (e instanceof Mob mob) {
                mob.getNavigation().stop();
                mob.setTarget(null);
                mob.setXRot(25F);
            }
            ServerLevel sl = server(e);
            if (sl != null && e.tickCount % 15 == 0) {
                sl.sendParticles(ParticleTypes.FALLING_SPORE_BLOSSOM, e.getX(), e.getY() + e.getBbHeight() + 0.4, e.getZ(), 2,
                        0.25, 0.1, 0.25, 0);
                sl.sendParticles(FaUtil.dustFade(0xD9B8FF, 0x6B4FA0, 0.8F), e.getX() + 0.3, e.getY() + e.getBbHeight() + 0.2,
                        e.getZ(), 1, 0.05, 0.15, 0.05, 0);
            }
            return true;
        }
    }

    /**
     * Miragem: o mob vê ilusões — ataca outro mob ("isca") ou corre atrás do reflexo no ar. A escolha muda a cada
     * segundo, mas é reforçada a cada 5 ticks: sem isso a IA do mob voltaria para o jogador quase na hora.
     */
    public static class Bewildered extends MobEffect {
        public Bewildered() {
            super(MobEffectCategory.HARMFUL, 0xE3C77A);
        }

        @Override
        public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
            return duration % 5 == 0;
        }

        @Override
        public boolean applyEffectTick(LivingEntity e, int amplifier) {
            ServerLevel sl = server(e);
            if (sl == null) return true;
            // Chamado de 5 em 5 ticks: exatamente uma dessas chamadas cai em cada janela de 20 ticks
            boolean second = e.tickCount % 20 < 5;
            if (second) {
                sl.sendParticles(FaUtil.dustFade(0xFFE9A8, 0xE3C77A, 0.9F), e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(),
                        4, 0.35, 0.15, 0.35, 0);
                sl.sendParticles(ParticleTypes.WAX_OFF, e.getX(), e.getY() + e.getBbHeight() * 0.8, e.getZ(), 1, 0.3, 0.2, 0.3, 0);
            }
            if (!(e instanceof Mob mob) || FaUtil.isBoss(e)) return true;
            CompoundTag t = e.getPersistentData();
            LivingEntity decoy = t.contains(DECOY_KEY) && sl.getEntity(t.getInt(DECOY_KEY)) instanceof LivingEntity d
                    && d.isAlive() && d != e && d.distanceToSqr(e) < 20 * 20 ? d : null;
            if (second) {
                decoy = null;
                if (e.getRandom().nextFloat() < 0.65F) {
                    List<Mob> others = sl.getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(10),
                            o -> o != e && o.isAlive() && !o.isAlliedTo(e) && !o.isInvisible()
                                    && o != e.getVehicle() && !e.hasPassenger(o));
                    if (!others.isEmpty()) decoy = others.get(e.getRandom().nextInt(others.size()));
                }
                if (decoy != null) t.putInt(DECOY_KEY, decoy.getId());
                else t.remove(DECOY_KEY);
            }
            if (decoy != null) {
                if (mob.getTarget() != decoy) mob.setTarget(decoy);
                return true;
            }
            if (mob.getTarget() != null) mob.setTarget(null);
            if (t.contains(MIRAGE_KEY) && (second || mob.getNavigation().isDone())) {
                BlockPos p = BlockPos.of(t.getLong(MIRAGE_KEY));
                mob.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, 1.1);
            }
            return true;
        }

        /** Limpa a isca e o ponto da miragem quando o efeito acaba. */
        public static void clear(LivingEntity e) {
            CompoundTag t = e.getPersistentData();
            t.remove(DECOY_KEY);
            t.remove(MIRAGE_KEY);
        }
    }

    private FaEffects() {}
}
