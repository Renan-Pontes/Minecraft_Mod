package com.renanpontes.isekaimahou.feature.class_enchanter.glyph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_enchanter.AntiMagic;
import com.renanpontes.isekaimahou.feature.class_enchanter.EnchFx;
import com.renanpontes.isekaimahou.feature.class_enchanter.EnchRegistries;
import com.renanpontes.isekaimahou.feature.class_enchanter.Enchants;
import com.renanpontes.isekaimahou.feature.class_enchanter.GearEnchant;
import com.renanpontes.isekaimahou.feature.class_enchanter.HeavenlyRestriction;
import com.renanpontes.isekaimahou.feature.class_enchanter.Imbue;
import com.renanpontes.isekaimahou.feature.class_enchanter.effect.EnchantEffect;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Os glifos de ORIGEM do Encantador ({@code origin(MagicPath.ENCHANTER)}): 9 encantamentos (efeitos benéficos que
 * empilham com limite e se combinam com qualquer forma — Si Mesmo, Toque, Aura, Golpe, Elo, Santuário...), a forma
 * Elo de Encantamento e a forma G Santuário do Soberano. Com Inverter, cada encantamento vira a versão "maldição" no
 * inimigo. Outras classes só aprendem por Lição (e aí usam os números base, sem os bônus de classe).
 */
public final class EnchanterGlyphs {
    public static final String MIGHT = "enchant_might";
    public static final String HASTE = "enchant_haste";
    public static final String AEGIS = "enchant_aegis";
    public static final String VITALITY = "enchant_vitality";
    public static final String IMBUE = "enchant_imbue";
    public static final String OVERDRIVE = "enchant_overdrive";
    public static final String GEAR = "enchant_gear";
    public static final String ANTI_MAGIC = "anti_magic";
    public static final String HEAVENLY = "heavenly_restriction";
    public static final String LINK = "enchant_link";
    public static final String SOVEREIGN = "enchant_sovereign";

    /** Todos os glifos de origem, na ordem em que o Rito os ensina. */
    public static final List<String> ALL = List.of(MIGHT, HASTE, ANTI_MAGIC, AEGIS, VITALITY, LINK, AnimeGlyphs.GEAR_SECOND, IMBUE, OVERDRIVE,
            AnimeGlyphs.GATES, AnimeGlyphs.KAIOKEN, HEAVENLY, GEAR, AnimeGlyphs.ULTRA, AnimeGlyphs.GEAR_FOURTH, SOVEREIGN);

    private static boolean registered;

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        GlyphRegistry.register(new Might());
        GlyphRegistry.register(new Haste());
        GlyphRegistry.register(new AntiMagicGlyph());
        GlyphRegistry.register(new Aegis());
        GlyphRegistry.register(new Vitality());
        GlyphRegistry.register(new LinkForm());
        GlyphRegistry.register(new ImbueGlyph());
        GlyphRegistry.register(new Overdrive());
        GlyphRegistry.register(new HeavenlyGlyph());
        GlyphRegistry.register(new Gear());
        GlyphRegistry.register(new SovereignForm());
        // pacote de técnicas de anime (transformações do corpo encantado)
        GlyphRegistry.register(new AnimeGlyphs.GearSecond());
        GlyphRegistry.register(new AnimeGlyphs.EightGates());
        GlyphRegistry.register(new AnimeGlyphs.Kaioken());
        GlyphRegistry.register(new AnimeGlyphs.UltraInstinct());
        GlyphRegistry.register(new AnimeGlyphs.GearFourth());
    }

    // =====================================================================================
    //  Base
    // =====================================================================================
    /** Encantamento: benéfico nos aliados/em si; com Inverter, a "maldição" no inimigo. Nunca ajuda inimigos. */
    abstract static class EnchantGlyph extends EffectGlyph {
        private int rgb = EnchFx.TEAL;

        EnchantGlyph(String id, int cost) {
            super(id, cost);
            element(Element.ARCANE);
            origin(MagicPath.ENCHANTER);
        }

        EnchantGlyph color(int rgb) {
            this.rgb = rgb;
            return this;
        }

        int rgb() {
            return rgb;
        }

        @Override
        public int argb() {
            return 0xFF000000 | rgb;
        }

        @Override
        public boolean isBeneficial() {
            return true;
        }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity le) || !le.isAlive()) return;
            boolean self = target == ctx.caster();
            boolean ally = self || ctx.isAlly(target);
            if (stats.inverted && !self && !ally) {
                curse(ctx, le, stats);
                return;
            }
            if (!ally) return; // "Só Inimigos" sem Inverter: encantamento não ajuda inimigo
            bless(ctx, le, stats, self);
        }

        abstract void bless(SpellContext ctx, LivingEntity t, SpellStats stats, boolean self);

        abstract void curse(SpellContext ctx, LivingEntity t, SpellStats stats);

        /** Visual/som padrão de um encantamento aplicado. */
        void blessFx(SpellContext ctx, LivingEntity t, int level) {
            ServerLevel l = ctx.level();
            EnchFx.spiral(l, t, rgb, level >= 3 ? EnchFx.GOLD : EnchFx.WHITE);
            EnchFx.sound(l, t.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 0.6F, 1.0F + 0.12F * level);
            EnchFx.sound(l, t.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 0.8F + 0.1F * level);
        }

        void curseFx(SpellContext ctx, LivingEntity t) {
            ServerLevel l = ctx.level();
            Vec3 c = EnchFx.center(t);
            l.sendParticles(EnchFx.dust(0x5A1E7A, 1.0F), c.x, c.y, c.z, 14, 0.3, 0.45, 0.3, 0);
            l.sendParticles(ParticleTypes.WITCH, c.x, c.y + 0.3, c.z, 6, 0.3, 0.3, 0.3, 0.02);
            EnchFx.sound(l, t.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 0.6F, 1.4F);
        }
    }

    /** Aviso (com respiro) para quem tenta pôr um encantamento só-em-si num aliado. */
    private static final Map<UUID, Long> SELF_ONLY_WARN = new HashMap<>();

    static void selfOnly(SpellContext ctx, String key) {
        if (!(ctx.caster() instanceof ServerPlayer sp)) return;
        long now = ctx.level().getGameTime();
        Long last = SELF_ONLY_WARN.get(sp.getUUID());
        if (last != null && now - last < 60) return;
        if (SELF_ONLY_WARN.size() > 256) SELF_ONLY_WARN.clear();
        SELF_ONLY_WARN.put(sp.getUUID(), now);
        sp.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY), true);
    }

    // =====================================================================================
    //  1. Força (t1)
    // =====================================================================================
    public static class Might extends EnchantGlyph {
        public Might() {
            super("enchant_might", 10);
            tier(1);
            color(EnchantEffect.Kind.MIGHT.color);
        }

        @Override
        void bless(SpellContext ctx, LivingEntity t, SpellStats stats, boolean self) {
            blessFx(ctx, t, Enchants.apply(ctx, t, EnchRegistries.MIGHT, stats, 600));
        }

        @Override
        void curse(SpellContext ctx, LivingEntity t, SpellStats stats) {
            ctx.status(t, MobEffects.WEAKNESS, 160, Math.min(2, Math.max(0, stats.amplify)), stats);
            curseFx(ctx, t);
        }

        @Override
        public int hintDuration() { return 600; }
    }

    // =====================================================================================
    //  2. Celeridade (t1)
    // =====================================================================================
    public static class Haste extends EnchantGlyph {
        public Haste() {
            super("enchant_haste", 10);
            tier(1);
            color(EnchantEffect.Kind.HASTE.color);
        }

        @Override
        void bless(SpellContext ctx, LivingEntity t, SpellStats stats, boolean self) {
            int lvl = Enchants.apply(ctx, t, EnchRegistries.HASTE, stats, 600);
            blessFx(ctx, t, lvl);
            ctx.level().sendParticles(ParticleTypes.CLOUD, t.getX(), t.getY() + 0.1, t.getZ(), 6, 0.3, 0.05, 0.3, 0.04);
        }

        @Override
        void curse(SpellContext ctx, LivingEntity t, SpellStats stats) {
            int amp = Math.min(2, Math.max(0, stats.amplify));
            ctx.status(t, MobEffects.MOVEMENT_SLOWDOWN, 160, amp, stats);
            ctx.status(t, MobEffects.DIG_SLOWDOWN, 160, amp, stats);
            curseFx(ctx, t);
        }

        @Override
        public int hintDuration() { return 600; }
    }

    // =====================================================================================
    //  3. Anti-Magia (t1, assinatura) — só em si mesmo
    // =====================================================================================
    public static class AntiMagicGlyph extends EnchantGlyph {
        public AntiMagicGlyph() {
            super("anti_magic", 16);
            tier(1);
            color(EnchantEffect.Kind.ANTI_MAGIC.color);
        }

        @Override
        void bless(SpellContext ctx, LivingEntity t, SpellStats stats, boolean self) {
            if (!self) {
                selfOnly(ctx, "message.isekai_mahou.enchanter.self_only_anti");
                return;
            }
            AntiMagic.activate(ctx, t, stats);
        }

        @Override
        void curse(SpellContext ctx, LivingEntity t, SpellStats stats) {
            AntiMagic.seal(ctx, t, stats);
        }

        @Override
        public int hintDuration() { return 600; }
    }

    // =====================================================================================
    //  4. Égide (t2)
    // =====================================================================================
    public static class Aegis extends EnchantGlyph {
        public Aegis() {
            super("enchant_aegis", 12);
            tier(2);
            color(EnchantEffect.Kind.AEGIS.color);
        }

        @Override
        void bless(SpellContext ctx, LivingEntity t, SpellStats stats, boolean self) {
            int lvl = Enchants.apply(ctx, t, EnchRegistries.AEGIS, stats, 600);
            blessFx(ctx, t, lvl);
            EnchFx.ring(ctx.level(), EnchFx.center(t), Math.max(0.8, t.getBbWidth()), EnchFx.GOLD, 18, 0.9F);
            EnchFx.sound(ctx.level(), t.position(), SoundEvents.ARMOR_EQUIP_IRON.value(), 0.8F, 1.2F);
        }

        @Override
        void curse(SpellContext ctx, LivingEntity t, SpellStats stats) {
            Statuses.apply(t, Statuses.Kind.EXPOSED, 1 + Math.max(0, stats.amplify), stats.duration(160), ctx.caster(), ctx.powerMult());
            curseFx(ctx, t);
        }

        @Override
        public int hintDuration() { return 600; }
    }

    // =====================================================================================
    //  5. Vitalidade (t2)
    // =====================================================================================
    public static class Vitality extends EnchantGlyph {
        public Vitality() {
            super("enchant_vitality", 14);
            tier(2);
            color(EnchantEffect.Kind.VITALITY.color);
        }

        @Override
        void bless(SpellContext ctx, LivingEntity t, SpellStats stats, boolean self) {
            ctx.heal(t, 2F, stats);
            blessFx(ctx, t, Enchants.apply(ctx, t, EnchRegistries.VITALITY, stats, 400));
        }

        @Override
        void curse(SpellContext ctx, LivingEntity t, SpellStats stats) {
            Statuses.apply(t, Statuses.Kind.BLEEDING, 2 + Math.max(0, stats.amplify), stats.duration(120), ctx.caster(), ctx.powerMult());
            curseFx(ctx, t);
        }

        @Override
        public float baseHeal() { return 2F; }

        @Override
        public int hintDuration() { return 400; }
    }

    // =====================================================================================
    //  6. Imbuir Arma (t3) — o elemento da magia vai para a arma
    // =====================================================================================
    public static class ImbueGlyph extends EnchantGlyph {
        public ImbueGlyph() {
            super("enchant_imbue", 14);
            tier(3);
            minCircle(2);
            color(EnchantEffect.Kind.IMBUE.color);
        }

        @Override
        void bless(SpellContext ctx, LivingEntity t, SpellStats stats, boolean self) {
            Element el = ctx.primaryElement();
            Imbue.set(t, el);
            int lvl = Enchants.apply(ctx, t, EnchRegistries.IMBUE, stats, 600);
            blessFx(ctx, t, lvl);
            Vec3 hand = EnchFx.center(t);
            ctx.level().sendParticles(EnchFx.dust(el.color, 1.2F), hand.x, hand.y, hand.z, 16, 0.4, 0.4, 0.4, 0);
            EnchFx.sound(ctx.level(), t.position(), SoundEvents.SMITHING_TABLE_USE, 0.6F, 1.5F);
        }

        /** Inverter: desencanta — arranca todos os encantamentos e um efeito bom. */
        @Override
        void curse(SpellContext ctx, LivingEntity t, SpellStats stats) {
            for (MobEffectInstance i : Enchants.active(t)) t.removeEffect(i.getEffect());
            for (MobEffectInstance i : new ArrayList<>(t.getActiveEffects())) {
                if (i.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) {
                    t.removeEffect(i.getEffect());
                    break;
                }
            }
            EnchFx.shatter(ctx.level(), EnchFx.center(t), 0.9);
            curseFx(ctx, t);
        }

        @Override
        public int hintDuration() { return 600; }
    }

    // =====================================================================================
    //  7. Sobrecarga (t3) — muito forte por pouco tempo, depois Exaustão
    // =====================================================================================
    public static class Overdrive extends EnchantGlyph {
        public Overdrive() {
            super("enchant_overdrive", 22);
            tier(3);
            minCircle(2);
            color(EnchantEffect.Kind.OVERDRIVE.color);
        }

        @Override
        void bless(SpellContext ctx, LivingEntity t, SpellStats stats, boolean self) {
            int lvl = Enchants.apply(ctx, t, EnchRegistries.OVERDRIVE, stats, 160);
            blessFx(ctx, t, lvl);
            ServerLevel l = ctx.level();
            l.sendParticles(ParticleTypes.ELECTRIC_SPARK, t.getX(), t.getY() + 1.0, t.getZ(), 20, 0.4, 0.6, 0.4, 0.3);
            EnchFx.ring(l, t.position().add(0, 0.1, 0), 1.4, EnchantEffect.Kind.OVERDRIVE.color, 24, 1.1F);
            EnchFx.sound(l, t.position(), SoundEvents.BEACON_POWER_SELECT, 0.9F, 1.8F);
            EnchFx.sound(l, t.position(), SoundEvents.FIRECHARGE_USE, 0.5F, 1.6F);
        }

        @Override
        void curse(SpellContext ctx, LivingEntity t, SpellStats stats) {
            ctx.status(t, MobEffects.MOVEMENT_SLOWDOWN, 100, 1, stats);
            ctx.status(t, MobEffects.WEAKNESS, 100, 0, stats);
            curseFx(ctx, t);
        }

        @Override
        public int hintDuration() { return 160; }
    }

    // =====================================================================================
    //  8. Restrição Celestial (t4, assinatura) — só em si mesmo
    // =====================================================================================
    public static class HeavenlyGlyph extends EnchantGlyph {
        public HeavenlyGlyph() {
            super("heavenly_restriction", 30);
            tier(4);
            minCircle(3);
            color(EnchantEffect.Kind.HEAVENLY.color);
        }

        @Override
        void bless(SpellContext ctx, LivingEntity t, SpellStats stats, boolean self) {
            if (!self) {
                selfOnly(ctx, "message.isekai_mahou.enchanter.self_only_heavenly");
                return;
            }
            HeavenlyRestriction.activate(ctx, t, stats);
        }

        @Override
        void curse(SpellContext ctx, LivingEntity t, SpellStats stats) {
            HeavenlyRestriction.seal(ctx, t, stats);
        }

        @Override
        public int hintDuration() { return 900; }
    }

    // =====================================================================================
    //  9. Equipamento Encantado (t4) — encanta os itens por um tempo
    // =====================================================================================
    public static class Gear extends EnchantGlyph {
        public Gear() {
            super("enchant_gear", 18);
            tier(4);
            minCircle(4);
            color(EnchantEffect.Kind.GEAR.color);
        }

        @Override
        void bless(SpellContext ctx, LivingEntity t, SpellStats stats, boolean self) {
            int lvl = Enchants.apply(ctx, t, EnchRegistries.GEAR, stats, 900);
            if (t instanceof ServerPlayer sp) GearEnchant.mark(sp);
            blessFx(ctx, t, lvl);
            ctx.level().sendParticles(ParticleTypes.ENCHANT, t.getX(), t.getY() + 1.2, t.getZ(), 40, 0.4, 0.6, 0.4, 1.0);
            EnchFx.sound(ctx.level(), t.position(), SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.8F);
        }

        /** Inverter: "ferrugem" — Exposto e Fraqueza. */
        @Override
        void curse(SpellContext ctx, LivingEntity t, SpellStats stats) {
            Statuses.apply(t, Statuses.Kind.EXPOSED, 1, stats.duration(120), ctx.caster(), ctx.powerMult());
            ctx.status(t, MobEffects.WEAKNESS, 120, 0, stats);
            curseFx(ctx, t);
        }

        @Override
        public int hintDuration() { return 900; }
    }

    // =====================================================================================
    //  Ajudante das formas: só os efeitos benéficos no próprio conjurador
    // =====================================================================================
    /** Aplica no conjurador apenas os efeitos benéficos (não invertidos) — ninguém se queima com o próprio Elo. */
    public static void blessCaster(SpellContext ctx) {
        LivingEntity c = ctx.caster();
        if (c == null) return;
        for (ParsedSpell.EffectEntry entry : ctx.spell().effects()) {
            if (entry.effect().isBeneficial() && !entry.stats().inverted) ctx.applyEffect(entry, c);
        }
    }

    /** Holder do efeito pelo glifo (detalhamento/aba). */
    public static Holder<MobEffect> effectOf(String glyph) {
        return switch (glyph) {
            case MIGHT -> EnchRegistries.MIGHT;
            case HASTE -> EnchRegistries.HASTE;
            case AEGIS -> EnchRegistries.AEGIS;
            case VITALITY -> EnchRegistries.VITALITY;
            case IMBUE -> EnchRegistries.IMBUE;
            case OVERDRIVE -> EnchRegistries.OVERDRIVE;
            case GEAR -> EnchRegistries.GEAR;
            case ANTI_MAGIC -> EnchRegistries.ANTI_MAGIC;
            case HEAVENLY -> EnchRegistries.HEAVENLY;
            default -> null;
        };
    }

    public static void clear() {
        SELF_ONLY_WARN.clear();
    }

    private EnchanterGlyphs() {}
}
