package com.renanpontes.isekaimahou.feature.legends_g.glyph;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.legends_g.LgUtil;
import com.renanpontes.isekaimahou.feature.legends_g.entity.GrimoireEntity;
import com.renanpontes.isekaimahou.glyph.AugmentGlyph;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphMastery;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos de Lloyd Frontera (homenagem a "Tensei shitara Dainana Ouji" — o sétimo príncipe otaku de magia). Todos
 * COMUNS: entram no caminho do elemento pelo tier (Arcanos: por nível) e nos sorteios de pergaminhos como qualquer
 * outro glifo.
 * <ul>
 *   <li>{@link #MANA_CORE} Núcleo de Mana (AUMENTO) — o glifo anterior SOBRECARREGA: dano +30% por cópia (até 3),
 *       pagando 3% da mana máxima por cópia. O núcleo brilha no peito ao conjurar.</li>
 *   <li>{@link #CHANTLESS} Sem Encantamento (AUMENTO) — conjuração na hora e sem cântico (custa mais).</li>
 *   <li>{@link Grimoire} Grimório Demoníaco (EFEITO) — invoca o Grim no ombro do alvo (você ou um aliado).</li>
 *   <li>{@link PureMagic} Magia Pura (EFEITO) — dano arcano que cresce com a MAESTRIA total de glifos de quem conjura.</li>
 * </ul>
 */
public final class LloydTechniques {
    public static final int COLOR = 0xC9A8FF;
    public static final int CORE_COLOR = 0x7FE8FF;
    public static final int GRIM_COLOR = 0xB0284A;

    /** Aumento Núcleo de Mana (marcador em {@code SpellStats.custom}; regras em {@code LgEvents}). */
    public static final ResourceLocation MANA_CORE = IsekaiMahou.id("lg_mana_core");
    /** Aumento Sem Encantamento (marcador; o "na hora" é o Instantâneo do núcleo). */
    public static final ResourceLocation CHANTLESS = IsekaiMahou.id("lg_chantless");

    public static List<Glyph> all() {
        AugmentGlyph core = new AugmentGlyph(MANA_CORE.getPath(), 10, s -> s.custom.merge(MANA_CORE, 1, Integer::sum));
        core.element(Element.ARCANE);
        core.tier(4);
        core.minCircle(4);
        // Instantâneo do núcleo (tempo 0, +50% de custo, fora da Pilha) + Silencioso (sem cântico nem anúncio)
        AugmentGlyph chantless = new AugmentGlyph(CHANTLESS.getPath(), 6, s -> {
            s.custom.merge(SpellStats.INSTANT, 1, Integer::sum);
            s.custom.merge(CHANTLESS, 1, Integer::sum);
            s.silent = true;
        });
        chantless.element(Element.ARCANE);
        chantless.tier(4);
        chantless.minCircle(4);
        return List.of(core, chantless, new Grimoire(), new PureMagic());
    }

    // =====================================================================================
    //  Grimório Demoníaco (Grim)
    // =====================================================================================
    /**
     * EFEITO (Sombra, benéfico): invoca o Grim por 20 s (Estender +) no ombro do alvo (você com Si Mesmo, ou um aliado).
     * A cada 1,5 s ele atira uma bala de mana (dano 5) no inimigo mais perto que o dono enxerga; a cada 0,5 s DEVORA
     * projéteis e entidades de magia inimigas a até 5 blocos do dono (cada uma devolve 3% de mana a um jogador); e a
     * cada 3 s engole uma conjuração inimiga pendente por perto (contramágica). Quieto em Modo Paz e em zonas seguras.
     * Um Grim por dono (reconjurar renova).
     */
    public static class Grimoire extends EffectGlyph {
        public Grimoire() {
            super("lg_grimoire", 30);
            element(Element.SHADOW);
            tier(4);
            minCircle(4);
        }

        @Override
        public boolean isBeneficial() { return true; }

        @Override
        public int hintDuration() { return 400; }

        @Override
        public float baseDamage() { return GrimoireEntity.SHOT_DAMAGE; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            if (!(target instanceof LivingEntity owner) || !LgUtil.once(ctx, "grimoire:" + owner.getId())) return;
            int life = Mth.clamp(stats.duration(400), 160, 1200);
            GrimoireEntity.summon(ctx, stats, owner, life);
        }
    }

    // =====================================================================================
    //  Magia Pura
    // =====================================================================================
    /**
     * EFEITO (Arcano): mana pura sem elemento. No alvo: dano 10 × o multiplicador de MAESTRIA de quem conjura —
     * {@code 1 + 0,3·ln(1 + soma/10)} (máx. ×2,5), onde soma = todos os níveis de maestria acima do I de todos os
     * glifos que ele conhece; contra jogadores, metade do bônus. Com Si Mesmo: meio segundo juntando mana e um FEIXE
     * reto (24 blocos, Expandir +) que atravessa até 8 inimigos. Não quebra blocos.
     */
    public static class PureMagic extends EffectGlyph {
        public static final float DAMAGE = 10F;
        public static final int CHARGE_TICKS = 10;

        public PureMagic() {
            super("lg_pure_magic", 24);
            element(Element.ARCANE);
            tier(4);
            minCircle(4);
        }

        @Override
        public float baseDamage() { return DAMAGE; }

        @Override
        public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
            LivingEntity c = ctx.caster();
            if (c == null) return;
            float mult = multOf(c);
            int color = colorOf(mult);
            if (LgUtil.self(ctx, target)) {
                if (LgUtil.once(ctx, "pure_magic_beam")) beam(ctx, stats, c, mult, color);
                return;
            }
            ctx.damage(target, DAMAGE * (target instanceof Player ? pvp(mult) : mult), stats, c, Element.ARCANE);
            ElementFx.impactFx(ctx.level(), LgUtil.center(target), Element.ARCANE, color, mult >= 2F ? 3 : 2, null,
                    Math.min(1.6F, 0.6F + 0.4F * mult));
            if (LgUtil.once(ctx, "pure_magic_sound")) LgUtil.sound(ctx.level(), target.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.2F, 0.7F);
        }

        private void beam(SpellContext ctx, SpellStats stats, LivingEntity c, float mult, int color) {
            ServerLevel level = ctx.level();
            ElementFx.gather(level, c, Element.ARCANE, color, 2F, 3, CHARGE_TICKS);
            LgUtil.sound(level, c.position(), SoundEvents.BEACON_POWER_SELECT, 1.2F, 1.6F);
            if (c instanceof ServerPlayer sp) {
                LgUtil.actionbar(sp, Component.translatable("message.isekai_mahou.legends_g.pure_magic_mastery",
                        masteryScore(MagicApi.get(sp)), String.format(Locale.ROOT, "%.2f", mult)).withStyle(ChatFormatting.LIGHT_PURPLE));
            }
            BossKit.schedule(level, CHARGE_TICKS, () -> {
                if (!c.isAlive() || c.level() != level) return;
                Vec3 dir = c.getLookAngle();
                Vec3 from = c.getEyePosition().add(dir.scale(0.8));
                float length = Mth.clamp(stats.radius(24F), 12F, 48F);
                Vec3 end = LgUtil.rayEnd(level, from, dir, length, c);
                float width = 0.5F + 0.3F * (mult - 1F);
                ElementFx.beam(level, from, end, Element.ARCANE, color, width, mult >= 2F ? 4 : 3);
                LgUtil.sound(level, from, SoundEvents.WARDEN_SONIC_BOOM, 1.2F, 1.5F);
                for (LivingEntity e : LgUtil.foesOnLine(ctx, from, end, width, 8)) {
                    ctx.damage(e, DAMAGE * (e instanceof Player ? pvp(mult) : mult), stats, c, Element.ARCANE);
                }
            });
        }
    }

    static float multOf(@Nullable LivingEntity c) {
        return c instanceof ServerPlayer sp ? masteryMult(masteryScore(MagicApi.get(sp))) : 1.15F;
    }

    static float pvp(float mult) {
        return 1F + (mult - 1F) * 0.5F;
    }

    static int colorOf(float mult) {
        return mult >= 2F ? 0xFFF2C0 : mult >= 1.5F ? 0xCFE8FF : COLOR;
    }

    /** Soma de (nível − 1) de maestria de todos os glifos que o jogador conhece (só leitura da API pública). */
    public static int masteryScore(MagicData d) {
        int sum = 0;
        for (Map.Entry<ResourceLocation, Integer> en : d.glyphMastery().entrySet()) {
            if (!d.knowsGlyph(en.getKey())) continue;
            sum += Math.max(0, GlyphMastery.level(en.getValue()) - 1);
        }
        return sum;
    }

    /** Multiplicador da Magia Pura: 1 + 0,3·ln(1 + soma/10), até ×2,5. */
    public static float masteryMult(int score) {
        return Math.min(2.5F, 1F + 0.3F * (float) Math.log(1.0 + Math.max(0, score) / 10.0));
    }

    private LloydTechniques() {}
}
