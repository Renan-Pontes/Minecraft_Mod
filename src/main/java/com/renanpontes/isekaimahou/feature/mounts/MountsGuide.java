package com.renanpontes.isekaimahou.feature.mounts;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.event.ReputationChangedEvent;
import com.renanpontes.isekaimahou.feature.mounts.entity.GroundMount;
import com.renanpontes.isekaimahou.feature.mounts.entity.RideMount;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * v1.0 — "Tem que deixar mais claro a montaria! liberar outras montarias e arrumar isso."
 *
 * <ul>
 *   <li><b>Clareza</b>: dicas de primeira vez (uma linha, pelo {@link Hints} do HUD) — na cidade ("a Guilda vende
 *       Rédeas", só para quem ainda não montou), perto de uma montaria selvagem (diz a comida da doma) e ao ganhar Rédeas. O passo a passo completo
 *       fica no capítulo "Montarias e Veículos" do Diário ({@code data/isekai_mahou/journal/mounts.json}).</li>
 *   <li><b>Mais montarias, com fonte clara</b>: Dragão Terrestre na loja da Guilda (dados em {@code guild_shop/mounts.json})
 *       ou domado; <b>Pesadelo</b> cai de chefes (chance por chefe, garantido no 6º); <b>Cervo da Aurora</b> é presente
 *       do Reino ao chegar em Honrado.</li>
 *   <li>Comandos de teste {@code /mahou mounts ...}.</li>
 * </ul>
 * Marcadores do jogador: {@code mounts.got.nightmare}, {@code mounts.got.aurora_stag}; contador {@code mounts.boss_rolls}.
 */
public final class MountsGuide {
    /** Facção cuja reputação dá o Cervo da Aurora. */
    private static final String KINGDOM = "kingdom";
    private static final String FLAG_NIGHTMARE = "mounts.got.nightmare";
    private static final String FLAG_STAG = "mounts.got.aurora_stag";
    private static final String BOSS_ROLLS = "mounts.boss_rolls";
    /** 1 chance em N por chefe derrotado; o 6º chefe sem sorte garante. */
    private static final int NIGHTMARE_ONE_IN = 6;
    private static final int NIGHTMARE_PITY = 6;
    /** Quem já tem o Pesadelo ainda pode ganhar Rédeas de novo (perdeu as dele?), mas é raro. */
    private static final int NIGHTMARE_REPEAT_ONE_IN = 25;
    private static final int CHECK_EVERY = 60;
    private static final String RIDE_COUNTER = "feat.mount_ride";
    /** Tempo de jogo (ticks) antes da dica da cidade: o começo é do tutorial do Espírito Guia. */
    private static final int QUIET_START_TICKS = 20 * 60 * 6;

    private MountsGuide() {}

    static void init() {
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(PlayerTickEvent.Post.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp && (sp.tickCount + sp.getId()) % CHECK_EVERY == 0) periodic(sp);
        });
        bus.addListener(MagicProgressEvent.Counter.class, e -> {
            if (e.key.startsWith("boss.")) bossDefeated(e.getPlayer());
        });
        bus.addListener(ReputationChangedEvent.class, e -> {
            if (e.getFaction().startsWith(KINGDOM)) kingdomGift(e.getPlayer());
        });
        // Montar em QUALQUER animal de sela (cavalo, camelo, lobo atroz...) conta como "primeira montaria" no Diário
        bus.addListener(EntityMountEvent.class, e -> {
            if (e.isMounting() && e.getEntityMounting() instanceof ServerPlayer sp && e.getEntityBeingMounted() instanceof LivingEntity
                    && MagicApi.get(sp).counter(RIDE_COUNTER) == 0) {
                MagicApi.addCounter(sp, RIDE_COUNTER, 1);
            }
        });
        MahouCommands.extend((root, ctx) -> root.then(commands()));
    }

    // =====================================================================================
    //  Dicas
    // =====================================================================================

    /**
     * A cada 3 s por jogador: dica da cidade (onde conseguir montaria), montaria selvagem por perto (dica única) e o
     * presente do Reino para quem já era Honrado. Depois que as dicas foram vistas, sobra só uma consulta num mapa.
     */
    private static void periodic(ServerPlayer sp) {
        if (sp.isSpectator()) return;
        if (!Hints.seen(sp, "mounts.city") && sp.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) > QUIET_START_TICKS
                && MagicApi.get(sp).counter(RIDE_COUNTER) == 0) {
            Places.Place place = Places.at(sp.serverLevel(), sp.blockPosition());
            if (place != null && ("city".equals(place.type()) || "capital".equals(place.type()))) {
                Hints.show(sp, "mounts.city", Component.keybind("key.isekai_mahou.menu"));
            }
        }
        if (!Hints.seen(sp, "mounts.wild")) {
            List<Mob> wild = sp.level().getEntitiesOfClass(Mob.class, sp.getBoundingBox().inflate(10.0, 6.0, 10.0),
                    m -> m instanceof RideMount r && r.isWildMount() && !m.isBaby());
            if (!wild.isEmpty()) {
                Mob m = wild.get(0);
                Hints.show(sp, "mounts.wild", m.getType().getDescription(), ((RideMount) m).tameHint());
            }
        }
        // Mundos antigos: quem já era Honrado antes da 1.0 também recebe (confere de vez em quando, não todo tick)
        if (sp.tickCount % (CHECK_EVERY * 10) < CHECK_EVERY) kingdomGift(sp);
    }

    // =====================================================================================
    //  Fontes das montarias novas
    // =====================================================================================

    /** Reputação: Honrado com o Reino (qualquer um dos reinos) → Rédeas do Cervo da Aurora (uma vez). */
    private static void kingdomGift(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.hasFlag(FLAG_STAG) || !honoredByAKingdom(sp)) return;
        d.setFlag(FLAG_STAG, true);
        d.markDirty();
        give(sp, MountsRegistries.AURORA_STAG_REINS.get(), "message.isekai_mahou.mount_gift_stag");
    }

    private static boolean honoredByAKingdom(ServerPlayer sp) {
        if (Reputation.standing(sp, KINGDOM).atLeast(Reputation.Standing.HONORED)) return true;
        for (Reputation.Faction f : Reputation.factions()) {
            if (f.id().startsWith(KINGDOM) && Reputation.standing(sp, f.id()).atLeast(Reputation.Standing.HONORED)) return true;
        }
        return false;
    }

    /** Chefes: cada chefe derrotado rola as Rédeas do Pesadelo (com garantia para não depender só de sorte). */
    private static void bossDefeated(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.hasFlag(FLAG_NIGHTMARE)) {
            if (sp.getRandom().nextInt(NIGHTMARE_REPEAT_ONE_IN) == 0) {
                give(sp, MountsRegistries.NIGHTMARE_REINS.get(), "message.isekai_mahou.mount_drop_nightmare");
            }
            return;
        }
        MagicApi.addCounter(sp, BOSS_ROLLS, 1);
        if (d.counter(BOSS_ROLLS) >= NIGHTMARE_PITY || sp.getRandom().nextInt(NIGHTMARE_ONE_IN) == 0) {
            d.setFlag(FLAG_NIGHTMARE, true);
            d.markDirty();
            give(sp, MountsRegistries.NIGHTMARE_REINS.get(), "message.isekai_mahou.mount_drop_nightmare");
        }
    }

    private static void give(ServerPlayer sp, Item reins, String messageKey) {
        ItemStack stack = new ItemStack(reins);
        Component name = stack.getHoverName();
        if (!sp.getInventory().add(stack)) sp.drop(stack, false);
        sp.sendSystemMessage(Component.translatable(messageKey, name).withStyle(ChatFormatting.GOLD));
        sp.level().playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.4F);
        Hints.show(sp, "mounts.reins");
    }

    // =====================================================================================
    //  Comandos de teste: /mahou mounts ...
    // =====================================================================================

    private static Map<String, Supplier<? extends EntityType<? extends Mob>>> types() {
        Map<String, Supplier<? extends EntityType<? extends Mob>>> m = new LinkedHashMap<>();
        m.put("griffin", MountsRegistries.GRIFFIN);
        m.put("sky_ray", MountsRegistries.SKY_RAY);
        m.put("pegasus", MountsRegistries.PEGASUS);
        m.put("wyvern", MountsRegistries.WYVERN);
        m.put("land_drake", MountsRegistries.LAND_DRAKE);
        m.put("nightmare", MountsRegistries.NIGHTMARE);
        m.put("aurora_stag", MountsRegistries.AURORA_STAG);
        return m;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> commands() {
        LiteralArgumentBuilder<CommandSourceStack> m = MahouCommands.op("mounts");
        // /mahou mounts summon <tipo>: a montaria já domada e selada ao lado do jogador
        m.then(Commands.literal("summon").then(Commands.argument("type", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(types().keySet(), b))
                .executes(c -> summon(c.getSource(), StringArgumentType.getString(c, "type")))));
        // /mahou mounts reins: as três Rédeas + sela + apito
        m.then(Commands.literal("reins").executes(c -> {
            ServerPlayer sp = c.getSource().getPlayerOrException();
            for (Item i : List.of(MountsRegistries.LAND_DRAKE_REINS.get(), MountsRegistries.NIGHTMARE_REINS.get(),
                    MountsRegistries.AURORA_STAG_REINS.get(), MountsRegistries.MOUNT_WHISTLE.get(), Items.SADDLE)) {
                ItemStack s = new ItemStack(i);
                if (!sp.getInventory().add(s)) sp.drop(s, false);
            }
            c.getSource().sendSuccess(() -> Component.literal("Rédeas (3), Apito e Sela entregues."), false);
            return 1;
        }));
        // /mahou mounts boss: simula um chefe derrotado (rola o Pesadelo) · /mahou mounts gift: confere o presente do Reino
        m.then(Commands.literal("boss").executes(c -> {
            ServerPlayer sp = c.getSource().getPlayerOrException();
            bossDefeated(sp);
            MagicData d = MagicApi.get(sp);
            c.getSource().sendSuccess(() -> Component.literal("Chefes contados: " + d.counter(BOSS_ROLLS) + "/" + NIGHTMARE_PITY
                    + " · Pesadelo: " + (d.hasFlag(FLAG_NIGHTMARE) ? "já ganhou" : "ainda não")), false);
            return 1;
        }));
        m.then(Commands.literal("gift").executes(c -> {
            ServerPlayer sp = c.getSource().getPlayerOrException();
            kingdomGift(sp);
            c.getSource().sendSuccess(() -> Component.literal("Reino: " + Reputation.standing(sp, KINGDOM).id + " (" + Reputation.get(sp, KINGDOM)
                    + ") · Cervo: " + (MagicApi.get(sp).hasFlag(FLAG_STAG) ? "já ganhou" : "precisa de Honrado (300)")), false);
            return 1;
        }));
        // /mahou mounts hints: mostra de novo as dicas do módulo
        m.then(Commands.literal("hints").executes(c -> {
            ServerPlayer sp = c.getSource().getPlayerOrException();
            Hints.force(sp, "mounts.city", Component.keybind("key.isekai_mahou.menu"));
            Hints.force(sp, "mounts.reins");
            Hints.force(sp, "mounts.wild", MountsRegistries.GRIFFIN.get().getDescription(),
                    Component.translatable("hint.isekai_mahou.mounts.tame.griffin"));
            return 1;
        }));
        return m;
    }

    private static int summon(CommandSourceStack src, String id) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        Supplier<? extends EntityType<? extends Mob>> type = types().get(id);
        if (type == null) {
            src.sendFailure(Component.literal("Montaria desconhecida: " + id + " (" + String.join(", ", types().keySet()) + ")"));
            return 0;
        }
        ServerLevel level = sp.serverLevel();
        Mob mob = type.get().create(level);
        if (mob == null) return 0;
        mob.moveTo(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), 0.0F);
        tameAndSaddle(mob, sp);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        src.sendSuccess(() -> Component.literal("Montaria pronta: ").append(mob.getDisplayName()), false);
        return 1;
    }

    /** Deixa a montaria domada e selada para o jogador (só para os comandos de teste). */
    private static void tameAndSaddle(Mob mob, Player owner) {
        if (mob instanceof GroundMount g) {
            g.tame(owner);
            g.setSaddled(true);
            g.setOrderedToSit(false);
        } else if (mob instanceof AbstractHorse h) {
            h.tameWithName(owner);
            h.equipSaddle(new ItemStack(Items.SADDLE), null);
        } else if (mob instanceof TamableAnimal t) {
            t.tame(owner);
            t.setOrderedToSit(false);
            if (t instanceof net.minecraft.world.entity.Saddleable s) s.equipSaddle(new ItemStack(Items.SADDLE), null);
        }
    }
}
