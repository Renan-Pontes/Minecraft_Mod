package com.renanpontes.isekaimahou.feature.automation.glyph;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.automation.AutoFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos de clima: Chamar Chuva, Céu Limpo e Chamar Tempestade. Mudam o tempo do mundo (5 min; Estender aumenta).
 * Só funcionam sob o céu da Superfície (o clima das outras dimensões não obedece).
 */
public final class WeatherGlyphs {
    /** Duração base: 5 minutos. */
    public static final int BASE_TICKS = 6000;

    public enum Kind { RAIN, CLEAR, THUNDER }

    public abstract static class WeatherEffect extends AreaEffect {
        private final Kind kind;

        protected WeatherEffect(String id, int cost, Kind kind) {
            super(id, cost);
            this.kind = kind;
        }

        @Override
        protected void work(SpellContext ctx, SpellStats stats, BlockPos center, Vec3 at, @Nullable Entity hit) {
            if (!AutoArea.firstThisTick(ctx, id().getPath())) return;
            ServerLevel level = ctx.level();
            if (level.dimension() != Level.OVERWORLD || !level.dimensionType().hasSkyLight() || level.dimensionType().hasCeiling()) {
                level.sendParticles(ParticleTypes.SMOKE, at.x, at.y + 0.5, at.z, 12, 0.3, 0.3, 0.3, 0.02);
                AutoFx.sound(level, at, SoundEvents.FIRE_EXTINGUISH, 0.5F, 1.4F);
                AutoFx.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.automation.weather_no_sky").withStyle(ChatFormatting.RED));
                return;
            }
            int ticks = stats.duration(BASE_TICKS);
            switch (kind) {
                case RAIN -> level.setWeatherParameters(0, ticks, true, false);
                case CLEAR -> level.setWeatherParameters(ticks, 0, false, false);
                case THUNDER -> level.setWeatherParameters(0, ticks, true, true);
            }
            skyFx(ctx, level, at, hit);
            int minutes = Math.max(1, Math.round(ticks / 1200F));
            String key = "message.isekai_mahou.automation.weather_" + kind.name().toLowerCase(java.util.Locale.ROOT);
            AutoFx.actionbar(ctx.caster(), Component.translatable(key, minutes).withColor(ctx.element().color));
        }

        /** Visual da mudança de clima (subindo do ponto até o céu). */
        private void skyFx(SpellContext ctx, ServerLevel level, Vec3 at, @Nullable Entity hit) {
            Vec3 top = at.add(0, 6, 0);
            switch (kind) {
                case RAIN -> {
                    AutoFx.line(level, at, top, ElementFx.dust(Element.WATER, 1.2F), 0.4);
                    level.sendParticles(ParticleTypes.CLOUD, top.x, top.y, top.z, 30, 2.0, 0.4, 2.0, 0.02);
                    level.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 30, 1.5, 0.2, 1.5, 0.1);
                    AutoFx.sound(level, at, SoundEvents.WEATHER_RAIN, 1.0F, 1.0F);
                    AutoFx.sound(level, at, SoundEvents.BUBBLE_COLUMN_UPWARDS_INSIDE, 0.8F, 1.2F);
                }
                case CLEAR -> {
                    AutoFx.line(level, at, top, ElementFx.dust(Element.LIGHT, 1.2F), 0.4);
                    AutoFx.ring(level, top, 2.0, ElementFx.dust(Element.LIGHT, 1.4F), 24);
                    level.sendParticles(ParticleTypes.END_ROD, top.x, top.y, top.z, 20, 1.5, 0.5, 1.5, 0.05);
                    AutoFx.sound(level, at, SoundEvents.BEACON_ACTIVATE, 0.8F, 1.3F);
                }
                case THUNDER -> {
                    AutoFx.line(level, at, top, ElementFx.dust(Element.LIGHTNING, 1.2F), 0.4);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, top.x, top.y, top.z, 30, 1.8, 0.5, 1.8, 0.1);
                    // Sem alvo atingido: um raio de enfeite (sem dano nem fogo) no chão perto do ponto
                    if (!(hit instanceof LivingEntity le) || le == ctx.caster()) {
                        BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, BlockPos.containing(at));
                        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                        if (bolt != null) {
                            bolt.moveTo(Vec3.atBottomCenterOf(ground));
                            bolt.setVisualOnly(true);
                            level.addFreshEntity(bolt);
                        }
                    }
                }
            }
        }
    }

    /** Chamar Chuva (Água t2): chuva por 5 min; uma criatura atingida fica Encharcada e apaga o fogo. */
    public static class CallRain extends WeatherEffect {
        public CallRain() {
            super("call_rain", 30, Kind.RAIN);
            element(Element.WATER);
            tier(2);
        }

        @Override
        protected void onTarget(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            le.clearFire();
            if (le != ctx.caster()) ctx.status(le, CoreRegistries.SOAKED, 200, 0, stats);
        }
    }

    /** Céu Limpo (Luz t2): para a chuva/tempestade por 5 min. */
    public static class ClearSky extends WeatherEffect {
        public ClearSky() {
            super("clear_sky", 30, Kind.CLEAR);
            element(Element.LIGHT);
            tier(2);
        }
    }

    /**
     * Chamar Tempestade (Raio t3): tempestade por 5 min. Uma criatura atingida (não você) leva um raio de verdade —
     * ótimo para carregar creepers numa fazenda.
     */
    public static class CallThunder extends WeatherEffect {
        public CallThunder() {
            super("call_thunder", 45, Kind.THUNDER);
            element(Element.LIGHTNING);
            tier(3);
        }

        @Override
        protected void onTarget(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || le == ctx.caster() || ctx.isAlly(le)) return;
            ServerLevel level = ctx.level();
            if (level.dimension() != Level.OVERWORLD) return;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) return;
            bolt.moveTo(Vec3.atBottomCenterOf(le.blockPosition()));
            if (ctx.casterPlayer() instanceof ServerPlayer sp) bolt.setCause(sp);
            level.addFreshEntity(bolt);
        }
    }

    private WeatherGlyphs() {}
}
