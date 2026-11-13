package com.renanpontes.isekaimahou.feature.character.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;
import com.renanpontes.isekaimahou.feature.character.CharacterNetwork;
import com.renanpontes.isekaimahou.feature.character.Race;
import com.renanpontes.isekaimahou.feature.character.RaceFlight;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/**
 * SÓ CLIENTE (v1.3). O espetáculo das <b>Transformações raciais</b> — "algo grande e temível":
 * <ol>
 *   <li><b>Carga</b> ({@link CharacterNetwork#FX_CHARGE}, 2,5 s): energia convergindo para o peito, o chão rachando e
 *       tremendo em volta (rachaduras brilhantes crescendo + círculo no chão), o corpo se erguendo um pouco, uma coluna de
 *       luz (o Anjo recebe um pilar descendo do céu); quem está perto sente a tela tremer e escurecer (vinheta).</li>
 *   <li><b>Explosão</b> ({@link CharacterNetwork#FX_BURST}): clarão na tela, onda de choque dupla correndo pelo chão
 *       (anel + parede), pilar, detritos voando, a assinatura da raça (teias, penas de luz, magma, névoa de sangue...),
 *       tremor forte e o título intimidador.</li>
 *   <li><b>Depois</b>: as rachaduras continuam brilhando e se apagam aos poucos; a forma tem aura constante
 *       ({@link #ambient}: fogo do inferno e pegadas em brasa, fumaça do sopro, penas de luz, runas da lua, gotas de
 *       magma, névoa de sangue, névoa gelada...).</li>
 *   <li><b>Desfazer</b>: o corpo da forma se dissipa em partículas que sobem.</li>
 *   <li>v1.3.1 — <b>voo planado</b> ({@link RaceFlight}): rastro nas pontas das asas (cresce com a velocidade; vento e
 *       assinatura da raça), lufada a cada batida de asas, poeira ao pousar, rastro de cometa no mergulho e a cratera do
 *       impacto (rachaduras + anel). Na explosão, as formas aladas abrem as asas num golpe de vento e a câmera de quem se
 *       transformou dá um "soco" de FOV.</li>
 * </ol>
 * release 1.0: o que é PRÓPRIO de cada raça (selo desenhado no chão, casca de luz, partícula de assinatura, a "voz" da
 * entrada) fica no {@link FormAura}; a pose de revelação, no {@link RacePoses}.
 * <p>
 * Tudo passa por {@link FxQuality} (quantidade, alcance, extras caros só no ALTO/ULTRA). Sem buscas grandes por tick:
 * só os jogadores do mundo do cliente e as listas daqui (com teto).
 */
public final class FormFx {
    private static final int MAX_BURSTS = 8;
    private static final int BURST_LIFE = 110;
    private static final int RING_LIFE = 18;
    private static final float RING_RADIUS = 9F;

    /** Carga em andamento (tempo de jogo do cliente) e as rachaduras dela. */
    private record Charge(long start, long until, float[][] cracks) {}

    /** Explosão: onde, de que raça/cor, quando, rachaduras (crescem e ficam brilhando); {@code small} = impacto do mergulho. */
    private record Burst(Vec3 pos, Race race, int rgb, long start, float[][] cracks, boolean small) {}

    private record Shake(Vec3 pos, float radius, float intensity, int ticks, long start) {}

    private static final Map<Integer, Charge> CHARGES = new ConcurrentHashMap<>();
    private static final List<Burst> BURSTS = new CopyOnWriteArrayList<>();
    private static final List<Shake> SHAKES = new CopyOnWriteArrayList<>();
    /** entityId → início do desfazer. */
    private static final Map<Integer, Long> FADES = new ConcurrentHashMap<>();
    /** entityId → última pegada em brasa (Demônio). */
    private static final Map<Integer, Vec3> PRINTS = new ConcurrentHashMap<>();
    /** entityId → fim do mergulho (rastro de cometa). */
    private static final Map<Integer, Long> DIVES = new ConcurrentHashMap<>();
    /** entityId → estava planando no tick anterior (para a poeira do pouso). */
    private static final Map<Integer, Boolean> GLIDING = new ConcurrentHashMap<>();
    /** Quando o jogador local se transformou (soco de FOV). */
    private static long selfBurstAt = -100;
    /** Camadas de pose empilhadas no RenderPlayerEvent.Pre (para o Post desempilhar só as nossas). */
    private static final Deque<Boolean> LIFTED = new ArrayDeque<>();

    private static float dark, darkPrev;
    private static int darkRgb;
    private static long flashStart = -100;
    private static int flashRgb = 0xFFFFFF;
    private static float flashPower;
    private static float chargeShake;
    private static ClientLevel fxLevel;

    static final RenderType GLOW = RenderType.create("isekai_mahou_race_form_glow",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 32768, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setOutputState(RenderStateShard.PARTICLES_TARGET)
                    .createCompositeState(false));

    static void init(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(FormFx::onTick);
        NeoForge.EVENT_BUS.addListener(FormFx::onCamera);
        NeoForge.EVENT_BUS.addListener(FormFx::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RenderPlayerEvent.Pre.class, FormFx::preRender);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, RenderPlayerEvent.Post.class, FormFx::postRender);
        modBus.addListener((RegisterGuiLayersEvent e) ->
                e.registerAbove(VanillaGuiLayers.CAMERA_OVERLAYS, IsekaiMahou.id("race_form_overlay"), FormFx::renderOverlay));
        // release 1.0: o que é próprio de cada raça (selo no chão, casca de luz, assinatura) e a pose de revelação
        FormAura.init();
        RacePoses.init();
    }

    static void clear() {
        CHARGES.clear();
        BURSTS.clear();
        FormAura.clear();
        SHAKES.clear();
        FADES.clear();
        PRINTS.clear();
        DIVES.clear();
        GLIDING.clear();
        selfBurstAt = -100;
        dark = darkPrev = 0;
        chargeShake = 0;
        flashStart = -100;
    }

    // =====================================================================================
    //  Pacotes
    // =====================================================================================
    static void onFx(CharacterNetwork.RaceFx f) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        syncLevel(mc.level);
        long now = mc.level.getGameTime();
        if (f.kind() == CharacterNetwork.FX_CHARGE) {
            if (f.ticks() <= 0) {
                CHARGES.remove(f.entityId());
                return;
            }
            CHARGES.put(f.entityId(), new Charge(now, now + f.ticks(), cracks(RandomSource.create(f.entityId() * 31L + now), 9, 5.5F)));
            // release 1.0: a "voz" da raça abre a entrada (só para quem está perto)
            Entity who = mc.level.getEntity(f.entityId());
            Race voice = who == null ? null : raceOf(who);
            if (voice != null && inRange(mc, who)) FormAura.entrySound(mc.level, who, voice);
            return;
        }
        // Explosão
        Charge ch = CHARGES.remove(f.entityId());
        Entity e = mc.level.getEntity(f.entityId());
        if (e == null) return;
        Race race = raceOf(e);
        if (race == null) return;
        int rgb = color(e, race);
        float[][] cr = ch != null ? ch.cracks() : cracks(RandomSource.create(f.entityId() * 31L + now), 9, 5.5F);
        while (BURSTS.size() >= MAX_BURSTS) BURSTS.remove(0);
        BURSTS.add(new Burst(e.position(), race, rgb, now, cr, false));
        if (e == mc.player) selfBurstAt = now;
        // release 1.0: a pose de revelação da raça (barata: toca mesmo de longe)
        if (e instanceof net.minecraft.client.player.AbstractClientPlayer acp) RacePoses.play(acp, race);
        double dist = mc.player == null ? 999 : mc.player.distanceTo(e);
        if (dist > FxQuality.range()) return;
        burstParticles(mc.level, e, race, rgb);
        FormAura.burst(mc.level, e, race, rgb);
        // Tremor forte, clarão e o título (só para quem está perto)
        SHAKES.add(new Shake(e.position(), 28F, 2.6F, 26, now));
        if (dist < 28) {
            flashStart = now;
            flashRgb = mix(rgb, 0xFFFFFF, 0.55F);
            flashPower = (float) Mth.clamp(1.0 - dist / 28.0, 0.25, 1.0);
            title(mc, e, race, rgb);
        }
    }

    /** v1.3.1: batida de asas de outro jogador, mergulho, impacto do mergulho. */
    static void onFlightFx(CharacterNetwork.RaceFx f) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        syncLevel(mc.level);
        long now = mc.level.getGameTime();
        Entity e = mc.level.getEntity(f.entityId());
        switch (f.kind()) {
            case CharacterNetwork.FX_BEAT -> {
                // O jogador local já viu a própria batida (previsão)
                if (!(e instanceof Player p) || p == mc.player) return;
                ClientCharacter.markBeat(p.getId(), now);
                Race race = GlideClient.wingedForm(p);
                if (race != null && inRange(mc, p)) beatFx(p, race, f.ticks() == RaceFlight.BEAT_TAKEOFF, false);
            }
            case CharacterNetwork.FX_DIVE -> {
                if (f.ticks() <= 0) DIVES.remove(f.entityId());
                else DIVES.put(f.entityId(), now + f.ticks());
            }
            case CharacterNetwork.FX_IMPACT -> {
                DIVES.remove(f.entityId());
                if (e == null) return;
                Race race = raceOf(e);
                if (race == null) return;
                int rgb = color(e, race);
                while (BURSTS.size() >= MAX_BURSTS) BURSTS.remove(0);
                BURSTS.add(new Burst(e.position(), race, rgb, now, cracks(RandomSource.create(f.entityId() * 17L + now), 7, 4.0F), true));
                if (inRange(mc, e)) impactParticles(mc.level, e, rgb);
            }
            default -> { }
        }
    }

    /** Lufada de uma batida de asas (para baixo e para os lados); na decolagem, o chão levanta poeira. */
    static void beatFx(Player p, Race race, boolean takeoff, boolean local) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ClientLevel level = mc.level;
        RandomSource rnd = level.random;
        int rgb = color(p, race);
        double s = p.getScale();
        Vec3 c = p.position().add(0, p.isFallFlying() ? 0.3 : 1.2 * s, 0);
        int n = FxQuality.count(takeoff ? 22 : 9);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double vx = Math.cos(a) * 0.35, vz = Math.sin(a) * 0.35;
            level.addParticle(ParticleTypes.CLOUD, c.x + Math.cos(a) * 0.8 * s, c.y, c.z + Math.sin(a) * 0.8 * s, vx, -0.12, vz);
            if (i % 2 == 0) level.addParticle(glow(rgb, 1.0F), c.x + Math.cos(a) * 1.4 * s, c.y, c.z + Math.sin(a) * 1.4 * s, vx * 0.5, -0.05, vz * 0.5);
        }
        switch (race) {
            case ANGEL -> {
                for (int i = 0; i < FxQuality.count(6); i++) {
                    level.addParticle(ParticleTypes.WHITE_ASH, c.x + (rnd.nextDouble() - 0.5) * 3 * s, c.y, c.z + (rnd.nextDouble() - 0.5) * 3 * s, 0, -0.05, 0);
                    level.addParticle(ParticleTypes.END_ROD, c.x + (rnd.nextDouble() - 0.5) * 2.5 * s, c.y, c.z + (rnd.nextDouble() - 0.5) * 2.5 * s, 0, -0.03, 0);
                }
            }
            case DEMON -> {
                for (int i = 0; i < FxQuality.count(5); i++) {
                    level.addParticle(rnd.nextBoolean() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.LARGE_SMOKE,
                            c.x + (rnd.nextDouble() - 0.5) * 2.5 * s, c.y, c.z + (rnd.nextDouble() - 0.5) * 2.5 * s, 0, -0.04, 0);
                }
            }
            default -> {
                ParticleOptions sig = ElementFx.style(elementOf(rgb), 0.8F);
                for (int i = 0; i < FxQuality.count(5); i++) {
                    level.addParticle(sig, c.x + (rnd.nextDouble() - 0.5) * 2.5 * s, c.y, c.z + (rnd.nextDouble() - 0.5) * 2.5 * s, 0, -0.04, 0);
                }
            }
        }
        if (takeoff) {
            BlockState ground = level.getBlockState(BlockPos.containing(p.getX(), p.getY() - 0.5, p.getZ()));
            if (ground.isAir()) ground = level.getBlockState(BlockPos.containing(p.getX(), p.getY() - 1.5, p.getZ()));
            if (!ground.isAir()) {
                BlockParticleOption crumb = new BlockParticleOption(ParticleTypes.BLOCK, ground);
                for (int i = 0; i < FxQuality.count(16); i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, r = 0.5 + rnd.nextDouble() * 2.0;
                    level.addParticle(crumb, p.getX() + Math.cos(a) * r, p.getY() - 0.4, p.getZ() + Math.sin(a) * r, Math.cos(a) * 0.2, 0.25, Math.sin(a) * 0.2);
                }
            }
        }
        if (local) {
            level.playLocalSound(p.getX(), p.getY(), p.getZ(), RaceFlight.flapSound(race), SoundSource.PLAYERS, takeoff ? 1.2F : 0.9F,
                    RaceFlight.flapPitch(race), false);
            if (takeoff) level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS, 0.7F, 0.6F, false);
        }
    }

    /** Cratera do mergulho: poeira correndo, detritos, coluna de cor. */
    private static void impactParticles(ClientLevel level, Entity e, int rgb) {
        RandomSource rnd = level.random;
        double x = e.getX(), y = e.getY(), z = e.getZ();
        int ring = FxQuality.count(36);
        for (int i = 0; i < ring; i++) {
            double a = Math.PI * 2 * i / ring;
            level.addParticle(ParticleTypes.CLOUD, x + Math.cos(a) * 0.6, y + 0.2, z + Math.sin(a) * 0.6, Math.cos(a) * 0.8, 0.03, Math.sin(a) * 0.8);
            if (i % 2 == 0) level.addParticle(glow(rgb, 1.5F), x + Math.cos(a), y + 0.3, z + Math.sin(a), Math.cos(a) * 0.5, 0.08, Math.sin(a) * 0.5);
        }
        BlockState ground = level.getBlockState(BlockPos.containing(x, y - 0.2, z));
        if (!ground.isAir()) {
            BlockParticleOption crumb = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            for (int i = 0; i < FxQuality.count(30); i++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = 0.5 + rnd.nextDouble() * 3.0;
                level.addParticle(crumb, x + Math.cos(a) * r, y + 0.2, z + Math.sin(a) * r, Math.cos(a) * 0.3, 0.5 + rnd.nextDouble() * 0.5, Math.sin(a) * 0.3);
            }
        }
        for (int i = 0; i < FxQuality.count(16); i++) {
            level.addParticle(glow(rgb, 1.8F), x + (rnd.nextDouble() - 0.5) * 0.8, y + rnd.nextDouble(), z + (rnd.nextDouble() - 0.5) * 0.8, 0, 0.4 + rnd.nextDouble() * 0.4, 0);
        }
    }

    /** O corpo da forma começa a se dissipar ({@link ClientCharacter#FADE} ticks). */
    static void onDetransform(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        FADES.put(entityId, mc.level.getGameTime());
        Entity e = mc.level.getEntity(entityId);
        Race race = e == null ? null : raceOf(e);
        if (race == null || !inRange(mc, e)) return;
        int rgb = color(e, race);
        RandomSource rnd = mc.level.random;
        int n = FxQuality.count(26);
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = 0.3 + rnd.nextDouble() * 0.6;
            mc.level.addParticle(glow(rgb, 1.2F), e.getX() + Math.cos(a) * r, e.getY() + rnd.nextDouble() * e.getBbHeight(),
                    e.getZ() + Math.sin(a) * r, Math.cos(a) * 0.05, 0.04 + rnd.nextDouble() * 0.06, Math.sin(a) * 0.05);
        }
        for (int i = 0; i < FxQuality.count(10); i++) {
            mc.level.addParticle(ParticleTypes.POOF, e.getX(), e.getY() + rnd.nextDouble() * e.getBbHeight(), e.getZ(),
                    (rnd.nextDouble() - 0.5) * 0.1, 0.03, (rnd.nextDouble() - 0.5) * 0.1);
        }
    }

    private static void title(Minecraft mc, Entity e, Race race, int rgb) {
        Component sub = e == mc.player
                ? Component.translatable("message.isekai_mahou.character.form_title_self").withColor(0xE8E0F0)
                : Component.translatable("message.isekai_mahou.character.form_title_other", e.getDisplayName()).withColor(0xE8E0F0);
        mc.gui.setTimes(4, 46, 18);
        mc.gui.setSubtitle(sub);
        mc.gui.setTitle(race.formName().withColor(rgb).withStyle(s -> s.withBold(true)));
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    private static void onTick(ClientTickEvent.Post ev) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            if (fxLevel != null) clear();
            fxLevel = null;
            return;
        }
        syncLevel(mc.level);
        if (mc.isPaused()) return;
        long now = mc.level.getGameTime();
        darkPrev = dark;
        float wantDark = 0F;
        chargeShake = 0F;

        // ---------- cargas ----------
        for (Iterator<Map.Entry<Integer, Charge>> it = CHARGES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Charge> en = it.next();
            Charge c = en.getValue();
            if (now > c.until() + 40) {
                it.remove();
                continue;
            }
            Entity e = mc.level.getEntity(en.getKey());
            if (e == null || !inRange(mc, e)) continue;
            Race race = raceOf(e);
            if (race == null) continue;
            float p = Mth.clamp((now - c.start()) / (float) Math.max(1, c.until() - c.start()), 0F, 1F);
            chargeParticles(mc.level, e, race, color(e, race), p, now - c.start());
            if (mc.player != null) {
                float fall = falloff(mc.player.distanceTo(e), 22F);
                if (fall * p > wantDark) {
                    wantDark = fall * p;
                    darkRgb = color(e, race);
                }
                chargeShake = Math.max(chargeShake, fall * (0.15F + 0.55F * p * p));
            }
        }
        dark = Mth.lerp(0.18F, dark, wantDark * 0.55F);
        if (dark < 0.004F) dark = 0;

        // ---------- explosões (detritos que ficam caindo) ----------
        for (Burst b : BURSTS) {
            long age = now - b.start();
            if (age > BURST_LIFE) {
                BURSTS.remove(b);
                continue;
            }
            if (age > 0 && age < 40 && age % 3 == 0 && inRange(mc, b.pos())) aftermath(mc.level, b, age);
        }
        SHAKES.removeIf(s -> now - s.start() > s.ticks());

        // ---------- desfazer: o corpo se dissipa ----------
        for (Iterator<Map.Entry<Integer, Long>> it = FADES.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> en = it.next();
            long age = now - en.getValue();
            if (age > ClientCharacter.FADE || age < 0) {
                it.remove();
                continue;
            }
            Entity e = mc.level.getEntity(en.getKey());
            Race race = e == null ? null : raceOf(e);
            if (race == null || !inRange(mc, e)) continue;
            int rgb = color(e, race);
            RandomSource rnd = mc.level.random;
            for (int i = 0; i < FxQuality.count(3); i++) {
                mc.level.addParticle(glow(rgb, 0.9F), e.getX() + (rnd.nextDouble() - 0.5) * e.getBbWidth() * 1.4,
                        e.getY() + rnd.nextDouble() * e.getBbHeight(), e.getZ() + (rnd.nextDouble() - 0.5) * e.getBbWidth() * 1.4,
                        0, 0.05 + rnd.nextDouble() * 0.05, 0);
            }
        }

        // ---------- aura das formas ----------
        if (now % 2 == 0) {
            for (Player p : mc.level.players()) {
                if (p.isSpectator()) continue;
                ClientCharacter.Look look = ClientCharacter.look(p.getId());
                if (look == null || look.race() == null || ClientCharacter.form(p.getId()) == null) continue;
                if (!inRange(mc, p) || (p == mc.player && mc.options.getCameraType().isFirstPerson() && now % 4 != 0)) continue;
                ambient(mc.level, p, look.race(), color(p, look.race()), now);
            }
        }
        // ---------- v1.3.1: voo planado (todo tick: o rastro precisa ser contínuo; só jogadores alados) ----------
        for (Player p : mc.level.players()) {
            if (p.isSpectator()) continue;
            Race race = GlideClient.wingedForm(p);
            if (race == null) {
                GLIDING.remove(p.getId());
                continue;
            }
            boolean gliding = p.isFallFlying();
            Boolean was = GLIDING.put(p.getId(), gliding);
            if (!inRange(mc, p)) continue;
            int rgb = color(p, race);
            if (gliding) trail(mc, p, race, rgb, now);
            else if (Boolean.TRUE.equals(was) && p.onGround()) landFx(mc.level, p, race, rgb);
        }
        if (now % 200 == 0) {
            PRINTS.keySet().removeIf(id -> mc.level.getEntity(id) == null);
            GLIDING.keySet().removeIf(id -> mc.level.getEntity(id) == null);
            DIVES.values().removeIf(until -> until < now);
        }
    }

    /**
     * Rastro do voo: fitas de luz nas pontas das asas (interpoladas, para não ficarem pontilhadas em alta velocidade),
     * vento cortando o ar acima de ~1,5 bloco/tick, a assinatura da raça e, no mergulho, um cometa.
     */
    private static void trail(Minecraft mc, Player p, Race race, int rgb, long now) {
        Vec3 vel = new Vec3(p.getX() - p.xo, p.getY() - p.yo, p.getZ() - p.zo);
        double sp = vel.length();
        Long diveUntil = DIVES.get(p.getId());
        boolean diving = diveUntil != null && diveUntil >= now;
        if (sp < 0.55 && !diving) return;
        boolean firstPerson = p == mc.player && mc.options.getCameraType().isFirstPerson();
        if (firstPerson && now % 2 != 0) return;
        ClientLevel level = mc.level;
        RandomSource rnd = level.random;
        Vec3 dir = sp > 0.05 ? vel.scale(1 / sp) : p.getLookAngle();
        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
        double scale = p.getScale();
        double span = 2.0 * scale;
        Vec3 c = p.position().add(0, 0.35 * scale, 0);
        Vec3 back = dir.scale(-0.6 * scale);
        int n = FxQuality.count(Math.min(4, 1 + (int) (sp * 1.5)));
        ParticleOptions ribbon = glow(rgb, (float) Math.min(1.3, 0.55 + sp * 0.25));
        for (int side = -1; side <= 1; side += 2) {
            Vec3 tip = c.add(right.scale(side * span)).add(back);
            for (int i = 0; i < n; i++) {
                Vec3 at = tip.subtract(vel.scale(i / (double) n));
                level.addParticle(ribbon, at.x, at.y, at.z, 0, 0, 0);
            }
            if (now % 2 == 0 && FxQuality.atLeast(Graphics.MEDIUM)) {
                ParticleOptions sig = switch (race) {
                    case DEMON -> rnd.nextInt(3) == 0 ? ParticleTypes.SMOKE : ParticleTypes.SOUL_FIRE_FLAME;
                    case ANGEL -> rnd.nextInt(3) == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.END_ROD;
                    default -> ElementFx.style(elementOf(rgb), 0.7F);
                };
                level.addParticle(sig, tip.x, tip.y, tip.z, -dir.x * 0.05, -dir.y * 0.05, -dir.z * 0.05);
            }
        }
        // Vento cortando o ar
        if (sp > 1.5 && !firstPerson && FxQuality.atLeast(Graphics.MEDIUM)) {
            for (int i = 0; i < FxQuality.count(2); i++) {
                Vec3 o = c.add((rnd.nextDouble() - 0.5) * 1.6, (rnd.nextDouble() - 0.5) * 1.0, (rnd.nextDouble() - 0.5) * 1.6);
                level.addParticle(ParticleTypes.CLOUD, o.x, o.y, o.z, -dir.x * 0.25, -dir.y * 0.25, -dir.z * 0.25);
            }
        }
        // Mergulho: um cometa (núcleo brilhante + fogo/fumaça/luz)
        if (diving) {
            ParticleOptions core = glow(mix(rgb, 0xFFFFFF, 0.3F), 2.0F);
            int k = FxQuality.count(4);
            for (int i = 0; i < k; i++) {
                Vec3 at = c.subtract(vel.scale(i / (double) k));
                level.addParticle(core, at.x + (rnd.nextDouble() - 0.5) * 0.4, at.y + (rnd.nextDouble() - 0.5) * 0.4,
                        at.z + (rnd.nextDouble() - 0.5) * 0.4, 0, 0, 0);
            }
            ParticleOptions tail = switch (race) {
                case DEMON -> ParticleTypes.SOUL_FIRE_FLAME;
                case ANGEL -> ParticleTypes.END_ROD;
                default -> ParticleTypes.FLAME;
            };
            for (int i = 0; i < FxQuality.count(3); i++) {
                level.addParticle(tail, c.x + (rnd.nextDouble() - 0.5) * 0.8, c.y + (rnd.nextDouble() - 0.5) * 0.8,
                        c.z + (rnd.nextDouble() - 0.5) * 0.8, -dir.x * 0.1, -dir.y * 0.1, -dir.z * 0.1);
            }
            if (FxQuality.atLeast(Graphics.HIGH) && race != Race.ANGEL) {
                level.addParticle(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, -dir.x * 0.05, 0.02, -dir.z * 0.05);
            }
        }
    }

    /** Pouso depois de planar: as asas fecham levantando poeira. */
    private static void landFx(ClientLevel level, Player p, Race race, int rgb) {
        RandomSource rnd = level.random;
        int n = FxQuality.count(14);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            level.addParticle(ParticleTypes.CLOUD, p.getX() + Math.cos(a) * 0.5, p.getY() + 0.1, p.getZ() + Math.sin(a) * 0.5,
                    Math.cos(a) * 0.3, 0.02, Math.sin(a) * 0.3);
        }
        BlockState ground = level.getBlockState(BlockPos.containing(p.getX(), p.getY() - 0.2, p.getZ()));
        if (!ground.isAir()) {
            BlockParticleOption crumb = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            for (int i = 0; i < FxQuality.count(10); i++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = 0.3 + rnd.nextDouble() * 1.2;
                level.addParticle(crumb, p.getX() + Math.cos(a) * r, p.getY() + 0.1, p.getZ() + Math.sin(a) * r, 0, 0.15, 0);
            }
        }
        for (int i = 0; i < FxQuality.count(6); i++) {
            level.addParticle(glow(rgb, 1.0F), p.getX() + (rnd.nextDouble() - 0.5) * 2, p.getY() + 1.4 * p.getScale(),
                    p.getZ() + (rnd.nextDouble() - 0.5) * 2, 0, -0.03, 0);
        }
        level.playLocalSound(p.getX(), p.getY(), p.getZ(), RaceFlight.flapSound(race), SoundSource.PLAYERS, 0.6F,
                RaceFlight.flapPitch(race) * 0.8F, false);
    }

    /** Soco de FOV quando o jogador local se transforma: fecha rápido e volta devagar (multiplicador, 1 = nada). */
    static float fovPunch() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 1F;
        long age = mc.level.getGameTime() - selfBurstAt;
        if (age < 0 || age > 24) return 1F;
        float k = age < 3 ? age / 3F : 1F - (age - 3) / 21F;
        return 1F - 0.14F * k * k * FxQuality.shake();
    }

    /** Elemento da cor da ancestralidade (o cliente só recebe a cor); desconhecida = Fogo. */
    static Element elementOf(int rgb) {
        for (Element el : Element.values()) {
            if ((el.color & 0xFFFFFF) == (rgb & 0xFFFFFF)) return el;
        }
        return Element.FIRE;
    }

    /** Energia convergindo, chão tremendo/rachando, faíscas subindo. {@code p} = progresso 0..1. */
    private static void chargeParticles(ClientLevel level, Entity e, Race race, int rgb, float p, long age) {
        FormAura.charge(level, e, race, rgb, p, age);
        RandomSource rnd = level.random;
        Vec3 c = e.position().add(0, e.getBbHeight() * 0.55, 0);
        // Convergindo para o peito (as partículas de encantamento/portal voam do deslocamento até o ponto)
        int n = FxQuality.count(2 + (int) (6 * p));
        ParticleOptions pull = pullParticle(race);
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = 2.5 + rnd.nextDouble() * 2.0;
            double dy = (rnd.nextDouble() - 0.4) * 2.5;
            if (pull == ParticleTypes.ENCHANT || pull == ParticleTypes.PORTAL) {
                level.addParticle(pull, c.x, c.y, c.z, Math.cos(a) * r, dy, Math.sin(a) * r);
            } else {
                Vec3 from = c.add(Math.cos(a) * r, dy, Math.sin(a) * r);
                Vec3 v = c.subtract(from).scale(0.09);
                level.addParticle(pull, from.x, from.y, from.z, v.x, v.y, v.z);
            }
        }
        // Espiral colorida subindo pelo corpo
        if (age % 2 == 0) {
            for (int i = 0; i < FxQuality.count(3); i++) {
                double a = age * 0.45 + i * Math.PI * 2 / 3;
                double r = 0.9 - 0.3 * p;
                level.addParticle(glow(rgb, 1.1F + p), e.getX() + Math.cos(a) * r, e.getY() + (age % 20) / 20.0 * e.getBbHeight() * 1.2,
                        e.getZ() + Math.sin(a) * r, 0, 0.06, 0);
            }
        }
        // O chão treme: pedaços do bloco de baixo pulando em anel
        if (age % 3 == 0) {
            BlockState ground = level.getBlockState(BlockPos.containing(e.getX(), e.getY() - 0.2, e.getZ()));
            if (!ground.isAir()) {
                BlockParticleOption crumb = new BlockParticleOption(ParticleTypes.BLOCK, ground);
                int k = FxQuality.count(3 + (int) (6 * p));
                for (int i = 0; i < k; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2;
                    double r = 0.8 + rnd.nextDouble() * (1.5 + 2.5 * p);
                    level.addParticle(crumb, e.getX() + Math.cos(a) * r, e.getY() + 0.1, e.getZ() + Math.sin(a) * r, 0,
                            0.15 + p * 0.25, 0);
                }
            }
        }
        // Rugido (depois de 1 s): poeira empurrada para fora em ondas
        if (age >= 20 && age % 8 == 0) {
            int k = FxQuality.count(14);
            for (int i = 0; i < k; i++) {
                double a = Math.PI * 2 * i / k;
                level.addParticle(ParticleTypes.CLOUD, e.getX() + Math.cos(a) * 0.8, e.getY() + 0.15, e.getZ() + Math.sin(a) * 0.8,
                        Math.cos(a) * 0.35, 0.01, Math.sin(a) * 0.35);
            }
        }
        // Anjo: luz caindo do céu na coluna; Demônio: brasas subindo do chão
        if (race == Race.ANGEL && age % 2 == 0) {
            for (int i = 0; i < FxQuality.count(2); i++) {
                level.addParticle(ParticleTypes.END_ROD, e.getX() + (rnd.nextDouble() - 0.5) * 1.6, e.getY() + 8 + rnd.nextDouble() * 6,
                        e.getZ() + (rnd.nextDouble() - 0.5) * 1.6, 0, -0.6, 0);
            }
        } else if (race == Race.DEMON && age % 2 == 0) {
            for (int i = 0; i < FxQuality.count(3); i++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * 3;
                level.addParticle(rnd.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SOUL_FIRE_FLAME, e.getX() + Math.cos(a) * r,
                        e.getY() + 0.1, e.getZ() + Math.sin(a) * r, 0, 0.08 + rnd.nextDouble() * 0.1, 0);
            }
        }
    }

    /** Explosão: anel de assinatura, detritos voando, coluna. Uma vez só. */
    private static void burstParticles(ClientLevel level, Entity e, Race race, int rgb) {
        RandomSource rnd = level.random;
        double x = e.getX(), y = e.getY(), z = e.getZ();
        double h = e.getBbHeight();
        level.addParticle(ParticleTypes.FLASH, x, y + h * 0.5, z, 0, 0, 0);
        if (FxQuality.atLeast(Graphics.HIGH)) level.addParticle(ParticleTypes.EXPLOSION_EMITTER, x, y + 0.5, z, 0, 0, 0);
        else level.addParticle(ParticleTypes.EXPLOSION, x, y + 0.5, z, 0, 0, 0);
        // Onda de poeira correndo pelo chão
        int ring = FxQuality.count(48);
        for (int i = 0; i < ring; i++) {
            double a = Math.PI * 2 * i / ring;
            level.addParticle(ParticleTypes.CLOUD, x + Math.cos(a) * 0.6, y + 0.2, z + Math.sin(a) * 0.6, Math.cos(a) * 0.9, 0.02,
                    Math.sin(a) * 0.9);
            level.addParticle(glow(rgb, 1.6F), x + Math.cos(a) * 0.8, y + 0.4, z + Math.sin(a) * 0.8, Math.cos(a) * 0.6, 0.05,
                    Math.sin(a) * 0.6);
        }
        // Detritos do chão voando
        BlockState ground = level.getBlockState(BlockPos.containing(x, y - 0.2, z));
        if (!ground.isAir()) {
            BlockParticleOption crumb = new BlockParticleOption(ParticleTypes.BLOCK, ground);
            for (int i = 0; i < FxQuality.count(40); i++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = 0.5 + rnd.nextDouble() * 3.5;
                level.addParticle(crumb, x + Math.cos(a) * r, y + 0.2, z + Math.sin(a) * r, Math.cos(a) * 0.3, 0.4 + rnd.nextDouble() * 0.5,
                        Math.sin(a) * 0.3);
            }
        }
        // Coluna subindo
        for (int i = 0; i < FxQuality.count(30); i++) {
            level.addParticle(glow(rgb, 2.0F), x + (rnd.nextDouble() - 0.5) * 0.8, y + rnd.nextDouble() * 2, z + (rnd.nextDouble() - 0.5) * 0.8,
                    0, 0.5 + rnd.nextDouble() * 0.6, 0);
        }
        // v1.3.1: as formas aladas abrem as asas num golpe de vento (lufada na altura dos ombros)
        if (RaceFlight.winged(race)) {
            int g = FxQuality.count(28);
            for (int i = 0; i < g; i++) {
                double a = Math.PI * 2 * i / g;
                level.addParticle(ParticleTypes.CLOUD, x + Math.cos(a) * 1.2, y + h * 0.75, z + Math.sin(a) * 1.2, Math.cos(a) * 0.7, -0.04,
                        Math.sin(a) * 0.7);
                if (i % 2 == 0) level.addParticle(glow(rgb, 1.4F), x + Math.cos(a) * 2.2, y + h * 0.8, z + Math.sin(a) * 2.2, Math.cos(a) * 0.4,
                        0.02, Math.sin(a) * 0.4);
            }
        }
        // Assinatura da raça
        int n = FxQuality.count(36);
        switch (race) {
            case DEMON -> {
                for (int i = 0; i < n; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, s = 0.2 + rnd.nextDouble() * 0.5;
                    level.addParticle(i % 3 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, x, y + 0.3, z, Math.cos(a) * s,
                            0.1 + rnd.nextDouble() * 0.3, Math.sin(a) * s);
                    if (i % 2 == 0) level.addParticle(ParticleTypes.LARGE_SMOKE, x, y + h * 0.5, z, Math.cos(a) * s * 0.5, 0.1, Math.sin(a) * s * 0.5);
                }
            }
            case DRAGONBORN -> {
                for (int i = 0; i < n; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, s = 0.3 + rnd.nextDouble() * 0.4;
                    level.addParticle(ParticleTypes.DRAGON_BREATH, x, y + h * 0.6, z, Math.cos(a) * s, (rnd.nextDouble() - 0.3) * 0.2, Math.sin(a) * s);
                    if (i % 2 == 0) level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x + Math.cos(a), y + 0.2, z + Math.sin(a), 0, 0.05, 0);
                }
            }
            case ANGEL -> {
                for (int i = 0; i < n; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, s = 0.15 + rnd.nextDouble() * 0.35;
                    level.addParticle(ParticleTypes.END_ROD, x, y + h * 0.6, z, Math.cos(a) * s, (rnd.nextDouble() - 0.2) * 0.3, Math.sin(a) * s);
                    level.addParticle(ParticleTypes.WHITE_ASH, x + Math.cos(a) * 2, y + h + rnd.nextDouble() * 2, z + Math.sin(a) * 2, 0, -0.02, 0);
                }
            }
            case ELF -> {
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    level.addParticle(ParticleTypes.ENCHANT, x + Math.cos(a) * 3, y + 0.3, z + Math.sin(a) * 3, -Math.cos(a) * 3, 1.5, -Math.sin(a) * 3);
                    level.addParticle(ParticleTypes.HAPPY_VILLAGER, x + Math.cos(a) * 1.5, y + rnd.nextDouble() * h, z + Math.sin(a) * 1.5, 0, 0.05, 0);
                }
            }
            case DWARF -> {
                for (int i = 0; i < n; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * 3;
                    level.addParticle(ParticleTypes.LAVA, x + Math.cos(a) * r, y + 0.3, z + Math.sin(a) * r, 0, 0, 0);
                    if (i % 2 == 0) level.addParticle(ParticleTypes.LARGE_SMOKE, x + Math.cos(a) * r, y + 0.3, z + Math.sin(a) * r, 0, 0.08, 0);
                }
            }
            case HALF_ORC -> {
                for (int i = 0; i < n; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, s = 0.1 + rnd.nextDouble() * 0.3;
                    level.addParticle(dust(0x8A0A10, 2.2F), x + Math.cos(a) * 0.5, y + rnd.nextDouble() * h, z + Math.sin(a) * 0.5,
                            Math.cos(a) * s, 0.02, Math.sin(a) * s);
                    if (i % 3 == 0) level.addParticle(ParticleTypes.CRIMSON_SPORE, x, y + h * 0.5, z, Math.cos(a) * s, 0.1, Math.sin(a) * s);
                }
            }
            case BEASTFOLK -> {
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    level.addParticle(ParticleTypes.SWEEP_ATTACK, x + Math.cos(a) * 1.6, y + 1.0, z + Math.sin(a) * 1.6, 0, 0, 0);
                    if (i % 2 == 0) level.addParticle(ParticleTypes.CRIT, x, y + h * 0.6, z, Math.cos(a) * 0.8, 0.2, Math.sin(a) * 0.8);
                }
                if (FxQuality.atLeast(Graphics.MEDIUM)) level.addParticle(ParticleTypes.SONIC_BOOM, x, y + h * 0.6, z, 0, 0, 0);
            }
            case ARACHNID -> {
                BlockParticleOption web = new BlockParticleOption(ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.COBWEB.defaultBlockState());
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    for (int k = 1; k <= 4; k++) {
                        level.addParticle(dust(0xF4F4F4, 0.7F), x + Math.cos(a) * k * 1.1, y + 0.6 + k * 0.08, z + Math.sin(a) * k * 1.1, 0, 0, 0);
                    }
                    level.addParticle(web, x + Math.cos(a) * 2, y + 1, z + Math.sin(a) * 2, Math.cos(a) * 0.3, 0.2, Math.sin(a) * 0.3);
                }
            }
            case PHANTOM -> {
                for (int i = 0; i < n; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, s = 0.1 + rnd.nextDouble() * 0.3;
                    level.addParticle(ParticleTypes.SCULK_SOUL, x, y + h * 0.5, z, Math.cos(a) * s, 0.05, Math.sin(a) * s);
                    level.addParticle(ParticleTypes.SNOWFLAKE, x + Math.cos(a) * 2, y + 0.3, z + Math.sin(a) * 2, Math.cos(a) * 0.2, 0.02, Math.sin(a) * 0.2);
                }
            }
            case SLIME -> {
                for (int i = 0; i < n; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, s = 0.2 + rnd.nextDouble() * 0.5;
                    level.addParticle(ParticleTypes.ITEM_SLIME, x, y + h * 0.4, z, Math.cos(a) * s, 0.3 + rnd.nextDouble() * 0.3, Math.sin(a) * s);
                }
            }
            case HUMAN -> {
                for (int i = 0; i < n; i++) {
                    double a = rnd.nextDouble() * Math.PI * 2, s = 0.2 + rnd.nextDouble() * 0.4;
                    level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, x, y + h * 0.5, z, Math.cos(a) * s, 0.2 + rnd.nextDouble() * 0.3, Math.sin(a) * s);
                    if (i % 2 == 0) level.addParticle(ParticleTypes.END_ROD, x, y + 0.2, z, Math.cos(a) * 0.05, 0.6, Math.sin(a) * 0.05);
                }
            }
        }
    }

    /** Depois da explosão: detritos caindo e fumaça baixa nas rachaduras. */
    private static void aftermath(ClientLevel level, Burst b, long age) {
        RandomSource rnd = level.random;
        int n = FxQuality.count(3);
        for (int i = 0; i < n; i++) {
            float[] cr = b.cracks()[rnd.nextInt(b.cracks().length)];
            int k = rnd.nextInt(cr.length / 2) * 2;
            double px = b.pos().x + cr[k], pz = b.pos().z + cr[k + 1];
            ParticleOptions o = switch (b.race()) {
                case DEMON, DWARF -> rnd.nextInt(3) == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.SMOKE;
                case PHANTOM -> ParticleTypes.SNOWFLAKE;
                case ANGEL, HUMAN -> ParticleTypes.END_ROD;
                default -> glow(b.rgb(), 0.8F);
            };
            level.addParticle(o, px, b.pos().y + 0.1, pz, 0, 0.03, 0);
        }
        if (age < 20) {
            for (int i = 0; i < FxQuality.count(2); i++) {
                level.addParticle(ParticleTypes.ASH, b.pos().x + (rnd.nextDouble() - 0.5) * 8, b.pos().y + 3 + rnd.nextDouble() * 3,
                        b.pos().z + (rnd.nextDouble() - 0.5) * 8, 0, -0.05, 0);
            }
        }
    }

    /** Aura constante das formas (a cada 2 ticks, só jogadores no alcance). */
    private static void ambient(ClientLevel level, Player p, Race race, int rgb, long now) {
        FormAura.ambient(level, p, race, rgb, now);
        RandomSource rnd = level.random;
        double x = p.getX(), y = p.getY(), z = p.getZ(), h = p.getBbHeight(), w = p.getBbWidth();
        boolean moving = p.getDeltaMovement().horizontalDistanceSqr() > 0.002;
        Vec3 eye = p.getEyePosition();
        switch (race) {
            case DEMON -> {
                wingTip(level, p, ParticleTypes.SOUL_FIRE_FLAME, 0.01);
                // Fogo do inferno em volta + fumaça; pegadas em brasa no chão
                for (int i = 0; i < FxQuality.count(2); i++) {
                    level.addParticle(rnd.nextInt(3) == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                            x + (rnd.nextDouble() - 0.5) * w * 1.6, y + rnd.nextDouble() * h * 0.6, z + (rnd.nextDouble() - 0.5) * w * 1.6, 0, 0.03, 0);
                }
                if (rnd.nextInt(3) == 0) level.addParticle(ParticleTypes.LARGE_SMOKE, x, y + h * 0.9, z, 0, 0.03, 0);
                Vec3 last = PRINTS.get(p.getId());
                if (p.onGround() && (last == null || last.distanceToSqr(p.position()) > 0.8)) {
                    PRINTS.put(p.getId(), p.position());
                    for (int i = 0; i < FxQuality.count(3); i++) {
                        level.addParticle(ParticleTypes.SMALL_FLAME, x + (rnd.nextDouble() - 0.5) * 0.4, y + 0.05, z + (rnd.nextDouble() - 0.5) * 0.4, 0, 0.005, 0);
                    }
                    level.addParticle(ParticleTypes.LAVA, x, y + 0.05, z, 0, 0, 0);
                }
            }
            case DRAGONBORN -> {
                level.addParticle(glow(rgb, 1.0F), x + (rnd.nextDouble() - 0.5) * w * 1.5, y + rnd.nextDouble() * h, z + (rnd.nextDouble() - 0.5) * w * 1.5, 0, 0.02, 0);
                wingTip(level, p, ElementFx.style(elementOf(rgb), 0.7F), 0.01);
                // Passos pesados: o chão estala
                if (moving && p.onGround() && now % 8 == 0) {
                    BlockState g = level.getBlockState(p.blockPosition().below());
                    if (!g.isAir()) {
                        BlockParticleOption crumb = new BlockParticleOption(ParticleTypes.BLOCK, g);
                        for (int i = 0; i < FxQuality.count(4); i++) {
                            level.addParticle(crumb, x + (rnd.nextDouble() - 0.5) * 0.8, y + 0.05, z + (rnd.nextDouble() - 0.5) * 0.8, 0, 0.12, 0);
                        }
                    }
                }
                // Fumaça saindo das narinas, de tempos em tempos
                if (now % 30 < 6) {
                    Vec3 look = p.getLookAngle();
                    Vec3 m = eye.add(look.scale(0.35)).add(0, -0.18, 0);
                    level.addParticle(ParticleTypes.SMOKE, m.x, m.y, m.z, look.x * 0.06, 0.02, look.z * 0.06);
                    if (FxQuality.atLeast(Graphics.HIGH)) level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, m.x, m.y, m.z, look.x * 0.02, 0.01, look.z * 0.02);
                }
            }
            case ANGEL -> {
                wingTip(level, p, ParticleTypes.END_ROD, -0.02);
                level.addParticle(ParticleTypes.END_ROD, x + (rnd.nextDouble() - 0.5) * 2.5, y + h * (0.4 + rnd.nextDouble() * 0.7), z + (rnd.nextDouble() - 0.5) * 2.5, 0, -0.01, 0);
                if (rnd.nextInt(2) == 0) level.addParticle(ParticleTypes.WHITE_ASH, x + (rnd.nextDouble() - 0.5) * 3, y + h, z + (rnd.nextDouble() - 0.5) * 3, 0, -0.02, 0);
                if (now % 10 == 0) level.addParticle(glow(0xFFF2B0, 1.4F), x, y + h + 0.4, z, 0, 0, 0);
            }
            case ELF -> {
                // Runas da lua orbitando e flutuando
                for (int i = 0; i < FxQuality.count(2); i++) {
                    double a = now * 0.12 + i * Math.PI;
                    level.addParticle(ParticleTypes.ENCHANT, x + Math.cos(a) * 1.2, y + h * 0.5 + Math.sin(now * 0.07 + i) * 0.4, z + Math.sin(a) * 1.2, 0, 0.1, 0);
                }
                level.addParticle(glow(0xCFF2FF, 0.9F), x + (rnd.nextDouble() - 0.5) * 1.6, y + rnd.nextDouble() * h, z + (rnd.nextDouble() - 0.5) * 1.6, 0, 0.015, 0);
                if (rnd.nextInt(4) == 0) level.addParticle(glow(0x7BD88F, 0.7F), x, y - 0.2, z, (rnd.nextDouble() - 0.5) * 0.05, -0.01, (rnd.nextDouble() - 0.5) * 0.05);
            }
            case DWARF -> {
                // Gotas de magma das rachaduras + brasas
                if (rnd.nextInt(2) == 0) level.addParticle(ParticleTypes.FALLING_LAVA, x + (rnd.nextDouble() - 0.5) * w, y + h * (0.3 + rnd.nextDouble() * 0.5), z + (rnd.nextDouble() - 0.5) * w, 0, 0, 0);
                if (rnd.nextInt(3) == 0) level.addParticle(ParticleTypes.SMALL_FLAME, x + (rnd.nextDouble() - 0.5) * w, y + rnd.nextDouble() * h, z + (rnd.nextDouble() - 0.5) * w, 0, 0.02, 0);
                if (moving && p.onGround() && now % 6 == 0) {
                    BlockState g = level.getBlockState(p.blockPosition().below());
                    if (!g.isAir()) level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, g), x, y + 0.1, z, 0, 0.1, 0);
                }
            }
            case HALF_ORC -> {
                // Névoa de sangue
                for (int i = 0; i < FxQuality.count(2); i++) {
                    level.addParticle(dust(rnd.nextBoolean() ? 0x9A0E14 : 0x5A0508, 1.8F), x + (rnd.nextDouble() - 0.5) * w * 2,
                            y + rnd.nextDouble() * h, z + (rnd.nextDouble() - 0.5) * w * 2, 0, 0, 0);
                }
                if (rnd.nextInt(6) == 0) level.addParticle(ParticleTypes.CRIMSON_SPORE, x, y + h * 0.6, z, 0, 0, 0);
            }
            case BEASTFOLK -> {
                // Rastro dos olhos brilhando quando corre
                if (moving) level.addParticle(glow(0xFFD24A, 0.6F), eye.x, eye.y, eye.z, 0, 0, 0);
                if (moving && p.onGround() && rnd.nextInt(2) == 0) level.addParticle(ParticleTypes.CLOUD, x, y + 0.1, z, 0, 0.01, 0);
                if (rnd.nextInt(3) == 0) level.addParticle(glow(rgb, 0.8F), x + (rnd.nextDouble() - 0.5) * w * 1.5, y + rnd.nextDouble() * h, z + (rnd.nextDouble() - 0.5) * w * 1.5, 0, 0.02, 0);
            }
            case ARACHNID -> {
                if (rnd.nextInt(2) == 0) level.addParticle(glow(rgb, 0.8F), x + (rnd.nextDouble() - 0.5) * w * 1.6, y + rnd.nextDouble() * h, z + (rnd.nextDouble() - 0.5) * w * 1.6, 0, 0.01, 0);
                if (rnd.nextInt(5) == 0) level.addParticle(ParticleTypes.FALLING_OBSIDIAN_TEAR, x + (rnd.nextDouble() - 0.5) * w, y + h * 0.4, z + (rnd.nextDouble() - 0.5) * w, 0, 0, 0);
            }
            case PHANTOM -> {
                // Névoa gelada no chão + almas
                for (int i = 0; i < FxQuality.count(2); i++) {
                    level.addParticle(ParticleTypes.WHITE_SMOKE, x + (rnd.nextDouble() - 0.5) * 1.8, y + 0.1, z + (rnd.nextDouble() - 0.5) * 1.8,
                            (rnd.nextDouble() - 0.5) * 0.02, 0.005, (rnd.nextDouble() - 0.5) * 0.02);
                }
                if (rnd.nextInt(2) == 0) level.addParticle(ParticleTypes.SNOWFLAKE, x + (rnd.nextDouble() - 0.5) * w * 2, y + rnd.nextDouble() * h, z + (rnd.nextDouble() - 0.5) * w * 2, 0, -0.01, 0);
                if (rnd.nextInt(4) == 0) level.addParticle(ParticleTypes.SOUL, x, y + 0.3, z, 0, 0.03, 0);
            }
            case SLIME -> {
                if (rnd.nextInt(2) == 0) level.addParticle(ParticleTypes.ITEM_SLIME, x + (rnd.nextDouble() - 0.5) * w * 1.2, y + rnd.nextDouble() * h, z + (rnd.nextDouble() - 0.5) * w * 1.2, 0, 0, 0);
                if (rnd.nextInt(4) == 0) level.addParticle(glow(rgb, 1.3F), x + (rnd.nextDouble() - 0.5) * w, y + rnd.nextDouble() * h * 0.8, z + (rnd.nextDouble() - 0.5) * w, 0, 0.03, 0);
            }
            case HUMAN -> {
                level.addParticle(glow(0xFFD86B, 1.0F), x + (rnd.nextDouble() - 0.5) * w * 1.8, y + rnd.nextDouble() * h, z + (rnd.nextDouble() - 0.5) * w * 1.8, 0, 0.04, 0);
                if (rnd.nextInt(3) == 0) level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + (rnd.nextDouble() - 0.5) * w * 1.6, y + rnd.nextDouble() * h, z + (rnd.nextDouble() - 0.5) * w * 1.6, 0, 0.05, 0);
            }
        }
    }

    /** Uma faísca numa das pontas das asas abertas (no chão; planando, o rastro é do {@link #trail}). */
    private static void wingTip(ClientLevel level, Player p, ParticleOptions o, double vy) {
        if (p.isFallFlying()) return;
        RandomSource rnd = level.random;
        double s = p.getScale();
        float yb = p.yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yb), fz = Mth.cos(yb);    // frente
        double rx = -Mth.cos(yb), rz = -Mth.sin(yb);   // direita
        double side = rnd.nextBoolean() ? 1 : -1;
        double up = p.onGround() ? 1.9 : 1.6;
        level.addParticle(o, p.getX() + rx * side * 1.6 * s - fx * 0.9 * s, p.getY() + up * s, p.getZ() + rz * side * 1.6 * s - fz * 0.9 * s,
                0, vy, 0);
    }

    // =====================================================================================
    //  Câmera: tremor da carga e da explosão
    // =====================================================================================
    private static void onCamera(ViewportEvent.ComputeCameraAngles e) {
        if (SHAKES.isEmpty() && chargeShake <= 0.01F) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        double now = mc.level.getGameTime() + e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        float k = chargeShake;
        for (Shake s : SHAKES) {
            double age = now - s.start();
            if (age < 0 || age > s.ticks()) continue;
            double fade = 1 - age / s.ticks();
            k += (float) (s.intensity() * fade * fade * falloff((float) cam.distanceTo(s.pos()), s.radius()));
        }
        if (k < 0.01F) return;
        k = Math.min(k, 3.0F) * com.renanpontes.isekaimahou.core.MahouConfig.shakeScale();
        if (!FxQuality.atLeast(Graphics.MEDIUM)) k *= 0.5F;
        e.setPitch(e.getPitch() + (float) ((Math.sin(now * 1.9) * 0.6 + Math.sin(now * 3.7 + 1.1) * 0.4) * k));
        e.setYaw(e.getYaw() + (float) ((Math.sin(now * 1.4 + 0.5) * 0.6 + Math.sin(now * 3.3 + 2.0) * 0.4) * k * 0.7));
        e.setRoll(e.getRoll() + (float) (Math.sin(now * 2.5 + 0.3) * k * 0.6));
    }

    // =====================================================================================
    //  Tela: vinheta escura na carga e clarão na explosão
    // =====================================================================================
    private static void renderOverlay(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        float pt = dt.getGameTimeDeltaPartialTick(false);
        int w = g.guiWidth(), h = g.guiHeight();
        float a = Mth.lerp(pt, darkPrev, dark);
        if (a > 0.004F) {
            int tint = mix(0x07040C, darkRgb, 0.18F);
            int center = alpha(a * 0.45F) | tint;
            int edge = alpha(Math.min(0.95F, a * 1.5F)) | tint;
            g.fill(0, 0, w, h, center);
            int band = Math.max(10, h / 4);
            g.fillGradient(0, 0, w, band, edge, tint);
            g.fillGradient(0, h - band, w, h, tint, edge);
            // Laterais em degraus (o fillGradient é só vertical)
            int side = Math.max(10, w / 6);
            for (int i = 0; i < 6; i++) {
                int x0 = side * i / 6, x1 = side * (i + 1) / 6;
                int col = alpha(Math.min(0.95F, a * 1.5F) * (1F - i / 6F)) | tint;
                g.fill(x0, 0, x1, h, col);
                g.fill(w - x1, 0, w - x0, h, col);
            }
        }
        double age = mc.level.getGameTime() + pt - flashStart;
        if (age >= 0 && age < 10) {
            float f = (float) (1 - age / 10.0);
            float max = FxQuality.atLeast(Graphics.MEDIUM) ? 0.75F : 0.45F;
            g.fill(0, 0, w, h, alpha(f * f * max * flashPower) | (flashRgb & 0xFFFFFF));
        }
    }

    // =====================================================================================
    //  Corpo erguido na carga; Elfo flutuando na forma
    // =====================================================================================
    private static void preRender(RenderPlayerEvent.Pre e) {
        Player p = e.getEntity();
        float lift = lift(p, e.getPartialTick());
        if (lift <= 0.001F) {
            LIFTED.push(Boolean.FALSE);
            return;
        }
        e.getPoseStack().pushPose();
        e.getPoseStack().translate(0, lift, 0);
        LIFTED.push(Boolean.TRUE);
    }

    private static void postRender(RenderPlayerEvent.Post e) {
        Boolean pushed = LIFTED.poll();
        if (Boolean.TRUE.equals(pushed)) e.getPoseStack().popPose();
    }

    /** Progresso (0..1) da carga da Transformação na entidade; −1 = não está carregando. */
    static float chargeProgress(Entity e, float pt) {
        Charge c = CHARGES.get(e.getId());
        if (c == null) return -1F;
        return Mth.clamp((e.level().getGameTime() - c.start() + pt) / (float) Math.max(1, c.until() - c.start()), 0F, 1F);
    }

    /** Quanto o corpo sobe (blocos): até 0,45 na carga; o Elfo transformado flutua balançando. */
    private static float lift(Player p, float pt) {
        Charge c = CHARGES.get(p.getId());
        long now = p.level().getGameTime();
        if (c != null) {
            float t = Mth.clamp((now - c.start() + pt) / (float) Math.max(1, c.until() - c.start()), 0F, 1F);
            return 0.45F * (float) Math.sin(Math.min(1F, t * 1.15F) * Math.PI * 0.5);
        }
        ClientCharacter.Fx form = ClientCharacter.form(p.getId());
        if (form == null) return 0F;
        ClientCharacter.Look look = ClientCharacter.look(p.getId());
        if (look == null || look.race() != Race.ELF) return 0F;
        float open = Mth.clamp((now - form.start() + pt) / 10F, 0F, 1F);
        long left = form.until() - now;
        if (left < 10) open = Math.min(open, Math.max(0F, (left - pt) / 10F));
        return open * (0.3F + 0.08F * Mth.sin((p.tickCount + pt) * 0.1F));
    }

    // =====================================================================================
    //  Mundo: rachaduras, círculo da carga, onda de choque, pilares
    // =====================================================================================
    private static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (CHARGES.isEmpty() && BURSTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = mc.isPaused() ? 0F : e.getPartialTick().getGameTimeDeltaPartialTick(false);
        double now = mc.level.getGameTime() + pt;
        Vec3 cam = e.getCamera().getPosition();
        double range = FxQuality.range() + 16;
        boolean rich = FxQuality.atLeast(Graphics.HIGH);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;

        for (Map.Entry<Integer, Charge> en : CHARGES.entrySet()) {
            Entity ent = mc.level.getEntity(en.getKey());
            if (ent == null || ent.position().distanceToSqr(cam) > range * range) continue;
            Race race = raceOf(ent);
            if (race == null) continue;
            Charge c = en.getValue();
            float p = (float) Mth.clamp((now - c.start()) / Math.max(1, c.until() - c.start()), 0, 1);
            Vec3 pos = ent.getPosition(pt);
            int rgb = color(ent, race);
            double gy = pos.y + 0.03;
            // Rachaduras crescendo
            drawCracks(buf, cam, pos.x, gy, pos.z, c.cracks(), 0.25F + 0.45F * p, rgb, 0.35F + 0.5F * p);
            // Círculo no chão girando
            float spin = (float) (now * 0.05);
            ring(buf, cam, pos.x, gy + 0.01, pos.z, 2.4F, 0.12F, 48, spin, true, rgb, 0.7F * p);
            ring(buf, cam, pos.x, gy + 0.01, pos.z, 1.6F + 0.2F * Mth.sin((float) now * 0.4F), 0.06F, 32, -spin, false, rgb, 0.5F * p);
            // Coluna de energia
            wall(buf, cam, pos.x, pos.y, pos.z, 0.55F - 0.15F * p, 2.2F + 4F * p, 16, rgb, 0.35F * p, 0F);
            if (race == Race.ANGEL || race == Race.HUMAN) {
                // Pilar descendo do céu
                float top = 40F;
                float reach = top * (1F - p);
                wall(buf, cam, pos.x, pos.y + reach, pos.z, 1.1F, top - reach, 20, mix(rgb, 0xFFFFFF, 0.5F), 0.45F * p, 0.45F * p);
            }
            any = true;
        }

        for (Burst b : BURSTS) {
            if (b.pos().distanceToSqr(cam) > range * range) continue;
            double age = now - b.start();
            if (age < 0 || age > BURST_LIFE) continue;
            double gy = b.pos().y + 0.03;
            // Rachaduras: crescem até o tamanho todo e ficam brilhando, apagando no fim
            float grow = (float) Math.min(1, 0.7 + age / 6.0 * 0.3);
            float glow = age < 40 ? 1F : (float) Math.max(0, 1 - (age - 40) / (BURST_LIFE - 40));
            drawCracks(buf, cam, b.pos().x, gy, b.pos().z, b.cracks(), grow, b.rgb(), 0.95F * glow);
            if (age < RING_LIFE) {
                float t = (float) (age / RING_LIFE);
                float ease = 1 - (1 - t) * (1 - t);
                float r = RING_RADIUS * (b.small() ? 0.7F : 1F) * ease;
                float fade = 1 - t;
                int white = mix(b.rgb(), 0xFFFFFF, 0.5F);
                // Onda de choque: anel no chão + parede que cai
                ring(buf, cam, b.pos().x, gy + 0.02, b.pos().z, r, 0.25F + 0.5F * fade, 64, 0F, false, white, 0.9F * fade);
                wall(buf, cam, b.pos().x, b.pos().y, b.pos().z, r, 1.8F * fade + 0.2F, 64, b.rgb(), 0.55F * fade, 0F);
                if (rich) ring(buf, cam, b.pos().x, gy + 0.03, b.pos().z, r * 0.6F, 0.15F, 48, 0F, false, b.rgb(), 0.6F * fade);
            }
            if (age < 16 && !b.small()) {
                // Pilar da explosão
                float t = (float) (age / 16);
                float fade = (1 - t) * (1 - t);
                wall(buf, cam, b.pos().x, b.pos().y, b.pos().z, 0.8F + 1.6F * t, 34F, 24, mix(b.rgb(), 0xFFFFFF, 0.35F), 0.6F * fade, 0.2F * fade);
                if (rich) wall(buf, cam, b.pos().x, b.pos().y, b.pos().z, 0.4F + 0.5F * t, 40F, 16, 0xFFFFFF, 0.7F * fade, 0.3F * fade);
            }
            any = true;
        }
        // Sempre fecha o buffer (sem vértices o build devolve null)
        MeshData mesh = buf.build();
        if (mesh != null && any) GLOW.draw(mesh);
        else if (mesh != null) mesh.close();
    }

    private static void drawCracks(BufferBuilder buf, Vec3 cam, double cx, double y, double cz, float[][] cracks, float grow,
                                   int rgb, float alpha) {
        if (alpha <= 0.01F) return;
        int hot = mix(rgb, 0xFFFFFF, 0.35F);
        for (float[] cr : cracks) {
            int pts = cr.length / 2;
            int shown = Math.max(2, Math.round(pts * Mth.clamp(grow, 0F, 1F)));
            for (int i = 0; i + 1 < shown; i++) {
                float w = 0.16F * (1F - i / (float) pts) + 0.04F;
                seg(buf, cam, cx + cr[i * 2], y, cz + cr[i * 2 + 1], cx + cr[i * 2 + 2], y, cz + cr[i * 2 + 3], w * 2.4F, rgb, alpha * 0.35F);
                seg(buf, cam, cx + cr[i * 2], y + 0.005, cz + cr[i * 2 + 1], cx + cr[i * 2 + 2], y + 0.005, cz + cr[i * 2 + 3], w, hot, alpha);
            }
        }
    }

    /** Rachaduras radiais em zigue-zague (pares x,z relativos ao centro), com galhos curtos. */
    private static float[][] cracks(RandomSource rnd, int count, float len) {
        float[][] out = new float[count][];
        for (int i = 0; i < count; i++) {
            int pts = 7;
            float[] cr = new float[pts * 2];
            double a = Math.PI * 2 * i / count + (rnd.nextDouble() - 0.5) * 0.5;
            double x = Math.cos(a) * 0.4, z = Math.sin(a) * 0.4;
            double l = len * (0.6 + rnd.nextDouble() * 0.5);
            for (int k = 0; k < pts; k++) {
                cr[k * 2] = (float) x;
                cr[k * 2 + 1] = (float) z;
                double da = a + (rnd.nextDouble() - 0.5) * 1.0;
                x += Math.cos(da) * l / (pts - 1);
                z += Math.sin(da) * l / (pts - 1);
            }
            out[i] = cr;
        }
        return out;
    }

    // ------------------------------------------------------------------ geometria (quads com cor, relativos à câmera)
    private static void v(BufferBuilder b, Vec3 cam, double x, double y, double z, int rgb, float a) {
        b.addVertex((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
                .setColor((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, (int) (Mth.clamp(a, 0F, 1F) * 255));
    }

    /** Segmento deitado no chão (faixa de largura {@code w}). */
    static void seg(BufferBuilder b, Vec3 cam, double x1, double y1, double z1, double x2, double y2, double z2, float w, int rgb, float a) {
        double dx = x2 - x1, dz = z2 - z1;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1e-4) return;
        double nx = -dz / len * w * 0.5, nz = dx / len * w * 0.5;
        v(b, cam, x1 + nx, y1, z1 + nz, rgb, a);
        v(b, cam, x2 + nx, y2, z2 + nz, rgb, a);
        v(b, cam, x2 - nx, y2, z2 - nz, rgb, a);
        v(b, cam, x1 - nx, y1, z1 - nz, rgb, a);
    }

    /** Anel deitado (raio {@code r}, espessura {@code w}); {@code dashed} = só segmentos alternados. */
    static void ring(BufferBuilder b, Vec3 cam, double cx, double y, double cz, float r, float w, int segs, float rot,
                             boolean dashed, int rgb, float a) {
        if (a <= 0.01F || r <= 0.01F) return;
        float ri = Math.max(0, r - w * 0.5F), ro = r + w * 0.5F;
        for (int i = 0; i < segs; i++) {
            if (dashed && i % 2 == 1) continue;
            double a0 = rot + Math.PI * 2 * i / segs, a1 = rot + Math.PI * 2 * (i + 1) / segs;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            v(b, cam, cx + c0 * ri, y, cz + s0 * ri, rgb, a);
            v(b, cam, cx + c0 * ro, y, cz + s0 * ro, rgb, a);
            v(b, cam, cx + c1 * ro, y, cz + s1 * ro, rgb, a);
            v(b, cam, cx + c1 * ri, y, cz + s1 * ri, rgb, a);
        }
    }

    /** Parede cilíndrica (onda de choque, coluna, pilar): alfa {@code aBottom} embaixo e {@code aTop} em cima. */
    static void wall(BufferBuilder b, Vec3 cam, double cx, double y, double cz, float r, float h, int segs, int rgb,
                             float aBottom, float aTop) {
        if ((aBottom <= 0.01F && aTop <= 0.01F) || r <= 0.01F || h <= 0.01F) return;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
            double c0 = Math.cos(a0) * r, s0 = Math.sin(a0) * r, c1 = Math.cos(a1) * r, s1 = Math.sin(a1) * r;
            v(b, cam, cx + c0, y, cz + s0, rgb, aBottom);
            v(b, cam, cx + c1, y, cz + s1, rgb, aBottom);
            v(b, cam, cx + c1, y + h, cz + s1, rgb, aTop);
            v(b, cam, cx + c0, y + h, cz + s0, rgb, aTop);
        }
    }

    // =====================================================================================
    //  Ajudantes
    // =====================================================================================
    private static void syncLevel(ClientLevel level) {
        if (level != fxLevel) {
            clear();
            fxLevel = level;
        }
    }

    static Race raceOf(Entity e) {
        ClientCharacter.Look look = ClientCharacter.look(e.getId());
        return look == null ? null : look.race();
    }

    /** Cor dos efeitos da forma (o Dracônico usa a ancestralidade). */
    static int color(Entity e, Race race) {
        if (race == Race.DRAGONBORN) {
            ClientCharacter.Look look = ClientCharacter.look(e.getId());
            if (look != null) return look.ancestryRgb() & 0xFFFFFF;
        }
        return fxColor(race);
    }

    static int fxColor(Race race) {
        return switch (race) {
            case HUMAN -> 0xFFD86B;
            case ELF -> 0xBFF0FF;
            case DWARF -> 0xFF8A2A;
            case HALF_ORC -> 0xE02828;
            case DEMON -> 0xFF3A2A;
            case DRAGONBORN -> Element.FIRE.color;
            case BEASTFOLK -> 0xFFB040;
            case ARACHNID -> 0xB050F0;
            case PHANTOM -> 0x9FE8F8;
            case ANGEL -> 0xFFF0B0;
            case SLIME -> 0x8FE05A;
        };
    }

    private static ParticleOptions pullParticle(Race race) {
        return switch (race) {
            case ELF -> ParticleTypes.ENCHANT;
            case DEMON -> ParticleTypes.SOUL_FIRE_FLAME;
            case PHANTOM -> ParticleTypes.SOUL;
            case ARACHNID -> ParticleTypes.PORTAL;
            case DWARF -> ParticleTypes.FLAME;
            case HALF_ORC -> ParticleTypes.CRIMSON_SPORE;
            default -> ParticleTypes.END_ROD;
        };
    }

    private static boolean inRange(Minecraft mc, Entity e) {
        return inRange(mc, e.position());
    }

    private static boolean inRange(Minecraft mc, Vec3 pos) {
        if (mc.gameRenderer == null) return true;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double r = FxQuality.range();
        return cam.distanceToSqr(pos) <= r * r;
    }

    private static float falloff(float dist, float radius) {
        float f = Mth.clamp(1F - dist / radius, 0F, 1F);
        return f * f * (3 - 2 * f);
    }

    static ParticleOptions glow(int rgb, float size) {
        return ElementFx.glow(rgb, size);
    }

    static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F),
                Math.max(0.2F, Math.min(4F, size)));
    }

    private static int alpha(float a) {
        return ((int) (Mth.clamp(a, 0F, 1F) * 255) & 0xFF) << 24;
    }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private FormFx() {}
}
