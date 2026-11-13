package com.renanpontes.isekaimahou.feature.cities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.cities.entity.Citizen;
import com.renanpontes.isekaimahou.feature.cities.entity.CityGuard;
import com.renanpontes.isekaimahou.feature.cities.entity.CityLord;
import com.renanpontes.isekaimahou.feature.cities.entity.CityMerchant;
import com.renanpontes.isekaimahou.feature.cities.entity.CityNpc;
import com.renanpontes.isekaimahou.feature.cities.entity.Profession;
import com.renanpontes.isekaimahou.feature.cities.network.CitiesNetwork;
import com.renanpontes.isekaimahou.feature.cities.trade.CityTrades;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * Sidequests das cidades (release 1.0 — "sidequests, sim"): pedidos curtos com história, dados pelos moradores.
 * <ul>
 *   <li>Cada tipo de NPC (cidadão, guarda, mercador, Lorde) tem alguns modelos; cada modelo tem 2–3 passos
 *       ({@link Kind}: buscar itens, derrotar monstros, falar com outro morador, ir a um lugar fora da muralha) e termina
 *       com UMA escolha simples entre dois desfechos (recompensas diferentes).</li>
 *   <li>Quem oferece o quê sai de uma conta com o UUID do NPC e o dia: mais ou menos metade dos moradores tem um
 *       pedido por dia, igual para todos os jogadores. Um pedido ativo por jogador; {@link #DAILY_MAX} concluídos por
 *       dia; o mesmo modelo não se repete no mesmo dia.</li>
 *   <li>Recompensa modesta: moedas, reputação no reino da cidade e um pouco de XP.</li>
 * </ul>
 * Textos: {@code quest.isekai_mahou.cities.<id>.*} (tools/gen/cities_npc.py). Estado no jogador: {@code Quest} e
 * {@code QuestDone} em {@link CityPlayer#tag}. A tela é a do diálogo (botão "Pedido"); o passo atual aparece no
 * rastreador de objetivos.
 */
public final class CityQuests {
    public enum Kind { FETCH, KILL, TALK, VISIT }

    /** Um passo: {@code param} = item (ou "prof": o da profissão) / alvo da conversa / "min:max" blocos fora da muralha. */
    public record Step(Kind kind, String param, int amount) {}

    /** Desfecho de uma escolha: moedas, reputação (pode ser negativa) e XP. */
    public record Outcome(int coins, int rep, int xp) {}

    public record Quest(String id, String giver, List<Step> steps, Outcome a, Outcome b) {}

    public static final String KEY = "quest.isekai_mahou.cities.";
    /** Pedidos concluídos por dia de jogo, por jogador. */
    public static final int DAILY_MAX = 3;
    /** Pedido aceito e esquecido some depois deste tanto de dias. */
    private static final int EXPIRE_DAYS = 3;
    private static final int HOOKS = 4, THANKS = 3;
    private static final double VISIT_REACH = 10.0;

    private static final Map<String, Quest> BY_ID = new LinkedHashMap<>();
    private static final Map<String, List<Quest>> BY_GIVER = new HashMap<>();

    private static Step fetch(String item, int n) {
        return new Step(Kind.FETCH, item, n);
    }

    private static Step kill(int n) {
        return new Step(Kind.KILL, "", n);
    }

    private static Step talk(String target) {
        return new Step(Kind.TALK, target, 1);
    }

    private static Step visit(int min, int max) {
        return new Step(Kind.VISIT, min + ":" + max, 1);
    }

    private static void add(String id, String giver, Outcome a, Outcome b, Step... steps) {
        Quest q = new Quest(id, giver, List.of(steps), a, b);
        BY_ID.put(id, q);
        BY_GIVER.computeIfAbsent(giver, k -> new ArrayList<>()).add(q);
    }

    static {
        // ---- Cidadãos ----
        add("lost_cat", "citizen", new Outcome(2, 6, 45), new Outcome(7, 2, 45), talk("citizen"), visit(30, 70));
        add("fever_tea", "citizen", new Outcome(3, 6, 50), new Outcome(8, 1, 50), talk("merchant:alchemist"), fetch("minecraft:dandelion", 4));
        add("lost_purse", "citizen", new Outcome(3, 7, 50), new Outcome(10, -4, 50), talk("guard"), visit(25, 60));
        add("night_noises", "citizen", new Outcome(7, 2, 60), new Outcome(3, 6, 60), kill(5), talk("guard"));
        add("love_letter", "citizen", new Outcome(4, 5, 45), new Outcome(6, 3, 45), talk("citizen"), fetch("minecraft:poppy", 3));
        // ---- Guardas ----
        add("extra_patrol", "guard", new Outcome(4, 7, 70), new Outcome(9, 2, 70), kill(6), visit(40, 90));
        add("lost_recruit", "guard", new Outcome(9, 1, 55), new Outcome(3, 7, 55), talk("merchant:innkeeper"), visit(30, 70));
        add("wall_arrows", "guard", new Outcome(2, 8, 55), new Outcome(8, 2, 55), fetch("minecraft:feather", 6), talk("merchant:blacksmith"));
        add("gate_rumor", "guard", new Outcome(4, 6, 60), new Outcome(8, 2, 60), talk("citizen"), kill(4));
        // ---- Mercadores ----
        add("late_delivery", "merchant", new Outcome(8, 2, 55), new Outcome(3, 7, 55), talk("merchant"), kill(4));
        add("short_stock", "merchant", new Outcome(6, 3, 50), new Outcome(9, 0, 50), fetch("prof", 0), talk("merchant"));
        add("apple_thief", "merchant", new Outcome(9, 1, 55), new Outcome(0, 9, 80), talk("citizen"), visit(25, 60));
        add("rival_prices", "merchant", new Outcome(7, 3, 50), new Outcome(4, 5, 50), talk("merchant"), fetch("minecraft:paper", 3));
        // ---- Lorde ----
        add("people_voice", "lord", new Outcome(6, 10, 100), new Outcome(14, 2, 100), talk("citizen"), talk("merchant"), talk("guard"));
        add("border_threat", "lord", new Outcome(6, 10, 110), new Outcome(15, 3, 110), kill(10), visit(60, 120));
        add("grand_feast", "lord", new Outcome(5, 10, 100), new Outcome(13, 3, 100), fetch("minecraft:wheat", 12), talk("merchant:innkeeper"));
    }

    /** O que cada profissão pede no modelo "estoque em falta". */
    private static ItemStack stockFor(Profession p) {
        return switch (p) {
            case BLACKSMITH -> new ItemStack(Items.COAL, 8);
            case ALCHEMIST -> new ItemStack(Items.SUGAR_CANE, 4);
            case SCRIBE -> new ItemStack(Items.LEATHER, 3);
            case JEWELER -> new ItemStack(Items.GOLD_INGOT, 2);
            case INNKEEPER -> new ItemStack(Items.WHEAT, 8);
            case PRIEST -> new ItemStack(Items.HONEYCOMB, 2);
            case FENCE -> new ItemStack(Items.STRING, 5);
            case GROCER -> new ItemStack(Items.EGG, 5);
            case TAILOR -> new ItemStack(Items.WHITE_WOOL, 5);
            case MECHANIC -> new ItemStack(Items.COPPER_INGOT, 6);
        };
    }

    public static List<String> ids() {
        return new ArrayList<>(BY_ID.keySet());
    }

    // =====================================================================================
    //  Estado
    // =====================================================================================
    /** O pedido ativo do jogador (a tag viva) ou null. */
    @Nullable
    private static CompoundTag active(ServerPlayer p) {
        CompoundTag t = CityPlayer.tag(p);
        if (!t.contains("Quest")) return null;
        CompoundTag q = t.getCompound("Quest");
        if (!BY_ID.containsKey(q.getString("Id"))) {
            t.remove("Quest");   // modelo que não existe mais
            return null;
        }
        return q;
    }

    public static boolean hasActive(ServerPlayer p) {
        return active(p) != null;
    }

    private static Quest quest(CompoundTag q) {
        return BY_ID.get(q.getString("Id"));
    }

    private static boolean ready(CompoundTag q) {
        return q.getInt("Step") >= quest(q).steps().size();
    }

    @Nullable
    private static Step step(CompoundTag q) {
        List<Step> steps = quest(q).steps();
        int i = q.getInt("Step");
        return i >= 0 && i < steps.size() ? steps.get(i) : null;
    }

    private static boolean isGiver(CompoundTag q, CityNpc npc) {
        return q.hasUUID("Giver") && q.getUUID("Giver").equals(npc.getUUID());
    }

    private static Component giverName(ServerPlayer p, CompoundTag q) {
        try {
            Component c = Component.Serializer.fromJson(q.getString("GiverName"), p.registryAccess());
            if (c != null) return c;
        } catch (RuntimeException e) {
            // nome ilegível (tag mexida à mão): cai no "?"
        }
        return Component.literal("?");
    }

    private static long day(ServerPlayer p) {
        return p.server.overworld().getDayTime() / 24000L;
    }

    /** Pedidos concluídos hoje ({@code QuestToday} vale só para o dia {@code QuestTodayDay}). */
    private static int doneToday(ServerPlayer p) {
        CompoundTag t = CityPlayer.tag(p);
        return t.getLong("QuestTodayDay") == day(p) ? t.getInt("QuestToday") : 0;
    }

    private static void clear(ServerPlayer p) {
        CityPlayer.tag(p).remove("Quest");
        sync(p);
    }

    // =====================================================================================
    //  Quem oferece o quê
    // =====================================================================================
    private static long mix(UUID id, long day) {
        long h = id.getLeastSignificantBits() ^ (day * 0x9E3779B97F4A7C15L) ^ id.getMostSignificantBits();
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return h & Long.MAX_VALUE;
    }

    private static int chance(String kind) {
        return switch (kind) {
            case "lord" -> 100;
            case "guard" -> 35;
            default -> 45;
        };
    }

    /** Só a conta do dia (barata, sem buscas): este morador tem um pedido hoje? */
    private static boolean offersToday(CityNpc npc) {
        List<Quest> list = BY_GIVER.get(npc.kindId());
        if (list == null || list.isEmpty()) return false;
        long h = mix(npc.getUUID(), npc.level().getDayTime() / 24000L);
        return h % 100 < chance(npc.kindId());
    }

    /** O pedido que este morador tem para este jogador agora (null = nenhum). */
    @Nullable
    private static Quest offer(ServerPlayer p, CityNpc npc) {
        if (!offersToday(npc) || npc.isWarEnemy(p) || CityDialogue.mood(p, npc).equals("hostile")) return null;
        if (CityCrimes.isWanted(p, npc.kingdomFaction())) return null;
        ServerLevel sl = p.serverLevel();
        if (CityPlaces.cityAt(sl, anchor(npc)) == null) return null;
        if (!p.getAbilities().instabuild && doneToday(p) >= DAILY_MAX) return null;
        List<Quest> list = BY_GIVER.get(npc.kindId());
        long today = day(p);
        long h = mix(npc.getUUID(), npc.level().getDayTime() / 24000L);
        int start = (int) ((h / 100) % list.size());
        CompoundTag done = CityPlayer.tag(p).getCompound("QuestDone");
        for (int i = 0; i < list.size(); i++) {
            Quest c = list.get((start + i) % list.size());
            if (done.contains(c.id()) && done.getLong(c.id()) == today) continue;
            if (feasible(sl, npc, c)) return c;
        }
        return null;
    }

    private static BlockPos anchor(CityNpc npc) {
        return npc.home() != null ? npc.home() : npc.blockPosition();
    }

    /** Todo alvo de conversa do modelo existe na cidade (perto de quem pede)? */
    private static boolean feasible(ServerLevel sl, CityNpc giver, Quest c) {
        List<CityNpc> near = null;
        for (Step s : c.steps()) {
            if (s.kind() != Kind.TALK) continue;
            if (near == null) near = sl.getEntitiesOfClass(CityNpc.class, new AABB(anchor(giver)).inflate(72, 24, 72), LivingEntity::isAlive);
            boolean any = false;
            for (CityNpc n : near) {
                if (n != giver && matches(s.param(), n)) {
                    any = true;
                    break;
                }
            }
            if (!any) return false;
        }
        return true;
    }

    private static boolean matches(String target, CityNpc npc) {
        if (target.startsWith("merchant:")) {
            return npc instanceof CityMerchant m && m.profession().id.equals(target.substring("merchant:".length()));
        }
        return switch (target) {
            case "guard" -> npc instanceof CityGuard;
            case "citizen" -> npc instanceof Citizen;
            case "lord" -> npc instanceof CityLord;
            case "merchant" -> npc instanceof CityMerchant;
            default -> false;
        };
    }

    private static boolean isTalkTarget(CompoundTag q, CityNpc npc) {
        Step s = step(q);
        if (s == null || s.kind() != Kind.TALK || isGiver(q, npc) || !matches(s.param(), npc)) return false;
        if (!npc.level().dimension().location().toString().equals(q.getString("Dim"))) return false;
        BlockPos home = BlockPos.of(q.getLong("Home"));
        double dx = npc.getX() - home.getX(), dz = npc.getZ() - home.getZ();
        return dx * dx + dz * dz < 200.0 * 200.0;
    }

    /** O diálogo mostra o botão "Pedido"? */
    public static boolean hasButton(ServerPlayer p, CityNpc npc) {
        CompoundTag q = active(p);
        if (q != null) return isGiver(q, npc) || isTalkTarget(q, npc);
        return offer(p, npc) != null;
    }

    /** Fala solta de quem tem um pedido hoje, para o jogador perceber (null = fala normal). */
    @Nullable
    public static Component hook(CityNpc npc, Player near) {
        if (!(near instanceof ServerPlayer sp) || npc.getRandom().nextInt(100) >= 40 || !offersToday(npc)) return null;
        if (hasActive(sp) || npc.isWarEnemy(sp) || doneToday(sp) >= DAILY_MAX) return null;
        if (CityDialogue.mood(sp, npc).equals("hostile") || CityCrimes.isWanted(sp, npc.kingdomFaction())) return null;
        return Component.translatable(KEY + "hook." + npc.getRandom().nextInt(HOOKS));
    }

    // =====================================================================================
    //  Diálogo
    // =====================================================================================
    /** Clique no botão "Pedido" ou numa das opções ({@code arg}). */
    public static void handle(ServerPlayer p, CityNpc npc, String arg) {
        switch (arg) {
            case "" -> open(p, npc);
            case "accept" -> accept(p, npc);
            case "decline" -> CityDialogue.send(p, npc, Component.translatable(KEY + "declined"));
            case "back" -> CityDialogue.send(p, npc, Component.translatable(KEY + "later"));
            case "abandon" -> {
                CompoundTag q = active(p);
                if (q != null && isGiver(q, npc)) clear(p);
                CityDialogue.send(p, npc, Component.translatable(KEY + "abandoned"));
            }
            case "a", "b" -> finish(p, npc, arg.equals("a"));
            default -> { }
        }
    }

    private static CitiesNetwork.Service option(String id, Component label, Component desc) {
        return new CitiesNetwork.Service(id, 0, true, label, desc);
    }

    private static Component rewardText(Outcome o) {
        return Component.translatable(KEY + "reward", o.coins(), (o.rep() > 0 ? "+" : "") + o.rep(), o.xp());
    }

    private static Component title(Quest c) {
        return Component.translatable(KEY + c.id() + ".title");
    }

    private static void open(ServerPlayer p, CityNpc npc) {
        CompoundTag q = active(p);
        if (q == null) {
            Quest c = offer(p, npc);
            if (c == null) {
                CityDialogue.send(p, npc, Component.translatable(KEY + "none"));
                return;
            }
            CityDialogue.sendChoice(p, npc, Component.translatable(KEY + c.id() + ".intro", p.getDisplayName()),
                    List.of(option("accept", Component.translatable(KEY + "accept"), Component.empty()),
                            option("decline", Component.translatable(KEY + "decline"), Component.empty())),
                    Component.translatable(KEY + "note", title(c)));
            return;
        }
        Quest c = quest(q);
        if (isTalkTarget(q, npc)) {
            int i = q.getInt("Step");
            advance(p, q, false);
            npc.playAction("wave");
            CityDialogue.send(p, npc, Component.translatable(KEY + c.id() + ".s" + i + ".say", p.getDisplayName(), giverName(p, q)));
            return;
        }
        if (!isGiver(q, npc)) {
            CityDialogue.send(p, npc, Component.translatable(KEY + "busy", giverName(p, q)));
            return;
        }
        // Quem pediu: entrega de itens, a escolha final ou o lembrete do passo.
        Step s = step(q);
        Component line = null;
        if (s != null && s.kind() == Kind.FETCH) {
            Item item = fetchItem(q);
            int need = q.getInt("Need");
            if (item != null && count(p, item) >= need) {
                take(p, item, need);
                int i = q.getInt("Step");
                advance(p, q, false);
                line = Component.translatable(KEY + c.id() + ".s" + i + ".say", p.getDisplayName());
                p.level().playSound(null, npc.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.7F, 1.0F);
            }
        }
        if (ready(q)) {
            if (line == null) line = Component.translatable(KEY + c.id() + ".ready", p.getDisplayName());
            CityDialogue.sendChoice(p, npc, line,
                    List.of(option("a", Component.translatable(KEY + c.id() + ".a"), rewardText(c.a())),
                            option("b", Component.translatable(KEY + c.id() + ".b"), rewardText(c.b()))),
                    Component.translatable(KEY + "choose"));
            return;
        }
        if (line == null) line = Component.translatable(KEY + "remind", stepText(p, q));
        CityDialogue.sendChoice(p, npc, line,
                List.of(option("back", Component.translatable(KEY + "back"), Component.empty()),
                        option("abandon", Component.translatable(KEY + "abandon"), Component.translatable(KEY + "abandon.desc"))),
                Component.translatable(KEY + "note", title(c)));
    }

    private static void accept(ServerPlayer p, CityNpc npc) {
        if (active(p) != null) return;
        Quest c = offer(p, npc);
        if (c == null) {
            CityDialogue.send(p, npc, Component.translatable(KEY + "none"));
            return;
        }
        start(p, npc, c);
        npc.playAction("cheer");
        CityDialogue.send(p, npc, Component.translatable(KEY + "thanks." + npc.getRandom().nextInt(THANKS), p.getDisplayName()));
    }

    /** Começa o pedido {@code c} com {@code npc} como quem pediu (também usado pelo comando de teste). */
    public static void start(ServerPlayer p, CityNpc npc, Quest c) {
        ServerLevel sl = p.serverLevel();
        CompoundTag q = new CompoundTag();
        q.putString("Id", c.id());
        q.putInt("Step", 0);
        q.putInt("Count", 0);
        q.putUUID("Giver", npc.getUUID());
        q.putString("GiverName", Component.Serializer.toJson(npc.getDisplayName(), p.registryAccess()));
        q.putLong("Home", anchor(npc).asLong());
        Places.Place city = CityPlaces.cityAt(sl, anchor(npc));
        q.putString("City", city != null ? city.id() : "");
        q.putString("Faction", npc.kingdomFaction());
        q.putString("Dim", sl.dimension().location().toString());
        q.putLong("Day", day(p));
        if (npc instanceof CityMerchant m) q.putString("Prof", m.profession().id);
        CityPlayer.tag(p).put("Quest", q);
        beginStep(p, q);
        sync(p);
        com.renanpontes.isekaimahou.feature.ui_hud.Hints.show(p, "cities.request");   // dica de primeira vez
        p.level().playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.1F);
    }

    private static void finish(ServerPlayer p, CityNpc npc, boolean first) {
        CompoundTag q = active(p);
        if (q == null || !isGiver(q, npc) || !ready(q)) return;
        Quest c = quest(q);
        Outcome o = first ? c.a() : c.b();
        String faction = q.getString("Faction");
        Component reason = Component.translatable("reason.isekai_mahou.cities.quest");
        if (o.coins() > 0) Rewards.give(p, new ItemStack(CityTrades.coin(), o.coins()));
        if (o.rep() > 0 && !faction.isEmpty()) Reputation.addDaily(p, faction, o.rep(), "cities.quest", 40, reason);
        else if (o.rep() < 0 && !faction.isEmpty()) Reputation.add(p, faction, o.rep(), reason);
        if (o.xp() > 0) MagicApi.giveXp(p, o.xp(), "cities.quest");
        MagicApi.addCounter(p, "cities.sidequest", 1);
        MagicApi.addCounter(p, "quest.cities_" + c.id(), 1);
        CompoundTag t = CityPlayer.tag(p);
        t.putInt("QuestToday", doneToday(p) + 1);
        t.putLong("QuestTodayDay", day(p));
        CompoundTag done = t.getCompound("QuestDone");
        done.putLong(c.id(), day(p));
        t.put("QuestDone", done);
        clear(p);
        Rewards.toast(p, Component.translatable(KEY + "done"), title(c), new ItemStack(CityTrades.coin()), 0xE0C070);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.3F);
        npc.playAction(npc instanceof CityLord ? "bow" : "cheer");
        CityDialogue.send(p, npc, Component.translatable(KEY + c.id() + (first ? ".a.done" : ".b.done"), p.getDisplayName()));
    }

    // =====================================================================================
    //  Passos
    // =====================================================================================
    /** Prepara o passo atual: sorteia o lugar (VISIT) ou resolve o item (FETCH). */
    private static void beginStep(ServerPlayer p, CompoundTag q) {
        Step s = step(q);
        q.putInt("Count", 0);
        q.remove("Target");
        if (s == null) return;
        if (s.kind() == Kind.FETCH) {
            ItemStack want;
            if (s.param().equals("prof")) {
                Profession prof = Profession.byId(q.getString("Prof"));
                want = stockFor(prof != null ? prof : Profession.GROCER);
            } else {
                ResourceLocation id = ResourceLocation.tryParse(s.param());
                Item item = id == null ? Items.WHEAT : BuiltInRegistries.ITEM.getOptional(id).orElse(Items.WHEAT);
                want = new ItemStack(item, s.amount());
            }
            q.putString("Item", BuiltInRegistries.ITEM.getKey(want.getItem()).toString());
            q.putInt("Need", want.getCount());
            q.putInt("Count", Math.min(want.getCount(), count(p, want.getItem())));
        } else if (s.kind() == Kind.VISIT) {
            q.putLong("Target", visitTarget(p.serverLevel(), q, s).asLong());
        }
    }

    /** Um ponto em terra firme fora da muralha, a "min..max" blocos dela, numa direção sorteada. */
    private static BlockPos visitTarget(ServerLevel sl, CompoundTag q, Step s) {
        BlockPos home = BlockPos.of(q.getLong("Home"));
        Places.Place city = q.getString("City").isEmpty() ? null : Places.get(sl, q.getString("City"));
        BlockPos center = city != null ? city.center() : home;
        int half = city != null ? Math.max(city.box().getXSpan(), city.box().getZSpan()) / 2 : 40;
        String[] range = s.param().split(":");
        int min = Integer.parseInt(range[0]), max = Integer.parseInt(range[1]);
        RandomSource r = RandomSource.create(mix(q.getUUID("Giver"), q.getLong("Day") * 31L + q.getInt("Step")));
        int sea = sl.getSeaLevel();
        BlockPos best = null;
        for (int i = 0; i < 8; i++) {
            double a = r.nextDouble() * Math.PI * 2.0;
            // A cidade é quadrada: nos cantos a muralha fica mais longe do centro.
            double edge = half / Math.max(Math.abs(Math.cos(a)), Math.abs(Math.sin(a)));
            double dist = edge + min + r.nextInt(Math.max(1, max - min));
            int x = center.getX() + (int) Math.round(Math.cos(a) * dist);
            int z = center.getZ() + (int) Math.round(Math.sin(a) * dist);
            int y = sl.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, sl,
                    sl.getChunkSource().randomState());
            best = new BlockPos(x, y, z);
            int floor = sl.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, sl,
                    sl.getChunkSource().randomState());
            if (floor >= sea - 1) break;   // terra firme: serve
        }
        return best;
    }

    @Nullable
    private static Item fetchItem(CompoundTag q) {
        ResourceLocation id = ResourceLocation.tryParse(q.getString("Item"));
        return id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
    }

    private static int count(Player p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private static void take(ServerPlayer p, Item item, int amount) {
        if (p.getAbilities().instabuild) return;
        int left = amount;
        for (ItemStack s : p.getInventory().items) {
            if (left <= 0) break;
            if (!s.is(item)) continue;
            int n = Math.min(left, s.getCount());
            s.shrink(n);
            left -= n;
        }
        p.getInventory().setChanged();
    }

    /** Passa ao passo seguinte (ou ao "volte a quem pediu"). {@code narrate}: conta no chat o que aconteceu. */
    private static void advance(ServerPlayer p, CompoundTag q, boolean narrate) {
        Quest c = quest(q);
        int i = q.getInt("Step");
        if (narrate) {
            p.displayClientMessage(Component.translatable(KEY + c.id() + ".s" + i + ".say", p.getDisplayName(), giverName(p, q))
                    .withStyle(net.minecraft.ChatFormatting.GRAY, net.minecraft.ChatFormatting.ITALIC), false);
        }
        q.putInt("Step", i + 1);
        beginStep(p, q);
        p.displayClientMessage(Component.translatable(KEY + "next", ready(q)
                ? Component.translatable(KEY + "return", giverName(p, q)) : stepText(p, q)), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5F, 1.2F);
        sync(p);
    }

    /** Texto curto do passo atual (rastreador, lembrete). */
    private static MutableComponent stepText(ServerPlayer p, CompoundTag q) {
        Quest c = quest(q);
        Step s = step(q);
        if (s == null) return Component.translatable(KEY + "return", giverName(p, q));
        String key = KEY + c.id() + ".s" + q.getInt("Step");
        return switch (s.kind()) {
            case FETCH -> {
                Item item = fetchItem(q);
                yield Component.translatable(key, q.getInt("Need"), item != null ? item.getDescription() : Component.literal("?"));
            }
            case KILL -> Component.translatable(key, s.amount());
            case TALK -> Component.translatable(key, Component.translatable(KEY + "target." + s.param().replace(':', '.')));
            case VISIT -> Component.translatable(key, CityRumors.direction(BlockPos.of(q.getLong("Home")), BlockPos.of(q.getLong("Target"))));
        };
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================
    /** Monstro derrotado pelo jogador (ou por um companheiro dele) conta no passo de caça. */
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide || !(victim instanceof Enemy) || victim instanceof CityNpc) return;
        Entity killer = e.getSource().getEntity();
        if (killer instanceof OwnableEntity pet && pet.getOwner() instanceof ServerPlayer owner) killer = owner;
        if (!(killer instanceof ServerPlayer sp)) return;
        CompoundTag q = active(sp);
        Step s = q == null ? null : step(q);
        if (s == null || s.kind() != Kind.KILL) return;
        int n = q.getInt("Count") + 1;
        q.putInt("Count", n);
        if (n >= s.amount()) advance(sp, q, true);
        else sync(sp);
    }

    /** A cada 2 s por jogador: chegou ao lugar? o pedido caducou? (e a contagem de itens do rastreador) */
    public static void tick(ServerPlayer p) {
        CompoundTag q = active(p);
        if (q == null) return;
        if (day(p) - q.getLong("Day") > EXPIRE_DAYS) {
            Component who = giverName(p, q);
            clear(p);
            p.displayClientMessage(Component.translatable(KEY + "expired", who), true);
            return;
        }
        Step s = step(q);
        if (s == null) return;
        if (s.kind() == Kind.VISIT && q.contains("Target") && p.level().dimension().location().toString().equals(q.getString("Dim"))) {
            BlockPos t = BlockPos.of(q.getLong("Target"));
            double dx = p.getX() - t.getX(), dz = p.getZ() - t.getZ();
            if (dx * dx + dz * dz <= VISIT_REACH * VISIT_REACH) advance(p, q, true);
        } else if (s.kind() == Kind.FETCH) {
            Item item = fetchItem(q);
            int have = item == null ? 0 : Math.min(q.getInt("Need"), count(p, item));
            if (have != q.getInt("Count")) {
                q.putInt("Count", have);
                sync(p);
            }
        }
    }

    /** Manda o pedido ativo ao rastreador do cliente (login, mudança). */
    public static void sync(ServerPlayer p) {
        CompoundTag q = active(p);
        if (q == null) {
            CitiesNetwork.send(p, CitiesNetwork.QuestView.none());
            return;
        }
        Quest c = quest(q);
        Step s = step(q);
        ResourceLocation dim = ResourceLocation.tryParse(q.getString("Dim"));
        if (dim == null) dim = p.level().dimension().location();
        int cur = 0, max = 0;
        boolean arrow = false;
        BlockPos pos = BlockPos.ZERO;
        if (s == null) {
            arrow = true;
            pos = BlockPos.of(q.getLong("Home"));
        } else if (s.kind() == Kind.VISIT && q.contains("Target")) {
            arrow = true;
            pos = BlockPos.of(q.getLong("Target"));
        } else if (s.kind() == Kind.KILL) {
            cur = q.getInt("Count");
            max = s.amount();
        } else if (s.kind() == Kind.FETCH) {
            cur = q.getInt("Count");
            max = q.getInt("Need");
        }
        CitiesNetwork.send(p, new CitiesNetwork.QuestView(true, title(c), stepText(p, q), cur, max, arrow, pos, dim));
    }

    // =====================================================================================
    //  Comandos de teste
    // =====================================================================================
    @Nullable
    public static Quest byId(String id) {
        return BY_ID.get(id);
    }

    /** Pula o passo atual. false = sem pedido ativo ou já pronto para entregar. */
    public static boolean debugStep(ServerPlayer p) {
        CompoundTag q = active(p);
        if (q == null || ready(q)) return false;
        advance(p, q, true);
        return true;
    }

    /** Apaga o pedido ativo e zera os limites do dia. */
    public static void debugReset(ServerPlayer p) {
        CompoundTag t = CityPlayer.tag(p);
        t.remove("Quest");
        t.remove("QuestDone");
        t.remove("QuestToday");
        t.remove("QuestTodayDay");
        sync(p);
    }

    /** Resumo do pedido ativo (comando de estado). */
    @Nullable
    public static Component debugStatus(ServerPlayer p) {
        CompoundTag q = active(p);
        if (q == null) return null;
        return Component.empty().append(title(quest(q))).append(" — ").append(stepText(p, q));
    }

    private CityQuests() {}
}
