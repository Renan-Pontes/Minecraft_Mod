package com.renanpontes.isekaimahou.feature.combat_feel;

import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.module.Module;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Módulo "combat_feel" — v1.0 RELEASE, a "sensação" do combate:
 * <ul>
 *   <li><b>Micro-pausa</b> (hit-stop, só visual/cliente) em golpe pesado, crítico, aparo e explosão grande:
 *       {@link HitStopServer} decide quando, {@code client/HitStop} pausa a tela de quem recebeu. Dá para desligar.</li>
 *   <li><b>Golpe de misericórdia</b>: inimigo comum com menos de 10% de vida é finalizado com a tecla de usar, de
 *       perto, com um golpe curto ({@link Finisher}).</li>
 *   <li><b>Combo de magias</b>: conjurar em sequência rápida soma "Combo ×N" com um bônus pequeno que zera ao parar
 *       ({@link SpellCombo}, {@code client/ComboHud}).</li>
 *   <li><b>Conjuradores mais espertos</b>: mobs da tag {@code isekai_mahou:smart_casters} desviam, recuam, se curam e
 *       pedem ajuda ({@link SmartCasters}).</li>
 * </ul>
 * Comandos de teste (op): {@code /mahou combat_feel info | hitstop [tipo] | finisher | combo [n] | caster dodge|heal|help}.
 */
public class CombatFeelModule implements Module {
    private static final String[] STOP_NAMES = {"crit", "hurt", "heavy", "parry", "blast", "finisher"};

    @Override
    public String id() {
        return "combat_feel";
    }

    @Override
    public void init(IEventBus modBus) {
        FeelConfig.register();
        modBus.addListener(CombatFeelModule::registerPayloads);
        IEventBus bus = NeoForge.EVENT_BUS;
        HitStopServer.init(bus);
        Finisher.init(bus);
        SpellCombo.init(bus);
        SmartCasters.init(bus);
        MahouCommands.extend((root, ctx) -> {
            var hitstop = Commands.literal("hitstop").executes(c -> hitStop(c.getSource(), FeelPayloads.STOP_HEAVY));
            for (byte i = 0; i < STOP_NAMES.length; i++) {
                final byte kind = i;
                hitstop.then(Commands.literal(STOP_NAMES[i]).executes(c -> hitStop(c.getSource(), kind)));
            }
            root.then(MahouCommands.op("combat_feel")
                    .then(Commands.literal("info").executes(c -> info(c.getSource())))
                    .then(hitstop)
                    .then(Commands.literal("finisher").executes(c -> weaken(c.getSource())))
                    .then(Commands.literal("combo").executes(c -> combo(c.getSource(), -1))
                            .then(Commands.argument("n", IntegerArgumentType.integer(0, 99))
                                    .executes(c -> combo(c.getSource(), IntegerArgumentType.getInteger(c, "n")))))
                    .then(Commands.literal("caster")
                            .then(Commands.literal("dodge").executes(c -> caster(c.getSource(), 0)))
                            .then(Commands.literal("heal").executes(c -> caster(c.getSource(), 1)))
                            .then(Commands.literal("help").executes(c -> caster(c.getSource(), 2)))));
        });
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("combat_feel-1");
        r.playToClient(FeelPayloads.HitStop.TYPE, FeelPayloads.HitStop.STREAM_CODEC, (p, c) -> c.enqueueWork(
                () -> com.renanpontes.isekaimahou.feature.combat_feel.client.HitStop.trigger(p.kind())));
        r.playToClient(FeelPayloads.Combo.TYPE, FeelPayloads.Combo.STREAM_CODEC, (p, c) -> c.enqueueWork(
                () -> com.renanpontes.isekaimahou.feature.combat_feel.client.ComboHud.receive(p)));
    }

    // =====================================================================================
    //  Comandos de teste
    // =====================================================================================
    private static int info(CommandSourceStack source) {
        ServerPlayer p = source.getPlayer();
        int combo = p == null ? 0 : SpellCombo.count(p);
        long bonus = Math.round(SpellCombo.bonus(combo) * 100);
        int casters = p == null ? 0 : p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32),
                m -> SmartCasters.mind(m) != null).size();
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.combat_feel.info",
                FeelConfig.on(FeelConfig.FINISHER_ENABLED), FeelConfig.of(FeelConfig.FINISHER_HEALTH_PERCENT),
                FeelConfig.on(FeelConfig.COMBO_ENABLED), combo, bonus, FeelConfig.on(FeelConfig.SMART_CASTERS), casters), false);
        return 1;
    }

    private static int hitStop(CommandSourceStack source, byte kind) {
        ServerPlayer p = source.getPlayer();
        if (p == null) return 0;
        HitStopServer.send(p, kind);
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.combat_feel.hitstop", STOP_NAMES[kind]), false);
        return 1;
    }

    /** Deixa o inimigo comum mais próximo (até 12 blocos) quase morto, pronto para o golpe de misericórdia. */
    private static int weaken(CommandSourceStack source) {
        ServerPlayer p = source.getPlayer();
        if (p == null) return 0;
        Mob mob = nearest(p, 12, m -> Finisher.isCommonEnemy(m, null));
        if (mob == null) {
            source.sendFailure(Component.translatable("command.isekai_mahou.combat_feel.no_mob"));
            return 0;
        }
        mob.setHealth(Math.max(0.5F, mob.getMaxHealth() * Finisher.threshold() * 0.5F));
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.combat_feel.finisher", mob.getDisplayName()), false);
        return 1;
    }

    private static int combo(CommandSourceStack source, int n) {
        ServerPlayer p = source.getPlayer();
        if (p == null) return 0;
        int count = n < 0 ? SpellCombo.count(p) + 1 : n;
        SpellCombo.set(p, count);
        long bonus = Math.round(SpellCombo.bonus(count) * 100);
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.combat_feel.combo", count, bonus), false);
        return 1;
    }

    /** Força um reflexo no conjurador esperto mais próximo (até 16 blocos): 0 desviar, 1 curar, 2 pedir ajuda. */
    private static int caster(CommandSourceStack source, int what) {
        ServerPlayer p = source.getPlayer();
        if (p == null) return 0;
        Mob mob = nearest(p, 16, m -> SmartCasters.mind(m) != null);
        SmartCasters.Mind mind = mob == null ? null : SmartCasters.mind(mob);
        if (mob == null || mind == null) {
            source.sendFailure(Component.translatable("command.isekai_mahou.combat_feel.no_caster"));
            return 0;
        }
        String result;
        switch (what) {
            case 0 -> result = String.valueOf(SmartCasters.dodge(mob, mind, p.getEyePosition(), true));
            case 1 -> {
                mob.setHealth(Math.max(1F, mob.getMaxHealth() * 0.3F));
                result = String.valueOf(SmartCasters.startHeal(mob, mind, true));
            }
            default -> result = String.valueOf(SmartCasters.callHelp(mob, mind, p));
        }
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.combat_feel.caster", mob.getDisplayName(), result), false);
        return 1;
    }

    @Nullable
    private static Mob nearest(ServerPlayer p, double range, Predicate<Mob> filter) {
        List<Mob> mobs = p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(range), m -> m.isAlive() && filter.test(m));
        Mob best = null;
        for (Mob m : mobs) {
            if (best == null || m.distanceToSqr(p) < best.distanceToSqr(p)) best = m;
        }
        return best;
    }
}
