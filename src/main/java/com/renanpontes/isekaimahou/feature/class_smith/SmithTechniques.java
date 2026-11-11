package com.renanpontes.isekaimahou.feature.class_smith;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.class_smith.entity.RhoAiasEntity;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Técnicas de anime do Ferreiro (pacote "Arsenal do Arqueiro de Lâminas"): o escudo Rho Aias segurando golpes da frente,
 * limpeza ao parar o servidor e a entrega das técnicas novas para quem já tinha os ranks das Provas da Forja (save de
 * antes do pacote): os glifos dos ranks alcançados e, no rank V, os pergaminhos das lendas novas.
 */
public final class SmithTechniques {
    /** Pergaminhos das lendas do pacote já entregues (rank V). */
    public static final String LEGENDS_FLAG = "smith.tech_legends";

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SmithTechniques::onIncoming);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> RhoAiasEntity.clearAll());
    }

    /** Rho Aias: golpes da frente vão para as pétalas; o que passar da última segue. */
    private static void onIncoming(LivingIncomingDamageEvent e) {
        LivingEntity target = e.getEntity();
        if (target.level().isClientSide || e.getAmount() <= 0F) return;
        RhoAiasEntity shield = RhoAiasEntity.of(target);
        if (shield == null || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !shield.covers(e.getSource())) return;
        float left = shield.absorb(e.getAmount());
        if (left <= 0F) e.setCanceled(true);
        else e.setAmount(left);
    }

    /** As lendas novas entregues no rank V. */
    public static List<ItemStack> legendScrolls() {
        List<ItemStack> out = new ArrayList<>();
        for (String id : SmithLegends.TECH_LEGENDS) {
            if (Legends.get(id) != null) out.add(Legends.scroll(id));
        }
        return out;
    }

    /** Login: ensina os glifos novos dos ranks já alcançados e entrega as lendas do rank V (uma vez). */
    public static void catchUp(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.SMITH) return;
        int rank = SmithProgress.rank(d);
        List<Glyph> learned = new ArrayList<>();
        for (int r = 1; r <= Math.min(rank, SmithRanks.MAX_RANK); r++) {
            SmithRanks.Rank rk = SmithRanks.rank(r);
            if (rk == null) continue;
            for (String id : rk.glyphs()) {
                ResourceLocation rl = IsekaiMahou.id(id);
                Glyph g = GlyphRegistry.get(rl);
                if (g == null || d.knowsGlyph(rl)) continue;
                MagicApi.learnGlyphSilently(sp, rl);
                learned.add(g);
            }
        }
        if (rank >= SmithRanks.MAX_RANK && !d.hasFlag(LEGENDS_FLAG)) {
            d.setFlag(LEGENDS_FLAG, true);
            d.markDirty();
            Rewards.giveAll(sp, legendScrolls());
        }
        if (learned.isEmpty()) return;
        MutableComponent names = Component.empty();
        for (int i = 0; i < learned.size(); i++) {
            if (i > 0) names.append(Component.literal(", "));
            names.append(learned.get(i).displayName().withColor(MagicPath.SMITH.rgb()));
        }
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.class_smith.tech_catchup", names).withStyle(ChatFormatting.GOLD));
    }

    private SmithTechniques() {}
}
