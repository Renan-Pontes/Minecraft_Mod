package com.renanpontes.isekaimahou.feature.class_knight;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.class_knight.glyph.KnightGlyphs;
import com.renanpontes.isekaimahou.feature.class_knight.oath.KnightOaths;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Pacote de técnicas de anime do Cavaleiro Rúnico: onde cada técnica nova é liberada e a entrega para quem já tinha os
 * Juramentos/espadas antes do pacote (login).
 * <ul>
 *   <li>Juramento I → Vento Invisível (Golpe do Ar); II → Lâmina Anti-Magia; IV → Balmung;</li>
 *   <li>despertar a Caliburn → Caliburn (Espada da Seleção); despertar a Espada do Lago → Avalon;</li>
 *   <li>Juramento V → as 3 lendas do pacote.</li>
 * </ul>
 */
public final class KnightTechniques {
    /** Pergaminhos das lendas do pacote já entregues. */
    public static final String LEGENDS_FLAG = "knight.tech_legends";

    /** Técnica ligada ao feito de despertar a espada (ou null). */
    @Nullable
    public static String swordTechnique(LegendSword s) {
        return switch (s) {
            case CALIBURN -> KnightGlyphs.CALIBURN_STRIKE;
            case LAKE -> KnightGlyphs.AVALON;
            default -> null;
        };
    }

    public static void catchUp(ServerPlayer p) {
        MagicData d = MagicApi.get(p);
        if (d.path() != MagicPath.KNIGHT) return;
        List<String> ids = new ArrayList<>();
        int oath = KnightOaths.rank(d);
        for (KnightOaths.Rank r : KnightOaths.RANKS) {
            if (r.number() <= oath) ids.addAll(r.glyphs());
        }
        for (LegendSword s : LegendSword.values()) {
            String tech = swordTechnique(s);
            if (tech != null && d.hasFlag(s.flag())) ids.add(tech);
        }
        List<Glyph> learned = new ArrayList<>();
        for (String id : ids) {
            ResourceLocation rl = IsekaiMahou.id(id);
            Glyph g = GlyphRegistry.get(rl);
            if (g == null || d.knowsGlyph(rl)) continue;
            MagicApi.learnGlyphSilently(p, rl);
            learned.add(g);
        }
        if (oath >= KnightOaths.MAX_RANK && !d.hasFlag(LEGENDS_FLAG)) {
            d.setFlag(LEGENDS_FLAG, true);
            d.markDirty();
            List<ItemStack> scrolls = new ArrayList<>();
            for (String id : KnightLegends.TECH_LEGENDS) {
                ItemStack st = Legends.scroll(id);
                if (!st.isEmpty()) scrolls.add(st);
            }
            Rewards.giveAll(p, scrolls);
        }
        if (learned.isEmpty()) return;
        MutableComponent names = Component.empty();
        for (int i = 0; i < learned.size(); i++) {
            if (i > 0) names.append(Component.literal(", "));
            names.append(learned.get(i).displayName().withColor(KnightFx.GOLD));
        }
        p.sendSystemMessage(Component.translatable("message.isekai_mahou.class_knight.tech_catchup", names).withStyle(ChatFormatting.GOLD));
    }

    private KnightTechniques() {}
}
