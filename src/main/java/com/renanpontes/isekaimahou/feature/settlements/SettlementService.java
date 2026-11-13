package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Allegiance;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.api.Travel;
import com.renanpontes.isekaimahou.feature.settlements.block.SettlementHeartBlockEntity;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprint;
import com.renanpontes.isekaimahou.feature.settlements.build.Blueprints;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.build.ConstructionEngine;
import com.renanpontes.isekaimahou.feature.settlements.build.Project;
import com.renanpontes.isekaimahou.feature.settlements.entity.BuilderNpc;
import com.renanpontes.isekaimahou.feature.settlements.entity.GuardNpc;
import com.renanpontes.isekaimahou.feature.settlements.entity.ResidentNpc;
import com.renanpontes.isekaimahou.feature.settlements.entity.SettlementNpc;
import com.renanpontes.isekaimahou.feature.settlements.network.SettlementsNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Cidades governadas por jogadores (docs/V10_DESIGN.md §3 "settlements"): fundação pela Carta, coração, plantas e
 * obras, chegada de moradores/guardas/mercadores/construtores, impostos e afinidade do povo (conta diária), decretos,
 * cidadania e a tela de governo (estado em NBT para o cliente). O Juramento/desafio fica em {@link Conquest} e a
 * guerra em {@link WarService}.
 */
public final class SettlementService {
    public static final int FESTIVAL_COST = 40;
    /** Alegria na abertura do festival (v1.0: +8 na hora e +4 por dia de festa — eram +15 de uma vez, todo dia). */
    public static final int FESTIVAL_AFFINITY = 8;
    /** Tamanho da fila de obras (v1.0: bem maior — com muitos construtores a cidade cresce a rodo). */
    public static final int MAX_QUEUE = 24;
    private static final int LOST_AFTER = 6000;
    private static final ResourceLocation CITY_HEART = IsekaiMahou.id("city_heart");
    private static final ResourceLocation TRAVEL_STONE = IsekaiMahou.id("travel_stone");
    private static final ResourceLocation GUILD_COIN = IsekaiMahou.id("guild_coin");

    /** Último valor da estatística "negociou com aldeão" por jogador (para contar trocas nas cidades). */
    private static final Map<UUID, Integer> TRADE_STATS = new HashMap<>();
    /** Último aviso de "cidade" mandado ao rastreador (para não repetir pacote igual). */
    private static final Map<UUID, Integer> TRACKER_HASH = new HashMap<>();

    // =====================================================================================
    //  Consultas
    // =====================================================================================
    @Nullable
    public static Settlement city(MinecraftServer server, String id) {
        return id.isEmpty() ? null : SettlementData.get(server).city(id);
    }

    /** Moeda dos impostos e do comércio: Moeda da Guilda (se o módulo existir) ou esmeralda. */
    public static Item coinItem() {
        return BuiltInRegistries.ITEM.getOptional(GUILD_COIN).orElse(Items.EMERALD);
    }

    /** O dia das cidades: segue o relógio do mundo, mas nunca volta (um "/time set" não trava mais nada). */
    public static long day(MinecraftServer server) {
        return SettlementData.get(server).day(server.overworld().getDayTime());
    }

    @Nullable
    public static ServerLevel level(MinecraftServer server, Settlement s) {
        return server.getLevel(s.dim);
    }

    /** O lugar do tipo cidade/capital que contém a posição (mesmo com um lugar menor dentro). */
    @Nullable
    public static Places.Place cityAt(ServerLevel level, BlockPos pos) {
        Places.Place here = Places.at(level, pos);
        if (here == null) return null;
        if (isCity(here)) return here;
        for (Places.Place p : Places.all(level)) {
            if (isCity(p) && p.dim() == level.dimension() && p.contains(pos)) return p;
        }
        return null;
    }

    public static boolean isCity(@Nullable Places.Place p) {
        return p != null && ("city".equals(p.type()) || "capital".equals(p.type()));
    }

    /** Senhor, alguém do grupo dele ou cidadão: a cidade protege. */
    public static boolean isProtected(MinecraftServer server, Settlement s, Player p) {
        // v1.4: cidadãos e conselheiros continuam protegidos mesmo com a cidade sem governante
        if (s.id.equals(Allegiance.of(p)) || s.council.contains(p.getUUID())) return true;
        if (s.owner == null) return false;
        if (p.getUUID().equals(s.owner)) return true;
        return Party.members(p).contains(s.owner);
    }

    public static Component cityName(MinecraftServer server, Settlement s) {
        return s.own() && !s.name.isEmpty() ? Component.literal(s.name) : Allegiance.cityName(server, s.id);
    }

    // =====================================================================================
    //  Fundação
    // =====================================================================================
    public static void openFounding(ServerPlayer sp, BlockPos pos) {
        Component problem = foundingProblem(sp, pos);
        if (problem != null) {
            sp.displayClientMessage(problem.copy().withColor(0xE8735E), true);
            sp.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 1.0F, 1.0F);
            return;
        }
        PacketDistributor.sendToPlayer(sp, new SettlementsNetwork.FoundOpen(pos));
    }

    @Nullable
    public static Component foundingProblem(ServerPlayer sp, BlockPos pos) {
        return foundingProblem(sp, pos, SkyCity.GROUND);
    }

    /**
     * Por que não dá para fundar aqui (null = pode). {@code terrain} = {@link SkyCity#GROUND} ou {@link SkyCity#SKYLAND}
     * (onda 7): no céu o ponto clicado continua sendo o do chão, mas a caixa conferida é a da ILHA, lá em cima, e o
     * fundador precisa também do Núcleo Celeste.
     */
    @Nullable
    public static Component foundingProblem(ServerPlayer sp, BlockPos pos, String terrain) {
        ServerLevel sl = sp.serverLevel();
        boolean sky = SkyCity.SKYLAND.equals(terrain);
        if (sl.dimension() != Level.OVERWORLD) return Component.translatable("settlements.isekai_mahou.found.overworld");
        SettlementData data = SettlementData.get(sp.server);
        if (data.ownedBy(sp.getUUID()) != null) return Component.translatable("settlements.isekai_mahou.found.already");
        // onda 8 (water_city): sobre a água o "chão" é a superfície dela — as regras do lugar são as da Cidade das Águas,
        // e daqui em diante o ponto conferido é o do Coração (2 blocos acima da água)
        boolean water = WaterCity.WATER.equals(terrain);
        if (water) {
            Component wet = WaterCity.siteProblem(sl, pos);
            if (wet != null) return wet;
            pos = WaterCity.heartPos(sl, pos);
        } else if (!sky && (!sl.getBlockState(pos).canBeReplaced() || !sl.getBlockState(pos.below()).isSolid())) {
            // onda 9 (fundar mais fácil): no CÉU o ponto do chão é só a coluna da cidade — vale água, folha, morro...
            return Component.translatable("settlements.isekai_mahou.found.ground");
        }
        BlockPos heart = pos;
        if (sky) {
            int y = SkyCity.heartY(sl, pos);
            if (y < 0) return SettlementsLang.tr("sky.found.too_high");
            heart = new BlockPos(pos.getX(), y, pos.getZ());
        }
        // onda 9 (isles): a Cidade das Águas nova é um arquipélago — o território do começo é o dele (mais espaçado)
        BoundingBox mine = sky ? SkyCity.box(heart, CityLayout.START_RADIUS).inflatedBy(8)
                : water ? isleStart(pos).inflatedBy(8) : CityLayout.territory(pos).inflatedBy(8);
        // ilha sobre cidade/vila dos outros faria sombra nela: para elas a caixa da ilha vale do céu até o fundo do mundo
        BoundingBox shadow = sky ? new BoundingBox(mine.minX(), sl.getMinBuildHeight(), mine.minZ(), mine.maxX(), mine.maxY(), mine.maxZ()) : mine;
        for (Places.Place p : Places.all(sl)) {
            if (p.dim() != sl.dimension()) continue;
            boolean town = isCity(p) || "village".equals(p.type());
            // com uma cidade do céu na conta (a nova ou a vizinha) a distância é a do mapa: a altura não afasta ninguém
            boolean flat = sky || SkyCity.isSky(data.city(p.id()));
            double d = flat ? Math.hypot(p.center().getX() - pos.getX(), p.center().getZ() - pos.getZ()) : Math.sqrt(p.center().distSqr(pos));
            // onda 9 (fundar mais fácil): vila pede menos distância que cidade; e o aviso diz QUANTO falta andar
            int min = "village".equals(p.type()) ? CityLayout.MIN_VILLAGE_DISTANCE : CityLayout.MIN_CITY_DISTANCE;
            BoundingBox mineBox = town ? shadow : mine;
            boolean overlap = p.box().intersects(mineBox);
            if (overlap || town && d < min) {
                int more = overlap ? escape(p.box(), mineBox) : 0;
                if (town && d < min) more = Math.max(more, (int) Math.ceil(min - d));
                return SettlementsLang.tr("found.move_away", p.name(), (int) d, more + 2);
            }
        }
        if (sky) {
            Component blocked = SkyCity.obstacle(sl, heart, CityLayout.START_RADIUS, true);
            if (blocked != null) return blocked;
        }
        if (findCharter(sp) < 0) return Component.translatable("settlements.isekai_mahou.found.no_charter");
        if (sky && findSkyCore(sp) < 0) return SettlementsLang.tr("sky.found.no_core");
        return null;
    }

    /** Onda 9: território do começo de uma cidade de ilhas com o Coração em {@code pos}. */
    private static BoundingBox isleStart(BlockPos pos) {
        int h = CityLayout.isleHalf(CityLayout.START_RADIUS);
        return new BoundingBox(pos.getX() - h, pos.getY() - CityLayout.BOX_DOWN, pos.getZ() - h, pos.getX() + h, pos.getY() + CityLayout.BOX_UP,
                pos.getZ() + h);
    }

    /** Quantos blocos (no eixo mais fácil, x ou z) a caixa {@code mine} precisa andar para sair de cima de {@code other}. */
    private static int escape(BoundingBox other, BoundingBox mine) {
        int ox = Math.min(other.maxX(), mine.maxX()) - Math.max(other.minX(), mine.minX()) + 1;
        int oz = Math.min(other.maxZ(), mine.maxZ()) - Math.max(other.minZ(), mine.minZ()) + 1;
        return Math.max(0, Math.min(ox, oz));
    }

    public static int findCharterSlot(ServerPlayer sp) {
        return findCharter(sp);
    }

    private static int findCharter(ServerPlayer sp) {
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            if (sp.getInventory().getItem(i).is(SettlementsContent.FOUNDING_CHARTER.get())) return i;
        }
        return -1;
    }

    /** Onda 7: espaço do inventário com um Núcleo Celeste (o custo de fundar no céu), ou -1. */
    public static int findSkyCore(ServerPlayer sp) {
        for (int i = 0; i < sp.getInventory().getContainerSize(); i++) {
            if (sp.getInventory().getItem(i).is(SettlementsContent.SKY_CORE.get())) return i;
        }
        return -1;
    }

    public static String sanitizeName(String raw) {
        StringBuilder b = new StringBuilder();
        for (char c : raw.toCharArray()) {
            if (c >= 32 && c != '§' && c != 127 && b.length() < 32) b.append(c);
        }
        return b.toString().trim();
    }

    /** Funda a cidade (vindo da tela de estilo). */
    public static void found(ServerPlayer sp, BlockPos pos, SettlementStyle style, String rawName) {
        found(sp, pos, style, rawName, 0);
    }

    /**
     * Funda a cidade no ponto e com a rotação da praça escolhidos na prévia (v1.0: o holograma deixa mover/girar
     * antes; só aqui a Carta é gasta).
     */
    public static void found(ServerPlayer sp, BlockPos pos, SettlementStyle style, String rawName, int rot) {
        found(sp, pos, style, rawName, rot, SkyCity.GROUND);
    }

    /**
     * Onda 9 (comandos de teste): põe a Carta (e, no céu, o Núcleo Celeste) no inventário se faltar. Inventário cheio
     * não falha mais em silêncio: devolve o motivo para o comando mostrar no chat. null = está tudo no inventário.
     */
    @Nullable
    public static Component giveFoundingItems(ServerPlayer sp, boolean sky) {
        if (findCharter(sp) < 0 && !sp.getInventory().add(new net.minecraft.world.item.ItemStack(SettlementsContent.FOUNDING_CHARTER.get()))) {
            return SettlementsLang.tr("found.full");
        }
        if (sky && findSkyCore(sp) < 0 && !sp.getInventory().add(new net.minecraft.world.item.ItemStack(SettlementsContent.SKY_CORE.get()))) {
            return SettlementsLang.tr("found.full");
        }
        return null;
    }

    /**
     * Onda 7: funda escolhendo o TIPO DE TERRENO. {@link SkyCity#SKYLAND} = Ilha Celeste: {@code at} continua sendo o
     * ponto escolhido no chão; o Coração nasce no céu acima dele ({@link SkyCity#heartY}), a terra se forma aos poucos
     * e o fundador é levado para a praça quando o miolo existir. Gasta a Carta e, no céu, também o Núcleo Celeste.
     * Qualquer outro valor = no chão, como sempre foi.
     */
    @Nullable
    public static Component found(ServerPlayer sp, BlockPos at, SettlementStyle style, String rawName, int rot, String terrain) {
        boolean sky = SkyCity.SKYLAND.equals(terrain);
        // onda 8 (water_city): terceiro tipo de terreno — "water" = Cidade das Águas ({@code at} = o ponto da água)
        boolean water = !sky && WaterCity.WATER.equals(terrain);
        int reach = CityPlots.REACH + 24;
        if (sp.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) > reach * reach) return SettlementsLang.tr("found.far");
        Component problem = foundingProblem(sp, at, sky ? SkyCity.SKYLAND : water ? WaterCity.WATER : SkyCity.GROUND);
        if (problem != null) {
            sp.displayClientMessage(problem.copy().withColor(0xE8735E), true);
            return problem;
        }
        ServerLevel sl = sp.serverLevel();
        // no céu o Coração (e tudo o que usa a posição da cidade) fica lá em cima; o id segue x/z, como no chão
        BlockPos pos = sky ? new BlockPos(at.getX(), SkyCity.heartY(sl, at), at.getZ()) : at;
        // na água o Coração fica 2 blocos acima dela (o piso da praça é o bloco logo acima da água)
        if (water) {
            BlockPos wet = WaterCity.heartPos(sl, at);
            if (wet == null) return SettlementsLang.tr("water.found.aim");
            pos = wet;
        }
        int slot = findCharter(sp);
        if (!sp.isCreative()) {
            sp.getInventory().getItem(slot).shrink(1);
            if (sky) sp.getInventory().getItem(findSkyCore(sp)).shrink(1);
        }
        String name = sanitizeName(rawName);
        // a tela já sugere um nome traduzido; sem nome nenhum, a cidade leva o nome do fundador
        if (name.isEmpty()) name = sp.getGameProfile().getName();
        String id = "settlement_" + pos.getX() + "_" + pos.getZ();
        Settlement s = new Settlement(id, Settlement.Kind.OWN, style, sl.dimension(), pos, name);
        s.owner = sp.getUUID();
        s.ownerName = sp.getGameProfile().getName();
        s.affinity = 50;
        s.lastDay = day(sp.server);
        s.plazaRot = Math.floorMod(rot, 4);
        s.sky = sky;
        s.water = water;
        // onda 9 (isles): toda cidade NOVA do céu ou da água é um arquipélago (ilhas/plataformas ligadas por pontes)
        s.isles = sky || water;
        Project plaza = new Project(Blueprint.PLAZA, -1, List.of(new Project.Piece(Blueprint.template(style, Blueprint.PLAZA),
                pos.getX(), pos.getZ(), s.plazaRotation(), Project.Piece.BUILDING, pos.getY() - 1)));
        // v1.0: a praça nasce com o lote inteiro aplainado e a rua em volta (os lotes vizinhos seguem este nível)
        plaza.clearHalf = com.renanpontes.isekaimahou.feature.settlements.build.CityTerrain.LEVEL_HALF;
        s.projects.add(plaza);
        SettlementData.get(sp.server).put(s);

        sl.setBlock(pos, SettlementsContent.HEART.get().defaultBlockState(), 3);
        if (sl.getBlockEntity(pos) instanceof SettlementHeartBlockEntity be) be.bind(id, style);
        registerPlace(sl, s);
        Allegiance.set(sp, id);
        Allegiance.setLeader(sp.server, id, sp.getUUID());
        Travel.discover(sp, id);
        if (sky) {
            // a terra começa a se formar; os dois construtores chegam (e o fundador sobe) quando o miolo da praça existir
            SkyCity.founded(sp, sl, s, at);
        } else if (water) {
            // o miolo do deque nasce na hora (chão para o fundador e os dois construtores); o resto é obra, sobre estacas
            WaterCity.founded(sp, sl, s);
            WaterLife.refresh(sp.server);
        } else {
            spawnNpc(sl, s, Settlement.Role.BUILDER, pos.offset(2, 0, 2), pos);
            spawnNpc(sl, s, Settlement.Role.BUILDER, pos.offset(-2, 0, 2), pos);
        }

        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5, 80, 1.5, 1.5, 1.5, 0.3);
        sl.playSound(null, pos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 1.5F, 0.8F);
        sl.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
        com.renanpontes.isekaimahou.core.MagicApi.addCounter(sp, "settlements.founded", 1); // v1.0: marco da campanha (story)
        Rewards.banner(sp, Component.translatable("settlements.isekai_mahou.found.banner", Component.literal(name)), style.color);
        sp.server.getPlayerList().broadcastSystemMessage(Component.translatable(
                sky ? "settlements.isekai_mahou.sky.found.announce" : water ? "settlements.isekai_mahou.water.found.announce"
                        : "settlements.isekai_mahou.found.announce",
                sp.getDisplayName(), Component.literal(name).withColor(style.color), style.displayName()), false);
        // no céu a tela não abre sozinha: o fundador assiste à terra se formar e sobe para a praça
        sendState(sp, id, !sky);
        return null;
    }

    /**
     * Onda 9 (pedido do dono: "devemos facilitar começar a cidade"): dica única, para quem já jogou um dia inteiro e
     * ainda não fundou nada, de onde tirar a Carta (Escriba da cidade inicial ou a receita barata) e como usá-la.
     */
    private static void foundHint(MinecraftServer server) {
        SettlementData data = SettlementData.get(server);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (sp.isSpectator() || data.ownedBy(sp.getUUID()) != null) continue;
            if (com.renanpontes.isekaimahou.feature.ui_hud.Hints.seen(sp, "city_how")) continue;
            if (sp.getStats().getValue(net.minecraft.stats.Stats.CUSTOM.get(net.minecraft.stats.Stats.PLAY_TIME)) < 24000) continue;
            com.renanpontes.isekaimahou.feature.ui_hud.Hints.show(sp, "city_how");
        }
    }

    /** (Re)registra o lugar da cidade própria (nome, estilo, caixa do território). */
    public static void registerPlace(ServerLevel sl, Settlement s) {
        // onda 5: o letreiro e o mapa mostram o degrau ("Brisa · Vila")
        Places.register(sl, new Places.Place(s.id, CityTier.placeName(s), "city", "", s.dim, CityLayout.territory(s),
                s.heart.immutable(), true, s.style.color));
    }

    // =====================================================================================
    //  Coração
    // =====================================================================================
    public static void openHeart(ServerPlayer sp, String cityId) {
        if (city(sp.server, cityId) == null) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.heart.orphan"), true);
            return;
        }
        sendState(sp, cityId, true);
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.2F);
    }

    public static void tickHeart(ServerLevel sl, SettlementHeartBlockEntity be) {
        Settlement s = city(sl.getServer(), be.cityId());
        if (s == null || !s.own()) return;
        int age = be.age();
        if (age == 20) {
            // lugar sumido, ou ainda com o nome de antes dos degraus (mundo antigo): registra de novo
            Places.Place place = Places.get(sl, s.id);
            if (place == null || !place.name().equals(CityTier.placeName(s))) registerPlace(sl, s);
        }
        // onda 7 (sky_island): a terra da Ilha Celeste se forma/cresce aos poucos (cidade no chão: não faz nada)
        SkyCity.tick(sl, s, age);
        // onda 7 (sky_ui): enfeites da ilha (cachoeiras, ilhotas, cristais) — entram quando a terra termina de se formar
        SkyDecor.tick(sl, s, age);
        // onda 8 (water_city): passarelas, pontes, cais e enfeites da Cidade das Águas (outras cidades: não faz nada)
        WaterWorks.tick(sl, s, age);
        ConstructionEngine.tick(sl, s, age);
        // onda 6 (city_pace): novo dia da cidade (comerciantes, monumentos...) — só com o coração carregado
        if (age % 20 == 7) CityPace.tickHeart(sl, s);
        // onda 5: reforma do degrau (ruas, postes, praça, fachadas, muralhas), aos poucos
        if (age % 5 == 2) CityRenewal.tick(sl, s, age);
        if (age % 100 == 60) Lordship.gates(sl, s);
        // onda 6: a aura do Obelisco do Sol só vale com ele ATIVO (limite de monumentos do degrau)
        if (age % 100 == 80 && s.wonder("obelisk") && CityMonuments.active(s, "obelisk")) Lordship.obeliskAura(sl, s);
        if (age % 600 == 300) populate(sl, s);
        // v1.0: construtores que faltam chegam logo (a cada 10 s), não um a cada 30 s
        else if (age % 200 == 130 && s.population(Settlement.Role.BUILDER) < s.cap(Settlement.Role.BUILDER)) populate(sl, s);
        CityDecor.tick(sl, s, age);
        if (age % 100 == 50) CityPerks.tick(sl, s);
    }

    /** Coração quebrado (criativo/comando): a cidade acaba. */
    public static void onHeartRemoved(ServerLevel sl, String cityId) {
        Settlement s = city(sl.getServer(), cityId);
        if (s != null && s.own()) dissolve(sl, s);
    }

    /** Abandona a cidade própria: lugar, lealdades, guerras e NPCs somem; os prédios ficam como ruínas habitáveis. */
    public static void dissolve(ServerLevel sl, Settlement s) {
        MinecraftServer server = sl.getServer();
        for (String enemy : new HashSet<>(Allegiance.enemiesOf(server, s.id))) Allegiance.makePeace(server, s.id, enemy);
        for (UUID u : Allegiance.citizens(server, s.id)) {
            ServerPlayer p = server.getPlayerList().getPlayer(u);
            if (p != null) Allegiance.set(p, null);
        }
        Allegiance.setLeader(server, s.id, null);
        ConstructionEngine.clearSigns(sl, s);
        CityDecor.clearNow(sl, s);
        // onda 7 (sky_access): a corrente acabou — o marco no chão sai (a ilha e o cais ficam)
        SkyAccess.dissolved(sl, s);
        Places.remove(sl, s.id);
        SettlementData.get(server).remove(s.id);
        if (sl.getBlockState(s.heart).is(SettlementsContent.HEART.get())) sl.removeBlock(s.heart, false);
        // os NPCs somem sozinhos no próximo sinal de vida (a cidade não existe mais)
    }

    // =====================================================================================
    //  Plantas e obras
    // =====================================================================================
    /** Por que não dá para construir (null = pode). */
    @Nullable
    public static Component buildProblem(Settlement s, Blueprint bp) {
        if (bp.isPlaza()) return Component.translatable("settlements.isekai_mahou.bp.auto");
        if (bp.isWall()) {
            int k = nextWall(s);
            return k < 0 ? SettlementsLang.tr("walls.all_built") : wallProblem(s, k);
        }
        if (!s.has(Blueprint.PLAZA)) return Component.translatable("settlements.isekai_mahou.bp.need_plaza");
        if (bp.minLevel() > s.level) return Component.translatable("settlements.isekai_mahou.bp.need_level", bp.minLevel());
        if (s.count(bp.key()) + s.inProgress(bp.key()) >= CityGrowth.maxCount(s, bp)) return Component.translatable("settlements.isekai_mahou.bp.maxed");
        if (s.projects.size() >= MAX_QUEUE) return Component.translatable("settlements.isekai_mahou.bp.queue_full", MAX_QUEUE);
        if (bp.usesCell() && freeCell(s) < 0) return Component.translatable("settlements.isekai_mahou.bp.no_cell");
        return null;
    }

    /** Primeiro terreno livre liberado no nível atual (mais perto da praça primeiro). */
    public static int freeCell(Settlement s) {
        Set<Integer> used = new HashSet<>(s.buildings.keySet());
        for (Project p : s.projects) used.add(p.cell);
        // v1.0 (city_plan): terreno coberto por um prédio em posição livre não conta como vago
        List<Integer> free = CityPlots.freeOccupied(s);
        for (int c : CityLayout.cellsFor(s)) {
            if (!used.contains(c) && !CityPlots.gridBlocked(free, c, Integer.MIN_VALUE)) return c;
        }
        return -1;
    }

    /** Muralha k em obra? */
    public static boolean wallInProgress(Settlement s, int k) {
        for (Project p : s.projects) if (Blueprint.WALL.equals(p.key) && CityLayout.wallOf(p) == k) return true;
        return false;
    }

    /** Primeira muralha que ainda dá para pedir (nem pronta nem em obra), ou -1. */
    public static int nextWall(Settlement s) {
        for (int k = 1; k <= CityLayout.wallSlots(s); k++) if (!s.walls.contains(k) && !wallInProgress(s, k)) return k;
        return -1;
    }

    /** Por que a muralha k não pode ser construída (null = pode). */
    @Nullable
    public static Component wallProblem(Settlement s, int k) {
        Blueprint bp = Blueprints.get(s.style, Blueprint.WALL);
        if (bp == null || k < 1 || k > CityLayout.wallSlots(s)) return SettlementsLang.tr("walls.outside");
        if (!s.has(Blueprint.PLAZA)) return Component.translatable("settlements.isekai_mahou.bp.need_plaza");
        if (bp.minLevel() > s.level) return Component.translatable("settlements.isekai_mahou.bp.need_level", bp.minLevel());
        if (s.walls.contains(k)) return SettlementsLang.tr("walls.done");
        if (wallInProgress(s, k)) return SettlementsLang.tr("walls.building");
        if (s.projects.size() >= MAX_QUEUE) return Component.translatable("settlements.isekai_mahou.bp.queue_full", MAX_QUEUE);
        return null;
    }

    /** Põe a muralha k na fila. */
    public static boolean queueWall(ServerPlayer sp, Settlement s, int k) {
        Component problem = wallProblem(s, k);
        if (problem != null) {
            sp.displayClientMessage(problem.copy().withColor(0xE8735E), true);
            sp.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.PLAYERS, 0.8F, 1.0F);
            return false;
        }
        // onda 5: a muralha nasce no grau do degrau (paliçada na Vila, pedra no Burgo, fortificada na Cidade...)
        Project p = new Project(Blueprint.WALL, CityLayout.wallCell(k), CityLayout.wallPieces(s, k,
                com.renanpontes.isekaimahou.feature.settlements.build.TierLook.wallTier(s.level)));
        p.style = s.style.id;
        s.projects.add(p);
        sp.displayClientMessage(SettlementsLang.tr("walls.queued", roman(k)).withColor(s.style.color), true);
        sp.playNotifySound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    public static String roman(int k) {
        String[] r = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return k >= 1 && k <= r.length ? r[k - 1] : Integer.toString(k);
    }

    private static void queue(ServerPlayer sp, Settlement s, String key) {
        Blueprint bp = Blueprints.get(s.style, key);
        if (bp == null) return;
        Component problem = buildProblem(s, bp);
        if (problem != null) {
            sp.displayClientMessage(problem.copy().withColor(0xE8735E), true);
            return;
        }
        if (bp.isWall()) {
            queueWall(sp, s, nextWall(s));
            return;
        }
        Project p;
        {
            int cell = freeCell(s);
            BlockPos c = CityLayout.cellCenter(s.heart, cell);
            int variant = Math.floorMod(s.count(key) + s.inProgress(key) + cell, Blueprints.variants(key));
            Integer base = s.plotBase.get(cell);
            p = new Project(key, cell, List.of(new Project.Piece(bp.template(variant, 1), c.getX(), c.getZ(), CityLayout.facingHeart(cell),
                    Project.Piece.BUILDING, base != null ? base : Integer.MIN_VALUE)));
            p.style = s.style.id;
            p.clearHalf = com.renanpontes.isekaimahou.feature.settlements.build.CityTerrain.LEVEL_HALF;
        }
        s.projects.add(p);
        sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.bp.queued", bp.displayName()).withColor(s.style.color), true);
        sp.playNotifySound(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.PLAYERS, 1.0F, 1.0F);
    }

    public static void onProjectDone(ServerLevel sl, Settlement s, Project p) {
        // onda 6: andaime esquecido (peça pulada, molde sumido) não fica de pé
        ConstructionEngine.dropScaffold(sl, p);
        // cidade em festa: o prédio novo também ganha enfeites
        if (CityDecor.isOn(s)) CityDecor.refresh(s);
        if (CityPlanning.onDone(sl, s, p)) {
            if (p.cell >= 0) CityRenewal.onBuilt(sl, s, p.cell);
            // onda 6 (city_pace): inauguração, Diário e o gancho de "obra concluída"
            CityPace.finished(sl, s, p);
            populate(sl, s);
            refreshViewers(sl.getServer(), s);
            return;
        }
        SettlementStyle pst = SettlementStyle.byId(p.style);
        Blueprint bp = Blueprints.get(pst == null ? s.style : pst, p.key);
        if (bp == null) bp = Blueprints.get(s.style, p.key);
        if (bp == null) return;
        if (bp.isWall()) {
            s.addWall(CityLayout.wallOf(p));
            // com que grau/estilo ela foi erguida (a reforma do degrau compara com isto)
            // onda 8 (city_looks): peça por peça — muralha que mudou de estilo/grau no meio da obra fica "mixed" e é conferida
            s.wallLooks.put(CityLayout.wallOf(p), CityRenewal.wallLookOf(s, p));
        } else {
            s.buildings.put(bp.isPlaza() ? CityLayout.CENTER_CELL : p.cell, p.key);
        }
        if (p.cell >= 0) CityRenewal.onBuilt(sl, s, p.cell);
        s.points += bp.points();
        if (bp.isPlaza()) findStone(sl, s);
        BlockPos at = bp.isWall() ? s.heart : p.cell >= 0 ? CityLayout.cellCenter(s.heart, p.cell) : s.heart;
        sl.sendParticles(ParticleTypes.FIREWORK, at.getX() + 0.5, at.getY() + 6, at.getZ() + 0.5, 60, 3, 2, 3, 0.15);
        sl.playSound(null, at, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1.2F, 1.2F);
        for (ServerPlayer g : CityEconomy.governors(sl.getServer(), s)) {
            Rewards.toast(g, Component.translatable("settlements.isekai_mahou.done.title", bp.displayName()),
                    cityName(sl.getServer(), s), new ItemStack(Items.BRICKS), s.style.color);
        }
        // v1.4: o nível da cidade não sobe sozinho — é o NÍVEL DO CENTRO (requisitos + reforma no planejamento)
        if (CityEconomy.centerReady(sl.getServer(), s)) {
            for (ServerPlayer g : CityEconomy.governors(sl.getServer(), s)) {
                g.displayClientMessage(SettlementsLang.tr("rank.ready", CityTier.name(s.level + 1)).withColor(0xFFD86B), false);
            }
        }
        // onda 6 (city_pace): inauguração, Diário e o gancho de "obra concluída"
        CityPace.finished(sl, s, p);
        populate(sl, s);
        refreshViewers(sl.getServer(), s);
    }

    private static void findStone(ServerLevel sl, Settlement s) {
        Block stone = BuiltInRegistries.BLOCK.getOptional(TRAVEL_STONE).orElse(null);
        if (stone == null) return;
        for (BlockPos p : BlockPos.betweenClosed(s.heart.offset(-7, -1, -7), s.heart.offset(7, 3, 7))) {
            if (sl.getBlockState(p).is(stone)) {
                s.stone = p.immutable();
                return;
            }
        }
    }

    // =====================================================================================
    //  NPCs
    // =====================================================================================
    @Nullable
    public static SettlementNpc spawnNpc(ServerLevel sl, Settlement s, Settlement.Role role, BlockPos at, BlockPos home) {
        EntityType<? extends SettlementNpc> type = switch (role) {
            case BUILDER -> SettlementsContent.BUILDER.get();
            case RESIDENT -> SettlementsContent.RESIDENT.get();
            case GUARD -> SettlementsContent.GUARD.get();
            case MERCHANT -> SettlementsContent.MERCHANT.get();
            case MILITIA -> SettlementsContent.MILITIA.get();
        };
        // onda 7 (sky_island): numa Ilha Celeste ninguém nasce onde a terra ainda não existe (cairia no vazio)
        if (s.sky && !SkyCity.hasLand(s, at.getX(), at.getZ())) {
            at = s.heart.offset(2, 0, 2);
            if (!SkyCity.hasLand(s, at.getX(), at.getZ())) return null;
        }
        // onda 8 (water_city): na Cidade das Águas ninguém nasce dentro d'água — vale o piso firme mais perto
        if (s.water) at = WaterCity.firmOrPlaza(sl, s, at, 12);
        SettlementNpc npc = type.create(sl);
        if (npc == null) return null;
        int y = sl.hasChunk(at.getX() >> 4, at.getZ() >> 4) ? sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ()) : at.getY();
        // (na água o ponto já veio com a altura certa do piso: medir de novo poria o NPC em cima de um telhado)
        if (s.water) y = at.getY();
        npc.moveTo(at.getX() + 0.5, y, at.getZ() + 0.5, sl.random.nextFloat() * 360F, 0F);
        npc.finalizeSpawn(sl, sl.getCurrentDifficultyAt(at), MobSpawnType.MOB_SUMMONED, null);
        String faction = s.own() ? "" : placeFaction(sl, s);
        npc.bind(s, home, faction);
        if (npc instanceof GuardNpc g) g.refreshPower(s);
        if (npc instanceof ResidentNpc r) r.setHouse(houseFor(s, s.population(Settlement.Role.RESIDENT)));
        sl.addFreshEntity(npc);
        s.npcs.get(role).add(npc.getUUID());
        s.lastSeen.put(npc.getUUID(), sl.getGameTime());
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, npc.getX(), npc.getY() + 1, npc.getZ(), 10, 0.4, 0.6, 0.4, 0.02);
        return npc;
    }

    private static String placeFaction(ServerLevel sl, Settlement s) {
        Places.Place p = Places.get(sl, s.id);
        return p == null ? "" : p.faction();
    }

    @Nullable
    private static BlockPos houseFor(Settlement s, int index) {
        List<Integer> homes = new ArrayList<>();
        s.buildings.forEach((cell, key) -> {
            if (key.startsWith("house") || key.equals("farm") || key.equals("tavern")) homes.add(cell);
        });
        if (homes.isEmpty()) return null;
        return CityLayout.cellCenter(s.heart, homes.get(index % homes.size()));
    }

    @Nullable
    private static BlockPos workplace(Settlement s, String... keys) {
        for (String k : keys) {
            for (Map.Entry<Integer, String> e : s.buildings.entrySet()) {
                if (e.getValue().equals(k)) return CityLayout.cellCenter(s.heart, e.getKey());
            }
        }
        return null;
    }

    /** Sinal de vida de um NPC. Falso = a cidade não o quer mais (sumiu ou está lotada). */
    public static boolean heartbeat(ServerLevel sl, SettlementNpc npc) {
        Settlement s = city(sl.getServer(), npc.cityId());
        if (s == null) return false;
        // onda 7 (sky_access): numa Ilha Celeste ninguém se perde — quem caiu ou ficou fora da ilha volta para casa
        if (s.sky) SkyAccess.rescue(sl, s, npc);
        // onda 8 (water_city): quem caiu na água volta para a passarela mais perto
        if (s.water) WaterLife.rescue(sl, s, npc);
        // onda 6 (city_trades): comerciantes de ofício têm lista própria (não disputam vaga com os mascates)
        if (npc instanceof com.renanpontes.isekaimahou.feature.settlements.entity.TraderNpc t) return CityTrades.heartbeat(sl, s, t);
        List<UUID> list = s.npcs.get(npc.role());
        if (!list.contains(npc.getUUID())) {
            if (list.size() >= s.cap(npc.role())) return false;
            list.add(npc.getUUID());
        }
        s.lastSeen.put(npc.getUUID(), sl.getGameTime());
        return true;
    }

    public static void onNpcDeath(ServerLevel sl, SettlementNpc npc, DamageSource source) {
        Settlement s = city(sl.getServer(), npc.cityId());
        if (s == null) return;
        s.npcs.get(npc.role()).remove(npc.getUUID());
        s.lastSeen.remove(npc.getUUID());
        s.affinity = Math.max(0, s.affinity - 1);
        ServerPlayer owner = s.owner == null ? null : sl.getServer().getPlayerList().getPlayer(s.owner);
        if (owner != null) {
            owner.displayClientMessage(Component.translatable("settlements.isekai_mahou.npc.died", npc.getDisplayName(),
                    cityName(sl.getServer(), s)).withColor(0xE8735E), false);
        }
    }

    /** Some com os perdidos e traz quem falta (um por vez; construtores sempre que faltar). */
    public static void populate(ServerLevel sl, Settlement s) {
        long now = sl.getGameTime();
        for (Settlement.Role r : Settlement.Role.values()) {
            if (r == Settlement.Role.MILITIA) continue;   // milicianos andam longe com o Senhor
            s.npcs.get(r).removeIf(u -> {
                Long seen = s.lastSeen.get(u);
                if (seen == null) {
                    s.lastSeen.put(u, now);
                    return false;
                }
                return now - seen > LOST_AFTER;
            });
        }
        if (!s.own()) {
            if (s.population(Settlement.Role.GUARD) < s.cap(Settlement.Role.GUARD)) {
                spawnNpc(sl, s, Settlement.Role.GUARD, s.heart.offset(sl.random.nextInt(7) - 3, 0, sl.random.nextInt(7) - 3), s.heart);
            }
            return;
        }
        int gate = sl.random.nextInt(4);
        BlockPos arrive = CityLayout.arrivalPoint(s, gate);
        if (s.population(Settlement.Role.BUILDER) < s.cap(Settlement.Role.BUILDER)) {
            // até 2 construtores por vez (a Casa dos Construtores enche rápido)
            announceArrival(sl, s, spawnNpc(sl, s, Settlement.Role.BUILDER, arrive, s.heart));
            if (s.population(Settlement.Role.BUILDER) < s.cap(Settlement.Role.BUILDER)) {
                spawnNpc(sl, s, Settlement.Role.BUILDER, arrive.offset(sl.random.nextInt(5) - 2, 0, sl.random.nextInt(5) - 2), s.heart);
            }
            return;
        }
        if (!s.has(Blueprint.PLAZA)) return;
        Settlement.Role[] order = {Settlement.Role.GUARD, Settlement.Role.MERCHANT, Settlement.Role.RESIDENT};
        for (Settlement.Role r : order) {
            if (s.population(r) >= s.cap(r)) continue;
            BlockPos home = switch (r) {
                case MERCHANT -> {
                    BlockPos w = workplace(s, "market", "tavern");
                    yield w == null ? s.heart : w;
                }
                default -> s.heart;
            };
            announceArrival(sl, s, spawnNpc(sl, s, r, arrive, home));
            return;
        }
    }

    private static void announceArrival(ServerLevel sl, Settlement s, @Nullable SettlementNpc npc) {
        if (npc == null || s.owner == null) return;
        ServerPlayer owner = sl.getServer().getPlayerList().getPlayer(s.owner);
        if (owner != null && owner.level() == sl && owner.distanceToSqr(npc) < 128 * 128) {
            owner.displayClientMessage(Component.translatable("settlements.isekai_mahou.arrival", npc.getDisplayName()).withColor(s.style.color), true);
        }
    }

    /** Segunda página da conversa de um NPC: o que ele acha da cidade. */
    public static Component npcStatusLine(SettlementNpc npc, Settlement s) {
        MinecraftServer server = npc.getServer();
        switch (npc.role()) {
            case BUILDER -> {
                if (s.projects.isEmpty()) return Component.translatable("settlements.isekai_mahou.status.builder.idle");
                Project p = s.projects.get(0);
                Blueprint bp = Blueprints.get(s.style, p.key);
                return Component.translatable("settlements.isekai_mahou.status.builder.busy",
                        bp == null ? Component.literal(p.key) : bp.displayName(), Math.round(CityPace.fraction(p) * 100));
            }
            case GUARD -> {
                if (server != null && !Allegiance.enemiesOf(server, s.id).isEmpty()) {
                    String first = Allegiance.enemiesOf(server, s.id).iterator().next();
                    return Component.translatable("settlements.isekai_mahou.status.guard.war", Allegiance.cityName(server, first));
                }
                return SettlementsLang.tr("status.guard.gates." + s.gates);
            }
            case MERCHANT -> {
                return SettlementsLang.tr("status.merchant.tax." + s.tax);
            }
            case MILITIA -> {
                return SettlementsLang.tr("status.militia.order." + s.militiaOrder);
            }
            default -> {
                String mood = s.affinity >= 80 ? "happy" : s.affinity >= 40 ? "ok" : "sad";
                return SettlementsLang.tr("status.resident." + mood);
            }
        }
    }

    // =====================================================================================
    //  Servidor: contas diárias, trocas, visitas
    // =====================================================================================
    public static void tickServer(MinecraftServer server) {
        int t = server.getTickCount();
        // onda 7: o calendário das cidades lê o relógio a cada segundo (para notar um "/time set" da noite para o dia)
        if (t % 20 == 3) day(server);
        if (t % 20 == 0) {
            Conquest.tickPlayers(server);
            WarService.tickPlayers(server);
            CityRaids.tick(server);
        }
        // onda 6 (city_pace): as obras andam pelos dias do mundo, com o Senhor perto ou longe (conta leve, sem tocar no mundo)
        if (t % 20 == 10) CityPace.tickServer(server);
        // v1.0 (city_life): caravanas e pedidos dos moradores (contas leves, sem tocar no mundo)
        if (t % 100 == 70) CityCaravans.tick(server);
        // onda 7 (sky_island): lista das ilhas para o teste de spawn debaixo delas
        if (t % 200 == 90) SkyCity.refreshShades(server);
        // onda 9 (fundar mais fácil): quem já jogou um dia e ainda não tem cidade ganha UMA dica de como começar
        if (t % 1200 == 610) foundHint(server);
        if (t % 200 == 150) CityGoals.tick(server);
        if (t % 100 == 40) {
            pollTrades(server);
            lordVisits(server);
            sendTrackers(server);
        }
        if (t % 200 == 100) {
            long today = day(server);
            SettlementData data = SettlementData.get(server);
            for (Settlement s : data.all()) {
                if (!s.own() && !Lordship.validateConquered(server, s)) continue;
                if (s.lastDay < 0) s.lastDay = today;
                if (today > s.lastDay) {
                    s.lastDay = today;
                    Lordship.daily(server, s);
                    // onda 6: uma linha sobre as obras, depois da conta do dia
                    CityPace.dawn(server, s);
                }
                if (!s.own() && t % 600 == 100) {
                    ServerLevel sl = level(server, s);
                    if (sl != null && sl.hasChunk(s.heart.getX() >> 4, s.heart.getZ() >> 4)) populate(sl, s);
                }
            }
        }
    }

    /** Conta as trocas feitas DENTRO de cada cidade governada (estatística "negociou", vale para qualquer mercador). */
    private static void pollTrades(MinecraftServer server) {
        SettlementData data = SettlementData.get(server);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            int now = sp.getStats().getValue(Stats.CUSTOM.get(Stats.TRADED_WITH_VILLAGER));
            Integer before = TRADE_STATS.put(sp.getUUID(), now);
            if (before == null || now <= before) continue;
            Places.Place p = cityAt(sp.serverLevel(), sp.blockPosition());
            Settlement s = p == null ? null : data.city(p.id());
            if (s != null) s.tradesToday += Math.min(64, now - before);
        }
    }

    private static void lordVisits(MinecraftServer server) {
        for (Settlement s : SettlementData.get(server).all()) {
            if (s.lordVisited || s.owner == null) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(s.owner);
            if (p != null && p.level().dimension() == s.dim && p.blockPosition().distSqr(s.heart) < 64 * 64) s.lordVisited = true;
        }
    }

    /** Linhas do rastreador: obras da cidade própria (o Juramento vai junto, ver {@link Conquest#trackerTag}). */
    private static void sendTrackers(MinecraftServer server) {
        SettlementData data = SettlementData.get(server);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            CompoundTag tag = new CompoundTag();
            Settlement own = data.ownedBy(sp.getUUID());
            // onda 7 (city_pause): o rastreador segue a primeira obra que está ANDANDO (com tudo pausado, a linha some)
            if (own != null && !own.projects.isEmpty() && !CityPause.allPaused(own)) {
                Project p = own.projects.get(0);
                for (Project q : own.projects) {
                    if (!q.paused) {
                        p = q;
                        break;
                    }
                }
                tag.putString("BuildKey", "*");
                tag.putString("BuildName", Component.Serializer.toJson(CityPlanning.projectName(own, p), sp.registryAccess()));
                CityPace.ensure(server, own, p);
                tag.putInt("BuildPct", Math.round(CityPace.fraction(p) * 100));
                tag.putInt("BuildQueue", own.projects.size());
                tag.put("BuildPos", net.minecraft.nbt.NbtUtils.writeBlockPos(own.heart));
                tag.putString("BuildCity", own.name);
            }
            Conquest.trackerTag(sp, tag);
            int hash = tag.hashCode();
            Integer old = TRACKER_HASH.put(sp.getUUID(), hash);
            if (old == null || old != hash) PacketDistributor.sendToPlayer(sp, new SettlementsNetwork.Tracker(tag));
        }
    }

    /** Progresso de uma obra (com o tamanho certo da fase, se o molde já carregou). */
    public static float progressOf(MinecraftServer server, Settlement s, Project p) {
        ServerLevel sl = level(server, s);
        Project.Piece piece = p.current();
        if (sl == null || piece == null) return p.progress(1);
        var tpl = com.renanpontes.isekaimahou.feature.settlements.build.Templates.get(sl, piece.template);
        if (tpl == null) return p.progress(1);
        return p.progress(ConstructionEngine.phaseSteps(p, piece, tpl));
    }

    // =====================================================================================
    //  Coração das cidades de reino (módulo cities): agachar + clicar abre Juramento/governo
    // =====================================================================================
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || e.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        BlockState st = sp.level().getBlockState(e.getPos());
        // v1.4: comida/combustível na mão + clique no coração da cidade fundada = entregar no celeiro
        if (st.is(SettlementsContent.HEART.get()) && !sp.isShiftKeyDown()
                && sp.level().getBlockEntity(e.getPos()) instanceof SettlementHeartBlockEntity hb) {
            Settlement own = city(sp.server, hb.cityId());
            if (own != null && depositHand(sp, own)) {
                e.setCanceled(true);
                e.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
            }
            return;
        }
        if (!CITY_HEART.equals(BuiltInRegistries.BLOCK.getKey(st.getBlock()))) return;
        boolean seal = sp.getMainHandItem().is(SettlementsContent.OATH_SEAL.get());
        if (!seal && !sp.isShiftKeyDown()) {
            Places.Place at = cityAt(sp.serverLevel(), e.getPos());
            Settlement gov = at == null ? null : SettlementData.get(sp.server).city(at.id());
            if (gov != null && depositHand(sp, gov)) {
                e.setCanceled(true);
                e.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
            }
            return;
        }
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.CONSUME);
        if (seal) {
            Conquest.useSealOn(sp, e.getPos());
            return;
        }
        Places.Place p = cityAt(sp.serverLevel(), e.getPos());
        if (p != null) sendState(sp, p.id(), true);
    }

    /** Entrega a pilha da mão (comida ou combustível) no celeiro da cidade. Falso = não era comida/combustível. */
    public static boolean depositHand(ServerPlayer sp, Settlement s) {
        var hand = sp.getMainHandItem();
        if (hand.isEmpty() || CityEconomy.foodValue(hand, sp) <= 0 && CityEconomy.fuelValue(hand) <= 0) return false;
        if (!CityEconomy.isMember(sp.server, s, sp)) {
            sp.displayClientMessage(SettlementsLang.tr("needs.deposit.member").withColor(0xE8735E), true);
            return true;
        }
        int food = s.food, fuel = s.fuel;
        int n = CityEconomy.depositStack(sp.server, s, sp, hand);
        if (n <= 0) {
            sp.displayClientMessage(SettlementsLang.tr("needs.deposit.full").withColor(0xE8735E), true);
            return true;
        }
        CityEconomy.feedback(sp, s, s.food - food, s.fuel - fuel);
        return true;
    }

    // =====================================================================================
    //  Ações da tela
    // =====================================================================================
    public static void handleAction(ServerPlayer sp, String cityId, String action, String arg) {
        MinecraftServer server = sp.server;
        Settlement s = city(server, cityId);
        boolean owner = s != null && sp.getUUID().equals(s.owner);
        boolean planner = s != null && s.canPlan(sp.getUUID());
        switch (action) {
            case "build" -> {
                if (planner && s.own()) queue(sp, s, arg);
            }
            case "cancel" -> {
                if (planner && s.own()) cancel(sp, s, parse(arg, -1));
            }
            case "tax" -> {
                if (owner) s.tax = Math.max(0, Math.min(2, parse(arg, 1)));
            }
            case "gates" -> {
                if (owner) s.gates = Math.max(0, Math.min(2, parse(arg, 0)));
            }
            case "order" -> {
                if (owner) s.militiaOrder = parse(arg, 0) == 1 ? Settlement.ORDER_DEFEND : Settlement.ORDER_FOLLOW;
            }
            case "withdraw" -> {
                if (owner) Lordship.withdraw(sp, s, parse(arg, 64));
            }
            case "festival" -> {
                if (planner) Lordship.festival(sp, s);
            }
            case "declare" -> {
                if (owner) WarService.declare(sp, s, arg);
            }
            case "peace" -> {
                if (owner) WarService.proposePeace(sp, s, arg);
            }
            case "accept_peace" -> {
                if (owner) WarService.acceptPeace(sp, s, arg);
            }
            case "train" -> {
                if (owner) WarService.trainMilitia(sp, s);
            }
            case "join" -> joinCity(sp, cityId);
            case "leave" -> {
                if (cityId.equals(Allegiance.of(sp)) && !owner) Allegiance.set(sp, null);
            }
            case "oath" -> Conquest.startOath(sp, cityId);
            case "tribute" -> Conquest.payTribute(sp);
            case "challenge" -> Conquest.challengeFromMenu(sp, cityId);
            case "abdicate" -> {
                if (owner && !s.own()) stepDown(server, s, true);
            }
            case "abandon" -> {
                if (owner && s.own() && "confirm".equals(arg)) {
                    // v1.4: abandonar só tira VOCÊ do poder; a cidade fica com tudo (prédios, obras, povo, estoques, nível)
                    sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.abandoned", cityName(server, s)).withColor(0xE8735E), false);
                    stepDown(server, s, true);
                }
            }
            case "claim" -> claim(sp, s);
            default -> {
                if (s != null && !CityActions.handle(sp, s, action, arg)) return;
            }
        }
        sendState(sp, cityId, false);
    }

    private static int parse(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /**
     * Onda 7 (city_pause): desistir de uma obra em QUALQUER etapa. As regras (o que pode, o desmonte seguro do canteiro,
     * a devolução proporcional — inclusive a da reforma de prédio da onda 5 — e o prédio mudado de lugar que só se
     * pausa) ficam no {@link CityPause}.
     */
    private static void cancel(ServerPlayer sp, Settlement s, int index) {
        CityPause.cancelFromPanel(sp, s, index);
    }

    /**
     * O governante deixa o poder (abandonou, abdicou ou caiu numa revolta). NADA da cidade é apagado: o poder passa ao
     * primeiro conselheiro/cidadão que pode governar; sem ninguém, a cidade fica independente (sem governo) com tudo
     * que tem. Cidade de reino conquistada volta para o reino (o registro fica, o próximo Senhor herda).
     */
    public static void stepDown(MinecraftServer server, Settlement s, boolean voluntary) {
        UUID old = s.owner;
        if (!s.own()) {
            Conquest.abdicate(server, s, voluntary);
            return;
        }
        UUID next = null;
        SettlementData data = SettlementData.get(server);
        List<UUID> candidates = new ArrayList<>(s.council);
        for (UUID u : Allegiance.citizens(server, s.id)) if (!candidates.contains(u)) candidates.add(u);
        for (UUID u : candidates) {
            if (u.equals(old)) continue;
            Settlement theirs = data.ownedBy(u);
            if (theirs == null || theirs == s) {
                next = u;
                break;
            }
        }
        s.owner = next;
        s.ownerName = next == null ? "" : nameOf(server, next);
        s.council.remove(next);
        Allegiance.setLeader(server, s.id, next);
        if (old != null) {
            ServerPlayer prev = server.getPlayerList().getPlayer(old);
            if (prev != null) {
                prev.displayClientMessage(SettlementsLang.tr(voluntary ? "rule.left" : "rule.overthrown", cityName(server, s)).withColor(0xE8735E), false);
                sendState(prev, s.id, false);
            }
        }
        Component name = cityName(server, s);
        server.getPlayerList().broadcastSystemMessage(next == null ? SettlementsLang.tr("rule.ungoverned", name).withColor(0xE8A440)
                : SettlementsLang.tr("rule.successor", name, s.ownerName).withColor(0xFFD86B), false);
        if (next != null) {
            ServerPlayer np = server.getPlayerList().getPlayer(next);
            if (np != null) sendState(np, s.id, false);
        }
    }

    /** Cidade própria sem governo: um cidadão (que não governa outra cidade fundada) assume o poder. */
    private static void claim(ServerPlayer sp, @Nullable Settlement s) {
        if (s == null || !s.own() || s.owner != null) return;
        if (!s.id.equals(Allegiance.of(sp))) {
            sp.displayClientMessage(SettlementsLang.tr("rule.claim.citizen").withColor(0xE8735E), true);
            return;
        }
        if (SettlementData.get(sp.server).ownedBy(sp.getUUID()) != null) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.found.already").withColor(0xE8735E), true);
            return;
        }
        s.owner = sp.getUUID();
        s.ownerName = sp.getGameProfile().getName();
        s.council.remove(sp.getUUID());
        Allegiance.setLeader(sp.server, s.id, sp.getUUID());
        Rewards.banner(sp, SettlementsLang.tr("rule.claimed", cityName(sp.server, s)), s.style.color);
    }

    private static String nameOf(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) return p.getGameProfile().getName();
        return server.getProfileCache() == null ? "?" : server.getProfileCache().get(id).map(gp -> gp.getName()).orElse("?");
    }

    private static void joinCity(ServerPlayer sp, String cityId) {
        if (Allegiance.isEnemyOf(sp, cityId)) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.citizen.enemy").withColor(0xE8735E), true);
            return;
        }
        if (cityId.equals(Allegiance.of(sp))) return;
        for (Settlement mine : SettlementData.get(sp.server).ruledBy(sp.getUUID())) {
            if (!mine.id.equals(cityId)) {
                sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.citizen.ruler").withColor(0xE8735E), true);
                return;
            }
        }
        Allegiance.set(sp, cityId);
        sp.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    // =====================================================================================
    //  Sincronização da tela
    // =====================================================================================
    /** Manda o estado para quem está com a tela aberta desta cidade (o dono, se online). */
    public static void refreshViewers(MinecraftServer server, Settlement s) {
        for (ServerPlayer p : CityEconomy.governors(server, s)) sendState(p, s.id, false);
    }

    public static void sendState(ServerPlayer sp, String cityId, boolean open) {
        String id = cityId.isEmpty() ? defaultCity(sp) : cityId;
        CompoundTag tag = id == null ? new CompoundTag() : SettlementView.build(sp, id);
        if (id == null) tag.putString("Mode", "none");
        tag.putBoolean("Open", open);
        PacketDistributor.sendToPlayer(sp, new SettlementsNetwork.State(tag));
    }

    /** Cidade mostrada na aba do menu: a própria, depois uma governada, depois a de lealdade, depois onde está. */
    @Nullable
    private static String defaultCity(ServerPlayer sp) {
        SettlementData data = SettlementData.get(sp.server);
        Settlement own = data.ownedBy(sp.getUUID());
        if (own != null) return own.id;
        List<Settlement> ruled = data.ruledBy(sp.getUUID());
        if (!ruled.isEmpty()) return ruled.get(0).id;
        String loyal = Allegiance.of(sp);
        if (loyal != null) return loyal;
        Places.Place here = cityAt(sp.serverLevel(), sp.blockPosition());
        return here == null ? null : here.id();
    }

    public static MutableComponent coins(int n) {
        return Component.translatable("settlements.isekai_mahou.coins", n);
    }

    public static void clear() {
        TRADE_STATS.clear();
        TRACKER_HASH.clear();
        CityPlots.clear();
        SkyCity.clear();
    }

    private SettlementService() {}
}
