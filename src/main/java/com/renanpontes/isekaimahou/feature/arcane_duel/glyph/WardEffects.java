package com.renanpontes.isekaimahou.feature.arcane_duel.glyph;

import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.arcane_duel.DuelFx;
import com.renanpontes.isekaimahou.feature.arcane_duel.DuelRegistry;
import com.renanpontes.isekaimahou.feature.arcane_duel.DuelUtil;
import com.renanpontes.isekaimahou.feature.arcane_duel.entity.DuelFxEntity;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.network.CorePayloads;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/** Proteções e tempo: Resguardo, Indestrutível e Passo no Tempo. */
public final class WardEffects {

    // =====================================================================================
    //  Resguardo (ARCANE t3, C3, benéfico)
    // =====================================================================================
    public static class HexproofEffect extends EffectGlyph {
        public HexproofEffect() {
            super("hexproof", 22);
            element(Element.ARCANE);
            tier(3);
            minCircle(3);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel level = ctx.level();
            Vec3 c = DuelFx.center(le);
            if (stats.inverted) {
                // Inverter: quebra o Resguardo de um inimigo
                if (le.removeEffect(DuelRegistry.HEXPROOF)) {
                    DuelFx.shatter(level, c, 1.2F, DuelFx.HEX, ctx.caster());
                }
                return;
            }
            int ticks = stats.duration(160);
            le.addEffect(new MobEffectInstance(DuelRegistry.HEXPROOF, ticks, 0, false, true), ctx.caster());
            // Entra limpo: o que já estava grudado (status ruins) cai na hora. Exaustão Mágica (Domínio) e Procurado
            // (ficha criminal das cidades) são regras: ficam. Condições do núcleo saem pela API (Em Chamas apaga o fogo)
            int cleansed = 0;
            for (Statuses.Kind k : Statuses.Kind.values()) {
                if (!Statuses.has(le, k)) continue;
                Statuses.clear(le, k);
                cleansed++;
            }
            for (MobEffectInstance inst : List.copyOf(le.getActiveEffects())) {
                if (inst.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) continue;
                if (DuelUtil.isRuleEffect(inst.getEffect())) continue;
                le.removeEffect(inst.getEffect());
                cleansed++;
            }
            DuelFx.attach(level, DuelFxEntity.HEX_WARD, le, le.getBbWidth() * 0.5F + 0.75F, ticks, DuelFx.HEX, le.getBbHeight());
            level.sendParticles(Vfx.endRod(), c.x, c.y, c.z, 12, 0.5, 0.6, 0.5, 0.04);
            DuelFx.sound(level, c, SoundEvents.BEACON_POWER_SELECT, 0.8F, 1.5F);
            DuelFx.sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.1F);
            if (cleansed > 0) DuelFx.sound(level, c, SoundEvents.ZOMBIE_VILLAGER_CURE, 0.3F, 1.8F);
        }
    }

    // =====================================================================================
    //  Indestrutível (LIGHT t4, C5, benéfico) — não cai abaixo de 1 de vida (ver DuelEvents)
    // =====================================================================================
    public static class IndestructibleEffect extends EffectGlyph {
        public IndestructibleEffect() {
            super("indestructible", 30);
            element(Element.LIGHT);
            tier(4);
            minCircle(5);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ServerLevel level = ctx.level();
            Vec3 c = DuelFx.center(le);
            if (stats.inverted) {
                if (le.removeEffect(DuelRegistry.INDESTRUCTIBLE)) DuelFx.shatter(level, c, 1.3F, DuelFx.GOLD, ctx.caster());
                return;
            }
            int ticks = stats.duration(80);
            le.addEffect(new MobEffectInstance(DuelRegistry.INDESTRUCTIBLE, ticks, 0, false, true), ctx.caster());
            DuelFx.attach(level, DuelFxEntity.AEGIS, le, le.getBbWidth() * 0.5F + 0.9F, ticks, DuelFx.GOLD, le.getBbHeight());
            level.sendParticles(Vfx.waxOn(), c.x, c.y, c.z, 20, 0.5, 0.7, 0.5, 0.1);
            level.sendParticles(Vfx.endRod(), c.x, c.y + 1, c.z, 16, 0.3, 1.2, 0.3, 0.03);
            DuelFx.sound(level, c, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.6F);
            DuelFx.sound(level, c, SoundEvents.ANVIL_LAND, 0.4F, 1.8F);
            DuelFx.sound(level, c, SoundEvents.BELL_BLOCK, 0.9F, 1.3F);
        }
    }

    // =====================================================================================
    //  Passo no Tempo (ARCANE lendário, C7) — zera suas recargas e devolve 30% do mana; recarga própria 120 s
    // =====================================================================================
    public static class TimeWalkEffect extends EffectGlyph {
        public static final ResourceLocation ID = IsekaiMahou.id("time_walk");
        public static final int OWN_COOLDOWN = 2400;
        /** Dados em {@code getPersistentData()["PlayerPersisted"]} do jogador (sobrevivem à morte: morrer não zera a trava). */
        public static final String LOCK = "isekai_mahou.time_walk.lock";
        public static final String KEY = "isekai_mahou.time_walk.key";
        public static final String KEY_UNTIL = "isekai_mahou.time_walk.key_until";

        /** Parte dos dados do jogador que o NeoForge copia ao renascer. */
        public static CompoundTag data(Player p) {
            CompoundTag pd = p.getPersistentData();
            if (!pd.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) pd.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
            return pd.getCompound(Player.PERSISTED_NBT_TAG);
        }

        public TimeWalkEffect() {
            super("time_walk", 60);
            element(Element.ARCANE);
            legendary();
            minCircle(7);
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity caster = ctx.caster();
            if (caster == null || target != caster) return;   // o tempo volta só para quem conjura
            ServerLevel level = ctx.level();
            Vec3 c = DuelFx.center(caster);
            ServerPlayer sp = ctx.casterPlayer();
            if (sp == null) {
                // Mob: o tempo volta para ele e os efeitos ruins somem
                for (MobEffectInstance inst : List.copyOf(caster.getActiveEffects())) {
                    if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) caster.removeEffect(inst.getEffect());
                }
                clock(level, caster, 30);
                return;
            }
            long now = level.getGameTime();
            CompoundTag pd = data(sp);
            long lock = pd.getLong(LOCK);
            if (now < lock && !DuelUtil.isCreative(sp)) {
                DuelUtil.actionbar(sp, Component.translatable("message.isekai_mahou.arcane_duel.time_walk.recovering",
                        (lock - now + 19) / 20), ChatFormatting.GRAY);
                level.sendParticles(Vfx.smoke(), c.x, c.y + 0.6, c.z, 10, 0.3, 0.3, 0.3, 0.01);
                return;
            }
            pd.putLong(LOCK, now + OWN_COOLDOWN);
            MagicData d = MagicApi.get(sp);
            // O slot do próprio Passo no Tempo (já com a recarga de 120 s, se a magia teve Atraso) não volta
            String keep = pd.getLong(KEY_UNTIL) > now ? pd.getString(KEY) : "";
            int reset = 0;
            for (Map.Entry<String, Long> e : d.cooldowns.entrySet()) {
                if (e.getValue() <= now || e.getKey().equals(keep)) continue;
                e.setValue(now);
                PacketDistributor.sendToPlayer(sp, new CorePayloads.Cooldown(e.getKey(), 0));
                reset++;
            }
            float mana = d.maxMana() * 0.3F * Math.min(1.5F, stats.power());
            d.addMana(mana);
            clock(level, sp, 50);
            DuelUtil.actionbar(sp, Component.translatable("message.isekai_mahou.arcane_duel.time_walk.done", reset, Math.round(mana)),
                    ChatFormatting.AQUA);
        }

        private static void clock(ServerLevel level, LivingEntity e, int life) {
            Vec3 c = DuelFx.center(e);
            DuelFx.attach(level, DuelFxEntity.CLOCK, e, 2.8F, life, DuelFx.TIME, e.getBbHeight());
            level.sendParticles(Vfx.reversePortal(), c.x, c.y, c.z, 60, 1.2, 1.0, 1.2, 0.05);
            DuelFx.sound(level, c, SoundEvents.BELL_RESONATE, 1.0F, 0.7F);
            DuelFx.sound(level, c, SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.6F);
            DuelFx.sound(level, c, SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.5F);
        }
    }

    private WardEffects() {}
}
