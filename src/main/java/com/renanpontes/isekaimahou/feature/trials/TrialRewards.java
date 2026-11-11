package com.renanpontes.isekaimahou.feature.trials;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.api.TrialsApi;
import com.renanpontes.isekaimahou.core.item.GlyphScrollItem;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphKind;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Recompensas da vitória (por jogador). Três situações:
 * <ul>
 *   <li><b>Primeira vez</b> (elegível, rank ainda não concluído): tudo cheio + item do Arsenal + marca a conclusão.</li>
 *   <li><b>Desafio</b> (rank já concluído): XP e espólio pela metade; conta a conclusão de novo (contadores).</li>
 *   <li><b>Assistência</b> (sem os requisitos): XP e espólio (60%), sem marcar a Provação.</li>
 * </ul>
 */
final class TrialRewards {
    enum Kind { FIRST, CHALLENGE, ASSIST }

    static void grant(TrialInstance inst, ServerPlayer p, TrialInstance.Member m) {
        Element el = inst.element;
        int rank = inst.rank;
        MagicData d = MagicApi.get(p);
        RandomSource r = p.getRandom();
        boolean cleared = TrialsApi.isCleared(d, el, rank);
        boolean eligible = m.eligible || TrialsApi.meetsRequirements(d, el, rank, new ArrayList<>());
        Kind kind = !eligible ? Kind.ASSIST : cleared ? Kind.CHALLENGE : Kind.FIRST;
        float mult = switch (kind) {
            case FIRST -> 1.0F;
            case CHALLENGE -> 0.5F;
            case ASSIST -> 0.6F;
        };

        int xp = Math.round(150 * rank * mult);
        // XP de elemento só existe para elemento despertado (Assistência de quem não tem o elemento: só XP de nível)
        int elementXp = d.isAwakened(el) ? Math.round(60 * rank * mult) : 0;
        MagicApi.giveXp(p, xp, "trial");
        if (elementXp > 0) MagicApi.giveElementXp(p, el, elementXp);

        List<ItemStack> loot = new ArrayList<>();
        List<Element> bases = TrialTheme.bases(el);
        int essences = Math.max(1, Math.round((2 + rank) * mult));
        int essenceTotal = 0;
        for (Element b : bases) {
            var ess = CoreRegistries.ESSENCES.get(b);
            if (ess == null) continue;
            int n = Math.max(1, (essences + bases.size() - 1) / bases.size());
            loot.add(new ItemStack(ess.get(), n));
            essenceTotal += n;
        }
        var arcane = CoreRegistries.ESSENCES.get(Element.ARCANE);
        if (arcane != null) {
            int n = Math.max(1, Math.round((1 + rank / 2F) * mult));
            loot.add(new ItemStack(arcane.get(), n));
            essenceTotal += n;
        }
        int crystals = Math.max(1, Math.round((2 + 2 * rank) * mult));

        int scrolls = switch (kind) {
            case FIRST -> 1 + (rank >= 4 ? 1 : 0);
            case CHALLENGE -> r.nextFloat() < 0.5F ? 1 : 0;
            case ASSIST -> 1;
        };
        List<Component> glyphNames = new ArrayList<>();
        List<ResourceLocation> taken = new ArrayList<>();
        for (int i = 0; i < scrolls; i++) {
            ResourceLocation g = pickGlyph(el, rank, d, kind == Kind.FIRST, taken, r);
            if (g == null) {
                crystals += 3;   // nenhum glifo novo para dar: vira cristais
                continue;
            }
            taken.add(g);
            loot.add(GlyphScrollItem.of(g));
            Glyph glyph = GlyphRegistry.get(g);
            if (glyph != null) glyphNames.add(glyph.displayName());
        }

        ItemStack armory = kind == Kind.FIRST ? armoryReward(p, d, rank) : ItemStack.EMPTY;
        if (kind == Kind.FIRST && armory.isEmpty()) crystals += 2 + rank;
        loot.add(new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), crystals));
        if (!armory.isEmpty()) loot.add(armory);

        ItemStack legend = ItemStack.EMPTY;
        if (rank >= 6 && (kind == Kind.FIRST || (kind == Kind.CHALLENGE && r.nextFloat() < 0.25F))) {
            legend = Legends.randomScroll(r, d.circle());
            if (!legend.isEmpty()) loot.add(legend);
        }

        Rewards.giveAll(p, loot);
        if (kind != Kind.ASSIST) TrialsApi.markCleared(p, el, rank);

        // ---- feedback
        Component toastTitle = switch (kind) {
            case FIRST -> Component.translatable("toast.isekai_mahou.trials.first");
            case CHALLENGE -> Component.translatable("toast.isekai_mahou.trials.challenge");
            case ASSIST -> Component.translatable("toast.isekai_mahou.trials.assist");
        };
        Component closing = switch (kind) {
            case FIRST -> Component.translatable("message.isekai_mahou.trials.rewards.first");
            case CHALLENGE -> Component.translatable("message.isekai_mahou.trials.rewards.challenge");
            case ASSIST -> Component.translatable("message.isekai_mahou.trials.rewards.assist");
        };
        ItemStack icon = bases.isEmpty() || CoreRegistries.ESSENCES.get(bases.get(0)) == null
                ? new ItemStack(CoreRegistries.MANA_CRYSTAL.get()) : new ItemStack(CoreRegistries.ESSENCES.get(bases.get(0)).get());
        // v1.1: UM aviso de resumo ("Provação concluída! — N recompensas · Fogo III"); os detalhes vão para o registro
        // de avisos do cliente (as linhas abaixo)
        ItemStack summaryIcon = !legend.isEmpty() ? legend : !armory.isEmpty() ? armory : icon;
        Rewards.toast(p, toastTitle, Component.translatable("toast.isekai_mahou.trials.summary", loot.size() + 1,
                TrialsApi.title(el, rank)), summaryIcon, el.color);

        p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.rewards.header", TrialsApi.title(el, rank))
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        p.sendSystemMessage((elementXp > 0
                ? Component.translatable("message.isekai_mahou.trials.rewards.xp", xp, elementXp, el.displayName())
                : Component.translatable("message.isekai_mahou.trials.rewards.xp_only", xp)).withStyle(ChatFormatting.AQUA));
        MutableComponent items = Component.translatable("message.isekai_mahou.trials.rewards.items", essenceTotal, crystals)
                .withStyle(ChatFormatting.GRAY);
        p.sendSystemMessage(items);
        for (Component g : glyphNames) {
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.rewards.glyph", g.copy().withColor(el.color))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (!armory.isEmpty()) {
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.rewards.special", armory.getHoverName())
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (!legend.isEmpty()) {
            p.sendSystemMessage(Component.translatable("message.isekai_mahou.trials.rewards.special", legend.getHoverName())
                    .withStyle(ChatFormatting.GOLD));
        }
        p.sendSystemMessage(closing.copy().withStyle(kind == Kind.ASSIST ? ChatFormatting.GRAY : ChatFormatting.GREEN));
    }

    /**
     * Pergaminho de um glifo do elemento (tier ≤ rank) que o jogador ainda não conhece; fusões incluem os glifos dos
     * pais. Prefere os tiers mais altos disponíveis (os básicos o caminho já ensina). Lendários só no rank VII.
     * Se o jogador já conhece tudo do elemento, vale um glifo (tier ≤ rank) de outro elemento que ele despertou.
     */
    @Nullable
    private static ResourceLocation pickGlyph(Element el, int rank, MagicData d, boolean firstClear, List<ResourceLocation> taken,
                                              RandomSource r) {
        List<Glyph> pool = new ArrayList<>(GlyphRegistry.byElement(el));
        if (el.isFusion()) {
            pool.addAll(GlyphRegistry.byElement(el.parentA));
            pool.addAll(GlyphRegistry.byElement(el.parentB));
        }
        // Na primeira conclusão o caminho ensina na hora o tier do nó liberado: um pergaminho dele seria desperdício.
        int teaches = firstClear ? taughtTier(rank) : 0;
        List<Glyph> ok = usable(pool, rank, d, taken, teaches);
        if (ok.isEmpty()) {
            // Tudo do elemento já é conhecido (o caminho ensina os tiers anteriores): glifo de outro elemento despertado.
            List<Glyph> other = new ArrayList<>();
            for (Element e : Element.values()) {
                if (e == Element.ARCANE || e == el || TrialTheme.bases(el).contains(e) || !d.isAwakened(e)) continue;
                other.addAll(GlyphRegistry.byElement(e));
            }
            ok = usable(other, rank, d, taken, 0);
        }
        if (ok.isEmpty()) return null;
        int best = 0;
        for (Glyph g : ok) best = Math.max(best, g.tier());
        int floor = best - 1;
        List<Glyph> top = ok.stream().filter(g -> g.tier() >= floor).toList();
        // glifos do próprio elemento (não dos pais) têm prioridade numa fusão
        List<Glyph> own = top.stream().filter(g -> g.element() == el).toList();
        List<Glyph> from = !own.isEmpty() && r.nextFloat() < 0.7F ? own : top;
        return from.get(r.nextInt(from.size())).id();
    }

    /**
     * Glifos que valem como recompensa: tier ≤ rank, desconhecidos, sem repetir, lendários só no VII, Leis de Domínio
     * só a partir do VI, nada de Magia de Origem de outra classe e nada do tier que o nó vai ensinar agora.
     */
    private static List<Glyph> usable(List<Glyph> pool, int rank, MagicData d, List<ResourceLocation> taken, int skipTier) {
        MagicPath path = d.path() == null ? MagicPath.NONE : d.path();
        List<Glyph> ok = new ArrayList<>();
        for (Glyph g : pool) {
            if (g.tier() > rank || g.tier() == skipTier || d.knowsGlyph(g.id()) || taken.contains(g.id()) || ok.contains(g)) continue;
            if (g.isLegendary() && rank < 7) continue;
            if (g.kind() == GlyphKind.LAW && rank < 6) continue;
            if (g.origin() != null && g.origin() != MagicPath.NONE && g.origin() != path) continue;
            ok.add(g);
        }
        return ok;
    }

    /**
     * Tier de glifos que o nó do rank ensina ao ser liberado pelo módulo Caminhos (ARCHITECTURE §3: 2 Aprendiz,
     * 3 Adepto, 4 Mestre, 5 Arquimago; Fluidez/Cataclismo/Domínio liberam outras coisas).
     */
    private static int taughtTier(int rank) {
        return switch (rank) {
            case 1 -> 2;
            case 2 -> 3;
            case 4 -> 4;
            case 7 -> 5;
            default -> 0;
        };
    }

    /**
     * Item do Arsenal (módulo armory, por id) na primeira conclusão de cada rank — cada item só uma vez por
     * jogador (flag), para não acumular repetidos entre elementos. Vazio se nada sobrar/existir.
     */
    private static ItemStack armoryReward(ServerPlayer p, MagicData d, int rank) {
        String weapon = switch (d.path() == null ? MagicPath.NONE : d.path()) {
            case WITCH -> "witch_wand_t";
            case WARLOCK -> "pact_blade_t";
            case DRUID -> "druid_staff_t";
            case NECROMANCER -> "reaper_scythe_t";
            case NONE, MAGE -> "mage_staff_t";
            // v1.0: Físicas/Destreza — arma de tier da classe, a herdada do Arsenal ou a genérica da categoria
            default -> com.renanpontes.isekaimahou.core.ClassKits.tieredWeaponPrefix(d.path()) + "_t";
        };
        List<String> options = switch (rank) {
            case 1 -> List.of("vitality_charm");
            case 2 -> List.of(weapon + 1);
            case 3 -> List.of("ward_ring");
            case 4 -> List.of(weapon + 2);
            case 5 -> List.of("arcane_upgrade_template");
            case 6 -> List.of(weapon + 3);
            default -> List.of("archmage_sigil", weapon + 3);   // arma: tier ≤ rank/2
        };
        for (String id : options) {
            Item item = BuiltInRegistries.ITEM.getOptional(IsekaiMahou.id(id)).orElse(null);
            if (item == null) continue;
            boolean repeatable = id.equals("arcane_upgrade_template");
            String flag = "trials.armory." + id;
            if (!repeatable && d.hasFlag(flag)) continue;
            if (!repeatable) {
                d.setFlag(flag, true);
                d.markDirty();
            }
            return new ItemStack(item);
        }
        return ItemStack.EMPTY;
    }

    private TrialRewards() {}
}
