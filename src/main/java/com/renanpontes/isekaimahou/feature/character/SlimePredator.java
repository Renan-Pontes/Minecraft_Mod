package com.renanpontes.isekaimahou.feature.character;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Statuses;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.MagmaCube;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * v1.7 — <b>Slime = Rimuru</b> (Tensei shitara Slime Datta Ken). A habilidade única <b>Predador</b>: devorar criaturas
 * abatidas para COPIAR uma habilidade delas (mimetismo), com a análise do <b>Grande Sábio</b>.
 * <ul>
 *   <li>Cada abate de um tipo copiável é uma amostra; com {@value #SAMPLES} amostras o Grande Sábio conclui a análise e a
 *       habilidade é aprendida (transformado: 1 amostra basta). Todo abate do Slime é "devorado" (+1 de vida).</li>
 *   <li>Capacidade: 2/3/4/5 habilidades nos níveis de raça I–IV e 6 no V ({@link #capacity}); cheio, a MAIS ANTIGA é
 *       descartada — o jogador escolhe o que guarda caçando de novo.</li>
 *   <li>Habilidades ({@link Mimic}): Fio Pegajoso (aranha: escala paredes), Passo do Lobo Tempestade (lobo: +6% de
 *       velocidade), Ultrassom (morcego: visão no escuro), Explosão Gelatinosa (creeper: abaixo de 30% de vida explode
 *       em ácido, 1×/60 s), Resistência a Chamas (blaze/cubo de magma: −40% de fogo), Respiração Aquática (afogado,
 *       guardião, lula) e Resistência à Podridão (zumbi: imune a Fome e Veneno). Os efeitos moram em
 *       {@link RacePassives} (atributos, dano, efeitos, visão) e no cliente (escalar).</li>
 *   <li>Grande Sábio: falas RARAS — análise começou (1× por tipo), análise concluída (chat), aviso de vida crítica (1× a
 *       cada 10 min) e o relatório sob demanda (Shift + tecla racial). Nada fica fixo na tela.</li>
 * </ul>
 */
public final class SlimePredator {
    /** Amostras para concluir a análise (fora da forma). */
    public static final int SAMPLES = 3;
    private static final int BURST_GAP = 60 * 20;
    private static final int WARN_GAP = 10 * 60 * 20;
    private static final int SAGE_RGB = 0x7FD4FF;

    /** Habilidades copiáveis. O id é salvo (CharacterData) — NÃO renomear. */
    public enum Mimic {
        SPIDER("spider", 0xC8C8D0),
        WOLF("wolf", 0x9FB8D8),
        BAT("bat", 0x8A6A9A),
        CREEPER("creeper", 0x5FD05A),
        BLAZE("blaze", 0xFFB040),
        AQUATIC("aquatic", 0x3FA9FF),
        ZOMBIE("zombie", 0x6A9A5A);

        public static final Mimic[] ALL = values();
        public final String id;
        public final int rgb;

        Mimic(String id, int rgb) {
            this.id = id;
            this.rgb = rgb;
        }

        /** Nome da habilidade copiada. */
        public MutableComponent displayName() {
            return Component.translatable("character.isekai_mahou.mimic." + id).withColor(rgb);
        }

        /** O que faz (curto). */
        public MutableComponent desc() {
            return Component.translatable("character.isekai_mahou.mimic." + id + ".desc");
        }

        /** De quem se copia ("Aranha", "Lobo"...). */
        public MutableComponent source() {
            return Component.translatable("character.isekai_mahou.mimic." + id + ".source");
        }

        @Nullable
        public static Mimic byId(String id) {
            for (Mimic m : ALL) if (m.id.equals(id)) return m;
            return null;
        }

        /** Que habilidade esta criatura ensina (null = nenhuma). A ordem importa: o Afogado também é um Zumbi. */
        @Nullable
        public static Mimic of(LivingEntity e) {
            if (e instanceof Spider) return SPIDER;
            if (e instanceof Wolf) return WOLF;
            if (e instanceof Bat) return BAT;
            if (e instanceof Creeper) return CREEPER;
            if (e instanceof Blaze || e instanceof MagmaCube) return BLAZE;
            if (e instanceof Drowned || e instanceof Guardian || e instanceof Squid) return AQUATIC;
            if (e instanceof Zombie) return ZOMBIE;
            return null;
        }
    }

    private static final Map<UUID, Long> BURST_READY = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> WARN_READY = new ConcurrentHashMap<>();

    static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, SlimePredator::onKill);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> forget(e.getEntity().getUUID()));
    }

    static void forget(UUID id) {
        BURST_READY.remove(id);
        WARN_READY.remove(id);
    }

    static void clearAll() {
        BURST_READY.clear();
        WARN_READY.clear();
    }

    /** Quantas habilidades o Predador guarda no nível de raça {@code lv}. */
    public static int capacity(int lv) {
        return lv >= 5 ? 6 : Math.max(2, Math.min(5, lv + 1));
    }

    /** O jogador é Slime e tem a habilidade {@code m}? (lado comum: o cliente usa os dados sincronizados) */
    public static boolean has(CharacterData d, Mimic m) {
        return d.is(Race.SLIME) && d.hasMimic(m.id);
    }

    public static boolean has(Player p, Mimic m) {
        return has(CharacterContent.data(p), m);
    }

    /** Linha do Grande Sábio (prefixo azul-claro). */
    static MutableComponent sage(Component line) {
        return Component.translatable("message.isekai_mahou.character.sage", line).withColor(SAGE_RGB);
    }

    // =====================================================================================
    //  Devorar
    // =====================================================================================
    private static void onKill(LivingDeathEvent e) {
        if (e.isCanceled() || !(e.getSource().getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        LivingEntity victim = e.getEntity();
        if (victim instanceof Player || victim == sp) return;
        CharacterData d = CharacterContent.data(sp);
        if (!d.is(Race.SLIME)) return;
        ServerLevel level = sp.serverLevel();
        // Todo abate é devorado: o corpo some numa gosma que volta para o Slime
        if (sp.getHealth() < sp.getMaxHealth()) sp.heal(1F);
        if (victim.distanceToSqr(sp) < 12 * 12) {
            RaceFx.line(level, RaceFx.center(victim), RaceFx.center(sp), ParticleTypes.ITEM_SLIME);
        }
        Mimic m = Mimic.of(victim);
        if (m == null || d.hasMimic(m.id)) return;
        boolean form = RaceForms.formOf(sp) == Race.SLIME;
        int n = d.analyze(m.id);
        if (n < (form ? 1 : SAMPLES)) {
            if (n == 1) {
                sp.displayClientMessage(sage(Component.translatable("message.isekai_mahou.character.sage_analyzing",
                        m.source(), n, SAMPLES)), true);
            }
            return;
        }
        learn(sp, d, m);
    }

    /** Aprende {@code m} (descarta a mais antiga se cheio) com a fala do Grande Sábio. */
    static void learn(ServerPlayer sp, CharacterData d, Mimic m) {
        String dropped = d.learnMimic(m.id, capacity(RaceProgress.level(sp)));
        sp.sendSystemMessage(sage(Component.translatable("message.isekai_mahou.character.sage_learned", m.displayName(), m.desc())));
        Mimic old = dropped == null ? null : Mimic.byId(dropped);
        if (old != null) {
            sp.sendSystemMessage(sage(Component.translatable("message.isekai_mahou.character.sage_dropped", old.displayName(),
                    capacity(RaceProgress.level(sp)))));
        }
        ServerLevel level = sp.serverLevel();
        RaceFx.dustBurst(level, RaceFx.center(sp), m.rgb, 18, 0.5);
        level.sendParticles(ParticleTypes.ITEM_SLIME, sp.getX(), sp.getY() + 0.8, sp.getZ(), 12, 0.35, 0.4, 0.35, 0.05);
        RaceFx.sound(level, sp.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 1.5F);
        RaceFx.sound(level, sp.position(), SoundEvents.SLIME_SQUISH, 0.8F, 0.7F);
        d.dirty = true;
    }

    // =====================================================================================
    //  A cada segundo (RacePassives.tickSecond): Explosão Gelatinosa, Respiração Aquática, aviso do Grande Sábio
    // =====================================================================================
    static void tickSecond(ServerPlayer sp, CharacterData d) {
        if (!d.is(Race.SLIME) || !sp.isAlive()) return;
        long now = RacePassives.now(sp);
        boolean hurtRecently = sp.tickCount - sp.getLastHurtByMobTimestamp() < 40 && sp.getLastHurtByMob() != null;
        float frac = sp.getHealth() / Math.max(1F, sp.getMaxHealth());
        if (has(d, Mimic.CREEPER) && frac < 0.30F && hurtRecently) {
            Long ready = BURST_READY.get(sp.getUUID());
            if (ready == null || now >= ready) {
                BURST_READY.put(sp.getUUID(), now + BURST_GAP);
                burst(sp);
            }
        }
        if (has(d, Mimic.AQUATIC) && sp.isEyeInFluid(FluidTags.WATER)) {
            sp.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 60, 0, true, false, false));
        }
        if (frac < 0.25F && hurtRecently) {
            Long ready = WARN_READY.get(sp.getUUID());
            if (ready == null || now >= ready) {
                WARN_READY.put(sp.getUUID(), now + WARN_GAP);
                sp.displayClientMessage(sage(Component.translatable("message.isekai_mahou.character.sage_warning")), true);
            }
        }
    }

    /** Explosão Gelatinosa (copiada do creeper): ácido e empurrão em volta — não quebra blocos. */
    private static void burst(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        for (LivingEntity le : RaceForms.foesAround(sp, 4.0)) {
            RaceForms.hit(sp, le, 8F);
            Vec3 away = le.position().subtract(sp.position());
            away = new Vec3(away.x, 0, away.z);
            away = away.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : away.normalize();
            le.push(away.x * 1.1, 0.4, away.z * 1.1);
            le.hurtMarked = true;
            Statuses.apply(le, Statuses.Kind.EXPOSED, 1, 80, sp, 1F);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, sp.getX(), sp.getY() + 0.8, sp.getZ(), 2, 0.6, 0.3, 0.6, 0);
        level.sendParticles(ParticleTypes.ITEM_SLIME, sp.getX(), sp.getY() + 0.8, sp.getZ(), 30, 1.2, 0.5, 1.2, 0.15);
        RaceFx.dustBurst(level, RaceFx.center(sp), Mimic.CREEPER.rgb, 20, 1.2);
        RaceFx.sound(level, sp.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 1.4F);
        RaceFx.sound(level, sp.position(), SoundEvents.SLIME_SQUISH, 1.0F, 0.6F);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.character.slime_burst").withColor(Mimic.CREEPER.rgb), true);
    }

    // =====================================================================================
    //  Relatório (Shift + tecla racial)
    // =====================================================================================
    static void report(ServerPlayer sp) {
        CharacterData d = CharacterContent.data(sp);
        int cap = capacity(RaceProgress.level(sp));
        d.trimMimics(cap);
        sp.sendSystemMessage(sage(Component.translatable("message.isekai_mahou.character.sage_report", d.mimics().size(), cap)));
        for (String id : d.mimics()) {
            Mimic m = Mimic.byId(id);
            if (m == null) continue;
            sp.sendSystemMessage(Component.literal("  • ").withColor(0x6C6480).append(m.displayName())
                    .append(Component.literal(" — ").withColor(0x6C6480)).append(m.desc().withColor(0xE8E2F4)));
        }
        MutableComponent missing = Component.empty();
        boolean any = false;
        for (Mimic m : Mimic.ALL) {
            if (d.hasMimic(m.id)) continue;
            if (any) missing.append(Component.literal(", ").withColor(0x6C6480));
            missing.append(m.source().withColor(0xA89EC0));
            int a = d.analysis(m.id);
            if (a > 0) missing.append(Component.literal(" " + a + "/" + SAMPLES).withColor(0x6C6480));
            any = true;
        }
        if (any) {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.character.sage_targets", missing).withColor(0xA89EC0));
        } else {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.character.sage_all").withColor(0xA89EC0));
        }
        RaceFx.sound(sp.serverLevel(), sp.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 1.8F);
    }

    private SlimePredator() {}
}
