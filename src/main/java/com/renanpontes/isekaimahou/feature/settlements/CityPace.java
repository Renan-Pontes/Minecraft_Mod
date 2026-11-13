package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprints;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.ConstructionEngine;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * RITMO DA CIDADE (onda 6 — "deixe o up da cidade lento em dias, mas que seja legal! demore dias pra construir e upar
 * as coisas, mas fazer que valha a pena!"). Toda obra tem uma DURAÇÃO EM DIAS do Minecraft e anda pelo tempo do mundo:
 * <ul>
 *   <li><b>Duração</b>: a tabela {@link #BASE} (dias de um prédio novo, por chave) e as fórmulas de {@link #nominal}
 *       (melhoria, muralha, degrau...). Um lugar só para ajustar; {@link CityPaceConfig#PACE} multiplica tudo;</li>
 *   <li><b>O tempo passa sozinho</b>: {@link #tickServer} soma os dias passados (hora do Overworld — dormir e
 *       {@code /time add} contam) em TODAS as cidades fundadas, com o coração carregado ou não. A obra física
 *       ({@link ConstructionEngine}) só coloca blocos até a fração de dias já trabalhada: longe, o tempo corre; ao
 *       voltar, os construtores recuperam rápido o que "já estava feito";</li>
 *   <li><b>Etapas</b> ({@link #stageOf}): terreno e fundação → andaime e estrutura → paredes → telhado → acabamento,
 *       no letreiro do canteiro ("dia X de Y"), no painel e no Diário da cidade;</li>
 *   <li><b>Ajuda do jogador</b> (um pouco, nunca obra instantânea): entregar materiais ({@link #deliver}), trabalhar
 *       no canteiro (agachar + clicar com a mão vazia, {@link #onRightClick}), cidade feliz, festival, construtores
 *       sobrando, e a magia de obra com limite diário ({@link #magicLeft});</li>
 *   <li><b>Vale a pena</b>: {@link #finished} faz a inauguração (sino, fogos, "antes → depois") e avisa os ganchos.</li>
 * </ul>
 * <b>Para outros sistemas</b> (comerciantes, monumentos): {@link #register} (dias de uma planta nova),
 * {@link #onWorkDone} (obra concluída), {@link #onNewDay} (novo dia da cidade) e {@link #paceMult}.
 */
public final class CityPace {
    /** Ticks de um dia do Minecraft. */
    public static final int DAY = 24000;
    /** Dias que uma cidade "recupera" de uma vez (servidor parado não conta; isto só limita saltos de /time). */
    public static final float MAX_CATCHUP = 40F;
    /** Entradas guardadas no Diário da cidade. */
    public static final int DIARY_MAX = 30;

    // =====================================================================================
    //  TABELA DE DURAÇÕES (dias do Minecraft, com "ritmo da cidade" = 1) — ajuste AQUI
    // =====================================================================================
    /** Dias de um prédio NOVO (nível 1), por chave da planta. Chave fora da tabela: {@link #DEFAULT_DAYS}. */
    private static final Map<String, Float> BASE = new HashMap<>();
    /** Planta sem linha na tabela (prédio comum) e maravilha sem linha. */
    public static final float DEFAULT_DAYS = 3F, DEFAULT_WONDER_DAYS = 7F;
    /** A praça da fundação (a primeira obra: curta, para a cidade começar logo). */
    public static final float PLAZA_DAYS = 0.25F;
    /** Aplainar/demolir um lote e consertar um prédio danificado. */
    public static final float TERRAIN_DAYS = 0.25F, REPAIR_DAYS = 0.5F;
    /** Muralha: a do anel 1 leva {@code WALL_DAYS}, cada anel de fora +{@code WALL_RING_DAYS}, até {@code WALL_MAX_DAYS}. */
    public static final float WALL_DAYS = 3F, WALL_RING_DAYS = 1F, WALL_MAX_DAYS = 5F;
    /** Reforma de uma muralha para o grau do degrau novo; praça refeita em outro estilo (Reforma). */
    public static final float WALL_RENOVATION_DAYS = 3F, PLAZA_RESTYLE_DAYS = 3F;
    /** Subir de degrau (obra do centro): para Vila, Burgo, Cidade, Capital. */
    private static final float[] CENTER_DAYS = {0F, 5F, 6F, 8F, 10F};
    /** Melhoria para o nível N: dias do prédio novo × (UP_BASE + UP_STEP × N) → ×1,2 · ×1,5 · ×1,8 · ×2,1. */
    public static final float UP_BASE = 0.6F, UP_STEP = 0.3F;
    /** Refazer um prédio no estilo da cidade: fração dos dias do prédio novo. */
    public static final float RESTYLE_FACTOR = 0.75F;
    /** Prédio mudado de lugar volta no nível que tinha: +25% dos dias por nível acima do 1. */
    public static final float MOVE_LEVEL_FACTOR = 0.25F;
    /** Nenhuma obra passa disto (nem com fórmula). */
    public static final float MAX_DAYS = 10F;

    static {
        // ~1 dia: casa pequena e obras miúdas
        days(1F, "house_small", "well", "garden", "statue", "arch", "trap_field", "watchtower", "arcane_beacon");
        days(1.5F, "granary", "farm");
        // 2–3 dias: prédios comuns
        days(2F, "house_large", "builders_hall", "port", "stable", "sawmill", "workshop", "mine", "smithy", "hospital");
        days(3F, "market", "barracks", "tavern", "library", "arena", "guild", "mana_spire");
        // 3–5 dias: prédios-chave
        days(4F, "temple", Blueprint.TOWN_HALL, "arcane_ward", "null_obelisk", "obelisk");
        for (String t : Blueprints.ELEMENT_TEMPLES) days(4F, t);
        days(5F, "magic_tower");
        // 5–10 dias: grandes obras
        days(7F, "sphinx");
        days(8F, "castle", "pyramid");
    }

    private static void days(float d, String... keys) {
        for (String k : keys) BASE.put(k, d);
    }

    // ---- ajuda do jogador e bônus (todos "um pouco": a espera continua)
    /** Teto dos bônus passivos somados (estilo, prédios, felicidade, festival). */
    public static final float MAX_SPEED = 2F;
    public static final float FESTIVAL_SPEED = 1.15F, MISERABLE_SPEED = 0.75F;
    /** Cada construtor sobrando soma isto (dividido entre as obras em andamento), até {@link #MAX_HELPER_BONUS}. */
    public static final float HELPER_BONUS = 0.10F, MAX_HELPER_BONUS = 0.5F;
    /** Materiais entregues adiantam no máximo esta fração da obra; {@link #MATERIAL_POINTS} pontos = 1 dia. */
    public static final float GIFT_CAP = 0.25F;
    public static final int MATERIAL_POINTS = 192;
    /** Trabalhar no canteiro: dias por martelada e o teto por obra por dia. */
    public static final float HAND_SWING = 0.004F, HANDS_DAILY = 0.15F;
    private static final int SWING_COOLDOWN = 8;
    /** Magia de obra (Impulso + Erguer): teto de dias ganhos por obra por dia. */
    public static final float MAGIC_DAILY = 0.75F;

    private static final Map<UUID, Long> LAST_SWING = new HashMap<>();

    // =====================================================================================
    //  Duração (API)
    // =====================================================================================
    /**
     * Registra (ou troca) os dias de um prédio novo de chave {@code key}. Monumentos e prédios de outros sistemas
     * chamam isto no init; as melhorias de nível saem sozinhas da fórmula ({@link #upgradeDays}).
     */
    public static void register(String key, float days) {
        BASE.put(key, Mth.clamp(days, 0.25F, MAX_DAYS));
    }

    /** Dias nominais de um prédio novo (nível 1) desta chave. */
    public static float baseDays(String key) {
        Float d = BASE.get(key);
        if (d != null) return d;
        if (Blueprint.PLAZA.equals(key)) return PLAZA_DAYS;
        if (Blueprint.WALL.equals(key)) return WALL_DAYS;
        for (SettlementStyle st : SettlementStyle.values()) {
            Blueprint bp = Blueprints.get(st, key);
            if (bp != null) return bp.isWonder() ? DEFAULT_WONDER_DAYS : DEFAULT_DAYS;
        }
        return DEFAULT_DAYS;
    }

    /** Dias nominais da melhoria de um prédio PARA o nível {@code toLevel} (2..5): cada nível custa mais. */
    public static float upgradeDays(String key, int toLevel) {
        return half(Math.min(MAX_DAYS, baseDays(key) * (UP_BASE + UP_STEP * Math.max(2, toLevel))));
    }

    /** Dias nominais da muralha do anel {@code k} (1 = a de dentro). */
    public static float wallDays(int k) {
        return Math.min(WALL_MAX_DAYS, WALL_DAYS + WALL_RING_DAYS * Math.max(0, k - 1));
    }

    /** Dias nominais da obra de subir PARA o degrau {@code toLevel} (2..5). */
    public static float centerDays(int toLevel) {
        return CENTER_DAYS[Mth.clamp(toLevel, 1, CENTER_DAYS.length) - 1];
    }

    /** Dias nominais de uma obra, pelo tipo dela (é isto que {@link #ensure} grava em {@link Project#days}). */
    public static float nominal(Settlement s, Project p) {
        float d = switch (p.kind) {
            case Project.KIND_TERRAIN -> TERRAIN_DAYS;
            case Project.KIND_REPAIR -> REPAIR_DAYS;
            case Project.KIND_CENTER -> p.level > s.level ? centerDays(p.level) : PLAZA_RESTYLE_DAYS;
            case Project.KIND_TIER -> WALL_RENOVATION_DAYS;
            case Project.KIND_RESTYLE -> half(baseDays(p.key) * RESTYLE_FACTOR);
            case Project.KIND_UPGRADE -> upgradeDays(p.key, p.level);
            default -> Blueprint.WALL.equals(p.key) ? wallDays(CityLayout.wallOf(p))
                    : Blueprint.PLAZA.equals(p.key) ? PLAZA_DAYS
                    : half(baseDays(p.key) * (1F + MOVE_LEVEL_FACTOR * Math.max(0, p.level - 1)));
        };
        return Mth.clamp(d, 0.25F, MAX_DAYS);
    }

    private static float half(float d) {
        return Math.max(0.5F, Math.round(d * 2F) / 2F);
    }

    /** "Ritmo da cidade" do servidor: multiplicador da DURAÇÃO das obras (1 = normal; 2 = o dobro de dias). */
    public static float paceMult() {
        return CityPaceConfig.pace();
    }

    /** Dias de verdade (com o ritmo do servidor) para uma duração nominal — é o número que o jogador lê. */
    public static float real(float nominalDays) {
        return nominalDays * paceMult();
    }

    /** "½ dia", "1 dia", "2½ dias" — para durações já em dias de verdade ({@link #real}). */
    public static MutableComponent daysText(float realDays) {
        float h = Math.max(0.5F, Math.round(realDays * 2F) / 2F);
        int whole = (int) h;
        boolean frac = h - whole >= 0.5F;
        String n = whole == 0 ? "½" : frac ? whole + "½" : Integer.toString(whole);
        return SettlementsLang.tr(h <= 1F ? "pace.day" : "pace.days", n);
    }

    // =====================================================================================
    //  Estado de uma obra
    // =====================================================================================
    /**
     * Garante que a obra tem duração. Obra nova: a da tabela. Obra de mundo antigo (ou que já nasce adiantada, como o
     * conserto): mantém o que já estava de pé — os dias trabalhados começam na fração física atual.
     */
    public static void ensure(MinecraftServer server, Settlement s, Project p) {
        if (p.days > 0F) return;
        p.days = nominal(s, p);
        float start = startOf(p);
        float done = Mth.clamp((SettlementService.progressOf(server, s, p) - start) / (1F - start), 0F, 1F);
        p.worked = Math.max(p.worked, done * p.days);
        // obra de mundo antigo já no meio do caminho: não é "obra nova" para o Diário (etapa 0 = ainda não começou)
        if (done > 0.01F) p.stage = stageOf(target(p));
    }

    /** Fração dos DIAS já trabalhados (0..1) — o que as barras e o "dia X de Y" mostram. */
    public static float fraction(Project p) {
        return p.days <= 0F ? 0F : Mth.clamp(p.worked / p.days, 0F, 1F);
    }

    /**
     * Onde a obra física começa na escala de {@link Project#progress}: degrau, conserto e reforma de muralha não
     * limpam nem fundam nada (nascem na fase dos blocos, que vale de 20% em diante da peça).
     */
    private static float startOf(Project p) {
        boolean skips = Project.KIND_CENTER.equals(p.kind) || Project.KIND_REPAIR.equals(p.kind) || Project.KIND_TIER.equals(p.kind);
        return skips ? 0.20F / Math.max(1, p.pieces.size()) : 0F;
    }

    /** Até que ponto da obra FÍSICA ({@link Project#progress}) os dias já trabalhados deixam os construtores chegar. */
    public static float target(Project p) {
        float start = startOf(p);
        return start + (1F - start) * fraction(p);
    }

    public static boolean timeDone(Project p) {
        return p.days > 0F && p.worked >= p.days - 1.0E-4F;
    }

    /** Total de dias de verdade da obra, arredondado para cima ("dia X de <b>Y</b>"). */
    public static int totalDays(Project p) {
        return Math.max(1, Mth.ceil(real(p.days) - 1.0E-3F));
    }

    /** Em que dia a obra está ("dia <b>X</b> de Y"). */
    public static int dayNumber(Project p) {
        return Mth.clamp((int) Math.floor(real(p.worked) + 1.0E-3F) + 1, 1, totalDays(p));
    }

    /** Etapa pelo ponto da obra física ({@link #target}): 1 terreno e fundação, 2 andaime e estrutura, 3 paredes, 4 telhado, 5 acabamento. */
    public static int stageOf(float fraction) {
        if (fraction < 0.20F) return 1;
        float b = (fraction - 0.20F) / 0.78F;
        return b < 0.30F ? 2 : b < 0.65F ? 3 : b < 0.92F ? 4 : 5;
    }

    /** Nome da etapa em que a obra está (muralhas: o trecho da vez). */
    public static MutableComponent stageText(Project p) {
        if (p.pieces.size() > 1) {
            int n = p.pieces.size();
            return SettlementsLang.tr("pace.stage.piece", Mth.clamp((int) (fraction(p) * n) + 1, 1, n), n);
        }
        if (Project.KIND_TERRAIN.equals(p.kind)) return SettlementsLang.tr("pace.stage.1");
        return SettlementsLang.tr("pace.stage." + stageOf(target(p)));
    }

    /** Multiplicador passivo da cidade: estilo, prédios de obra, felicidade, festival (teto {@link #MAX_SPEED}). */
    public static float speed(MinecraftServer server, Settlement s) {
        float v = s.style.buildSpeed * (1F + s.speedPct() / 100F);
        if (Lordship.festivalActive(server, s)) v *= FESTIVAL_SPEED;
        if (s.affinity < CityHappiness.MISERABLE) v *= MISERABLE_SPEED;
        return Mth.clamp(v, 0.5F, MAX_SPEED);
    }

    /** Quantas obras andam ao mesmo tempo (uma por construtor). */
    public static int crews(Settlement s) {
        return Math.max(1, s.population(Settlement.Role.BUILDER));
    }

    /** As obras que estão recebendo dias agora: as primeiras da fila que ainda têm dias a cumprir, uma por construtor. */
    public static List<Project> working(MinecraftServer server, Settlement s) {
        List<Project> out = new ArrayList<>();
        int crews = crews(s);
        for (Project p : s.projects) {
            ensure(server, s, p);
            if (timeDone(p) || ConstructionEngine.waiting(s, p)) continue;
            // onda 7 (sky_island): obra esperando a terra do lote não gasta dias — a vez passa para a próxima da fila
            if (SkyCity.waitingLand(s, p)) continue;
            // onda 7 (city_pause): obra pausada (ou sendo desmontada) não recebe dias — nem na recuperação de dias
            // com a cidade descarregada; a vez passa para a próxima da fila
            if (p.paused || p.undo) continue;
            out.add(p);
            if (out.size() >= crews) break;
        }
        return out;
    }

    /** Dias de verdade que faltam para a obra, no ritmo de agora (obra parada na fila: só a duração dela). */
    public static float remaining(MinecraftServer server, Settlement s, Project p) {
        return real(Math.max(0F, p.days - p.worked)) / speed(server, s);
    }

    // =====================================================================================
    //  O tempo passa
    // =====================================================================================
    /** A cada segundo: soma às obras de todas as cidades fundadas o tempo que passou no mundo. */
    public static void tickServer(MinecraftServer server) {
        long now = server.overworld().getDayTime();
        long game = server.overworld().getGameTime();
        // mundo com o ciclo do dia parado (doDaylightCycle false): as obras seguem o tempo de jogo, senão nunca andariam
        boolean frozen = !server.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT);
        for (Settlement s : SettlementData.get(server).all()) {
            if (!s.own()) continue;
            long played = s.paceGame < 0 ? 0 : Math.max(0, game - s.paceGame);
            s.paceGame = game;
            if (s.paceTime < 0 || now < s.paceTime) {
                // primeira vez (mundo antigo) ou o relógio voltou (/time set): começa a contar daqui
                s.paceTime = now;
                continue;
            }
            long delta = Math.max(now - s.paceTime, frozen ? played : 0);
            s.paceTime = now;
            if (delta <= 0) continue;
            if (!s.projects.isEmpty()) advance(server, s, Math.min(MAX_CATCHUP, delta / (float) DAY), delta <= 200);
        }
    }

    /**
     * Passa {@code realDays} dias para as obras da cidade. Cada construtor toca uma obra; quando uma cumpre os dias
     * dela, o tempo que sobra vai para a próxima da fila (voltar depois de dias longe adianta a fila inteira).
     *
     * @param live tempo passando agora (o Impulso de Obra só conta assim, nunca em salto de tempo)
     */
    public static void advance(MinecraftServer server, Settlement s, float realDays, boolean live) {
        float left = realDays;
        float base = speed(server, s) / paceMult();
        long day = SettlementService.day(server);
        long game = server.overworld().getGameTime();
        for (int guard = 0; guard < s.projects.size() + 2 && left > 1.0E-6F; guard++) {
            List<Project> act = working(server, s);
            if (act.isEmpty()) return;
            int spare = Math.max(0, s.population(Settlement.Role.BUILDER) - act.size());
            float rate = base * (1F + Math.min(MAX_HELPER_BONUS, HELPER_BONUS * spare / act.size()));
            float dt = left;
            for (Project p : act) dt = Math.min(dt, (p.days - p.worked) / rate);
            dt = Math.max(dt, 1.0E-5F);
            for (Project p : act) {
                float add = dt * rate;
                if (live && p.magicUntil > game && p.magicMult > 1F) {
                    float extra = Math.min(magicLeft(server, p), add * (p.magicMult - 1F));
                    if (extra > 0F) {
                        spendMagic(day, p, extra);
                        add += extra;
                    }
                }
                work(server, s, p, add);
            }
            left -= dt;
        }
    }

    /** Soma dias de trabalho a uma obra (qualquer origem) e anota no Diário o começo e as mudanças de etapa. */
    public static void work(MinecraftServer server, Settlement s, Project p, float nominalDays) {
        ensure(server, s, p);
        // onda 7 (city_pause): obra pausada não avança, venha o trabalho de onde vier (dias, materiais, mãos, magia)
        if (p.paused || p.undo) return;
        boolean fresh = p.stage == 0;
        p.worked = Math.min(p.days, p.worked + Math.max(0F, nominalDays));
        boolean logged = p.pieces.size() == 1 && !Project.KIND_TERRAIN.equals(p.kind) && !Project.KIND_REPAIR.equals(p.kind);
        if (fresh && p.worked > 0F && !Project.KIND_TERRAIN.equals(p.kind)) {
            diary(server, s, SettlementsLang.tr("pace.diary.start", CityPlanning.projectName(s, p), daysText(real(p.days))));
        }
        int stage = stageOf(target(p));
        if (stage != p.stage) {
            // só obras de 2 dias ou mais contam as etapas no Diário (casa de 1 dia encheria a lista)
            if (logged && !fresh && stage > p.stage && p.days >= 2F && !timeDone(p)) {
                diary(server, s, SettlementsLang.tr("pace.diary.stage", CityPlanning.projectName(s, p), SettlementsLang.tr("pace.stage." + stage)));
            }
            p.stage = stage;
        }
    }

    /** A obra cumpre todos os dias agora (comandos de teste, conserto pago): a parte física termina em seguida. */
    public static void complete(MinecraftServer server, Settlement s, Project p) {
        ensure(server, s, p);
        p.worked = p.days;
        p.stage = 5;
    }

    // =====================================================================================
    //  Ganchos (API para comerciantes, monumentos...)
    // =====================================================================================
    /** Obra concluída: chamado DEPOIS de a cidade registrar o resultado (prédio no mapa, nível novo gravado...). */
    @FunctionalInterface
    public interface WorkDone {
        /**
         * @param p     a obra ({@code p.key} = chave da planta, {@code p.kind} = build/upgrade/repair/..., {@code p.cell}
         *              = lote, {@code p.level} = nível com que o prédio ficou)
         * @param grand obra grande (5 dias ou mais): teve cerimônia
         */
        void done(ServerLevel sl, Settlement s, Project p, boolean grand);
    }

    /** Novo dia da cidade: chamado com o CORAÇÃO CARREGADO (dá para mexer em blocos e NPCs). */
    @FunctionalInterface
    public interface NewDay {
        /**
         * @param day     dia do Overworld que começou ({@link SettlementService#day})
         * @param elapsed dias passados desde a última chamada: 1 no dia a dia; mais quando a cidade ficou descarregada
         *                (ou o tempo saltou) — é a "recuperação": vem UMA chamada com o total
         */
        void dawn(ServerLevel sl, Settlement s, long day, int elapsed);
    }

    private static final List<WorkDone> DONE = new ArrayList<>();
    private static final List<NewDay> NEW_DAY = new ArrayList<>();

    /** Registra um ouvinte de "obra concluída" (no init do módulo; roda no servidor). */
    public static void onWorkDone(WorkDone listener) {
        DONE.add(listener);
    }

    /** Registra um ouvinte de "novo dia da cidade" (no init do módulo; roda no servidor, cidade fundada carregada). */
    public static void onNewDay(NewDay listener) {
        NEW_DAY.add(listener);
    }

    /** Coração carregado, uma vez por segundo: se o dia virou (ou a cidade acordou dias depois), avisa os ouvintes. */
    public static void tickHeart(ServerLevel sl, Settlement s) {
        long today = SettlementService.day(sl.getServer());
        if (s.paceDay < 0 || today < s.paceDay) {
            s.paceDay = today;
            return;
        }
        if (today == s.paceDay) return;
        int elapsed = (int) Math.min(1000, today - s.paceDay);
        s.paceDay = today;
        fireNewDay(sl, s, today, elapsed);
    }

    /** Dispara o gancho de novo dia (também usado pelos comandos de teste). */
    public static void fireNewDay(ServerLevel sl, Settlement s, long day, int elapsed) {
        for (NewDay l : NEW_DAY) {
            try {
                l.dawn(sl, s, day, Math.max(1, elapsed));
            } catch (RuntimeException e) {
                // um ouvinte com defeito não pode travar o coração da cidade
                com.mojang.logging.LogUtils.getLogger().error("[isekai_mahou] ouvinte de novo dia da cidade falhou", e);
            }
        }
    }

    // =====================================================================================
    //  Amanhecer: resumo e Diário
    // =====================================================================================
    /** Uma linha no chat de quem governa, ao amanhecer: o que anda, o que terminou ontem e o que falta. */
    public static void dawn(MinecraftServer server, Settlement s) {
        if (!s.own()) return;
        int done = s.paceDone;
        s.paceDone = 0;
        if (s.projects.isEmpty() && done == 0) return;
        List<ServerPlayer> govs = CityEconomy.governors(server, s);
        if (govs.isEmpty()) return;
        List<Project> act = working(server, s);
        MutableComponent body = Component.empty();
        int shown = 0;
        for (Project p : act) {
            if (shown >= 2) break;
            if (shown++ > 0) body.append(" · ");
            body.append(SettlementsLang.tr("pace.dawn.item", CityPlanning.projectName(s, p), dayNumber(p), totalDays(p)));
        }
        int rest = s.projects.size() - shown;
        // onda 7 (city_pause): com tudo pausado o resumo diz isso (em vez de "esperando a vez")
        if (shown == 0) body.append(SettlementsLang.tr(s.projects.isEmpty() ? "pace.dawn.idle" : CityPause.allPaused(s) ? "pause.dawn.all" : "pace.dawn.waiting"));
        else if (rest > 0) body.append(SettlementsLang.tr("pace.dawn.queue", rest));
        if (done > 0) body.append(SettlementsLang.tr("pace.dawn.done", done));
        Component line = SettlementsLang.tr("pace.dawn", SettlementService.cityName(server, s), body).withColor(s.style.color);
        for (ServerPlayer g : govs) g.displayClientMessage(line, false);
    }

    /** Escreve uma linha no Diário da cidade (aparece no painel, em Obras → Diário). */
    public static void diary(MinecraftServer server, Settlement s, Component text) {
        CompoundTag e = new CompoundTag();
        e.putLong("Day", SettlementService.day(server));
        e.putString("Text", Component.Serializer.toJson(text, server.registryAccess()));
        s.diary.add(e);
        while (s.diary.size() > DIARY_MAX) s.diary.remove(0);
    }

    // =====================================================================================
    //  Inauguração (obra concluída)
    // =====================================================================================
    /**
     * Chamado pelo {@link SettlementService#onProjectDone} depois de a cidade registrar o resultado: Diário, uma linha
     * de "o que mudou" para quem governa, sino e fogos no prédio (cerimônia curta e sem travar ninguém) e os ganchos.
     */
    public static void finished(ServerLevel sl, Settlement s, Project p) {
        MinecraftServer server = sl.getServer();
        ensure(server, s, p);
        // aplainar um lote não é "obra concluída" para o resumo da manhã
        if (!Project.KIND_TERRAIN.equals(p.kind)) s.paceDone++;
        boolean grand = p.days >= 5F && !Project.KIND_REPAIR.equals(p.kind);
        Component name = CityPlanning.projectName(s, p);
        boolean building = p.cell >= 0 && s.buildings.containsKey(p.cell);
        Component line = null;
        boolean ceremony = false;
        switch (p.kind) {
            case Project.KIND_TERRAIN -> { }
            // a obra do centro é a subida de degrau (a festa é do CityTier) — ou só a praça refeita numa Reforma de estilo
            case Project.KIND_CENTER -> diary(server, s, s.tierDay == SettlementService.day(server)
                    ? SettlementsLang.tr("pace.diary.rank", CityTier.name(s.level)) : SettlementsLang.tr("pace.diary.other", name));
            case Project.KIND_UPGRADE -> {
                Component before = CityViewExtra.fxLine(p.key, Math.max(1, p.level - 1)), after = CityViewExtra.fxLine(p.key, p.level);
                Blueprint bp = building ? s.blueprintAt(p.cell) : null;
                Component bname = bp == null ? name : bp.displayName();
                line = SettlementsLang.tr("pace.done.up", bname, Math.max(1, p.level - 1), p.level, before, after);
                diary(server, s, SettlementsLang.tr("pace.diary.up", bname, p.level, after));
                ceremony = true;
            }
            case Project.KIND_BUILD -> {
                if (Blueprint.PLAZA.equals(p.key)) {
                    diary(server, s, SettlementsLang.tr("pace.diary.other", name));
                } else if (Blueprint.WALL.equals(p.key)) {
                    line = SettlementsLang.tr("pace.done.new", name, CityViewExtra.fxLine(p.key, 1));
                    diary(server, s, SettlementsLang.tr("pace.diary.done", name, CityViewExtra.fxLine(p.key, 1)));
                    ceremony = true;
                } else {
                    Component fx = CityViewExtra.fxLine(p.key, Math.max(1, p.level));
                    line = SettlementsLang.tr("pace.done.new", name, fx);
                    diary(server, s, SettlementsLang.tr("pace.diary.done", name, fx));
                    ceremony = true;
                }
            }
            default -> diary(server, s, SettlementsLang.tr("pace.diary.other", name));
        }
        if (line != null) {
            Component colored = line.copy().withColor(s.style.color);
            for (ServerPlayer g : CityEconomy.governors(server, s)) g.displayClientMessage(colored, false);
        }
        if (ceremony) ceremony(sl, s, p, name, grand);
        for (WorkDone l : DONE) {
            try {
                l.done(sl, s, p, grand);
            } catch (RuntimeException e) {
                com.mojang.logging.LogUtils.getLogger().error("[isekai_mahou] ouvinte de obra concluída falhou", e);
            }
        }
    }

    /** Sino e fogos no prédio; obra grande ganha um letreiro curto para quem está perto (sem travar, pulável). */
    private static void ceremony(ServerLevel sl, Settlement s, Project p, Component name, boolean grand) {
        long now = sl.getGameTime();
        // várias obras prontas juntas (recuperação, /mahou city finish): uma festa só
        if (now - s.lastInauguration < 60 && s.lastInauguration != 0) return;
        s.lastInauguration = now;
        BlockPos at = p.cell >= 0 ? CityLayout.cellCenter(s.heart, p.cell) : s.heart;
        if (!sl.hasChunk(at.getX() >> 4, at.getZ() >> 4)) return;
        Integer base = p.cell >= 0 ? s.plotBase.get(p.cell) : null;
        double y = (base != null ? base : s.heart.getY()) + 8.0;
        sl.playSound(null, at.getX() + 0.5, y, at.getZ() + 0.5, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0F, grand ? 0.8F : 1.1F);
        sl.sendParticles(ParticleTypes.FIREWORK, at.getX() + 0.5, y + 2, at.getZ() + 0.5, grand ? 90 : 40, 3.5, 2.5, 3.5, 0.12);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, at.getX() + 0.5, y - 5, at.getZ() + 0.5, 24, 4, 1.5, 4, 0.02);
        for (int i = 0; i < (grand ? 4 : 1); i++) CityDecor.firework(sl, s);
        if (!grand) return;
        sl.playSound(null, at, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1.2F, 1.0F);
        double r = CityLayout.half(s) + 16;
        Component sub = SettlementsLang.tr("pace.done.grand", SettlementService.cityName(sl.getServer(), s));
        for (ServerPlayer pl : sl.players()) {
            if (pl.distanceToSqr(s.heart.getX() + 0.5, s.heart.getY(), s.heart.getZ() + 0.5) > r * r) continue;
            Cutscenes.play(pl, "ascension", pl, name, sub, s.style.color, 50, false);
        }
    }

    // =====================================================================================
    //  Ajuda do jogador: materiais
    // =====================================================================================
    /** Quanto vale 1 item como material de obra (0 = não serve). */
    public static int materialValue(ItemStack st) {
        if (st.isEmpty()) return 0;
        if (st.is(Items.IRON_INGOT)) return 6;
        if (st.is(ItemTags.LOGS)) return 4;
        if (st.is(Items.COPPER_INGOT)) return 3;
        if (st.is(ItemTags.STONE_BRICKS) || st.is(Items.BRICKS) || st.is(Items.DEEPSLATE_BRICKS) || st.is(Items.CUT_SANDSTONE)) return 2;
        if (st.is(ItemTags.PLANKS) || st.is(ItemTags.STONE_CRAFTING_MATERIALS) || st.is(Items.STONE) || st.is(Items.DEEPSLATE)
                || st.is(Items.SANDSTONE) || st.is(Items.GLASS) || st.is(Items.BRICK) || st.is(Items.TERRACOTTA)) {
            return 1;
        }
        return 0;
    }

    /** Dias (nominais) que os materiais ainda podem adiantar nesta obra. */
    public static float giftLeft(Project p) {
        if (p.days <= 0F || Project.KIND_TERRAIN.equals(p.kind)) return 0F;
        return Math.max(0F, Math.min(GIFT_CAP * p.days - p.gift, p.days - p.worked));
    }

    /** Botão "Materiais" de uma obra: leva do inventário o que couber e adianta a obra um pouco. */
    public static void deliver(ServerPlayer sp, Settlement s, int index) {
        MinecraftServer server = sp.server;
        if (index < 0 || index >= s.projects.size()) return;
        if (!CityEconomy.isMember(server, s, sp)) {
            warn(sp, "needs.deposit.member");
            return;
        }
        if (!CityActions.inCity(sp, s)) {
            warn(sp, "needs.deposit.far");
            return;
        }
        Project p = s.projects.get(index);
        ensure(server, s, p);
        // onda 7 (city_pause): obra pausada não consome materiais
        if (p.paused || p.undo) {
            warn(sp, "pause.help.paused");
            return;
        }
        float left = giftLeft(p);
        if (left <= 1.0E-4F) {
            warn(sp, "pace.help.full");
            return;
        }
        int want = Mth.ceil(left * MATERIAL_POINTS), points = 0, items = 0;
        var inv = sp.getInventory();
        for (int i = 0; i < inv.items.size() && points < want; i++) {
            ItemStack st = inv.items.get(i);
            int v = materialValue(st);
            if (v <= 0) continue;
            int n = Math.min(st.getCount(), (want - points + v - 1) / v);
            st.shrink(n);
            points += n * v;
            items += n;
        }
        if (items <= 0) {
            warn(sp, "pace.help.none");
            return;
        }
        float given = Math.min(left, points / (float) MATERIAL_POINTS);
        p.gift += given;
        work(server, s, p, given);
        Component name = CityPlanning.projectName(s, p);
        sp.displayClientMessage(SettlementsLang.tr("pace.help.materials", items, name, Math.max(1, Math.round(given / p.days * 100)))
                .withColor(0x7DDA6B), true);
        sp.playNotifySound(SoundEvents.BUNDLE_INSERT, SoundSource.PLAYERS, 1.0F, 0.9F);
        diary(server, s, SettlementsLang.tr("pace.diary.gift", sp.getGameProfile().getName(), name));
    }

    private static void warn(ServerPlayer sp, String key, Object... args) {
        sp.displayClientMessage(SettlementsLang.tr(key, args).withColor(0xE8735E), true);
    }

    // =====================================================================================
    //  Ajuda do jogador: trabalhar no canteiro
    // =====================================================================================
    /**
     * Agachar + clicar com a mão vazia num bloco dentro de um canteiro em andamento da SUA cidade: você martela junto.
     * Cada martelada adianta um pouquinho, com teto por obra por dia ({@link #HANDS_DAILY}) — dá para sentir que
     * ajudou, não para apagar a espera.
     */
    public static void onRightClick(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!sp.isShiftKeyDown() || !sp.getMainHandItem().isEmpty()) return;
        ServerLevel sl = sp.serverLevel();
        BlockPos pos = e.getPos();
        if (sl.getBlockState(pos).is(SettlementsContent.HEART.get())) return;
        var place = SettlementService.cityAt(sl, pos);
        Settlement s = place == null ? null : SettlementService.city(sp.server, place.id());
        if (s == null || !s.own() || s.projects.isEmpty() || !CityEconomy.isMember(sp.server, s, sp)) return;
        Project site = null;
        // onda 7 (city_pause): só os canteiros com equipe (obra pausada não aceita marteladas, e não tira a vez das outras)
        List<Project> crewed = CityPause.crewed(s);
        for (int i = 0; i < crewed.size() && site == null; i++) {
            Project p = crewed.get(i);
            Project.Piece piece = p.current();
            if (piece == null || !piece.resolved() || Project.KIND_TERRAIN.equals(p.kind)) continue;
            if (p.undo) continue;
            int half = Math.max(p.clearHalf, 6) + 1;
            if (Math.abs(pos.getX() - piece.cx) <= half && Math.abs(pos.getZ() - piece.cz) <= half
                    && pos.getY() >= piece.baseY - 3 && pos.getY() <= piece.baseY + 40) {
                site = p;
            }
        }
        if (site == null) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.SUCCESS);
        long now = sl.getGameTime();
        Long last = LAST_SWING.get(sp.getUUID());
        if (last != null && now - last < SWING_COOLDOWN) return;
        if (LAST_SWING.size() > 256) LAST_SWING.clear();
        LAST_SWING.put(sp.getUUID(), now);
        ensure(sp.server, s, site);
        long day = SettlementService.day(sp.server);
        if (site.handsDay != day) {
            site.handsDay = day;
            site.hands = 0F;
        }
        if (timeDone(site)) return;
        if (site.hands >= HANDS_DAILY - 1.0E-4F) {
            warn(sp, "pace.help.hands.full");
            return;
        }
        float add = Math.min(HAND_SWING, HANDS_DAILY - site.hands);
        site.hands += add;
        work(sp.server, s, site, add);
        sp.swing(InteractionHand.MAIN_HAND, true);
        sl.playSound(null, pos, SoundEvents.WOOD_HIT, SoundSource.PLAYERS, 0.8F, 0.9F + sl.random.nextFloat() * 0.3F);
        sl.sendParticles(ParticleTypes.CRIT, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 4, 0.3, 0.2, 0.3, 0.05);
        sp.displayClientMessage(SettlementsLang.tr("pace.help.hands", CityPlanning.projectName(s, site),
                Math.round(site.hands / HANDS_DAILY * 100)).withColor(0x7DDA6B), true);
        Hints.show(sp, "city_pace_help");
    }

    // =====================================================================================
    //  Magia de obra (onda 4): rende uma fração de dia, com limite diário por obra
    // =====================================================================================
    /** Dias (nominais) que a magia ainda pode dar a esta obra HOJE. */
    public static float magicLeft(MinecraftServer server, Project p) {
        // onda 7 (city_pause): a magia de obra não pega em obra pausada
        if (p.paused || p.undo) return 0F;
        long day = SettlementService.day(server);
        float used = p.magicDay == day ? p.magic : 0F;
        return Math.max(0F, Math.min(MAGIC_DAILY - used, p.days - p.worked));
    }

    private static void spendMagic(long day, Project p, float days) {
        if (p.magicDay != day) {
            p.magicDay = day;
            p.magic = 0F;
        }
        p.magic += days;
    }

    /**
     * Erguer Obra: a magia faz de uma vez até {@code days} dias de trabalho (dentro do limite diário da obra).
     * Devolve quanto rendeu de verdade (0 = o limite de hoje já foi).
     */
    public static float magicWork(MinecraftServer server, Settlement s, Project p, float days) {
        ensure(server, s, p);
        float give = Math.min(days, magicLeft(server, p));
        if (give <= 1.0E-4F) return 0F;
        spendMagic(SettlementService.day(server), p, give);
        work(server, s, p, give);
        return give;
    }

    // =====================================================================================
    //  Tela
    // =====================================================================================
    /** Campos de uma obra para a lista "Obras" (dia X de Y, etapa, quanto falta, se aceita materiais). */
    public static void viewProject(CompoundTag e, MinecraftServer server, Settlement s, Project p, boolean active, HolderLookup.Provider reg) {
        ensure(server, s, p);
        e.putInt("Pct", Math.round(fraction(p) * 100));
        e.putInt("Day", dayNumber(p));
        e.putInt("Days", totalDays(p));
        e.putString("Stage", Component.Serializer.toJson(stageText(p), reg));
        e.putString("Left", Component.Serializer.toJson(daysText(remaining(server, s, p)), reg));
        e.putBoolean("Started", p.stage != 0);
        e.putBoolean("Help", (active || p.stage != 0) && giftLeft(p) > 1.0E-4F);
    }

    /** Parte "ritmo" do estado da tela: velocidade de agora, multiplicador do servidor e o Diário. */
    public static void view(CompoundTag t, MinecraftServer server, Settlement s, HolderLookup.Provider reg) {
        CompoundTag pc = new CompoundTag();
        pc.putInt("Speed", Math.round(speed(server, s) * 100));
        pc.putFloat("Mult", paceMult());
        long today = SettlementService.day(server);
        ListTag diary = new ListTag();
        for (int i = s.diary.size() - 1; i >= 0; i--) {
            CompoundTag d = s.diary.get(i);
            CompoundTag e = new CompoundTag();
            e.putInt("Ago", (int) Math.max(0, Math.min(9999, today - d.getLong("Day"))));
            e.putString("Text", d.getString("Text"));
            diary.add(e);
        }
        pc.put("Diary", diary);
        // o que as próximas obras custam em dias (a tela mostra ANTES de enfileirar)
        if (s.level < CityTier.MAX) pc.putString("RankDays", Component.Serializer.toJson(daysText(real(centerDays(s.level + 1))), reg));
        pc.putString("WallDays", Component.Serializer.toJson(daysText(real(wallDays(Math.max(1, SettlementService.nextWall(s))))), reg));
        t.put("Pace", pc);
    }

    /** Dias (texto pronto) de um prédio novo — para o catálogo do planejador. */
    public static String buildDaysJson(String key, HolderLookup.Provider reg) {
        return Component.Serializer.toJson(daysText(real(baseDays(key))), reg);
    }

    /** Dias (texto pronto) da melhoria de um prédio para o próximo nível. */
    public static String upgradeDaysJson(String key, int toLevel, HolderLookup.Provider reg) {
        return Component.Serializer.toJson(daysText(real(upgradeDays(key, toLevel))), reg);
    }

    // =====================================================================================
    //  Depuração
    // =====================================================================================
    /** Linhas do {@code /mahou city pace}: ritmo, velocidade e cada obra da fila. */
    public static List<Component> debug(MinecraftServer server, Settlement s) {
        List<Component> out = new ArrayList<>();
        out.add(SettlementsLang.tr("cmd.pace", SettlementService.cityName(server, s), String.format(java.util.Locale.ROOT, "%.2f", paceMult()),
                Math.round(speed(server, s) * 100), s.population(Settlement.Role.BUILDER), s.projects.size()));
        List<Project> act = working(server, s);
        for (Project p : s.projects) {
            ensure(server, s, p);
            out.add(Component.literal(p.undo ? "  ✖ " : p.paused ? "  ⏸ " : act.contains(p) ? "  ▶ " : "  · ").append(SettlementsLang.tr("cmd.pace.work", CityPlanning.projectName(s, p),
                    dayNumber(p), totalDays(p), Math.round(fraction(p) * 100), stageText(p),
                    String.format(java.util.Locale.ROOT, "%.2f", remaining(server, s, p)))));
        }
        return out;
    }

    public static void forget(UUID player) {
        LAST_SWING.remove(player);
    }

    public static void clear() {
        LAST_SWING.clear();
    }

    /** "Que dia é hoje" para as cidades (dia do Overworld) — o mesmo número que o gancho de novo dia recebe. */
    public static long today(MinecraftServer server) {
        return SettlementService.day(server);
    }

    /** A obra em andamento (ou na fila) de um lote, já com a duração medida; null = lote sem obra. */
    @Nullable
    public static Project workAt(MinecraftServer server, Settlement s, int cell) {
        Project p = s.projectAt(cell);
        if (p != null) ensure(server, s, p);
        return p;
    }

    private CityPace() {}
}
