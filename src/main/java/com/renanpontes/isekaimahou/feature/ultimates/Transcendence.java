package com.renanpontes.isekaimahou.feature.ultimates;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.network.CorePayloads;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Transcendência (estado de servidor): transformação de 30 s com aura, auréola e asas (desenhadas no cliente),
 * VOO, +50% de poder mágico, regeneração de mana ×3, recargas pela metade e pulsos dos efeitos da receita a cada
 * 1 s nos inimigos a até 6 blocos. Ao fim: Exaustão Mágica 5 s (e Queda Lenta se estiver no ar).
 * <p>
 * Voo e bônus são modificadores de atributo TRANSITÓRIOS com ids próprios (o voo é o atributo
 * {@code neoforge:creative_flight}): nunca mexemos em {@code abilities.mayfly}, então criativo/espectador continuam
 * voando, e nada fica salvo no jogador — sair do jogo, morrer ou o servidor cair não deixa voo "preso".
 */
public final class Transcendence {
    public static final ResourceLocation FLIGHT_ID = IsekaiMahou.id("ultimates_transcend_flight");
    public static final ResourceLocation POWER_ID = IsekaiMahou.id("ultimates_transcend_power");
    public static final ResourceLocation REGEN_ID = IsekaiMahou.id("ultimates_transcend_regen");
    public static final ResourceLocation SPEED_ID = IsekaiMahou.id("ultimates_transcend_speed");
    public static final ResourceLocation STEADY_ID = IsekaiMahou.id("ultimates_transcend_steady");
    /** Raio dos pulsos. */
    public static final double PULSE_RADIUS = 6.0;
    /** Poder de cada pulso (acerta a cada 1 s). */
    public static final float PULSE_POWER = 0.5F;

    private static final class State {
        final UUID id;
        final SpellContext pulse;
        final ServerLevel level;
        final int rgb;
        final Element element;
        final long endTick;
        final int total;
        int age;
        boolean warned;
        /** O voo já foi ligado automaticamente uma vez (depois disso o jogador liga/desliga como quiser). */
        boolean autoFlight;

        State(UUID id, SpellContext pulse, ServerLevel level, int rgb, Element element, long endTick, int total) {
            this.id = id;
            this.pulse = pulse;
            this.level = level;
            this.rgb = rgb;
            this.element = element;
            this.endTick = endTick;
            this.total = total;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();
    /** Recargas antes da conjuração (para cortar pela metade só as que a conjuração criou). */
    private static final Map<UUID, Map<String, Long>> COOLDOWN_SNAPSHOT = new HashMap<>();

    public static boolean isActive(Entity e) {
        return ACTIVE.containsKey(e.getUUID());
    }

    static void init() {
        NeoForge.EVENT_BUS.addListener(Transcendence::onServerTick);
        NeoForge.EVENT_BUS.addListener(Transcendence::onDeath);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> stop(e.getEntity(), false, false));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> stop(e.getEntity(), true, false));
        NeoForge.EVENT_BUS.addListener(Transcendence::onStartTracking);
        NeoForge.EVENT_BUS.addListener(Transcendence::onCastPre);
        NeoForge.EVENT_BUS.addListener(Transcendence::onCastPost);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            ACTIVE.clear();
            COOLDOWN_SNAPSHOT.clear();
        });
    }

    // =====================================================================================
    //  Início / fim
    // =====================================================================================
    /** Começa (ou renova) a transformação de {@code e} com os efeitos de {@code ctx} por {@code ticks}. */
    public static void start(SpellContext ctx, LivingEntity e, int ticks) {
        ServerLevel level = ctx.level();
        if (ACTIVE.containsKey(e.getUUID())) stop(e, false, true);
        int rgb = UltFx.rgb(ctx);
        Element element = ctx.primaryElement();
        State st = new State(e.getUUID(), ctx.copy(ctx.powerMult() * PULSE_POWER), level, rgb, element,
                level.getGameTime() + ticks, ticks);
        ACTIVE.put(e.getUUID(), st);

        e.addEffect(new MobEffectInstance(UltRegistries.TRANSCENDENCE, ticks, 0, false, false, true));
        add(e, CoreRegistries.SPELL_POWER, POWER_ID, 0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        add(e, CoreRegistries.MANA_REGEN, REGEN_ID, 2.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        add(e, Attributes.MOVEMENT_SPEED, SPEED_ID, 0.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        add(e, Attributes.KNOCKBACK_RESISTANCE, STEADY_ID, 0.6, AttributeModifier.Operation.ADD_VALUE);
        if (e instanceof Player p) {
            add(p, NeoForgeMod.CREATIVE_FLIGHT, FLIGHT_ID, 1.0, AttributeModifier.Operation.ADD_VALUE);
            // Ascende: decola (e já entra em voo alguns ticks depois, quando sair do chão — ver tickActive)
            p.setDeltaMovement(p.getDeltaMovement().add(0, 0.9, 0));
            p.hurtMarked = true;
            p.displayClientMessage(Component.translatable("message.isekai_mahou.ultimates.transcend_start", ticks / 20)
                    .withStyle(ChatFormatting.GOLD), true);
        } else {
            e.setDeltaMovement(e.getDeltaMovement().add(0, 0.6, 0));
            e.hurtMarked = true;
        }
        e.fallDistance = 0;

        // Efeitos benéficos da receita valem no próprio transcendente uma vez, ao despertar
        for (ParsedSpell.EffectEntry entry : ctx.spell().effects()) {
            if (entry.effect().isBeneficial()) ctx.applyEffect(entry, e);
        }

        // Despertar: coluna de luz, explosão de partículas do elemento, sinos
        Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
        UltFx.far(level, ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        UltFx.far(level, ElementFx.dust(element, 2.5F), c.x, c.y, c.z, 40, 1.2, 1.6, 1.2, 0);
        UltFx.far(level, ElementFx.signature(element), c.x, c.y, c.z, 24, 0.8, 1.2, 0.8, 0.15);
        // v1.3: pilar de luz de verdade + nova do elemento + aura que acompanha a Transcendência inteira
        ElementFx.pillar(level, e.position(), rgb, 1.0F, 28F, 34);
        ElementFx.nova(level, e.position().add(0, 0.1, 0), element, rgb, 6F, 5, false);
        ElementFx.aura(level, e, element, rgb, 1.0F, 4, Math.min(ticks, 600));
        UltFx.sound(level, c, SoundEvents.TOTEM_USE, 1.6F, 1.2F);
        UltFx.sound(level, c, SoundEvents.BEACON_POWER_SELECT, 2.5F, 0.6F);
        UltFx.sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 2.5F, 0.5F);
        UltNet.fx(level, UltNet.FX_RING, e.position().add(0, 0.2, 0), 0F, 9F, rgb, 14);
        UltNet.fx(level, UltNet.FX_FLASH, c, 0F, 2.5F, rgb, 8);
        BossKit.shake(level, c, 24, 0.8F, 14);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new UltNet.TranscendSync(e.getId(), true, rgb, ticks));
    }

    /**
     * Termina a transformação.
     * @param exhaustion aplica a Exaustão Mágica (fim natural)
     * @param quiet      sem efeitos/mensagem (renovação)
     */
    public static void stop(LivingEntity e, boolean exhaustion, boolean quiet) {
        State st = ACTIVE.remove(e.getUUID());
        if (st == null) return;
        remove(e, CoreRegistries.SPELL_POWER, POWER_ID);
        remove(e, CoreRegistries.MANA_REGEN, REGEN_ID);
        remove(e, Attributes.MOVEMENT_SPEED, SPEED_ID);
        remove(e, Attributes.KNOCKBACK_RESISTANCE, STEADY_ID);
        if (e instanceof Player p) {
            remove(p, NeoForgeMod.CREATIVE_FLIGHT, FLIGHT_ID);
            // Sem o atributo, só criativo/espectador continuam podendo voar (na renovação o voo volta no mesmo tick:
            // não derruba quem está no ar)
            if (!quiet && p.getAbilities().flying && !p.mayFly()) {
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
            }
            p.fallDistance = 0;
        }
        COOLDOWN_SNAPSHOT.remove(e.getUUID());
        if (quiet) return;
        e.removeEffect(UltRegistries.TRANSCENDENCE);
        if (e.isAlive()) {
            if (!e.onGround()) e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false, true));
            if (exhaustion) e.addEffect(new MobEffectInstance(CoreRegistries.MAGIC_EXHAUSTION, 100, 0));
            if (e.level() instanceof ServerLevel sl) {
                Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
                UltFx.far(sl, ElementFx.dust(st.element, 1.6F), c.x, c.y, c.z, 50, 0.8, 1.0, 0.8, 0);
                UltFx.far(sl, Vfx.cloud(), c.x, c.y, c.z, 20, 0.6, 0.8, 0.6, 0.02);
                UltFx.sound(sl, c, SoundEvents.BEACON_DEACTIVATE, 1.5F, 0.8F);
            }
            if (e instanceof ServerPlayer sp && exhaustion) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.ultimates.transcend_end")
                        .withStyle(ChatFormatting.GRAY), true);
            }
        }
        if (e.level() instanceof ServerLevel) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new UltNet.TranscendSync(e.getId(), false, st.rgb, 0));
        }
    }

    private static void add(LivingEntity e, Holder<Attribute> attr, ResourceLocation id, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) return;
        inst.removeModifier(id);
        inst.addTransientModifier(new AttributeModifier(id, amount, op));
    }

    private static void remove(LivingEntity e, Holder<Attribute> attr, ResourceLocation id) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst != null) inst.removeModifier(id);
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    private static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) return;
        MinecraftServer server = event.getServer();
        // Cópia: um pulso pode matar OUTRO transcendente (PvP, chefe que conjurou), e o LivingDeathEvent dele tira
        // o estado do mapa no meio da volta — iterar o mapa direto daria ConcurrentModificationException.
        List<State> states = new ArrayList<>(ACTIVE.values());
        List<Runnable> later = new ArrayList<>();
        for (State st : states) {
            if (ACTIVE.get(st.id) != st) continue;   // terminou/renovou durante esta volta
            Entity ent = st.level.getEntity(st.id);
            if (ent == null) {
                ServerPlayer sp = server.getPlayerList().getPlayer(st.id);
                ent = sp;
            }
            if (!(ent instanceof LivingEntity e) || !e.isAlive() || e.isRemoved() || e.level() != st.level) {
                // Sumiu sem passar pelos eventos (descarregou): os modificadores são transitórios, basta esquecer
                ACTIVE.remove(st.id, st);
                COOLDOWN_SNAPSHOT.remove(st.id);
                continue;
            }
            long now = st.level.getGameTime();
            if (now >= st.endTick || !e.hasEffect(UltRegistries.TRANSCENDENCE)) {
                later.add(() -> stop(e, true, false));
                continue;
            }
            tickActive(st, e, now);
        }
        later.forEach(Runnable::run);
    }

    private static void tickActive(State st, LivingEntity e, long now) {
        ServerLevel level = st.level;
        long left = st.endTick - now;
        e.fallDistance = 0;
        // Decolou: liga o voo no ar (no chão o cliente desligaria o voo na hora). Debaixo de teto baixo a decolagem
        // não tira do chão: o voo liga no primeiro pulo dos 3 s seguintes, sem precisar do toque duplo.
        st.age++;
        if (!st.autoFlight && st.age >= 4 && st.age <= 60 && e instanceof Player p && !p.onGround() && p.mayFly()) {
            st.autoFlight = true;
            if (!p.getAbilities().flying) {
                p.getAbilities().flying = true;
                p.onUpdateAbilities();
            }
        }
        if (now % 2 == 0) aura(level, e, st, now);
        if (now % 20 == 0) pulse(level, e, st);
        if (left == 60 && !st.warned && e instanceof ServerPlayer sp) {
            st.warned = true;
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.ultimates.transcend_fading")
                    .withStyle(ChatFormatting.YELLOW), true);
            UltFx.sound(level, e.position(), SoundEvents.BEACON_AMBIENT, 1.5F, 1.6F);
        }
    }

    /** Aura: espiral de partículas do elemento subindo pelo corpo + faíscas brancas. */
    private static void aura(ServerLevel level, LivingEntity e, State st, long now) {
        ParticleOptions dust = ElementFx.dust(st.element, 1.1F);
        double h = e.getBbHeight();
        for (int k = 0; k < 2; k++) {
            double a = now * 0.35 + k * Math.PI;
            double y = (now * 0.08 + k * 0.5) % 1.0 * h;
            level.sendParticles(dust, e.getX() + Math.cos(a) * 0.6, e.getY() + y, e.getZ() + Math.sin(a) * 0.6, 1, 0, 0, 0, 0);
        }
        if (now % 6 == 0) {
            level.sendParticles(Vfx.endRod(), e.getX(), e.getY() + h * 0.5, e.getZ(), 1, 0.35, h * 0.35, 0.35, 0.01);
            level.sendParticles(new DustParticleOptions(Vec3.fromRGB24(0xFFFFFF).toVector3f(), 0.8F),
                    e.getX(), e.getY() + 0.1, e.getZ(), 3, 0.4, 0.05, 0.4, 0);
        }
    }

    /** Pulso: os efeitos da receita nos inimigos a até 6 blocos (e os benéficos nos aliados por perto). */
    private static void pulse(ServerLevel level, LivingEntity e, State st) {
        Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
        List<LivingEntity> foes = UltFx.foesAround(st.pulse, e, c, PULSE_RADIUS);
        float[] snap = UltFx.chargeSnapshot(e);
        for (LivingEntity f : foes) {
            st.pulse.resolveOnEntity(f, e);
            ElementFx.line(level, c, f.position().add(0, f.getBbHeight() * 0.5, 0), st.element);
        }
        boolean beneficial = st.pulse.spell().effects().stream().anyMatch(x -> x.effect().isBeneficial());
        if (beneficial) {
            for (LivingEntity a : level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(PULSE_RADIUS),
                    a -> a != e && a.isAlive() && st.pulse.isAlly(a))) {
                st.pulse.resolveOnEntity(a, e);
            }
        }
        UltFx.chargeRestore(e, snap);
        UltNet.fx(level, UltNet.FX_RING, e.position().add(0, 0.15, 0), 0F, (float) PULSE_RADIUS, st.rgb, 10);
        if (!foes.isEmpty()) UltFx.sound(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.7F);
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================
    private static void onDeath(LivingDeathEvent e) {
        if (!e.getEntity().level().isClientSide) stop(e.getEntity(), false, false);
    }

    private static void onStartTracking(PlayerEvent.StartTracking e) {
        State st = ACTIVE.get(e.getTarget().getUUID());
        if (st == null || !(e.getEntity() instanceof ServerPlayer viewer)) return;
        int left = (int) Math.max(1, st.endTick - st.level.getGameTime());
        PacketDistributor.sendToPlayer(viewer, new UltNet.TranscendSync(e.getTarget().getId(), true, st.rgb, left));
    }

    /** Recargas pela metade: guarda as recargas antes e corta pela metade as que a conjuração criou. */
    private static void onCastPre(SpellCastEvent.Pre e) {
        if (!(e.getCaster() instanceof ServerPlayer sp) || !ACTIVE.containsKey(sp.getUUID())) return;
        COOLDOWN_SNAPSHOT.put(sp.getUUID(), new HashMap<>(MagicApi.get(sp).cooldowns));
    }

    private static void onCastPost(SpellCastEvent.Post e) {
        if (!(e.getCaster() instanceof ServerPlayer sp)) return;
        Map<String, Long> before = COOLDOWN_SNAPSHOT.remove(sp.getUUID());
        if (before == null || !ACTIVE.containsKey(sp.getUUID())) return;
        if (e.getContext().rootSpell().isUltimate()) return;
        MagicData d = MagicApi.get(sp);
        long now = sp.serverLevel().getGameTime();
        for (Map.Entry<String, Long> c : d.cooldowns.entrySet()) {
            Long old = before.get(c.getKey());
            long end = c.getValue();
            if (end <= now || (old != null && old == end)) continue;
            int ticks = (int) ((end - now) / 2);
            c.setValue(now + ticks);
            PacketDistributor.sendToPlayer(sp, new CorePayloads.Cooldown(c.getKey(), ticks));
        }
    }

    private Transcendence() {}
}
