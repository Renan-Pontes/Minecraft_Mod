package com.renanpontes.isekaimahou.feature.combat_feel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * <b>Combo de magias</b> — "é tipo um storm do Magic": cada magia conjurada logo depois da anterior soma 1 no contador
 * "Combo ×N". A partir da segunda, cada magia dá um bônus PEQUENO de poder mágico (padrão +2% cada, até 10 magias =
 * +20%), que vale para as próximas magias e some inteiro quando o jogador para de conjurar (janela padrão: 4 s).
 * <p>
 * O bônus é um modificador temporário (não salvo) do atributo {@code isekai_mahou:spell_power}; morrer, sair ou trocar
 * de dimensão zera. Custo: um mapa por jogador com combo ativo, nada por tick para quem não está conjurando.
 */
public final class SpellCombo {
    public static final ResourceLocation MODIFIER_ID = IsekaiMahou.id("combat_feel_combo");
    public static final String HINT = "combat_feel_combo";
    /** Ticks mínimos entre duas magias que contam (magia repetida no mesmo instante — eco, gatilho — não infla o combo). */
    private static final int MIN_GAP = 4;
    private static final int MAX_COUNT = 99;

    private static final class State {
        int count;
        long lastCast;
        long expire;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    static void init(IEventBus bus) {
        bus.addListener(EventPriority.LOWEST, false, SpellCastEvent.Post.class, SpellCombo::onCast);
        bus.addListener(PlayerTickEvent.Post.class, SpellCombo::onTick);
        bus.addListener(LivingDeathEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) end(sp, true);
        });
        bus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) end(sp, true);
        });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp) end(sp, false);
        });
        bus.addListener(ServerStoppedEvent.class, e -> STATES.clear());
    }

    /** Combo atual do jogador (0 = nenhum). */
    public static int count(ServerPlayer player) {
        State st = STATES.get(player.getUUID());
        return st == null ? 0 : st.count;
    }

    private static void onCast(SpellCastEvent.Post event) {
        if (!(event.getCaster() instanceof ServerPlayer sp) || !FeelConfig.on(FeelConfig.COMBO_ENABLED)) return;
        long now = sp.serverLevel().getGameTime();
        State st = STATES.get(sp.getUUID());
        if (st != null && now - st.lastCast < MIN_GAP && now >= st.lastCast) return;
        set(sp, st == null || now > st.expire ? 1 : Math.min(MAX_COUNT, st.count + 1));
    }

    /** Põe o combo em {@code count} (reinicia a janela), aplica o bônus e avisa o cliente. */
    static void set(ServerPlayer sp, int count) {
        if (count <= 0) {
            end(sp, true);
            return;
        }
        long now = sp.serverLevel().getGameTime();
        int window = FeelConfig.of(FeelConfig.COMBO_WINDOW);
        State st = STATES.computeIfAbsent(sp.getUUID(), u -> new State());
        st.count = Math.min(MAX_COUNT, count);
        st.lastCast = now;
        st.expire = now + window;
        double bonus = bonus(st.count);
        apply(sp, bonus);
        PacketDistributor.sendToPlayer(sp, new FeelPayloads.Combo(st.count, window, window, (int) Math.round(bonus * 1000)));
        if (st.count >= 2) Hints.show(sp, HINT);
    }

    /** Bônus (fração: 0,04 = +4%) de um combo de {@code count} magias. A primeira não dá nada. */
    static double bonus(int count) {
        int steps = Math.min(Math.max(0, count - 1), FeelConfig.of(FeelConfig.COMBO_MAX_STEPS));
        return steps * FeelConfig.of(FeelConfig.COMBO_BONUS) / 100.0;
    }

    private static void apply(ServerPlayer sp, double bonus) {
        AttributeInstance inst = sp.getAttribute(CoreRegistries.SPELL_POWER);
        if (inst == null) return;
        if (bonus <= 0) inst.removeModifier(MODIFIER_ID);
        else inst.addOrUpdateTransientModifier(new AttributeModifier(MODIFIER_ID, bonus, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (STATES.isEmpty() || !(event.getEntity() instanceof ServerPlayer sp)) return;
        State st = STATES.get(sp.getUUID());
        if (st != null && sp.serverLevel().getGameTime() > st.expire) end(sp, true);
    }

    /** Zera o combo e tira o bônus. */
    static void end(ServerPlayer sp, boolean tellClient) {
        if (STATES.remove(sp.getUUID()) == null) return;
        apply(sp, 0);
        if (tellClient) PacketDistributor.sendToPlayer(sp, new FeelPayloads.Combo(0, 0, 0, 0));
    }

    private SpellCombo() {}
}
