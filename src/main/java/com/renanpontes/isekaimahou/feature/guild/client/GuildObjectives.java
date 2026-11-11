package com.renanpontes.isekaimahou.feature.guild.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.objectives.ObjectiveLine;
import com.renanpontes.isekaimahou.client.objectives.ObjectiveTracker;
import com.renanpontes.isekaimahou.feature.guild.GuildContent;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork.ObjectiveView;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork.QuestView;
import com.renanpontes.isekaimahou.feature.guild.GuildRank;

import com.renanpontes.isekaimahou.feature.guild.entity.AdventurerEntity;

import net.minecraft.client.Minecraft;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Provedor do rastreador de objetivos (canto superior direito, tecla J) com a Guilda:
 * <ol>
 *   <li>a prova de promoção (progresso do objetivo atual; "relate ao Mestre" com seta quando concluída) ou o aviso de que
 *       a prova do próximo rank foi liberada;</li>
 *   <li>missões prontas para entregar (uma linha só quando são várias), com seta para o último quadro/recepção usado;</li>
 *   <li>cada missão em andamento: título na cor do rank, objetivo atual e a barra n/m;</li>
 *   <li>sem nada disso: os pontos da Guilda rumo à próxima prova, com seta para o quadro/recepção.</li>
 * </ol>
 * v0.8: missões de facção (cor da facção, seta para o Quadro daquela facção) e um segundo provedor com os companheiros
 * contratados (vida, tempo de contrato, seta quando longe).
 * Tudo vem do estado já sincronizado ({@link GuildNetwork#clientState}) — nenhum pacote extra.
 */
public final class GuildObjectives {
    public static final String ID = "guild";
    /** Abaixo da Provação/chefe (1000), Ascensão (600) e Diário (500). */
    public static final int PRIORITY = 300;

    private static final int TRIAL_RGB = 0xD9A8FF;
    private static final int READY_RGB = 0x6BD66B;
    private static final String KEY = "guild.isekai_mahou.tracker.";

    public static void register() {
        ObjectiveTracker.register(ID, PRIORITY, GuildObjectives::lines);
        ObjectiveTracker.register(COMPANIONS_ID, COMPANIONS_PRIORITY, GuildObjectives::companions);
    }

    /** Companheiros contratados: logo acima das missões (o grupo importa mais que o papel). */
    public static final String COMPANIONS_ID = "guild_companions";
    public static final int COMPANIONS_PRIORITY = 320;
    private static final int MAX_COMPANION_LINES = 3;

    /**
     * Uma linha por companheiro: "⚔ Kazuo [C]" na cor do rank, "Seguindo · 2d 5h" e a barra de VIDA; seta quando ele
     * está longe (mais de 24 blocos) ou noutro lugar. Vida ao vivo da entidade quando ela está carregada no cliente.
     */
    static List<ObjectiveLine> companions() {
        List<GuildNetwork.CompanionView> list = GuildNetwork.clientCompanions;
        if (list.isEmpty()) return List.of();
        Minecraft mc = Minecraft.getInstance();
        List<ObjectiveLine> out = new ArrayList<>();
        for (GuildNetwork.CompanionView v : list) {
            if (out.size() >= MAX_COMPANION_LINES) break;
            GuildRank rank = v.guildRank();
            Component title = Component.translatable(KEY + "companion", v.name(), rank.letter());
            Entity e = v.loaded() && mc.level != null ? mc.level.getEntity(v.entityId()) : null;
            float hp = e instanceof LivingEntity le ? le.getHealth() : v.health();
            float max = e instanceof LivingEntity le ? le.getMaxHealth() : v.maxHealth();
            Component state = Component.translatable(v.following() ? KEY + "companion_following" : KEY + "companion_waiting");
            Component detail = Component.translatable(KEY + "companion_detail", state, AdventurerDialogScreen.time(v.ticksLeft()));
            ObjectiveLine line = max > 0
                    ? ObjectiveLine.progress(title, detail, Mth.ceil(hp), Mth.ceil(max), healthColor(hp / max), classIcon(v.variant()))
                    : ObjectiveLine.of(title, Component.translatable(KEY + "companion_far"), rank.rgb(), classIcon(v.variant()));
            boolean far = e == null || (mc.player != null && mc.player.distanceToSqr(e) > 24 * 24);
            if (far && v.pos().isPresent()) {
                GlobalPos p = v.pos().get();
                line = e != null ? line.withTarget(e.position(), p.dimension()) : line.withTarget(p.pos(), p.dimension());
            }
            out.add(line);
        }
        return out;
    }

    private static int healthColor(float frac) {
        return frac > 0.6F ? 0x6BD66B : frac > 0.3F ? 0xFFC94F : 0xE8735E;
    }

    private static ItemStack classIcon(int variant) {
        AdventurerEntity.Variant[] all = AdventurerEntity.Variant.values();
        return new ItemStack(switch (all[Mth.clamp(variant, 0, all.length - 1)]) {
            case WARRIOR -> Items.IRON_SWORD;
            case ARCHER -> Items.BOW;
            case MAGE -> Items.BLAZE_ROD;
            case ROGUE -> Items.STONE_SWORD;
            case CLERIC -> Items.GOLDEN_APPLE;
        });
    }

    static List<ObjectiveLine> lines() {
        GuildNetwork.State s = GuildNetwork.clientState;
        // Sem cartão, só as missões de facção (podem ser feitas sem ser da Guilda).
        if (s.rank() <= 0 && s.active().isEmpty()) return List.of();
        List<ObjectiveLine> out = new ArrayList<>();

        // 1) Prova de promoção
        if (s.trial().isPresent()) {
            QuestView t = s.trial().get();
            Component title = Component.translatable(KEY + "trial", t.guildRank().letter());
            if (t.complete()) {
                out.add(point(ObjectiveLine.of(title, Component.translatable(KEY + "trial_report"), READY_RGB, icon(t)),
                        s.master().or(s::hall)));
            } else {
                out.add(progress(t, title, TRIAL_RGB));
            }
        } else if (s.trialReady()) {
            GuildRank next = s.guildRank() == null ? null : s.guildRank().next();
            if (next != null) {
                out.add(point(ObjectiveLine.of(Component.translatable(KEY + "trial_unlocked", next.letter()),
                        Component.translatable(KEY + "trial_unlocked_hint"), next.rgb(), new ItemStack(GuildContent.ADVENTURER_CARD.get())),
                        s.master().or(s::hall)));
            }
        }

        // 2) Missões prontas
        List<QuestView> ready = new ArrayList<>(), going = new ArrayList<>();
        for (QuestView q : s.active()) (q.state() == GuildNetwork.Q_READY || q.complete() ? ready : going).add(q);
        // Missões de facção prontas: uma linha por missão, com seta para o Quadro daquela facção.
        List<QuestView> readyGuild = new ArrayList<>();
        for (QuestView q : ready) {
            if (q.guildFaction().isGuild()) {
                readyGuild.add(q);
                continue;
            }
            out.add(point(ObjectiveLine.of(q.title(), Component.translatable(KEY + "ready_faction", q.guildFaction().displayName()),
                    q.guildFaction().rgb(), icon(q)), s.deskFor(q.faction())));
        }
        if (readyGuild.size() == 1) {
            QuestView q = readyGuild.get(0);
            out.add(point(ObjectiveLine.of(q.title(), Component.translatable(KEY + "ready"), READY_RGB, icon(q)), s.hall()));
        } else if (readyGuild.size() > 1) {
            out.add(point(ObjectiveLine.of(Component.translatable(KEY + "ready_many", readyGuild.size()), Component.translatable(KEY + "ready"),
                    READY_RGB, new ItemStack(GuildContent.GUILD_COIN.get())), s.hall()));
        }

        // 3) Missões em andamento (as de facção na cor da facção)
        for (QuestView q : going) out.add(progress(q, q.title(), q.guildFaction().isGuild() ? q.guildRank().rgb() : q.guildFaction().rgb()));

        // 4) Nada em andamento: a meta é juntar pontos para a próxima prova (seta para o quadro/recepção).
        if (out.isEmpty() && s.rank() > 0) {
            GuildRank rank = s.guildRank();
            GuildRank next = rank == null ? null : rank.next();
            if (next != null) {
                out.add(point(ObjectiveLine.progress(Component.translatable(KEY + "next_rank", next.letter()),
                        Component.translatable(KEY + "next_rank_hint"), Math.min(s.points(), next.pointsRequired), next.pointsRequired,
                        next.rgb(), new ItemStack(GuildContent.QUEST_BOARD_ITEM.get())), s.hall()));
            }
        }
        return out;
    }

    /** Linha com o PRIMEIRO objetivo ainda não cumprido: "[1/2] Derrotar: Goblin" + barra n/m. */
    private static ObjectiveLine progress(QuestView q, Component title, int rgb) {
        List<ObjectiveView> objs = q.objectives();
        int index = 0;
        while (index < objs.size() - 1 && objs.get(index).done()) index++;
        ObjectiveView o = objs.isEmpty() ? null : objs.get(index);
        if (o == null) return ObjectiveLine.of(title, q.description(), rgb, ItemStack.EMPTY);
        Component detail = objs.size() > 1 ? Component.translatable(KEY + "step", index + 1, objs.size(), o.label()) : o.label();
        // Objetivos de "valer 1" (explorar, marcador) ficam sem barra: o "0/1" não diz nada.
        int target = o.target() > 1 ? o.target() : 0;
        ObjectiveLine line = ObjectiveLine.progress(title, detail, Math.min(o.progress(), o.target()), target, rgb, o.icon());
        return q.hunt().map(h -> hunt(line, h, o)).orElse(line);
    }

    /**
     * v0.9: missão com Área de Caça — "⚑ Vá à Área de Caça: Goblin" com seta até lá; na luta, "⚔ Onda 2/3 — Derrotar: Goblin"
     * (seta só se o jogador se afastar do centro).
     */
    private static ObjectiveLine hunt(ObjectiveLine line, GuildNetwork.HuntView h, ObjectiveView o) {
        if (h.pos().isEmpty() || h.phase() == GuildNetwork.HUNT_OFFER) return line;
        GlobalPos p = h.pos().get();
        Component detail;
        boolean arrow;
        if (h.phase() == GuildNetwork.HUNT_FIGHT) {
            detail = h.wave() <= 0 ? Component.translatable(KEY + "hunt_ready", o.label())
                    : h.wave() <= h.waves() ? Component.translatable(KEY + "hunt_wave", h.wave(), h.waves(), o.label())
                    : Component.translatable(KEY + "hunt_reinforcements", o.label());
            Minecraft mc = Minecraft.getInstance();
            arrow = mc.player == null || mc.player.level().dimension() != p.dimension()
                    || mc.player.distanceToSqr(p.pos().getX() + 0.5, mc.player.getY(), p.pos().getZ() + 0.5) > 32 * 32;
        } else {
            detail = Component.translatable(KEY + "hunt_go", h.name());
            arrow = true;
        }
        ObjectiveLine out = new ObjectiveLine(line.title(), detail, line.current(), line.target(), line.rgb(), line.icon(), null, null);
        return arrow ? out.withTarget(p.pos(), p.dimension()) : out;
    }

    /** Seta para o lugar guardado (quadro/recepção/Mestre), quando o jogador já esteve lá. */
    private static ObjectiveLine point(ObjectiveLine line, Optional<GlobalPos> where) {
        return where.map(p -> line.withTarget(p.pos(), p.dimension())).orElse(line);
    }

    private static ItemStack icon(@Nullable QuestView q) {
        if (q == null || q.objectives().isEmpty()) return ItemStack.EMPTY;
        return q.objectives().get(0).icon();
    }

    private GuildObjectives() {}
}
