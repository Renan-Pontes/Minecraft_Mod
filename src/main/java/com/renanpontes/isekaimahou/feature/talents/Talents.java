package com.renanpontes.isekaimahou.feature.talents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * API dos talentos (servidor). Outros módulos podem chamar direto ou por reflexão:
 * <ul>
 *   <li>{@link #reset(ServerPlayer)} — o "Altar do Recomeço": devolve TODOS os pontos da classe atual e desfaz a
 *       especialização ({@link #reset(ServerPlayer, boolean)} para escolher);</li>
 *   <li>{@link #subclass(Player)} / {@link #hasSubclass} — especialização da classe atual ("" se não tem);</li>
 *   <li>{@link #stat(Player, String)} — soma de uma passiva ({@link TalentStats});</li>
 *   <li>{@link #addBonusPoints} — pontos extras como recompensa (use com MUITA parcimônia).</li>
 * </ul>
 * Regras (release 1.0): 1 ponto a cada {@link #LEVELS_PER_POINT} níveis (calculado do nível — mundos antigos ganham os
 * pontos sozinhos). A árvore tem MAIS níveis do que pontos (uma build é uma escolha) e cada ramo termina num nó de
 * Maestria repetível, então nunca sobra ponto sem destino. Cada nó pede nível mínimo, pontos nas fileiras acima do
 * ramo e, nas escolhas "um ou outro", que o rival esteja vazio; a especialização abre no Círculo
 * {@link #SUBCLASS_CIRCLE}, é permanente e traz uma fileira de nós só dela. As regras de compra ficam em
 * {@link #check} (as mesmas no servidor e na tela). Nada aqui dá XP nem acelera o upar.
 */
public final class Talents {
    public static final int LEVELS_PER_POINT = 1;
    public static final int SUBCLASS_CIRCLE = 5;
    /** Dicas de primeira vez (texto em {@code hint.isekai_mahou.<id>}). */
    public static final String HINT_POINTS = "talents.unspent", HINT_SUBCLASS = "talents.subclass";

    /** Um modificador por operação em cada atributo (ids fixos: trocar quebraria a limpeza em mundos salvos). */
    private static final ResourceLocation[] MODIFIER_IDS = {
            IsekaiMahou.id("talents_add"), IsekaiMahou.id("talents_mult_base"), IsekaiMahou.id("talents_mult_total")};

    /** Cliente: quem lê a especialização do jogador local (ligado pelo {@code TalentsClient}). */
    public static Supplier<String> clientSubclass = () -> "";

    public enum Result { OK, NO_TREE, UNKNOWN, MAXED, NO_POINTS, LOW_LEVEL, LOCKED, LOW_CIRCLE, ALREADY, LOW_BRANCH, EXCLUSIVE, WRONG_SPEC }

    // =====================================================================================
    //  Leitura
    // =====================================================================================

    public static TalentData data(Player player) {
        return player.getData(TalentsModule.DATA);
    }

    /** Pontos ganhos até o nível {@code level} (sem contar os gastos). */
    public static int earned(int level, int bonus) {
        return Math.max(0, level) / LEVELS_PER_POINT + Math.max(0, bonus);
    }

    /** Pontos livres na árvore da classe atual (nunca negativo). */
    public static int unspent(Player player) {
        MagicData md = MagicApi.get(player);
        TalentData d = data(player);
        return Math.max(0, earned(md.level(), d.bonusPoints()) - d.spent(md.path().id));
    }

    /** Especialização da classe atual ("" se ainda não escolheu). Funciona no cliente para o jogador local. */
    public static String subclass(Player player) {
        if (player.level().isClientSide) return clientSubclass.get();
        return data(player).subclass(MagicApi.get(player).path().id);
    }

    public static boolean hasSubclass(Player player, String id) {
        return id != null && !id.isEmpty() && id.equals(subclass(player));
    }

    /** Passivas somadas do jogador (servidor; vazio até a 1ª aplicação depois de entrar). */
    public static TalentStats stats(Player player) {
        return player.level().isClientSide ? TalentStats.EMPTY : data(player).stats;
    }

    public static double stat(Player player, String key) {
        return stats(player).get(key);
    }

    // =====================================================================================
    //  Regras de compra (iguais no servidor e na tela)
    // =====================================================================================

    /**
     * Pontos que contam para liberar {@code node}: os gastos nas fileiras ACIMA dele no mesmo ramo (ou na mesma fileira
     * de especialização). A Maestria nunca conta.
     */
    public static int groupPoints(TalentTree tree, TalentData d, String cls, TalentTree.Node node) {
        int n = 0;
        for (TalentTree.Node o : tree.nodes()) {
            if (o.kind() == TalentTree.Kind.MASTERY || o.tier() >= node.tier() || !node.sameGroup(o)) continue;
            n += Math.min(d.rank(cls, o.id()), o.max());
        }
        return n;
    }

    /** O jogador pode pôr mais 1 ponto em {@code node}? Devolve o primeiro motivo que impede. */
    public static Result check(TalentTree tree, TalentData d, String cls, TalentTree.Node node, int level, int unspent) {
        if (d.rank(cls, node.id()) >= node.max()) return Result.MAXED;
        if (node.isSpec() && !node.subclass().equals(d.subclass(cls))) return Result.WRONG_SPEC;
        for (String x : node.exclusive()) if (d.rank(cls, x) > 0) return Result.EXCLUSIVE;
        if (level < node.level()) return Result.LOW_LEVEL;
        if (groupPoints(tree, d, cls, node) < node.branchPoints()) return Result.LOW_BRANCH;
        for (Map.Entry<String, Integer> req : node.requires().entrySet()) {
            if (d.rank(cls, req.getKey()) < req.getValue()) return Result.LOCKED;
        }
        return unspent < 1 ? Result.NO_POINTS : Result.OK;
    }

    // =====================================================================================
    //  Escrita
    // =====================================================================================

    /** Gasta 1 ponto no nó (confere tudo de novo no servidor). */
    public static Result spend(ServerPlayer sp, String nodeId) {
        MagicData md = MagicApi.get(sp);
        String cls = md.path().id;
        TalentTree tree = md.path() == MagicPath.NONE ? null : TalentTrees.get(cls);
        if (tree == null) return Result.NO_TREE;
        TalentTree.Node node = tree.node(nodeId);
        if (node == null) return Result.UNKNOWN;
        TalentData d = data(sp);
        Result r = check(tree, d, cls, node, md.level(), unspent(sp));
        if (r != Result.OK) return r;
        d.setRank(cls, nodeId, d.rank(cls, nodeId) + 1);
        refresh(sp);
        return Result.OK;
    }

    /** Escolhe a especialização (permanente até um {@link #reset}). */
    public static Result chooseSubclass(ServerPlayer sp, String id) {
        MagicData md = MagicApi.get(sp);
        String cls = md.path().id;
        TalentTree tree = md.path() == MagicPath.NONE ? null : TalentTrees.get(cls);
        if (tree == null) return Result.NO_TREE;
        if (tree.subclass(id) == null) return Result.UNKNOWN;
        if (md.circle() < SUBCLASS_CIRCLE) return Result.LOW_CIRCLE;
        TalentData d = data(sp);
        if (!d.subclass(cls).isEmpty()) return Result.ALREADY;
        d.setSubclass(cls, id);
        refresh(sp);
        return Result.OK;
    }

    /** Recomeço completo da classe atual: devolve todos os pontos e desfaz a especialização. */
    public static void reset(ServerPlayer sp) {
        reset(sp, true);
    }

    /** Devolve os pontos da classe atual; com {@code includeSubclass}, também desfaz a especialização. */
    public static void reset(ServerPlayer sp, boolean includeSubclass) {
        String cls = MagicApi.get(sp).path().id;
        TalentData d = data(sp);
        d.clearRanks(cls);
        if (includeSubclass) d.setSubclass(cls, "");
        d.dirty = true;
        refresh(sp);
    }

    /** Mesmo que {@link #reset(ServerPlayer)}, para quem só tem um {@code Player} (ignora o lado do cliente). */
    public static void reset(Player player) {
        if (player instanceof ServerPlayer sp) reset(sp, true);
    }

    /** Pontos extras (recompensa rara ou comando de teste). */
    public static void addBonusPoints(ServerPlayer sp, int amount) {
        TalentData d = data(sp);
        d.setBonusPoints(d.bonusPoints() + amount);
        refresh(sp);
    }

    // =====================================================================================
    //  Mundos antigos: conferir a distribuição contra a árvore atual
    // =====================================================================================

    /**
     * Tira da distribuição da classe tudo o que a árvore atual não aceita mais — nó que sumiu, nível acima do máximo,
     * nó de outra especialização, os dois lados de uma escolha, nó sem os pontos/pré-requisitos acima dele — e devolve
     * quantos pontos voltaram ao jogador (pontos não são guardados: tirar o nível do nó já é o reembolso). Nós que
     * continuam existindo e válidos ficam como estavam. O nível do jogador NÃO é conferido aqui (perder nível não
     * desfaz talentos).
     */
    public static int validate(TalentData d, String cls, TalentTree tree) {
        int before = d.spent(cls);
        if (before == 0) return 0;
        String sub = d.subclass(cls);
        for (String id : new ArrayList<>(d.ranks(cls).keySet())) {
            TalentTree.Node n = tree.node(id);
            if (n == null || n.isSpec() && !n.subclass().equals(sub)) d.setRank(cls, id, 0);
            else if (d.rank(cls, id) > n.max()) d.setRank(cls, id, n.max());
        }
        // De cima para baixo: quem ficou sem base (ou é o 2º lado de uma escolha) sai, e isso pode derrubar os de baixo.
        List<TalentTree.Node> order = new ArrayList<>(tree.nodes());
        order.sort((a, b) -> Integer.compare(a.tier(), b.tier()));
        for (TalentTree.Node n : order) {
            if (d.rank(cls, n.id()) <= 0) continue;
            boolean ok = groupPoints(tree, d, cls, n) >= n.branchPoints();
            for (Map.Entry<String, Integer> req : n.requires().entrySet()) {
                if (d.rank(cls, req.getKey()) < req.getValue()) ok = false;
            }
            for (String x : n.exclusive()) {
                TalentTree.Node rival = tree.node(x);
                // o rival que vem antes na árvore (ou o de mais nível) fica
                if (rival != null && d.rank(cls, x) > 0 && order.indexOf(rival) < order.indexOf(n)) ok = false;
            }
            if (!ok) d.setRank(cls, n.id(), 0);
        }
        return before - d.spent(cls);
    }

    // =====================================================================================
    //  Aplicação dos efeitos + sincronia
    // =====================================================================================

    private record AttrKey(ResourceLocation attribute, AttributeModifier.Operation op) {}

    /** Confere 1×/s se algo mudou (talentos, classe trocada por altar/comando, datapack recarregado) e as dicas. */
    static void tick(ServerPlayer sp) {
        TalentData d = data(sp);
        MagicData md = MagicApi.get(sp);
        String cls = md.path().id;
        if (d.dirty || !cls.equals(d.appliedClass) || d.sentTreeVersion != TalentTrees.version()) refresh(sp);
        if (md.path() == MagicPath.NONE || TalentTrees.get(cls) == null) return;
        // Dicas de primeira vez (o Hints não repete e custa uma consulta num mapa)
        if (earned(md.level(), d.bonusPoints()) > d.spent(cls)) Hints.show(sp, HINT_POINTS);
        if (md.circle() >= SUBCLASS_CIRCLE && d.subclass(cls).isEmpty()) Hints.show(sp, HINT_SUBCLASS);
    }

    /** Recalcula modificadores de atributo e passivas da classe atual e manda tudo ao cliente. */
    static void refresh(ServerPlayer sp) {
        TalentData d = data(sp);
        MagicData md = MagicApi.get(sp);
        String cls = md.path().id;
        TalentTree tree = md.path() == MagicPath.NONE ? null : TalentTrees.get(cls);

        // Árvore nova (atualização do mod, datapack) ou especialização trocada: confere a distribuição uma vez
        if (tree != null) {
            String key = cls + "#" + TalentTrees.version() + "#" + d.subclass(cls);
            if (!key.equals(d.checkedKey)) {
                d.checkedKey = key;
                int back = validate(d, cls, tree);
                if (back > 0) {
                    sp.sendSystemMessage(Component.translatable("message.isekai_mahou.talents.refund", back).withColor(0xFFD86B));
                }
            }
        }

        Map<AttrKey, Double> attrs = new HashMap<>();
        Map<String, Double> stats = new HashMap<>();
        if (tree != null) {
            for (TalentTree.Node n : tree.nodes()) {
                int rank = Math.min(d.rank(cls, n.id()), n.max());
                if (rank > 0) collect(n.effects(), n.scale(rank), attrs, stats);
            }
            TalentTree.Subclass sub = tree.subclass(d.subclass(cls));
            if (sub != null) collect(sub.effects(), 1, attrs, stats);
        }

        // Tira o que estava aplicado (em qualquer atributo: a árvore pode ter mudado) e põe o conjunto novo.
        // Modificadores PERMANENTES: ficam no save, então a vida extra não some ao relogar.
        BuiltInRegistries.ATTRIBUTE.holders().forEach(h -> {
            AttributeInstance inst = sp.getAttribute(h);
            if (inst == null) return;
            for (ResourceLocation id : MODIFIER_IDS) inst.removeModifier(id);
        });
        for (Map.Entry<AttrKey, Double> e : attrs.entrySet()) {
            Holder<Attribute> holder = BuiltInRegistries.ATTRIBUTE.getHolder(e.getKey().attribute()).orElse(null);
            AttributeInstance inst = holder == null ? null : sp.getAttribute(holder);
            if (inst == null || e.getValue() == 0.0) continue;
            AttributeModifier.Operation op = e.getKey().op();
            inst.addOrReplacePermanentModifier(new AttributeModifier(MODIFIER_IDS[op.ordinal()], e.getValue(), op));
        }
        if (sp.getHealth() > sp.getMaxHealth()) sp.setHealth(sp.getMaxHealth());

        d.stats = TalentStats.of(stats);
        d.appliedClass = cls;
        d.dirty = false;
        sync(sp, d, cls);
    }

    private static void collect(List<TalentTree.Effect> effects, double scale, Map<AttrKey, Double> attrs, Map<String, Double> stats) {
        for (TalentTree.Effect e : effects) {
            double total = e.amount() * scale;
            if (e.isAttribute()) attrs.merge(new AttrKey(e.attribute(), e.op()), total, Double::sum);
            else stats.merge(e.stat(), total, Double::sum);
        }
    }

    /** Manda os dados; a árvore (JSON) só vai junto quando a classe ou os datapacks mudaram. */
    private static void sync(ServerPlayer sp, TalentData d, String cls) {
        CompoundTag tag = new CompoundTag();
        tag.putString("class", cls);
        tag.put("data", d.serializeNBT(sp.registryAccess()));
        String treeKey = cls + "#" + TalentTrees.version();
        if (!treeKey.equals(d.sentTreeKey)) {
            tag.putString("tree", TalentTrees.json(cls));
            d.sentTreeKey = treeKey;
        }
        d.sentTreeVersion = TalentTrees.version();
        PacketDistributor.sendToPlayer(sp, new TalentsNetwork.Sync(tag));
    }

    private Talents() {}
}
