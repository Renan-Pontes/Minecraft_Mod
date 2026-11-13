package com.renanpontes.isekaimahou.feature.ambience;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * SERVIDOR. Sons próprios das magias SEM tocar nos glifos: ouve os eventos do núcleo.
 * <ul>
 * <li>{@link SpellCastEvent.Post} → som de conjuração na posição do conjurador (jogador ou mob);</li>
 * <li>{@link SpellDamageEvent.Post} (dano que entrou) → som de impacto no alvo.</li>
 * </ul>
 * O timbre vem da família do elemento; o tamanho vem do custo-base da magia (o mesmo patamar do anúncio:
 * {@link SpellCaster#ANNOUNCE_COST}) e de ser ultimate. Magias com o aumento "Silenciosa" não fazem som.
 * Freios: um impacto por conjurador a cada 3 ticks e um teto de pacotes por tick (chuva de projéteis não vira
 * chuva de pacotes).
 */
public final class SpellSounds {
    /** Custo-base a partir do qual a magia é "média". */
    private static final int MEDIUM_COST = 22;
    /** Dano de um único golpe que já soa como impacto grande. */
    private static final float BIG_HIT = 24F;
    private static final int IMPACT_GAP = 3;
    private static final int MAX_PER_TICK = 8;
    private static final double[] CAST_RADIUS = {24, 36, 64};
    private static final double[] IMPACT_RADIUS = {28, 56};

    private static final Map<UUID, Long> LAST_IMPACT = new HashMap<>();
    private static long budgetTick;
    private static int budgetUsed;

    static void init(IEventBus bus) {
        bus.addListener(SpellSounds::onCast);
        bus.addListener(SpellSounds::onDamage);
    }

    private static void onCast(SpellCastEvent.Post e) {
        LivingEntity caster = e.getCaster();
        SpellContext ctx = e.getContext();
        if (ctx == null || !(caster.level() instanceof ServerLevel level)) return;
        ParsedSpell spell = ctx.spell();
        if (spell == null || spell.formStats().silent || !budget(level)) return;
        int size = sizeOf(spell);
        send(level, AmbienceSounds.KIND_CAST, ctx.element(), size, caster.getEyePosition(), CAST_RADIUS[size], 0.06F);
    }

    private static void onDamage(SpellDamageEvent.Post e) {
        if (!e.landed()) return;
        LivingEntity target = e.target();
        SpellContext ctx = e.ctx();
        if (ctx == null || !(target.level() instanceof ServerLevel level)) return;
        ParsedSpell root = ctx.rootSpell();
        if (root == null || root.formStats().silent) return;
        boolean big = root.isUltimate() || root.baseCost() >= SpellCaster.ANNOUNCE_COST || e.getAmount() >= BIG_HIT;
        long now = level.getGameTime();
        UUID who = ctx.casterId();
        if (who != null) {
            Long last = LAST_IMPACT.get(who);
            if (last != null && now - last < IMPACT_GAP && now >= last) return;
            if (LAST_IMPACT.size() > 512) LAST_IMPACT.clear();
            LAST_IMPACT.put(who, now);
        }
        if (!budget(level)) return;
        Vec3 pos = target.position().add(0, target.getBbHeight() * 0.5, 0);
        send(level, AmbienceSounds.KIND_IMPACT, e.element(), big ? 1 : 0, pos, IMPACT_RADIUS[big ? 1 : 0], 0.1F);
    }

    /** 0 pequena, 1 média, 2 grande. */
    static int sizeOf(ParsedSpell spell) {
        if (spell.isUltimate() || spell.baseCost() >= SpellCaster.ANNOUNCE_COST) return 2;
        return spell.baseCost() >= MEDIUM_COST ? 1 : 0;
    }

    private static boolean budget(ServerLevel level) {
        long now = level.getGameTime();
        if (now != budgetTick) {
            budgetTick = now;
            budgetUsed = 0;
        }
        return budgetUsed++ < MAX_PER_TICK;
    }

    static void send(ServerLevel level, byte kind, Element element, int size, Vec3 pos, double radius, float pitchSpread) {
        float pitch = AmbienceSounds.pitchOf(element) * (1F + (level.random.nextFloat() * 2F - 1F) * pitchSpread);
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, radius, new AmbienceNet.Sfx(kind,
                (byte) AmbienceSounds.familyOf(element).ordinal(), (byte) size, (float) pos.x, (float) pos.y, (float) pos.z, pitch));
    }

    private SpellSounds() {}
}
