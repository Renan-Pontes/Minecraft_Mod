package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprints;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Estado de uma cidade governada por um jogador: a PRÓPRIA (fundada com a Carta) ou uma CONQUISTADA (Senhor de uma
 * cidade de reino). Guardado em {@link SettlementData}. O id é o id do lugar ({@code Places}) — o mesmo do
 * {@code Allegiance}.
 */
public final class Settlement {
    public enum Kind { OWN, CONQUERED }

    /** Papéis dos NPCs da cidade. */
    public enum Role {
        BUILDER, RESIDENT, GUARD, MERCHANT, MILITIA;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final int TAX_LOW = 0, TAX_NORMAL = 1, TAX_HIGH = 2;
    public static final int GATES_OPEN = 0, GATES_NIGHT = 1, GATES_CLOSED = 2;
    public static final int ORDER_FOLLOW = 0, ORDER_DEFEND = 1;
    public static final int MAX_AFFINITY = 100;
    public static final int MAX_TREASURY = 99999;

    public final String id;
    public final Kind kind;
    public SettlementStyle style;
    public final ResourceKey<Level> dim;
    public final BlockPos heart;
    /** Nome escolhido (cidade própria); conquistada = "" (usa o nome do lugar). */
    public String name;
    @Nullable public UUID owner;
    public String ownerName = "";

    public int level = 1;
    public int points;
    public int affinity;
    public int treasury;
    public int tax = TAX_NORMAL;
    public int gates = GATES_OPEN;
    public int militiaOrder = ORDER_FOLLOW;

    /** Dia do Overworld da última conta diária. */
    public long lastDay = -1;
    public int tradesToday;
    public int killsToday;
    public boolean lordVisited;
    public long festivalDay = -1;
    public int lastIncome;
    public int lastAffinityDelta;

    /** Obras (as primeiras, até o número de construtores, andam ao mesmo tempo). */
    public final List<Project> projects = new ArrayList<>();
    /** Prédios prontos: célula → chave (praça = célula central; muralha = -1). */
    public final Map<Integer, String> buildings = new LinkedHashMap<>();
    /** Alguma muralha pronta (= {@code !walls.isEmpty()}). */
    public boolean wallDone;
    /** Muralhas prontas (anel de muralha k = 1, 2, 3...: veja {@code CityLayout.wallDist}). */
    public final java.util.TreeSet<Integer> walls = new java.util.TreeSet<>();
    /** Quantos anéis de terrenos a cidade tem (começa com 2 = 5×5; cresce expandindo). */
    public int radius = com.renanpontes.isekaimahou.feature.settlements.build.CityLayout.START_RADIUS;
    public final Map<Role, List<UUID>> npcs = new EnumMap<>(Role.class);
    /** Grades dos portões (posições) e se estão fechadas agora. */
    public final List<BlockPos> gateBlocks = new ArrayList<>();
    public boolean gatesShut;
    @Nullable public BlockPos stone;
    /** Cidades que propuseram paz a esta. */
    public final Set<String> peaceOffers = new LinkedHashSet<>();
    // ---- v1.4: planejamento, necessidades, ataques (tudo opcional no NBT: mundos antigos carregam)
    public static final int MAX_BUILDING_LEVEL = 5;
    /** Nível de cada prédio (célula → 1..5). */
    public final Map<Integer, Integer> bLevels = new HashMap<>();
    /** Estilo escolhido de cada prédio (célula → id do estilo). */
    public final Map<Integer, String> bStyles = new HashMap<>();
    /**
     * Onda 8 (city_looks): geração do VISUAL de cada prédio (célula → 1 = visual novo; ausente = molde de antes da onda
     * 8). Prédio de mundo antigo não muda sozinho: só passa para o visual novo numa obra ({@link CityLooks}).
     */
    public final Map<Integer, Integer> bLooks = new HashMap<>();
    /** Altura do piso de cada terreno pronto ou aplainado (célula → y). */
    public final Map<Integer, Integer> plotBase = new HashMap<>();
    /** Prédios danificados por um ataque (efeito pela metade até o conserto). */
    public final Set<Integer> damaged = new LinkedHashSet<>();
    /** Conselheiros: cidadãos que o Senhor deixou planejar a cidade. */
    public final Set<UUID> council = new LinkedHashSet<>();
    /** Habitantes (população simulada; os NPCs são uma parte visível dela). -1 = ainda não calculada. */
    public int people = -1;
    public int food = 40;
    public int fuel = 20;
    /** Números do último dia (para a tela). */
    public int lastFoodProd, lastFoodUse, lastFuelProd, lastFuelUse, lastSecurity, lastSecurityNeed, lastPeopleDelta;
    public int lowApprovalDays;
    /** Um ataque de monstros está para acontecer (espera alguém da cidade por perto). */
    public boolean raidDue;
    public long nextRaidDay = -1;
    public int raidsWon, raidsLost;
    /** Pedidos do Povo: id do item, quantidade pedida, entregue. */
    public final List<CompoundTag> requests = new ArrayList<>();
    public long requestsDay = -1;
    // ---- v1.0: enfeites de festa (CityDecor) — tudo opcional no NBT
    /** Tema dos enfeites ligados ("" = cidade sem enfeite). */
    public String decor = "";
    /** Último dia do Overworld com os enfeites (-1 = até desligarem). */
    public long decorUntil = -1;
    /** Blocos de enfeite colocados (para tirar tudo limpo depois). */
    public final List<BlockPos> decorBlocks = new ArrayList<>();
    /** Próximo lote a enfeitar — só em memória (recomeçar é inofensivo: só completa o que falta). */
    public transient int decorCursor;
    /** Teto de construtores de uma cidade (entidades de verdade: não pode crescer sem limite). */
    public static final int MAX_BUILDERS = 24;

    // ---- v1.0 (city_life): caravanas, mercadorias, pedidos dos moradores — tudo opcional no NBT
    /** Tetos de NPCs de verdade (cidade enorme não pode virar centenas de entidades). */
    public static final int MAX_RESIDENT_NPCS = 40, MAX_GUARD_NPCS = 20, MAX_MERCHANT_NPCS = 8;
    /** Caravanas em viagem (veja {@link CityCaravans}). */
    public final List<CompoundTag> caravans = new ArrayList<>();
    /** Resultado da última caravana que voltou (para a tela). */
    public CompoundTag caravanLast = new CompoundTag();
    /** Caravanas mandadas hoje para cada destino ({Day, Sent:{id: n}}): o mercado de lá satura. */
    public CompoundTag caravanMarket = new CompoundTag();
    /** Mercadorias guardadas (produção dos prédios e o que as caravanas trazem): {Item, Count}. */
    public final List<CompoundTag> stash = new ArrayList<>();
    /** Pedidos dos moradores em andamento ({@link CityGoals}): {Id, Base, Need}. */
    public final List<CompoundTag> goals = new ArrayList<>();
    public final Set<String> goalsDone = new LinkedHashSet<>();
    public int caravansSent, caravanProfit, festivals, goalsCompleted;
    /** Dia em que os batedores já avisaram do próximo ataque (para não repetir). */
    public long scoutDay = -1;

    // ---- v1.0 (city_plan): prévia da fundação — a praça pode nascer girada (opcional no NBT; 0 = como sempre foi)
    /** Rotação da praça (índice de {@code Rotation}: 0 nenhuma, 1 = 90° horário, 2 = 180°, 3 = 90° anti-horário). */
    public int plazaRot;

    // ---- onda 5 (city_tiers): degraus da cidade — tudo opcional no NBT (mundo antigo: 0/vazio = "ainda não reformada")
    /** Degrau cujo visual já está aplicado na cidade inteira (0 = nunca passou pela reforma: {@link CityRenewal}). */
    public int look;
    /** Degrau que a reforma em andamento está aplicando (0 = sem reforma) e o lote da vez. */
    public int lookGoal, lookCursor;
    /** Espera até o próximo lote e lotes pulados nesta volta (chunk descarregado) — só em memória. */
    public transient int lookWait, lookSkipped;
    /** Com que estilo e grau cada muralha foi erguida (anel → "estilo:grau"; ausente = molde .nbt antigo). */
    public final Map<Integer, String> wallLooks = new HashMap<>();
    /** Enfeites postos pela reforma do degrau (para trocar no degrau seguinte sem tocar em nada do jogador). */
    public final List<BlockPos> tierBlocks = new ArrayList<>();
    /** Dia do Overworld da última subida de degrau (-1 = nunca). */
    public long tierDay = -1;
    /**
     * Onda 8 (city_looks): versão do visual de degrau já aplicada (ruas com meio-fio/calçada, mobília, praça). Cidade de
     * mundo antigo (0) passa pela reforma mais uma vez para ganhar o que cada degrau tem de novo ({@code CityRenewal}).
     */
    public int lookGen;

    // ---- onda 6 (city_pace): obras em DIAS — tudo opcional no NBT (mundo antigo: -1/vazio = "começa a contar agora")
    /** Hora do Overworld (dayTime) da última vez que as obras receberam o tempo passado (-1 = nunca). */
    public long paceTime = -1;
    /** Último dia do Overworld em que o gancho de "novo dia" rodou com o coração carregado (-1 = nunca). */
    public long paceDay = -1;
    /** Diário da cidade: {Day, Text (JSON de Component)} — as últimas entradas ({@code CityPace.DIARY_MAX}). */
    public final List<CompoundTag> diary = new ArrayList<>();
    /** Obras terminadas desde o último amanhecer (para o resumo da manhã). */
    public int paceDone;
    /** Tempo do jogo (gameTime) da última conta das obras — só em memória; vale quando o ciclo do dia está parado. */
    public transient long paceGame = -1;
    /** Tempo do jogo da última inauguração com fogos (para várias obras prontas juntas não virarem barulho) — só em memória. */
    public transient long lastInauguration;

    // ---- onda 6 (city_trades): comerciantes de ofício — opcional no NBT (vazio = cidade sem nenhum ainda)
    /** Estado dos comerciantes de ofício ({@link CityTrades}): quem ocupa cada loja, estoque do dia e a taxa do Senhor. */
    public CompoundTag trades = new CompoundTag();

    // ---- onda 6 (city_monuments): monumentos ativos — opcional no NBT (mundo antigo: vazio = "consagra os que já existem")
    /** Estado dos monumentos ({@link CityMonuments}): {Active:{lote: dia em que foi consagrado}, Init, Manual}. */
    public CompoundTag monuments = new CompoundTag();

    // ---- onda 7 (sky_island): Ilha Celeste (SkyLand) — opcional no NBT (ausente = cidade no chão, como sempre foi)
    /** A cidade fica numa ilha flutuante criada pelo mod (regras em {@link SkyCity}). O Coração está no céu. */
    public boolean sky;
    /** Raio (anéis) cuja terra já está TODA gerada (0 = nenhuma ainda). */
    public int skyLand;
    /** Raio que a geração em andamento está formando (0 = parada) e o cursor dela: anel a partir do coração e coluna do anel. */
    public int skyGoal, skyRing, skyIdx;
    /** Anel em que a passada atual começou (para o percentual). */
    public int skyStart;
    /** Primeiro anel com coluna pulada nesta passada por estar em chunk descarregado (-1 = nenhuma): a geração volta nele. */
    public int skySkip = -1;
    /** O fundador ainda está no chão: é levado para a praça quando o miolo da ilha existir. */
    public boolean skyLift;
    /** Ticks de espera antes de voltar às colunas puladas — só em memória. */
    public transient int skyWait;
    /** Onda 7 (sky_access): cais, marco no chão e coluna da Corrente Ascendente ({@link SkyAccess}) — opcional (vazio = ainda não montado). */
    public CompoundTag skyAccess = new CompoundTag();
    // ---- onda 8 (water_city): Cidade das Águas — opcional no NBT (ausente = não é cidade sobre a água)
    /** A cidade fica SOBRE A ÁGUA, em deques e passarelas sobre estacas (regras em {@link WaterCity}). */
    public boolean water;
    /** Passarelas, cais, barcos e enfeites da Cidade das Águas ({@link WaterWorks}) — opcional (vazio = nada montado ainda). */
    public CompoundTag waterData = new CompoundTag();
    // ---- onda 9 (isles): cidade de ILHAS — opcional no NBT (ausente = planta antiga, como sempre foi)
    /**
     * Cidade do céu ou da água fundada a partir da onda 9: em vez da grade colada, um ARQUIPÉLAGO — a ilha (ou
     * plataforma) do meio e uma ilha por lote, separadas por vãos e ligadas por pontes, criadas só quando a cidade
     * precisa ({@link SkyIsles}, {@code CityLayout.ISLE_PITCH}). Cidades antigas ficam com a planta antiga.
     */
    public boolean isles;
    /** Ilhas do céu já criadas, cursor da geração e pontes ({@link SkyIsles}) — opcional (vazio = nada ainda). */
    public CompoundTag isleData = new CompoundTag();

    /** Onda 8: tipo de terreno da cidade por id — "ground", "skyland" ({@link #sky}) ou "water" ({@link #water}). */
    public String terrain() {
        return water ? WaterCity.WATER : sky ? SkyCity.SKYLAND : SkyCity.GROUND;
    }

    /** Última vez (tempo do jogo) que cada NPC deu sinal de vida — só em memória. */
    public final transient Map<UUID, Long> lastSeen = new HashMap<>();

    public Settlement(String id, Kind kind, SettlementStyle style, ResourceKey<Level> dim, BlockPos heart, String name) {
        this.id = id;
        this.kind = kind;
        this.style = style;
        this.dim = dim;
        this.heart = heart.immutable();
        this.name = name;
        for (Role r : Role.values()) npcs.put(r, new ArrayList<>());
    }

    public boolean own() {
        return kind == Kind.OWN;
    }

    /** Rotação da praça (escolhida na prévia da fundação). */
    public net.minecraft.world.level.block.Rotation plazaRotation() {
        return net.minecraft.world.level.block.Rotation.values()[Math.floorMod(plazaRot, 4)];
    }

    // =====================================================================================
    //  Prédios e efeitos
    // =====================================================================================
    public int count(String key) {
        int n = 0;
        for (String k : buildings.values()) if (k.equals(key)) n++;
        if (Blueprint.WALL.equals(key)) n += walls.size();
        return n;
    }

    public boolean has(String key) {
        return count(key) > 0;
    }

    public int inProgress(String key) {
        int n = 0;
        for (Project p : projects) if (p.key.equals(key) && Project.KIND_BUILD.equals(p.kind)) n++;
        return n;
    }

    private int sum(java.util.function.ToIntFunction<Blueprint.Effects> f) {
        int t = 0;
        for (Map.Entry<Integer, String> e : buildings.entrySet()) {
            Blueprint b = blueprintAt(e.getKey());
            if (b != null) t += f.applyAsInt(b.fx());
        }
        if (!walls.isEmpty()) {
            Blueprint w = Blueprints.get(style, Blueprint.WALL);
            if (w != null) t += f.applyAsInt(w.fx()) * walls.size();
        }
        return t;
    }

    public boolean wonder(String id) {
        for (int cell : buildings.keySet()) {
            Blueprint b = blueprintAt(cell);
            if (b != null && b.fx().wonder().equals(id)) return true;
        }
        return false;
    }

    public boolean guardPower() {
        for (int cell : buildings.keySet()) {
            Blueprint b = blueprintAt(cell);
            if (b != null && b.fx().guardPower()) return true;
        }
        return false;
    }

    // ---- v1.4: nível, estilo e estado de cada terreno
    /** Estilo do prédio do terreno (escolhido no planejamento; padrão = estilo da cidade). */
    public SettlementStyle styleOf(int cell) {
        SettlementStyle st = SettlementStyle.byId(bStyles.get(cell));
        return st == null ? style : st;
    }

    /** Nível do prédio do terreno (1..5). */
    public int levelOf(int cell) {
        return Math.max(1, Math.min(MAX_BUILDING_LEVEL, bLevels.getOrDefault(cell, 1)));
    }

    @Nullable
    public Blueprint blueprintAt(int cell) {
        String key = buildings.get(cell);
        if (key == null) return null;
        Blueprint b = Blueprints.get(styleOf(cell), key);
        return b != null ? b : Blueprints.get(style, key);
    }

    /** Muralha mais de fora pronta (0 = nenhuma). */
    public int outerWall() {
        return walls.isEmpty() ? 0 : walls.last();
    }

    /** Marca uma muralha pronta. */
    public void addWall(int k) {
        walls.add(Math.max(1, k));
        wallDone = true;
    }

    /** Obra em andamento num terreno (ou null). */
    @Nullable
    public Project projectAt(int cell) {
        for (Project p : projects) if (p.cell == cell && cell >= 0) return p;
        return null;
    }

    /** Senhor ou conselheiro: pode planejar a cidade. */
    public boolean canPlan(UUID player) {
        return player.equals(owner) || council.contains(player);
    }

    /** Vagas de cada papel. Cidade conquistada: guarda pessoal do Senhor e milícia fixas. */
    public int cap(Role role) {
        if (!own()) {
            return switch (role) {
                case GUARD -> 4;
                case MILITIA -> 6;
                default -> 0;
            };
        }
        return switch (role) {
            case BUILDER -> builderCap();
            // onda 5: cada degrau traz mais gente à vista (moradores, guardas e mascates na praça)
            case RESIDENT -> Math.min(MAX_RESIDENT_NPCS, people < 0 ? sum(Blueprint.Effects::residents)
                    : Math.min(sum(Blueprint.Effects::residents) + 2 + CityTier.residents(level), Math.max(0, people / 2)));
            case GUARD -> Math.min(MAX_GUARD_NPCS, sum(Blueprint.Effects::guards) + CityTier.guards(level));
            case MERCHANT -> Math.min(MAX_MERCHANT_NPCS, sum(Blueprint.Effects::merchants) + CityTier.merchants(level));
            case MILITIA -> has("barracks") ? 2 + 2 * level + sum(Blueprint.Effects::militia) : 0;
        };
    }

    /**
     * Vagas de construtores (v1.0, "ter mais que 2 construtores"): a cidade começa com 2 e ganha 1 por nível do
     * centro; a Casa dos Construtores dá 2 POR NÍVEL dela (2, 4, 6, 8, 10); a Oficina dá 1 (2 no nível 3+).
     * Cada construtor toca uma obra; os que sobram ajudam nas obras em andamento.
     */
    public int builderCap() {
        int n = 2 + Math.max(0, level - 1);
        for (Map.Entry<Integer, String> e : buildings.entrySet()) {
            Blueprint b = blueprintAt(e.getKey());
            if (b == null || b.fx().builders() <= 0) continue;
            int lvl = levelOf(e.getKey());
            n += "builders_hall".equals(e.getValue()) ? 2 * lvl : b.fx().builders() + (lvl >= 3 ? 1 : 0);
        }
        return Math.min(MAX_BUILDERS, n);
    }

    /** Velocidade extra das obras (%): prédios + cidade feliz (v1.0). */
    public int speedPct() {
        return sum(Blueprint.Effects::speedPct) + CityHappiness.speedBonus(affinity);
    }

    public int taxPct() {
        return sum(Blueprint.Effects::taxPct) + style.taxPct() + CityMonuments.cityStat(this, CityMonuments.Stat.TAX);
    }

    public int buildingAffinity() {
        return sum(Blueprint.Effects::affinity);
    }

    public int population(Role role) {
        return npcs.get(role).size();
    }

    public int totalPopulation() {
        int t = 0;
        for (List<UUID> l : npcs.values()) t += l.size();
        return t;
    }

    /** Recalcula o nível pela prosperidade. Devolve true se subiu. */
    public boolean recomputeLevel() {
        int old = level;
        if (own()) level = Math.max(level, Blueprints.levelFor(style, points, has(Blueprint.TOWN_HALL)));
        return level > old;
    }

    // =====================================================================================
    //  NBT
    // =====================================================================================
    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("Id", id);
        t.putString("Kind", kind.name());
        t.putString("Style", style.id);
        t.putString("Dim", dim.location().toString());
        t.put("Heart", NbtUtils.writeBlockPos(heart));
        t.putString("Name", name);
        if (owner != null) t.putUUID("Owner", owner);
        t.putString("OwnerName", ownerName);
        t.putInt("Level", level);
        t.putInt("Points", points);
        t.putInt("Affinity", affinity);
        t.putInt("Treasury", treasury);
        t.putInt("Tax", tax);
        t.putInt("Gates", gates);
        t.putInt("MilitiaOrder", militiaOrder);
        t.putLong("LastDay", lastDay);
        t.putInt("TradesToday", tradesToday);
        t.putInt("KillsToday", killsToday);
        t.putBoolean("LordVisited", lordVisited);
        t.putLong("FestivalDay", festivalDay);
        t.putInt("LastIncome", lastIncome);
        t.putInt("LastAffinityDelta", lastAffinityDelta);
        ListTag pl = new ListTag();
        for (Project p : projects) pl.add(p.save());
        t.put("Projects", pl);
        CompoundTag b = new CompoundTag();
        buildings.forEach((cell, key) -> b.putString(Integer.toString(cell), key));
        t.put("Buildings", b);
        t.putBoolean("WallDone", wallDone);
        t.putIntArray("Walls", walls.stream().mapToInt(Integer::intValue).toArray());
        t.putInt("Radius", radius);
        CompoundTag n = new CompoundTag();
        npcs.forEach((role, list) -> {
            ListTag l = new ListTag();
            for (UUID u : list) l.add(NbtUtils.createUUID(u));
            n.put(role.name(), l);
        });
        t.put("Npcs", n);
        ListTag g = new ListTag();
        for (BlockPos p : gateBlocks) g.add(NbtUtils.writeBlockPos(p));
        t.put("GateBlocks", g);
        t.putBoolean("GatesShut", gatesShut);
        if (stone != null) t.put("Stone", NbtUtils.writeBlockPos(stone));
        ListTag po = new ListTag();
        for (String s : peaceOffers) po.add(StringTag.valueOf(s));
        t.put("PeaceOffers", po);
        CompoundTag lv = new CompoundTag();
        bLevels.forEach((c, v) -> lv.putInt(Integer.toString(c), v));
        t.put("BLevels", lv);
        CompoundTag bs = new CompoundTag();
        bStyles.forEach((c, v) -> bs.putString(Integer.toString(c), v));
        t.put("BStyles", bs);
        if (!bLooks.isEmpty()) {
            CompoundTag bl = new CompoundTag();
            bLooks.forEach((c, v) -> bl.putInt(Integer.toString(c), v));
            t.put("BLooks", bl);
        }
        CompoundTag pb = new CompoundTag();
        plotBase.forEach((c, v) -> pb.putInt(Integer.toString(c), v));
        t.put("PlotBase", pb);
        t.putIntArray("Damaged", damaged.stream().mapToInt(Integer::intValue).toArray());
        ListTag cn = new ListTag();
        for (UUID u : council) cn.add(NbtUtils.createUUID(u));
        t.put("Council", cn);
        t.putInt("People", people);
        t.putInt("Food", food);
        t.putInt("Fuel", fuel);
        t.putIntArray("LastStats", new int[] {lastFoodProd, lastFoodUse, lastFuelProd, lastFuelUse, lastSecurity, lastSecurityNeed,
                lastPeopleDelta});
        t.putInt("LowApprovalDays", lowApprovalDays);
        t.putBoolean("RaidDue", raidDue);
        t.putLong("NextRaidDay", nextRaidDay);
        t.putInt("RaidsWon", raidsWon);
        t.putInt("RaidsLost", raidsLost);
        ListTag rq = new ListTag();
        rq.addAll(requests);
        t.put("Requests", rq);
        t.putLong("RequestsDay", requestsDay);
        if (!decor.isEmpty()) {
            t.putString("Decor", decor);
            t.putLong("DecorUntil", decorUntil);
        }
        if (!decorBlocks.isEmpty()) t.putLongArray("DecorBlocks", decorBlocks.stream().mapToLong(BlockPos::asLong).toArray());
        if (!caravans.isEmpty()) t.put("Caravans", tags(caravans));
        if (!caravanLast.isEmpty()) t.put("CaravanLast", caravanLast);
        if (!caravanMarket.isEmpty()) t.put("CaravanMarket", caravanMarket);
        if (!stash.isEmpty()) t.put("Stash", tags(stash));
        if (!goals.isEmpty()) t.put("Goals", tags(goals));
        if (!goalsDone.isEmpty()) {
            ListTag gd = new ListTag();
            for (String done : goalsDone) gd.add(StringTag.valueOf(done));
            t.put("GoalsDone", gd);
        }
        t.putIntArray("LifeStats", new int[] {caravansSent, caravanProfit, festivals, goalsCompleted});
        t.putLong("ScoutDay", scoutDay);
        if (plazaRot != 0) t.putInt("PlazaRot", plazaRot);
        if (look != 0) t.putInt("Look", look);
        if (lookGoal != 0) {
            t.putInt("LookGoal", lookGoal);
            t.putInt("LookCursor", lookCursor);
        }
        if (!wallLooks.isEmpty()) {
            CompoundTag wl = new CompoundTag();
            wallLooks.forEach((k, v) -> wl.putString(Integer.toString(k), v));
            t.put("WallLooks", wl);
        }
        if (!tierBlocks.isEmpty()) t.putLongArray("TierBlocks", tierBlocks.stream().mapToLong(BlockPos::asLong).toArray());
        if (tierDay >= 0) t.putLong("TierDay", tierDay);
        if (lookGen != 0) t.putInt("LookGen", lookGen);
        if (paceTime >= 0) t.putLong("PaceTime", paceTime);
        if (paceDay >= 0) t.putLong("PaceDay", paceDay);
        if (!diary.isEmpty()) t.put("Diary", tags(diary));
        if (paceDone != 0) t.putInt("PaceDone", paceDone);
        if (!monuments.isEmpty()) t.put("Monuments", monuments.copy());
        if (!trades.isEmpty()) t.put("Trades", trades);
        if (sky) {
            CompoundTag k = new CompoundTag();
            k.putInt("Land", skyLand);
            k.putInt("Goal", skyGoal);
            k.putInt("Ring", skyRing);
            k.putInt("Idx", skyIdx);
            k.putInt("Start", skyStart);
            k.putInt("Skip", skySkip);
            if (skyLift) k.putBoolean("Lift", true);
            if (!skyAccess.isEmpty()) k.put("Access", skyAccess.copy());
            t.put("Sky", k);
        }
        // onda 8 (water_city): o tipo de terreno também vai por id ("ground" não é gravado: cidades no chão ficam como eram)
        if (water) {
            t.putString("Terrain", WaterCity.WATER);
            t.put("Water", waterData.copy());
        } else if (sky) {
            t.putString("Terrain", SkyCity.SKYLAND);
        }
        // onda 9 (isles): só gravado nas cidades de ilhas (as antigas não ganham chave nova)
        if (isles) {
            t.putBoolean("Isles", true);
            if (!isleData.isEmpty()) t.put("IsleData", isleData.copy());
        }
        return t;
    }

    @Nullable
    public static Settlement load(CompoundTag t) {
        ResourceLocation dimId = ResourceLocation.tryParse(t.getString("Dim"));
        BlockPos heart = NbtUtils.readBlockPos(t, "Heart").orElse(null);
        if (dimId == null || heart == null || t.getString("Id").isEmpty()) return null;
        Kind kind = "CONQUERED".equals(t.getString("Kind")) ? Kind.CONQUERED : Kind.OWN;
        SettlementStyle style = SettlementStyle.byId(t.getString("Style"));
        Settlement s = new Settlement(t.getString("Id"), kind, style == null ? SettlementStyle.KINGDOM : style,
                ResourceKey.create(Registries.DIMENSION, dimId), heart, t.getString("Name"));
        if (t.hasUUID("Owner")) s.owner = t.getUUID("Owner");
        s.ownerName = t.getString("OwnerName");
        s.level = Math.max(1, t.getInt("Level"));
        s.points = t.getInt("Points");
        s.affinity = t.getInt("Affinity");
        s.treasury = t.getInt("Treasury");
        s.tax = t.getInt("Tax");
        s.gates = t.getInt("Gates");
        s.militiaOrder = t.getInt("MilitiaOrder");
        s.lastDay = t.getLong("LastDay");
        s.tradesToday = t.getInt("TradesToday");
        s.killsToday = t.getInt("KillsToday");
        s.lordVisited = t.getBoolean("LordVisited");
        s.festivalDay = t.getLong("FestivalDay");
        s.lastIncome = t.getInt("LastIncome");
        s.lastAffinityDelta = t.getInt("LastAffinityDelta");
        ListTag pl = t.getList("Projects", Tag.TAG_COMPOUND);
        for (int i = 0; i < pl.size(); i++) {
            Project p = Project.load(pl.getCompound(i));
            if (p != null) s.projects.add(p);
        }
        CompoundTag b = t.getCompound("Buildings");
        for (String k : b.getAllKeys()) {
            try {
                s.buildings.put(Integer.parseInt(k), b.getString(k));
            } catch (NumberFormatException ignored) {
                // célula inválida (save editado): ignora o prédio
            }
        }
        s.wallDone = t.getBoolean("WallDone");
        for (int k : t.getIntArray("Walls")) {
            if (k >= 1) s.walls.add(k);
        }
        // mundos antigos: a muralha única é a do anel 1
        if (!t.contains("Walls") && s.wallDone) s.walls.add(1);
        s.wallDone = !s.walls.isEmpty();
        if (t.contains("Radius")) s.radius = Math.max(com.renanpontes.isekaimahou.feature.settlements.build.CityLayout.START_RADIUS,
                Math.min(com.renanpontes.isekaimahou.feature.settlements.build.CityLayout.MAX_RADIUS, t.getInt("Radius")));
        CompoundTag n = t.getCompound("Npcs");
        for (Role r : Role.values()) {
            ListTag l = n.getList(r.name(), Tag.TAG_INT_ARRAY);
            for (Tag e : l) s.npcs.get(r).add(NbtUtils.loadUUID(e));
        }
        ListTag g = t.getList("GateBlocks", Tag.TAG_INT_ARRAY);
        for (Tag e : g) {
            if (e instanceof net.minecraft.nbt.IntArrayTag ia && ia.size() == 3) {
                s.gateBlocks.add(new BlockPos(ia.get(0).getAsInt(), ia.get(1).getAsInt(), ia.get(2).getAsInt()));
            }
        }
        s.gatesShut = t.getBoolean("GatesShut");
        if (t.contains("Stone")) s.stone = NbtUtils.readBlockPos(t, "Stone").orElse(null);
        ListTag po = t.getList("PeaceOffers", Tag.TAG_STRING);
        for (int i = 0; i < po.size(); i++) s.peaceOffers.add(po.getString(i));
        intMap(t.getCompound("BLevels"), s.bLevels);
        intMap(t.getCompound("BLooks"), s.bLooks);
        intMap(t.getCompound("PlotBase"), s.plotBase);
        CompoundTag bs = t.getCompound("BStyles");
        for (String k : bs.getAllKeys()) {
            try {
                s.bStyles.put(Integer.parseInt(k), bs.getString(k));
            } catch (NumberFormatException ignored) {
                // célula inválida: usa o estilo da cidade
            }
        }
        for (int c : t.getIntArray("Damaged")) s.damaged.add(c);
        for (Tag e : t.getList("Council", Tag.TAG_INT_ARRAY)) s.council.add(NbtUtils.loadUUID(e));
        s.people = t.contains("People") ? t.getInt("People") : -1;
        if (t.contains("Food")) s.food = t.getInt("Food");
        if (t.contains("Fuel")) s.fuel = t.getInt("Fuel");
        int[] st = t.getIntArray("LastStats");
        if (st.length >= 7) {
            s.lastFoodProd = st[0];
            s.lastFoodUse = st[1];
            s.lastFuelProd = st[2];
            s.lastFuelUse = st[3];
            s.lastSecurity = st[4];
            s.lastSecurityNeed = st[5];
            s.lastPeopleDelta = st[6];
        }
        s.lowApprovalDays = t.getInt("LowApprovalDays");
        s.raidDue = t.getBoolean("RaidDue");
        s.nextRaidDay = t.contains("NextRaidDay") ? t.getLong("NextRaidDay") : -1;
        s.raidsWon = t.getInt("RaidsWon");
        s.raidsLost = t.getInt("RaidsLost");
        ListTag rq = t.getList("Requests", Tag.TAG_COMPOUND);
        for (int i = 0; i < rq.size(); i++) s.requests.add(rq.getCompound(i));
        s.requestsDay = t.contains("RequestsDay") ? t.getLong("RequestsDay") : -1;
        s.decor = t.getString("Decor");
        s.decorUntil = t.contains("DecorUntil") ? t.getLong("DecorUntil") : -1;
        for (long l : t.getLongArray("DecorBlocks")) s.decorBlocks.add(BlockPos.of(l));
        untag(t.getList("Caravans", Tag.TAG_COMPOUND), s.caravans);
        s.caravanLast = t.getCompound("CaravanLast").copy();
        s.caravanMarket = t.getCompound("CaravanMarket").copy();
        untag(t.getList("Stash", Tag.TAG_COMPOUND), s.stash);
        untag(t.getList("Goals", Tag.TAG_COMPOUND), s.goals);
        ListTag gd = t.getList("GoalsDone", Tag.TAG_STRING);
        for (int i = 0; i < gd.size(); i++) s.goalsDone.add(gd.getString(i));
        int[] life = t.getIntArray("LifeStats");
        if (life.length >= 4) {
            s.caravansSent = life[0];
            s.caravanProfit = life[1];
            s.festivals = life[2];
            s.goalsCompleted = life[3];
        }
        s.scoutDay = t.contains("ScoutDay") ? t.getLong("ScoutDay") : -1;
        s.plazaRot = Math.floorMod(t.getInt("PlazaRot"), 4);
        s.look = t.getInt("Look");
        s.lookGoal = t.getInt("LookGoal");
        s.lookCursor = t.getInt("LookCursor");
        CompoundTag wl = t.getCompound("WallLooks");
        for (String k : wl.getAllKeys()) {
            try {
                s.wallLooks.put(Integer.parseInt(k), wl.getString(k));
            } catch (NumberFormatException ignored) {
                // anel inválido (save editado): a muralha conta como antiga
            }
        }
        for (long l : t.getLongArray("TierBlocks")) s.tierBlocks.add(BlockPos.of(l));
        s.tierDay = t.contains("TierDay") ? t.getLong("TierDay") : -1;
        s.lookGen = t.getInt("LookGen");
        s.paceTime = t.contains("PaceTime") ? t.getLong("PaceTime") : -1;
        s.paceDay = t.contains("PaceDay") ? t.getLong("PaceDay") : -1;
        untag(t.getList("Diary", Tag.TAG_COMPOUND), s.diary);
        s.paceDone = t.getInt("PaceDone");
        s.monuments = t.getCompound("Monuments").copy();
        s.trades = t.getCompound("Trades").copy();
        if (t.contains("Sky", Tag.TAG_COMPOUND)) {
            CompoundTag k = t.getCompound("Sky");
            s.sky = true;
            s.skyLand = Math.max(0, k.getInt("Land"));
            s.skyGoal = Math.max(0, k.getInt("Goal"));
            s.skyRing = Math.max(0, k.getInt("Ring"));
            s.skyIdx = Math.max(0, k.getInt("Idx"));
            s.skyStart = Math.max(0, k.getInt("Start"));
            s.skySkip = k.contains("Skip") ? k.getInt("Skip") : -1;
            s.skyLift = k.getBoolean("Lift");
            s.skyAccess = k.getCompound("Access").copy();
        }
        // onda 8 (water_city): o composto antigo "Sky" continua sendo o que diz "céu"; "Terrain"/"Water" são novos e opcionais
        if (!s.sky && (WaterCity.WATER.equals(t.getString("Terrain")) || t.contains("Water", Tag.TAG_COMPOUND))) {
            s.water = true;
            s.waterData = t.getCompound("Water").copy();
        }
        // onda 9 (isles): arquipélago só vale para cidade do céu ou da água (uma chave perdida numa cidade do chão é ignorada)
        s.isles = (s.sky || s.water) && t.getBoolean("Isles");
        if (s.isles) s.isleData = t.getCompound("IsleData").copy();
        return s;
    }

    private static ListTag tags(List<CompoundTag> list) {
        ListTag l = new ListTag();
        l.addAll(list);
        return l;
    }

    private static void untag(ListTag from, List<CompoundTag> into) {
        for (int i = 0; i < from.size(); i++) into.add(from.getCompound(i));
    }

    private static void intMap(CompoundTag from, Map<Integer, Integer> into) {
        for (String k : from.getAllKeys()) {
            try {
                into.put(Integer.parseInt(k), from.getInt(k));
            } catch (NumberFormatException ignored) {
                // célula inválida (save editado)
            }
        }
    }
}
