package com.renanpontes.isekaimahou.glyph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * release 1.0 — MAESTRIA POR GLIFO. Cada glifo que o jogador conhece ganha XP de USO REAL e sobe de nível
 * (I..{@link #MAX_LEVEL}); o XP fica em {@link MagicData#glyphXp}.
 *
 * <h3>Como ganha XP (sem spam no vazio)</h3>
 * Só quando um EFEITO da magia realmente cai em alguma coisa ({@link SpellContext} avisa):
 * <ul>
 *   <li>inimigo (monstro, jogador rival, mob que mira você): {@link #XP_ENEMY} — conta como "em combate";</li>
 *   <li>outro ser vivo (animal, aldeão): {@link #XP_OTHER};</li>
 *   <li>você mesmo ou um aliado (cura, escudo, buff): {@link #XP_OTHER}, e SÓ durante um combate
 *       (até {@link #COMBAT_WINDOW} ticks depois de acertar um inimigo ou de apanhar);</li>
 *   <li>bloco, com efeito que trabalha em blocos (quebrar, colher...) e que MUDOU o bloco atingido ou um vizinho:
 *       {@link #XP_OTHER}, com intervalo maior (acender pedra ou "quebrar" o ar não conta).</li>
 * </ul>
 * Conjurar no ar não dá nada. Todos os glifos da receita (forma, efeitos, aumentos, gatilhos, ligações, leis) ganham
 * juntos, mas cada glifo só ganha UMA vez a cada {@link #AWARD_INTERVAL} ticks ({@link #BLOCK_INTERVAL} em blocos):
 * Chuva em dez inimigos vale o mesmo que um acerto.
 *
 * <h3>O que o nível dá</h3>
 * <ul>
 *   <li>custo: −{@value #COST_PER_LEVEL_PCT}% por nível acima do I na parte do custo de mana que é daquele glifo (X = −18%);</li>
 *   <li>efeitos: +{@value #POWER_PER_LEVEL_PCT10}‰ de poder por nível acima do I (X = +13,5%) — dano, cura, força...;</li>
 *   <li>visual: círculo de conjuração mais rico quando a média dos glifos principais é alta ({@link #visualTier}).</li>
 * </ul>
 * A curva ({@link #XP_TABLE}) é LENTA de propósito: maestria é o caminho de "sofrer para ficar forte".
 * Mobs, pedestais e lendas não têm maestria (o parser recebe {@code null}).
 *
 * <h3>Maestria INFINITA (depois do X)</h3>
 * O X não é mais o fim: a maestria continua (XI, XII... e depois de {@link #ROMAN_UNTIL} vira "★ 57"). Cada nível
 * depois do X custa {@link #GROWTH}× o anterior (o passo IX→X era 10 000 XP; X→XI = 10 800, XX→XXI ≈ 23 300...), então
 * quem já usou um feitiço 10 000 vezes fica MUITO à frente de quem acabou de aprender — e ninguém chega ao fim
 * ({@link #LEVEL_CAP} ≈ 135 é só o teto do número salvo, mais de um bilhão de usos).
 * O poder depois do X cresce em LOG (sem teto, mas cada vez mais devagar; ~×2 no {@link #LEVEL_CAP}); o desconto de
 * mana tende a 25% e nunca passa disso. As MUTAÇÕES (a magia muda de comportamento/visual em níveis marcados) ficam em
 * {@link MasteryMutations}.
 */
public final class GlyphMastery {
    /** Fim da escada CLÁSSICA (I..X). Depois dele a maestria segue sem fim ("infinita"). */
    public static final int MAX_LEVEL = 10;
    /** Até este nível o número aparece em romano (XX); acima, "★ 21". */
    public static final int ROMAN_UNTIL = 20;
    /** Cada passo depois do X custa este fator × o anterior. */
    public static final double GROWTH = 1.08;
    /** Passo IX→X (o último da escada clássica); os passos infinitos partem dele. */
    private static final int LAST_CLASSIC_STEP = 10000;
    /** XP total para ESTAR em cada nível (índice 0 = nível I). I..X fixos; depois, gerado até caber num int. */
    private static final int[] XP_TABLE;
    /** Maior nível que cabe no XP salvo (int). Na prática inalcançável. */
    public static final int LEVEL_CAP;

    static {
        int[] classic = {0, 100, 300, 700, 1500, 3000, 5500, 9500, 16000, 26000};
        List<Integer> t = new ArrayList<>();
        for (int v : classic) t.add(v);
        long total = classic[classic.length - 1];
        for (int lv = MAX_LEVEL; ; lv++) {
            long step = Math.round(LAST_CLASSIC_STEP * Math.pow(GROWTH, lv - (MAX_LEVEL - 1)));
            if (total + step > Integer.MAX_VALUE) break;
            total += step;
            t.add((int) total);
        }
        XP_TABLE = new int[t.size()];
        for (int i = 0; i < XP_TABLE.length; i++) XP_TABLE[i] = t.get(i);
        LEVEL_CAP = XP_TABLE.length;
    }

    /** Desconto de custo por nível acima do I, em % (só para o texto do javadoc/tela). */
    public static final int COST_PER_LEVEL_PCT = 2;
    /** Poder extra por nível acima do I, em décimos de % (15 = 1,5%). */
    public static final int POWER_PER_LEVEL_PCT10 = 15;

    public static final int XP_ENEMY = 2;
    public static final int XP_OTHER = 1;
    /** Ticks mínimos entre dois ganhos do MESMO glifo (acertos em seres vivos). */
    public static final int AWARD_INTERVAL = 40;
    /** Ticks mínimos entre dois ganhos do mesmo glifo trabalhando em blocos. */
    public static final int BLOCK_INTERVAL = 100;
    /** Por quantos ticks depois de um acerto em inimigo (ou de apanhar) curas/buffs em si/aliados contam. */
    public static final int COMBAT_WINDOW = 300;

    /** Níveis "absurdos": ao chegar neles o servidor inteiro fica sabendo (linha no chat). */
    private static final int[] LEGEND_LEVELS = {25, 50, 75, 100};

    /** Estado temporário por jogador (não salvo): quando cada glifo pode ganhar de novo e o último combate. */
    private static final class State {
        final Map<ResourceLocation, Long> next = new HashMap<>();
        long lastCombat = Long.MIN_VALUE / 2;
        /** Próximo tick em que trabalho em bloco pode valer de novo (evita conferir blocos à toa). */
        long nextBlock = Long.MIN_VALUE / 2;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    /** Chamado uma vez pelo núcleo dos efeitos ({@code SpellFxCore.init}). */
    public static void init() {
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> STATES.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> STATES.clear());
        MahouCommands.extend((root, buildCtx) -> root.then(command()));
        // maestria infinita: mutações (eco, fragmentos, réplicas, arcos, eclipse...)
        MasteryMutations.init();
    }

    // =====================================================================================
    //  Níveis e bônus
    // =====================================================================================

    /** Nível (1..{@link #LEVEL_CAP}) para um total de XP (busca binária na tabela). */
    public static int level(int xp) {
        int lo = 0, hi = XP_TABLE.length - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (xp >= XP_TABLE[mid]) lo = mid;
            else hi = mid - 1;
        }
        return lo + 1;
    }

    /** XP total necessário para estar no nível {@code level} (1..{@link #LEVEL_CAP}). */
    public static int xpForLevel(int level) {
        return XP_TABLE[Math.max(1, Math.min(LEVEL_CAP, level)) - 1];
    }

    /** Teto do XP salvo (o número é um int). Saves antigos (≤ 26 000) continuam valendo igual. */
    public static int maxXp() {
        return Integer.MAX_VALUE;
    }

    /** Nível depois do X (a parte "infinita")? */
    public static boolean isInfinite(int level) {
        return level > MAX_LEVEL;
    }

    /** Nível de maestria do jogador num glifo (1 se nunca usou; 1 se {@code data} é null). */
    public static int level(@Nullable MagicData data, ResourceLocation glyph) {
        return data == null ? 1 : level(data.glyphXp(glyph));
    }

    /** Progresso (0..1) dentro do nível atual; 1 só no {@link #LEVEL_CAP}. */
    public static float progress(int xp) {
        int l = level(xp);
        if (l >= LEVEL_CAP) return 1F;
        long from = XP_TABLE[l - 1], to = XP_TABLE[l];
        return Math.max(0F, Math.min(1F, (xp - from) / (float) (to - from)));
    }

    /**
     * Desconto de custo no nível dado: I..X = 2% por nível (0..0,18); depois do X sobe devagar rumo a 25% e nunca
     * chega lá (XX ≈ 20,3%, L ≈ 22,7%, C ≈ 23,7%) — mana grátis quebraria o jogo.
     */
    public static float costDiscount(int level) {
        int l = Math.max(1, level);
        float d = (Math.min(MAX_LEVEL, l) - 1) * COST_PER_LEVEL_PCT / 100F;
        if (l > MAX_LEVEL) d += 0.07F * (1F - 1F / (1F + (l - MAX_LEVEL) / 20F));
        return d;
    }

    /**
     * Multiplicador de poder dos efeitos no nível dado: I..X = +1,5% por nível (1..1,135); depois do X cresce em LOG,
     * sem teto mas cada vez mais devagar: XV ×1,34 · XX ×1,45 · ★30 ×1,58 · ★50 ×1,73 · ★100 ×1,92 · ★135 ×2,0.
     */
    public static float powerMult(int level) {
        int l = Math.max(1, level);
        float p = 1F + (Math.min(MAX_LEVEL, l) - 1) * POWER_PER_LEVEL_PCT10 / 1000F;
        if (l > MAX_LEVEL) p += infiniteBonus(l);
        return p;
    }

    /** Parte "infinita" do poder (0 até o X): 0,25 × ln(1 + (nível − 10) / 4). */
    private static float infiniteBonus(int level) {
        return level <= MAX_LEVEL ? 0F : 0.25F * (float) Math.log(1.0 + (level - MAX_LEVEL) / 4.0);
    }

    /** Poder contra JOGADORES (PvP): a parte depois do X conta pela metade (ex.: ★100 = ×1,53 em vez de ×1,92). */
    public static float powerMultPvp(int level) {
        return powerMult(Math.min(MAX_LEVEL, Math.max(1, level))) + infiniteBonus(level) * 0.5F;
    }

    /**
     * Desconto de mana (0..0,18) que a maestria dá a uma receita inteira: média dos descontos dos glifos, ponderada
     * pelo custo de cada um (glifos de custo zero ou negativo não entram) — a mesma conta do {@link SpellParser}.
     */
    public static float spellDiscount(@Nullable MagicData data, List<ResourceLocation> glyphs) {
        if (data == null || glyphs == null) return 0F;
        float weight = 0F, off = 0F;
        for (ResourceLocation id : glyphs) {
            Glyph g = GlyphRegistry.get(id);
            if (g == null || g.cost() <= 0) continue;
            weight += g.cost();
            off += g.cost() * costDiscount(level(data, id));
        }
        return weight > 0F ? off / weight : 0F;
    }

    /** Rótulo do nível: romano até {@link #ROMAN_UNTIL} (I..XX), depois "★ 21", "★ 57"... */
    public static String roman(int level) {
        int l = Math.max(1, level);
        if (l > ROMAN_UNTIL) return "★ " + l;
        StringBuilder sb = new StringBuilder();
        int[] val = {10, 9, 5, 4, 1};
        String[] sym = {"X", "IX", "V", "IV", "I"};
        for (int i = 0; i < val.length; i++) {
            while (l >= val[i]) { sb.append(sym[i]); l -= val[i]; }
        }
        return sb.toString();
    }

    /** Número compacto para telas: 950 · 12,3k · 4,56M. */
    public static String compact(long n) {
        long a = Math.abs(n);
        if (a < 10000) return Long.toString(n);
        if (a < 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fk", n / 1000.0);
        if (a < 1_000_000_000) return String.format(java.util.Locale.ROOT, "%.2fM", n / 1_000_000.0);
        return String.format(java.util.Locale.ROOT, "%.2fB", n / 1_000_000_000.0);
    }

    /**
     * Nível MÉDIO da forma e dos efeitos de todos os estágios (o mesmo critério do {@link #visualTier}); 0 = magia sem
     * maestria (mob/pedestal). Usado pelas mutações visuais da magia inteira ({@link MasteryMutations}).
     */
    public static float averageLevel(@Nullable ParsedSpell spell) {
        if (spell == null) return 0F;
        int sum = 0, n = 0;
        for (ParsedSpell s : spell.stages()) {
            sum += Math.max(1, s.formStats().masteryLevel);
            n++;
            for (ParsedSpell.EffectEntry e : s.effects()) {
                sum += Math.max(1, e.stats().masteryLevel);
                n++;
            }
        }
        return n == 0 ? 0F : sum / (float) n;
    }

    /**
     * Força do visual pela maestria (0..3): média dos níveis da forma e dos efeitos de TODOS os estágios da magia.
     * média ≥ 4 → 1 (selo interno), ≥ 7 → 2 (círculo maior e onda), 10 → 3 (camada extra e runas).
     * Magia de mob/pedestal (sem maestria nos stats) = 0.
     */
    public static int visualTier(@Nullable ParsedSpell spell) {
        if (spell == null) return 0;
        int sum = 0, n = 0;
        for (ParsedSpell s : spell.stages()) {
            sum += Math.max(1, s.formStats().masteryLevel);
            n++;
            for (ParsedSpell.EffectEntry e : s.effects()) {
                sum += Math.max(1, e.stats().masteryLevel);
                n++;
            }
        }
        if (n == 0) return 0;
        float avg = sum / (float) n;
        return avg >= MAX_LEVEL ? 3 : avg >= 7F ? 2 : avg >= 4F ? 1 : 0;
    }

    // =====================================================================================
    //  Ganho de XP (o SpellContext chama)
    // =====================================================================================

    /** Um efeito da magia acabou de ser aplicado numa entidade. */
    static void onEntity(SpellContext ctx, Entity target) {
        ServerPlayer sp = masteryPlayer(ctx);
        if (sp == null || !(target instanceof LivingEntity le) || target instanceof ArmorStand) return;
        long now = ctx.level().getGameTime();
        State st = STATES.computeIfAbsent(sp.getUUID(), k -> new State());
        if (target == sp || ctx.isAlly(target)) {
            // cura/buff em si ou em aliado: só no meio de uma luta (senão dava para upar parado em casa)
            if (!inCombat(sp, st, now)) return;
            award(sp, st, ctx.recipe(), XP_OTHER, AWARD_INTERVAL, now);
            return;
        }
        boolean enemy = le instanceof Enemy || le instanceof Player || (le instanceof Mob m && m.getTarget() == sp);
        if (enemy) st.lastCombat = now;
        award(sp, st, ctx.recipe(), enemy ? XP_ENEMY : XP_OTHER, AWARD_INTERVAL, now);
    }

    /**
     * Antes de um efeito de bloco agir: guarda o estado do bloco atingido e dos 6 vizinhos — ou devolve null quando
     * nem adianta conferir (não é jogador, ou o trabalho em bloco ainda está no intervalo).
     */
    @Nullable
    static BlockState[] blockSnapshot(SpellContext ctx, BlockPos pos) {
        ServerPlayer sp = masteryPlayer(ctx);
        if (sp == null || pos == null) return null;
        State st = STATES.get(sp.getUUID());
        long now = ctx.level().getGameTime();
        if (st != null && now < st.nextBlock && st.nextBlock - now <= BLOCK_INTERVAL) return null;
        return snapshot(ctx, pos);
    }

    private static BlockState[] snapshot(SpellContext ctx, BlockPos pos) {
        Direction[] dirs = Direction.values();
        BlockState[] out = new BlockState[dirs.length + 1];
        out[0] = ctx.level().getBlockState(pos);
        for (int i = 0; i < dirs.length; i++) out[i + 1] = ctx.level().getBlockState(pos.relative(dirs[i]));
        return out;
    }

    /** Um efeito que trabalha em blocos (quebrar, colher, construir...) acabou de agir: vale se algo mudou ali. */
    static void onBlock(SpellContext ctx, BlockPos pos, BlockState[] before) {
        ServerPlayer sp = masteryPlayer(ctx);
        if (sp == null) return;
        BlockState[] after = snapshot(ctx, pos);
        boolean changed = false;
        for (int i = 0; i < after.length && !changed; i++) changed = after[i] != before[i];
        if (!changed) return;
        State st = STATES.computeIfAbsent(sp.getUUID(), k -> new State());
        long now = ctx.level().getGameTime();
        st.nextBlock = now + BLOCK_INTERVAL;
        award(sp, st, ctx.recipe(), XP_OTHER, BLOCK_INTERVAL, now);
    }

    /** O jogador de verdade que conjurou pela tecla (mobs, FakePlayer, pedestais e lendas não têm maestria). */
    @Nullable
    private static ServerPlayer masteryPlayer(SpellContext ctx) {
        if (!ctx.isPlayerCast()) return null;
        ServerPlayer sp = ctx.casterPlayer();
        return sp == null || sp instanceof FakePlayer || sp.isSpectator() ? null : sp;
    }

    private static boolean inCombat(ServerPlayer sp, State st, long now) {
        if (now - st.lastCombat <= COMBAT_WINDOW) return true;
        return sp.getLastHurtByMob() != null && sp.tickCount - sp.getLastHurtByMobTimestamp() <= COMBAT_WINDOW;
    }

    private static void award(ServerPlayer sp, State st, SpellRecipe recipe, int points, int interval, long now) {
        if (recipe == null || recipe.isEmpty()) return;
        MagicData data = MagicApi.get(sp);
        Set<ResourceLocation> seen = new HashSet<>();
        List<LevelUp> leveled = null;
        for (ResourceLocation id : recipe.glyphs()) {
            if (!seen.add(id)) continue;
            Glyph g = GlyphRegistry.get(id);
            // criativo conjura glifos que não conhece: esses não acumulam nada
            if (g == null || !data.knowsGlyph(id)) continue;
            Long next = st.next.get(id);
            if (next != null && now < next && now >= next - BLOCK_INTERVAL) continue;
            st.next.put(id, now + interval);
            int before = data.glyphXp(id);
            if (before >= maxXp()) continue;
            int after = (int) Math.min(maxXp(), (long) before + points);
            data.setGlyphXp(id, after);
            int from = level(before), to = level(after);
            if (to > from) {
                if (leveled == null) leveled = new ArrayList<>();
                leveled.add(new LevelUp(g, from, to));
            }
        }
        if (leveled != null) announce(sp, leveled);
    }

    /** Um glifo que subiu de nível nesta conjuração (de {@code from} para {@code to}). */
    private record LevelUp(Glyph glyph, int from, int to) {}

    /**
     * Aviso curto (barra de ação, um só) + som baixo só para o jogador. Depois do X: cada MUTAÇÃO nova vira uma linha
     * no chat do próprio jogador (com som de "algo mudou"), e os níveis absurdos ({@link #LEGEND_LEVELS}) avisam o
     * servidor inteiro.
     */
    private static void announce(ServerPlayer sp, List<LevelUp> leveled) {
        LevelUp first = leveled.get(0);
        String lv = roman(first.to());
        MutableComponent msg = leveled.size() == 1
                ? Component.translatable("message.isekai_mahou.mastery.up", first.glyph().displayName(), lv)
                : Component.translatable("message.isekai_mahou.mastery.up_more", first.glyph().displayName(), lv, leveled.size() - 1);
        sp.displayClientMessage(msg.withColor(first.to() > MAX_LEVEL ? 0x8FE8FF : 0xFFD86B), true);
        sp.playNotifySound(SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.6F, first.to() > MAX_LEVEL ? 0.8F : 1.5F);
        boolean mutated = false;
        for (LevelUp up : leveled) {
            for (MasteryMutations.Mut m : MasteryMutations.mutations(up.glyph())) {
                if (m.level() <= up.from() || m.level() > up.to()) continue;
                mutated = true;
                sp.sendSystemMessage(Component.translatable("message.isekai_mahou.mastery.mutation",
                                up.glyph().displayName(), m.displayName(), roman(m.level()))
                        .withColor(0xC58CFF)
                        .append(Component.literal(" — ").withStyle(ChatFormatting.DARK_GRAY))
                        .append(m.description().withStyle(ChatFormatting.GRAY)));
            }
            for (int legend : LEGEND_LEVELS) {
                if (legend <= up.from() || legend > up.to()) continue;
                sp.server.getPlayerList().broadcastSystemMessage(Component.translatable(
                        "message.isekai_mahou.mastery.legend", sp.getDisplayName(), up.glyph().displayName(), roman(legend))
                        .withColor(legend >= 75 ? 0xFF5A7A : 0x7CFFB0), false);
            }
        }
        if (mutated) sp.playNotifySound(SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.PLAYERS, 0.7F, 0.6F);
    }

    // =====================================================================================
    //  Comandos (/mahou mastery ...): "list" é de qualquer jogador; o resto é de teste (op)
    // =====================================================================================

    private static LiteralArgumentBuilder<CommandSourceStack> command() {
        return Commands.literal("mastery")
                .executes(GlyphMastery::cmdList)
                .then(Commands.literal("list").executes(GlyphMastery::cmdList))
                // maestria infinita: a curva (qualquer jogador) e as mutações de um glifo
                .then(Commands.literal("curve").executes(GlyphMastery::cmdCurve))
                .then(Commands.literal("info")
                        .then(Commands.argument("glyph", ResourceLocationArgument.id())
                                .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                                        GlyphRegistry.all().stream().map(Glyph::id), b))
                                .executes(GlyphMastery::cmdInfo)))
                .then(MahouCommands.op("set")
                        .then(Commands.argument("glyph", ResourceLocationArgument.id())
                                .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                                        GlyphRegistry.all().stream().map(Glyph::id), b))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, LEVEL_CAP))
                                        .executes(c -> cmdSet(c, IntegerArgumentType.getInteger(c, "level"))))))
                .then(MahouCommands.op("add")
                        .then(Commands.argument("glyph", ResourceLocationArgument.id())
                                .suggests((c, b) -> SharedSuggestionProvider.suggestResource(
                                        GlyphRegistry.all().stream().map(Glyph::id), b))
                                .then(Commands.argument("xp", IntegerArgumentType.integer(-2_000_000_000, 2_000_000_000))
                                        .executes(GlyphMastery::cmdAdd))))
                .then(MahouCommands.op("all")
                        .then(Commands.argument("level", IntegerArgumentType.integer(1, LEVEL_CAP))
                                .executes(GlyphMastery::cmdAll)))
                .then(MahouCommands.op("reset").executes(GlyphMastery::cmdReset));
    }

    /** Os 10 glifos com mais maestria de quem chamou. */
    private static int cmdList(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        MagicData data = MagicApi.get(sp);
        List<Map.Entry<ResourceLocation, Integer>> top = new ArrayList<>(data.glyphMastery().entrySet());
        top.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        if (top.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.mastery.none"), false);
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.mastery.header", top.size())
                .withStyle(ChatFormatting.GOLD), false);
        for (int i = 0; i < Math.min(10, top.size()); i++) {
            Map.Entry<ResourceLocation, Integer> e = top.get(i);
            Glyph g = GlyphRegistry.get(e.getKey());
            Component name = g != null ? g.displayName() : Component.literal(e.getKey().toString());
            int xp = e.getValue();
            int lv = level(xp);
            String next = lv >= LEVEL_CAP ? "MAX" : compact(xp) + "/" + compact(xpForLevel(lv + 1));
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.mastery.line", name, roman(lv), next), false);
        }
        return top.size();
    }

    /** Tabela da curva: XP total, usos (acertos em inimigo) e bônus em níveis marcados. */
    private static int cmdCurve(CommandContext<CommandSourceStack> c) {
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.mastery.curve_header", LEVEL_CAP)
                .withStyle(ChatFormatting.GOLD), false);
        for (int lv : new int[] {5, 10, 11, 12, 15, 20, 25, 30, 40, 50, 75, 100, LEVEL_CAP}) {
            int xp = xpForLevel(lv);
            String power = String.format(java.util.Locale.ROOT, "%.1f", (powerMult(lv) - 1F) * 100F);
            String cost = String.format(java.util.Locale.ROOT, "%.1f", costDiscount(lv) * 100F);
            c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.mastery.curve_line",
                    roman(lv), compact(xp), compact(xp / XP_ENEMY), power, cost), false);
        }
        return LEVEL_CAP;
    }

    /** Nível, progresso, mutações ativas e as próximas de um glifo (do próprio jogador). */
    private static int cmdInfo(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        Glyph g = glyphArg(c);
        if (g == null) return 0;
        int xp = MagicApi.get(sp).glyphXp(g.id());
        int lv = level(xp);
        String next = lv >= LEVEL_CAP ? "MAX" : compact(xp) + "/" + compact(xpForLevel(lv + 1));
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.mastery.line", g.displayName(), roman(lv), next)
                .withStyle(ChatFormatting.GOLD), false);
        Component family = MasteryMutations.familyName(MasteryMutations.family(g));
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.mastery.family", family)
                .withStyle(ChatFormatting.GRAY), false);
        for (MasteryMutations.Mut m : MasteryMutations.mutations(g)) {
            boolean on = lv >= m.level();
            MutableComponent line = Component.literal((on ? "✦ " : "· ") + roman(m.level()) + " ")
                    .append(on ? m.displayName() : Component.literal("???"));
            if (on) line.append(Component.literal(" — ").append(m.description()));
            c.getSource().sendSuccess(() -> line.withStyle(on ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.DARK_GRAY), false);
        }
        return lv;
    }

    private static Glyph glyphArg(CommandContext<CommandSourceStack> c) {
        Glyph g = GlyphRegistry.get(ResourceLocationArgument.getId(c, "glyph"));
        if (g == null) c.getSource().sendFailure(Component.translatable("command.isekai_mahou.mastery.bad_glyph"));
        return g;
    }

    private static int cmdSet(CommandContext<CommandSourceStack> c, int level) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        Glyph g = glyphArg(c);
        if (g == null) return 0;
        setLevel(sp, g.id(), level);
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.mastery.set", g.displayName(), roman(level)), false);
        return level;
    }

    /** Soma XP como se fosse uso de verdade: passa pelos avisos de nível/mutação (bom para testar). */
    private static int cmdAdd(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        Glyph g = glyphArg(c);
        if (g == null) return 0;
        int from = level(MagicApi.get(sp).glyphXp(g.id()));
        int xp = addXp(sp, g.id(), IntegerArgumentType.getInteger(c, "xp"));
        int to = level(xp);
        if (to > from) announce(sp, List.of(new LevelUp(g, from, to)));
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.mastery.xp", g.displayName(), compact(xp), roman(to)), false);
        return xp;
    }

    /** Todos os glifos CONHECIDOS vão para o nível pedido. */
    private static int cmdAll(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        int level = IntegerArgumentType.getInteger(c, "level");
        MagicData data = MagicApi.get(sp);
        int n = 0;
        for (ResourceLocation id : new ArrayList<>(data.glyphs())) {
            data.setGlyphXp(id, xpForLevel(level));
            n++;
        }
        final int count = n;
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.mastery.all", count, roman(level)), false);
        return n;
    }

    private static int cmdReset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer sp = c.getSource().getPlayerOrException();
        MagicData data = MagicApi.get(sp);
        for (ResourceLocation id : new ArrayList<>(data.glyphMastery().keySet())) data.setGlyphXp(id, 0);
        STATES.remove(sp.getUUID());
        c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.mastery.reset"), false);
        return 1;
    }

    /** Define o nível de maestria de um glifo (1..{@link #LEVEL_CAP}) direto, sem aviso. */
    public static void setLevel(ServerPlayer sp, ResourceLocation glyph, int level) {
        MagicApi.get(sp).setGlyphXp(glyph, xpForLevel(level));
    }

    /** Soma XP de uso num glifo (com teto, sem estourar o int), sem checar intervalo. Retorna o XP novo. */
    public static int addXp(ServerPlayer sp, ResourceLocation glyph, int xp) {
        MagicData data = MagicApi.get(sp);
        long sum = (long) data.glyphXp(glyph) + xp;
        int after = (int) Math.max(0L, Math.min(maxXp(), sum));
        data.setGlyphXp(glyph, after);
        return after;
    }

    private GlyphMastery() {}
}
