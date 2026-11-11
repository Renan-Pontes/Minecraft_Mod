package com.renanpontes.isekaimahou.core.social;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.social.SocialData.PartyRec;
import com.renanpontes.isekaimahou.core.social.SocialData.WarRec;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Lealdade (cidade de cada jogador), líderes das cidades e guerras (servidor). API pública:
 * {@link com.renanpontes.isekaimahou.core.api.Allegiance}.
 *
 * <p>"Lado" de um jogador numa guerra = a cidade dele + as cidades cujo líder está no grupo dele (a party do líder luta
 * pela cidade do líder). Dois jogadores estão em guerra se algum lado de um é inimigo de algum lado do outro.
 */
public final class AllegianceService {
    public static final int WAR_COLOR = 0xFF5A4A;
    /** Tamanho máximo de um id de cidade. */
    public static final int MAX_ID = 128;

    // =====================================================================================
    //  Lealdade
    // =====================================================================================
    @Nullable
    public static String of(MinecraftServer server, UUID player) {
        return SocialData.get(server).allegiance.get(player);
    }

    public static void set(ServerPlayer player, @Nullable String city) {
        SocialData d = SocialData.get(player.server);
        String clean = clean(city);
        String old = d.allegiance.get(player.getUUID());
        if (Objects.equals(old, clean)) return;
        if (clean == null) d.allegiance.remove(player.getUUID());
        else d.allegiance.put(player.getUUID(), clean);
        d.setDirty();
        if (clean != null) {
            player.sendSystemMessage(Component.translatable("social.isekai_mahou.allegiance.joined", cityName(player.server, clean))
                    .withColor(0x9FD8FF));
            player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.7F, 1.0F);
        } else if (old != null) {
            player.sendSystemMessage(Component.translatable("social.isekai_mahou.allegiance.left", cityName(player.server, old))
                    .withStyle(ChatFormatting.GRAY));
        }
        NeoForge.EVENT_BUS.post(new SocialEvent.AllegianceChanged(player, old, clean));
        SocialSync.markAllStates();
    }

    /** Jogadores (UUID) que juraram lealdade à cidade. */
    public static List<UUID> citizens(MinecraftServer server, String city) {
        List<UUID> out = new ArrayList<>();
        for (Map.Entry<UUID, String> e : SocialData.get(server).allegiance.entrySet()) {
            if (e.getValue().equals(city)) out.add(e.getKey());
        }
        return out;
    }

    // =====================================================================================
    //  Líderes
    // =====================================================================================
    public static void setLeader(MinecraftServer server, String city, @Nullable UUID leader) {
        String clean = clean(city);
        if (clean == null) return;
        SocialData d = SocialData.get(server);
        if (Objects.equals(d.leaders.get(clean), leader)) return;
        if (leader == null) d.leaders.remove(clean);
        else d.leaders.put(clean, leader);
        d.setDirty();
        SocialSync.markAllStates();
    }

    @Nullable
    public static UUID leader(MinecraftServer server, String city) {
        return SocialData.get(server).leaders.get(city);
    }

    // =====================================================================================
    //  Guerras
    // =====================================================================================
    public static boolean declare(MinecraftServer server, String city, String target, @Nullable ServerPlayer by) {
        String a = clean(city), b = clean(target);
        if (a == null || b == null || a.equals(b)) return false;
        SocialData d = SocialData.get(server);
        String key = SocialData.warKey(a, b);
        if (d.wars.containsKey(key)) return false;
        String sa = a.compareTo(b) <= 0 ? a : b, sb = sa.equals(a) ? b : a;
        d.wars.put(key, new WarRec(sa, sb, by != null ? by.getUUID() : null, by != null ? by.getGameProfile().getName() : "",
                server.overworld().getGameTime()));
        d.setDirty();
        Component msg = Component.translatable("social.isekai_mahou.war.declared",
                cityName(server, a).copy().withStyle(ChatFormatting.BOLD), cityName(server, b).copy().withStyle(ChatFormatting.BOLD))
                .withColor(WAR_COLOR);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Set<String> sides = sides(server, p.getUUID());
            if (sides.contains(a) || sides.contains(b)) {
                p.sendSystemMessage(msg);
                p.sendSystemMessage(Component.translatable("social.isekai_mahou.war.pvp_hint").withStyle(ChatFormatting.GRAY));
                p.playNotifySound(SoundEvents.RAID_HORN.value(), SoundSource.HOSTILE, 0.6F, 1.0F);
            }
        }
        NeoForge.EVENT_BUS.post(new SocialEvent.WarDeclared(a, b, by));
        SocialSync.markAllStates();
        return true;
    }

    public static boolean peace(MinecraftServer server, String a, String b) {
        String ca = clean(a), cb = clean(b);
        if (ca == null || cb == null) return false;
        SocialData d = SocialData.get(server);
        if (d.wars.remove(SocialData.warKey(ca, cb)) == null) return false;
        d.setDirty();
        Component msg = Component.translatable("social.isekai_mahou.war.peace", cityName(server, ca), cityName(server, cb))
                .withColor(0x9FE8A0);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Set<String> sides = sides(server, p.getUUID());
            if (sides.contains(ca) || sides.contains(cb)) {
                p.sendSystemMessage(msg);
                p.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.PLAYERS, 0.7F, 0.8F);
            }
        }
        NeoForge.EVENT_BUS.post(new SocialEvent.PeaceMade(ca, cb));
        SocialSync.markAllStates();
        return true;
    }

    public static boolean areEnemies(MinecraftServer server, String a, String b) {
        if (a == null || b == null || a.equals(b)) return false;
        return SocialData.get(server).wars.containsKey(SocialData.warKey(a, b));
    }

    public static Set<String> enemiesOf(MinecraftServer server, String city) {
        return SocialData.get(server).enemiesOf(city);
    }

    public static List<WarRec> wars(MinecraftServer server) {
        return new ArrayList<>(SocialData.get(server).wars.values());
    }

    /** Cidades pelas quais o jogador luta (a dele + as dos líderes do grupo dele). */
    public static Set<String> sides(MinecraftServer server, UUID player) {
        SocialData d = SocialData.get(server);
        Set<String> out = new LinkedHashSet<>();
        String own = d.allegiance.get(player);
        if (own != null) out.add(own);
        if (d.leaders.isEmpty()) return out;
        PartyRec party = d.partyOf(player);
        for (Map.Entry<String, UUID> e : d.leaders.entrySet()) {
            UUID leader = e.getValue();
            if (leader.equals(player) || (party != null && party.members.contains(leader))) out.add(e.getKey());
        }
        return out;
    }

    public static boolean atWar(MinecraftServer server, UUID a, UUID b) {
        if (a.equals(b)) return false;
        SocialData d = SocialData.get(server);
        if (d.wars.isEmpty()) return false;
        Set<String> sa = sides(server, a);
        if (sa.isEmpty()) return false;
        Set<String> sb = sides(server, b);
        for (String x : sa) for (String y : sb) if (!x.equals(y) && d.wars.containsKey(SocialData.warKey(x, y))) return true;
        return false;
    }

    public static boolean isEnemyOf(MinecraftServer server, UUID player, String city) {
        SocialData d = SocialData.get(server);
        if (d.wars.isEmpty() || city == null) return false;
        for (String s : sides(server, player)) if (!s.equals(city) && d.wars.containsKey(SocialData.warKey(s, city))) return true;
        return false;
    }

    // =====================================================================================
    //  Nomes
    // =====================================================================================
    /** Nome do lugar ({@code Places}) em qualquer dimensão; sem lugar, o próprio id. */
    public static Component cityName(MinecraftServer server, String city) {
        // Places.get já procura no nível dado e depois em todas as dimensões
        ServerLevel overworld = server.overworld();
        Places.Place p = overworld == null || city == null ? null : Places.get(overworld, city);
        if (p != null) return p.name().copy().withColor(p.color());
        return Component.literal(city == null ? "?" : city);
    }

    /** "Valdória ⚔ Kharum" (para listas). */
    public static MutableComponent warLine(MinecraftServer server, WarRec w) {
        return Component.empty().append(cityName(server, w.a())).append(Component.literal(" ⚔ ").withColor(WAR_COLOR))
                .append(cityName(server, w.b()));
    }

    @Nullable
    private static String clean(@Nullable String city) {
        if (city == null) return null;
        String s = city.strip();
        if (s.isEmpty() || s.length() > MAX_ID) return null;
        return s;
    }

    private AllegianceService() {}
}
