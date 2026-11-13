package com.renanpontes.isekaimahou.feature.talents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.Progression;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comandos de teste (op):
 * <pre>
 *   /mahou talents info                 — pontos, nós, especialização e passivas somadas (com os tetos)
 *   /mahou talents list                 — nós da árvore da classe atual (id, tipo, fileira, nível e pontos pedidos)
 *   /mahou talents budget               — TABELA DE ORÇAMENTO de todas as classes: níveis da árvore × pontos no nível máximo
 *   /mahou talents validate             — confere TODAS as árvores (nós alcançáveis, pré-requisitos, escolhas, níveis,
 *                                         passivas/atributos/ícones conhecidos, total × pontos)
 *   /mahou talents check                — reconfere a SUA distribuição contra a árvore (o que roda sozinho em mundos antigos)
 *   /mahou talents points &lt;n&gt;           — dá n pontos bônus (0 zera os bônus)
 *   /mahou talents set &lt;nó&gt; &lt;nível&gt;     — põe o nó num nível, sem conferir requisitos
 *   /mahou talents max                  — enche a árvore (um lado de cada escolha, a sua especialização e a Maestria)
 *   /mahou talents subclass &lt;id|none&gt;  — força/limpa a especialização (sem pedir o Círculo 5)
 *   /mahou talents reset                — o mesmo que o Altar do Recomeço (Talents.reset)
 * </pre>
 */
final class TalentsCommands {
    static void register() {
        MahouCommands.extend((root, ctx) -> {
            LiteralArgumentBuilder<CommandSourceStack> t = MahouCommands.op("talents");
            t.then(Commands.literal("info").executes(c -> info(c.getSource())));
            t.then(Commands.literal("list").executes(c -> list(c.getSource())));
            t.then(Commands.literal("budget").executes(c -> budget(c.getSource())));
            t.then(Commands.literal("validate").executes(c -> validateAll(c.getSource())));
            t.then(Commands.literal("check").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                TalentTree tree = tree(sp);
                if (tree == null) return noTree(c.getSource());
                int back = Talents.validate(Talents.data(sp), tree.classId(), tree);
                Talents.refresh(sp);
                c.getSource().sendSuccess(() -> Component.literal("Distribuição conferida: " + back + " ponto(s) devolvido(s); livres: "
                        + Talents.unspent(sp)), false);
                return 1;
            }));
            t.then(Commands.literal("points").then(Commands.argument("n", IntegerArgumentType.integer(0, 500)).executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                int n = IntegerArgumentType.getInteger(c, "n");
                TalentData d = Talents.data(sp);
                d.setBonusPoints(n == 0 ? 0 : d.bonusPoints() + n);
                Talents.refresh(sp);
                c.getSource().sendSuccess(() -> Component.literal("Pontos bônus: " + d.bonusPoints() + " (livres: " + Talents.unspent(sp) + ")"), false);
                return 1;
            })));
            t.then(Commands.literal("set").then(Commands.argument("node", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(nodeIds(c.getSource()), b))
                    .then(Commands.argument("rank", IntegerArgumentType.integer(0, 100)).executes(c -> {
                        ServerPlayer sp = c.getSource().getPlayerOrException();
                        TalentTree tree = tree(sp);
                        String id = StringArgumentType.getString(c, "node");
                        TalentTree.Node node = tree == null ? null : tree.node(id);
                        if (node == null) {
                            c.getSource().sendFailure(Component.literal("Nó desconhecido: " + id));
                            return 0;
                        }
                        int rank = Math.min(node.max(), IntegerArgumentType.getInteger(c, "rank"));
                        Talents.data(sp).setRank(tree.classId(), id, rank);
                        Talents.refresh(sp);
                        c.getSource().sendSuccess(() -> Component.literal(id + " = " + rank + "/" + node.max()), false);
                        return 1;
                    }))));
            t.then(Commands.literal("max").executes(c -> {
                ServerPlayer sp = c.getSource().getPlayerOrException();
                TalentTree tree = tree(sp);
                if (tree == null) return noTree(c.getSource());
                TalentData d = Talents.data(sp);
                String cls = tree.classId(), sub = d.subclass(cls);
                d.clearRanks(cls);
                int total = 0;
                for (TalentTree.Node n : tree.nodes()) {
                    if (n.isSpec() && !n.subclass().equals(sub)) continue;
                    boolean rival = false;
                    for (String x : n.exclusive()) if (d.rank(cls, x) > 0) rival = true;
                    if (rival) continue;
                    d.setRank(cls, n.id(), n.max());
                    total += n.max();
                }
                Talents.refresh(sp);
                int filled = total;
                c.getSource().sendSuccess(() -> Component.literal("Árvore cheia: " + filled + " níveis (com a Maestria)."), false);
                return 1;
            }));
            t.then(Commands.literal("subclass").then(Commands.argument("id", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(subclassIds(c.getSource()), b))
                    .executes(c -> {
                        ServerPlayer sp = c.getSource().getPlayerOrException();
                        TalentTree tree = tree(sp);
                        if (tree == null) return noTree(c.getSource());
                        String id = StringArgumentType.getString(c, "id");
                        if (!id.equals("none") && tree.subclass(id) == null) {
                            c.getSource().sendFailure(Component.literal("Especialização desconhecida: " + id));
                            return 0;
                        }
                        Talents.data(sp).setSubclass(tree.classId(), id.equals("none") ? "" : id);
                        Talents.refresh(sp);
                        c.getSource().sendSuccess(() -> Component.literal("Especialização: " + id), false);
                        return 1;
                    })));
            t.then(Commands.literal("reset").executes(c -> {
                Talents.reset(c.getSource().getPlayerOrException());
                c.getSource().sendSuccess(() -> Component.literal("Talentos e especialização resetados."), false);
                return 1;
            }));
            root.then(t);
        });
    }

    private static TalentTree tree(ServerPlayer sp) {
        return TalentTrees.get(MagicApi.get(sp).path().id);
    }

    private static int noTree(CommandSourceStack src) {
        src.sendFailure(Component.literal("A classe atual não tem árvore de talentos."));
        return 0;
    }

    private static List<String> nodeIds(CommandSourceStack src) {
        List<String> out = new ArrayList<>();
        TalentTree tree = src.getPlayer() == null ? null : tree(src.getPlayer());
        if (tree != null) for (TalentTree.Node n : tree.nodes()) out.add(n.id());
        return out;
    }

    private static List<String> subclassIds(CommandSourceStack src) {
        List<String> out = new ArrayList<>(List.of("none"));
        TalentTree tree = src.getPlayer() == null ? null : tree(src.getPlayer());
        if (tree != null) for (TalentTree.Subclass s : tree.subclasses()) out.add(s.id());
        return out;
    }

    private static int info(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        MagicData md = MagicApi.get(sp);
        String cls = md.path().id;
        TalentData d = Talents.data(sp);
        TalentTree tree = TalentTrees.get(cls);
        StringBuilder sb = new StringBuilder();
        sb.append("Classe ").append(cls).append(tree == null ? " (SEM árvore)" : "").append(" · nível ").append(md.level())
                .append(" · pontos ").append(Talents.earned(md.level(), d.bonusPoints())).append(" (bônus ").append(d.bonusPoints())
                .append("), gastos ").append(d.spent(cls)).append(", livres ").append(Talents.unspent(sp))
                .append("\nEspecialização: ").append(d.subclass(cls).isEmpty() ? "-" : d.subclass(cls))
                .append("\nNós: ").append(new TreeMap<>(d.ranks(cls)))
                .append("\nPassivas (tetos: resistências somadas ").append(TalentCombat.MAX_RESIST).append(", bônus de dano somados ")
                .append(TalentCombat.MAX_BONUS).append(", golpe ×").append(TalentCombat.MAX_MULT).append("):");
        for (Map.Entry<String, Double> e : new TreeMap<>(Talents.stats(sp).all()).entrySet()) {
            sb.append("\n  ").append(e.getKey()).append(" = ").append(Math.round(e.getValue() * 1000.0) / 1000.0);
            Double cap = TalentStats.CAPS.get(e.getKey());
            if (cap != null) sb.append(e.getValue() > cap ? "  (CORTADO no teto " : "  (teto ").append(cap).append(')');
        }
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int list(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        TalentTree tree = tree(sp);
        if (tree == null) return noTree(src);
        StringBuilder sb = new StringBuilder("Árvore " + tree.classId() + " (" + tree.totalRanks() + " níveis + Maestria "
                + tree.masteryRanks() + "):");
        for (TalentTree.Node n : tree.nodes()) {
            sb.append("\n  ").append(n.isSpec() ? n.subclass() : tree.branches().get(n.branch()).id()).append('/').append(n.tier())
                    .append(' ').append(n.id()).append(" [").append(n.kind().name().toLowerCase()).append("] x").append(n.max())
                    .append(" nv").append(n.level()).append(" pts").append(n.branchPoints());
            if (!n.exclusive().isEmpty()) sb.append(" ou ").append(n.exclusive());
        }
        sb.append("\nEspecializações:");
        for (TalentTree.Subclass s : tree.subclasses()) sb.append(' ').append(s.id());
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    // =====================================================================================
    //  Orçamento e validação
    // =====================================================================================

    /** Níveis compráveis de cada árvore × pontos no nível máximo (150) e no escondido (200). */
    private static int budget(CommandSourceStack src) {
        int p150 = Talents.earned(Progression.MAX_LEVEL, 0), p200 = Talents.earned(Progression.HIDDEN_MAX_LEVEL, 0);
        StringBuilder sb = new StringBuilder("Orçamento — 1 ponto a cada " + Talents.LEVELS_PER_POINT + " níveis: " + p150
                + " pontos no nível " + Progression.MAX_LEVEL + ", " + p200 + " no " + Progression.HIDDEN_MAX_LEVEL
                + "\nclasse: níveis compráveis (ramos + 1 especialização) · % que cabe no " + Progression.MAX_LEVEL + " / "
                + Progression.HIDDEN_MAX_LEVEL + " · Maestria (sobra)");
        for (String cls : new TreeSet<>(TalentTrees.classes())) {
            TalentTree tree = TalentTrees.get(cls);
            if (tree == null) continue;
            int total = Math.max(1, tree.totalRanks());
            sb.append("\n  ").append(cls).append(": ").append(total).append(" · ").append(Math.round(100F * p150 / total)).append("% / ")
                    .append(Math.round(100F * p200 / total)).append("% · +").append(tree.masteryRanks());
        }
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /** Confere todas as árvores carregadas. Cada problema vira uma linha; no fim, o resumo por classe. */
    private static int validateAll(CommandSourceStack src) {
        int p150 = Talents.earned(Progression.MAX_LEVEL, 0), p200 = Talents.earned(Progression.HIDDEN_MAX_LEVEL, 0);
        List<String> problems = new ArrayList<>();
        StringBuilder sb = new StringBuilder("Validação das árvores de talentos:");
        for (String cls : new TreeSet<>(TalentTrees.classes())) {
            TalentTree tree = TalentTrees.get(cls);
            if (tree == null) continue;
            int before = problems.size();
            validateTree(tree, problems);
            int total = tree.totalRanks(), mastery = tree.masteryRanks();
            // (13/11/2026) o Renan quer poder pegar quase tudo: a árvore caber no nível 150 agora é o esperado, não problema
            if (total + mastery < p200 + 20) problems.add(cls + ": pouca Maestria para os pontos do nível " + Progression.HIDDEN_MAX_LEVEL);
            sb.append("\n  ").append(cls).append(": ").append(tree.nodes().size()).append(" nós, ").append(total).append(" níveis + ")
                    .append(mastery).append(" de Maestria, ").append(tree.subclasses().size()).append(" especializações — ")
                    .append(problems.size() == before ? "OK" : (problems.size() - before) + " PROBLEMA(S)");
        }
        for (String p : problems) sb.append("\n  ! ").append(p);
        sb.append(problems.isEmpty() ? "\nTudo certo." : "\n" + problems.size() + " problema(s).");
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text), false);
        return problems.isEmpty() ? 1 : 0;
    }

    private static void validateTree(TalentTree tree, List<String> out) {
        String cls = tree.classId();
        Set<String> subs = new HashSet<>();
        for (TalentTree.Subclass s : tree.subclasses()) {
            subs.add(s.id());
            checkEffects(cls + "/" + s.id(), s.effects(), out);
            if (tree.specNodes(s.id()).isEmpty()) out.add(cls + "/" + s.id() + ": especialização sem fileira exclusiva");
            if (!s.icon().isEmpty() && !itemExists(s.icon())) out.add(cls + "/" + s.id() + ": ícone desconhecido " + s.icon());
        }
        if (tree.masteryRanks() == 0) out.add(cls + ": sem nó de Maestria (pontos podem sobrar)");
        for (TalentTree.Node n : tree.nodes()) {
            String where = cls + "/" + n.id();
            if (n.effects().isEmpty()) out.add(where + ": sem efeitos");
            checkEffects(where, n.effects(), out);
            if (!n.icon().isEmpty() && !itemExists(n.icon())) out.add(where + ": ícone desconhecido " + n.icon());
            if (n.level() > Progression.MAX_LEVEL) out.add(where + ": pede nível " + n.level() + " (acima do máximo)");
            if (n.isSpec() && !subs.contains(n.subclass())) out.add(where + ": especialização desconhecida " + n.subclass());
            for (Map.Entry<String, Integer> r : n.requires().entrySet()) {
                TalentTree.Node req = tree.node(r.getKey());
                if (req == null) out.add(where + ": pré-requisito desconhecido " + r.getKey());
                else if (r.getValue() > req.max()) out.add(where + ": pede " + r.getKey() + " acima do máximo dele");
            }
            for (String x : n.exclusive()) {
                TalentTree.Node rival = tree.node(x);
                if (rival == null) out.add(where + ": escolha rival desconhecida " + x);
                else if (!rival.exclusive().contains(n.id())) out.add(where + ": a escolha com " + x + " não é recíproca");
            }
            // alcançável: o máximo de pontos nas fileiras acima (um lado de cada escolha) cobre o que o nó pede
            int above = 0;
            Set<String> counted = new HashSet<>();
            for (TalentTree.Node o : tree.nodes()) {
                if (o.kind() == TalentTree.Kind.MASTERY || o.tier() >= n.tier() || !n.sameGroup(o)) continue;
                boolean rival = false;
                for (String x : o.exclusive()) if (counted.contains(x)) rival = true;
                if (rival) continue;
                counted.add(o.id());
                above += o.max();
            }
            if (above < n.branchPoints()) out.add(where + ": inalcançável (pede " + n.branchPoints() + " pontos acima, o máximo é " + above + ")");
        }
    }

    private static void checkEffects(String where, List<TalentTree.Effect> effects, List<String> out) {
        for (TalentTree.Effect e : effects) {
            if (e.isAttribute()) {
                if (!BuiltInRegistries.ATTRIBUTE.containsKey(e.attribute())) out.add(where + ": atributo desconhecido " + e.attribute());
            } else if (!TalentStats.known(e.stat())) {
                out.add(where + ": passiva desconhecida " + e.stat());
            }
        }
    }

    private static boolean itemExists(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl != null && BuiltInRegistries.ITEM.containsKey(rl);
    }

    private TalentsCommands() {}
}
