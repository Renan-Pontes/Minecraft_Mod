package com.renanpontes.isekaimahou.feature.legends_f;

import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Desbloqueio das técnicas de anime das 5 classes mágicas: cada uma chega sozinha a quem é DA CLASSE ao alcançar o
 * Círculo e o rank do ritual da classe (contador {@code ritual_rank}). Confere em toda subida de nível/Círculo/rank,
 * na escolha da classe e no login (quem já passou do ponto ganha na hora). Outras classes só aprendem por Lição.
 */
public final class LfUnlocks {
    /** Técnica, classe, Círculo mínimo e rank mínimo do ritual. */
    public record Unlock(String glyph, MagicPath path, int circle, int rank) {}

    public static final List<Unlock> ALL = List.of(
            // Mago (Frieren)
            new Unlock("mage_mana_sense", MagicPath.MAGE, 2, 0),
            new Unlock("mage_conceal_mana", MagicPath.MAGE, 3, 1),
            new Unlock("mage_general_barrier", MagicPath.MAGE, 4, 2),
            // Bruxa (Elaina, Bela Adormecida, Inveja)
            new Unlock("witch_broom", MagicPath.WITCH, 2, 0),
            new Unlock("witch_sleep_curse", MagicPath.WITCH, 3, 1),
            new Unlock("witch_envy_hands", MagicPath.WITCH, 5, 3),
            // Bruxo (pactos)
            new Unlock("warlock_faust", MagicPath.WARLOCK, 4, 1),
            new Unlock("warlock_chainsaw", MagicPath.WARLOCK, 5, 2),
            new Unlock("warlock_bang", MagicPath.WARLOCK, 6, 3),
            // Druida (Mononoke, Senjutsu)
            new Unlock("druid_kodama", MagicPath.DRUID, 2, 0),
            new Unlock("druid_sage_mode", MagicPath.DRUID, 5, 2),
            new Unlock("druid_forest_god", MagicPath.DRUID, 7, 4),
            // Necromante (Solo Leveling)
            new Unlock("necro_arise", MagicPath.NECROMANCER, 4, 2),
            new Unlock("necro_shadow_command", MagicPath.NECROMANCER, 4, 2),
            new Unlock("necro_shadow_exchange", MagicPath.NECROMANCER, 5, 3),
            new Unlock("necro_monarch", MagicPath.NECROMANCER, 8, 4));

    /** Ensina o que o jogador já merece. */
    public static void check(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        MagicPath path = d.path();
        int rank = LfUtil.rank(sp);
        for (Unlock u : ALL) {
            if (u.path() != path || d.circle() < u.circle() || rank < u.rank()) continue;
            ResourceLocation id = IsekaiMahou.id(u.glyph());
            if (d.knowsGlyph(id)) continue;
            Glyph g = GlyphRegistry.get(id);
            if (g == null || !MagicApi.learnGlyph(sp, id)) continue;
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.legends_f.unlock", g.displayName().withStyle(ChatFormatting.GOLD))
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
            sp.sendSystemMessage(Component.literal("  ").append(g.description().withStyle(ChatFormatting.GRAY)));
        }
    }

    static void onLevel(MagicProgressEvent.LevelUp e) {
        check(e.getPlayer());
    }

    static void onCircle(MagicProgressEvent.CircleChanged e) {
        check(e.getPlayer());
    }

    static void onCounter(MagicProgressEvent.Counter e) {
        if ("ritual_rank".equals(e.key)) check(e.getPlayer());
    }

    static void onPath(MagicProgressEvent.PathChosen e) {
        check(e.getPlayer());
    }

    static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) check(sp);
    }

    private LfUnlocks() {}
}
