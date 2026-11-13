package com.renanpontes.isekaimahou.feature.lore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.feature.lore.LoreFragments.Fragment;
import com.renanpontes.isekaimahou.feature.lore.LoreFragments.Kind;
import com.renanpontes.isekaimahou.feature.lore.LoreFragments.LoreSet;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Servidor do módulo lore: ler Páginas Perdidas e Estelas, guardar os fragmentos do jogador, <b>revelar o lugar</b> de
 * que cada fragmento fala (estrutura mais próxima ou tesouro enterrado) e avisar quando o jogador chega lá.
 * <p>
 * Progresso para os outros módulos (Diário, Renome): contadores {@code lore.fragments}, {@code lore.sets},
 * {@code lore.steles}, {@code lore.places}, {@code lore.treasure}, {@code lore.complete}; flags
 * {@code lore.frag.<id>} e {@code lore.set.<coleção>}; evento {@link LoreEvent.FragmentFound}.
 * <p>
 * Balanço: lore não é atalho de poder — 40 de XP por fragmento, 250 por coleção, tesouro modesto.
 */
public final class LoreService {
    static final int XP_FRAGMENT = 40, XP_SET = 250;
    /** Distâncias (blocos, na horizontal): chegou ao lugar / acerta a altura / desenterra o tesouro. */
    private static final int ARRIVE = 24, REFINE = 160, DIG = 10;
    /** Recarga do "procurar de novo" por jogador (ticks). */
    private static final int SEARCH_COOLDOWN = 60;
    private static final int CHECK_EVERY = 20;

    private static final Map<UUID, Long> LAST_SEARCH = new HashMap<>();

    private LoreService() {}

    static CompoundTag data(ServerPlayer sp) {
        return sp.getData(LoreContent.PLAYER_DATA.get());
    }

    private static void store(ServerPlayer sp, CompoundTag d) {
        sp.setData(LoreContent.PLAYER_DATA.get(), d);
    }

    /** Manda os dados de lore do jogador para o cliente dele. */
    public static void sync(ServerPlayer sp) {
        PacketDistributor.sendToPlayer(sp, new LoreNetwork.Sync(data(sp).copy()));
    }

    /** Dimensões onde o módulo nunca mexe em blocos (a Dimensão Casa é do jogador). */
    static boolean blocksForbidden(ServerLevel level) {
        return level.dimension().location().equals(IsekaiMahou.id("home_realm"));
    }

    static void onLogout(UUID id) {
        LAST_SEARCH.remove(id);
    }

    static void clearAll() {
        LAST_SEARCH.clear();
    }

    // ================================================================================================
    //  Leitura
    // ================================================================================================
    /** O jogador usou uma Página Perdida. */
    public static void readPage(ServerPlayer sp, ItemStack stack) {
        if (sp.getCooldowns().isOnCooldown(stack.getItem())) return;
        sp.getCooldowns().addCooldown(stack.getItem(), 20);
        CompoundTag d = data(sp);
        Fragment f = LoreFragments.get(stack.get(LoreContent.FRAGMENT.get()));
        if (f != null && LoreData.has(d, f.id())) {
            // Página de um fragmento que ele já tem: relê (sem gastar — serve para dar a um amigo)
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.lore.page_known"), true);
            PacketDistributor.sendToPlayer(sp, new LoreNetwork.Read(f.id(), false, false));
            return;
        }
        if (f == null) f = next(sp, d);
        if (f == null) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.lore.all_known"), true);
            return;
        }
        stack.consume(1, sp);
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1F, 0.9F);
        discover(sp, f);
    }

    /** O jogador clicou numa Estela gravada. Uma vez por jogador, por estela. */
    public static void readStele(ServerPlayer sp, BlockPos pos) {
        CompoundTag d = data(sp);
        if (LoreData.steleRead(d, pos)) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.lore.stele_read"), true);
            return;
        }
        LoreData.addStele(d, pos);
        store(sp, d);
        MagicApi.addCounter(sp, "lore.steles", 1);
        sp.serverLevel().playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.8F, 0.8F);
        sp.serverLevel().sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 24, 0.4, 0.5, 0.4, 0.6);
        Fragment f = next(sp, d);
        if (f == null) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.lore.stele_nothing_new"), true);
            sync(sp);
            return;
        }
        discover(sp, f);
    }

    /**
     * Próximo fragmento para este jogador: sorteia uma coleção ainda incompleta (pesando pelo que falta, e mais as que
     * combinam com a dimensão onde ele está) e entrega a página seguinte dela — assim cada coleção é lida em ordem.
     */
    @Nullable
    static Fragment next(ServerPlayer sp, CompoundTag d) {
        List<Fragment> candidates = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        int total = 0;
        for (LoreSet set : LoreFragments.sets()) {
            Fragment first = null;
            int missing = 0;
            for (Fragment f : set.fragments()) {
                if (LoreData.has(d, f.id())) continue;
                if (first == null) first = f;
                missing++;
            }
            if (first == null) continue;
            int w = missing * (favored(sp, set) ? 4 : 1);
            candidates.add(first);
            weights.add(w);
            total += w;
        }
        if (candidates.isEmpty()) return null;
        int roll = sp.getRandom().nextInt(total);
        for (int i = 0; i < candidates.size(); i++) {
            roll -= weights.get(i);
            if (roll < 0) return candidates.get(i);
        }
        return candidates.get(0);
    }

    /** No Nether saem mais Confissões do Rei Demônio; no End, mais Cantos do Vazio. */
    private static boolean favored(ServerPlayer sp, LoreSet set) {
        if (sp.level().dimension() == Level.NETHER) return set.id().equals("demon");
        if (sp.level().dimension() == Level.END) return set.id().equals("void");
        return false;
    }

    /**
     * Entrega o fragmento ao jogador: guarda, revela o lugar, dá o XP pequeno, confere a coleção, sincroniza e abre a
     * página na tela dele.
     *
     * @return false se ele já tinha
     */
    public static boolean discover(ServerPlayer sp, Fragment f) {
        return discover(sp, f, true);
    }

    /**
     * Como {@link #discover(ServerPlayer, Fragment)}; com {@code locate} falso o lugar NÃO é procurado agora (a busca no
     * mapa custa — o comando de teste que libera tudo de uma vez usa isto; o jogador procura depois pela Enciclopédia).
     */
    static boolean discover(ServerPlayer sp, Fragment f, boolean locate) {
        CompoundTag d = data(sp);
        if (LoreData.has(d, f.id())) return false;
        LoreData.add(d, f.id());
        if (locate) reveal(sp, d, f);
        LoreData.Spot spot = LoreData.spot(d, f.id());
        // Nada sendo seguido: o lugar novo já vai para o rastreador (o jogador troca/para na Enciclopédia)
        if (spot != null && LoreData.tracked(d).isEmpty()) LoreData.setTracked(d, f.id());
        LoreSet set = LoreFragments.set(f.set());
        boolean complete = set != null && LoreData.setComplete(d, set);
        store(sp, d);

        MagicData md = MagicApi.get(sp);
        md.setFlag("lore.frag." + f.id(), true);
        MagicApi.addCounter(sp, "lore.fragments", 1);
        MagicApi.giveXp(sp, XP_FRAGMENT, "lore");
        if (complete) {
            md.setFlag("lore.set." + set.id(), true);
            MagicApi.addCounter(sp, "lore.sets", 1);
            MagicApi.giveXp(sp, XP_SET, "lore");
            if (LoreData.count(d) >= LoreFragments.total()) MagicApi.addCounter(sp, "lore.complete", 1);
        }

        sync(sp);
        if (locate) {
            PacketDistributor.sendToPlayer(sp, new LoreNetwork.Read(f.id(), true, complete));
            sp.sendSystemMessage(foundMessage(f, spot));
        }
        if (complete) {
            LoreBook.Entry truth = LoreBook.entry(set.truth());
            sp.sendSystemMessage((truth != null
                    ? Component.translatable("message.isekai_mahou.lore.set_complete_truth", set.title(), truth.title())
                    : Component.translatable("message.isekai_mahou.lore.set_complete", set.title())).withStyle(ChatFormatting.GOLD));
        }
        Hints.show(sp, "lore_encyclopedia");
        NeoForge.EVENT_BUS.post(new LoreEvent.FragmentFound(sp, f, complete));
        return true;
    }

    /** Uma linha no chat com o fragmento e as coordenadas do lugar revelado (fica no histórico para consulta). */
    private static MutableComponent foundMessage(Fragment f, @Nullable LoreData.Spot spot) {
        if (spot == null) {
            return Component.translatable("message.isekai_mahou.lore.found", f.title()).withStyle(ChatFormatting.GRAY);
        }
        MutableComponent coords = Component.literal(spot.pos().getX() + ", " + spot.pos().getZ()).withStyle(ChatFormatting.AQUA);
        MutableComponent place = f.placeName().withStyle(ChatFormatting.YELLOW);
        if (spot.dim() != Level.OVERWORLD) {
            return Component.translatable("message.isekai_mahou.lore.found_place_dim", f.title(), place, coords, dimName(spot.dim().location().getPath()))
                    .withStyle(ChatFormatting.GRAY);
        }
        return Component.translatable("message.isekai_mahou.lore.found_place", f.title(), place, coords).withStyle(ChatFormatting.GRAY);
    }

    public static MutableComponent dimName(String path) {
        return Component.translatableWithFallback("lore." + IsekaiMahou.MODID + ".dim." + path, path.replace('_', ' '));
    }

    // ================================================================================================
    //  Revelar o lugar
    // ================================================================================================
    /** Procura o lugar do fragmento e grava em {@code d} (nada muda se não achar). */
    private static void reveal(ServerPlayer sp, CompoundTag d, Fragment f) {
        MinecraftServer server = sp.getServer();
        ServerLevel target = server == null ? null : server.getLevel(f.dim());
        if (target == null) return;
        BlockPos from = originIn(sp, target);
        if (f.kind() == Kind.TREASURE) {
            BlockPos spot = LoreLocator.treasureSpot(target, from, sp.getRandom(), 160, 380);
            if (spot != null) LoreData.setSpot(d, f.id(), LoreData.REVEALED, f.dim(), spot, false);
            return;
        }
        LoreLocator.Found found = LoreLocator.structure(target, f.structures(), from);
        if (found != null) LoreData.setSpot(d, f.id(), LoreData.REVEALED, f.dim(), found.pos(), found.exactY());
    }

    /** De onde a busca parte na dimensão do lugar (o jogador pode estar em outra). */
    private static BlockPos originIn(ServerPlayer sp, ServerLevel target) {
        BlockPos here = sp.blockPosition();
        if (sp.level() == target) return here;
        boolean fromNether = sp.level().dimension() == Level.NETHER, toNether = target.dimension() == Level.NETHER;
        if (toNether && sp.level().dimension() == Level.OVERWORLD) return new BlockPos(here.getX() / 8, 64, here.getZ() / 8);
        if (fromNether && target.dimension() == Level.OVERWORLD) return new BlockPos(here.getX() * 8, 64, here.getZ() * 8);
        if (target.dimension() == Level.OVERWORLD) return target.getSharedSpawnPos();
        return new BlockPos(here.getX(), 64, here.getZ());
    }

    // ================================================================================================
    //  Ações do cliente (Enciclopédia)
    // ================================================================================================
    static void action(ServerPlayer sp, int op, String id) {
        CompoundTag d = data(sp);
        Fragment f = LoreFragments.get(id);
        switch (op) {
            case LoreNetwork.UNTRACK -> LoreData.setTracked(d, "");
            case LoreNetwork.TRACK -> {
                LoreData.Spot spot = f == null ? null : LoreData.spot(d, f.id());
                if (spot == null) return;
                LoreData.setTracked(d, f.id());
            }
            case LoreNetwork.RELOCATE -> {
                if (f == null || !LoreData.has(d, f.id())) return;
                LoreData.Spot spot = LoreData.spot(d, f.id());
                // Tesouro já marcado não muda de lugar (senão daria para "rolar" até cair ao lado)
                if (spot != null && (f.kind() == Kind.TREASURE || spot.state() == LoreData.VISITED)) return;
                long now = sp.serverLevel().getGameTime();
                Long last = LAST_SEARCH.get(sp.getUUID());
                if (last != null && now >= last && now - last < SEARCH_COOLDOWN) return;
                LAST_SEARCH.put(sp.getUUID(), now);
                LoreData.clearSpot(d, f.id());
                reveal(sp, d, f);
                LoreData.Spot found = LoreData.spot(d, f.id());
                if (found != null) LoreData.setTracked(d, f.id());
                else if (f.id().equals(LoreData.tracked(d))) LoreData.setTracked(d, "");
                sp.displayClientMessage(found != null
                        ? Component.translatable("message.isekai_mahou.lore.relocated", f.placeName(), found.pos().getX(), found.pos().getZ())
                        : Component.translatable("message.isekai_mahou.lore.relocate_none", f.placeName()), true);
            }
            default -> {
                return;
            }
        }
        store(sp, d);
        sync(sp);
    }

    // ================================================================================================
    //  Chegada aos lugares e tesouros (1x por segundo; só contas de distância)
    // ================================================================================================
    static void tick(MinecraftServer server) {
        if (server.getTickCount() % CHECK_EVERY != 3) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (sp instanceof FakePlayer || sp.isSpectator() || !sp.isAlive()) continue;
            try {
                check(sp);
            } catch (RuntimeException e) {
                IsekaiMahou.LOGGER.debug("Lore: falha ao conferir lugares de {}", sp.getGameProfile().getName(), e);
            }
        }
    }

    private static void check(ServerPlayer sp) {
        CompoundTag d = data(sp);
        CompoundTag frags = d.getCompound("frags");
        if (frags.isEmpty()) return;
        boolean changed = false;
        for (String id : List.copyOf(frags.getAllKeys())) {
            LoreData.Spot spot = LoreData.spot(d, id);
            if (spot == null || spot.state() != LoreData.REVEALED || spot.dim() != sp.level().dimension()) continue;
            Fragment f = LoreFragments.get(id);
            if (f == null) continue;
            double dist = Math.hypot(spot.pos().getX() + 0.5 - sp.getX(), spot.pos().getZ() + 0.5 - sp.getZ());
            if (f.kind() == Kind.TREASURE) {
                if (dist <= DIG) changed |= dig(sp, d, f, spot);
            } else if (dist <= ARRIVE) {
                LoreData.setState(d, id, LoreData.VISITED);
                if (id.equals(LoreData.tracked(d))) LoreData.setTracked(d, "");
                MagicApi.addCounter(sp, "lore.places", 1);
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.lore.arrived", f.placeName())
                        .withStyle(ChatFormatting.GOLD), true);
                changed = true;
            } else if (!spot.exactY() && dist <= REFINE) {
                LoreLocator.Found fine = LoreLocator.refine(sp.serverLevel(), spot.pos());
                if (fine.exactY()) {
                    LoreData.setSpot(d, id, LoreData.REVEALED, spot.dim(), fine.pos(), true);
                    changed = true;
                }
            }
        }
        if (changed) {
            store(sp, d);
            sync(sp);
        }
    }

    /** O jogador chegou ao X do mapa: enterra o barril (ou empurra o X para perto se ali não dá). */
    private static boolean dig(ServerPlayer sp, CompoundTag d, Fragment f, LoreData.Spot spot) {
        ServerLevel level = sp.serverLevel();
        RandomSource random = sp.getRandom();
        BlockPos chest = LoreLocator.bury(level, spot.pos().getX(), spot.pos().getZ(), random);
        if (chest == null) {
            // Chão de alguém, cidade, água funda sem fundo natural...: o tesouro "estava" um pouco adiante
            BlockPos again = LoreLocator.treasureSpot(level, sp.blockPosition(), random, 32, 72);
            if (again == null) return false;
            LoreData.setSpot(d, f.id(), LoreData.REVEALED, spot.dim(), again, false);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.lore.treasure_moved"), true);
            return true;
        }
        LoreData.setSpot(d, f.id(), LoreData.VISITED, spot.dim(), chest, true);
        if (f.id().equals(LoreData.tracked(d))) LoreData.setTracked(d, "");
        MagicApi.addCounter(sp, "lore.treasure", 1);
        level.playSound(null, chest, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.2F, 0.7F);
        level.sendParticles(ParticleTypes.WAX_ON, chest.getX() + 0.5, chest.getY() + 2.2, chest.getZ() + 0.5, 14, 0.4, 0.3, 0.4, 0.02);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.lore.treasure_here",
                Component.literal(chest.getX() + ", " + chest.getY() + ", " + chest.getZ()).withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GOLD));
        return true;
    }

    // ================================================================================================
    //  Teste (comandos)
    // ================================================================================================
    static void reset(ServerPlayer sp) {
        CompoundTag d = data(sp);
        MagicData md = MagicApi.get(sp);
        for (Fragment f : LoreFragments.all()) md.setFlag("lore.frag." + f.id(), false);
        for (LoreSet s : LoreFragments.sets()) md.setFlag("lore.set." + s.id(), false);
        for (String key : List.of("lore.fragments", "lore.sets", "lore.steles", "lore.places", "lore.treasure", "lore.complete")) {
            int v = md.counter(key);
            if (v != 0) MagicApi.addCounter(sp, key, -v);
        }
        for (String k : List.copyOf(d.getAllKeys())) d.remove(k);
        store(sp, d);
        sync(sp);
    }
}
