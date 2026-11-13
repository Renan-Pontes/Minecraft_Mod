package com.renanpontes.isekaimahou.feature.origins_a.glyph;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.origins_a.OaRegistries;
import com.renanpontes.isekaimahou.feature.origins_a.OaUtil;
import com.renanpontes.isekaimahou.feature.origins_a.curse.Frogify;
import com.renanpontes.isekaimahou.feature.origins_a.curse.VoodooLinks;
import com.renanpontes.isekaimahou.feature.origins_a.familiar.Familiars;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;

/**
 * MALDIÇÕES — Magia de Origem da Bruxa. Efeitos arcanos usáveis em magia de qualquer elemento.
 */
public final class CurseGlyphs {
    private static final DustParticleOptions HEX_DUST = new DustParticleOptions(new Vector3f(0.45F, 0.12F, 0.55F), 1.2F);
    private static final DustParticleOptions EYE_DUST = new DustParticleOptions(new Vector3f(0.25F, 0.85F, 0.3F), 1.0F);

    public static void register() {
        GlyphRegistry.register(new Hex());
        GlyphRegistry.register(new VoodooDoll());
        GlyphRegistry.register(new EvilEye());
        GlyphRegistry.register(new Frogification());
        GlyphRegistry.register(new Familiar());
    }

    private static void curseFx(ServerLevel level, LivingEntity t, DustParticleOptions dust) {
        double y = t.getY() + t.getBbHeight() * 0.6;
        level.sendParticles(Vfx.witch(), t.getX(), y, t.getZ(), 14, 0.3, 0.4, 0.3, 0.05);
        level.sendParticles(dust, t.getX(), y, t.getZ(), 12, 0.35, 0.45, 0.35, 0);
    }

    // =====================================================================================
    /** Hex (T1): Fraqueza + Lentidão + Corrompido leve. Amplificar sobe o nível. */
    public static class Hex extends EffectGlyph {
        public Hex() {
            super("hex", 8);
            element(Element.ARCANE);
            origin(MagicPath.WITCH);
            tier(1);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            int amp = stats.amplify > 0 ? 1 : 0;
            ctx.status(le, MobEffects.WEAKNESS, 200, amp, stats);
            ctx.status(le, MobEffects.MOVEMENT_SLOWDOWN, 200, amp, stats);
            ctx.status(le, CoreRegistries.CORRUPTED, 120, 0, stats);
            curseFx(ctx.level(), le, HEX_DUST);
            ctx.level().playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.WITCH_AMBIENT, SoundSource.PLAYERS, 0.8F, 1.4F);
        }
    }

    // =====================================================================================
    /**
     * Boneco de Vodu (T2): liga você ao alvo por 60s; o próximo golpe que você receberia vai para ele
     * (Amplificar = +1 golpe e mais dano transferido).
     */
    public static class VoodooDoll extends EffectGlyph {
        public VoodooDoll() {
            super("voodoo_doll", 14);
            element(Element.ARCANE);
            origin(MagicPath.WITCH);
            tier(2);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity caster = ctx.caster();
            if (caster == null || target == caster || !(target instanceof LivingEntity le)) return;
            int duration = stats.duration(1200);
            int charges = 1 + Math.max(0, stats.amplify) + (stats.charge > 0 ? 1 : 0);
            ctx.status(le, OaRegistries.VOODOO_BOUND, 1200, 0, stats);
            VoodooLinks.bind(ctx.level(), caster, le, charges, duration, stats.power());
        }
    }

    // =====================================================================================
    /** Mau-Olhado (T3): azar (sorte -2, erra golpes, colhe menos) + Exposto. */
    public static class EvilEye extends EffectGlyph {
        public EvilEye() {
            super("evil_eye", 16);
            element(Element.ARCANE);
            origin(MagicPath.WITCH);
            tier(3);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le)) return;
            ctx.status(le, OaRegistries.EVIL_EYE, 400, 0, stats);
            ctx.status(le, CoreRegistries.EXPOSED, 300, stats.amplify > 0 ? 1 : 0, stats);
            ServerLevel level = ctx.level();
            curseFx(level, le, EYE_DUST);
            // "Olho" que se abre sobre o alvo
            double y = le.getY() + le.getBbHeight() + 0.5;
            for (int i = 0; i < 16; i++) {
                double a = Math.PI * 2 * i / 16;
                level.sendParticles(EYE_DUST, le.getX() + Math.cos(a) * 0.5, y + Math.sin(a) * 0.25, le.getZ(), 1, 0, 0, 0, 0);
            }
            level.sendParticles(Vfx.sculkSoul(), le.getX(), y, le.getZ(), 2, 0.05, 0.05, 0.05, 0.01);
            level.playSound(null, le.getX(), le.getY(), le.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.35F, 1.5F);
        }
    }

    // =====================================================================================
    /** Virar Sapo (T4): mob comum (não chefe, vida ≤ 40) vira sapo por 20s e depois volta ao normal. */
    public static class Frogification extends EffectGlyph {
        public Frogification() {
            super("frogify", 28);
            element(Element.ARCANE);
            origin(MagicPath.WITCH);
            tier(4);
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || Frogify.isFrogified(le)) return;
            if (le instanceof Mob mob && Frogify.canFrogify(mob)) {
                Frogify.transform(ctx.level(), mob, stats.duration(400));
                return;
            }
            // Resistiu (chefe, grande demais ou jogador)
            ctx.level().sendParticles(Vfx.smoke(), le.getX(), le.getY() + le.getBbHeight() * 0.6, le.getZ(), 10, 0.3, 0.3, 0.3, 0.02);
            if (ctx.caster() instanceof Player p) OaUtil.actionbar(p, "frogify_resist", le.getDisplayName());
        }
    }

    // =====================================================================================
    /** Familiar (T5, lendário): invoca um Corvo (ou um Gato, se conjurar agachado) por 10 minutos. Use em si mesmo. */
    public static class Familiar extends EffectGlyph {
        /** Uma invocação por conjuração (formas de área chamariam o efeito para cada aliado). */
        private static final Map<UUID, Long> LAST = new HashMap<>();

        public Familiar() {
            super("familiar", 50);
            element(Element.ARCANE);
            origin(MagicPath.WITCH);
            legendary();
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            long now = ctx.level().getGameTime();
            Long last = LAST.get(caster.getUUID());
            if (last != null && last == now) return;
            LAST.put(caster.getUUID(), now);
            Familiars.summon(ctx.level(), caster, caster.isShiftKeyDown(), stats.duration(12000), stats.power());
        }
    }

    private CurseGlyphs() {}
}
