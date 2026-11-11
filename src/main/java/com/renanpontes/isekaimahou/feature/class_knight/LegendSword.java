package com.renanpontes.isekaimahou.feature.class_knight;

import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.class_knight.oath.Offer;
import com.renanpontes.isekaimahou.feature.class_knight.oath.Req;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * As seis Espadas Mágicas Lendárias do Cavaleiro Rúnico. Cada uma é despertada no Altar do Juramento cumprindo um
 * FEITO (requisitos + oferenda + condição do lugar) e tem habilidade própria (Liberação, eco nos golpes e passiva).
 * <ul>
 *   <li><b>Caliburn</b> — a espada na pedra: o primeiro chefe vencido de lâmina na mão.</li>
 *   <li><b>Lâmina Carmesim</b> (fogo), <b>Espada da Tempestade</b> (vento/raio), <b>Espada do Gelo Eterno</b> (água)
 *   e <b>Espada das Trevas</b> (sombra) — o coração do guardião do elemento e abates mágicos do elemento.</li>
 *   <li><b>Espada do Lago</b> (homenagem a Excalibur) — Caliburn se parte no altar cercado de água e a Dama do Lago
 *   devolve a lâmina verdadeira; potencializa a forma G "Excalibur".</li>
 * </ul>
 */
public enum LegendSword {
    CALIBURN("knight_caliburn", Element.LIGHT, 0xFFD86B, 6.5F, Site.NONE,
            List.of(Req.oath(2), Req.counter(KnightCounters.BOSSES, 1, "req.isekai_mahou.class_knight.bosses")),
            List.of(new Offer("minecraft:gold_block", 2), new Offer("isekai_mahou:essence_light", 4),
                    new Offer("isekai_mahou:essence_earth", 4))),
    FLAME("knight_flame_sword", Element.FIRE, 0xFF6A2B, 6.5F, Site.NONE,
            List.of(Req.oath(2), Req.counter("kill.spell.fire", 40, "req.isekai_mahou.class_knight.kill_fire")),
            List.of(new Offer("isekai_mahou:guardian_core_fire", 1), new Offer("minecraft:blaze_rod", 8),
                    new Offer("isekai_mahou:essence_fire", 4))),
    STORM("knight_storm_sword", Element.WIND, 0x7FDBFF, 6.5F, Site.STORM,
            List.of(Req.oath(2), Req.counter("kill.spell.wind", 40, "req.isekai_mahou.class_knight.kill_wind")),
            List.of(new Offer("isekai_mahou:guardian_core_wind", 1), new Offer("minecraft:copper_ingot", 16),
                    new Offer("isekai_mahou:essence_wind", 4))),
    FROST("knight_frost_sword", Element.WATER, 0xA8E8FF, 6.5F, Site.ICE,
            List.of(Req.oath(2), Req.counter("kill.spell.water", 40, "req.isekai_mahou.class_knight.kill_water")),
            List.of(new Offer("isekai_mahou:guardian_core_water", 1), new Offer("minecraft:blue_ice", 8),
                    new Offer("isekai_mahou:essence_water", 4))),
    DARK("knight_dark_sword", Element.SHADOW, 0x8E5BD6, 6.5F, Site.NIGHT,
            List.of(Req.oath(3), Req.counter("kill.spell.shadow", 50, "req.isekai_mahou.class_knight.kill_shadow")),
            List.of(new Offer("isekai_mahou:guardian_core_shadow", 1), new Offer("minecraft:wither_skeleton_skull", 1),
                    new Offer("isekai_mahou:essence_shadow", 6))),
    LAKE("knight_lake_sword", Element.LIGHT, 0xCFF2FF, 7.5F, Site.LAKE,
            List.of(Req.oath(4), Req.flag(KnightCounters.SWORD_FLAG + "knight_caliburn", "req.isekai_mahou.class_knight.has_caliburn"),
                    Req.counter("kill.spell.light", 60, "req.isekai_mahou.class_knight.kill_light")),
            List.of(new Offer("isekai_mahou:knight_caliburn", 1), new Offer("isekai_mahou:guardian_core_light", 1),
                    new Offer("minecraft:heart_of_the_sea", 1), new Offer("isekai_mahou:essence_light", 8)));

    public final String id;
    public final Element element;
    public final int rgb;
    /** Bônus de dano do material (SimpleTier) — a Espada do Lago corta um pouco mais. */
    public final float damageBonus;
    public final Site site;
    public final List<Req> reqs;
    public final List<Offer> offers;

    LegendSword(String id, Element element, int rgb, float damageBonus, Site site, List<Req> reqs, List<Offer> offers) {
        this.id = id;
        this.element = element;
        this.rgb = rgb;
        this.damageBonus = damageBonus;
        this.site = site;
        this.reqs = reqs;
        this.offers = offers;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Flag gravada quando a espada foi despertada (uma vez por jogador). */
    public String flag() {
        return KnightCounters.SWORD_FLAG + id;
    }

    public MutableComponent displayName() {
        return KnightText.item(id).withColor(rgb);
    }

    /** Nome do feito ("A Espada na Pedra"...). */
    public MutableComponent featName() {
        return KnightText.tr("sword", key(), "feat");
    }

    /** Texto curto do feito (o que fazer). */
    public MutableComponent featHint() {
        return KnightText.tr("sword", key(), "feat", "desc");
    }

    /** Nome da Liberação (botão direito). */
    public MutableComponent abilityName() {
        return KnightText.tr("sword", key(), "ability");
    }

    public MutableComponent abilityDesc() {
        return KnightText.tr("sword", key(), "ability", "desc");
    }

    public MutableComponent passiveDesc() {
        return KnightText.tr("sword", key(), "passive");
    }

    public static LegendSword byId(String id) {
        for (LegendSword s : values()) {
            if (s.id.equals(id) || s.key().equals(id)) return s;
        }
        return null;
    }

    /** Condição do LUGAR na hora de despertar (em volta do altar). */
    public enum Site {
        NONE, LAKE, ICE, STORM, NIGHT;

        public MutableComponent text() {
            return KnightText.tr("site", name().toLowerCase(java.util.Locale.ROOT));
        }

        /** Confere a condição em volta do altar. */
        public boolean check(ServerLevel level, BlockPos altar) {
            return switch (this) {
                case NONE -> true;
                case LAKE -> count(level, altar, 4, s -> s.getFluidState().is(Fluids.WATER) && s.getFluidState().isSource()) >= 8;
                case ICE -> count(level, altar, 3, s -> s.is(BlockTags.ICE) || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW)) >= 6;
                case STORM -> level.isThundering() || count(level, altar, 3, s -> s.is(Blocks.LIGHTNING_ROD)) >= 2;
                case NIGHT -> level.isNight() || level.dimensionType().hasFixedTime() && !level.dimensionType().hasSkyLight();
            };
        }

        private static int count(ServerLevel level, BlockPos c, int r, java.util.function.Predicate<BlockState> test) {
            int n = 0;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dx = -r; dx <= r; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -r; dz <= r; dz++) {
                        p.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                        if (test.test(level.getBlockState(p))) n++;
                    }
                }
            }
            return n;
        }
    }
}
